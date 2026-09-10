# Release acceptance

**1.0 prerelease under hardening — not a release candidate.**

All ten phases of the [release hardening plan](1.0_RELEASE_HARDENING_PLAN.md)
and every requirement in the [custom TV display and independent-stream specification](1.0_CUSTOM_TV_DISPLAY_PLAN.md)
are mandatory across all 16 artifacts and 21 runtime profiles, including Forge
1.7.10. New custom TVs default to Fit, Detailed, Auto; Quick TV construction and
presets remain intact. These are required behaviors, not certification claims.

The integrated feature implementation is in progress. No frozen final candidate
or complete release acceptance exists for the changed binaries. The earlier
V11 batch was stopped after concurrent source changes. Seventeen scoped runtime
acceptances preceded those changes; Forge 1.21.1's runtime checks closed but its
source guard failed, and NeoForge 1.21.1 was interrupted. The 704 reviewed PNGs
and 38 measured audio pairs, two identical builds, native checks and deployment
retain their earlier scope and cannot certify the new implementation.

The authoritative interruption/cleanup record is
`build/release-resume-20260909/plex-session-lifecycle/source-change-interruption-review.json`.
It overrides the earlier in-progress summaries. The original 843-input source
snapshot and prior receipts remain historical evidence. Designated Proxmox,
DiscPanel, Plex and native guest access works; access is not the blocker.

Freeze the integrated implementation in an isolated checkout before fresh
certification. Retain all existing 37 local cases, add the feature smoke across
all 21 profiles, and inventory additional capacity/failure/raster supplements
on representative legacy/modern boundaries and every distinct rendering adapter.
The new smoke does not replace existing controls, A/V, adverse-network,
ModMenu/minimum-loader, terminal, lifecycle or native-platform requirements.

Run two complete matching builds, all focused tests and ten GameTests, the
expanded runtime suite, both retained native guests, all four real-Plex profiles,
four recovery supplements and two lifecycle supplements. Real-Plex evidence
must exercise the specified multi-TV watch party, different qualities, Quick TV
preset, same-TV viewers, live quality changes and paused mapping changes.
Then require reviewed final metadata/docs/scans, the complete final-commit local
release gate, all hosted jobs and aggregate gate, downloaded artifact parity,
and a clean checkout with HEAD equal to origin/main. No tag or publication.

Prior evidence limitations remain explicit: overwritten paused PCM does not
certify pause silence; sequential automatic screenshots are not simultaneous
phase proof; standalone native tests establish decoder ABI/functionality, not
full Windows/ARM Minecraft playback or physical ARM performance. Preserve
failed/interrupted runs and their source identity; do not retry to mask failures.

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
