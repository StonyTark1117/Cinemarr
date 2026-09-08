# Historical hardening evidence

This file preserves superseded checkpoint notes from the 1.0 release hardening
plan. Statements such as "current", "passed", "green" or "complete" below refer
only to the candidate described by that checkpoint. They do not certify the
current worktree, final release bytes, or hosted CI.

See [the active release hardening plan](1.0_RELEASE_HARDENING_PLAN.md) and
[release acceptance](RELEASE_ACCEPTANCE.md) for current requirements and status.
Failed attempts and earlier successful candidates remain recorded here so that
later fixes do not erase the evidence that prompted them.

## Superseded narrative checkpoints moved on 2026-09-07

The preceding disconnect-corrected matrix
terminated with five passed profiles, one failed Fabric 1.20.2 reconnect and
fifteen unrun profiles. Logs and hashes are preserved in
`build/clock-fix-gate/disconnect-candidate-reconnect-rejection.json`; owned
processes, game port and audio modules were absent after teardown.

The current worktree now guards modern tracking with each loader's actual hello
acceptance predicate, before viewer registration/publication/visibility caching.
All modern hello paths synchronize normal players after acceptance, and late
Plex-manager installation synchronizes already accepted players. Legacy's
existing handshake guard is preserved. All four controllers now share bounded
local feedback and consistent command guards, display server errors in the open
screen, and use authoritative paused/playing messages after completed starts or
seeks. Browse offset overflow is rejected before HTTP. Core/root tests passed,
including six new feedback tests and the upstream-request boundary regression.

This is not release certification. Representative builds have passed; the
current owner-timeline correction and expanded actual-widget checks must pass before a
new full frozen build/matrix/external/native certification. The maintained gate
now checks modern JOIN-reset ordering and actual non-owner widget dispatch;
framebuffer review remains explicit. Exact-commit green hosted CI, artifact
parity, and the subsequent Jammarr feasibility report remain required.

The first focused Fabric 1.20.2 run now passes reconnect, actual non-owner
widget dispatch/error handling, physical audio (10 ms lag, 0.991657 correlation)
and cleanup in 233 seconds with all 785 source hashes unchanged. Thirteen
captures were directly reviewed. That review found stale elapsed-time and
UI seek/stream request positions. The current UI and CLI probes now use the
synchronized live clock. Server stream changes already ignored the supplied
client position; they now sample the server cursor after metadata preparation.
Paused seeking/reconfiguration preserves pause without starting a transcode,
and paused snapshots retain selected-stream metadata. The coordinator
regression and seven private-window ownership tests passed, as did Fabric
1.20.2, Fabric 26.2 and legacy representative builds. The first owner-widget
attempt was rejected because the recovery-only restart operation did not
advance the paused stream generation; the rejection and five reviewed partial
captures are preserved. A dedicated reconfiguration operation now advances
that generation without resuming paused playback.
The corrected frozen 788-input Fabric 1.20.2 focused run passed in 227 seconds,
with 10 ms physical audio separation and correlation 0.999708. All 24 captures
were directly reviewed: the playing clock advances, paused time stays fixed,
paused seek/stream changes retain pause, resume uses the saved cursor, and
playing seek/stream changes follow the live cursor. Permission/error feedback
and expiry remain readable. Three closed client logs contain six render-thread
reset acknowledgements. The private test unit, game port and task audio modules
were absent after teardown. Evidence:
`build/clock-fix-gate/live-timeline-v2-focus-1.20.2-fabric.visual-review.json`.
Other representative runtimes and all final-byte certification remain open.
The earlier focused review remains bound to its previous source hashes.

The first legacy widget attempt exposed a test-only open shortcut missing the
normal library request; that shortcut and its drift check are corrected. The
next attempt reached real owner controls and exposed an old-generation segment
request surfacing a false tracking error during successful seek. All three
server paths now distinguish obsolete existing-viewer traffic from malformed
or unauthorized requests and suppress obsolete asynchronous failures. Core
regressions and representative builds pass. The corrected legacy focused run
passed in 308 seconds with 10 ms audio separation; eighteen reviewed images
prove owner timeline behavior and input retention through ten session packets
and twenty UI rebuilds, as well as identifiable video after reconnect.
The integrated follower edit check now exercises selection and drafts through
actual owner state changes without competing input drivers. Paused legacy
captures also expose bare screen pixels rather than a retained program frame;
that gap is corrected in the new focused runs but still needs final-byte certification. A shared policy
and texture-only ownership transfer now preserve the last displayed frame
across newer paused revisions of the same session and movie. Decoder/audio
resources remain generation-owned and are closed normally. Two policy tests
and representative builds passed. Forge 1.7.10 and Fabric 26.2 then passed
fresh runs against identical 791-input source manifests in 316 and 298 seconds,
respectively, with 10 ms audio separation. Twenty-one relevant captures per
run were directly reviewed, including the paused TV outside the controller,
input retention and cleared drafts. Required client cleanup acknowledgements
and absent task units, game ports and audio modules were verified. The broader
797-input full-build attempt then failed after 560 seconds and all ten passing
GameTests: `verifyLogMarkerPolling` still used a healthy fixture without the
JOIN-reset evidence required by the gate. No snapshot or second build was
created. The failed report and log remain preserved under `paused-frame-build-*`.
The corrected fixture sets the real repository root and valid JOIN/session
markers, verifies the specific rejection reason in each closed client log, and
covers modern wrong-order and legacy disconnect-only reset behavior. All seven
log-marker tests and the other lightweight harness checks pass. A new
`paused-frame-r2` attempt uses its own manifest, logs and snapshot. Both forced
builds passed in 512 and 481 seconds, with all 76 root tasks executed, all ten
required GameTests passed and all sixteen artifacts inspected in each. All 18
bundle files are byte-identical, and a separate packaged-class audit verifies
the new UI, paused-frame and shared safety references in every JAR. Evidence:
`build/clock-fix-gate/paused-frame-r2-build-reproducibility.json` and
`build/clock-fix-gate/paused-frame-r2-packaged-path-audit-first.json`.
The 797-input freeze was rechecked after the build unit exited; all 21 runtime
ports were free and no task audio modules remained before the new serial matrix
started. Runtime results are development-launcher evidence from the frozen
source; exact packaged real-Plex certification is separate. All runtime,
external, native and hosted gates remain open. Both earlier legacy failures
remain recorded, not retried into an unexplained green result.

### Rejected disconnect-candidate checkpoint (historical)

Cinemarr remains a prerelease under certification after fixing a release-blocking
Fabric disconnect-cleanup defect. Direct log
inspection rejected the responsive-UI 26.2 real-Plex run despite its automated
pass: both clients attempted to close Cinemarr GPU textures from a Netty thread.
The same direct-reset pattern existed in all five Fabric adapters. A shared
client-executor/connection-ownership correction now passes core tests and a fresh
26.2 development-runtime gate, including six render-thread cleanup acknowledgements
across the leader, original follower and reconnected follower. Its first full
74-task build passed in 8m01s, including all ten required GameTests and inspection
of all sixteen JARs. The independent 74-task rebuild passed in 8m02s with the same
ten required GameTests; all eighteen bundle files are byte-identical. Complete
frozen-byte runtime and candidate certification remain required. Fresh Windows/ARM
decoder checks and packaged-payload parity passed, with both retained guests
confirmed off. All four representative artifacts passed fresh real-Plex
playback/control/reconnect and explicit cleanup gates, with twelve directly
reviewed images and 0–40 ms measured audio separation. Verified rollback copies
and stopped/disabled test-server baselines are preserved. All four recovery and
both restart/unloaded-chunk lifecycle cases also passed. The full runtime matrix,
remaining UI review, whole-evidence audit and final hosted gates remain open. The gate now
rejects the captured wrong-thread failure and requires client cleanup
acknowledgements before terminating launchers. Historical checkpoints below
remain bound to their recorded candidate bytes; the latest disconnect-corrected
checkpoint identifies the current bundle separately.

Cinemarr remains a prerelease. Responsive controller layouts, bounded library
pages, legacy server-page navigation, and search/session edit retention are now
implemented. All four representative development runtimes passed focused
320x240 logical-viewport editing review of 24 captures; the earlier 26.2
runtime-cleanup acceptance was subsequently withdrawn after the same
wrong-thread texture error was found in its logs. Shared geometry,
server-pagination, and private-X
allocation/cleanup regressions pass. These are focused regressions, not final
release certification.

The superseded responsive-UI sixteen-artifact build pair passed in 8m03s and 7m56s, with all
74 tasks executed and all ten required GameTests passing in each run. All
eighteen bundle files are byte-identical, and structural inspection confirms
the responsive controller and shared safety paths in all sixteen JARs.
The preceding UI-visibility bundle passed reproducible builds, ten GameTests, Windows/ARM
native checks with both retained guests off, four real-Plex cases, four recovery
cases and two lifecycle cases; those results remain tied to their earlier
bytes. The responsive-UI bundle also passed fresh Windows/ARM native checks, package
parity and a combined retained-guests-off audit. Its four representative JARs
are deployed with verified rollback copies and stopped/disabled baselines.
The current disconnect-corrected bundle still needs complete reviewed 21-runtime/control and adverse-network gates,
exact-byte external certification, final evidence/security and documentation audit,
scoped commits, push, and exact-SHA GitHub CI with local/hosted artifact parity.

Recent corrections address genuine failures found beyond green automation:
shared nonblocking media/cache and health policies across all server forks,
coordinator I/O outside its global state lock with bounded asynchronous
retirement and generation ownership, stable reconnect cameras, dead/obscured
capture rejection, reusable legacy probe-save preparation, legacy wide-button
texture corruption, overlapping controls/notices, and missing Minecraft 26
text alpha. Historical checkpoints below describe their recorded candidates,
not certification of current bytes; the successful subset is not release readiness.

Jammarr provides useful structural and legacy-hardening patterns. The earlier comparison recorded 78 implemented artifacts across 29 Minecraft versions and six loader families in its 1.1 manifest; those historical counts are design context, not a fresh compatibility certification. Cinemarr retains its 16-artifact/21-runtime release scope. Newer Jammarr target feasibility is assessed separately only after the preceding hardening, documentation and CI work is complete.

## Implementation checkpoint (2026-08-30)

Phases 1 through 8 are implemented and locally green. Phase 9 remains open because the final four representative artifacts have not yet been redeployed and exercised with a process-local DiscPanel token. Phase 10 remains open until that credentialed evidence exists, the scoped commits are pushed, and GitHub CI plus hosted-bundle hash parity are green for the exact SHA.

## Implementation checkpoint (2026-08-31)

The final code commit `c96da6005b83f00d989dfdf6c34cbd2f56005b1a` fixes a real-Plex-only deadlock that the small synthetic segments did not expose: clients requested the next eight-chunk window before acknowledging the previous window. The shared modern/legacy flow now acknowledges every bounded window before continuing, the server binds acknowledgements to the granted chunk range, and a deterministic 25-chunk regression test covers the contract. A diagnostic Forge 1.7.10 real-Plex run then passed two-client A/V, controls, reconnect, and cleanup; because its server still used the preceding artifact, that run is regression evidence rather than final-byte certification.

