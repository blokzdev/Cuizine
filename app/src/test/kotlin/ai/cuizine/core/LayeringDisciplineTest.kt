package ai.cuizine.core

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Enforces the layering discipline from `build-conventions.md` §3 by scanning
 * import statements in the main source set (no extra dependency needed —
 * DECISION-LOG.md #1b):
 *
 * - `engine/` and `shared/` are pure Kotlin — no Android, no Compose, no DI.
 * - `data/` has no UI dependencies.
 * - `ui/` never imports Room DAOs directly.
 * - `agents/` (when it exists, Phase 5) has no UI dependencies.
 */
class LayeringDisciplineTest {
    private val sourceRoot: File by lazy {
        // Unit tests run with the module directory as the working dir; walk up
        // defensively in case the runner differs.
        generateSequence(File(checkNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .take(4)
            .map { File(it, "src/main/kotlin/ai/cuizine") }
            .firstOrNull { it.isDirectory }
            ?: error("Could not locate src/main/kotlin/ai/cuizine from ${System.getProperty("user.dir")}")
    }

    private fun violations(
        packageDir: String,
        forbiddenImportPrefixes: List<String>,
    ): List<String> {
        val dir = File(sourceRoot, packageDir)
        if (!dir.isDirectory) return emptyList() // package not created yet — fine
        return dir
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file
                    .readLines()
                    .filter { line -> line.startsWith("import ") }
                    .filter { line -> forbiddenImportPrefixes.any { line.removePrefix("import ").startsWith(it) } }
                    .map { line -> "${file.relativeTo(sourceRoot)}: $line" }
            }.toList()
    }

    @Test
    fun engineIsPureKotlin() {
        val found =
            violations(
                packageDir = "engine",
                forbiddenImportPrefixes =
                    listOf(
                        "android.",
                        "androidx.",
                        "dagger.",
                        "javax.inject",
                        "ai.cuizine.ui",
                        "ai.cuizine.data",
                        "ai.cuizine.agents",
                        "ai.cuizine.billing",
                    ),
            )
        assertTrue(
            "engine/ must stay pure Kotlin (build-conventions §3):\n${found.joinToString("\n")}",
            found.isEmpty(),
        )
    }

    @Test
    fun sharedTypesArePureKotlin() {
        val found =
            violations(
                packageDir = "shared",
                forbiddenImportPrefixes = listOf("android.", "androidx.", "dagger.", "javax.inject"),
            )
        assertTrue("shared/ must stay pure Kotlin:\n${found.joinToString("\n")}", found.isEmpty())
    }

    @Test
    fun dataLayerHasNoUiDependencies() {
        val found =
            violations(
                packageDir = "data",
                forbiddenImportPrefixes = listOf("androidx.compose", "ai.cuizine.ui"),
            )
        assertTrue("data/ must not depend on UI:\n${found.joinToString("\n")}", found.isEmpty())
    }

    @Test
    fun uiNeverImportsRoomDaosDirectly() {
        val found =
            violations(
                packageDir = "ui",
                forbiddenImportPrefixes = listOf("ai.cuizine.data.database.daos", "androidx.room"),
            )
        assertTrue(
            "ui/ must go through containers, never DAOs (build-conventions §3):\n${found.joinToString("\n")}",
            found.isEmpty(),
        )
    }

    @Test
    fun agentsLayerHasNoUiDependencies() {
        val found =
            violations(
                packageDir = "agents",
                forbiddenImportPrefixes = listOf("androidx.compose", "ai.cuizine.ui"),
            )
        assertTrue("agents/ must not depend on UI:\n${found.joinToString("\n")}", found.isEmpty())
    }
}
