# 0011 — Minimal backend in v1 (thin LLM proxy and API key vault)

**Status:** Superseded / absorbed by `0011-optional-backend-with-capability-tiers.md` (the canonical ADR 0011)
**Date:** 2026-04-11

> **This is an early, narrower draft of ADR 0011 and is NOT the canonical decision.** It framed the question as "does v1 have a thin backend at all?" The canonical ADR 0011 — `0011-optional-backend-with-capability-tiers.md` — broadened that framing into the optional-backend-with-capability-tiers model (the four-quadrant signed-in/signed-out × free/paid matrix), which is the decision the rest of the foundation references. This file is retained for historical context only; when the two conflict, the optional-backend version wins. Do not build against this draft.

## Context

v1 is the closed alpha, distributed by APK to 15-25 hand-picked users (per ADR 0001). The constraint engine, the agent topology, and the data layer all live on the user's device per the local-first trust posture. The unresolved question was whether *any* Cuizine-owned backend service exists during v1, or whether the alpha is fully serverless with direct LLM API calls from the device.

Two real concerns shape the answer. First, embedding LLM provider API keys directly in the distributed APK is operationally simple but exposes the keys to extraction by anyone who decompiles the package. Second, every server we run during alpha is operational burden, security surface, and hosting cost we'd prefer to avoid for a 15-25-user closed test. Both concerns are real and they pull in opposite directions.

The architectural choice also has trust-posture implications. The strongest possible version of the trust story — "your inference requests went from your phone to the LLM provider and Cuizine never saw them" — is only available if no Cuizine server sits in the middle of the call path. Once a backend is inserted, even one that does nothing more than forward requests, the maximally strong claim weakens slightly to "your inference content passed through Cuizine's backend in flight but was never stored, logged, or read."

## Decision

**v1 ships with a minimal Cuizine-owned backend consisting of exactly one stateless service: a thin LLM proxy with API key vault.** Nothing else. No auth, no user data storage, no billing, no telemetry, no analytics, no sync coordination, no Instacart integration. The backend exists only to:

1. Hold the LLM provider API keys server-side, so they are never embedded in the distributed APK.
2. Issue short-lived per-device tokens to v1 alpha devices on first launch.
3. Receive structured inference requests from devices and forward them upstream to the appropriate LLM provider (Anthropic, Gemini, or OpenAI per the per-agent routing in ADR 0006).
4. Return the upstream provider's response to the device unchanged.

The proxy is **stateless**: it holds no per-user data, no conversation history, no constraint graphs, no logs of inference content. The only persistent state on the backend is the LLM provider API keys themselves (encrypted at rest) and a minimal device-token registry (device ID, token, expiry — no PII).

The proxy is **content-blind**: it does not parse, log, store, or inspect the body of inference requests. Request bodies are forwarded byte-for-byte to the upstream provider. Responses are forwarded byte-for-byte back. The only thing the proxy reads from a request is the routing header (which provider, which model) — never the prompt content.

Anthropic API zero-data-retention is enabled at the upstream provider account level (not just request-by-request) so that the upstream provider also does not retain content. The trust chain is: device → proxy (in flight, unread) → provider (zero retention) → response → proxy (in flight, unread) → device.

### What this means for the trust posture in v1

The claim Cuizine can honestly make in v1 is:
> "Your data lives only on your device. When Cuizine generates a meal suggestion for you, the relevant slice of your data is sent through Cuizine's stateless proxy to an LLM provider with zero data retention. The proxy does not read, store, or log the content of your request. Nothing about you is persistently stored anywhere except on your own device."

This is *almost* the maximally strong claim (which would be "no Cuizine server is involved at all") but is still meaningfully stronger than what any comparable health-adjacent app offers. The small concession — that inference content passes through a Cuizine-owned process in flight — is the price of protecting the API keys from APK extraction, and we judge it worth paying.

### Hosting and operational shape

The proxy is small enough to run on a free tier of any cloud provider or a $5/month VPS. It is stateless, can be horizontally scaled trivially, has no database beyond the API key vault and the device-token registry, and requires roughly zero ongoing maintenance once deployed. v1 alpha load (15-25 users, occasional inference calls per user per day) is well within free-tier limits everywhere.

