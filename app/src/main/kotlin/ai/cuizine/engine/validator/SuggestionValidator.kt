package ai.cuizine.engine.validator

import ai.cuizine.engine.ports.FoodDataPort
import ai.cuizine.engine.ports.IngredientFacts
import ai.cuizine.engine.ports.IngredientResolution
import ai.cuizine.engine.types.AvoidPayload
import ai.cuizine.engine.types.ConstraintType
import ai.cuizine.engine.types.ContextualPayload
import ai.cuizine.engine.types.LimitPayload
import ai.cuizine.engine.types.PreferPayload
import ai.cuizine.engine.types.RequirePayload
import ai.cuizine.engine.types.Severity
import ai.cuizine.engine.types.SuggestionInput
import ai.cuizine.engine.types.ValidationResult
import ai.cuizine.engine.types.Violation
import ai.cuizine.shared.types.Constraint

/**
 * The deterministic post-generation validator (ADR 0010;
 * `constraint-engine-spec.md` §7): non-optional, non-configurable,
 * non-skippable, stateless. A pure function from (active constraints,
 * suggestion) to (passed, violations, disclosures). Its only external calls
 * are Food Data Provider lookups and the severity-scoped AI-fallback
 * sub-call — never a reasoning LLM.
 *
 * PARK-ALWAYS (CLAUDE.md §5): this logic follows the spec exactly; any
 * deviation is a founder decision, never an autonomous one.
 */
