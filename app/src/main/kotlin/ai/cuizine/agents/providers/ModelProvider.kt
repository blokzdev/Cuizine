package ai.cuizine.agents.providers

/**
 * The provider abstraction (ADR 0006): every LLM call in Cuizine goes through
 * this interface; agents declare capability tiers and the routing layer maps
 * tier → concrete provider/model. Adapters exist for Anthropic, Google, and
 * OpenAI; the deterministic [ai.cuizine.agents.providers.FakeModelProvider]
 * is bound whenever a provider's key is absent (CLAUDE.md §9).
 */
interface ModelProvider {
    val id: ProviderId

    /**
     * One structured completion. [ModelRequest.outputSchemaName] names the
     * agent output contract the response must conform to — adapters enable
     * the provider's constrained-generation mechanics for it, and the
     * orchestrator schema-validates regardless (§2: malformed output is an
     * agent error, never parsed heuristically).
     */
    suspend fun complete(request: ModelRequest): ModelResult
}

enum class ProviderId { Anthropic, Google, OpenAi, Fake }

/**
 * Capability tiers from the §8 routing table. Adapters map these to concrete
 * model ids in `core/config` — model names are configuration, never
 * hardcoded at call sites (`build-conventions.md` §11).
 */
enum class ModelTier {
    /** Curator/Chef default: reasoning-strong, interactive. */
    ReasoningStrong,

    /** §8 escalation: high-stakes Curator turns; Chef attempts ≥ 2. */
    ReasoningEscalated,

    /** Pantry + validator structured-extraction: cheap, fast, parsing-shaped. */
    Light,
}

data class ModelRequest(
    val systemPrompt: String,
    val userContent: String,
    val tier: ModelTier,
    /** curator_output | chef_output | pantry_output | ingredient_categorization. */
    val outputSchemaName: String,
    val maxOutputTokens: Int = 2048,
)

sealed interface ModelResult {
    /** Raw JSON text the orchestrator deserializes + schema-validates. */
    data class Success(
        val jsonText: String,
    ) : ModelResult

    data class Failure(
        val kind: FailureKind,
        val message: String,
    ) : ModelResult
}

enum class FailureKind { Network, RateLimited, ServerError, MalformedOutput, NotConfigured }
