package ai.cuizine.shared.types

import ai.cuizine.engine.types.ConstraintPayload
import ai.cuizine.engine.types.ConstraintScope
import ai.cuizine.engine.types.ConstraintType
import ai.cuizine.engine.types.ProvenanceRecord
import ai.cuizine.engine.types.Severity

/**
 * The constraint aggregate as surfaces consume it — the typed contract between
 * the UI and the engine (`build-conventions.md` §3). Phase 2 mocks and the
 * Phase 3 real engine both produce exactly this shape.
 *
 * @property humanLabel The constraint in the user's terms ("No beef",
 *   "Vegetarian on Tuesdays") — what `ui-ux-spec.md` §5.4 renders. The
 *   structured truth lives in [payload]/[scope]; the label never replaces it.
 */
data class Constraint(
    val id: String,
    val profileId: String,
    val type: ConstraintType,
    val severity: Severity,
    val humanLabel: String,
    val scope: ConstraintScope,
    val payload: ConstraintPayload,
    val provenance: ProvenanceRecord,
    val expiresAt: String? = null,
    /** Whether the constraint is in the active set right now (scope-evaluated). */
    val isActiveNow: Boolean = true,
)
