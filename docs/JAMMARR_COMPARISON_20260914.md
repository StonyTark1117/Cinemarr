# Jammarr comparison and improvement assessment — September 14, 2026

The best immediate lessons for Cinemarr are loader-login hardening, clearer
local-audio failure handling, explanatory display help, and sustained multi-TV
acceptance. Broad version parity is a separate, substantially larger project.
Several Jammarr fixes already have Cinemarr equivalents; copying its music
transport or optional-client policy would undo deliberate product decisions.

This assessment was brought forward at the user's explicit request. Cinemarr's
release acceptance remains paused; this report neither completes those gates
nor adds targets to its 1.0 matrix. Recommendations below are assessed work,
not implemented fixes or newly certified compatibility.

## Freshness and evidence

- Fetched Jammarr's GitHub refs and independently queried `git ls-remote`:
  default `HEAD`, `main`, and `v1.1.0` all resolve to
  **`8eccf06e972689315932072dd42aab7c4ff315a8`**. The retained checkout was
  already current and is clean; the comparison does not assume that from its age.
- GitHub's [latest release](https://github.com/StonyTark1117/Jammarr/releases/tag/v1.1.0)
  is **1.1.0**, published September 5, 2026, targeting that commit. Its
  [original CI run 33898240759](https://github.com/StonyTark1117/Jammarr/actions/runs/33898240759)
  reports success on that exact SHA. The changelog still says “Unreleased”; the
  live release API is the publication authority.
- Cinemarr comparison base: **`11c0b61`**, product 1.0.0 prerelease, protocol 11.
  Jammarr is product 1.1.0, protocol 6. Those numbers are independent protocols;
  neither numbering nor release age measures feature superiority.
- The earlier feasibility report used `f680a0ee`. There are **130 subsequent
  commits**, but no added or removed artifact identities. Its target-manifest
  delta changes Forge 1.12.2 runtime control/audio metadata, not target count.
  This review inspected the relevant implementation diffs, current manifests,
  client/server policies and regression sources; it is not an independent rerun
  of Jammarr's advertised 99-runtime qualification.
- [Machine-readable comparison](JAMMARR_COMPARISON_20260914.json) records full
  revisions, remote refs, release/CI metadata, every target, source hashes, and
  dependency-source provenance. Cinemarr's existing target-manifest suite passed
  **16 tests** during this assessment. No Minecraft clients or test servers were
  started, and no credentials were reloaded.

## Feature comparison

| Area | Latest Jammarr | Current Cinemarr | Assessment |
| --- | --- | --- | --- |
| Product | One shared Plex music queue; audio-only MP3 transport | Movies/shows on persistent player-built TVs; HLS video and positional audio | Keep separate products. Cinemarr does not need Jammarr, JLayer or Jump3r. |
| Browsing | Search, artists, albums, audio playlists, stations and Sonic Adventure | Allowlisted movie/show browsing, search, hierarchy, TV queues, audio/subtitle selection | Video playlists or next-episode continuation are possible later features; music sonic analysis is not a video recommendation implementation. |
| Control | Everyone can browse/append; operators manage the shared queue | Screen owners/operators control TVs; watch parties share timeline commands while TVs own independent rendition streams | Retain per-TV ownership and library permissions. Do not import global music-queue permissions. |
| Local experience | Volume/mute, explicit local audio state and manual retry after bounded recovery | Persistent local settings, Records-category positional volume, controller feedback and decoder diagnostics | A clear per-stream local-audio error/retry state is a useful gap to address. |
| Presentation | Music menu with explanatory hover help | Fit/Fill/Stretch, Detailed/pixel mapping, custom quality, holes, Quick TVs, remotes and redstone | Cinemarr's world/rendering features are additional architecture, not missing Jammarr parity. Display-control help can improve. |
| Server policy | Selected music section; bounded cache, preparation and transfer | Explicit movie/show allowlists and rating/permission policy; per-TV stream capacity, isolated replacement and bounded relay | Retain Cinemarr's explicit allowlist. Do not introduce Jammarr's blank-library automatic selection. |
| Transport | Frame-aligned MP3 windows, hashes, acknowledgements and bounded recovery | Identity-bound compressed video segments, 16 KiB chunks, hashes, acknowledgements and bounded prefetch | Share invariants and failure tests, not packet formats or MP3 buffering assumptions. |
| Client requirement | Unmodded players may join without music; supported modded clients can visit vanilla servers | Matching client required to register world content, negotiate and render TVs | Optional-client and reverse-vanilla matrices do not transfer as Cinemarr requirements. |
| Runtime footprint | Pure-Java music codec dependencies | JavaCV/FFmpeg and packaged native payloads; software default with hardware fallback | A Jammarr loader port does not prove Cinemarr video/native compatibility on that loader or OS. |

Feature sources: pinned [Jammarr README](https://github.com/StonyTark1117/Jammarr/blob/8eccf06e972689315932072dd42aab7c4ff315a8/README.md),
[Cinemarr README](../README.md), and [display specification](1.0_CUSTOM_TV_DISPLAY_PLAN.md).

## Version and loader comparison

Manifest counts, rather than README badges: Jammarr has **78 artifacts** across
**29 Minecraft versions** and **six artifact loader IDs**: 56 stable and 22
preview. Of these, **75 are server artifacts**, **three are LiteLoader client
companions**, and **24 modern Fabric artifacts also run on Quilt**, producing
**99 server runtime profiles**. Legacy Fabric uses the `fabric` ID but is a
different implementation; Quilt reuse is not a separate artifact.

Cinemarr has **16 artifacts**, **six Minecraft versions**, and **21 runtimes**
(five Quilt reuses). All sixteen artifact version/loader pairs occur in Jammarr.
The delta is **62 artifacts**, of which **59 are standalone port candidates**,
plus **78 server runtime profiles** when the additional Quilt reuses are counted.
These are inventory differences, not a commitment to support them.

`P` below means Jammarr's manifest marks that artifact preview. `F/Q` means one
modern Fabric artifact plus a Quilt runtime. Every row's exact artifact and Java
metadata is retained in the JSON comparison.

| Minecraft | Jammarr implemented artifacts / reuse | Cinemarr today |
| --- | --- | --- |
| Beta 1.7.3 | Babric P | None |
| 1.6.4 | Forge P, Legacy Fabric P, Ornithe P, LiteLoader companion P | None |
| 1.7.10 | Forge, LiteLoader companion P | Forge |
| 1.8.9 | Forge P, Legacy Fabric P, Ornithe P, LiteLoader companion P | None |
| 1.12.2 | Forge | None |
| 1.16.5 | F/Q, Forge | None |
| 1.18.2 | F/Q, Forge | None |
| 1.19.2 | F/Q, Forge | None |
| 1.20 | F/Q, Forge P | None |
| 1.20.1 | F/Q, Forge, NeoForge | Same pairs |
| 1.20.2 | F/Q, Forge, NeoForge | Same pairs |
| 1.20.3 | F/Q, Forge P, NeoForge P | None |
| 1.20.4 | F/Q, Forge, NeoForge | None |
| 1.20.5 | F/Q, NeoForge P | None; no Jammarr Forge artifact |
| 1.20.6 | F/Q, Forge, NeoForge | None |
| 1.21 | F/Q, Forge P, NeoForge | None |
| 1.21.1 | F/Q, Forge, NeoForge | Same pairs |
| 1.21.2 | F/Q, NeoForge P | None; no Jammarr Forge artifact |
| 1.21.3–1.21.5 | F/Q, Forge, NeoForge for each version | None |
| 1.21.6–1.21.7 | F/Q, Forge P, NeoForge P for each version | None |
| 1.21.8 | F/Q, Forge, NeoForge | None |
| 1.21.9 | F/Q, Forge P, NeoForge P | None |
| 1.21.10–1.21.11 | F/Q, Forge, NeoForge for each version | None |
| 26.1.2 | F/Q, Forge, NeoForge | Same pairs |
| 26.2 | F/Q, Forge, NeoForge | Same pairs |

Source: pinned [Jammarr manifest](https://github.com/StonyTark1117/Jammarr/blob/8eccf06e972689315932072dd42aab7c4ff315a8/gradle/targets.json)
and [Cinemarr manifest](../gradle/targets.json). “Stable” is Jammarr's declaration;
Cinemarr retains `prerelease` until its own final acceptance completes.

Recommended expansion sequence, based on code reuse and API boundaries rather
than an unverified popularity estimate:

1. **Modern boundary prototypes:** 1.20.4 (Java 17), 1.20.6 (Java 21), then
   1.21.4 and 1.21.11. Prove one complete video adapter at each boundary before
   filling intervening patches and additional loaders. Screen render/blur,
   payload, resource, registry and sound APIs require real ports.
2. **Intermediate families:** 1.19.2 and 1.18.2 (Java 17), then 1.16.5 (Java 8).
   Expect new networking, rendering, world-persistence and native-decoder bridges.
3. **Legacy Forge:** 1.12.2, then 1.8.9; investigate 1.6.4 only with a Java 8
   runtime requirement and its older ASM/class-header constraints. Shared Java 8
   policy code helps, but does not supply Minecraft adapters.
4. **Research ports:** Babric Beta 1.7.3, Legacy Fabric and Ornithe. Jammarr proves
   useful loader/packet/audio plumbing exists; it does not prove custom TV
   registries, block rendering or FFmpeg packaging. Feasibility remains unproven
   until a small production-artifact TV prototype works.

**Do not pursue separate LiteLoader parity** under the current architecture.
Its audio companions are not standalone server targets. The 1.6.4/1.7.10
companions also need Forge on the client, while a LiteLoader-only client does
not supply Cinemarr's Forge world-registry contract. A Forge+LiteLoader setup
can instead investigate coexistence with the ordinary Forge Cinemarr artifact.

Every proposed port needs packaged client/server launch, two-client video/PCM,
required-client rejection, persistent screens, reload, quality/capacity changes,
native checks, reproducible artifacts and teardown evidence. None is a
metadata-only support addition.

## Fix comparison and prioritized improvements

### P1 — Investigate the shared Forge login race first

Jammarr [126651e](https://github.com/StonyTark1117/Jammarr/commit/126651e)
wraps `HandshakeHandler.sentMessages` in a synchronized list because server
ticks add acknowledgements while the network path removes them. Both products
pin **Forge 47.4.23 / NeoForge 47.1.106** for 1.20.1; Cinemarr has no equivalent
`HandshakePendingMessagesMixin` in its tracked Java sources.

The exact NeoForge source was downloaded from its official Maven repository;
the Forge source was read from its existing version-specific Gradle cache after
the official endpoint returned HTTP 403. Both contain an `ArrayList`,
`removeIf` in `handleIndexedMessage`, and `add` in `tickServer`. Their hashes and
provenance are recorded. This confirms matching upstream exposure, **not a
reproduced Cinemarr crash**.

Next: reproduce simultaneous login/acknowledgement churn with Cinemarr alone,
then qualify a version-scoped upstream fix or mixin on both 1.20.1 loaders.
Include wrong-protocol rejection, cold reconnect, clean teardown and a separate
coinstalled-Jammarr case: Jammarr itself patches this shared loader class and
could otherwise conceal the exposure in a combined test.

### P1 — Bound local audio allocation failures and expose recovery

Jammarr's current `JammarrAudioPlayer` routes channel-creation failure through
bounded recovery and exposes an audio error plus manual retry. Cinemarr's
`CinemarrVideoAudio.finishStart` clears `channelPending` and returns on a null
handle/error; subsequent eligible ticks can request another handle. There is
no corresponding allocation-failure budget or backoff in this class. This is a
source-confirmed recovery-policy gap, **not evidence of a current silent client**.

Add per-stream attempts/backoff, a readable local audio state, and a deliberate
retry/reset path after exhaustion. Keep it separate from the operator-only
server `/cinemarr retry` command. Test a failed OpenAL device, exhausted streaming
pool, resource reload and multiple TVs; a retry must not duplicate sources or
alter another TV's watch-party timeline. Jammarr's global vanilla-music suppression
fix [597fbf2](https://github.com/StonyTark1117/Jammarr/commit/597fbf2) is not a
drop-in remedy for multiple positional TV sources.

### P2 — Transfer the late-response tests, not the MP3 state machine

Jammarr [8eccf06](https://github.com/StonyTark1117/Jammarr/commit/8eccf06)
rejects replies outside the current requested window after recovery; earlier
[b75bef5](https://github.com/StonyTark1117/Jammarr/commit/b75bef5) acknowledges
duplicate replies that finish an exact retry. Cinemarr already checks stream
identity, generation, request ID and segment before assembly, retains the exact
window on retry, hashes completed segments, and bounds window acknowledgements.
Its new-segment request IDs prevent the same old-request poisoning mechanism.

Add explicit modern-and-legacy tests for recovery followed by old/future-window
chunks, delayed duplicate boundary chunks and acknowledgements, and replacement
while a window is in flight. `TransferWindowFlowTest` currently tests ordinary
window progression and invalid coordinates; that is narrower than these cases.
Do not declare the Jammarr bug present merely because Cinemarr also transfers
chunks, or assume a guard after assembly proves all receive-path invariants.

### P2 — Improve sustained audio behavior with A/V-specific proof

Jammarr adds same-direction, monotonic-duration drift hysteresis and
[bounded rate correction](https://github.com/StonyTark1117/Jammarr/commit/b6c6497)
(20 ms deadband, at most 1% pitch change). Cinemarr already measures physical
OpenAL timing, compensates startup buffer preparation, and re-buffers after five
observations beyond 50 ms. The current modern policy does not require the drift
sign to remain constant or enforce a monotonic persistence duration.

First test alternating-sign backend jitter and sustained drift; add hysteresis
if the existing policy causes unnecessary resets. Rate correction is an
experiment, not an automatic backport: it affects pitch and must preserve
audio/video synchronization against the TV timeline. Never loosen the existing
two-listener acceptance allowance to make it pass.

Promote a **30-minute canary followed by a two-hour multi-TV scenario** into an
opt-in maintained gate. Use identifiable PCM and video markers, late joins,
repeated seeks/quality replacements, pause/resume, resource reload and temporary
Plex failure. Measure continuity, A/V skew, device sources, retained rasters,
decoder threads, queue/cache limits and resource growth. Jammarr's changelog
reports a 349-cycle two-hour qualification; this assessment did not rerun it.
Cinemarr's shorter accepted captures and fault cases do not establish that
same continuous-duration claim.

### P2 — Add explanatory display help and cold cancellation coverage

Jammarr's issue #2 work explains actions, not just abbreviated labels.
Cinemarr's controller helper generally uses the button label as its tooltip;
the inspected display adapters provide fixed explanatory lines but no individual
layout/mapping/quality/Apply/Reload tooltips. Add localized explanations and
disabled reasons, preserving the 320×240 logical viewport. Verify real hover
rendering, focus and stale/pending behavior on each UI boundary. The legacy
text-selection issue already has Cinemarr's dedicated field fix and regressions;
it is not an outstanding Jammarr backport.

Jammarr [ddcaf55](https://github.com/StonyTark1117/Jammarr/commit/ddcaf55)
avoids interrupting lazy JLayer class loading during cancellation. Cinemarr uses
FFmpeg instead, retires publications before closing workers, but its shared
`BoundedWorkExecutor.close()` calls `shutdownNow()`. Add a cold first-decode /
rapid-replacement / reload test under Forge module loading. **No poisoned
FFmpeg class load was reproduced here.** Do not simply remove interruption:
native/network workers still need bounded cancellation and teardown.

### P3 — Maintain the operational patterns; defer optional product expansion

Jammarr has manifest-driven dry-run deployment and resumable per-runtime
qualification. Cinemarr already has its target manifest, exact artifact hashes,
stopped-state deployment checks and transactional restoration; much recent
orchestration lives in task-local helpers. A reusable runner should bind resume
to artifact hash, source/helper revision, profile and immutable attempt evidence,
recheck remote state, preserve failures, and stop once after interruption.
This improves future maintenance without rerunning or relabeling old evidence.

Loader-specific Jammarr fixes for Forge 1.12.2 disconnect handling,
Forge 1.21 / NeoForge 1.21–1.21.2 config-watcher shutdown, and newer menu blur
belong in the relevant port checklist. They are not proof of defects in every
current Cinemarr loader. Existing reproducible archive canonicalization,
server-owned credentials, bounded egress, lifecycle cleanup and actual PCM
acceptance already embody the transferable release principles.

After reliability work, consider optional **next-episode continuation** or
curated **video playlists/channels** if desired. Specify per-party ownership,
allowlist/rating revalidation on every item, manual-queue precedence, bounded
prefetch and restart semantics before implementation. Do not silently import
Sonic Adventure, Plex Pass assumptions, music fallback or a global autoplay queue.

## Assessment outcome

The requested current feature/version/fix comparison is complete. Its new
findings are source-backed recommendations with explicit verification paths.
Product source, target counts, deployed services and release acceptance state
are unchanged. The remaining release work is still listed in the
[pause checkpoint](RELEASE_AUDIT_PAUSE_20260914.md); this assessment does not
certify new versions or close that separate release objective.
