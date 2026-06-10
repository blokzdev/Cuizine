package ai.cuizine.engine.types

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * The five scope dimensions for `constraints.scope_json` (`data-model.md` §4,
 * `constraint-engine-spec.md` §5): temporal, contextual, location, household,
 * profile. Scope *evaluation* lives in `engine/scope/` (Phase 3); these are
 * the persistent shapes.
 */
@Serializable
data class ConstraintScope(
    val temporal: TemporalScope,
    val contextual: ContextualScopeSpec = ContextualScopeSpec(),
    val location: LocationScope = LocationScope(),
    val household: HouseholdScope,
    val profile: ProfileScope,
)

/**
 * The six temporal kinds: always, weekly, daily_window, date_bounded,
 * phase_bounded, composite. Optional fields apply per kind; `composite_parts`
 * holds nested temporal scopes combined with `composite_operator`.
 */
@Serializable
data class TemporalScope(
    val kind: String,
    val weekdays: List<String>? = null,
    @SerialName("daily_window_start") val dailyWindowStart: String? = null,
    @SerialName("daily_window_end") val dailyWindowEnd: String? = null,
    @SerialName("daily_window_basis") val dailyWindowBasis: String? = null,
    @SerialName("date_range_start") val dateRangeStart: String? = null,
    @SerialName("date_range_end") val dateRangeEnd: String? = null,
    @SerialName("phase_ref") val phaseRef: String? = null,
    @SerialName("composite_operator") val compositeOperator: String? = null,
    @SerialName("composite_parts") val compositeParts: List<JsonObject>? = null,
)

@Serializable
data class ContextualScopeSpec(
    @SerialName("required_flags") val requiredFlags: List<String> = emptyList(),
    @SerialName("excluded_flags") val excludedFlags: List<String> = emptyList(),
)

@Serializable
data class LocationScope(
    /** null means "anywhere". */
    val country: String? = null,
    val region: String? = null,
)

@Serializable
data class HouseholdScope(
    val mode: String,
    @SerialName("profile_ids") val profileIds: List<String>? = null,
)

@Serializable
data class ProfileScope(
    /** Always the owning profile in v1. */
    @SerialName("profile_id") val profileId: String,
)
