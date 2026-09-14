# Release acceptance

**1.0 prerelease under hardening — not a release candidate.**

All ten phases of the [release hardening plan](1.0_RELEASE_HARDENING_PLAN.md)
and every requirement in the [custom TV display and independent-stream specification](1.0_CUSTOM_TV_DISPLAY_PLAN.md)
are mandatory across all 16 artifacts and 21 runtime profiles, including Forge
1.7.10. New custom TVs default to Fit, Detailed, Auto; Quick TV construction and
presets remain intact. These are required behaviors, not certification claims.

The product remediation baseline is `d6e3566e76bcfa35ca52fa4b21e0fb46953c7491`
on `codex/release-audit-remediation-20260913`. The display implementation and
expanded maintained gates are integrated. Certification remains incomplete;
no final acceptance checkbox is closed by a scoped diagnostic result.

The latest completed hosted run is [34756506555](https://github.com/StonyTark1117/Cinemarr/actions/runs/34756506555)
for `d823945`: all five Fabric jobs (including Quilt and minimum-loader checks)
and four Forge jobs passed. Forge 1.20.2 failed fresh-server persistence and
legacy Forge failed terminal observation; five NeoForge builds failed on upstream
HTTP 502. The aggregate was skipped. The two runtime defects are addressed in
`d6e3566`; [run 34810548231](https://github.com/StonyTark1117/Cinemarr/actions/runs/34810548231)
is in progress and is not yet accepted. Its NeoForge 1.20.2 job exposed stale
retained-frame evidence in the owner observer: a prior TV reused the same stream
generation. The follow-up observer now requires retention after each actual
widget action, including paused seeks that keep the stream generation. Two
regressions cover that race. The earlier local aggregate also failed at the
legacy terminal observer with unchanged sources.

Windows run `20260913T113700Z` and ARM run `20260913T115705Z` passed all three native
software fixtures. Both guests are stopped and original ARM access is restored.
All four real-Plex recovery profiles have passed on exact build-3 profile JARs;
Fabric required a separate retry after a DiscPanel server-start failure. Original
server configurations/overrides are restored, all four servers are stopped with
Cinemarr disabled, and the temporary recovery workspace is removed. Native and
recovery bytes must still match the final indexed bundle. Four full real-Plex
A/V cases and two lifecycle cases remain open. The four packaged-client recipes
pass preflight locally; preflight is not playback certification.

Two earlier full bundles match byte for byte, but the subsequent Forge 1.20.2
saved-data fix changes the 1.20.2 family and requires a new matching build pair.
The complete final local matrix, direct visual/audio review, all hosted jobs and
aggregate, downloaded bundle parity, current metadata/scans and clean synchronized
main remain required. The [remediation ledger](RELEASE_AUDIT_REMEDIATION.md)
retains detailed scope, failures and teardown receipts.

The [September 12 audit](RELEASE_AUDIT_20260912.md) preserves the original
`8e1efda` / run `34538849435` findings (seven successful, seven failed and two
cancelled artifact jobs; aggregate cancelled). Earlier V11 source-change and
native/Plex evidence remains historical under
`build/release-resume-20260909/plex-session-lifecycle/`, including
`source-change-interruption-review.json`; it does not certify changed binaries.

Retain all 37 local cases, including the expanded feature scenario on all 21
profiles, plus the specified capacity/failure/raster checks. Preserve failed and
interrupted runs and their source identities. Standalone native decoding proves
ABI/functionality, not full Windows/ARM Minecraft playback or physical ARM speed.
No tag or publication is authorized by this plan.

## Completion checklist

These boxes close only for the final candidate and commit. Scoped intermediate
passes above do not complete a broader requirement.

## Display controls and independent streams

The complete linked specification is required; this checklist groups its
evidence rather than replacing its detailed acceptance criteria.

- [ ] Shared settings, revisions and schema migration preserve existing custom/Quick TVs, unloaded-controller behavior, reactivation, restart and construction-recovery records.
- [ ] Atomic display commands and the presentation-order regression reject unauthorized, stale, malformed and Quick-locked changes before mutation; the protocol bump, identities and all adapter/mismatch tests agree.
- [ ] Distinct TV streams share only watch-party timeline/track state; same-TV viewers share one stream, and local quality changes/failures cannot restart or corrupt healthy sibling streams, textures or positional audio.
- [ ] Health reports, transfer windows and recovery state belong to the full current timeline/TV-stream identity. Reports from one TV cannot overwrite a sibling's state; stale completion, expiry, retune and disconnect clean up only the affected ownership, within configured resource bounds.
- [ ] The unchanged stream cap counts active/reserved TV streams; FIFO waiting/admission, deduplication, cancellation, current-position start and bounded replacement/retirement pass controlled lifecycle tests.
- [ ] Resolution policy covers Auto versus explicit quality, source aspect/no upscaling, server limits, even dimensions and allocation budgets; requested and measured effective quality are truthful.
- [ ] Independent golden-color tests and original captures verify Fit/Fill/Stretch block rasters, nearest sampling, holes/sparse shapes, paused redraw, no double transform and bounded retained-frame/texture cleanup across every distinct rendering adapter.
- [ ] The Display Settings page passes direct 320×240 scaled-viewport review for input limits, Apply/Cancel, read-only/Quick controls, focus/drafts, pending/errors and requested/effective resolution.
- [ ] Feature smoke runs on all 21 profiles with two differently configured custom TVs, a preset Quick TV, same-TV viewers and both mapping modes; deeper capacity/failure/raster supplements and exact-artifact real-Plex scenarios pass.
- [ ] Existing A/V, active-underrun, transport, Plex identity/timeline/stop, native, saved-data and Quick TV construction/rollback requirements remain satisfied by the integrated candidate.
- [ ] User documentation, per-TV redacted diagnostics, scenario inventory, source/artifact hashes and full security scans cover the new feature and its final local/hosted evidence.

## Code and artifact gates

- [ ] The worktree is intentionally scoped and the release commit is identified.
- [ ] Unit tests and all 10 required GameTests pass under the required Java toolchain.
- [ ] All 16 canonical JARs build from the current hardening tree.
- [ ] Artifact inspection verifies identity/version, protocol, loader metadata, Java bytecode level, native classifiers, licenses, recipes/assets, and no credentials.
- [ ] No JAR contains the removed music/station runtime, JLayer, or Jump3r.
- [ ] SHA-256 digests are recorded for every artifact.

## Runtime matrix

- [ ] All 21 maintained runtime profiles start a dedicated server and two clients.
- [ ] Each profile constructs a real 144p Quick TV through normal block behavior and persists 16x9 geometry with a 256x144 rendition.
- [ ] Both clients render identifiable video, produce audible synchronized audio, survive reconnect, and leave no process/port/audio-module residue.
- [ ] Both framebuffer screenshots are directly reviewed on all 21 profiles, including the reconnected follower; matching decoded hashes alone do not check this box.
- [ ] Forge 1.7.10 is run fresh twice because its Java 8/LWJGL 2 path is independent.
- [ ] Quilt profiles use the Fabric artifact with the intended Quilt runtime.

## Failure and lifecycle gates

- [ ] Plex-disabled, connecting, ready, degraded, automatic retry, and operator retry states behave consistently at the legacy Forge, Fabric/Quilt, modern NeoForge, and 26.2 Fabric boundaries.
- [ ] Configured credentials and the private Plex endpoint are absent from source, logs, diagnostics, saves, runtime evidence, and release artifacts.
- [ ] Segment latency, transient failure, and retry exhaustion report bounded redacted behavior and recover without wedging the session.
- [ ] Quick TV obstruction, chunk unload, controller removal, server stop, and restart-mid-build all roll back generated pixels without removing player-built screens.
- [ ] Unloaded Quick TV recovery footprints remain persisted until their chunks can be inspected.
- [ ] Non-owners can view but cannot mutate a session unless permission policy grants control.
- [ ] Removing a television cancels its pending capacity/start work and closes its own Plex stream without stopping healthy sibling streams. Removing the final television also checkpoints the shared watch-party timeline and leaves no owned media resources.

## Native host gates

- [ ] The packaged `linux-arm64` classifier decodes 144p, 480p, and 1080p fixtures in software under an aarch64 kernel and aarch64 Java runtime.
- [ ] The packaged `windows-x86_64` classifier decodes the same fixtures in software under native Windows x86-64 and its FFmpeg DLL reports PE machine `0x8664`.
- [ ] Both runs stop their tagged transient QEMU units and leave their reusable installed guests powered off with ready identity markers.

## Decisive real-Plex gate

- [ ] The in-game controller/UI browses and selects an identifiable item from an allowed real Plex library.
- [ ] Two independent clients show matching identifiable program video.
- [ ] Both clients produce synchronized audible program audio within the documented threshold.
- [ ] Pause, seek, stream selection, stop, disconnect/reconnect, and final-TV teardown behave correctly.
- [ ] Plex reports no leftover session/transcode and the host has no leftover game process, port, temporary credential file, or credential-bearing log.

## Isolated scaled controller checks

The dedicated gate launches each client through `scripts/run-private-xvfb.sh`,
using Xvfb's atomic display binding and readiness descriptor. A failed binding
cannot silently connect a client to an already-running display. The launcher
records its owned display/PID, disables TCP, and tears down only its command
group and X server. Display numbers 90–190 avoid the low numbers normally used
by desktop/Xwayland startup; no GUI action targets the user's desktop.

The non-owner controller gate uses a 640x480 physical window with GUI scale
two, requiring a 320x240 logical viewport and zero clipped or overlapping
widgets. Its temporary client options disable vanilla movement/inventory
tutorial hints so unrelated toasts do not obscure controller evidence.
Screenshots and editing/browse behavior still require direct review; passing
rectangle checks alone does not establish text visibility or usability.

## Reusing legacy probe saves

`run-discopanel-real-plex-gate.sh 1.7.10-forge` prepares only the offline
`CinemarrVideoA` and `CinemarrVideoB` identities before server startup. Their
UUIDs are derived from vanilla's `OfflinePlayer:<name>` identity scheme; no
ordinary player files are selected. Existing compressed NBT is held locally
in the owning process, bounded/validated, and only its top-level health,
death, hurt, and fall fields are normalized for the new test. This prevents
an earlier lifecycle fall from starting the next playback gate at a death
screen. No health reset occurs during playback.

After the clients disconnect and the server stops, both saved probe players
must still be alive. The wrapper then restores their original NBT bytes and
verifies them through a fresh read, including on normal failure cleanup.
The helper tests and a real stopped-server roundtrip cover this preparation;
a successful reset alone is not playback acceptance. Never run the helper on
an active server or another player's save. Missing or malformed required
post-run state fails the gate. Keep both client framebuffer reviews as a
separate requirement, even when the health check succeeds.

## Historical evidence

All previous acceptance narratives, failures, commands and receipt references
remain verbatim in [the September 10 acceptance archive](RELEASE_ACCEPTANCE_CHECKPOINTS_20260910.md).
Earlier history is retained in [hardening evidence history](HARDENING_EVIDENCE_HISTORY.md).