### v1-to-v2 transition

In v2, the backend grows to include real services: account creation and authentication, subscription billing, encrypted sync coordination, and the Instacart integration's server-side OAuth. These are added as *new services*, not as modifications to the LLM proxy. The proxy itself stays exactly as it was in v1 — content-blind, stateless, doing only the proxy job — even when other backend services exist alongside it. This separation is important because it preserves the trust property: even when v2 has a real backend, the inference path remains content-blind.

The device-side architecture in v1 must be designed so that swapping "call thin proxy" for "call full backend that proxies inference and also does sync/auth/billing" is changing a few adapter layers, not refactoring the agents or the constraint engine. This is the same architectural-readiness discipline applied throughout (ADR 0001's NA readiness, ADR 0004's profile-as-first-class, ADR 0005's Flutter cross-platform readiness).

## Consequences

**Positive.** API keys are protected from APK extraction. v1 alpha has the smallest possible operational and security footprint while still being secure. The trust posture is honestly strong — content-blind proxy with zero-retention upstream provider is a meaningfully better story than any comparable app offers. Hosting cost is essentially zero for the alpha. v2 can grow the backend incrementally without rebuilding the v1 components. The proxy itself is so small it can be implemented and tested in a few days.

**Negative.** v1 is no longer "zero backend" in the strictest sense. The strongest possible trust claim ("nothing ever leaves your device for Cuizine") is unavailable; the claim is now the slightly weaker "nothing is persistently stored anywhere except your device, and inference content passes through a content-blind proxy in flight." This is the price of API key protection and we accept it.

The proxy is one more piece of infrastructure to deploy, monitor, and secure, even if it's small. It needs basic uptime monitoring (the alpha breaks if the proxy is down). It needs API key rotation procedures. It needs a deployment pipeline. These are real but bounded costs.

Alpha users are dependent on the proxy being available — if it goes down, no inference works. v1 has no fallback for this. v2 should consider provider-failover behavior more deeply, but v1 accepts the single point of failure as part of the small-and-simple tradeoff.

**Neutral.** The proxy must enforce per-device rate limits to prevent runaway usage from a single misbehaving device. The exact rate limits are tuned during alpha based on observed usage.

## Alternatives considered

**Embed API keys directly in the APK (no backend).** Rejected. Operationally simplest and gives the strongest trust claim, but exposes keys to extraction. Even with a 15-25 user alpha and bounded blast radius, the leakage risk during alpha plus the precedent for v2 (keys cannot be embedded in a public Play Store app under any circumstances) made this not worth doing even temporarily.

**Real backend from v1 (auth, billing, sync coordination, telemetry).** Rejected. v1 alpha has no need for any of these — there's no billing in alpha (per ADR 0007), no sync coordination needed for users with one device each, no auth required for an APK install with no user accounts, and no telemetry per the trust posture. Building these for alpha would be premature and the operational burden would distract from the actual goal of validating the engine.

**Backend that handles inference reasoning server-side (not just proxying).** Rejected. Moving reasoning to the backend would mean storing the constraint graph or relevant context server-side, which violates the local-first trust posture. The whole point of the proxy is that it is content-blind — it does not see or reason about user data, only forwards requests.

**Per-user API keys (BYOK in disguise).** Rejected per ADR 0008 (BYOK deferred to v3). v1 users do not provide their own API keys. The v1 proxy holds Cuizine's keys.

**Long-lived device tokens with no expiry.** Rejected. Short-lived tokens with refresh on first launch each session are the right hygiene. If a device is lost or compromised, the token expires within hours rather than indefinitely.

## Related

- See `vision.md` § Trust posture
- Pairs with 0006 (multi-provider routing — the proxy implements per-agent routing to the right provider based on request headers)
- Pairs with 0007 (the proxy is paid-tier infrastructure in production; the alpha uses it freely as part of the alpha-is-fully-free posture)
- Pairs with 0008 (BYOK is *not* the v1 design; the proxy holds Cuizine's keys, not user keys)
- The forthcoming `technical-architecture.md` treats the proxy as a first-class component in the system map and the trust boundaries diagram
- The forthcoming `security-and-privacy.md` must specify the proxy's threat model, key rotation policy, and rate limiting in detail
