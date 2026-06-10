package ai.cuizine.shared.fixtures

import ai.cuizine.engine.types.AddedContext
import ai.cuizine.engine.types.AvoidPayload
import ai.cuizine.engine.types.AvoidTarget
import ai.cuizine.engine.types.ConstraintPayload
import ai.cuizine.engine.types.ConstraintScope
import ai.cuizine.engine.types.ConstraintType
import ai.cuizine.engine.types.ContextualScopeSpec
import ai.cuizine.engine.types.HouseholdScope
import ai.cuizine.engine.types.LimitPayload
import ai.cuizine.engine.types.LimitTarget
import ai.cuizine.engine.types.PreferPayload
import ai.cuizine.engine.types.PreferTarget
import ai.cuizine.engine.types.ProfileScope
import ai.cuizine.engine.types.ProvenanceRecord
import ai.cuizine.engine.types.Severity
import ai.cuizine.engine.types.TemporalScope
import ai.cuizine.engine.types.Threshold
import ai.cuizine.shared.types.ConflictOption
import ai.cuizine.shared.types.Constraint
import ai.cuizine.shared.types.ConstraintConflict
import ai.cuizine.shared.types.ConstraintFitNote
import ai.cuizine.shared.types.CookingFor
import ai.cuizine.shared.types.CulturalContext
import ai.cuizine.shared.types.MealSuggestion
import ai.cuizine.shared.types.PantryEntry
import ai.cuizine.shared.types.ProfileOverview
import ai.cuizine.shared.types.RecoveryPassphrase
import ai.cuizine.shared.types.SuggestionValidation

/**
 * The canonical Sukhi fixture (`testing-strategy.md` §5; PRD §2/§4): 53,
 * Punjabi home cook in Brampton, newly managing Type 2 diabetes and IBS-M,
 * cooking for a four-generation household of five. Defined once, reused by
 * every mock repository and every test that needs realistic data — never
 * invent a new profile when Sukhi works (`testing-strategy.md` Principle 2).
 */
object SukhiFixtures {
    const val PROFILE_ID = "fixture-profile-sukhi"
    private const val DAY0 = "2026-06-01T14:30:00Z"

    val profile =
        ProfileOverview(
            id = PROFILE_ID,
            displayName = "Myself",
            culturalContext =
                CulturalContext(
                    cuisineOrigins = listOf("Punjabi", "North Indian"),
                    regionalMarkers = listOf("Brampton, Ontario", "immigrant household"),
                    householdTraditions = listOf("vegetarian on Tuesdays", "family masala patterns"),
                    ingredientVocabularyHints = listOf("rai = mustard seeds", "haldi = turmeric", "kheera = cucumber"),
                ),
            cookingFor =
                CookingFor(
                    householdSize = 5,
                    householdCompositionNotes = "Four generations at one table; Beeji eats early.",
                    dietaryMixSummary = "Only Sukhi has medical constraints; everyone shares Tuesday vegetarian.",
                ),
            timezone = "America/Toronto",
        )

    private fun provenance(
        phrasing: String,
        questionIndex: Int? = null,
    ) = ProvenanceRecord(
        source = "user_direct",
        addedAt = DAY0,
        addedContext =
            AddedContext(
                flow = "constraint_conversation",
                questionIndex = questionIndex,
            ),
        originalPhrasing = phrasing,
        confidence = "high_user_direct",
    )

    private fun scope(
        temporal: TemporalScope = TemporalScope(kind = "always"),
        contextual: ContextualScopeSpec = ContextualScopeSpec(),
    ) = ConstraintScope(
        temporal = temporal,
        contextual = contextual,
        household = HouseholdScope(mode = "self"),
        profile = ProfileScope(profileId = PROFILE_ID),
    )

    private fun constraint(
        id: String,
        type: ConstraintType,
        severity: Severity,
        humanLabel: String,
        payload: ConstraintPayload,
        scope: ConstraintScope,
        phrasing: String,
        questionIndex: Int? = null,
        isActiveNow: Boolean = true,
    ) = Constraint(
        id = "fixture-constraint-$id",
        profileId = PROFILE_ID,
        type = type,
        severity = severity,
        humanLabel = humanLabel,
        scope = scope,
        payload = payload,
        provenance = provenance(phrasing, questionIndex),
        isActiveNow = isActiveNow,
    )

    /** Medical: onion/garlic avoidance scoped to active IBS flares. */
    val avoidAlliumsDuringFlare =
        constraint(
            id = "allium-flare",
            type = ConstraintType.Avoid,
            severity = Severity.Medical,
            humanLabel = "No onion or garlic while my stomach is bad",
            payload =
                AvoidPayload(
                    target = AvoidTarget(kind = "category", value = "allium", reference = "fodmap_high"),
                ),
            scope = scope(contextual = ContextualScopeSpec(requiredFlags = listOf("ibs_flare"))),
            phrasing = "When my stomach acts up, onions and garlic make it so much worse",
            questionIndex = 3,
            isActiveNow = false,
        )

