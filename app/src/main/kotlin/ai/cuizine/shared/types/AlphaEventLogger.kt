package ai.cuizine.shared.types

/**
 * Local-only alpha event logging (`data-model.md` §7; PRD §5). Operational
 * telemetry only — NEVER user content beyond what the closed event-type set
 * requires for audit reconstruction. Nothing here ever leaves the device
 * except through the user-initiated export flow.
 */
interface AlphaEventLogger {
    suspend fun log(
        eventType: String,
        severity: String,
        payloadJson: String,
        profileId: String? = null,
    )
}
