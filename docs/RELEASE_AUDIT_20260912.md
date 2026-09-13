# Cinemarr release-plan audit — September 12, 2026

**Assessment: 1.0 prerelease; not ready for release-candidate designation.** Substantial foundations are implemented, but required feature behavior is incomplete, a fresh display edit is rejected by the server, and final runtime certification is absent.

Audited commit: `8e1efda206ed57935ac9e5498ead27e0ad7a445a`. The checkout was clean at the start, and a live `git ls-remote` confirmed that remote `main` points to this SHA. This report is the only tracked-file addition made by the audit. No product code, acceptance thresholds, deployments, CI runs, tags, or releases were changed.

Scope: the ten-phase [hardening plan](/mnt/MSata/Projects/Cinemarr/docs/1.0_RELEASE_HARDENING_PLAN.md), complete [display/stream specification](/mnt/MSata/Projects/Cinemarr/docs/1.0_CUSTOM_TV_DISPLAY_PLAN.md), and [release acceptance checklist](/mnt/MSata/Projects/Cinemarr/docs/RELEASE_ACCEPTANCE.md). The maintained scope remains **16 artifacts and 21 runtime profiles**, including Forge 1.7.10.

## Findings, in priority order

**1. P1 — Display Settings Apply rejects a fresh edit as stale. Confirmed with executable reproduction.**

[`DisplaySettingsDraft.apply()`](/mnt/MSata/Projects/Cinemarr/core/src/main/java/stonytark/cinemarr/core/video/DisplaySettingsDraft.java:36) calls `original.apply(...)`, producing revision `N+1`. The modern [page](/mnt/MSata/Projects/Cinemarr/src/main/java/stonytark/cinemarr/client/DisplaySettingsScreen.java:43) sends that object unchanged. The [server update](/mnt/MSata/Projects/Cinemarr/src/main/java/stonytark/cinemarr/screen/CinemarrWorldScreens.java:198) treats its revision as the expected current revision `N`. The same pattern exists in the other modern pages and world-screen adapters.

The reproduction used the compiled production core classes: start with default revision 0, change mapping through `DisplaySettingsDraft`, then pass the result through the same settings validation as the server. Result: `server revision=0; UI payload revision=1`, followed by `TV display settings changed; refresh before applying`. No concurrent edit is needed. Send the expected revision separately from the resulting settings revision, or preserve the expected revision in the command. Add a complete draft → packet → server regression, including a genuinely stale second writer.

**2. P1 — Required Display Settings behavior is missing or partial across platforms. Confirmed by source inspection.**

Forge 1.7.10 has protocol/server/rendering foundations, but its [controller](/mnt/MSata/Projects/Cinemarr/platforms/mc1.7.10/forge/src/main/java/stonytark/cinemarr/client/LegacyVideoScreen.java:112) has no Display Settings page or entry point. The [source-layout guard](/mnt/MSata/Projects/Cinemarr/scripts/check-source-layout.py:204) explicitly skips the legacy screen when enforcing the entry point.

The modern [page](/mnt/MSata/Projects/Cinemarr/src/main/java/stonytark/cinemarr/client/DisplaySettingsScreen.java:27), with equivalent limitations in 1.20.x and 26.x, exposes Auto, 1080p and Custom; the other required named presets are absent. It rebuilds the draft and empty text fields in `init()`, leaves dimension fields editable outside Custom, does not use `canControl` to make the page read-only, and closes immediately after sending Apply without an acknowledgement/pending state. Requested quality, selected choices, and the pixel-mode explanation are not rendered. These are implementation gaps, before the separate required 320×240 visual review.

**3. P1 — “Actual” dimensions are the requested rendition, not measured output. Confirmed by data-flow inspection.**

The [server](/mnt/MSata/Projects/Cinemarr/src/main/java/stonytark/cinemarr/server/ServerVideoManager.java:533) computes rendition dimensions, passes them to Plex, stores that same object in `ActiveVideoMedia`, and [publishes it as effective dimensions](/mnt/MSata/Projects/Cinemarr/src/main/java/stonytark/cinemarr/server/ServerVideoManager.java:580). The UI labels these values “Actual.” No measured output replaces them on this path. If Plex rounds or otherwise changes the rendition, the display can report incorrect quality. Report measured encoded/decoded dimensions and keep requested dimensions distinct; show unknown until measurement exists.

**4. P1 — The mandatory expanded feature acceptance is not wired into maintained gates. Confirmed by gate inspection.**

