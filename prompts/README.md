# prompts/

Versioned agent system prompts (`build-conventions.md` §8): content, not
code, iterated at their own cadence with per-file change histories. The
`## System prompt` section of each file is what the agent receives at
runtime (bundled into app assets at build time). `copy/` holds user-facing
copy strings as they graduate from in-code placeholders (Phase 7 polish).

Test bar for prompt changes: the hard-cases suite plus targeted cases for
the behavior the change addresses (`build-conventions.md` §8). A/B testing
is deferred to v2 by design.
