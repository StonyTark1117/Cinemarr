# Release acceptance

**Cinemarr 1.0 prerelease: audio recovery repair under validation.**

The [current ledger](1.0_CANDIDATE_LEDGER.md#current-work-bounded-audio-recovery)
and [frozen checkpoint manifest](RELEASE_CERTIFICATION_AC25666.json) record
`ac25666`: two identical builds, sixteen inspected artifacts, ten GameTests,
eighteen green hosted jobs, matching downloaded artifacts, all fourteen
real-Plex cases, and artifact-bound native decoder checks.

The local matrix failed NeoForge 1.21.1 audio recovery after reconnect. Twenty
of twenty-one main profiles completed automation; only seventeen of the full
sixty-two cases have scoped acceptance. The second fresh legacy run and the
remaining supplements are incomplete. Its source and artifacts stayed unchanged,
and the terminal audit found no owned processes, private displays, or open game
and RCON ports.

The current source retains bounded PCM across audio recovery and discards it
at terminal drain or identity reset. The repaired development build passed all
sixteen artifact inspections, ten GameTests, and thirteen new recovery tests.
Complete NeoForge 1.21.1 and Forge 1.7.10 development main profiles passed,
including measured resource/sound reload and reconnect audio, with scoped
cleanup and configured scans. The [development manifest](RELEASE_AUDIO_RECOVERY_DEVELOPMENT_20260924.json)
records the measurements and incomplete image-review scope. Two forced frozen
builds and all original final-commit certification gates, artifact binding,
complete evidence review, scans, cleanup, and release-record closure remain
open. No tag or publication is authorized. Older sections are historical.

## Historical pre-consolidation status

**Cinemarr 1.0.0 prerelease: protocol correction awaiting new artifact certification.**

The final local gate at `1ea796f91fe4e38993dc68ffa611370062fe92f1`
failed Fabric 26.1.2's wrong-protocol check after 600 seconds. Both peers
reported only a generic disconnect. Nineteen preceding cases passed; the
remaining matrix was stopped through the harness cleanup handler and is not
certified. Its frozen sources and eighteen-file bundle stayed unchanged.

The acceptance-only outgoing protocol override also affected incoming server
validation, allowing the client to close before the server rejected its bad
hello. Both client adapters now use canonical capability validation for incoming
hellos. Three consecutive Fabric 26.1.2 cases and one Forge 1.7.10 case passed
explicit server rejection, compatible-client command checks, missing-client
checks and clean shutdown. Sixteen protocol unit tests and ten source-layout
tests passed; the new regression fails on both original adapters. The rejection
oracle and its timeout are unchanged.

The correction changes the artifact bytes. Target statuses return to prerelease
until the new candidate is certified. The evidence below and its manifest are
historical, bound to the recorded `8668435` bytes; they do not certify the new
candidate. A new build pair and artifact/runtime evidence, followed by the full
final-commit local and hosted gates, scans and cleanup, remain required.

## Previous candidate evidence

All ten phases of the [release hardening plan](1.0_RELEASE_HARDENING_PLAN.md)
and every requirement in the [custom TV display and independent-stream specification](1.0_CUSTOM_TV_DISPLAY_PLAN.md)
remain mandatory across all sixteen artifacts and twenty-one runtime profiles,
including Forge 1.7.10. New custom TVs default to Fit, Detailed, Auto. Quick TV
construction and preset behavior remain intact. No tag or publication is authorized.

The audit findings are implemented and the integrated artifact evidence is
reconciled in the [candidate evidence manifest](RELEASE_CANDIDATE_EVIDENCE_20260914.json).
It records all sixteen JAR hashes and twenty-one runtime profiles. The product
source is `86684359df84f305531639a9d0184b2c2eab083c`; subsequent certification
metadata and documentation changes must reproduce that bundle exactly.

All sixteen targets have **runtime-certified artifact evidence**. This means
build, launch, playback, protocol, controls, reconnect, lifecycle and representative
real-Plex acceptance for the recorded bytes. Release readiness additionally
requires phase 10 for the final containing commit: the complete local gate,
all eighteen hosted jobs including the aggregate, inspected downloaded artifacts
matching the local bundle, final security/cleanup review and clean synchronized
`main`. The metadata does not substitute for those checks or authorize publication.

The corrected forced build pair passed all 92 top-level tasks in each build,
including all ten GameTests and inspection of all sixteen JARs. Both eighteen-file
bundles match exactly; all 894 source inputs stayed unchanged. Fifteen JARs match
the earlier `b99a5c2` candidate. The changed Forge 1.7.10 JAR is
`70b7eb7afa5c94b0297b7014703111b6e553fafcc5721732c1b6fc239ee620f5`.
Its field fix preserves complete text when replacing a full selection; four
regressions pass, including cases that failed against vanilla behavior.

The earlier complete local matrix passed all 37 cases. Its same-byte evidence
covers the fifteen unchanged artifacts; fresh legacy GUI and live runs cover
the changed artifact. All five maintained GUI boundaries passed complete
runtime checks and direct review: Forge 1.7.10, Quilt 1.20.1, Forge 1.20.2,
NeoForge 26.1.2 and Fabric 26.2. Each has 27 newly reviewed GUI originals.
Legacy has 98 reviewed originals in total; the other four also retain 36
previously reviewed same-JAR originals each. The root NeoForge prototype and
all six rendering boundaries have separate direct reviews. The final commit
must rerun the whole local matrix; these scoped joins do not replace it.

Exact-byte real-Plex evidence covers four main profiles, four outage/recovery
supplements and two lifecycle supplements. The refreshed legacy main passed
in 536 seconds with all 96 originals directly reviewed, audible physical PCM
(correlation 0.923318, lag 0 ms), two resource reloads per client, three-TV
restoration in a fresh server process and clean shutdown. Its recovery test
passed both manual and automatic recovery from a 503 outage. Its lifecycle
run passed in 154 seconds: checkpoint 256 placed/8960 remaining, restart with
9216 unloaded cells still pending, loaded cleanup to zero, first pixel restored
to air. Local controlled scenarios contain 33 feature steps; real-Plex scenarios
contain 31 because synthetic failed-replacement injection belongs to the local
fixture. All thresholds remain unchanged.

Windows x86-64 and emulated Linux ARM64 passed three native software fixtures.
The new legacy JAR retains all 86 native members unchanged. These are decoder
ABI/functionality checks, not full Windows/ARM Minecraft or physical-ARM speed
claims. Both retained guests are powered off. Fresh checks found all four test
servers stopped with original configuration and other mods preserved, Cinemarr
disabled, and no Cinemarr Plex sessions/transcodes. The owned recovery scratch
workspace was removed after verifying its owner, stopped unit, artifact and
exported evidence. User credential files are preserved.

Configured-secret scans passed for the new artifacts, GUI runs and live evidence.
The final live scan covered 369 files and 6807 payloads (1,915,037,515 bytes),
with fourteen configured values and no findings, errors or changed inputs.
Final source, local-gate and hosted-output scans and generated-private cleanup
are required as part of phase 10.

Earlier successful hosted runs are
[34815343612](https://github.com/StonyTark1117/Cinemarr/actions/runs/34815343612),
[34827174909](https://github.com/StonyTark1117/Cinemarr/actions/runs/34827174909)
and [34835244372](https://github.com/StonyTark1117/Cinemarr/actions/runs/34835244372).
Their recorded bundle scopes and failed predecessors remain in the
[remediation ledger](RELEASE_AUDIT_REMEDIATION.md). They do not certify a later
commit's CI outcome. For final acceptance, use the run whose `headSha` equals
the tested containing commit and verify all phase-10 conditions.

## Completion checklist

Use this checklist to assess the final containing commit. The committed evidence
manifest records completed artifact checks; final local/hosted receipts record
commit-level completion. An unchecked template item is a required verification,
not a claim that its implementation is missing. Preserve failed attempts and
require exact hashes when joining earlier evidence.

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

The display-capacity supplement runs with a controlled two-TV-stream limit and
three registered TVs. It verifies queued quality edits do not reserve duplicate
work, freeing a slot admits the waiting TV at the current timeline, retuning
cancels a queued request, and a rejected replacement preserves its working
stream and healthy sibling. An explicit later quality change recovers the
failed replacement. It retains original waiting/error controller captures and
requires the enclosing physical-audio and normal-shutdown checks.

Run it on the legacy, NeoForge 1.21.1, Fabric 26.1.2 and Fabric 26.2 boundaries:

```sh
CINEMARR_VIDEO_CLIENT_GATE=true CINEMARR_VIDEO_DISPLAY_GATE=true \
CINEMARR_VIDEO_CAPACITY_GATE=true CINEMARR_ALSA_PCM_TYPE=pulse \
CINEMARR_GATE_OUTPUT_ROOT=build/video-capacity-development \
bash scripts/run-dedicated-server-gate.sh 1.21.1-neoforge
```

Use a fresh output directory for each attempt. `verifyVideoCapacityRuntimes`
runs the four representatives serially and is required by `releaseMatrixGate`.
CI runs the same supplement for those artifact representatives. The injected
failure rejects only new fake-Plex starts: existing segment delivery and stop
remain available. This supplement is deliberately unavailable for real Plex.