The [local aggregate](/mnt/MSata/Projects/Cinemarr/build.gradle:784), [dedicated runtime harness](/mnt/MSata/Projects/Cinemarr/scripts/run-dedicated-server-gate.sh), [real-Plex wrapper](/mnt/MSata/Projects/Cinemarr/scripts/run-discopanel-real-plex-gate.sh), and [hosted workflow](/mnt/MSata/Projects/Cinemarr/.github/workflows/ci.yml) retain the existing runtime/control/terminal/pressure/fault/loader gates. They do not implement the required feature scenario: two custom TVs at different qualities, a preset Quick TV, same-TV viewers, both mapping modes, independent live replacement, paused redraw, capacity admission, and framebuffer interior-color checks. The acceptance/probe sources contain no `SET_DISPLAY` scenario.

The raster unit tests have useful small golden-color cases. Their larger/odd/line-shape loop checks reuse and opacity rather than independently calculated colors; this does not satisfy the complete required raster matrix or rendered holes/L-shape evidence. Inventory and implement the added scenarios across all 21 profiles and the required rendering/capacity/failure supplements before freezing a candidate. An otherwise green existing gate would still be insufficient.

**5. P1 — Current-SHA hosted runtime acceptance is red/incomplete. Verified through GitHub.**

The latest [run 34538849435](https://github.com/StonyTark1117/Cinemarr/actions/runs/34538849435) belongs to the audited SHA. All 16 artifact jobs completed both “Build and inspect artifact” and “Verify byte-identical rebuild” successfully. Their final job outcomes, after runtime checks, were **7 success, 7 failure, 2 cancelled**. The aggregate “1.0 release gate” was cancelled.

| Outcome | Profiles | Observed failure or limitation |
| --- | --- | --- |
| Success | Forge 1.20.1, 1.20.2, 1.21.1, 26.1.2, 26.2; NeoForge 1.20.1, 26.1.2 | Existing automated job scope only; no added feature certification |
| Failure | Fabric 1.20.1, 1.20.2, 1.21.1, 26.1.2 | `No visible Minecraft window on the private X server` |
| Failure | NeoForge 1.20.2 | `Paused generation lost the previously displayed frame` |
| Failure | NeoForge 1.21.1 | `No fresh authoritative terminal-phase result before timeout` |
| Failure | NeoForge 26.2 | Leader initialized but did not reach acceptance-ready state |
| Cancelled | Forge 1.7.10; Fabric 26.2 | No completed profile result |

These are observed failure signatures, not a claim that every root cause is established. The NeoForge 1.21.1 main runtime and GameTest steps passed before its terminal supplement failed. Quilt coverage is dependent on the Fabric jobs; 16 artifact-job outcomes must not be described as 21 completed runtime profiles. Preserve failed/cancelled evidence and diagnose the failures before certification.

**6. P2 — Pixel-derived CPU storage remains retained after switching to Detailed. Confirmed by ownership inspection.**

The Detailed branch in [modern textures](/mnt/MSata/Projects/Cinemarr/src/main/java/stonytark/cinemarr/client/CinemarrVideoTexture.java:27) and [legacy textures](/mnt/MSata/Projects/Cinemarr/platforms/mc1.7.10/forge/src/main/java/stonytark/cinemarr/client/LegacyVideoTexture.java:23) closes the derived texture but leaves the parent `PresentedFrame.raster` allocated. `PresentedFrame.releaseRaster()` has no production call sites. The allocation is bounded, but it survives a mode change until later replacement/close, contrary to the specified explicit release behavior. Wire ownership cleanup into mode changes and cover it at the adapter boundary. Legacy `uploadRaw` also allocates a fresh direct upload buffer each frame; bounded buffer reuse remains unfinished there.

**7. P2 — Stale presentation rejection still follows other state writes. Confirmed by command ordering.**

The actual presentation write is now after the generation check, which fixes the originally identified layout write. However, [modern command handling](/mnt/MSata/Projects/Cinemarr/src/main/java/stonytark/cinemarr/server/ServerVideoManager.java:256) still tunes the requested session, potentially restores dormant playback, persists the TV session name and refreshes tracking before checking `expectedGeneration`. [Legacy handling](/mnt/MSata/Projects/Cinemarr/platforms/mc1.7.10/forge/src/main/java/stonytark/cinemarr/server/LegacyVideoManager.java:393) has the same ordering. A stale presentation packet with a different session name can therefore change attachment before rejection. Validate against the existing authoritative state before side effects and add a regression asserting unchanged session attachment as well as unchanged layout.

**8. P2 — Release records and user guidance lag the implementation and current evidence.**

The current plan and acceptance introduction still identify `cd5501b` / run `34536028642` as the latest completed hosted candidate and `7308440` as the follow-up. Several later completed runs exist, including the audited SHA's cancelled run with substantive build and failure results. The release verdict remains correct, but the failure inventory is stale. Existing user docs describe the feature as required work; they do not yet explain requested versus effective quality and per-TV capacity waiting as required by the specification.

## Progress against the ten phases

“Implemented” below describes inspected foundations, not final release acceptance.

| Phase | Current state | What prevents closure |
| --- | --- | --- |
| 1. Truthful prerelease status | Correct top-level status; remote SHA matches local | Refresh current checkpoint and failure inventory |
| 2. NeoForge A/V/terminal hardening | Policies/regressions and main runtime pass exist | Current terminal supplement fails; repeated complete boundary acceptance absent |
| 3. Target manifest | 16 artifacts / 21 profiles; validation passes; protocol 11 agrees | Final evidence/certification reconciliation and expanded scenario inventory |
| 4. Shared sources and persistence | Family source sets and drift guard pass; schema 4 and display migration exist | Complete legacy UI, migration/reactivation/unloaded-controller and renderer integration proof |
| 5. Bounded asynchronous work | Stream pool, FIFO admission, independent identities, bounded egress/health and controlled tests exist | Full platform/Plex multi-TV isolation, capacity, replacement and cleanup evidence |
| 6. Protocol/security | Protocol 11 and distinct timeline/stream identities are integrated | Broken display revision handoff; remaining mutation ordering; full adapter/runtime security proof |
| 7. Legacy improvements/UI | Existing input-preservation and audio/lifecycle foundations exist | Missing legacy display page; current legacy runtime job cancelled; rendering lifecycle gaps |
| 8. Expanded verification | Fresh core/modern tests pass; hosted artifact/rebuild steps pass | Feature scenarios missing; runtime failures; fresh complete suite and direct visual/audio review |
| 9. Exact candidate certification | Historical native/Plex receipts retained | New frozen candidate, native guests, four real-Plex profiles, four recovery and two lifecycle supplements with feature scenario |
| 10. Final commit/CI | Starting tree clean; HEAD equals remote main | Final local gate, all hosted runtime jobs, successful aggregate, downloaded/local bundle parity and final reviews |

## Verification performed in this audit

| Check | Result and scope |
| --- | --- |
| Fresh Java tests | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :core:test --rerun test --rerun --no-build-cache --console=plain` succeeded |
| Core test XML | 273 tests; zero failures/errors; one skipped |
| Modern root test XML | 50 tests; zero failures/errors; two skipped |
| Skipped cases | Credentialed core Plex smoke, credentialed client Plex playback, hardware backend metrics |
| Display revision reproduction | Production classes deterministically reject an uncontended UI edit as stale |
| Manifest | Summary reports 16 artifacts, 21 runtimes, five Quilt runtimes; repository verification passed |
| Source layout | Family-shared source verification passed; its explicit legacy display exemption limits this result |
| Tracked release hygiene | Passed the private-network IPv4 scan; this is not the full configured-secret/evidence audit |
| Existing local artifact bundle | Inspector accepted all 16 JARs; manifest product version 1.0.0, protocol 11 |
| Hosted current-SHA evidence | Read job/step outcomes and failed logs; all 16 build/rebuild steps passed; runtime outcomes above |
| Local runtime prerequisites | Neither `Xvfb` nor `xvfb-run` is on PATH; complete isolated local runtime execution remains blocked here |

The first local Gradle invocation returned cached/up-to-date tests; the separately listed `--rerun --no-build-cache` invocation then executed both test tasks freshly. The existing local bundle was inspected without rebuilding it; its success does not establish current-source provenance or local/hosted parity. This audit did not rerun legacy Java tests, the full artifact build, Minecraft/GameTests locally, native guests, physical A/V review, real Plex, or comprehensive secret scans. Hosted step results and historical receipts are identified separately from fresh local checks.

## Recommended completion order

1. Fix the display revision handoff and add the combined command regression. Complete all display pages, measured dimensions, stale-command ordering and renderer lifecycle gaps.
2. Implement the normative feature harness and its complete scenario inventory; strengthen golden-color, migration, UI and platform-boundary coverage.
3. Resolve the current runtime failure signatures and provide the required isolated X environment. Preserve failed evidence and existing A/V thresholds.
4. Freeze the completed source in an isolated checkout. Produce two matching full bundles and complete all deterministic, GameTest, 21-profile, supplement, native and visual/audio checks.
5. Certify those exact bytes through real-Plex multi-TV, recovery and lifecycle acceptance; complete teardown, documentation and security review.
6. Run the final-commit local gate, then require fully green hosted jobs/aggregate, downloaded artifact inspection and local parity, accurate certification metadata and clean synchronized Git state. Tagging/publication remain outside the plan.