For `c96da60`, the Java-21 aggregate build passed all 16 artifact targets and all 10 required GameTests, and the complete local 21-runtime matrix passed in 48 minutes 54 seconds with private Xvfb displays and residue-free teardown. The final Forge 1.7.10 and NeoForge 1.21.1 adverse-network runs also passed slow delivery, transient recovery, bounded exhaustion, redacted failure, same-session recovery, and cleanup. GitHub Actions run `33377165241` is fully green: manifest validation, all 16 artifact/runtime jobs, byte-identical rebuilds, and the non-skipped `1.0 release gate` succeeded. Its 16-JAR hosted bundle passed deep inspection and every recorded checksum, and the local release index is byte-for-byte identical to that hosted bundle.

The four representative hosted artifacts are deployed on stopped, autostart-disabled DiscPanel servers with exact active hashes and disabled verified rollback copies. Phase 9 remains open: the process that held the DiscPanel token exited before exact-candidate real-Plex, recovery, and lifecycle recertification could start. Packaged native reruns also remain open because their process-local hypervisor inputs are unavailable. The final artifact and previously native-certified artifact contain identical decoder classes and all 618 identical native entries, so the code change has no native-path delta, but that parity is not being substituted for the plan's strict rerun. Documentation remains prerelease-truthful, and a final documentation commit plus clean CI for that exact pushed SHA is still required by phase 10.

Post-1.0 target-expansion feasibility is documented in [JAMMARR_TARGET_FEASIBILITY.md](JAMMARR_TARGET_FEASIBILITY.md). It does not expand the 1.0 matrix.

### Later 2026-08-31 findings

The `c96da60` checkpoint was not the final code candidate. Exact Quilt 1.20.1
real-Plex testing found that active version-specific screen forks lacked the
acceptance instrumentation used by the shared source. Commits `0360530` and
`f7e5492` put the instrumentation in every maintained fork and use remappable,
version-specific Minecraft 26 render-target adapters.

GitHub Actions run `33385409580` then correctly rejected `f7e5492`: its
1.21.1 Quilt clients produced highly correlated audio but were 160 ms apart,
over the 150 ms release limit. The leader had reached the old 16-sample
fallback with a best clock round trip of 258 ms, scheduled audio from an
imprecise offset, and received a good sample only after playback had begun.
Commit `8ed83d8d833d5912c4256e108e72ab7b21f0c4d6` removes that fallback for
synchronized media. Modern and legacy clients require at least eight samples
and a best round trip no greater than 150 ms; until then they continue startup
sampling every 250 ms instead of switching to the ten-second steady interval.
A regression test proves that arbitrarily many 200 ms samples never qualify.

Two consecutive private-Xvfb 1.21.1 Quilt two-client gates passed the corrected
code with 60 ms and 70 ms measured lag, correlations of 0.989659 and 0.992603,
matching identifiable video, and clean teardown. The complete local
`verifyAllTargets` gate then passed all artifact targets, Forge 1.7.10, all ten
required GameTests, release hygiene, source layout, and target-manifest checks
in 6 minutes 24 seconds. Hosted CI, exact-byte real-Plex/recovery/lifecycle
recertification, and strict packaged-native reruns remain decisive rather than
being inferred from these local results.

Run `33388139604` subsequently passed all 16 artifact/runtime jobs and the
non-skipped aggregate release gate for `8ed83d8`; its hosted 16-JAR bundle
passed checksums, deep inspection, and byte-for-byte local indexing. Exact-byte
Forge 1.7.10 real-Plex playback then exposed another generation-boundary bug:
initial two-client playback and pause succeeded, but resume generation 3 was
blocked by the unacknowledged segment window left by generation 1. Both
clients repeatedly received `A video transfer window is already awaiting
acknowledgement`, so the gate correctly stopped before seek and did not advance
to the other representative targets.

Commit `1cbd341b88023bffce340963f1890428f5be9712` allows a valid newer
generation of the same session to atomically supersede an older transfer
grant, while same-generation and unrelated-session contention remains blocked.
A registry regression test covers stale acknowledgements and ownership of the
replacement grant. Private-Xvfb control gates passed pause/resume, seek, stream
selection, follower reconnect, synchronized A/V, and clean teardown on both
Forge 1.7.10 and NeoForge 1.21.1. The full local target matrix also passed in
5 minutes 22 seconds. GitHub Actions run `33392358275` then passed all 16
artifact/runtime jobs and the non-skipped aggregate release gate for
`1cbd341`. Its hosted 16-JAR bundle passed checksum verification, deep
inspection, and byte-for-byte local indexing.

The exact hosted bundle was staged for the four DiscPanel representatives.
The first three replacements retained disabled, hash-verified rollback copies;
the 26.2 rollback upload returned HTTP 500. The workflow nevertheless advanced
because the deployment helper did not propagate that command-substitution
failure, and the first 1.7.10 acceptance preparation then received HTTP 500
from `UpdateServer` after five retries. No acceptance client or GUI was opened.
Commit `63e0150ad234236fb0489bfd15f93f9bfc9ff1f6` makes rollback and canonical
upload/import failures explicit fatal boundaries. Before retrying, the four
remote targets must be re-audited read-only, and only stopped,
autostart-disabled targets with exactly one active expected artifact and a
disabled verified rollback may proceed. The DiscPanel credential-holder exited
with the failed workflow, so exact-byte external recertification remains open.

Commit `2de12a725ea9c3f2bd05e4a2732455230ed7b70f` enforces that retry preflight:
each stopped target must have exactly one enabled Cinemarr artifact, and a
target whose active artifact is already exact is accepted only after a
disabled rollback is downloaded and its SHA-256 matches the hash prefix in its
canonical rollback filename. This prevents a partial prior deployment from
silently bypassing the recoverability contract.

Commit `501c9c629cb2e1db512611931074d0169c610667` then closes the replacement
failure path itself. The rollback trap preserves the original nonzero status,
disables any failed candidate, re-enables the verified backup, and verifies
that the backup is the only active Cinemarr artifact before returning the
original failure. Focused shell tests cover both state restoration and failure
status preservation.

The first CI run after that operational fix, `33396201966`, exposed an
independent five-second Forge Mavenizer connection timeout while downloading
the official 1.21.1 client mappings from Mojang. It failed before compilation
of the platform artifact and was not treated as a product pass or rerun.
Commit `9adb3c49e5dd49f7b1d9c15b9d0ec21c91c93f28` wraps only artifact-build
Gradle invocations with one retry when the captured failure contains both a
recognized Mojang/Forge artifact origin and a connection/reset/HTTP-5xx
signature. Compilation, tests, inspection, reproducibility, and every runtime
gate remain first-failure terminal. The same commit updates GitHub's official
checkout, Java setup, upload, and download actions to their current Node-24
majors so the final clean run must contain no Node-20 deprecation warnings.

GitHub Actions run `33398719853` then supplied another useful failure instead
of a release result. Fifteen artifact/runtime jobs passed, including their
deterministic rebuilds and uploads, but 1.21.1 Fabric failed the two-client
program-audio gate. The captured waveforms had 0.99988 correlation only after
a 330 ms shift, well outside the 150 ms contract, so the aggregate release gate
was correctly skipped. The diagnostic logs showed that the two clients started
from different future media boundaries under heavy runner load. They also
exposed unsafe source-start accounting: Cinemarr treated OpenAL's streaming
offset as a monotonic total even though Minecraft can unqueue processed buffers
and move that offset backward.

Commit `21431bfb44c051b6fb9d02a080afe1355f05eb0b` now measures elapsed time
since `alSourcePlay` as the monotonic source cursor, retains the backend offset
as a precision floor, and logs both values independently. A regression test
covers an offset that has moved back after more than one buffer of playback.
The exact private-Xvfb 1.21.1 Fabric gate passed locally with matching
identifiable video, zero underruns, 0.996978 audio correlation, and 80 ms
measured lag. Before push, unit tests and the nine 1.21.1, 26.1.2, and 26.2
artifact targets passed locally. The subsequent full `verifyAllTargets` pass
covered all 16 artifacts, both Quilt build profiles, Forge 1.7.10, all ten
required GameTests, release hygiene, source layout, and the target manifest in
3 minutes 46 seconds.

GitHub Actions run `33401793006` is fully green for documentation SHA
`4f6541f42abf72ec649c6d435e1677259badff74`: manifest validation, all 16
artifact/runtime jobs, byte-identical same-runner rebuilds, the repaired
1.21.1 Fabric gate, and the non-skipped aggregate release gate passed on their
first product/runtime attempt. The one permitted infrastructure retry was the
existing bounded 1.20.2 NeoForge asset warm-up; its later runtime gate passed.
The downloaded bundle contains exactly 16 JARs plus `manifest.json` and
`SHA256SUMS`; all checksums and deep inspection passed, and all 18 files match
the local release index generated from those exact hosted inputs byte for
byte. There were no Node-20-action or `punycode` deprecation warnings. A final
documentation-only commit still needs its own clean CI run. None of this
reopens the unchecked credentialed and packaged-native gates.

### Final-byte recertification checkpoint

The next working-tree rebuild exposed why exact bytes remain mandatory:
Fabric/Quilt 1.20.1 and NeoForge 1.21.1 no longer matched the earlier hosted
candidate hashes. The four representatives were therefore re-audited and the
rebuilt artifacts redeployed before any current result was accepted. Active
representative digests are `573906984a49d50d4a1421a008fffb61d50d1405cb3f635d05defb6a8eeb5e83`,
`236798fdaad4089a34daf4af47abe991d4e2bde7fc34416b6d7c355771c8bc98`,
`ef558b0f8bab0f58cf4df4c500338bd654499c7b0b3763722e1286204520a88e`,
and `a76afb77d23d0174da54d609ab99567ea622ce0fa68fd8768d79e70d78b55a63`
for the Forge 1.7.10, Fabric/Quilt 1.20.1, NeoForge 1.21.1, and Fabric 26.2
boundaries respectively. Every server retains a disabled hash-verified
predecessor and is stopped with autostart disabled outside its gate.

All four current artifacts passed real-Plex two-client playback and the full
disabled/degraded/manual/automatic recovery matrix. Forge 1.7.10 and NeoForge
1.21.1 passed restart-mid-build and unloaded-footprint lifecycle recovery. The
legacy lifecycle rerun also exposed a stale Jammarr dev JAR in its reusable
client workspace; the harness now removes only task-owned `jammarr-*-dev.jar`
files before staging the current dependency, and the clean rerun passed.

Native testing now provisions Windows x86-64 and Debian ARM64 once, then reuses
their installed disks. Both guests passed a second no-install boot against the
current benchmark bundle and were powered off. Their ready state and stopped
QEMU processes are independently audited; operation and cautious
reprovisioning are documented in `NATIVE_TEST_GUESTS.md`. A live-value scan
found no Plex, DiscPanel, or Proxmox credential and no real Plex endpoint in
source, retained text evidence, or any release JAR.

This checkpoint is still working-tree evidence. Phase 10 completes only after
the scoped tree is committed and pushed, every GitHub artifact/runtime job and
the aggregate gate are green for that exact SHA, and the downloaded 16-JAR
bundle is byte-identical to the locally indexed external-test bundle.

The decisive local runtime rerun completed all 21 manifest-derived profiles in
53 minutes 35 seconds. It caught and corrected two test-infrastructure gaps:
clients are now kicked through the real server console before launcher
termination so active OpenAL mixing cannot crash during forced teardown, and
each independent client uses a private JavaCPP extraction cache so concurrent
FFmpeg initialization cannot race through the host user's shared cache. Every
fresh profile recorded disconnect cleanup without a native JVM crash report
and then passed strict process, port, Xvfb, and audio-module residue checks.

### 2026-09-01 UTC final hardening checkpoint

