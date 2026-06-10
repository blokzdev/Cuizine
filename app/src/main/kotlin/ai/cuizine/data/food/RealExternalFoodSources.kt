package ai.cuizine.data.food

import ai.cuizine.engine.ports.EngineClock

/**
 * The live Layer 3 (ADR 0012): USDA FoodData Central first (raw-ingredient
 * authority), Open Food Facts second. Every failure path returns null — the
 * provider falls through to Unknown and the validator's severity rules take
 * over. A network hiccup must never crash a validation.
 *
 * Activated only when `cuizine.usda.api.key` exists in local.properties
 * (CLAUDE.md §9); otherwise the recorded-fixture fake is bound.
 */
class RealExternalFoodSources(
    private val usdaApi: UsdaApi,
    private val offApi: OffSearchApi,
    private val clock: EngineClock,
) : ExternalFoodSources {
    override suspend fun lookupByName(name: String): ExternalLookupResult? {
        usdaLookup(name)?.let { return it }
        return offLookup(name)
    }

    private suspend fun usdaLookup(name: String): ExternalLookupResult? =
        runCatching {
            val response = usdaApi.search(UsdaSearchRequest(query = name))
            val best = response.foods.firstOrNull() ?: return@runCatching null
            ExternalLookupResult(
                entry = UsdaMapper.toEntry(best, clock.nowIso()),
                confidence = "high_usda",
                sourceLayer = "usda",
            )
        }.getOrNull()

    private suspend fun offLookup(name: String): ExternalLookupResult? =
        runCatching {
            val response = offApi.search(query = name)
            val entry =
                response.hits.firstNotNullOfOrNull { hit -> OffMapper.toEntry(hit, clock.nowIso()) }
                    ?: return@runCatching null
            ExternalLookupResult(
                entry = entry,
                confidence = "high_off",
                sourceLayer = "open_food_facts",
            )
        }.getOrNull()
}
