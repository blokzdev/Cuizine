import java.util.Properties

// The Cuizine application module — single app module for v1
// (build-conventions.md §12; multi-module revisited at v2).
// AGP 9 built-in Kotlin: no org.jetbrains.kotlin.android here (DECISION-LOG #1a).
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kover)
}

// Absence-driven credentials (CLAUDE.md §9): keys live ONLY in the gitignored
// local.properties; an absent key compiles to "" and DI selects the
// deterministic fake. Never a placeholder secret.
val localProperties =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) file.inputStream().use { stream -> load(stream) }
    }

fun localConfig(key: String): String = localProperties.getProperty(key)?.trim().orEmpty()

android {
    namespace = "ai.cuizine"
    compileSdk = 37

    defaultConfig {
        applicationId = "ai.cuizine"
        minSdk = 26 // DECISION-LOG #1c: java.time without desugaring; below the low-end floor device
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-alpha"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "CUIZINE_USDA_API_KEY", "\"${localConfig("cuizine.usda.api.key")}\"")
        buildConfigField("String", "CUIZINE_OFF_CONTACT", "\"${localConfig("cuizine.off.contact")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false // revisited before alpha APK distribution (Phase 7)
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            // The authoritative prompt files live at repo-root prompts/
            // (build-conventions.md §8) and are bundled into assets so the
            // agents read the same versioned files the founder edits.
            assets.directories.add(rootProject.file("prompts").path)
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true // Robolectric
        }
    }

    lint {
        // Zero-warnings bar (CLAUDE.md quality bar): warnings fail the gate.
        warningsAsErrors = true
        abortOnError = true
        // Version-currency checks are owned by the roadmap's dependency
        // checkpoints (DECISION-LOG #1a), not the per-commit gate — leaving
        // them on would make the gate fail whenever upstream releases.
        disable +=
            listOf(
                "OldTargetApi", // targetSdk 36 is deliberate: Robolectric cap (DECISION-LOG #1a)
                "AndroidGradlePluginVersion",
                "GradleDependency",
                "NewerVersionAvailable",
            )
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

ktlint {
    version.set(libs.versions.ktlint.get()) // plugin default lags (DECISION-LOG #1a)
    android.set(true)
}

kover {
    reports {
        // Risk-tiered coverage (testing-strategy.md §7): coverage is measured
        // and enforced on the high-risk modules — the validator (safety
        // floor) and scope evaluation now; encryption and billing classes
        // join this include list in Phase 6. Trivial accessors elsewhere are
        // deliberately not coverage-targeted (Principle 5).
        filters {
            includes {
                classes(
                    "ai.cuizine.engine.validator.*",
                    "ai.cuizine.engine.scope.*",
                )
            }
        }
        verify {
            rule("engine-critical-line-coverage") {
                bound {
                    minValue.set(95)
                    coverageUnits.set(kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE)
                }
            }
            rule("engine-critical-branch-coverage") {
                bound {
                    minValue.set(85)
                    coverageUnits.set(kotlinx.kover.gradle.plugin.dsl.CoverageUnit.BRANCH)
                }
            }
        }
    }
}

dependencies {
    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    // AndroidX core, lifecycle, navigation
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    // Orbit MVI
    implementation(libs.orbit.viewmodel)
    implementation(libs.orbit.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Networking (real Layer 3 food clients; absence-driven activation)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)

    // Coroutines + serialization
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)

    // Unit tests (JVM + Robolectric)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.room.testing)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.orbit.test)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
