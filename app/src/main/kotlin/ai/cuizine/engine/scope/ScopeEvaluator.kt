package ai.cuizine.engine.scope

import ai.cuizine.engine.types.ConstraintScope
import ai.cuizine.engine.types.TemporalScope
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Deterministic scope evaluation (`constraint-engine-spec.md` §5): a
 * constraint is active iff ALL five dimensions are true, evaluated in the
 * spec's order — temporal → contextual → location → household → profile.
 * Pure function; no LLM, no I/O. Returns attribution (the decisive dimension)
 * for the active-set audit metadata.
 */
class ScopeEvaluator(
    private val solarTimes: SolarTimesPort = ApproximateSolarTimes(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    data class Context(
        val at: ZonedDateTime,
        /** Flags of currently-active Type-5 contextual constraints. */
        val activeFlags: Set<String>,
        /** Current coarse location; nulls mean "at home / unspecified". */
        val country: String? = null,
        val region: String? = null,
        /** Profile-level named phases currently active (none in v1 personas). */
        val activePhases: Set<String> = emptySet(),
        val profileId: String,
    )

    data class Evaluation(
        val isActive: Boolean,
        /** The dimension that deactivated the constraint, or null if active. */
        val decisiveDimension: String? = null,
    )

    fun evaluate(
        scope: ConstraintScope,
        context: Context,
    ): Evaluation {
        if (!temporalIsActive(scope.temporal, context)) {
            return Evaluation(isActive = false, decisiveDimension = "temporal")
        }
        val contextual = scope.contextual
        if (!contextual.requiredFlags.all { it in context.activeFlags }) {
            return Evaluation(isActive = false, decisiveDimension = "contextual")
        }
        if (contextual.excludedFlags.any { it in context.activeFlags }) {
            return Evaluation(isActive = false, decisiveDimension = "contextual")
        }
        val country = scope.location.country
        if (country != null && !country.equals(context.country, ignoreCase = true)) {
            return Evaluation(isActive = false, decisiveDimension = "location")
        }
        val region = scope.location.region
        if (region != null && !region.equals(context.region, ignoreCase = true)) {
            return Evaluation(isActive = false, decisiveDimension = "location")
        }
        // Household: always passes in v1 (single profile) — the code path
        // exists for v2 readiness (`constraint-engine-spec.md` §5 Dimension 4).
        // Profile: the constraint belongs to the queried profile in v1.
        if (scope.profile.profileId != context.profileId) {
            return Evaluation(isActive = false, decisiveDimension = "profile")
        }
        return Evaluation(isActive = true)
    }

    private fun temporalIsActive(
        temporal: TemporalScope,
        context: Context,
    ): Boolean =
        when (temporal.kind) {
            "always" -> {
                true
            }

            "weekly" -> {
                val weekday =
                    context.at.dayOfWeek.name
                        .lowercase()
                temporal.weekdays.orEmpty().any { it.equals(weekday, ignoreCase = true) }
            }

            "daily_window" -> {
                isWithinDailyWindow(temporal, context)
            }

            "date_bounded" -> {
                val date = context.at.toLocalDate()
                val startsOk =
                    temporal.dateRangeStart?.let { date >= LocalDate.parse(it) } ?: true
                val endsOk =
                    temporal.dateRangeEnd?.let { date <= LocalDate.parse(it) } ?: true
                startsOk && endsOk
            }

            "phase_bounded" -> {
                temporal.phaseRef != null && temporal.phaseRef in context.activePhases
            }

            "composite" -> {
                val parts =
                    temporal.compositeParts.orEmpty().map { part ->
                        json.decodeFromString<TemporalScope>(part.toString())
                    }
                when (temporal.compositeOperator) {
                    "or" -> parts.any { temporalIsActive(it, context) }

                    // AND is the default composite semantics.
                    else -> parts.all { temporalIsActive(it, context) }
                }
            }

            else -> {
                throw IllegalArgumentException("Unknown temporal scope kind: ${temporal.kind}")
            }
        }

    private fun isWithinDailyWindow(
        temporal: TemporalScope,
        context: Context,
    ): Boolean {
        val time = context.at.toLocalTime()
        val (start, end) =
            when (temporal.dailyWindowBasis) {
                "solar_sunrise", "solar_sunset", "solar" -> {
                    val solar = solarTimes.solarTimes(context.at.toLocalDate(), context.at.zone)
                    solar.sunrise to solar.sunset
                }

                else -> {
                    LocalTime.parse(temporal.dailyWindowStart ?: "00:00:00") to
                        LocalTime.parse(temporal.dailyWindowEnd ?: "23:59:59")
                }
            }
        return if (start <= end) {
            time >= start && time <= end
        } else {
            // Overnight window (e.g. 22:00–06:00).
            time >= start || time <= end
        }
    }
}

/** Sunrise/sunset for a date in a zone. Injected so tests are deterministic. */
fun interface SolarTimesPort {
    fun solarTimes(
        date: LocalDate,
        zone: ZoneId,
    ): SolarWindow
}

data class SolarWindow(
    val sunrise: LocalTime,
    val sunset: LocalTime,
)

/**
 * v1 solar approximation. The spec computes sunrise/sunset "from the user's
 * location", but v1 location is coarse (country/region — no coordinates;
 * DECISION-LOG #3a). Approximation: representative coordinates per IANA zone
 * for the v1 (Canadian) market via the NOAA algorithm; zones without an entry
 * fall back to a fixed 06:00–18:00 window. Deterministic either way.
 */
class ApproximateSolarTimes : SolarTimesPort {
    override fun solarTimes(
        date: LocalDate,
        zone: ZoneId,
    ): SolarWindow {
        val coords = ZONE_COORDINATES[zone.id] ?: return SolarWindow(LocalTime.of(6, 0), LocalTime.of(18, 0))
        return noaaSolarWindow(date, zone, coords.first, coords.second)
    }

    private fun noaaSolarWindow(
        date: LocalDate,
        zone: ZoneId,
        latitude: Double,
        longitude: Double,
    ): SolarWindow {
        // NOAA solar position approximation (good to ~2 minutes).
        val dayOfYear = date.dayOfYear.toDouble()
        val gamma = 2.0 * Math.PI / 365.0 * (dayOfYear - 1)
        val eqTimeMinutes =
            229.18 * (
                0.000075 + 0.001868 * Math.cos(gamma) - 0.032077 * Math.sin(gamma) -
                    0.014615 * Math.cos(2 * gamma) - 0.040849 * Math.sin(2 * gamma)
            )
        val declination =
            0.006918 - 0.399912 * Math.cos(gamma) + 0.070257 * Math.sin(gamma) -
                0.006758 * Math.cos(2 * gamma) + 0.000907 * Math.sin(2 * gamma) -
                0.002697 * Math.cos(3 * gamma) + 0.00148 * Math.sin(3 * gamma)
        val latRad = Math.toRadians(latitude)
        val cosHourAngle =
            (Math.cos(Math.toRadians(90.833)) - Math.sin(latRad) * Math.sin(declination)) /
                (Math.cos(latRad) * Math.cos(declination))
        if (cosHourAngle > 1.0 || cosHourAngle < -1.0) {
            // Polar day/night: degrade to the fixed window.
            return SolarWindow(LocalTime.of(6, 0), LocalTime.of(18, 0))
        }
        val hourAngleDeg = Math.toDegrees(Math.acos(cosHourAngle))
        val zoneOffsetMinutes =
            zone.rules.getOffset(date.atStartOfDay(zone).toInstant()).totalSeconds / 60.0
        val sunriseMinutesUtc = 720.0 - 4.0 * (longitude + hourAngleDeg) - eqTimeMinutes
        val sunsetMinutesUtc = 720.0 - 4.0 * (longitude - hourAngleDeg) - eqTimeMinutes
        return SolarWindow(
            sunrise = minutesToLocalTime(sunriseMinutesUtc + zoneOffsetMinutes),
            sunset = minutesToLocalTime(sunsetMinutesUtc + zoneOffsetMinutes),
        )
    }

    private fun minutesToLocalTime(minutes: Double): LocalTime {
        val wrapped = ((minutes % 1440.0) + 1440.0) % 1440.0
        return LocalTime.of((wrapped / 60).toInt(), (wrapped % 60).toInt())
    }

    private companion object {
        /** Representative coordinates per IANA zone for the v1 market. */
        val ZONE_COORDINATES: Map<String, Pair<Double, Double>> =
            mapOf(
                "America/Toronto" to (43.7 to -79.4),
                "America/Montreal" to (45.5 to -73.6),
                "America/Vancouver" to (49.3 to -123.1),
                "America/Edmonton" to (53.5 to -113.5),
                "America/Winnipeg" to (49.9 to -97.1),
                "America/Halifax" to (44.6 to -63.6),
                "America/St_Johns" to (47.6 to -52.7),
                "America/Regina" to (50.4 to -104.6),
            )
    }
}