The earlier green GitHub run `33460062096` was not accepted as final evidence.
Although all 18 jobs passed, a direct download comparison found that 10 of the
16 hosted JARs differed from the locally certified files. The differences were
packaging nondeterminism rather than source behavior, but either explanation
invalidates exact-byte certification. Release packaging now canonicalizes each
unsigned JAR with fixed entry order, timestamps, permissions, storage method,
and stable manifest attributes; rejects duplicate entries and signed archives;
and removes only inert versioned `module-info.class` entries. The latter is
required because Forge 1.7.10's ASM 5 scans those Java 9 descriptors and rejects
an otherwise valid fat JAR. Canonicalizing independent copies of all 16
historical local and hosted artifacts now converges to identical SHA-256 values.

Exact real-Plex testing then found two defects that the synthetic matrix had
not made decisive. Forge 1.7.10 used a six-second prefetch lead against roughly
eight-second Plex HLS segments, while a cold legacy decode could take six to
nine seconds. The shared lead is now 20 seconds, still below the server's
30-second bound. Earlier prefetch also exposed Plex manifests that listed a
segment before its URL materialized: a 404 exhausted the prior short retry and
could reset the manifest onto old timestamps. Modern and legacy servers now use
one bounded `PlexSegmentFetch` policy: HLS 404s receive an 11.6-second bounded
materialization window, ordinary transport errors keep the short 100/250 ms
retry, authentication/configuration errors fail immediately, and interruption
is preserved. Focused tests cover eventual materialization, exhaustion,
ordinary/offline failure, authentication, and interruption.

The rebuilt Quilt 1.20.1 gate subsequently caught a 1.32-second two-client
program-audio offset with 0.992 waveform correlation despite individually
healthy logical timelines. When Minecraft delayed the sound-thread probe, it
could queue program PCM before Cinemarr inserted the remaining compensating
silence. Audio startup now calculates the shared server boundary and output
latency on the OpenAL executor, prepends all required silence, pumps the entire
initial runway, and only then starts the source. The repeated Quilt run passed
with 0.991434 correlation and 20 ms lag; final NeoForge 1.21.1 passed with
0.954765 correlation and 10 ms lag.

The final local release build passed 71 forced tasks in 9 minutes 28 seconds: all 16
artifact targets, both Quilt build profiles, Forge 1.7.10, all ten required
GameTests, release hygiene, source-layout validation, and target-manifest
validation. A second complete forced build produced an identical 18-file
release directory. All 16 indexed JARs pass `SHA256SUMS` and deep inspection. Final
representative digests are:

- Forge 1.7.10: `c3c0845295a80b6359d0ba9335f4fcee04e6012d1651ad4ac7b64fd4dee0be21`
- Fabric/Quilt 1.20.1: `bd7ae3e9a90dde58135a5464fcb206091448ec95eca8acd63dc0d6edf94665bf`
- NeoForge 1.21.1: `480467db73f22ec26d86e750141beb994119587f1162a2ad897f8c5cc901ba4e`
- Fabric 26.2: `88bca91acc34eafdfed7e8289cc33b4068726882c046fe4618a8e86068037b46`

Those exact bytes passed the four-profile disabled/degraded/manual/automatic
Plex recovery gate and real-Plex controller-selected two-client A/V gate.
Forge 1.7.10 and NeoForge 1.21.1 also passed restart-mid-build and persisted
unloaded-footprint lifecycle recovery. Every managed server returned to stopped
with autostart disabled and retained a disabled, separately verified rollback.
The lifecycle helper now accepts an immutable `JAMMARR_LEGACY_BUILD_DIR`, so a
concurrent Jammarr build cannot change the legacy client dependency mid-run.

Fresh packaged-native evidence is retained under
`build/native-smoke/windows-x86_64/20260901T182751Z/` and
`build/native-smoke/linux-arm64/20260901T183204Z/`. Both reused their installed
headless guests, passed the current decoder bundle, and powered off. The
combined audit `build/native-smoke/retained-vm-audit-20260901T184323Z.json`
records both ready guests as powered off; their disks remain installed for
future release checks.

This remains prerelease working-tree evidence. Phase 10 still requires the
final-source 21-runtime rerun, scoped commit and push, a fully green first
product/runtime attempt for the exact pushed SHA, and unmodified downloaded
hosted artifacts that match the locally certified 16 JARs byte for byte. No tag
or publication is authorized by this checkpoint.

### 2026-09-06 audio-clock checkpoint

Late-join testing invalidated the September 1 candidate's certification:
healthy logical clocks did not reliably prove aligned physical output. The
current implementation accounts for consumed OpenAL buffers, the current
streaming cursor, leading scheduling silence, and measured output latency.
Its reported timeline can stall to correct an overestimate but cannot rewind
when processed buffers are unqueued. OpenAL 1.1's standard seconds cursor is
used when the optional source-latency extension is unavailable.

Focused first-attempt evidence under `build/clock-fix-gate/`:

- `monotonic-v7-physical-stall`: Quilt 1.20.2 late join with an injected 250 ms
  sound-executor setup delay passed at 0 ms lag and 0.991779 correlation.
- `monotonic-v8-simultaneous`: Quilt 1.20.1 simultaneous startup passed at
  0 ms lag and 0.999710 correlation.
- `no-latency-extension`: Quilt 1.20.1 with
  `JDK_JAVA_OPTIONS=-Dorg.lwjgl.openal.extensionFilter=AL_SOFT_source_latency`
  passed at 10 ms lag and 0.991186 correlation. Both clients logged
  `latencyMeasured=false`, zero starvations/underruns, and clean teardown.

The calibrated capture regression still rejects an injected 210 ms program
offset; alignment does not remove real program lag. Standalone alignment,
canonicalization, redaction, target-manifest, source-layout, and release-hygiene
checks pass. DiscPanel authentication was verified without exposing the token;
all four representative servers are stopped with autostart disabled. A fresh
read-only native-guest audit found both retained installations ready and off.

Final reproducible builds and all candidate-wide recertification remain open,
as do scoped commits, push, exact-SHA GitHub CI, and hosted artifact parity.
The initial build launched during this checkpoint overlaps the fallback edit
and is diagnostic only, not either member of the final reproducibility pair.

Review also exposed server-thread contention in modern and legacy
`ActiveMedia`: synchronized metadata/cache readers shared the monitor held
across segment HTTP fetches and the bounded HLS materialization retry. A slow
fetch could therefore block request validation and diagnostics on the server
thread. Metadata now owns an immutable list snapshot, its readers do not take
the download monitor, and cache reads use the cache's independent short lock.
Downloads remain serialized on worker threads, with visible retry counters.

New modern and legacy concurrency regressions failed before the fix with a
metadata-read timeout and mutable-list assertion, then both complete unit
suites passed after it. Evidence is in
`build/clock-fix-gate/{server,legacy}-monitor-regression-{before,after}.log`.
The in-flight candidate build was cancelled before indexing; the new pair is
recorded in `final-lock-build-{first,second}.log` and compared against
`build/reproducibility/20260906-clock-and-fetch-first/`.

The full local runtime tasks and CI previously enabled two-client playback
without setting `CINEMARR_VIDEO_CONTROL_GATE`. They now enable the existing
pause/resume, seek, stream-change, and follower-reconnect scenarios for every
maintained profile. Prior playback-only matrix results do not satisfy that
part of phase 8. The final matrix must retain each profile's
`video-controls.evidence.txt` as well as physical A/V and cleanup evidence.

The corrected code passed two forced full builds in 8m06s and 8m02s. Both
executed 71 tasks, passed all ten required GameTests, and inspected all sixteen
JARs. The complete eighteen-file release directories compare byte for byte,
and all `SHA256SUMS` entries pass. Windows and ARM native checks also passed;
their current runs and exact native-payload parity are recorded in
`COMPATIBILITY.md`.

September 6 deployment preflight found all four canonical Cinemarr mods
disabled, with Jammarr enabled and the test servers stopped. No replacement
occurred on that first preflight. Their original disabled state is recorded in
`build/clock-fix-gate/discopanel-original-enable-state.json`; only the canonical
Cinemarr mods were temporarily enabled for artifact replacement and acceptance.
Final cleanup must restore Cinemarr to disabled on these four shared servers,
leave all other mod settings intact, and verify stopped/autostart-disabled state.

The first newly enabled control run exposed a harness precondition error:
the scenario required a 640x360 non-owner UI but its launcher defaulted to
854x480. The UI itself reported no clipping and correct permissions. Control
runs now automatically launch the follower at the required small size. The
corrected Quilt 1.20.1 run with Mod Menu and the latency extension disabled
passed its complete control sequence, final physical audio comparison
(10 ms lag, 0.996673 correlation), and clean teardown. Evidence:
`build/clock-fix-gate/no-latency-controls-small-window/`.

The shared DiscPanel servers also lacked both Cinemarr configuration files.
Their existing Jammarr configuration still supplied the Plex endpoint, and
the Cinemarr environment credential remained available. The previously used
video section 1 was validated live before recreating
`world/serverconfig/cinemarr-server.toml` with an empty file token and
`world/serverconfig/cinemarr-libraries.toml` restricted to that section. These
Cinemarr test configurations are retained for reuse; Jammarr configuration
and mod selection were preserved.

Remote legacy Jammarr now matches commit `8eccf06`, not the September 1
snapshot. An isolated build of that exact commit produced a production
payload identical to the remote artifact and its paired development JAR.
The reusable pair is `build/jammarr-legacy-snapshot-8eccf06/`; pass it as
`JAMMARR_LEGACY_BUILD_DIR` for subsequent legacy external gates. The temporary
source checkout was removed after validation. External recovery, real-Plex,
and lifecycle runs now use this pair and the new Cinemarr candidate.

All four September 6 exact-candidate recovery gates subsequently passed:
disabled/degraded/manual/automatic states, redaction, original configuration
restoration, and stopped/autostart-disabled teardown. Their evidence under
`build/discopanel-plex-recovery/` identifies these candidate SHA-256 digests:

- Forge 1.7.10: `121615012ac1f71d950f2dd1f80ee673f842966196498c323ff9903368329ebe`
- Fabric/Quilt 1.20.1: `2648f85df25f82614392c785a4d539ca6656bd6480831b5379637a911c442c42`
- NeoForge 1.21.1: `d08d644ff9743c3c2837c282f114719544571026497ff8e433ffa5943f7f1159`
- Fabric 26.2: `d6142f8ea2391ab9f5d23112298e4274583728942af7faeba1323c8e148b2bd5`

All four real-Plex two-client gates then passed controller selection, controls,
alternate streams, non-owner rejection, reconnect, and clean teardown on these
same hashes. The final Forge, Quilt, NeoForge, and 26.2 captures measured
40/10/0/10 ms separation respectively, with correlations
0.979987/0.992439/0.963522/0.994584. Current records are in
`build/discopanel-real-plex/` and `build/clock-fix-gate/real-plex-*-final.log`.

The first legacy lifecycle run correctly rolled back after snow obstructed
pixel 137, before the required 256-pixel restart checkpoint. That failed run is
retained in `build/discopanel-lifecycle/1.7.10-forge-20260906-unprepared-chunks/`
and `build/clock-fix-gate/lifecycle-1.7.10-forge-final.log`. Legacy chunk
loading can synchronously populate neighboring terrain during block updates;
clearing only the footprint had not established the fixture's loaded-world
precondition. The lifecycle harness now loads the surrounding player view with
a separate observer before the builder clears the footprint, retaining its
ticket until the checkpoint. Production obstruction/rollback behavior and
candidate bytes are unchanged. The corrected legacy run and the NeoForge run
both passed checkpoint/restart, persisted unloaded-footprint recovery, and the
final air-block assertion. Logs are
`build/clock-fix-gate/lifecycle-*-prepared-final.log`; current evidence is under
`build/discopanel-lifecycle/<runtime>/`. The failed sequence had not advanced to
local adverse-network or runtime tests.

