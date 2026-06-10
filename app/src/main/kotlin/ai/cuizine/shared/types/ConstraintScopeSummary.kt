package ai.cuizine.shared.types

/**
 * One quiet line summarizing where a constraint applies (`ui-ux-spec.md`
 * §5.4) — shared because both the Profile surface and the agent input
 * summaries speak it.
 */
fun Constraint.scopeSummary(): String {
    val temporal =
        when (scope.temporal.kind) {
            "always" -> {
                "Always"
            }

            "weekly" -> {
                scope.temporal.weekdays
                    ?.joinToString(", ") { day -> day.replaceFirstChar(Char::uppercase) + "s" }
                    ?: "Weekly"
            }

            "daily_window" -> {
                "Certain hours each day"
            }

            "date_bounded" -> {
                "For a set period"
            }

            "phase_bounded" -> {
                "During a life phase"
            }

            "composite" -> {
                "On a combined schedule"
            }

            else -> {
                ""
            }
        }
    val contextual =
        scope.contextual.requiredFlags
            .takeIf { it.isNotEmpty() }
            ?.joinToString(", ") { flag -> "while " + flag.replace('_', ' ') }
    // "Always · while X" reads wrong — the contextual condition IS the scope.
    if (contextual != null && scope.temporal.kind == "always") return contextual
    return listOfNotNull(temporal.takeIf { it.isNotEmpty() }, contextual).joinToString(" · ")
}
