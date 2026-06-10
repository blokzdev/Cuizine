package ai.cuizine.agents.providers

import ai.cuizine.shared.types.agents.ChefIngredient
import ai.cuizine.shared.types.agents.ChefInput
import ai.cuizine.shared.types.agents.ChefOutput
import ai.cuizine.shared.types.agents.ConstraintOperation
import ai.cuizine.shared.types.agents.CuratorInput
import ai.cuizine.shared.types.agents.CuratorOutput
import ai.cuizine.shared.types.agents.PantryInput
import ai.cuizine.shared.types.agents.PantryOutput
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The deterministic ModelProvider fake (CLAUDE.md §9's named fake): no key,
 * no network, no randomness — it deserializes the typed agent input and
 * answers from fixtures, returning SCHEMA-VALID JSON for every agent
 * contract. It covers the eval-harness scenarios, including a scripted
 * validation-failure meal so the orchestrator's regeneration loop is
 * exercisable end to end with zero credentials.
 *
 * It does not pretend to be intelligent. It pretends to be *predictable*.
 */
@Singleton
class FakeModelProvider
    @Inject
    constructor() : ModelProvider {
        override val id: ProviderId = ProviderId.Fake
        private val json = Json { ignoreUnknownKeys = true }

        override suspend fun complete(request: ModelRequest): ModelResult =
            when (request.outputSchemaName) {
                "curator_output" -> {
                    curator(request)
                }

                "chef_output" -> {
                    chef(request)
                }

                "pantry_output" -> {
                    pantry(request)
                }

                "ingredient_categorization" -> {
                    categorize(request)
                }

                else -> {
                    ModelResult.Failure(
                        FailureKind.MalformedOutput,
                        "Unknown schema ${request.outputSchemaName}",
                    )
                }
            }

        // ── Curator ──────────────────────────────────────────────────────

        private fun curator(request: ModelRequest): ModelResult {
            val input = json.decodeFromString<CuratorInput>(request.userContent)
            val output =
                when (input.intentType) {
                    "begin_constraint_conversation" -> {
                        CuratorOutput(
                            userMessage = ONBOARDING_QUESTIONS.first(),
                            nextIntent = "continue_constraint_conversation",
                        )
                    }

                    "continue_constraint_conversation" -> {
                        continueConversation(input)
                    }

                    "free_text_update" -> {
                        freeTextUpdate(input)
                    }

                    "request_suggestion_explanation" -> {
                        CuratorOutput(
                            userMessage = explanation(input),
                            nextIntent = "ready_for_suggestions",
                        )
                    }

                    else -> {
                        CuratorOutput(
                            userMessage = "I didn't quite catch that — could you say it another way?",
                            nextIntent = "ready_for_suggestions",
                        )
                    }
                }
            return ModelResult.Success(json.encodeToString(output))
        }

        private fun continueConversation(input: CuratorInput): CuratorOutput {
            val answered = input.conversationHistory.count { it.author == "user" } + 1
            val operations = captureOperations(input.currentMessage)
            return if (answered >= ONBOARDING_QUESTIONS.size) {
                CuratorOutput(
                    constraintOperations = operations,
                    userMessage =
                        "That's everything I need to start. I'll keep all of it in mind for " +
                            "every meal — you never have to repeat yourself.",
                    nextIntent = "ready_for_suggestions",
                )
            } else {
                CuratorOutput(
                    constraintOperations = operations,
                    userMessage = "${ack(answered - 1)}\n\n${ONBOARDING_QUESTIONS[answered]}",
                    nextIntent = "continue_constraint_conversation",
                )
            }
        }

        private fun freeTextUpdate(input: CuratorInput): CuratorOutput {
            val operations = captureOperations(input.currentMessage)
            val message =
                when {
                    operations.any { it.flag == "blood_sugar_elevated" } -> {
                        "Got it — I'll keep meals gentler on your sugar for the next little " +
                            "while. You don't need to do anything else."
                    }

                    operations.any { it.flag == "ibs_flare" } -> {
                        "I'm sorry it's a rough stomach day. No onion or garlic until you tell " +
                            "me it's settled — and I'll lean gentler in general."
                    }

                    operations.any { it.flag == "traveling" } -> {
                        "Noted — enjoy the trip. I'll keep suggestions to what's easy to find " +
                            "where you are."
                    }

                    operations.isNotEmpty() -> {
                        "Understood — that's off the list for now. You can change your mind " +
                            "any time in your Profile."
                    }

                    else -> {
                        "I didn't quite catch that — could you say it another way? I'm good " +
                            "with plain words: what you ate, how you feel, what you're avoiding."
                    }
                }
            return CuratorOutput(
                constraintOperations = operations,
                userMessage = message,
                nextIntent = "ready_for_suggestions",
            )
        }

        /** Keyword-scripted constraint capture — deterministic, fixture-grade. */
        private fun captureOperations(message: String): List<ConstraintOperation> {
            val lower = message.lowercase()
            val operations = mutableListOf<ConstraintOperation>()
            if (Regex("sugar (was|is|hit) ?[0-9]").containsMatchIn(lower) || "glucose" in lower) {
                operations +=
                    ConstraintOperation(
                        operation = "set_contextual_state",
                        flag = "blood_sugar_elevated",
                        originalPhrasing = message,
                    )
            }
            if ("stomach" in lower || "flare" in lower || "ibs" in lower) {
                operations +=
                    ConstraintOperation(
                        operation = "set_contextual_state",
                        flag = "ibs_flare",
                        originalPhrasing = message,
                    )
            }
            if (Regex("i'?m in [a-z]+ for").containsMatchIn(lower) || "travel" in lower) {
                operations +=
                    ConstraintOperation(
                        operation = "set_contextual_state",
                        flag = "traveling",
                        originalPhrasing = message,
                    )
            }
            if ("avoiding dairy" in lower || "no dairy" in lower) {
                operations +=
                    ConstraintOperation(
                        operation = "add_constraint",
                        type = "avoid",
                        severity = "preference",
                        humanLabel = "Skipping dairy for now",
                        payloadJson = """{"target":{"kind":"category","value":"dairy"}}""",
                        originalPhrasing = message,
                    )
            }
            if ("diabetes" in lower) {
                operations +=
                    ConstraintOperation(
                        operation = "add_constraint",
                        type = "limit",
                        severity = "medical",
                        humanLabel = "Go easy on refined carbs at each meal",
                        payloadJson =
                            """{"target":{"kind":"nutritional_property","value":"refined_carbohydrates"},""" +
                                """"ceiling":{"value":45.0,"unit":"g"},"window":"per_meal","hard_or_soft":"soft"}""",
                        originalPhrasing = message,
                    )
            }
            if ("tuesday" in lower && ("vegetarian" in lower || "fast" in lower)) {
                operations +=
                    ConstraintOperation(
                        operation = "add_constraint",
                        type = "avoid",
                        severity = "religious_cultural",
                        humanLabel = "Vegetarian on Tuesdays",
                        payloadJson = """{"target":{"kind":"category","value":"meat"}}""",
                        originalPhrasing = message,
                    )
            }
            return operations
        }

        private fun explanation(input: CuratorInput): String {
            val labels = input.activeConstraintSummary.take(3).joinToString("; ") { it.label }
            return if (labels.isEmpty()) {
                "I picked this because it fits what you've told me so far."
            } else {
                "I picked this because it fits everything you've told me — including: $labels. " +
                    "Ask about any rule on your Profile and I'll tell you exactly when you added it."
            }
        }

        // ── Chef ─────────────────────────────────────────────────────────

        private fun chef(request: ModelRequest): ModelResult {
            val input = json.decodeFromString<ChefInput>(request.userContent)
            val lowerRequest = input.requestDetails.lowercase()
            val flareActive =
                input.activeConstraintSummary.any { summary ->
                    "stomach" in summary.label.lowercase() || "allium" in summary.label.lowercase()
                }
            val output =
                when {
                    // Scripted validation-failure path: lets the eval harness
                    // (and a keyless build) exercise the real regeneration
                    // loop — attempt 1 violates, attempt 2+ corrects.
                    "steak" in lowerRequest && input.attemptNumber == 1 -> {
                        BEEF_KEEMA
                    }

                    input.previousFailureReasons.isNotEmpty() || input.attemptNumber > 1 -> {
                        if (flareActive) KHICHDI else MASOOR_DAL
                    }

                    flareActive -> {
                        KHICHDI
                    }

                    "quick" in lowerRequest || "fast" in lowerRequest -> {
                        KHICHDI
                    }

                    "rajma" in lowerRequest -> {
                        RAJMA
                    }

                    input.recentHistory.any { "masoor" in it.lowercase() } -> {
                        ALOO_METHI
                    }

                    else -> {
                        MASOOR_DAL
                    }
                }
            return ModelResult.Success(json.encodeToString(output))
        }

        // ── Pantry ───────────────────────────────────────────────────────

        private fun pantry(request: ModelRequest): ModelResult {
            val input = json.decodeFromString<PantryInput>(request.userContent)
            val normalized = input.queryString.trim().lowercase()
            val output =
                when {
                    normalized == "dal" -> {
                        PantryOutput(
                            resolvedName = "toor dal",
                            candidates = listOf("toor dal", "masoor dal", "moong dal"),
                            confidence = "medium",
                            disclosureNotes =
                                listOf(
                                    "I read \"dal\" as toor dal — the most common in North Indian " +
                                        "kitchens. Tell me if you meant a different one.",
                                ),
                        )
                    }

                    normalized in KNOWN_PANTRY_NAMES -> {
                        PantryOutput(resolvedName = normalized, confidence = "high")
                    }

                    else -> {
                        PantryOutput(resolvedName = null, confidence = "low")
                    }
                }
            return ModelResult.Success(json.encodeToString(output))
        }

        // ── Validator AI-fallback categorization ─────────────────────────

        private fun categorize(request: ModelRequest): ModelResult {
            val name = request.userContent.trim().lowercase()
            val categories = CATEGORIZATION_FIXTURES[name] ?: return ModelResult.Success("null")
            return ModelResult.Success(
                """{"display_name":"$name","categories":[${categories.joinToString(",") { "\"$it\"" }}],""" +
                    """"source_metadata":{"source":"ai_fallback","retrieved_at":"1970-01-01T00:00:00Z"}}""",
            )
        }

        private companion object {
            val ONBOARDING_QUESTIONS =
                listOf(
                    "What kind of food do you usually cook at home?",
                    "Who do you usually cook for?",
                    "Are there foods you're trying to avoid right now — for any reason? " +
                        "Health, religion, preference, anything.",
                    "And the other side: what do you and your family love to eat?",
                    "Any days of the week that are different — fasting days, special meals, traditions?",
                )

            fun ack(index: Int): String =
                listOf(
                    "That sounds like a wonderful kitchen. I'll suggest food that belongs in it.",
                    "Got it — cooking for the whole family. I'll keep everyone at the table in mind.",
                    "Thank you for telling me. I'll keep that in mind for everything from now on — quietly.",
                    "Noted, and happily. Favourites matter as much as rules.",
                    "Lovely — I'll remember how your week flows.",
                ).getOrElse(index) { "Got it." }

            val KNOWN_PANTRY_NAMES =
                setOf("atta", "rajma", "spinach", "haldi", "rai", "garam masala", "masoor dal", "onion", "garlic")

            val CATEGORIZATION_FIXTURES: Map<String, List<String>> =
                mapOf(
                    "mystery green" to listOf("vegetable", "leafy_green"),
                    "celtuce" to listOf("vegetable", "stem_vegetable"),
                )

            val MASOOR_DAL =
                ChefOutput(
                    mealName = "Masoor dal with spinach",
                    culturalContext = "North Indian, everyday Punjabi home cooking",
                    ingredients =
                        listOf(
                            ChefIngredient("masoor dal", 150.0, "g"),
                            ChefIngredient("spinach", 100.0, "g"),
                            ChefIngredient("haldi", 2.0, "g"),
                            ChefIngredient("cumin", 2.0, "g"),
                            ChefIngredient("ginger", 5.0, "g"),
                            ChefIngredient("yogurt", 100.0, "g", "for a side raita"),
                            ChefIngredient("cucumber", 80.0, "g", "for the raita"),
                        ),
                    preparationSummary =
                        "Pressure-cook the dal with haldi, temper with jeera and ginger, fold in " +
                            "the spinach at the end; cucumber raita on the side.",
                    cookingInstructions =
                        "Rinse the masoor dal and pressure-cook with haldi for three whistles. " +
                            "Heat a little oil, crackle the jeera, add ginger, and pour over the " +
                            "dal. Stir the spinach in off the heat — it wilts on its own. Grate " +
                            "kheera into dahi for the raita. Phulke if you're making them fresh.",
                    nutritionalRoughEstimate = mapOf("carbohydrates_g" to 38.0, "sodium_mg" to 350.0),
                )

            val KHICHDI =
                ChefOutput(
                    mealName = "Moong dal khichdi with kheera",
                    culturalContext = "North Indian comfort food, gentle by design",
                    ingredients =
                        listOf(
                            ChefIngredient("moong dal", 100.0, "g"),
                            ChefIngredient("rice", 80.0, "g"),
                            ChefIngredient("ghee", 10.0, "g"),
                            ChefIngredient("haldi", 2.0, "g"),
                            ChefIngredient("cucumber", 80.0, "g"),
                        ),
                    preparationSummary = "One-pot khichdi, lightly spiced, with kheera on the side.",
                    cookingInstructions =
                        "Rinse dal and rice together, pressure-cook with haldi and salt to taste. " +
                            "Finish with a spoon of ghee. Sliced kheera alongside — nothing that " +
                            "argues with a tired stomach.",
                    nutritionalRoughEstimate = mapOf("carbohydrates_g" to 42.0, "sodium_mg" to 300.0),
                )

            val RAJMA =
                ChefOutput(
                    mealName = "Rajma with brown basmati",
                    culturalContext = "Punjabi Sunday classic",
                    ingredients =
                        listOf(
                            ChefIngredient("rajma", 150.0, "g"),
                            ChefIngredient("brown basmati", 80.0, "g"),
                            ChefIngredient("tomato", 120.0, "g"),
                            ChefIngredient("ginger", 8.0, "g"),
                            ChefIngredient("garam masala", 3.0, "g"),
                        ),
                    preparationSummary =
                        "Soaked rajma simmered in a tomato-ginger masala, brown basmati instead of white.",
                    cookingInstructions =
                        "Soak the rajma overnight. Pressure-cook until soft. Build the masala on " +
                            "tomatoes and ginger, simmer the rajma in it until the gravy clings, " +
                            "finish with garam masala. The brown basmati sits better with your " +
                            "sugar — the family won't notice; your glucometer will.",
                    nutritionalRoughEstimate = mapOf("carbohydrates_g" to 44.0, "sodium_mg" to 420.0),
                )

            val ALOO_METHI =
                ChefOutput(
                    mealName = "Aloo methi with phulka roti",
                    culturalContext = "North Indian, from-the-pantry weeknight dish",
                    ingredients =
                        listOf(
                            ChefIngredient("potato", 200.0, "g"),
                            ChefIngredient("fenugreek leaves", 80.0, "g"),
                            ChefIngredient("atta", 100.0, "g"),
                            ChefIngredient("haldi", 2.0, "g"),
                            ChefIngredient("mustard seeds", 2.0, "g"),
                        ),
                    preparationSummary = "Dry-spiced aloo methi with fresh phulke.",
                    cookingInstructions =
                        "Crackle rai in hot oil, add diced aloo and haldi, cover and cook till " +
                            "tender, fold in the methi at the end. Knead the atta soft, roll thin, " +
                            "puff the phulke straight on the flame.",
                    nutritionalRoughEstimate = mapOf("carbohydrates_g" to 40.0, "sodium_mg" to 280.0),
                )

            val BEEF_KEEMA =
                ChefOutput(
                    mealName = "Beef keema with peas",
                    culturalContext = "scripted validation-failure fixture",
                    ingredients =
                        listOf(
                            ChefIngredient("beef", 200.0, "g"),
                            ChefIngredient("peas", 80.0, "g"),
                            ChefIngredient("garam masala", 3.0, "g"),
                        ),
                    preparationSummary = "A deliberately non-conforming meal used to exercise the regeneration loop.",
                    cookingInstructions = "(Never shown — the validator rejects this before any user sees it.)",
                )
        }
    }
