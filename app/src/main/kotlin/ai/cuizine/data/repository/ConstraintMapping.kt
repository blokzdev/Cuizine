package ai.cuizine.data.repository

import ai.cuizine.data.database.entities.ConstraintEntity
import ai.cuizine.engine.types.AvoidPayload
import ai.cuizine.engine.types.ConstraintPayload
import ai.cuizine.engine.types.ConstraintScope
import ai.cuizine.engine.types.ConstraintType
import ai.cuizine.engine.types.ContextualPayload
import ai.cuizine.engine.types.LimitPayload
import ai.cuizine.engine.types.PreferPayload
import ai.cuizine.engine.types.ProvenanceRecord
import ai.cuizine.engine.types.RequirePayload
import ai.cuizine.engine.types.Severity
import ai.cuizine.shared.types.Constraint
import kotlinx.serialization.json.Json

/**
 * Entity ↔ domain mapping for constraints: the JSON columns (de)serialize to
 * the engine's typed payloads, discriminated by the `type` COLUMN
 * (`data-model.md` §4). Unknown stored enum values fail loudly — the code is
 * the CHECK-constraint locus (DECISION-LOG #1c).
 */
object ConstraintMapping {
    val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    fun toDomain(entity: ConstraintEntity): Constraint {
        val type = ConstraintType.fromStorage(entity.type)
        val payload: ConstraintPayload =
            when (type) {
                ConstraintType.Avoid -> json.decodeFromString<AvoidPayload>(entity.payloadJson)
                ConstraintType.Prefer -> json.decodeFromString<PreferPayload>(entity.payloadJson)
                ConstraintType.Require -> json.decodeFromString<RequirePayload>(entity.payloadJson)
                ConstraintType.Limit -> json.decodeFromString<LimitPayload>(entity.payloadJson)
                ConstraintType.Contextual -> json.decodeFromString<ContextualPayload>(entity.payloadJson)
            }
        val scope = json.decodeFromString<ConstraintScope>(entity.scopeJson)
        val provenance = json.decodeFromString<ProvenanceRecord>(entity.provenanceJson)
        return Constraint(
            id = entity.id,
            profileId = entity.profileId,
            type = type,
            severity = Severity.fromStorage(entity.severity),
            humanLabel = humanLabelFrom(provenance, payload),
            scope = scope,
            payload = payload,
            provenance = provenance,
            expiresAt = entity.expiresAt,
        )
    }

    fun toEntity(
        constraint: Constraint,
        createdAtIso: String,
        modifiedAtIso: String = createdAtIso,
    ): ConstraintEntity =
        ConstraintEntity(
            id = constraint.id,
            profileId = constraint.profileId,
            type = constraint.type.storageValue,
            severity = constraint.severity.storageValue,
            temporalScopeKind = constraint.scope.temporal.kind,
            contextualScopeFlags =
                constraint.scope.contextual.requiredFlags
                    .takeIf { it.isNotEmpty() }
                    ?.let { json.encodeToString(it) },
            locationScopeCountry = constraint.scope.location.country,
            householdScopeMode = constraint.scope.household.mode,
            scopeJson = json.encodeToString(constraint.scope),
            payloadJson = encodePayload(constraint.payload),
            provenanceJson = json.encodeToString(constraint.provenance),
            createdAt = createdAtIso,
            modifiedAt = modifiedAtIso,
            expiresAt = constraint.expiresAt,
        )

    private fun encodePayload(payload: ConstraintPayload): String =
        when (payload) {
            is AvoidPayload -> json.encodeToString(payload)
            is PreferPayload -> json.encodeToString(payload)
            is RequirePayload -> json.encodeToString(payload)
            is LimitPayload -> json.encodeToString(payload)
            is ContextualPayload -> json.encodeToString(payload)
        }

    /**
     * The human label is presentation state, not a stored column
     * (`data-model.md` §4 has no label column). The user's own words
     * (provenance.original_phrasing) win when concise — that's what the
     * Profile surface promises (`ui-ux-spec.md` §5.4); otherwise a calm
     * mechanical description. The Curator refines labels in Phase 5.
     */
    private fun humanLabelFrom(
        provenance: ProvenanceRecord,
        payload: ConstraintPayload,
    ): String {
        val phrasing = provenance.originalPhrasing
        if (phrasing != null && phrasing.length <= 60) return phrasing
        return when (payload) {
            is AvoidPayload -> "Avoid ${payload.target.value}"
            is PreferPayload -> "${payload.target.value} is a favourite"
            is RequirePayload -> "Needs ${payload.target.value}"
            is LimitPayload -> "Limit ${payload.target.value.replace('_', ' ')}"
            is ContextualPayload -> payload.state.flag.replace('_', ' ')
        }
    }
}