All four shared servers have now been restored to stopped/autostart-off with
the canonical Cinemarr mod disabled, matching the recorded original state.
Other mod settings are unchanged and disabled rollback copies remain. The
read-back audit is `build/clock-fix-gate/discopanel-restored-state-20260906.json`.

A separate log-polling regression reproduced false timeouts when `grep -q`
closed a large log pipe early under `pipefail`. Four actual waiters failed the
large-log test before the correction; all five now pass, still rejecting
missing markers and markers before the requested checkpoint. This verification
fix does not alter candidate bytes. `scripts/test-log-marker-polling.py` runs in
the aggregate local gate and hosted manifest-validation job; before/after logs
are `build/clock-fix-gate/log-polling-regression-{before,after}.log`.

The complete local runtime/control matrix, adverse-network gates, final evidence
scanning, and exact pushed-SHA CI/hosted parity also remain open.

### Later September 6 evidence corrections

Deep scanning of tracked/untracked source, all sixteen nested release JARs,
retained logs, and decompressed NBT/region saves found private endpoints in
rotated gzip logs and saved acceptance-client server lists. The text-only
redactor had skipped both formats. It now recompresses redacted gzip logs and
uses same-byte-length masking in server-list NBT, preserving framing. Its new
regression failed before the fix and passed after it. The cleaned scan covered
20,650 files and 522,978 unique payloads with zero live-value matches; record:
`build/clock-fix-gate/secret-scan-20260906-cleaned-pre-matrix.json`. Fresh runtime
outputs and the next candidate still need the final scan.

One adverse invocation was invalidated when the agent edited its running shell
script and disrupted subsequent command reading. It was stopped and archived
under `build/adverse-network-20260906-interrupted-harness-edit/`; it is not an
acceptance pass. A frozen-script invocation then passed Forge 1.7.10 adverse
network/controls, but its NeoForge continuation was intentionally stopped before
the camera correction below. The full matrix did not start. Script/source input
hashes must remain frozen during subsequent execution; stop the owning process
and verify cleanup before editing a running harness.

Direct image inspection then contradicted the apparent four-profile visual
success. Quilt 1.20.1 and Fabric 26.2's reconnected followers captured the other
avatar instead of the TV. B had joined first and A second; when B rejoined, its
player-list index changed and both occupied the same camera position. The shared
`ProtocolLimits.videoProbeCameraX` now assigns A/B fixed, distinct positions;
unit tests cover reverse arrival/reconnect, and source-layout validation requires
every manifest-listed adapter to use it. This is acceptance setup, not a change
to normal player movement. The new candidate build pair is recorded in
`build/clock-fix-gate/stable-viewpoints-build-{first,second}.log` and will be
compared against `build/reproducibility/20260906-stable-viewpoints-first/`.
Both builds passed all 72 tasks, all ten GameTests, and deep inspection in
8m32s and 7m48s, with byte-identical eighteen-file bundles. Candidate hashes are:

- Forge 1.7.10: `45c06f01d3e2b00738d142dfc8af2a50f25055a026f434318d01c7c5f79e7fd6`
- Fabric/Quilt 1.20.1: `92653a10dc7f9ff8a48f435f630830a70fd1caf6c13215734e18d0d2ff3cfdf8`
- NeoForge 1.21.1: `2d9981817f2e80876f967e16ca54219104e470e8a3a81660cf54034137f007ad`
- Fabric 26.2: `bb616a8514db5c312e37c746ada81f883d7d47a89b9aca2bfa00344be1f267b8`

The earlier external outputs are preserved in
`build/discopanel-{real-plex,plex-recovery,lifecycle}-pre-viewpoints-20260906/`.
Fresh native checks and deployment/acceptance are in progress. Before launching
new video clients, their generated options now disable pause-on-focus-loss so
an unfocused private-X window does not cover the program with the game menu.
This harness-only option does not change the packaged bytes. All validation
inputs are frozen in
`build/clock-fix-gate/frozen-validation-inputs-viewpoints-20260906.sha256`.
Final-byte runtime/external/native certification and hosted CI remain open.

### Shared-server correction after stable-camera review

The stable-camera candidate passed Windows x86-64 and Linux ARM64 decoding at
144p, 480p, and 1080p; both installed guests were retained powered off. The audit
is `build/native-smoke/retained-vm-audit-20260906-viewpoints.json`. Quilt 1.20.1
and Fabric 26.2 passed real-Plex automated assertions, but only Quilt's two
framebuffers were directly reviewed before the sequence was stopped during
Forge 1.7.10. Quilt's follower now shows the TV after reconnect. These partial
runs do not complete the external matrix.

Quilt's leader also displayed `Invalid video health report` during a legitimate
restored-session → stop → new playback transition. Well-formed telemetry from
an obsolete generation must be discarded without chat or diagnostics mutation;
malformed telemetry remains rejected. `VideoHealthPolicy` now enforces this in
all three server managers, including signed drift bounds that cannot overflow
on `Long.MIN_VALUE`, nonnegative counters, and known telemetry states. Aggregate
diagnostic counters use longs. Coordinator-based tests cover the actual
restore/stop/start/viewer-departure sequence. The common runtime gate now fails
on this error in either client's current or pre-reconnect log.

The active Minecraft 26 server fork had also missed the earlier metadata-lock
and materialization-retry corrections, confirmed in the compiled artifact at
`build/clock-fix-gate/mc26-active-media-before.txt`. `ActiveVideoMedia` replaces
the three duplicate cache implementations in core, preserving serialized,
bounded downloads without blocking server-thread metadata reads. The former
root/legacy concurrency tests are consolidated into core, with an additional
production fetch-path test covering 404 retries, metadata reads during a held
fetch, response closure, hashes, and cache reuse. Source-layout validation
requires every server manager to use the shared implementation and health
policy. It also verifies that all three modern audio implementations differ
only in their legitimate camera API accessor.

All four shared servers were restored to stopped/autostart-off with canonical
Cinemarr disabled and other mod settings unchanged; read-back evidence is
`build/clock-fix-gate/discopanel-restored-after-viewpoints.json`. No candidate
containing these new shared-server changes has yet completed the full build,
runtime, external, native, or hosted CI gates.

### Held-I/O coordinator regressions: before-fix evidence

The complete core suite passed after the shared-media and health corrections,
including four `ActiveVideoMediaTest` tests and three `VideoHealthPolicyTest`
tests (`build/clock-fix-gate/shared-server-core-tests.log`). A subsequent audit
found that `VideoSessionCoordinator.play`, stop, pause, suspension, untune, and
close still invoke media start/close callbacks while holding the coordinator's
global monitor. The adapters perform synchronous Plex HTTP work in those
callbacks. Worker-owned starts can therefore block the server's ordinary tick,
tracking, and query paths; server-thread pause/stop/removal can perform network
I/O directly as well. Fixing only segment-cache locks does not fix this.

The first new full build was intentionally terminated before further source
edits; `build/clock-fix-gate/shared-server-build-first.log` is an interrupted
build, not a pass, and no second build ran. Its frozen-input manifest is now
superseded. No runtime or external validation was started on these bytes.

`VideoSessionCoordinatorResponsivenessTest` holds media start/close callbacks
behind latches until the queried operation finishes. All three tests fail on
the current implementation with two-second timeouts:

- Server-thread queries during a held Plex start.
- Stop cancellation while that start is still held, followed by rejection and
  closure of any late returned handle rather than resurrection of playback.
- Server-thread queries during a held Plex close.

Before-fix evidence is
`build/clock-fix-gate/coordinator-responsiveness-before.log` (3 tests, 3 failures).
The earlier removal race test released the held start immediately after
launching the removal thread; it tested eventual cleanup, not responsiveness.
These new failures are deliberate regression evidence, not waived tests.

The next implementation must separate bounded media I/O from coordinator state
locks, reserve start ownership/capacity before releasing the lock, reject and
close superseded starts, and ensure pause/stop/removal/tick paths do not perform
blocking network work on the game server thread. Shutdown must reject new
starts and account for every pending or retiring handle. Tests must cover
concurrent replacements, stream-limit reservations, failures, removal, and
shutdown before restarting the full candidate build pair. Existing generation,
stream-limit, and cleanup guarantees must remain intact.

Fresh read-only DiscPanel and Proxmox checks after this interruption confirmed
all four shared servers stopped/autostart-off with no enabled Cinemarr mods,
and both retained native guests ready and powered off. Credentials remain
available from the user-provided files. The release goal is active, not blocked;
there has been no new commit, push, or exact-SHA CI certification.

### Coordinator correction: local regression checkpoint

`VideoSessionCoordinator` now reserves per-session start ownership, a unique
generation, and bounded stream/handle capacity before releasing its state lock
for media creation. Stop, pause, suspension, retune/removal, and shutdown
invalidate pending starts; late handles are retired rather than published.
Cancelled and failed generation numbers are never reused. The three production
adapters opt into a dedicated, bounded cleanup executor, with at most twice the
configured stream count owned across active, starting, and retiring media.
Cleanup saturation rejects new playback explicitly rather than dropping closes
or executing HTTP on the game thread. Shutdown cancels workers, rejects new
starts, drains late and retiring handles, and reports any cleanup failure.

Seek adapters now consume the returned snapshot instead of assuming a
generation increment of exactly one. Playback library/stream-option writes
move to generation-validated main-thread completions. A separate playback
metadata revision survives pause/suspension while changing on replacement, so
checkpoints cannot combine a newly started item with the preceding item's
options. Modern completion scheduling also checks manager closure before
enqueueing and before executing its action.

The former three held-I/O failures now pass. The 22 coordinator tests include
bounded cleanup pressure, reserved stream capacity, cancellation followed by a
newer start, failed-generation non-reuse, actual seek results, uninterruptible
late-start shutdown, observable cleanup failures, dormant paused restoration,
metadata revision, and stale completion rejection. The root/core verification
log is `build/clock-fix-gate/coordinator-root-and-core-final-tests.log` (10s).
Recorded totals: core 139 tests with zero failures/errors and one opt-in test
skipped; root 39 tests with zero failures/errors and two environment-dependent
tests skipped. Cross-loader builds, GameTests, and runtime/native/external
recertification are not inferred from these results.

All adapters expose `mediaStarts`, `mediaRetiring`, and `mediaCloseFailures`.
Real-Plex teardown now requires all three to be zero in addition to its actual
Plex session/transcode checks. Local runtime gates reject logged media lifecycle
cleanup failures. The five shell-log regression tests pass, including negative
cases for missing/new nonzero lifecycle counters; source-layout verification
requires asynchronous retirement, shared health/fetch policies, guarded
metadata, and actual seek generations in every server fork.

The corrected forced build pair is running with input hashes frozen in
`build/clock-fix-gate/frozen-inputs-coordinator-20260906.sha256`. Logs are
`build/clock-fix-gate/coordinator-build-{first,second}.log`; only after the first
build succeeds is its eighteen-file bundle copied to
`build/reproducibility/20260906-coordinator-first/`. The second build must match
that snapshot byte for byte. Until both commands finish successfully, this is
an in-progress verification step, not certification. Legacy playlist tests now
reference the shared core segment type and retain their URI/timing assertions.