class SuggestionValidator(
    private val foodData: FoodDataPort,
) {
    suspend fun validate(
        activeConstraints: List<Constraint>,
        suggestion: SuggestionInput,
    ): ValidationResult {
        val violations = mutableListOf<Violation>()
        val disclosures = mutableListOf<String>()

        // Step 2: resolve every ingredient once through the Food Data Provider.
        val resolutions: Map<String, IngredientResolution> =
            suggestion.ingredients.associate { it.name to foodData.lookup(it.name) }

        // Per-severity AI-fallback cache so the sub-call runs at most once per
        // unknown ingredient (§7 Step 4).
        val aiFacts = mutableMapOf<String, IngredientFacts?>()

        suspend fun aiFactsFor(name: String): IngredientFacts? =
            aiFacts.getOrPut(name) { foodData.categorizeWithAi(name) }

        // Steps 3+4 interleaved per constraint: the unknown-ingredient policy
        // depends on the severity of the constraint being checked.
        for (constraint in activeConstraints) {
            when (val payload = constraint.payload) {
                is AvoidPayload -> {
                    for (ingredient in suggestion.ingredients) {
                        val facts =
                            factsForCheck(
                                ingredientName = ingredient.name,
                                resolution = resolutions.getValue(ingredient.name),
                                severity = constraint.severity,
                                aiFactsFor = ::aiFactsFor,
                                violations = violations,
                                disclosures = disclosures,
                                constraint = constraint,
                            ) ?: continue
                        val matches = matchesTarget(payload.target.kind, payload.target.value, ingredient.name, facts)
                        val excepted =
                            payload.exceptions.any { exception ->
                                matchesTarget(exception.kind, exception.value, ingredient.name, facts)
                            }
                        if (matches && !excepted) {
                            violations +=
                                Violation(
                                    constraintId = constraint.id,
                                    constraintType = ConstraintType.Avoid,
                                    severity = constraint.severity,
                                    reason = "contains avoided ${payload.target.kind} '${payload.target.value}'",
                                    ingredientName = ingredient.name,
                                )
                        }
                    }
                }

                is RequirePayload -> {
                    // v1 enforces the single-meal window; daily accounting
                    // needs cross-meal history (v2+, per §3 Type 3).
                    if (payload.window != "single_meal") continue
                    val satisfied =
                        suggestion.ingredients.any { ingredient ->
                            val facts =
                                when (val resolution = resolutions.getValue(ingredient.name)) {
                                    is IngredientResolution.Known -> resolution.facts
                                    IngredientResolution.Unknown -> null
                                } ?: return@any false
                            val targetMatch =
                                matchesTarget(payload.target.kind, payload.target.value, ingredient.name, facts)
                            val threshold = payload.target.threshold
                            if (threshold == null) {
                                targetMatch
                            } else {
                                val amount =
                                    (facts.nutritionPer100g[payload.target.value] ?: 0.0) *
                                        ingredient.quantityGrams() / 100.0
                                amount >= threshold.value
                            }
                        }
                    if (!satisfied) {
                        violations +=
                            Violation(
                                constraintId = constraint.id,
                                constraintType = ConstraintType.Require,
                                severity = constraint.severity,
                                reason = "missing required ${payload.target.kind} '${payload.target.value}'",
                            )
                    }
                }

                is LimitPayload -> {
                    val property = payload.target.value
                    val sum =
                        suggestion.ingredients.sumOf { ingredient ->
                            val facts =
                                when (val resolution = resolutions.getValue(ingredient.name)) {
                                    is IngredientResolution.Known -> resolution.facts
                                    IngredientResolution.Unknown -> null
                                }
                            ((facts?.nutritionPer100g?.get(property)) ?: 0.0) *
                                ingredient.quantityGrams() / 100.0
                        }
                    // per_meal ceilings apply directly; a single meal exceeding
                    // a per_day/per_week ceiling outright is also definitive.
                    if (sum > payload.ceiling.value) {
                        if (payload.hardOrSoft == "hard") {
                            violations +=
                                Violation(
                                    constraintId = constraint.id,
                                    constraintType = ConstraintType.Limit,
                                    severity = constraint.severity,
                                    reason =
                                        "$property ${"%.0f".format(sum)}${payload.ceiling.unit} exceeds the " +
                                            "${payload.ceiling.value}${payload.ceiling.unit} ${payload.window} ceiling",
                                )
                        } else {
                            disclosures +=
                                "This one runs a little high on ${property.replace('_', ' ')} for the day — " +
                                "worth knowing, not worth worrying."
                        }
                    }
                }

                is PreferPayload -> {
                    val aligned =
                        suggestion.mealName.contains(payload.target.value, ignoreCase = true) ||
                            suggestion.ingredients.any { ingredient ->
                                ingredient.name.contains(payload.target.value, ignoreCase = true)
                            }
                    if (!aligned && payload.strength == "high") {
                        // Misalignment is a quiet note, never a rejection (§7 Step 3).
                        disclosures += "Not a ${payload.target.value} day this time — it's still on the list."
                    }
                }

                is ContextualPayload -> {
                    val incompatible = payload.state.incompatibleWith.orEmpty()
                    if ("any_meal" in incompatible && suggestion.ingredients.isNotEmpty()) {
                        violations +=
                            Violation(
                                constraintId = constraint.id,
                                constraintType = ConstraintType.Contextual,
                                severity = constraint.severity,
                                reason =
                                    "the active '${payload.state.flag}' state is incompatible with " +
                                        "any meal in this window",
                            )
                    }
                }
            }
        }

        // Step 5: aggregate.
        return ValidationResult(
            passed = violations.isEmpty(),
            violations = violations,
            disclosureNotes = disclosures.distinct(),
        )
    }

    /**
     * §7 Step 4 — severity-scoped unknown-ingredient handling. Returns facts
     * usable for the check, or null when this ingredient cannot be checked
     * (in which case the safety-floor outcome has already been recorded).
     */
    private suspend fun factsForCheck(
        ingredientName: String,
        resolution: IngredientResolution,
        severity: Severity,
        aiFactsFor: suspend (String) -> IngredientFacts?,
        violations: MutableList<Violation>,
        disclosures: MutableList<String>,
        constraint: Constraint,
    ): IngredientFacts? =
        when (resolution) {
            is IngredientResolution.Known -> {
                resolution.facts
            }

            IngredientResolution.Unknown -> {
                when (severity) {
                    Severity.Inviolable -> {
                        // The safety floor: unknown ingredients are NEVER
                        // AI-categorized against Inviolable constraints.
                        violations +=
                            Violation(
                                constraintId = constraint.id,
                                constraintType = constraint.type,
                                severity = severity,
                                reason =
                                    "'$ingredientName' could not be identified and is checked against a " +
                                        "never-crossed constraint — rejected outright",
                                ingredientName = ingredientName,
                            )
                        null
                    }

                    Severity.Medical, Severity.ReligiousCultural -> {
                        val ai = aiFactsFor(ingredientName)
                        if (ai != null) {
                            disclosures +=
                                "I'm not 100% sure about $ingredientName — if you know it bothers you, " +
                                "swap it out."
                            ai
                        } else {
                            violations +=
                                Violation(
                                    constraintId = constraint.id,
                                    constraintType = constraint.type,
                                    severity = severity,
                                    reason =
                                        "'$ingredientName' could not be identified or categorized under a " +
                                            "${severity.storageValue} constraint",
                                    ingredientName = ingredientName,
                                )
                            null
                        }
                    }

                    Severity.Preference -> {
                        // Lower stakes: AI fallback without a disclosure (§7 Step 4).
                        aiFactsFor(ingredientName)
                    }
                }
            }
        }

    private fun matchesTarget(
        kind: String,
        value: String,
        rawIngredientName: String,
        facts: IngredientFacts,
    ): Boolean =
        when (kind) {
            "ingredient", "dish" -> {
                facts.canonicalName.equals(value, ignoreCase = true) ||
                    rawIngredientName.equals(value, ignoreCase = true)
            }

            "category" -> {
                facts.categories.any { it.equals(value, ignoreCase = true) } ||
                    facts.allergens.any { it.equals(value, ignoreCase = true) }
            }

            "nutritional_property" -> {
                (facts.nutritionPer100g[value] ?: 0.0) > 0.0
            }

            else -> {
                false
            }
        }
}
