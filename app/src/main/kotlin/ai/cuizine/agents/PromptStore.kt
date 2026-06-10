package ai.cuizine.agents

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads the versioned system prompts (`build-conventions.md` §8). The
 * authoritative files live in the repo-root `prompts/` directory and are
 * bundled into assets at build time; the `## System prompt` section is what
 * the agent receives.
 */
@Singleton
class PromptStore
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        private val cache = mutableMapOf<String, String>()

        fun systemPrompt(agentFile: String): String =
            cache.getOrPut(agentFile) {
                val raw =
                    context.assets
                        .open(agentFile)
                        .bufferedReader()
                        .use { it.readText() }
                raw
                    .substringAfter(SYSTEM_PROMPT_HEADER)
                    .trim()
                    .ifBlank { error("Prompt file $agentFile has no system prompt section") }
            }

        private companion object {
            const val SYSTEM_PROMPT_HEADER = "## System prompt"
        }
    }
