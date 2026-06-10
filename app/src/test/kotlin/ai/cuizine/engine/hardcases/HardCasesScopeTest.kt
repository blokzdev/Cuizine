package ai.cuizine.engine.hardcases

import ai.cuizine.engine.ConstraintGraphEngine
import ai.cuizine.engine.ports.ConstraintStore
import ai.cuizine.engine.ports.EngineClock
import ai.cuizine.engine.ports.FoodDataPort
import ai.cuizine.engine.ports.IngredientFacts
import ai.cuizine.engine.ports.IngredientResolution
import ai.cuizine.engine.scope.ApproximateSolarTimes
import ai.cuizine.engine.scope.ScopeEvaluator
import ai.cuizine.engine.scope.SolarTimesPort
import ai.cuizine.engine.scope.SolarWindow
import ai.cuizine.engine.types.ConstraintScope
import ai.cuizine.engine.types.ContextualScopeSpec
import ai.cuizine.engine.types.HouseholdScope
import ai.cuizine.engine.types.LocationScope
import ai.cuizine.engine.types.ProfileScope
import ai.cuizine.engine.types.TemporalScope
import ai.cuizine.shared.fixtures.AishaFixtures
import ai.cuizine.shared.fixtures.SukhiFixtures
import ai.cuizine.shared.types.Constraint
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Hard-cases scope slice (`testing-strategy.md` §5 Category 3;
 * `constraint-engine-spec.md` §5): the six temporal kinds, DST transitions in
 * America/Toronto 2026, solar daily windows (exact fixture + the v1
 * approximation fallback), contextual required/excluded flags, and location
 * scope. Fine-grained cases hit [ScopeEvaluator] directly; the local-day
 * boundary and DST weekday cases go through the full engine because the
 * instant-to-profile-timezone conversion is the behavior under test.
 */
class HardCasesScopeTest {
    // ── Test ports (canonical pattern) ───────────────────────────────────

    private class InMemoryStore : ConstraintStore {
        val rows = mutableMapOf<String, Constraint>()
        val removed = mutableSetOf<String>()

        override suspend fun loadLive(profileId: String) =
            rows.values.filter { it.profileId == profileId && it.id !in removed }

        override suspend fun loadAll(profileId: String) = rows.values.filter { it.profileId == profileId }

        override suspend fun loadById(constraintId: String) = rows[constraintId]

        override suspend fun insert(constraint: Constraint) {
            rows[constraint.id] = constraint
        }

        override suspend fun update(constraint: Constraint) {
            rows[constraint.id] = constraint
        }

        override suspend fun markRemoved(
            constraintId: String,
            removedAtIso: String,
        ) {
            removed += constraintId
        }
    }

    /** Scope tests never resolve ingredients; the port only satisfies the engine wiring. */
    private class FixtureFoodData : FoodDataPort {
        override suspend fun lookup(ingredientName: String): IngredientResolution = IngredientResolution.Unknown

        override suspend fun categorizeWithAi(ingredientName: String): IngredientFacts? = null
    }

    private val store = InMemoryStore()
    private val fixedClock = EngineClock { "2026-06-09T22:00:00Z" } // a Tuesday evening in Toronto

    private fun engine(timezone: String = "America/Toronto") =
        ConstraintGraphEngine(
            store = store,
            foodData = FixtureFoodData(),
            clock = fixedClock,
            profileTimezone = { timezone },
        )

    private fun seedSukhi() {
        SukhiFixtures.constraints.forEach { store.rows[it.id] = it }
    }

    // ── Evaluator fixtures ───────────────────────────────────────────────

    private val toronto = ZoneId.of("America/Toronto")
    private val json = Json
    private val evaluator = ScopeEvaluator()

    /** Exact solar fixture per the task brief: sunrise 06:30, sunset 19:45 — deterministic. */
    private val solarFixture =
        SolarTimesPort { _, _ -> SolarWindow(sunrise = LocalTime.of(6, 30), sunset = LocalTime.of(19, 45)) }
    private val solarEvaluator = ScopeEvaluator(solarTimes = solarFixture)