    val limitSodium =
        constraint(
            id = "sodium",
            type = ConstraintType.Limit,
            severity = Severity.Medical,
            humanLabel = "Keep sodium under 2,000 mg a day",
            payload =
                LimitPayload(
                    target = LimitTarget(kind = "nutritional_property", value = "sodium"),
                    ceiling = Threshold(value = 2000.0, unit = "mg"),
                    window = "per_day",
                    hardOrSoft = "soft",
                ),
            scope = scope(),
            phrasing = "The doctor said to watch salt for my blood pressure",
            questionIndex = 3,
        )

    val limitRefinedCarbs =
        constraint(
            id = "refined-carbs",
            type = ConstraintType.Limit,
            severity = Severity.Medical,
            humanLabel = "Go easy on refined carbs at each meal",
            payload =
                LimitPayload(
                    target = LimitTarget(kind = "nutritional_property", value = "refined_carbohydrates"),
                    ceiling = Threshold(value = 45.0, unit = "g"),
                    window = "per_meal",
                    hardOrSoft = "soft",
                ),
            scope = scope(),
            phrasing = "I'm supposed to watch rice and white flour now, for the sugar",
            questionIndex = 3,
        )

    val avoidBeef =
        constraint(
            id = "beef",
            type = ConstraintType.Avoid,
            severity = Severity.ReligiousCultural,
            humanLabel = "No beef — always",
            payload =
                AvoidPayload(
                    target = AvoidTarget(kind = "ingredient", value = "beef"),
                ),
            scope = scope(),
            phrasing = "We have never cooked beef at home",
            questionIndex = 1,
        )

    val tuesdayVegetarian =
        constraint(
            id = "tuesday-veg",
            type = ConstraintType.Avoid,
            severity = Severity.ReligiousCultural,
            humanLabel = "Vegetarian on Tuesdays",
            payload =
                AvoidPayload(
                    target = AvoidTarget(kind = "category", value = "meat"),
                ),
            scope = scope(temporal = TemporalScope(kind = "weekly", weekdays = listOf("tuesday"))),
            phrasing = "Tuesdays we keep vegetarian, always have",
            questionIndex = 5,
        )

    val preferRajma =
        constraint(
            id = "rajma",
            type = ConstraintType.Prefer,
            severity = Severity.Preference,
            humanLabel = "Rajma is a favourite",
            payload =
                PreferPayload(target = PreferTarget(kind = "dish", value = "rajma"), strength = "high"),
            scope = scope(),
            phrasing = "If you ask the kids, rajma chawal every day",
            questionIndex = 4,
        )

    val preferAloo =
        constraint(
            id = "aloo",
            type = ConstraintType.Prefer,
            severity = Severity.Preference,
            humanLabel = "Aloo dishes feel like home",
            payload =
                PreferPayload(target = PreferTarget(kind = "dish", value = "aloo"), strength = "medium"),
            scope = scope(),
            phrasing = "Anything with aloo, honestly",
            questionIndex = 4,
        )

    val constraints: List<Constraint> =
        listOf(
            avoidAlliumsDuringFlare,
            limitSodium,
            limitRefinedCarbs,
            avoidBeef,
            tuesdayVegetarian,
            preferRajma,
            preferAloo,
        )

    val pantry: List<PantryEntry> =
        listOf(
            PantryEntry(id = "fixture-pantry-atta", name = "Atta", group = "Staples"),
            PantryEntry(
                id = "fixture-pantry-masoor",
                name = "Masoor dal",
                quantityValue = 2.0,
                quantityUnit = "kg",
                group = "Staples",
            ),
            PantryEntry(id = "fixture-pantry-rajma", name = "Rajma (dry)", group = "Staples"),
            PantryEntry(id = "fixture-pantry-spinach", name = "Spinach", group = "Vegetables"),
            PantryEntry(id = "fixture-pantry-kheera", name = "Kheera (cucumber)", group = "Vegetables"),
            PantryEntry(id = "fixture-pantry-haldi", name = "Haldi (turmeric)", group = "Spices"),
            PantryEntry(id = "fixture-pantry-rai", name = "Rai (mustard seeds)", group = "Spices"),
            PantryEntry(id = "fixture-pantry-garam-masala", name = "Garam masala", group = "Spices"),
        )

    /** The canonical first suggestion (PRD §4, day 4). */
    val masoorDal =
        MealSuggestion(
            id = "fixture-suggestion-masoor-dal",
            mealName = "Masoor dal with spinach",
            description =
                "Masoor dal with spinach and a side of cucumber raita. It works with your " +
                    "diabetes — low glycemic load, plenty of fiber — and there's no onion or " +
                    "garlic in this version, so it's gentle on your stomach. Ready in about " +
                    "30 minutes. Want me to show you how I'd cook it?",
            constraintFit =
                listOf(
                    ConstraintFitNote(limitRefinedCarbs.id, "Low glycemic load, high fiber"),
                    ConstraintFitNote(avoidAlliumsDuringFlare.id, "No onion or garlic in this version"),
                    ConstraintFitNote(limitSodium.id, "Well under your sodium budget for the day"),
                ),
            ingredients =
                listOf("Masoor dal", "Spinach", "Haldi", "Jeera", "Ginger", "Dahi (for raita)", "Kheera"),
            approximateMinutes = 30,
        )

