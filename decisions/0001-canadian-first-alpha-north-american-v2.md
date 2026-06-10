# 0001 — Canadian-first alpha, North American v2

**Status:** Accepted (amended 2026-04-15)
**Date:** 2026-04-11
**Amendment note:** ADR 0015 (Android-only scope) clarifies that the v3 expansion described below is **geographic only** — the same native Android app distributed via Google Play in new markets. The original foundation paired v3 geographic expansion with an iOS launch; that iOS aspect is removed. Cuizine is Android-only across all versions. The geographic sequence (Canada → North America → other English-speaking markets) in this ADR is unchanged.

## Context

Cuizine touches medical conditions, religious practice, household composition, and cultural identity — among the most regulated and sensitive data categories an app can hold. The closed alpha needs to be small (15-25 users), deeply engaged, and drawn from a population we can actually recruit and support. The founder is based in Brampton, Ontario, and the natural alpha pool is the founder's Canadian social graph. At the same time, Instacart — Cuizine's chosen sourcing layer — operates across Canada and the United States, which means the technical lift to support both countries in v2 is meaningfully smaller than expanding beyond North America. The question was whether to launch v1 in Canada only, North America from the start, or English-speaking global.

## Decision

**v1 (closed alpha) is Canadian users only.** Single regulatory jurisdiction (PIPEDA), focused alpha pool from the founder's actual social graph, clean messaging, and a contained legal surface. Distribution by APK to 15-25 hand-picked users.

**v2 (public launch) opens to North America** — Canada and the United States together — because Instacart already covers both, the engine will be proven by the end of v1, and the legal work for US compliance (state privacy laws, health-app posture) can be done deliberately during the v1→v2 window.

**v3 expands to other English-speaking markets** (UK, Australia, etc.) where a non-Instacart sourcing strategy will be required. This expansion is geographic only — Android in new markets, not a new platform (per ADR 0015).

Critically: **the codebase, schema, agents, and integrations are architected for North American readiness from day one of v1**. Nothing in the engine assumes a single country. The only thing Canada-only about v1 is who we let in — not how the system is built.

## Consequences

**Positive.** Legal footprint stays small in v1 (one jurisdiction we can actually understand). Alpha messaging is clean ("Canadian closed alpha") instead of muddled ("North American alpha mostly Canadian users"). Alpha recruitment matches reality. v2 launch becomes "flip a flag" rather than a rebuild because the architecture was always ready. Family-plan pricing tier becomes feasible at v2 launch.

**Negative.** Slightly more discipline required during v1 build to *not* leak Canadian assumptions into the codebase. Slightly more documentation overhead to remember that v1 is "Canadian by go-to-market, North American by architecture." Some US users in the founder's network may have to wait until v2.

**Neutral.** Instacart vendor lock-in for v1 and v2 sourcing is now architecturally committed (see 0003).

## Alternatives considered

**North America from v1.** Rejected because the marginal user-pool benefit was illusory (alpha would be Canadian regardless), the legal surface expansion was real (US state privacy laws, plaintiff exposure for health-adjacent apps), and the messaging became muddled. The technical lift was the smallest part of the cost.

**English-speaking global from v1.** Rejected for all the above reasons plus the impossibility of a non-Instacart sourcing strategy being ready by v1.

**Canada only through v2.** Rejected because Instacart covers both countries already, US users represent a meaningful portion of the addressable market for the chronic-conditions user segment, and waiting until v3 to add the US would slow business growth without protecting against any real risk.

## Related

- See `vision.md` § Launch strategy
- Pairs with 0003 (Instacart as sole grocery integration)
