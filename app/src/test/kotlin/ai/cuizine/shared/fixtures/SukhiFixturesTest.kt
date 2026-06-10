package ai.cuizine.shared.fixtures

import ai.cuizine.engine.types.Severity
import ai.cuizine.shared.types.SuggestionValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keeps the canonical fixture internally coherent — every screen mock and
 * later hard-cases test builds on these invariants.
 */
class SukhiFixturesTest {
    @Test
    fun constraintGraph_matchesTestingStrategyShape() {
        // No inviolable constraints for Sukhi (testing-strategy.md §5).
        assertTrue(SukhiFixtures.constraints.none { it.severity == Severity.Inviolable })
        // Medical, religious-cultural, and preference tiers all present.
        assertEquals(
            setOf(Severity.Medical, Severity.ReligiousCultural, Severity.Preference),
            SukhiFixtures.constraints.map { it.severity }.toSet(),
        )
        // The flare-scoped allium avoidance is contextual and inactive by default.
        assertTrue(!SukhiFixtures.avoidAlliumsDuringFlare.isActiveNow)
        assertEquals(
            listOf("ibs_flare"),
            SukhiFixtures.avoidAlliumsDuringFlare.scope.contextual.requiredFlags,
        )
    }

    @Test
    fun suggestionFitNotes_referenceRealConstraints() {
        val constraintIds = SukhiFixtures.constraints.map { it.id }.toSet()
        SukhiFixtures.suggestionCycle
            .filter { it.validation == SuggestionValidation.Passed }
            .flatMap { it.constraintFit }
            .forEach { note ->
                assertTrue(
                    "fit note references unknown constraint ${note.constraintId}",
                    note.constraintId in constraintIds,
                )
            }
    }

    @Test
    fun conflictScript_isDeterministicAndHonest() {
        // Third request in a session hits the conflict (ui-ux-spec.md §10).
        val third = SukhiFixtures.suggestionCycle[2]
        val conflict = (third.validation as SuggestionValidation.Conflict).conflict
        assertTrue(conflict.options.size >= 2)
        // No option ever relaxes a medical or inviolable constraint silently —
        // the options are phrased against preferences/traditions only.
        val conflictIds = conflict.conflictingConstraintIds.toSet()
        assertTrue(SukhiFixtures.preferRajma.id in conflictIds)
    }

    @Test
    fun passphrase_isExactlySixWords() {
        assertEquals(6, SukhiFixtures.recoveryPassphrase.words.size)
    }
}