    val alooMethi =
        MealSuggestion(
            id = "fixture-suggestion-aloo-methi",
            mealName = "Aloo methi with phulka roti",
            description =
                "Aloo methi the way you'd make it at home, with fresh phulka. Uses what's " +
                    "already in your kitchen — no shopping trip needed tonight. About 35 minutes.",
            constraintFit =
                listOf(
                    ConstraintFitNote(preferAloo.id, "Aloo, like you like it"),
                    ConstraintFitNote(limitRefinedCarbs.id, "Whole-wheat atta keeps the carbs steady"),
                ),
            ingredients = listOf("Potatoes", "Methi", "Atta", "Haldi", "Rai", "Green chilli"),
            approximateMinutes = 35,
        )

    val rajmaBrownRice =
        MealSuggestion(
            id = "fixture-suggestion-rajma",
            mealName = "Rajma with brown basmati",
            description =
                "Sunday-style rajma, but with brown basmati so it sits better with your " +
                    "sugar. The family won't notice the difference; your glucometer will. " +
                    "About 45 minutes with soaked rajma.",
            constraintFit =
                listOf(
                    ConstraintFitNote(preferRajma.id, "The family favourite"),
                    ConstraintFitNote(limitRefinedCarbs.id, "Brown basmati instead of white rice"),
                ),
            ingredients = listOf("Rajma", "Brown basmati", "Tomatoes", "Ginger", "Garam masala"),
            approximateMinutes = 45,
        )

    val khichdi =
        MealSuggestion(
            id = "fixture-suggestion-khichdi",
            mealName = "Moong dal khichdi with kheera",
            description =
                "A gentle khichdi tonight — moong dal, a little ghee, kheera on the side. " +
                    "Easy on your stomach, steady for your sugar, and done in 25 minutes.",
            constraintFit =
                listOf(
                    ConstraintFitNote(avoidAlliumsDuringFlare.id, "Made without onion or garlic"),
                    ConstraintFitNote(limitRefinedCarbs.id, "Moong dal keeps it light and steady"),
                ),
            ingredients = listOf("Moong dal", "Rice", "Ghee", "Haldi", "Kheera"),
            approximateMinutes = 25,
            disclosureNotes = listOf("Suggested with your stomach in mind — you mentioned a flare today."),
        )

    /**
     * The scripted honest-conflict suggestion (`ui-ux-spec.md` §10: a
     * deterministic pass/fail script so the conflict surface can demo).
     * Served on the third request in a session.
     */
    val tuesdayFlareConflict =
        MealSuggestion(
            id = "fixture-suggestion-conflict",
            mealName = "",
            description = "",
            constraintFit = emptyList(),
            ingredients = emptyList(),
            approximateMinutes = 0,
            validation =
                SuggestionValidation.Conflict(
                    ConstraintConflict(
                        conflictingConstraintIds =
                            listOf(tuesdayVegetarian.id, avoidAlliumsDuringFlare.id, preferRajma.id),
                        explanation =
                            "Tonight is your vegetarian day, and you're avoiding onion and " +
                                "garlic while your stomach settles. Rajma the family way needs " +
                                "both. I can work around it — here are two honest options.",
                        options =
                            listOf(
                                ConflictOption(
                                    id = "fixture-conflict-opt-khichdi",
                                    label = "Keep everything — go gentler tonight",
                                    severityImplication =
                                        "Respects every constraint. A khichdi instead of rajma; " +
                                            "the rajma craving waits for a better day.",
                                ),
                                ConflictOption(
                                    id = "fixture-conflict-opt-no-tadka",
                                    label = "Rajma, but with a no-onion tadka",
                                    severityImplication =
                                        "Keeps Tuesday vegetarian and your stomach safe. It will " +
                                            "taste a little different from the family pattern — " +
                                            "your call, tonight only.",
                                ),
                            ),
                    ),
                ),
        )

    /** Request-cycle script for the mock Chef: index 2 (third request) is the conflict. */
    val suggestionCycle: List<MealSuggestion> =
        listOf(masoorDal, rajmaBrownRice, tuesdayFlareConflict, alooMethi, khichdi)

    /** Pre-saved history so Today/history surfaces render on first open (`ui-ux-spec.md` §10). */
    val savedSuggestions: List<MealSuggestion> = listOf(masoorDal, alooMethi)

    /** Fixed mock passphrase — clearly fake, never generated by real crypto. */
    val recoveryPassphrase =
        RecoveryPassphrase(
            words = listOf("copper", "lantern", "monsoon", "verandah", "cardamom", "harvest"),
        )
}