### Coordinator candidate: reproducible bundle complete

Both forced builds finished successfully: 8m06s and 7m55s, 72 tasks each, all
sixteen artifacts and all ten required GameTests. All eighteen bundle files
are byte-identical; summary evidence is
`build/clock-fix-gate/coordinator-build-reproducibility.json`. The packaged-path
audit at `build/clock-fix-gate/coordinator-packaged-path-audit-first.json`
confirms that every artifact's server manager references the shared media and
health policies, asynchronous coordinator constructor, and generation-guarded
metadata path. This is packaging evidence, not runtime acceptance.

Current representative artifact SHA-256 values are:

- Forge 1.7.10: `eb71bcad508ef9497cdf1d78e6efe01dc159af981695591d78e08e9d0b51a91b`
- Fabric/Quilt 1.20.1: `734a0324e0bc337363954a07d0770612102c01a917a04024fd97cf929f6db787`
- NeoForge 1.21.1: `9025e4e47aeff6c42e26d532f150e627562ae1203f5167c77c34cbc928fcfd33`
- Fabric 26.2: `5ff834c3164bc1e14e2c2bec3b4b9cded51a9d61ec2af806236167e55ff8bfdc`

The earlier partial stable-camera real-Plex outputs are retained at
`build/discopanel-real-plex-pre-coordinator-20260906/`. Before new deployment,
fresh read-only checks found all four shared servers stopped/autostart-off
with Cinemarr disabled (`build/clock-fix-gate/discopanel-before-coordinator.json`),
and both native guests ready/off
(`build/native-smoke/retained-vm-audit-20260906-before-coordinator.json`).
Exact-byte deployment completed for all four representatives, with disabled,
hash-verified rollback copies retained. The post-deployment readback at
`build/clock-fix-gate/discopanel-restored-after-coordinator-deployment.json`
confirms stopped/autostart-off servers, Cinemarr disabled, and unchanged other
mod settings. Each subsequent external case temporarily enables only its
candidate and restores that baseline in its finalizer.

The native bundle preparation passed in 3s/13 tasks. Fresh Windows and ARM
runs then passed all 144p/480p/1080p software rows, with evidence at
`build/native-smoke/windows-x86_64/20260906T142401Z/` and
`build/native-smoke/linux-arm64/20260906T142743Z/`. The combined
`build/native-smoke/retained-vm-audit-20260906-coordinator.json` confirms both
installed guests ready/off. `build/native-smoke/20260906-coordinator-native-parity.json`
matches 90 tested native entries against every release JAR, 16 representative
decoder classes, and each run's fixture hashes. This does not substitute for
Minecraft runtime acceptance.

Current Quilt 1.20.1 and Fabric 26.2 real-Plex cases passed automated controls,
two-client playback, and clean teardown. Direct framebuffer review also found
unobscured identifiable program video on both clients, including reconnected
followers, with no stale-health error visible. The remaining external cases,
adverse-network and full reviewed runtime matrix, final secret scan, scoped
commits, and exact-SHA hosted CI/artifact parity remain open. Cumulative
per-case results are recorded at
`build/clock-fix-gate/coordinator-external-results.json`.

### Direct UI review supersedes the coordinator candidate

All four representative automated real-Plex runs passed, but the direct review
at `build/clock-fix-gate/coordinator-real-plex-visual-review.json` rejected the
legacy leader: a death screen covered otherwise playing video. Its saved
`Health` and `HealF` were zero, and a retained server rotation recorded the
probe's earlier lifecycle fall at 12:26:57; there was no new death in the
playback invocation. The legacy controller screenshot also showed texture
corruption on title/library buttons wider than vanilla's 400-pixel two-quad
limit. Modern screenshots showed the status notice overlapping subtitles;
Minecraft 26 omitted title/status text with zero-alpha colors. Episode-next
placement also overlapped presentation controls at the small-window size.

The current corrections are:

- Legacy buttons tile only the 200-pixel sprite's interior and preserve its
  borders. Two tests verify exact coverage, odd widths, and bounded UVs for
  fourteen widths and three visual states. The legacy Gradle 9.5/Java 26 test
  invocation passed in 4s; the preceding invocation used the root Gradle 8.8
  wrapper by mistake and failed before compilation, so it is not a test result.
- All four screen implementations reserve a separate notice line above stream
  controls and use explicit opaque text colors. Modern episode-next controls
  move to the stream row. Source-layout checks enforce these fork invariants.
- All sixteen client adapters require a living player and no open GUI before
  capture readiness. A shared predicate regression rejects all other states;
  direct framebuffer review is still required for avatar/geometry occlusion.
- Before a legacy real-Plex run, only the two named offline probe saves are
  prepared while stopped. The bounded NBT helper preserves unrelated fields,
  clears prior fall/death state, and never heals during playback. Both players
  must be alive afterward. Their original save bytes are restored and readback
  verified on success or failure. Five helper tests pass, and a live stopped-
  server prepare/check/restore roundtrip passed for both original saves at
  `build/clock-fix-gate/legacy-probe-state-roundtrip.log`. CI runs the helper tests.

The root/core and source/log gates passed in 13s before the final opaque-color
edit; final verification must include that edit. The external sequence passed
Quilt recovery, then its exact owning Fabric 26.2 recovery wrapper was stopped
through cleanup before any source/harness edits. Exit 130 is an intentional
interruption, not an accepted recovery result. All four servers were freshly
verified stopped/autostart-off, Cinemarr disabled, and unrelated mod settings
unchanged in `build/clock-fix-gate/discopanel-after-ui-review-interruption.json`.
No lifecycle case from this sequence ran. The previous native guests remain
installed and off. Fresh candidate builds, native/runtime/external checks,
secret scan, docs, commits, and exact-SHA GitHub CI/parity remain required.

The corrected source and harness are now frozen at
`build/clock-fix-gate/frozen-inputs-ui-visibility-20260906.sha256`. Two forced
`verifyAllTargets inspectReleaseArtifacts` builds are running serially, with
logs `build/clock-fix-gate/ui-visibility-build-{first,second}.log`. The first
successful bundle will be retained at
`build/reproducibility/20260906-ui-visibility-first/`; the second must match
all eighteen files exactly. No running harness or build input may be edited
during this pair. Completion is not inferred from its launch.

Both frozen UI-visibility builds subsequently passed: 7m56s and 7m59s,
73 tasks each, all sixteen artifacts, and all ten required GameTests.
All eighteen bundle files are byte-identical, recorded at
`build/clock-fix-gate/ui-visibility-build-reproducibility.json`. The packaged
class/disassembly audit at
`build/clock-fix-gate/ui-visibility-packaged-path-audit-first.json` verifies
the capture predicate and opaque UI text values in every artifact, plus the
legacy tiled-button implementation. Direct runtime screenshots remain required.

Superseded coordinator playback/UI evidence is preserved at
`build/discopanel-real-plex-pre-ui-visibility-20260906/`, and its recovery
evidence at `build/discopanel-plex-recovery-pre-ui-visibility-20260906/`.
The visual-review JSON paths now point to those archives. Exact-byte deployment
of the UI-visibility bundle and native preparation are in progress, with
one-case-at-a-time external runs to permit direct review before advancing.

### UI-visibility candidate: native and first two live cases accepted

Exact-byte deployment completed in 205s. The report
`build/clock-fix-gate/ui-visibility-deployment.result.json` confirms all four
servers restored to stopped/autostart-off, Cinemarr disabled, original overrides
restored, and unrelated mod settings unchanged. Verified disabled coordinator-
candidate rollback copies are retained. Current representative hashes are:

- Forge 1.7.10: `f0dd79d47b1043d02049e210a93c274c79be49a36480082fefc870989074f101`
- Fabric/Quilt 1.20.1: `1112e3ca09ee33aa73e3b16673302b2009bd2bc17caed32dcc1d5462b560b8dc`
- NeoForge 1.21.1: `f335b44e54e36daebb6664664d29066f104b7d15c51c0d56ed2dfb73be0f090c`
- Fabric 26.2: `013c5a4a6c8e664368674100ed577fa3ff6533e5417177d3a0b1cda8769bdeb1`

The current native bundle preparation passed in 3s/13 tasks. Windows and ARM
then passed in 234s and 301s, with evidence at
`build/native-smoke/windows-x86_64/20260906T151455Z/` and
`build/native-smoke/linux-arm64/20260906T151849Z/`. All 144p/480p/1080p rows
passed. The current `20260906-ui-visibility-native-parity.json` report matches
90 tested payload entries in every release JAR, 16 representative decoder
classes, fixture hashes, and invocation IDs. The combined
`retained-vm-audit-20260906-ui-visibility.json` confirms both installed guests
ready and off; no OS reinstall was needed.

Forge 1.7.10 and Fabric 26.2 passed fresh exact-byte real-Plex controls,
reconnect, non-owner rejection, two-client program A/V, and teardown. Direct
review of both playback captures and each controller screenshot verified the
specific visual corrections. Legacy's dead-player/texture corruption is gone;
26.2's title/status text is visible and its notice does not overlap stream
controls. The vanilla movement-tutorial toast remains in the 26.2 capture's
upper-right area and is not claimed as a Cinemarr widget. Legacy's saved
probe players remained alive during testing and their original bytes were
restored afterward. Audio correlations were 0.917769/0.979617 with absolute
separation 40/20 ms, respectively. Direct review and image hashes are recorded
at `build/clock-fix-gate/ui-visibility-real-plex-visual-review.json`.

Quilt real-Plex is running next. NeoForge real-Plex, all four recovery cases,
both lifecycle cases, adverse-network and the full reviewed local runtime
matrix, final secret/evidence scan, final documentation/commits, and exact-SHA
GitHub CI with hosted artifact parity remain open. No final acceptance is
inferred from the successful subset.

All four UI-visibility real-Plex cases have now completed successfully. The
direct-review JSON records all eight playback captures and all four controller
captures, their image hashes, matching deployed/current artifact digests, and
per-case cleanup reports. Current audio results are:

| Representative | Correlation | Absolute audio separation |
| --- | --- | --- |
| Forge 1.7.10 | 0.917769 | 40 ms |
| Quilt 1.20.1 | 0.973985 | 0 ms |
| NeoForge 1.21.1 | 0.974175 | 0 ms |
| Fabric 26.2 | 0.979617 | 20 ms |

Every run restored its selected server to stopped/autostart-off, Cinemarr
disabled, original overrides, and unchanged unrelated mod settings. Quilt's
current recovery case also passed in 139s; the remaining recovery and both
lifecycle cases are progressing serially, with one result JSON per case under
`build/clock-fix-gate/`. The global final scan and reviewed 21-runtime/control
matrix, commits, and exact-SHA GitHub/hosted-parity gates are still required.

### Normal GUI scale and browse coverage correction

The earlier 640x360 scale-one probe missed ordinary 320x240 and 480x270
logical viewports. `VideoControllerLayout` now supplies one Java-8-compatible
layout for all four screen implementations. Narrow layouts separate playback
and presentation controls; browse/queue row counts reserve the notice and
footer space. Library navigation has bounded pages covering all 64 protocol-
allowed libraries. Forge 1.7.10 now exposes previous/next server-page controls;
all screens reject browse responses for a different query or page. Modern
screens retain text-field objects and restore semantic keyboard focus around
Minecraft's entire widget rebuild lifecycle.

