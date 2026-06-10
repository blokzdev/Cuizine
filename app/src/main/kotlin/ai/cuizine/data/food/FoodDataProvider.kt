package ai.cuizine.data.food

import ai.cuizine.data.database.daos.FoodDataCacheDao
import ai.cuizine.data.database.entities.FoodDataCacheEntity
import ai.cuizine.data.food.bundle.BundleLoader
import ai.cuizine.engine.ports.EngineClock
import ai.cuizine.engine.ports.FoodDataPort
import ai.cuizine.engine.ports.IngredientFacts
import ai.cuizine.engine.ports.IngredientResolution
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Food Data Provider (ADR 0012; `technical-architecture.md` §3): the
 * unified "give me the canonical entry for this name" API over the
 * three-layer architecture — cache → curated bundle → external sources →
 * severity-scoped AI fallback. Layer orchestration is invisible to callers;
 * the validator just sees [FoodDataPort].
 */
@Singleton
class FoodDataProvider
    @Inject
    constructor(
        private val cacheDao: FoodDataCacheDao,
        private val bundleLoader: BundleLoader,
        private val externalSources: ExternalFoodSources,
        private val aiCategorizer: AiIngredientCategorizer,
        private val clock: EngineClock,
    ) : FoodDataPort {
        private val json = Json { ignoreUnknownKeys = true }

        override suspend fun lookup(ingredientName: String): IngredientResolution {
            val key = BundleLoader.normalize(ingredientName)
            val now = clock.nowIso()

            // Layer 1: cache.
            cacheDao.lookup(key, KIND_INGREDIENT_NAME, now)?.let { row ->
                val entry = json.decodeFromString<CanonicalIngredientEntry>(row.canonicalEntryJson)
                return IngredientResolution.Known(entry.toFacts(), row.confidence)
            }

            // Layer 2: curated bundle (corrections outrank external sources).
            bundleLoader.findByName(ingredientName)?.let { entry ->
                cache(key, entry, confidence = "high_bundle", sourceLayer = "bundle", ttl = null)
                return IngredientResolution.Known(entry.toFacts(), "high_bundle")
            }

            // Layer 3: external sources (USDA → OFF), recorded-fixture fakes
            // when no key/config is present (CLAUDE.md §9).
            externalSources.lookupByName(ingredientName)?.let { found ->
                val corrected = bundleLoader.correctionFor(ingredientName) ?: found.entry
                cache(
                    key,
                    corrected,
                    confidence = found.confidence,
                    sourceLayer = found.sourceLayer,
                    ttl = found.ttlIso(clock.nowIso()),
                )
                return IngredientResolution.Known(corrected.toFacts(), found.confidence)
            }

            return IngredientResolution.Unknown
        }

        /**
         * The AI-fallback sub-call (ADR 0012): cheap-tier categorization,
         * tagged low_ai, cached so repeated unknowns don't re-ask. The
         * VALIDATOR decides when this may be consulted (severity-scoped —
         * never for Inviolable checks).
         */
        override suspend fun categorizeWithAi(ingredientName: String): IngredientFacts? {
            val key = BundleLoader.normalize(ingredientName)
            val now = clock.nowIso()
            cacheDao.lookup(key, KIND_INGREDIENT_NAME, now)?.let { row ->
                if (row.sourceLayer == "ai_fallback") {
                    return json.decodeFromString<CanonicalIngredientEntry>(row.canonicalEntryJson).toFacts()
                }
            }
            val entry = aiCategorizer.categorize(ingredientName) ?: return null
            cache(key, entry, confidence = "low_ai", sourceLayer = "ai_fallback", ttl = null)
            return entry.toFacts()
        }

        private suspend fun cache(
            key: String,
            entry: CanonicalIngredientEntry,
            confidence: String,
            sourceLayer: String,
            ttl: String?,
        ) {
            val now = clock.nowIso()
            cacheDao.upsert(
                FoodDataCacheEntity(
                    id = "food-${UUID.randomUUID()}",
                    lookupKey = key,
                    lookupKind = KIND_INGREDIENT_NAME,
                    canonicalEntryJson = json.encodeToString(entry),
                    confidence = confidence,
                    sourceLayer = sourceLayer,
                    firstCachedAt = now,
                    lastRefreshedAt = now,
                    ttlExpiresAt = ttl,
                ),
            )
        }

        private companion object {
            const val KIND_INGREDIENT_NAME = "ingredient_name"
        }
    }

/** An external-source hit, with its ADR 0012 confidence tag and TTL policy. */
data class ExternalLookupResult(
    val entry: CanonicalIngredientEntry,
    /** high_usda | high_off. */
    val confidence: String,
    /** usda | open_food_facts. */
    val sourceLayer: String,
) {
    /** TTLs per `data-model.md` §5: USDA 6 months, OFF 3 months. */
    fun ttlIso(nowIso: String): String {
        val months = if (sourceLayer == "usda") 6L else 3L
        return Instant.parse(nowIso).plusSeconds(months * 30 * 24 * 3600).toString()
    }
}

/**
 * Layer 3 seam: USDA FoodData Central then Open Food Facts. Implementations
 * are absence-driven (real clients when keys/config exist, recorded fixtures
 * otherwise — CLAUDE.md §9).
 */
interface ExternalFoodSources {
    suspend fun lookupByName(name: String): ExternalLookupResult?
}

/** The AI categorization seam, routed through ModelProvider in Phase 5 (ADR 0006). */
interface AiIngredientCategorizer {
    suspend fun categorize(name: String): CanonicalIngredientEntry?
}
