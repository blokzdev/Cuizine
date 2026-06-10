package ai.cuizine.agents.providers

import ai.cuizine.shared.types.AlphaEventLogger

/** Which agent (or sub-call) is asking — keys into the §8 routing table. */
enum class AgentKind { Curator, Chef, Pantry, ValidatorExtraction }

/**
 * Per-agent provider routing with the §8 fallback chain: retry once on the
 * default provider, then fall over to the fallback. Fallbacks are logged as
 * OPERATIONAL telemetry (ADR 0011 — never content). Providers that aren't
 * configured resolve to the deterministic fake (CLAUDE.md §9).
 */
class ProviderRouter(
    private val providers: Map<ProviderId, ModelProvider>,
    private val fake: ModelProvider,
    private val events: AlphaEventLogger,
) {
    private fun provider(id: ProviderId): ModelProvider = providers[id] ?: fake

    private fun table(kind: AgentKind): Pair<ProviderId, ProviderId> =
        when (kind) {
            // agent-architecture.md §8: Curator/Chef → Anthropic, fallback Google;
            // Pantry + validator extraction → Google, fallback Anthropic.
            AgentKind.Curator, AgentKind.Chef -> ProviderId.Anthropic to ProviderId.Google

            AgentKind.Pantry, AgentKind.ValidatorExtraction -> ProviderId.Google to ProviderId.Anthropic
        }

    suspend fun complete(
        kind: AgentKind,
        request: ModelRequest,
    ): ModelResult {
        val (defaultId, fallbackId) = table(kind)
        val default = provider(defaultId)
        val first = default.complete(request)
        if (first is ModelResult.Success) return first
        val second = default.complete(request)
        if (second is ModelResult.Success) return second

        events.log(
            eventType = "provider_fallback_triggered",
            severity = "warn",
            payloadJson = """{"agent":"${kind.name}","from":"${default.id}","to":"$fallbackId"}""",
        )
        return provider(fallbackId).complete(request)
    }
}
