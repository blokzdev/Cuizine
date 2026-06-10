// Root build script. Plugins are declared here (apply false) so the single
// :app module and any future modules resolve identical versions from the
// catalog (build-conventions.md §4).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.kover) apply false
}

// The local-first CI gate (CLAUDE.md §9): every commit runs this and passes.
// Aggregates ktlint, Android Lint, unit tests (incl. Robolectric), and
// coverage verification. No GitHub Actions — this is the gate.
tasks.register("qualityGate") {
    group = "verification"
    description = "Runs ktlint, Android Lint, all unit tests, and coverage verification."
    dependsOn(
        ":app:ktlintCheck",
        ":app:lintDebug",
        ":app:testDebugUnitTest",
        ":app:koverVerify",
    )
}
