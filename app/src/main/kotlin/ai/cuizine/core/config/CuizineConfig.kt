package ai.cuizine.core.config

/**
 * Centralized runtime configuration (`build-conventions.md` §11: endpoints,
 * model names, pricing, and feature flags are never hardcoded inline — they
 * live here). Phase 1 carries only what exists; each later phase adds its
 * config surface and the absence-driven fake/real selection reads from here
 * (CLAUDE.md §9: key present → real client, absent → deterministic fake).
 */
data class CuizineConfig(
    /** The constraint-graph schema version this build writes (`data-model.md` §8). */
    val constraintGraphSchemaVersion: Int = CURRENT_CONSTRAINT_GRAPH_SCHEMA_VERSION,
    /** Visible mock-data indicator (`ui-ux-spec.md` §10) — must be true only in non-production builds. */
    val isMockDataIndicatorEnabled: Boolean,
) {
    companion object {
        const val CURRENT_CONSTRAINT_GRAPH_SCHEMA_VERSION = 1
    }
}
