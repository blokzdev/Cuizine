package ai.cuizine.engine.types

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * JSON column shape validation (`testing-strategy.md` §9): the five payload
 * types, the scope, and the provenance record round-trip through the exact
 * snake_case wire shapes from `data-model.md` §4.
 */
class PayloadSerializationTest {
    private val json =
        Json {
            // Additive payload extensions need no ADR (`data-model.md` §8), so
            // readers tolerate unknown keys.
            ignoreUnknownKeys = true
        }

    @Test
    fun avoidPayload_roundTripsSnakeCaseWireShape() {
        val wire =
            """{"target":{"kind":"category","value":"allium","reference":"fodmap"},""" +
                """"exceptions":[{"kind":"ingredient","value":"garlic-infused oil","reason":"low fodmap"}]}"""
        val decoded = json.decodeFromString<AvoidPayload>(wire)
        assertEquals("allium", decoded.target.value)
        assertEquals(1, decoded.exceptions.size)
        val reEncoded = json.encodeToString(AvoidPayload.serializer(), decoded)
        assertEquals(decoded, json.decodeFromString<AvoidPayload>(reEncoded))
    }

    @Test
    fun preferPayload_roundTrips() {
        val payload = PreferPayload(target = PreferTarget(kind = "dish", value = "rajma"), strength = "high")
        val decoded = json.decodeFromString<PreferPayload>(json.encodeToString(PreferPayload.serializer(), payload))
        assertEquals(payload, decoded)
    }

    @Test
    fun requirePayload_roundTripsWithThreshold() {
        val payload =
            RequirePayload(
                target =
                    RequireTarget(
                        kind = "nutritional_property",
                        value = "fiber",
                        threshold = Threshold(value = 8.0, unit = "g"),
                    ),
                window = "single_meal",
            )
        val decoded = json.decodeFromString<RequirePayload>(json.encodeToString(RequirePayload.serializer(), payload))
        assertEquals(payload, decoded)
    }

    @Test
    fun limitPayload_usesHardOrSoftWireName() {
        val wire =
            """{"target":{"kind":"nutritional_property","value":"sodium"},""" +
                """"ceiling":{"value":2000.0,"unit":"mg"},"window":"per_day","hard_or_soft":"soft"}"""
        val decoded = json.decodeFromString<LimitPayload>(wire)
        assertEquals("soft", decoded.hardOrSoft)
        assertEquals(2000.0, decoded.ceiling.value, 0.0)
    }

    @Test
    fun contextualPayload_roundTrips() {
        val wire = """{"state":{"flag":"ibs_flare","value":"active"},"triggers":["constraint-7"]}"""
        val decoded = json.decodeFromString<ContextualPayload>(wire)
        assertEquals("ibs_flare", decoded.state.flag)
        assertEquals(listOf("constraint-7"), decoded.triggers)
    }

    @Test
    fun constraintScope_roundTripsAllFiveDimensions() {
        val wire =
            """{"temporal":{"kind":"weekly","weekdays":["tuesday"]},""" +
                """"contextual":{"required_flags":["ibs_flare"],"excluded_flags":[]},""" +
                """"location":{"country":null,"region":null},""" +
                """"household":{"mode":"self","profile_ids":null},""" +
                """"profile":{"profile_id":"profile-1"}}"""
        val decoded = json.decodeFromString<ConstraintScope>(wire)
        assertEquals("weekly", decoded.temporal.kind)
        assertEquals(listOf("tuesday"), decoded.temporal.weekdays)
        assertEquals(listOf("ibs_flare"), decoded.contextual.requiredFlags)
        assertEquals("self", decoded.household.mode)
        assertEquals("profile-1", decoded.profile.profileId)
    }

    @Test
    fun provenanceRecord_roundTripsWithModificationHistory() {
        val wire =
            """{"source":"user_direct","added_at":"2026-06-10T12:00:00Z",""" +
                """"added_context":{"flow":"constraint_conversation","question_index":3},""" +
                """"original_phrasing":"no onions when my stomach is bad",""" +
                """"confidence":"high_user_direct",""" +
                """"modification_history":[{"at":"2026-06-11T08:00:00Z","source":"user_edited",""" +
                """"reason":"tightened","previous_payload_json":"{}","previous_scope_json":"{}"}]}"""
        val decoded = json.decodeFromString<ProvenanceRecord>(wire)
        assertEquals("user_direct", decoded.source)
        assertEquals(3, decoded.addedContext.questionIndex)
        assertEquals(1, decoded.modificationHistory.size)
    }

    @Test
    fun severityAndType_rejectUnknownStorageValues() {
        // The code is the enforcement locus for the DDL CHECK sets (DECISION-LOG #1c).
        assertThrows(IllegalArgumentException::class.java) { Severity.fromStorage("critical") }
        assertThrows(IllegalArgumentException::class.java) { ConstraintType.fromStorage("forbid") }
        assertEquals(Severity.ReligiousCultural, Severity.fromStorage("religious_cultural"))
        assertEquals(ConstraintType.Contextual, ConstraintType.fromStorage("contextual"))
    }
}
