package ai.cuizine.agents

import ai.cuizine.agents.providers.AgentKind
import ai.cuizine.agents.providers.ModelRequest
import ai.cuizine.agents.providers.ModelResult
import ai.cuizine.agents.providers.ModelTier
import ai.cuizine.agents.providers.ProviderRouter
import ai.cuizine.shared.types.AlphaEventLogger
import kotlinx.serialization.json.Json

/**
 * The shared agent-invocation mechanics (`agent-architecture.md` §2):
 * serialize the typed input, call the routed provider with constrained
 * generation, decode the typed output. Malformed output is an agent error —
 * retried once, never heuristically parsed.
 */
class AgentInvoker(
    @PublishedApi internal val router: ProviderRouter,
    @PublishedApi internal val promptStore: PromptStore,
    @PublishedApi internal val events: AlphaEventLogger,
) {
    val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    sealed interface AgentResult<out T> {
        data class Ok<T>(
            val value: T,
        ) : AgentResult<T>

        data class Failed(
            val reason: String,
        ) : AgentResult<Nothing>
    }

    suspend inline fun <reified I, reified O> invoke(
        kind: AgentKind,
        promptFile: String,
        schemaName: String,
        tier: ModelTier,
        input: I,
    ): AgentResult<O> {
        val request =
            ModelRequest(
                systemPrompt = promptStore.systemPrompt(promptFile),
                userContent = json.encodeToString(input),
                tier = tier,
                outputSchemaName = schemaName,
            )
        repeat(2) { attempt ->
            when (val result = router.complete(kind, request)) {
                is ModelResult.Success -> {
                    runCatching { json.decodeFromString<O>(result.jsonText) }
                        .onSuccess { return AgentResult.Ok(it) }
                    // Malformed output (§4/§5 error handling): one retry.
                    if (attempt == 1) {
                        events.log(
                            eventType = "agent_error",
                            severity = "severity_one",
                            payloadJson = """{"agent":"${kind.name}","error":"malformed_output"}""",
                        )
                    }
                }

                is ModelResult.Failure -> {
                    if (attempt == 1) {
                        events.log(
                            eventType = "agent_error",
                            severity = "severity_one",
                            payloadJson = """{"agent":"${kind.name}","error":"${result.kind}"}""",
                        )
                        return AgentResult.Failed(result.message)
                    }
                }
            }
        }
        return AgentResult.Failed("malformed agent output after retry")
    }
}
