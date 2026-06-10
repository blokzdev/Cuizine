package ai.cuizine.data.food.bundle

import ai.cuizine.data.food.CanonicalIngredientEntry
import ai.cuizine.data.food.CuratedBundle
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Layer 2 of the food data architecture (ADR 0012; `data-model.md` §5): the
 * curated bundle shipped in the APK, loaded into memory once. Corrections
 * override external sources for their lookup keys.
 */
@Singleton
class BundleLoader
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val json = Json { ignoreUnknownKeys = true }

        val bundle: CuratedBundle by lazy {
            context.assets.open(BUNDLE_PATH).bufferedReader().use { reader ->
                json.decodeFromString<CuratedBundle>(reader.readText())
            }
        }

        private val byKey: Map<String, CanonicalIngredientEntry> by lazy {
            buildMap {
                bundle.entries.forEach { entry ->
                    put(normalize(entry.displayName), entry)
                    entry.alternativeNames.forEach { alt -> put(normalize(alt), entry) }
                }
            }
        }

        private val correctionsByKey: Map<String, CanonicalIngredientEntry> by lazy {
            bundle.corrections
                .filter { it.lookupKind == "ingredient_name" }
                .associate { normalize(it.lookupKey) to it.entry }
        }

        fun findByName(name: String): CanonicalIngredientEntry? {
            val key = normalize(name)
            return correctionsByKey[key] ?: byKey[key]
        }

        /** A correction outranks whatever an external source returned (ADR 0012). */
        fun correctionFor(name: String): CanonicalIngredientEntry? = correctionsByKey[normalize(name)]

        companion object {
            const val BUNDLE_PATH = "food_data_bundle/v1.json"

            /** `data-model.md` §5: lowercase, whitespace-stripped, accent-folded. */
            fun normalize(raw: String): String =
                java.text.Normalizer
                    .normalize(raw.trim().lowercase(), java.text.Normalizer.Form.NFD)
                    .replace(Regex("\\p{M}+"), "")
                    .replace(Regex("\\s+"), " ")
        }
    }