The shared geometry tests exercise 13 widths by six heights, browse and queue
views, every footer control including next-episode, text rows and library tabs.
They reject both clipping and rectangle overlap. A second test checks library
page coverage for every count from zero through 64. Root compilation/core tests
and the legacy test suite pass. The live control gate now requires a 640x480
physical follower window at scale two, reporting a 320x240 logical UI with
zero clipped **and** overlapping widgets.

The first focused NeoForge run passed its automated control/reconnect and clean
shutdown gates (`build/responsive-ui-runtime-20260906/`), but direct inspection
rejected placeholder text spilling outside resized edit boxes and typing focus
lost during state updates. Neither was detected by the rectangle checks.
Placeholder width limits and focus restoration around `super.rebuildWidgets()`
were corrected afterward. That first run is regression evidence, not UI
acceptance; focused visual revalidation is still required before the next
full artifact freeze. No commit, push, hosted CI certification or release has
been performed for these changes.

The corrected NeoForge focused run subsequently passed controls, reconnect,
and clean teardown in `build/responsive-ui-edit-runtime-20260906/`. A bounded
observer verified ownership of its private X server and captured actual mouse
and keyboard input at 320x240 logical resolution. Direct review of all six
captures confirmed the complete draft and selected substring survived at least
two logged state rebuilds (playing to paused), replacement affected only that
selection, and both search/session drafts survived Queue/Browse toggles.
`build/clock-fix-gate/responsive-ui-edit-neoforge-visual-review.json` records
that limited acceptance. Legacy, 1.20.1 and Minecraft 26 focused checks are
still pending; this is not final packaged-artifact certification.

Subsequent legacy, Quilt 1.20.1 and Fabric 26.2 runtime/control runs all passed,
but their focused editing evidence is not fully accepted. Legacy's synthetic
short Shift chords did not establish a selection, so the observer now holds
the modifier across frames. The modern acceptance shortcut also now requests
libraries, matching a normal controller open and exercising populated rows.
Two refused private-display observations exposed an allocation race in the
installed `xvfb-run -a`: concurrent launchers can select the same number before
either server reserves it, and that wrapper does not require successful Xvfb
startup before launching the client against that number.

`scripts/run-private-xvfb.sh` replaces all three dedicated-gate GUI launch
sites. It uses Xvfb's atomic binding/readiness descriptor in a separate high
display-number range, verifies the owned server before launching a command,
disables TCP, and cleans up only its command group and X server. Three real-X
regression tests cover distinct concurrent displays with their requested
geometries, signal cleanup without stopping a second client, and propagation
of a failing command's exit status. They pass locally and are included in
`verifyAllTargets` and hosted artifact jobs. The observer now binds input to
the exact display/PID recorded by its own client's launcher. All earlier
refused or incomplete probes remain explicitly unaccepted, not silently
reclassified as passing.

All four corrected scaled-UI/edit cohorts now have successful runtime/control
and cleanup results plus direct review of six captures each. The accepted
cohort index is `build/clock-fix-gate/responsive-ui-private-x-reviewed-summary.json`;
it verifies all 24 capture hashes and points to each result and review. Forge
1.7.10 and Fabric 26.2 use the private-X cohort; Quilt 1.20.1 and NeoForge 1.21.1
use the subsequent one-case `private-x-final` invocations. The interrupted
long-batch Quilt run remains unaccepted, with manual teardown/restoration
recorded separately. No game ports, private X servers or task audio modules
remained after the accepted cohort. Report filenames containing dotted version
numbers were corrected without changing their contents or screenshot hashes.

A further core regression verifies a 41-item catalogue across offsets 0, 20,
40 and back to 0, including lookahead preservation, no duplicated/dropped items,
and the final `hasMore=false` boundary. All core tests and the private-X tests
pass. The source guard also requires identical modern browse/control behavior
across the root, 1.20.1 and Minecraft 26 screen bodies, with only the existing
capture-field difference normalized. These two verification-only additions
did not change any runtime source or launcher after the accepted UI cohort.

The new frozen sixteen-artifact build pair subsequently passed in 8m03s and
7m56s, with all 74 tasks executed and all ten required GameTests green in
each run. Both bundles contain exactly sixteen inspected JARs plus the index
and checksums; all eighteen files are byte-identical to
`build/reproducibility/20260906-responsive-ui-first/`. The reproducibility
report and every digest are recorded in
`build/clock-fix-gate/responsive-ui-build-reproducibility.json`.
`responsive-ui-packaged-path-audit-first.json` separately checks packaged
layout/paging, edit-retention, capture-health and media-generation references.
These structural checks are not visual or full runtime certification.

Fresh read-only preflights confirmed access through both supplied credential
files, all four DiscPanel representatives stopped with Cinemarr disabled, and
both installed native guests ready and off. Closed preceding-candidate
external evidence was archived with every file hash verified; the archive
index and updated visual-review image paths preserve that historical proof.
Deployment of the new exact bytes passed in 204 seconds, retaining verified
rollback copies and restoring all four servers stopped with Cinemarr disabled,
original overrides and unrelated mod settings intact. Fresh Windows and ARM
runs also passed all three decoder fixtures while reusing their installed
guests. The native parity report binds all 90 platform payload entries in all
sixteen JARs, 16 representative decoder classes, fixture hashes and invocation
IDs to those runs; the combined retained-guest audit confirms both ready and
off. Current native evidence is indexed in
`build/native-smoke/20260906-responsive-ui-native-parity.json`.
The first responsive-UI real-Plex case, Forge 1.7.10, passed in 200 seconds.
Direct review accepted the leader's visible program, the unobscured
reconnected follower, and the populated 320x240 controller. Program audio
correlation was 0.929824 with 20 ms separation. Permission rejection,
pause/silence, resume, seek, stream selection, reconnect and cleanup passed;
both probe saves remained alive, original saves/settings were restored, and
no private-X or task-audio residue remained. The current review index is
`build/clock-fix-gate/responsive-ui-real-plex-visual-review.json`; only its
listed cases are accepted. The other three representative cases and all
new-candidate recovery/lifecycle cases remain unaccepted.

Remaining work is full reviewed
runtime/adverse-network and exact-byte external certification,
evidence/security and documentation audit, scoped commits, and
final exact-SHA hosted CI/artifact parity. The focused UI results do not check
those remaining release gates.

### Disconnect-thread rejection and correction

The responsive-UI Fabric 26.2 real-Plex wrapper returned success in 167 seconds,
but inspection of both closed client logs found `Rendersystem called from wrong
thread` while closing `cinemarr:dynamic/video_frame_*`. The stack runs from
Fabric's Netty disconnect callback through Cinemarr's playback reset into
Minecraft's texture manager. Minecraft catches and logs that exception, which
is why checking only launcher/native-crash exit state missed it. This case is
explicitly rejected in
`build/clock-fix-gate/responsive-ui-26.2-disconnect-rejection.json`; it was not
added to the accepted visual-review index.

All five Fabric adapters now route joins/disconnects through a shared
`ClientConnectionLifecycle` on the client executor. New connections reset old
resources before hello, duplicate callbacks are idempotent, and a delayed old
disconnect cannot reset a replacement connection. Core tests verify network
thread deferral, ordering, duplicate/stale callbacks and reset-failure
propagation. Root/core tests and 26.2 Fabric compilation pass. Legacy already
scheduled its reset on the client thread.

Every maintained client now reports completion after its media reset. The
control gate kicks and waits for the follower's reset before reconnecting;
final teardown waits for both client acknowledgements after its own checkpoint.
The closed-log check rejects wrong-render-thread and Cinemarr texture-close
errors in current and pre-reconnect logs, including errors Minecraft suppresses.
Regression fixtures and the actual rejected 26.2 logs prove the strengthened
check fails on this defect. A fresh 26.2 full runtime passed protocol, command,
video/control, reconnect and strengthened cleanup checks. Its leader,
pre-reconnect follower and reconnected follower logs each contain two completed
resets on the render thread and no suppressed texture-close error. Evidence is
recorded in `build/clock-fix-gate/disconnect-fix-26.2-runtime.result.json`.
The same suppressed error was also found in the earlier focused 26.2 cohort;
its combined summary now explicitly rejects runtime cleanup while preserving
valid editing-image review. The uncorrected summary is retained with its hash.
At that focused-development checkpoint, no later external case, commit, push
or hosted CI was counted as passed for this correction.

### Disconnect-corrected candidate certification checkpoint (2026-09-06)

The frozen 781-input candidate passed two forced full builds in 8m01s and
8m02s: 74 executed tasks, all ten required GameTests and sixteen-artifact
inspection in each. All eighteen bundle files are byte-identical to
`build/reproducibility/20260906-disconnect-first/`; the complete hash record
is `build/clock-fix-gate/disconnect-build-reproducibility.json`.

Fresh Windows (`20260906T182100Z`) and ARM (`20260906T182455Z`) decoder runs
accepted 144p/480p/1080p and reused their installed guests. Both guests are
confirmed off. `build/native-smoke/20260906-disconnect-native-parity.json`
binds tested native entries across all sixteen JARs, representative decoder
classes, fixture hashes, invocation IDs and the combined stopped audit.

All four exact-artifact real-Plex gates passed without retries. Forge 1.7.10,
Quilt 1.20.1, NeoForge 1.21.1 and Fabric 26.2 measured audio separation of
40, 10, 0 and 10 ms respectively. Twelve directly reviewed images show
identifiable video on both clients and readable controller controls; modern
controllers retain a stale `Buffering` notice that remains a final UI review
item. `build/clock-fix-gate/disconnect-real-plex-visual-review.json` records
the observations and image/log/artifact hashes. Closed logs acknowledge real
client-thread cleanup without the previously suppressed texture-close failure.
All selected servers returned to stopped/autostart-off/Cinemarr-disabled
baselines, preserving unrelated settings. The legacy probe saves remained
alive and their original bytes were restored. No task-owned client/X/audio
residue remained between cases.

A preflight configured-secret scan passed 1,309 source/artifact/native/closed
legacy-and-26.2 evidence files and 4,380 decoded payloads, with zero matches
or read errors. It is not the final whole-evidence scan. Recovery and lifecycle
recertification, the complete reviewed 21-runtime/control matrix, auxiliary
loader/adverse gates, remaining UI review, final evidence/documentation audit,
scoped commits, exact-SHA hosted CI and byte parity remain open. No commit,
push, tag or publication is claimed by this checkpoint.

The four exact-artifact recovery cases subsequently passed in 90s (legacy),
137s (Quilt), 105s (NeoForge) and 98s (26.2 Fabric), including injected 503
outages, manual/automatic retry, redacted diagnostics and exact configuration
restoration. Legacy and NeoForge lifecycle cases passed in 94s and 79s:
256 placed pixels/8,960 remaining survived restart as a pending 9,216-cell
recovery footprint; unavailable chunks retained that work until recovery
completed, and the tested first pixel became air. The aggregate report is
`build/clock-fix-gate/disconnect-external-certification.json`. A fresh global
audit confirmed all four test servers stopped/autostart-off/Cinemarr-disabled,
and both retained native guests remain off.

The broader closed-evidence scan initially rejected 28,377 files: three old
ARM runner logs retained private-host addresses, and 1,216 zero-byte region
placeholders were treated as truncated headers. The three closed logs were
redacted (23 endpoint occurrences, no provided credentials); before/after
hashes are recorded in `disconnect-historical-native-log-redaction.json`.
The task-local scanner now distinguishes an empty file containing no data
from a nonempty truncated region. Five regression fixtures retain rejection
of malformed/unknown nonempty data and compressed secret literals. A fresh
whole-closed-evidence scan is still required; the initial rejection is preserved
as `disconnect-all-closed-evidence-secret-scan.json`.

