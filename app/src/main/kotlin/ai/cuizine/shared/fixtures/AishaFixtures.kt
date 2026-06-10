package ai.cuizine.shared.fixtures

import ai.cuizine.engine.types.AddedContext
import ai.cuizine.engine.types.AvoidPayload
import ai.cuizine.engine.types.AvoidTarget
import ai.cuizine.engine.types.ConstraintScope
import ai.cuizine.engine.types.ConstraintType
import ai.cuizine.engine.types.ContextualPayload
import ai.cuizine.engine.types.ContextualState
import ai.cuizine.engine.types.HouseholdScope
import ai.cuizine.engine.types.ProfileScope
import ai.cuizine.engine.types.ProvenanceRecord
import ai.cuizine.engine.types.RequirePayload
import ai.cuizine.engine.types.RequireTarget
import ai.cuizine.engine.types.Severity
import ai.cuizine.engine.types.TemporalScope
import ai.cuizine.shared.types.Constraint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * The canonical Aisha fixture (`testing-strategy.md` §5): a Muslim Canadian
 * observing Ramadan — the temporal-scope workhorse. Exercises composite
 * scopes (date-bounded AND solar daily window), lifelong religious
 * observance, and the fasting state's `incompatible_with` semantics.
 */
object AishaFixtures {
    const val PROFILE_ID = "fixture-profile-aisha"
    const val TIMEZONE = "America/Toronto"

    // Ramadan 2026 (approximate civil dates; exact dates are fixture data,
    // not religious authority).
    const val RAMADAN_START = "2026-02-18"
    const val RAMADAN_END = "2026-03-19"

    private val json = Json

    private fun provenance(phrasing: String) =
        ProvenanceRecord(
            source = "user_direct",
            addedAt = "2026-02-01T18:00:00Z",
            addedContext = AddedContext(flow = "constraint_conversation", questionIndex = 3),
            originalPhrasing = phrasing,
            confidence = "high_user_direct",
        )

    private fun scope(temporal: TemporalScope) =
        ConstraintScope(
            temporal = temporal,
            household = HouseholdScope(mode = "self"),
            profile = ProfileScope(profileId = PROFILE_ID),
        )

    /** Composite: date-bounded Ramadan AND solar daily window (§5 Dimension 1). */
    val ramadanFastingWindow =
        TemporalScope(
            kind = "composite",
            compositeOperator = "and",
            compositeParts =
                listOf(
                    json.decodeFromString<JsonObject>(
                        """{"kind":"date_bounded","date_range_start":"$RAMADAN_START","date_range_end":"$RAMADAN_END"}""",
                    ),
                    json.decodeFromString<JsonObject>(
                        """{"kind":"daily_window","daily_window_basis":"solar"}""",
                    ),
                ),
        )

    /** Lifelong halal observance — Religious & Cultural, always active. */
    val halalObservance =
        Constraint(
            id = "fixture-constraint-aisha-halal",
            profileId = PROFILE_ID,
            type = ConstraintType.Avoid,
            severity = Severity.ReligiousCultural,
            humanLabel = "Halal — always",
            scope = scope(TemporalScope(kind = "always")),
            payload = AvoidPayload(target = AvoidTarget(kind = "category", value = "non_halal")),
            provenance = provenance("We keep halal, always have"),
        )

    /** The fast itself: active only in the composite window; incompatible with any meal. */
    val ramadanFast =
        Constraint(
            id = "fixture-constraint-aisha-fast",
            profileId = PROFILE_ID,
            type = ConstraintType.Contextual,
            severity = Severity.ReligiousCultural,
            humanLabel = "Fasting, sunrise to sunset, through Ramadan",
            scope = scope(ramadanFastingWindow),
            payload =
                ContextualPayload(
                    state =
                        ContextualState(
                            flag = "ramadan_fasting",
                            incompatibleWith = listOf("any_meal"),
                        ),
                ),
            provenance = provenance("I'm fasting for Ramadan — no food between sunrise and sunset"),
        )

    /** Iftar: hydrating foods required at the evening meal during Ramadan. */
    val iftarHydration =
        Constraint(
            id = "fixture-constraint-aisha-iftar",
            profileId = PROFILE_ID,
            type = ConstraintType.Require,
            severity = Severity.Preference,
            humanLabel = "Something hydrating at iftar",
            scope =
                scope(
                    TemporalScope(
                        kind = "date_bounded",
                        dateRangeStart = RAMADAN_START,
                        dateRangeEnd = RAMADAN_END,
                    ),
                ),
            payload =
                RequirePayload(
                    target = RequireTarget(kind = "category", value = "hydrating"),
                    window = "single_meal",
                ),
            provenance = provenance("After the fast we start with something hydrating"),
        )

    val constraints: List<Constraint> = listOf(halalObservance, ramadanFast, iftarHydration)
}