    private fun scopeOf(
        temporal: TemporalScope = TemporalScope(kind = "always"),
        contextual: ContextualScopeSpec = ContextualScopeSpec(),
        location: LocationScope = LocationScope(),
    ) = ConstraintScope(
        temporal = temporal,
        contextual = contextual,
        location = location,
        household = HouseholdScope(mode = "self"),
        profile = ProfileScope(profileId = SukhiFixtures.PROFILE_ID),
    )

    private fun contextAt(
        at: ZonedDateTime,
        flags: Set<String> = emptySet(),
        country: String? = null,
        region: String? = null,
        phases: Set<String> = emptySet(),
    ) = ScopeEvaluator.Context(
        at = at,
        activeFlags = flags,
        country = country,
        region = region,
        activePhases = phases,
        profileId = SukhiFixtures.PROFILE_ID,
    )

    private fun torontoLocal(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ) = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, toronto)

    private fun torontoInstant(iso: String) = Instant.parse(iso).atZone(toronto)

    private fun clockWindow(
        start: String,
        end: String,
    ) = TemporalScope(kind = "daily_window", dailyWindowStart = start, dailyWindowEnd = end)

    private val solarDailyWindow = TemporalScope(kind = "daily_window", dailyWindowBasis = "solar")

    private val ramadanDates =
        TemporalScope(
            kind = "date_bounded",
            dateRangeStart = AishaFixtures.RAMADAN_START,
            dateRangeEnd = AishaFixtures.RAMADAN_END,
        )

    private val firstTrimester = TemporalScope(kind = "phase_bounded", phaseRef = "first_trimester")

    /** Composite OR: weekly Tuesday OR a December date range (§5 Dimension 1, Composite). */
    private val tuesdayOrDecember =
        TemporalScope(
            kind = "composite",
            compositeOperator = "or",
            compositeParts =
                listOf(
                    json.decodeFromString<JsonObject>("""{"kind":"weekly","weekdays":["tuesday"]}"""),
                    json.decodeFromString<JsonObject>(
                        """{"kind":"date_bounded","date_range_start":"2026-12-01","date_range_end":"2026-12-31"}""",
                    ),
                ),
        )

    // ── Temporal: always (§5 Dimension 1, "Always — the default") ───────

    @Test
    fun always_constraintIsActiveAtAnArbitraryMoment() {
        val result = evaluator.evaluate(scopeOf(), contextAt(torontoLocal(2026, 6, 9, 13, 0)))
        assertTrue(result.isActive)
    }

    // ── Temporal: daily_window, clock basis (§5 Dimension 1) ────────────

    @Test
    fun dailyWindow_clockBasedActiveInsideWindow() {
        val scope = scopeOf(temporal = clockWindow("12:00", "14:00"))
        assertTrue(evaluator.evaluate(scope, contextAt(torontoLocal(2026, 6, 9, 13, 0))).isActive)
    }

    @Test
    fun dailyWindow_clockBasedInactiveOutsideWindow() {
        val scope = scopeOf(temporal = clockWindow("12:00", "14:00"))
        val result = evaluator.evaluate(scope, contextAt(torontoLocal(2026, 6, 9, 15, 0)))
        assertFalse(result.isActive)
        assertEquals("temporal", result.decisiveDimension)
    }

    @Test
    fun dailyWindow_overnightWindowActiveBeforeMidnight() {
        // Overnight 22:00–06:00 crosses midnight: late evening sits inside it.
        val scope = scopeOf(temporal = clockWindow("22:00", "06:00"))
        assertTrue(evaluator.evaluate(scope, contextAt(torontoLocal(2026, 6, 9, 23, 30))).isActive)
    }

    @Test
    fun dailyWindow_overnightWindowActiveAfterMidnight() {
        val scope = scopeOf(temporal = clockWindow("22:00", "06:00"))
        assertTrue(evaluator.evaluate(scope, contextAt(torontoLocal(2026, 6, 10, 3, 0))).isActive)
    }

    @Test
    fun dailyWindow_overnightWindowInactiveMidday() {
        val scope = scopeOf(temporal = clockWindow("22:00", "06:00"))
        assertFalse(evaluator.evaluate(scope, contextAt(torontoLocal(2026, 6, 9, 12, 0))).isActive)
    }

    // ── Temporal: date_bounded (§5 Dimension 1, optional open ends) ─────

    @Test
    fun dateBounded_activeInsideRange() {
        val scope = scopeOf(temporal = ramadanDates)
        assertTrue(evaluator.evaluate(scope, contextAt(torontoLocal(2026, 3, 2, 12, 0))).isActive)
    }

    @Test
    fun dateBounded_inactiveBeforeStart() {
        val scope = scopeOf(temporal = ramadanDates)
        assertFalse(evaluator.evaluate(scope, contextAt(torontoLocal(2026, 2, 17, 12, 0))).isActive)
    }

    @Test
    fun dateBounded_inactiveAfterEnd() {
        val scope = scopeOf(temporal = ramadanDates)
        assertFalse(evaluator.evaluate(scope, contextAt(torontoLocal(2026, 3, 20, 12, 0))).isActive)
    }

    @Test
    fun dateBounded_openStartActiveAnytimeBeforeEnd() {
        val scope = scopeOf(temporal = TemporalScope(kind = "date_bounded", dateRangeEnd = "2026-06-30"))
        assertTrue(evaluator.evaluate(scope, contextAt(torontoLocal(2024, 1, 1, 9, 0))).isActive)
    }

    @Test
    fun dateBounded_openEndActiveAnytimeAfterStart() {
        val scope = scopeOf(temporal = TemporalScope(kind = "date_bounded", dateRangeStart = "2026-06-01"))
        assertTrue(evaluator.evaluate(scope, contextAt(torontoLocal(2030, 12, 31, 9, 0))).isActive)
    }

    // ── Temporal: phase_bounded (§5 Dimension 1, profile-level phases) ──

    @Test
    fun phaseBounded_inactiveWhenPhaseAbsent() {
        val result =
            evaluator.evaluate(
                scopeOf(temporal = firstTrimester),
                contextAt(torontoLocal(2026, 6, 9, 12, 0)),
            )
        assertFalse(result.isActive)
        assertEquals("temporal", result.decisiveDimension)
    }

    @Test
    fun phaseBounded_activeWhenPhasePresentInContext() {
        val result =
            evaluator.evaluate(
                scopeOf(temporal = firstTrimester),
                contextAt(torontoLocal(2026, 6, 9, 12, 0), phases = setOf("first_trimester")),
            )
        assertTrue(result.isActive)
    }

    // ── Temporal: composite AND / OR (§5 Dimension 1, Composite) ────────

    @Test
    fun compositeAnd_activeWhenDateAndSolarPartsBothTrue() {
        // Aisha's Ramadan window: date-bounded AND solar daily window (§5).
        val result =
            solarEvaluator.evaluate(
                scopeOf(temporal = AishaFixtures.ramadanFastingWindow),
                contextAt(torontoLocal(2026, 3, 2, 12, 0)),
            )
        assertTrue(result.isActive)
    }

    @Test
    fun compositeAnd_inactiveWhenDatePartFalse() {
        // Midday sun, but after Ramadan's end: AND fails on the date part.
        val result =
            solarEvaluator.evaluate(
                scopeOf(temporal = AishaFixtures.ramadanFastingWindow),
                contextAt(torontoLocal(2026, 4, 10, 12, 0)),
            )
        assertFalse(result.isActive)
    }

    @Test
    fun compositeAnd_inactiveWhenSolarWindowPartFalse() {
        // Mid-Ramadan, but 23:00 is past the 19:45 fixture sunset.
        val result =
            solarEvaluator.evaluate(
                scopeOf(temporal = AishaFixtures.ramadanFastingWindow),
                contextAt(torontoLocal(2026, 3, 2, 23, 0)),
            )
        assertFalse(result.isActive)
    }

    @Test
    fun compositeOr_activeWhenOnlyOnePartTrue() {
        // 2026-06-09 is a Tuesday but outside the December range: OR passes.
        val result =
            evaluator.evaluate(
                scopeOf(temporal = tuesdayOrDecember),
                contextAt(torontoLocal(2026, 6, 9, 12, 0)),
            )
        assertTrue(result.isActive)
    }

    // ── DST transitions, America/Toronto 2026 (§5: "evaluated against the
    //    user's timezone") ─────────────────────────────────────────────────

    @Test
    fun dst_springForwardSkippedWindowHasNoInteriorActivation() {
        // 2026-03-08: 02:00 EST jumps to 03:00 EDT, so the 02:00–03:00
        // wall-clock hour never occurs — no interior moment activates.
        val scope = scopeOf(temporal = clockWindow("02:00", "03:00"))
        // 06:59Z = 01:59 EST, one minute before the jump.
        assertFalse(evaluator.evaluate(scope, contextAt(torontoInstant("2026-03-08T06:59:00Z"))).isActive)
        // 07:01Z = 03:01 EDT, one minute after landing past the window.
        assertFalse(evaluator.evaluate(scope, contextAt(torontoInstant("2026-03-08T07:01:00Z"))).isActive)
    }

    @Test
    fun dst_fallBackRepeatedHourActivatesWindowInBothPasses() {
        // 2026-11-01: 01:30 local occurs twice (EDT then EST). Both UTC
        // instants sit inside the 01:00–02:00 wall-clock window.
        val scope = scopeOf(temporal = clockWindow("01:00", "02:00"))
        assertTrue(evaluator.evaluate(scope, contextAt(torontoInstant("2026-11-01T05:30:00Z"))).isActive)
        assertTrue(evaluator.evaluate(scope, contextAt(torontoInstant("2026-11-01T06:30:00Z"))).isActive)
    }

    // ── Solar daily windows (§5: sunrise/sunset computed, not hardcoded) ──

    @Test
    fun solar_windowActiveAtMiddayBetweenFixtureSunriseAndSunset() {
        val scope = scopeOf(temporal = solarDailyWindow)
        assertTrue(solarEvaluator.evaluate(scope, contextAt(torontoLocal(2026, 3, 2, 12, 0))).isActive)
    }

    @Test
    fun solar_windowInactiveBeforeFixtureSunrise() {
        // 05:00 is before the 06:30 fixture sunrise.
        val scope = scopeOf(temporal = solarDailyWindow)
        assertFalse(solarEvaluator.evaluate(scope, contextAt(torontoLocal(2026, 3, 2, 5, 0))).isActive)
    }

    @Test
    fun solar_windowInactiveAfterFixtureSunset() {
        // 20:30 is after the 19:45 fixture sunset.
        val scope = scopeOf(temporal = solarDailyWindow)
        assertFalse(solarEvaluator.evaluate(scope, contextAt(torontoLocal(2026, 3, 2, 20, 30))).isActive)
    }

    @Test
    fun solar_unknownZoneApproximationFallsBackToSixToSixWindow() {
        // ApproximateSolarTimes has no coordinates for Pacific/Kiritimati.
        val window =
            ApproximateSolarTimes().solarTimes(LocalDate.of(2026, 6, 10), ZoneId.of("Pacific/Kiritimati"))
        assertEquals(SolarWindow(sunrise = LocalTime.of(6, 0), sunset = LocalTime.of(18, 0)), window)
    }

    @Test
    fun solar_unknownZoneEvaluationUsesFallbackWindow() {
        // Europe/Paris is outside the v1 coordinate table: 06:00–18:00 applies.
        val paris = ZoneId.of("Europe/Paris")
        val scope = scopeOf(temporal = solarDailyWindow)
        assertTrue(evaluator.evaluate(scope, contextAt(ZonedDateTime.of(2026, 6, 10, 12, 0, 0, 0, paris))).isActive)
        assertFalse(evaluator.evaluate(scope, contextAt(ZonedDateTime.of(2026, 6, 10, 5, 0, 0, 0, paris))).isActive)
    }

    // ── Contextual scope (§5 Dimension 2: requires-flag / excludes-flag) ──

    @Test
    fun contextual_requiredFlagPresentActivates() {
        val scope = scopeOf(contextual = ContextualScopeSpec(requiredFlags = listOf("ibs_flare")))
        val context = contextAt(torontoLocal(2026, 6, 9, 12, 0), flags = setOf("ibs_flare"))
        assertTrue(evaluator.evaluate(scope, context).isActive)
    }

    @Test
    fun contextual_requiredFlagAbsentDeactivates() {
        val scope = scopeOf(contextual = ContextualScopeSpec(requiredFlags = listOf("ibs_flare")))
        val result = evaluator.evaluate(scope, contextAt(torontoLocal(2026, 6, 9, 12, 0)))
        assertFalse(result.isActive)
        assertEquals("contextual", result.decisiveDimension)
    }

    @Test
    fun contextual_excludedFlagPresentBlocks() {
        val scope = scopeOf(contextual = ContextualScopeSpec(excludedFlags = listOf("traveling")))
        val context = contextAt(torontoLocal(2026, 6, 9, 12, 0), flags = setOf("traveling"))
        val result = evaluator.evaluate(scope, context)
        assertFalse(result.isActive)
        assertEquals("contextual", result.decisiveDimension)
    }

    @Test
    fun contextual_requiredAndExcludedFlagsComposeIndependently() {
        // §5 Dimension 2: required flag must be present AND excluded absent.
        val scope =
            scopeOf(
                contextual =
                    ContextualScopeSpec(
                        requiredFlags = listOf("ibs_flare"),
                        excludedFlags = listOf("traveling"),
                    ),
            )
        val flareOnly = contextAt(torontoLocal(2026, 6, 9, 12, 0), flags = setOf("ibs_flare"))
        assertTrue(evaluator.evaluate(scope, flareOnly).isActive)
        val flareWhileTraveling =
            contextAt(torontoLocal(2026, 6, 9, 12, 0), flags = setOf("ibs_flare", "traveling"))
        assertFalse(evaluator.evaluate(scope, flareWhileTraveling).isActive)
    }

    // ── Location scope (§5 Dimension 3: NULL means "anywhere") ──────────

    @Test
    fun location_countryMatchPasses() {
        val scope = scopeOf(location = LocationScope(country = "CA"))
        assertTrue(evaluator.evaluate(scope, contextAt(torontoLocal(2026, 6, 9, 12, 0), country = "CA")).isActive)
    }

    @Test
    fun location_countryMismatchDeactivates() {
        val scope = scopeOf(location = LocationScope(country = "CA"))
        val result = evaluator.evaluate(scope, contextAt(torontoLocal(2026, 6, 9, 12, 0), country = "US"))
        assertFalse(result.isActive)
        assertEquals("location", result.decisiveDimension)
    }

    @Test
    fun location_nullCountryMeansAnywhere() {
        // §5 Dimension 3: most v1 constraints default to location = anywhere.
        val scope = scopeOf(location = LocationScope())
        assertTrue(evaluator.evaluate(scope, contextAt(torontoLocal(2026, 6, 9, 12, 0), country = "FR")).isActive)
    }

    @Test
    fun location_regionMatchPasses() {
        val scope = scopeOf(location = LocationScope(country = "CA", region = "Ontario"))
        val result =
            evaluator.evaluate(
                scope,
                contextAt(torontoLocal(2026, 6, 9, 12, 0), country = "CA", region = "Ontario"),
            )
        assertTrue(result.isActive)
    }

    @Test
    fun location_regionMismatchDeactivatesEvenWhenCountryMatches() {
        val scope = scopeOf(location = LocationScope(country = "CA", region = "Ontario"))
        val result =
            evaluator.evaluate(
                scope,
                contextAt(torontoLocal(2026, 6, 9, 12, 0), country = "CA", region = "Quebec"),
            )
        assertFalse(result.isActive)
        assertEquals("location", result.decisiveDimension)
    }

    @Test
    fun location_nullRegionMeansAnywhereWithinCountry() {
        val scope = scopeOf(location = LocationScope(country = "CA"))
        val result =
            evaluator.evaluate(
                scope,
                contextAt(torontoLocal(2026, 6, 9, 12, 0), country = "CA", region = "Alberta"),
            )
        assertTrue(result.isActive)
    }

    // ── Engine integration: weekly boundary + DST weekday via queryActive
    //    (§5 "How scope composes"; profile timezone governs the local day) ──

    @Test
    fun engine_weeklyTuesdayStillActiveAt2359LocalDespiteUtcWednesday() =
        runTest {
            seedSukhi()
            // 2026-06-10T03:59Z is Wednesday in UTC but Tuesday 23:59 in Toronto (EDT).
            val active = engine().queryActive(SukhiFixtures.PROFILE_ID, "2026-06-10T03:59:00Z")
            assertTrue(active.constraints.any { it.id == SukhiFixtures.tuesdayVegetarian.id })
        }

    @Test
    fun engine_weeklyTuesdayInactiveAt0001LocalWednesday() =
        runTest {
            seedSukhi()
            // Two minutes later in UTC, the LOCAL day has rolled to Wednesday 00:01.
            val active = engine().queryActive(SukhiFixtures.PROFILE_ID, "2026-06-10T04:01:00Z")
            assertFalse(active.constraints.any { it.id == SukhiFixtures.tuesdayVegetarian.id })
        }

    /** Sunday-weekly variant built with the fixture helper style (never a new persona). */
    private val sundayVegetarian =
        SukhiFixtures.tuesdayVegetarian.copy(
            id = "fixture-constraint-sunday-veg",
            scope =
                SukhiFixtures.tuesdayVegetarian.scope.copy(
                    temporal = TemporalScope(kind = "weekly", weekdays = listOf("sunday")),
                ),
        )

    @Test
    fun engine_dstSpringForwardWeeklyWeekdayGovernedByLocalDate() =
        runTest {
            store.rows[sundayVegetarian.id] = sundayVegetarian
            val subject = engine()
            // 2026-03-09T03:30Z = Sunday 2026-03-08 23:30 EDT, the spring-forward day.
            val lateSunday = subject.queryActive(SukhiFixtures.PROFILE_ID, "2026-03-09T03:30:00Z")
            assertTrue(lateSunday.constraints.any { it.id == sundayVegetarian.id })
            // 2026-03-09T05:30Z = Monday 01:30 EDT: the local date governs.
            val monday = subject.queryActive(SukhiFixtures.PROFILE_ID, "2026-03-09T05:30:00Z")
            assertFalse(monday.constraints.any { it.id == sundayVegetarian.id })
        }

    @Test
    fun engine_dstFallBackLocalSundayExtendsPastUtcMidnight() =
        runTest {
            store.rows[sundayVegetarian.id] = sundayVegetarian
            val subject = engine()
            // Fall back makes Sunday 2026-11-01 a 25-hour local day:
            // 2026-11-02T04:30Z is still Sunday 23:30 EST in Toronto.
            val lateSunday = subject.queryActive(SukhiFixtures.PROFILE_ID, "2026-11-02T04:30:00Z")
            assertTrue(lateSunday.constraints.any { it.id == sundayVegetarian.id })
            // 2026-11-02T05:30Z = Monday 00:30 EST: Sunday is over locally too.
            val monday = subject.queryActive(SukhiFixtures.PROFILE_ID, "2026-11-02T05:30:00Z")
            assertFalse(monday.constraints.any { it.id == sundayVegetarian.id })
        }
}