The corrected broad scan subsequently passed 28,380 files and 586,243 decoded
payloads with no configured-secret/private-endpoint matches or read errors.
`build/clock-fix-gate/disconnect-all-closed-evidence-secret-scan-corrected.json`
records its full source/artifact/native/runtime/archive scope. This is the
closed-evidence checkpoint before the new full runtime matrix, not a substitute
for scanning the matrix's later output. The serial 21-profile matrix uses a
single task-owned transient unit and separate no-retry case invocations; it
stops at the first failure. Its automated results do not replace direct image
review or the final exact-commit CI gate.

## Superseded detailed assessment archived 2026-09-07 during modern pressure acceptance

Current checkpoint (2026-09-07): the 21-profile development matrix and four
exact-production-JAR real-Plex representatives are visually accepted. Four
recovery cases, two lifecycle cases and both retained native guests have also
passed and cleaned up. The subsequent requirement audit found that live legacy
video resource/sound reload coverage was missing: the existing reload scenario
ran only in the older audio-only path. The corrected live test exposed a real
Forge 1.7.10 crash: the sound-reload handler calls native OpenAL cleanup after
the owning context has been destroyed. The context-ownership fix passes 190
legacy tests and a focused 318-second local case with two reloads per client,
post-reload PCM, six directly reviewed world captures and closed resources.
Fresh production-bundle rebuild and exact-byte acceptance remain required; the
previous candidate bytes do not satisfy this lifecycle requirement.
The added legacy envelope fuzz and compressed save/reload LRU tests now pass.
They exposed numeric NBT tags being coerced into session names; explicit string
type checks now reject corrupt identities while retaining healthy neighbors.
The terminal/queue scenarios passed on legacy (277 seconds) and NeoForge 1.21.1
(228 seconds), including 24 directly reviewed images, post-transition correlated
PCM, empty-EOS/STOP silence and closed resources. Ten observer regressions include
an injected active underrun that turns the actual shell gate red despite a later
counter reset. Subsequent egress fixes add explicit client/session byte budgets
and prevent a send exception from stranding the remaining queue. Current tests
pass: 219 legacy, 175 core and 39 modern (two/one/two opt-in skips), including a
new legacy world-unload test that closes media while retaining the live
connection's hello and clock. Seven observer tests reject stale or replacement
JVM evidence. The terminal
runtime receipts precede these egress changes; fresh affected-runtime and final
artifact acceptance remain required. The corrected
configured-secret scan passed, but new work requires a fresh final scan. Complete
source/documentation review, commit/push, final-commit local validation and
exact-SHA hosted CI gates remain open; this is not release readiness. The
evidence sequence follows.

The requirement audit also confirms remaining phase-5/8 work. Egress now has
client/session/global item and byte limits and a deterministic 1,000-tick
greedy-client test, but these unit tests do not replace live unfair-client
pressure and queued-work overload acceptance. The live adverse-network helper
covers transient/slow/exhausted transport, not
queued-work overload or unfair-client pressure. The same-JVM world-change case
now performs two opt-in Nether/overworld round trips, checking resource/thread
counts, silence away and physical A/V after each return. Its first live run
failed: legacy screen registries used Forge's dimension-shared `mapStorage`,
so returning clients never received the screen/session again. The fix uses
`perWorldStorage`, preserves the original save before migration, and has two
additional storage/migration regressions. The corrected 340-second run passes
both same-JVM round trips, with fourteen directly reviewed transition images,
20/30 ms correlated return PCM, zero retained media/decoder threads while away,
and independently parsed saved registries (one overworld TV, zero Nether/End
TVs). The twelve queue/replay captures, terminal silence and closed resources
also pass. These are development-launcher receipts, not final artifact
certification. Old saves lack dimension identity: non-overworld controllers
may need reactivation/retuning; see the documented backup/migration procedure.
The first source-frozen live browse-overload run failed after 150 seconds:
260 actual follower requests filled the shared 64-task queue, rejected 204
tasks and starved both clients during active playback. Physical PCM and advancing
frame checks also failed. The fix separates browsing (one worker/16 queued)
from playback (two workers/64 queued), preserving bounded rejection and close
semantics. Typed overloads now retain actionable retry guidance rather than
exposing the nested executor message. Three new core regressions plus six
pressure-observer contracts pass. The corrected legacy run completed in 388
seconds with 220 real browse requests, sustained 16-slot saturation, no playback
rejections/active underruns, 50 ms correlated PCM during and after pressure,
fresh browse recovery and closed resources. Its eighteen pressure images are
accepted; all 57 images were reviewed, but the later reload captures retained
the F3 debug overlay and are not accepted as unobstructed final evidence.
Acceptance UI preparation now explicitly clears that overlay; its final live
capture check is pending. The source-layout gate also caught missing immediate
underrun diagnostics in the two 26.x audio shims; both are corrected and the
source-layout gate passes. The modern pressure representative completed in 323
seconds with 264 actual requests, sustained browse saturation, zero playback
rejections/active underruns, 0 ms PCM separation during/after the load, 51
directly reviewed images and closed resources. Both pressure modes are now
mandatory local/CI supplements; final-commit runs remain required.
The supplementary pressure gate now also implements a third, network-only real
Minecraft peer sending bounded segment-request bursts for forty seconds. It
requires actual media/window acknowledgements and both rejection paths while
two ordinary viewers retain strict A/V checks. Separate private display/audio
ownership, three-client transfer bounds, peer disconnect acknowledgement and
complete transfer drain are checked. Seven Java peer tests and nine observer
regressions pass; legacy verification and modern/core tests pass. Live acceptance
is still pending: adding the scenario does not satisfy unfair-client pressure.
The first 255-second legacy attempt is retained as failed: its recovery observer
incorrectly included a delayed pre-departure diagnostic. The actual peer sent
3,180 requests, received 3,750,976 bytes and acknowledged 116 windows with both
rejection paths exercised and no unexpected errors. During-load PCM passed at
10 ms / 0.999894, but post-departure capture/PCM and the full continuation did
not run. Five fresh post-leave snapshots show two clients and zero transfers;
the observer now takes its recovery cursor after confirmed leave/reset and peer
termination. A regression rejects stale boundary mixing without ignoring any
post-departure excess. This is a harness correction, not a retrospectively
accepted runtime. Its five private X processes, port and task audio sinks are
verified gone; a new source-frozen attempt is required.
The corrected `segment2` attempt completed both pressure/recovery phases and
the owner/widget observers, but its wrapper subsequently exited with SIGTERM
(143) before the remaining controls/reload/reconnect finished. The signal sender
is not established; there is no final pass receipt. Its 3,140-request peer
received 3,653,968 bytes and acknowledged 113 windows; during/recovered PCM
passed at 30/40 ms with correlations 0.990452/0.987218. All 36 pressure images
were directly reviewed. The separately recorded interruption remains a failed
overall attempt, not complete acceptance. Manual cleanup stopped its exact
orphaned server/clients, verified all five private displays and task sinks gone,
and restored the original world and byte-identical server properties. The fake
service had already died, so the manual stop also recorded a transcode-close
connection-refused warning. A fresh complete run is still required.
Source review then found browse/segment rate-limit maps retaining departed
player UUIDs in all three managers. Disconnect now removes both entries and
manager close clears both maps. Two core regressions cover 1,000 departing
identities and idempotent stop; the nine segment-observer contracts also require
bounded rate-limit subject diagnostics and cleanup routing in all managers.
Core/modern and legacy verification pass after that fix (175/39/219 tests,
one/two/two opt-in skips). The next source-frozen modern attempt is owned by
the bounded transient service `cinemarr-pressure-neoforge-segment3-20260907`;
its running process is not acceptance. Its wrapper records interruption and
forwards shutdown once, and its cgroup contains the complete test process tree.
Final candidate queued-work overload acceptance also remains open, not waived
by earlier source-bound matrix or browse-only passes.

The earlier `paused-frame-r2` is rejected for release.
Both forced full builds passed, but its serial matrix terminated after seven
automated passes and one Forge 1.20.2 failure; thirteen profiles were unrun.
The failed follower received media without a JOIN reset. All ten modern
Forge/NeoForge adapters still reset only on logout, unlike the Fabric adapters.
The old generic reset marker could also mistake a pre-connect logout for a
successful JOIN, so earlier green ordering checks do not prove that contract.
The worktree now applies the shared connection-owned client-thread lifecycle to
every modern adapter and requires an explicit JOIN-reset marker before media.
Ten checker fixtures, seven log-gate tests and the source-layout guard pass.
Root tests and selected Fabric/Forge/NeoForge builds passed in 1m51s after a
compile-only correction removed an unnecessary version-specific logging call.
The focused Forge 1.20.2 runtime passed in 266 seconds with all 797 source
hashes unchanged, 10 ms PCM separation and 0.993095 correlation. All 33 captures
were directly reviewed; all three closed client logs prove explicit JOIN-reset
ordering and two media-reset acknowledgements. Recorded private-X/gate PIDs,
game listener and case audio modules are absent. Both fresh `explicit-join`
full builds passed in 610 and 537 seconds, with all 76 tasks executed, all ten
required GameTests passed and all sixteen artifacts inspected in each. All
eighteen bundle files match byte-for-byte. A separate snapshot audit confirms
the new lifecycle and explicit JOIN-reset references in every modern JAR.
The fresh 21-profile matrix passed against the identical 797-input source
manifest in 5,729 seconds. All 21 profiles are accepted, each with all 33 captures
directly reviewed (693 total), 0–30 ms PCM separation and closed-log/process/port/audio
checks. Individual runs took 240–307 seconds; the serial batch exited successfully.
The thirteen supplementary cases remain separate from this completed matrix.
All five Quilt/Mod Menu cases have completed 33-image, closed-log and cleanup
reviews. The minimum-loader audit found three false-positive automated passes:
1.20.1, 1.20.2 and 1.21.1 actually loaded Fabric 0.19.3 despite requesting
0.19.2. Only the two 26.x logs prove the requested minimum. The override and
missing loaded-version assertion have now been corrected: all five fresh
minimum-loader cases pass with an independent check of the actual 0.19.2
startup marker. Normal release locks remain unchanged. The legacy repeat's
33-image review and both adverse-network reviews are complete; the latter
prove baseline video/PCM and logged post-fault stability, not post-fault
physical video/audio capture.
Production-client input preparation has verified public library/index inputs
for all four representative profiles, but no packaged-client launch is
certified by those preparation checks.
The rejected
logs, bundle and six completed visual reviews remain
historical, not certification of the changed code. External/native, security,
commit/push and final-SHA hosted CI
gates remain open. See [release acceptance](RELEASE_ACCEPTANCE.md).

