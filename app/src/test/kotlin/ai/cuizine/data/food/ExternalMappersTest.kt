package ai.cuizine.data.food

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure mapping tests against recorded response shapes (DECISION-LOG #4a) —
 * no network. The shapes mirror live-verified payloads from the Phase 4
 * research: USDA's flattened search nutrients; OFF's grams-everywhere
 * nutriments (sodium 0.043 g == 43 mg) with en:-prefixed tags.
 */
class ExternalMappersTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun usdaSearchHit_mapsNutrientNumbersToEngineKeys() {
        val payload =
            """
            {
              "foods": [
                {
                  "fdcId": 747447,
                  "description": "Quinoa, uncooked",
                  "dataType": "Foundation",
                  "foodCategory": "Cereal Grains and Pasta",
                  "foodNutrients": [
                    { "nutrientId": 1003, "nutrientName": "Protein", "nutrientNumber": "203", "unitName": "G", "value": 14.1 },
                    { "nutrientId": 1004, "nutrientName": "Total lipid (fat)", "nutrientNumber": "204", "unitName": "G", "value": 6.1 },
                    { "nutrientId": 1005, "nutrientName": "Carbohydrate, by difference", "nutrientNumber": "205", "unitName": "G", "value": 64.2 },
                    { "nutrientId": 1079, "nutrientName": "Fiber, total dietary", "nutrientNumber": "291", "unitName": "G", "value": 7.0 },
                    { "nutrientId": 1093, "nutrientName": "Sodium, Na", "nutrientNumber": "307", "unitName": "MG", "value": 5.0 },
                    { "nutrientId": 1092, "nutrientName": "Potassium, K", "nutrientNumber": "306", "unitName": "MG", "value": 563.0 },
                    { "nutrientId": 1091, "nutrientName": "Phosphorus, P", "nutrientNumber": "305", "unitName": "MG", "value": 457.0 },
                    { "nutrientId": 9999, "nutrientName": "Something irrelevant", "nutrientNumber": "999", "unitName": "G", "value": 1.0 }
                  ]
                }
              ],
              "totalHits": 1
            }
            """.trimIndent()
        val response = json.decodeFromString<UsdaSearchResponse>(payload)
        val entry = UsdaMapper.toEntry(response.foods.single(), retrievedAtIso = NOW)

        assertEquals("Quinoa, uncooked", entry.displayName)
        assertEquals(listOf("cereal_grains_and_pasta"), entry.categories)
        val facts = entry.toFacts()
        assertEquals(14.1, facts.nutritionPer100g.getValue("protein_g"), 0.001)
        assertEquals(5.0, facts.nutritionPer100g.getValue("sodium_mg"), 0.001)
        assertEquals(457.0, facts.nutritionPer100g.getValue("phosphorus_mg"), 0.001)
        // Unmapped nutrient numbers are dropped, not misfiled.
        assertTrue(facts.nutritionPer100g.keys.none { it.contains("999") })
        assertEquals("usda", entry.sourceMetadata.source)
        assertEquals("747447", entry.sourceMetadata.sourceEntryId)
    }

    @Test
    fun offHit_convertsGramsToMilligrams_andStripsTagPrefixes() {
        val payload =
            """
            {
              "hits": [
                {
                  "code": "3017624010701",
                  "product_name": "Hazelnut spread",
                  "categories_tags": ["en:breakfasts", "en:sweet-spreads"],
                  "allergens_tags": ["en:nuts", "en:milk"],
                  "ingredients_analysis_tags": ["en:non-vegan", "en:palm-oil"],
                  "nutriments": {
                    "sodium_100g": 0.043,
                    "carbohydrates_100g": 57.5,
                    "fiber_100g": "3.4",
                    "proteins_100g": 6.3,
                    "fat_100g": 30.9,
                    "energy-kcal_100g": 539
                  }
                }
              ],
              "count": 1
            }
            """.trimIndent()
        val response = json.decodeFromString<OffSearchResponse>(payload)
        val entry = OffMapper.toEntry(response.hits.single(), retrievedAtIso = NOW)!!

        // 0.043 g sodium per 100 g == 43 mg (#4a unit warning).
        assertEquals(43.0, entry.nutritionalComposition.sodiumMg!!, 0.001)
        // String-typed numbers parse defensively.
        assertEquals(3.4, entry.nutritionalComposition.fiberG!!, 0.001)
        assertTrue(entry.categories.containsAll(listOf("breakfasts", "sweet_spreads", "non_vegan", "palm_oil")))
        assertEquals(listOf("nuts", "milk"), entry.allergens)
        assertEquals("open_food_facts", entry.sourceMetadata.source)
    }

    @Test
    fun offHit_withoutName_isSkipped() {
        val hit = OffHit(code = "123", productName = "  ")
        assertNull(OffMapper.toEntry(hit, NOW))
    }

    private companion object {
        const val NOW = "2026-06-10T12:00:00Z"
    }
}