Exact-byte client acceptance exposed a further gap: earlier real-Plex scripts used
development launchers locally. The maintained Plex and lifecycle paths now
require production inputs, with twenty passing launcher-input regressions.
All four representative prototype launches reached the expected disconnected
screen on private X; those launch prerequisites do not prove media playback.
Fresh full acceptance was required to close that gap, and its completed
four-target evidence is recorded below. See
[production-client acceptance](PACKAGED_CLIENT_ACCEPTANCE.md) for preparation
and reuse; server hash parity alone is insufficient.
The maintained NeoForge packaged-client integration has now passed against the
deterministic development server in 218 seconds, with 33 directly reviewed
captures, 10 ms PCM lag and complete closed-log/process/audio cleanup. This
proves the maintained launch path works with the exact client JAR; it did not
replace the four credentialed real-Plex representatives completed afterward.
All four candidate deployments then passed exact-hash and rollback checks.
The first real-Plex batch failed on legacy's paused stream test: its selected
movie has one audio track plus subtitles, while the observer assumed two audio
tracks. The cursor was preserved. An explicit audio/subtitle fixture setting
and ten strict assertion regressions address that harness assumption; the
failed run remains rejected and the three subsequent cases were not run.
The corrected four-target production real-Plex batch has now passed all
automated cases against 803 frozen inputs and the unchanged candidate JARs.
All 132 captures have been directly reviewed. Quilt 1.20.1, NeoForge 1.21.1
and Fabric 26.2 pass the visual review, including identifiable footage on the
reconnected follower; their post-reconnect physical PCM separations are 20,
10 and 0 ms. Legacy's 50 ms PCM check passes, but its saved reconnect world
image is black and does not establish identifiable post-reconnect video.
That visual requirement remains open. A maintained six-image, fixed-sequence
world capture now follows PCM recording. Its first follow-up exposed a misplaced
audio-only hook and is rejected as capture evidence despite passing the older
automated checks. The corrected video hook has eleven regression tests and an
independent six-image assertion. A focused legacy follow-up uses a new 805-input
`packaged-reconnect-capture-r2` freeze without changing product bytes.
Earlier reports and raw images remain preserved, not rewritten as passes.
The corrected legacy follow-up has now passed in 218 seconds. All 39 images
are directly reviewed: six fixed post-reconnect captures show advancing movie
footage on both clients, and eight-second PCM passes at 50 ms / 0.847651.
The four production representatives are now visually accepted, with independent
verification of twelve exact Cinemarr/Jammarr launch records, 138 accepted
images, baseline server diagnostics, closed client/display/audio resources and
fresh stopped/autostart-off/Cinemarr-disabled server state. Recovery/lifecycle,
native, secret-scan, commit/push and final hosted CI gates remain open.
The four fresh Plex recovery cases have also passed in 458 seconds, with
disabled/degraded/manual/automatic recovery and exact configuration restoration;
an independent authenticated audit confirms their stopped baseline. The two
restart/unloaded-chunk lifecycle cases and retained native guest checks are
still in progress; neither is treated as complete merely because it is running.
Both retained native checks subsequently passed (Windows 237 seconds, ARM 325
seconds) with all three decoder resolutions accepted, exact 90-entry native
payload parity across sixteen JARs and both installed guests verified off.
This is decoder/ABI coverage, not full Windows/ARM Minecraft certification.
The two production-client restart/unloaded-chunk lifecycle cases also passed
(legacy 83 seconds, NeoForge 75 seconds), with exact dependency hashes, four
closed private displays, no task audio sinks and all managed servers stopped.
The newer four Python regression suites are now included in GitHub preflight;
their 51 cases pass locally. Final configured-secret scanning, source/docs
audit, commits, final-commit local gate and exact-SHA hosted CI remain required.

The release scope remains 16 artifacts and 21 runtime profiles. The ten phases
below retain their original requirements. Newer Jammarr target feasibility is
reported only after the documentation and final exact-SHA CI work is complete.

Superseded narrative checkpoints are preserved verbatim in
[historical evidence](HARDENING_EVIDENCE_HISTORY.md); their passes do not certify
the current source or artifacts.

## Superseded proposal-status narrative archived 2026-09-07

# Prerelease implementation status

This checkout is a Cinemarr 1.0.0 prerelease under hardening. It is not a
validated release candidate and has not been tagged or published. Evidence in
this document predating protocol 10 is regression history only; every required
gate must be repeated against the exact final commit and artifact bytes.

Latest checkpoint: the disconnect candidate's full matrix stopped at Fabric
1.20.2 reconnect (five profiles passed, one failed, fifteen were not run).
Pre-handshake tracking published media that the client's JOIN reset erased.
The current worktree corrects modern handshake/tracking order, controller
permission/error feedback, completed-operation status text and browse-offset
overflow. Core tests and Fabric 1.20.2, Fabric 26.2 and legacy representative
builds passed. Direct review then found stale controller time/relative seek,
and actual owner-widget testing exposed paused stream reconfiguration that
did not advance generation. Both corrections now pass the Fabric 1.20.2
focused gate in 227 seconds with 24 directly reviewed images, 10 ms audio
separation and clean render-thread teardown. Other representative checks,
full new-artifact certification and final hosted CI remain open. All earlier results below are
historical, not certification of these new changes.

Subsequent legacy widget testing caught a test-only library-open mismatch and
a genuine stale-generation media request producing a false permission error
during successful seek. Both are corrected. Direct paused captures also
exposed texture disposal on pause. A shared same-program retention policy now
moves only the displayed texture to a newer paused revision while closing old
decoder/audio ownership normally. Fresh legacy and Fabric 26.2 focused runs
passed in 316 and 298 seconds with 10 ms audio separation, 21 reviewed images
each and clean teardown. Their evidence also proves edit selection and drafts
survive authoritative state updates. The first complete-build attempt failed
after all ten GameTests passed because an old healthy log fixture lacked the
now-required JOIN-reset evidence. The corrected seven-test suite and other
lightweight harness checks pass. The failed attempt remains recorded. The
separate `paused-frame-r2` attempt passed both forced builds in 512 and 481
seconds, with 76 root tasks executed, ten required GameTests passed and sixteen
artifacts inspected in each. All 18 bundle files match byte-for-byte; a separate
packaged-class audit confirms the new UI and safety references in every JAR.
That serial runtime matrix subsequently stopped with seven automated passes,
one Forge 1.20.2 JOIN-reset failure and thirteen unrun profiles. The latest
worktree extends connection-owned reset to all modern adapters and requires
an explicit JOIN acknowledgement instead of accepting generic logout markers.
Root tests and selected loader builds passed, followed by a fresh Forge 1.20.2
runtime pass in 266 seconds with 33 reviewed captures, explicit JOIN ordering,
10 ms PCM separation and verified cleanup. Both fresh full builds passed in
610 and 537 seconds, each executing all 76 tasks and passing all ten required
GameTests and sixteen-artifact inspection. All eighteen bundle files match
byte-for-byte. The new 21-profile development-runtime matrix passed in 5,729
seconds, with all 693 captures directly reviewed and per-case closed-log and
cleanup evidence. Supplementary runtime tests remain open. These results do not
check off exact-artifact real-Plex/native acceptance or hosted CI; packaged
client execution still needs proof beyond the existing server-JAR hash checks.

The responsive-UI Fabric 26.2 real-Plex run is rejected despite its automated pass:
both clients logged suppressed GPU texture-close failures from Netty threads.
Shared Fabric connection ownership and client-thread cleanup, plus explicit
cleanup acknowledgements and closed-log rejection, passed a fresh focused
26.2 development-runtime gate. The corrected candidate also passed two complete
74-task builds in 8m01s and 8m02s, all ten required GameTests in each, and
byte-identical sixteen-JAR bundles. Fresh Windows/ARM decoder checks and package
parity also passed with both retained guests off. All four exact-artifact
real-Plex representatives passed controls, reconnect, physical audio and
client cleanup, with twelve directly reviewed images. Full runtime,
recovery/lifecycle certification, the recorded stale controller-notice review,
and final hosted gates remain open. The historical subsets below predate this correction.

The audio-clock changes after the September 1 checkpoint invalidated that
candidate's final-byte certification. Direct September 6 screenshot review then
contradicted apparent real-Plex visual success: two reconnected follower cameras
overlapped the other avatar. Stable participant-owned camera positions are now
shared by all adapters, but their rebuilt bytes need fresh acceptance. Earlier
build, GameTest, audio, recovery, lifecycle, native, and secret-scan results are
regression evidence only. The full reviewed runtime/control matrix, exact-byte
external checks, commits, and exact-SHA hosted CI/parity remain open. The table
below records a superseded checkpoint; its green results do not certify today's
worktree.

The later coordinator candidate passed reproducible builds and retained-guest
native checks, but direct controller review found legacy wide-button texture
corruption, overlapping status/episode controls, and missing Minecraft 26 text
alpha. A legacy framebuffer also exposed a persisted dead probe identity from
an earlier lifecycle fall. Those corrections passed on the subsequent
UI-visibility candidate, but normal-GUI-scale review then found missing browse
coverage, clipped placeholders and lost edit focus. Responsive layout, library
and legacy browse paging, and edit-retention corrections now pass focused
four-runtime editing checks with 24 directly reviewed captures; the earlier
26.2 cleanup acceptance was withdrawn after its logs exposed the same defect.
Their superseded responsive-UI frozen
candidate passed two full 74-task builds, all ten required GameTests, and
byte-identical inspection of all sixteen JARs. Fresh native checks and payload
parity also passed with both retained guests off. Full runtime, exact-byte
external acceptance and final hosted gates remain open. Earlier green
automated playback results do not overrule direct visual failures.

| Area | Superseded checkpoint | Requirement retained for the new candidate |
| --- | --- | --- |
| Product boundary | Video-only protocol 10; inherited music/station UI, transport, persistence, tests, JLayer, and Jump3r remain removed; the rebuilt 16-JAR index passed checksums and deep inspection | Preserve the exact bundle through hosted CI |
| Platform matrix | The then-current tree passed all 16 artifact targets, all 10 required GameTests, and a complete 21-runtime matrix in 53 minutes 35 seconds | Repeat the complete runtime and CI matrix for the final tree and exact pushed SHA |
| Plex security | Server-only credentials, allowlists, origin confinement, redaction, and bounded recovery passed the exact four-profile recovery matrix; a live-value source/evidence/JAR scan found zero matches | Preserve redaction and secret-scan results through final CI |
| Screens | Persistent geometry, ownership, overlap, lifecycle limits, and 10 GameTests passed; representative owner/non-owner real-client layouts passed on private Xvfb displays | Preserve the exact client behavior through final CI |
| Quick TVs | Exact Forge 1.7.10 and NeoForge 1.21.1 artifacts passed restart-mid-build and unloaded-footprint recovery | Preserve lifecycle behavior through final CI |
| Controller UI | Pagination, session draft retention, `canControl`, queue, stream, and presentation controls passed the exact representative real-Plex gates | Preserve exact behavior through final CI |
| Media relay | Bounded work/cache/egress, fairness, EOS handling, HLS materialization retry, sustained latency, transient failure, exhaustion, and recovery passed on the then-tested representative bytes | Revalidate exact behavior before final CI |
| Client decode | Software remains the release baseline; final-byte real-Plex captures validate pre-play OpenAL scheduling, and fresh Windows x86-64/Linux ARM64 package runs passed with both retained guests stopped afterward | Preserve packaged bytes through final hosted-bundle parity |
| Release evidence | Local build/inspection, 16-way canonical convergence, exact DiscPanel deployment, real-Plex, recovery, lifecycle, live-secret scan, and retained native-guest gates passed at that superseded checkpoint | Complete all current-candidate checks, commit and push the scoped tree, obtain fully green exact-SHA CI, download the hosted bundle, and prove byte parity |

The authoritative remaining work is [the 1.0 hardening plan](1.0_RELEASE_HARDENING_PLAN.md)
and the unchecked [release acceptance](RELEASE_ACCEPTANCE.md) checklist. No tag
or publication is authorized by completing those engineering gates.

Jammarr target-expansion lessons and loader constraints are recorded in
[JAMMARR_TARGET_FEASIBILITY.md](JAMMARR_TARGET_FEASIBILITY.md); they do not
expand the Cinemarr 1.0 release matrix.
