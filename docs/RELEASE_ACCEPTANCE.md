# Release acceptance

## Current certification boundary — 2026-09-08 UTC

R13 failed its first runtime case: zero scoped accepted cases, one failed and
36 unattempted, after 229 seconds (the case itself ran 224 seconds). Its unchanged
terminal observer requires `Playing next queued video`; the suspension fix had
replaced that feedback with generic `Playing` across all three managers. The
legacy leader and follower did advance from near-EOS generation 2 to generation
3, item 9001 near zero, and both received an empty queue. That does not excuse
the missing feedback or count as terminal acceptance. No runtime retry was made.
The failed unit is terminal with exit 1; 152 evidence files are preserved in
`build/clock-fix-gate/release-audit-r13-runtime-failure-preserved-20260908.json`.
Four owned private displays and owned sinks/game/RCON ports are closed, with no
owned-unit core dump. Playback clients lack normal exit receipts and were
cleaned up on the failure path, not accepted as clean normal exits.

The contextual-feedback correction now keeps the queue/next-episode messages
only for currently transcoding snapshots. Paused, suspended and empty snapshots
still report their actual state. New source checks first failed against unchanged
r13 managers across all three families, retained in
`build/clock-fix-gate/r13-feedback-before-fix-20260908.json` and its log. Eight
publication/feedback tests and six source-layout tests now pass. Broader counts
are core 201 total/200 passed/1 skipped, modern 40/38/2 and legacy 246/244/2;
these suites overlap and retain the existing opt-in live/backend skips.
Root modern, Fabric 26.1.2 and legacy release builds pass. The source-bound
legacy terminal case under `cinemarr-queue-feedback-fix-20260908.service`
passed in 348 seconds. Its 28 original captures were directly reviewed:
advancing test video on both clients through queue/restart and two same-JVM
world returns, plus two Nether-away views without a television picture.
Captures retain cropped top counters, HUD/hand and initial leader construction
chat. Paired exposures are sequential, not simultaneous-frame proof.
Five eight-second PCM pairs were independently recomputed: initial +40 ms /
0.993701, queue -10 / 0.999613, restarted +20 / 0.999923, world return one
-20 / 0.993038 and world return two -10 / 0.998829. Four terminal drains and
both away-follower recordings measure -91 dBFS; the away leader remains audible.
All six single-launch clients exited zero, the unit closed normally without a
core dump, original world/properties were restored and private displays,
owned sinks and game/RCON ports were closed.

The scoped review is
`build/clock-fix-gate/queue-feedback-fix-1.7.10-forge-20260908.direct-review.json`.
The `.preserved.json` receipt retains 258 hashed evidence references and a copy
of the development JAR (SHA-256
`7879ad0f2375bc9e191252fa1cdad36b7744b9c9af55b47d62015be061f27302`).
This is corrective development acceptance, not packaged release certification.
Neither the terminal observer's feedback assertion nor timing limits changed.

R14 tooling preflight passed in 24 seconds on unchanged source. Its first full
build under `cinemarr-release-audit-r14-build-20260908.service` was interrupted
after 489 seconds by user-session shutdown (SIGTERM 15, child exit 143).
It did not complete the root build, second build or reproducibility checks.
The preserved interruption receipt contains 24 hashed references. No r14
runtime or production/native acceptance ran. A separately named resumed build
was prepared but never launched before the user requested a pause and Git
handoff for another device. See the migration handoff in
`1.0_RELEASE_HARDENING_PLAN.md`; ignored local evidence does not accompany Git.
The handoff commit/push is not final certification, and any resulting CI remains
unverified until inspected. Earlier full builds and corrective passes do not
certify this bundle.

### R13 build evidence and earlier suspension corrective case

R13's tooling preflight passed in 23 seconds. Its two forced full builds passed
in 521/507 seconds, each executing all 89 tasks, passing all ten GameTests and
inspecting all sixteen artifacts. The eighteen-file bundles match byte-for-byte
across 833 unchanged non-documentation inputs, independently checked after
`cinemarr-release-audit-r13-build-20260908.service` exited zero with no owned
core dump. The fresh 37-case runtime batch started at 11:09 Arizona under
`cinemarr-release-audit-r13-runtimes-20260908.service`; that batch subsequently
failed as described above. Fifteen handoff/GUI-exit/final-review identity guard
tests pass. Production, native, final-commit and remote-CI certification remain open.

The earlier suspension/publication fix had scoped corrective runtime acceptance;
its original source identity does not certify the later contextual-feedback fix.
All three server managers install metadata only for the same playback revision
and use the returned current snapshot for persistence and publication. A
pause/suspension can retain its restart metadata without reviving the retired
media or publishing an obsolete playing snapshot. Paused stream-option changes
and restores receive distinct playback revisions; stop, replacement, removal,
name reuse and close reject obsolete completions. Initial/reconnected idle
snapshots now say `Suspended` rather than `Playing` when an item has no media.
Seven new coordinator tests pass, covering the observed ordering and its
negative cases. Five source-layout mutation tests cover all three managers,
all fifteen completion paths and restored-revision/status-message checks.
The new layout tests are dependencies of the existing release verification.

Broader local tests report core 200 total (199 passed, one existing live-Plex
skip), modern 40 total (38 passed, two skips), and legacy 245 total (243 passed,
two skips). These suites overlap; their counts must not be summed as unique
tests. Root modern, Fabric 26.1.2 and Forge 1.7.10 release builds passed, including
legacy reobfuscation. The skips are live-Plex tests plus the explicitly enabled
hardware-backend decoder tests in modern/legacy suites. Credentialed playback
and native/backend certification remain
separate requirements. The original failing model and r12 bundle remain unchanged;
its exact pre-fix coordinator bytecode has also been recovered by hash from
the retained r12 artifact because the original `build/classes` path is mutable.
That provenance is recorded in
`build/clock-fix-gate/r12-suspension-publication-bytecode-provenance-20260908.json`.

The single corrective development runtime passed in 252 seconds; its service
`cinemarr-suspension-publication-fix-20260908.service` is inactive, main PID 0,
exit 0, with frozen inputs at
`build/clock-fix-gate/suspension-publication-fix-26.1.2-fabric-20260908.inputs.json`.
All 33 original captures were directly reviewed and hashed in the sibling
`.visual-review.json`: clock advancement, stable paused world frame 195,
paused seek/Commentary selection and resume, permission-error persistence and
expiry, and draft/selection preservation through state and queue/browse changes.
Playing seek and stream-switch captures are dark; later captures show restored
video. Initial unsigned-chat toasts obscure the upper-right picture, leader
build/join chat crosses the lower picture, and the close camera crops the top
counter band. These are recorded limitations, not flawless-UI claims.
The independent eight-second PCM comparison reports 0 ms lag and correlation
0.990489. All five GUI roles launched once and exited zero; no owned-unit core
dump was recorded. Private displays, owned sinks and game/RCON ports are closed;
original world/properties and frozen source/development artifact are unchanged.
The deterministic tests reproduce the failed ordering; this corrective runtime
was not forced to trigger that exact race. It is not a packaged production,
full-bundle or final-release gate.
The exact corrective development JAR and 43 evidence references are retained
in the sibling `.preserved.json` receipt before later builds replace mutable
development outputs. R13 reproducibility passed but its runtime failed as
described above; no new commit or remote CI has started.

R12 is failed, not a release candidate. The batch stopped at Fabric 26.1.2
after 6,595 seconds: nineteen scoped accepted cases, one failed and seventeen
not attempted. The leader initialized but never reached video-ready within
the original timeout. Its first playback was suspended at position 23 ms
with generation 2 and no stream metadata because the TV chunks were unloaded.
The later follower snapshot was also `status=IDLE`, with the contradictory
`message=Playing`; neither client produced a ready frame. Read-only RCON
checks found players at the intended camera positions, loaded TV-area chunks,
two tracking clients, zero active streams and no pending or retiring work.
These observations establish a stalled session, not recovery.

The unchanged compiled r12 coordinator reproduces the ordering
`play -> suspend -> applyIfCurrent(prepared)`: generation advances 1 to 2 while
playback generation remains 1, the metadata callback is rejected and the
item remains selected with no active stream. The current manager requires
that metadata to persist the checkpoint used by restart. This deterministic
model is not a runtime rerun and does not yet prove a fix. Its original failing
result is retained at
`build/clock-fix-gate/r12-suspension-publication-before-fix-20260908.json`.
The implementation above addresses the race and misleading idle message;
scoped corrective verification passed, while complete recertification remains pending.

The terminal batch and 4,386 evidence files are preserved by hash in
`build/clock-fix-gate/release-audit-r12-runtime-failure-preserved-20260908.json`.
The owned service is failed with main PID 0 and exit 1; 105 private X processes,
owned audio sinks and game ports are gone, the failed RCON port is closed and
no owned-unit core dump was recorded. The failed playback clients have no
normal zero-exit receipt; failure cleanup is not normal-exit acceptance.
The generated failed world is retained and the original absent `run/world`
state restored. The retained r12 bundles match their original frozen inputs;
current source has changed for the correction and is not certified by r12.
No r12 production/native/final-commit or remote certification
was started. Earlier scoped results below retain their original identities.

### Earlier scoped work and r12 case evidence

The follow-up exit oracle is implemented and passed scoped corrective testing.
It sends `WM_DELETE_WINDOW` directly on an identity-checked owned display
(private X has no window manager), waits for the launcher, and requires a zero
exit plus exactly one zero-status command receipt. Forced cleanup cannot
satisfy those checks. Required protocol/command clients, playback reconnects,
pressure peers and final playback teardown use this path. Seven focused
Gradle tasks pass 63 tests, including actual private-X normal closes and a
deliberate SIGSEGV with core writing disabled. The exit-status regressions are
also wired into GitHub CI. The fresh legacy terminal corrective run passed in
357 seconds: all six GUI command exits are zero and an independent journal
audit finds no core dump for the owned unit. All 28 original images were
directly reviewed; five independently recomputed eight-second PCM pairs show
10–20 ms absolute offset (correlations 0.990074–0.999611). Both same-JVM world
returns, queue/EOS/reconnect/restart and terminal silence checks pass. All
private displays, sinks and the game port are closed, and original world/
properties and the retained release bundle are unchanged. The close camera
crops the top timestamp band; initial leader build chat is below the video.
Away-world images show a dark empty Nether view with no retained video.
Receipts: `build/clock-fix-gate/client-exit-fix-1.7.10-forge-20260908.result.json`
and `build/clock-fix-gate/client-exit-fix-1.7.10-forge-20260908.direct-review.json`.
This closes the corrective case only. Both r9 full builds subsequently passed
at 06:27 Arizona (505/495 seconds, 88 executed tasks and ten required GameTests
each). All sixteen inspected JARs and both manifest files are byte-identical;
all 831 frozen non-documentation inputs are unchanged. The fresh 37-case
runtime batch later failed its tenth case, as recorded below. Its first legacy
terminal case passed in 362 seconds: six zero-exit GUI launches, no owned-unit
core dump, all 28 original PNGs directly reviewed, five independently recomputed
eight-second PCM pairs at 0–40 ms absolute offset (correlations
0.990611–0.999883), both same-process world-return cycles and verified closed
displays/sinks/port. Away-world images contain no retained TV; returned/queue/
restart captures show changing identifiable test video without avatar
obstruction. The close camera crops the upper counter band; initial leader
build chat is below the video. The scoped receipt is
`build/clock-fix-gate/release-audit-r9-terminal-1.7.10-forge-20260908.direct-review.json`.
The modern terminal case subsequently passed in 243 seconds: all five GUI
exits zero, no owned-unit core dump, all fourteen original images reviewed and
three independently recomputed eight-second PCM pairs at 0–10 ms absolute
offset (correlations 0.988076–0.999207). All four terminal drain recordings
measure -91 dB mean/maximum after the first second; no vanilla-toast audio
exception was used. The initial leader and pre-reconnect follower captures
have the vanilla unsigned-chat warning over the upper picture, and initial
build chat overlaps the lower picture/HUD. Queue/restarted sequences are clear
of that warning and show changing video on both clients. Receipt:
`build/clock-fix-gate/release-audit-r9-terminal-1.21.1-neoforge-20260908.direct-review.json`.
Legacy pressure subsequently passed in 487 seconds, with seven zero-exit GUI
clients and no owned-unit core dump. Its normal-control and pressure reviews
cover 39 and 42 image references respectively (75 distinct original PNGs,
including six shared post-reload captures). All were directly reviewed.
Browse sent 261 requests over 40 seconds with forty media HTTP requests still
served during the held-browse phase; the third peer sent 3,950 requests and
remained network-only and silent. Four independent eight-second during/
recovered PCM pairs measured 0 ms offset (correlations 0.994490–0.995988);
the post-reload pair measured 20 ms (0.989612). Bounded-work diagnostics,
recovery, two reloads per client and closed displays/sinks/port passed.
Browse-overload chat obscures part of the requesting follower picture during
the flood; recovery captures are clear. The close camera crops the upper band.
Owner seek/stream-change and the non-owner snapshot catch dark TV tiles during
transitions; later editing, final-controller and all post-reload captures show
the picture restored. Paused clock/frame retention, visible expiring denial
messages and text-selection/draft retention are directly reviewed. Receipts:
`build/clock-fix-gate/release-audit-r9-pressure-1.7.10-forge-20260908.visual-review.json`
and `build/clock-fix-gate/release-audit-r9-pressure-1.7.10-forge-20260908.pressure-review.json`.
Modern pressure subsequently passed in 427 seconds: six zero-exit GUI clients,
no owned-unit core dump and all 69 original images reviewed (33 controls,
36 pressure). Browse sent 264 requests with forty media requests served during
the held phase; the third peer sent 3,730 requests and remained network-only
and silent. Four independently recomputed eight-second during/recovery PCM
pairs measured 0 ms (correlations 0.996490–0.998162), and the post-reconnect
pair measured 10 ms (0.999526). Work bounds, recovery and closed resources
passed. Initial world captures retain unsigned-chat warnings, browse-overload
chat partly covers the requesting client during the flood, and playing-seek/
stream-change snapshots catch dark tiles before video returns in later edits
and final controllers. Paused frame/clock, denial expiry and selected-text/
draft retention were directly reviewed; hover tooltips transiently cover
neighboring controls. Receipts:
`build/clock-fix-gate/release-audit-r9-pressure-1.21.1-neoforge-20260908.visual-review.json`
and `build/clock-fix-gate/release-audit-r9-pressure-1.21.1-neoforge-20260908.pressure-review.json`.
Legacy fault recovery then passed in 422 seconds: six zero-exit GUI clients,
no owned-unit core dump and all 57 original images directly reviewed (39
controls/reload and eighteen post-fault). Transient, slow and exhausted fault
phases used distinct recovery generations 12/13/14 and recorded 159/48/6
actual fault-mode HTTP requests. Independent eight-second recovered PCM pairs
measured 0/10/0 ms absolute offset (correlations 0.990017–0.999984); the final
post-reload/reconnect pair measured 0 ms (0.998798). Both clients completed
two resource/sound reloads. All recovered sequences show moving identifiable
video without an avatar or chat obscuring the picture. Close-camera top-band
cropping, initial leader build chat below the picture and transient dark tiles
during control changes remain explicit. Later edit, final controller and
post-reload captures show restored video. Text selection, drafts, permission
feedback and paused frame/clock checks pass. Receipts:
`build/clock-fix-gate/release-audit-r9-fault-1.7.10-forge-20260908.visual-review.json`
and `build/clock-fix-gate/release-audit-r9-fault-1.7.10-forge-20260908.fault-review.json`.
Modern fault recovery passed in 337 seconds: five zero-exit GUI clients, no
owned-unit core dump and all 51 original images directly reviewed. Recovery
generations 12/13/14 correspond to 150/49/6 actual fault-mode HTTP requests.
Three independent eight-second recovered PCM pairs measured 10/0/0 ms
absolute offset (correlations 0.990971–0.996236); final reconnect PCM measured
0 ms (0.989532). All eighteen recovery images show moving identifiable video
without avatar, chat toast or persistent dark tiles obscuring it. Baseline
world captures retain vanilla unsigned-chat warnings and initial leader build
chat. Paused clock/frame 00:00:57.600/counter 288 remain fixed; seek, stream
change, resume, selected substring and draft retention pass. Immediate playing
seek/stream snapshots show dark tiles, with video restored by the later edit
capture at 2:04 and both final controllers. Hover tooltips briefly overlap
neighboring buttons. Receipts:
`build/clock-fix-gate/release-audit-r9-fault-1.21.1-neoforge-20260908.visual-review.json`
and `build/clock-fix-gate/release-audit-r9-fault-1.21.1-neoforge-20260908.fault-review.json`.
The main Forge 1.7.10 case passed in 336 seconds with six zero-exit GUI
clients, no owned-unit core dump and all 39 images directly reviewed. Both
clients completed two resource/sound reloads, the follower reconnected and
independent eight-second PCM measured 0 ms offset (correlation 0.987970).
Playing advances 1:09 to 1:12; paused time holds at 1:13 with byte-identical
original paused-world PNGs and counter 365. An apparent shape discrepancy
during grouped image inspection was disproved by direct byte comparison,
matching SHA256 and individual original-resolution views; no runtime retry
or source change was made. Paused seek/stream, resume, selected substring,
draft retention and permission-error expiry pass. Immediate seek/stream and
non-owner snapshots retain the transient dark-tile caveat; later editing,
both final controllers and all six post-reload/reconnect images show restored
video. Top-band camera cropping and initial build chat below the picture remain
explicit. Receipt:
`build/clock-fix-gate/release-audit-r9-matrix-1.7.10-forge-20260908.visual-review.json`.
Main Fabric 1.20.1 passed in 263 seconds: five zero-exit GUI clients, no
owned-unit core dump, 33 directly reviewed images, follower reconnect and
independent eight-second PCM at 10 ms offset (correlation 0.993608). Paused
world captures hold 00:00:39.600/counter 198; seek, stream change, resume,
selection/draft retention and error expiry pass. Baseline world captures
retain vanilla unsigned-chat warnings and initial leader build/join chat.
Immediate seek/stream snapshots show backing tiles, with video restored by
the later editing capture at 1:46 and saved controllers. The owner/follower
controller files were captured at different times (1:51 English versus 2:45
Commentary); they are not used as simultaneous synchronization proof. Menus
strongly dim the video and hover tooltips temporarily overlap neighboring
buttons. Closed logs/private displays/audio checks pass. Receipt:
`build/clock-fix-gate/release-audit-r9-matrix-1.20.1-fabric-20260908.visual-review.json`.
Main Quilt 1.20.1 passed in 303 seconds: five zero-exit GUI clients, no
owned-unit core dump, all 33 images reviewed and independent eight-second
PCM at 10 ms (correlation 0.997900). Paused time/frame holds at
00:01:03.000/counter 315; selection/draft retention and permission expiry pass.
The playing-seek snapshot shows dark tiles, but video is restored by the
stream-change capture at 2:09 and later edits. Baseline unsigned-chat warnings,
initial build chat and dimmed menus remain explicit. Saved owner/follower
controllers are from different times and are not simultaneous sync evidence.
Follower reconnect and closed resources pass. Receipt:
`build/clock-fix-gate/release-audit-r9-matrix-1.20.1-quilt-20260908.visual-review.json`.

**R9 then failed Forge 1.20.1 and is not accepted as a batch.** The protocol
mismatch was correctly rejected, but the Gradle launcher exited 1 while
storing an unsupported configuration cache. This was a launcher-policy
omission, not a native crash: both protocol and command clients lacked the
manifest's cache-disable argument. The batch stopped after 3,299 seconds,
with nine scoped accepted cases, one failure in 65 seconds and 27 unattempted.
All 52 private displays, audio sinks and game ports are closed; the generated
world is retained and original world/properties restored. There are no
owned-unit journal core dumps. All original receipts plus the copied Gradle
configuration-cache report are preserved across 2,288 hashed files:
`build/clock-fix-gate/release-audit-r9-runtime-failure-preserved-20260908.json`.
The actual argument regression reproduced ten failures (both launchers across
five cache-disabled targets). Both launchers now pass the manifest flag.
All 21 profiles' cache and Quilt/Mod Menu/minimum-loader arguments pass the
new test; focused Gradle exit/polling verification passes 20 tests in 15 seconds.
Fresh full certification remains required; neither old
r9 builds nor the nine scoped passes certify the changed launcher scripts.
A separate corrective Forge 1.20.1 run passed in 258 seconds, bound to the
two changed launcher/test files and unchanged r9 artifact bytes. All 33 original
images were directly reviewed; all five GUI roles launched once and exited zero,
with no owned-unit core dump. Independent eight-second reconnect PCM measures
20 ms offset (correlation 0.993393). Original world/properties are restored,
and owned displays/audio/port are closed. Paused clock/frame, draft/selection
retention and permission-feedback lifetime pass. Baseline vanilla chat warnings,
brief hover-tooltip overlap and transient seek/stream dark tiles remain explicit;
later controls show restored video. Final owner/follower controllers were captured
at different times and are not simultaneous-sync proof. The evidence root is
`build/cache-policy-fix-1.20.1-forge-20260908`; the complete scoped receipt is
`build/clock-fix-gate/cache-policy-fix-1.20.1-forge-20260908.visual-review.json`.
The failed r9 batch is not resumed. The first r10 build subsequently failed
README matrix-label validation after 552 seconds and 80 executed tasks, despite
all ten GameTests passing. It is not a successful build or reproducibility pair.
The required literal `16-artifact` / `21-runtime` labels are restored without
changing the validator. Its original log, 831-input manifest, false result,
closure-time README and previous eighteen-file bundle are preserved in
`build/clock-fix-gate/release-audit-r10-build-failure-preserved-20260908.json`
(22 hashed files). Documentation was excluded from the code freeze; the README
snapshot is explicitly its closure-time state, not a frozen input claim.
No second build or r10 runtime started. The corrected documentation passes all
sixteen manifest tests and the three focused Gradle manifest/hygiene tasks.
Fresh r11 builds started at 07:51 Arizona with no maintained code changes
since r10, but the first build failed after 487 seconds and 71 executed tasks
on a separate synthetic-clock test failure. All ten GameTests passed. Bash
`SECONDS` continued advancing with real time inside the mocked-clock fixture;
the eight-second assertion returned at tick 27 instead of 28. A deliberate
real-time-boundary regression failed for both legacy and modern fixtures.
Unsetting `SECONDS` before assigning the fixture clock fixes its timing, without
changing the extracted production waiter or live eight-second requirement.
All seventeen polling tests and five focused Gradle tasks pass. The failed build,
exact pre-fix test source, deterministic red regression and prior bundle are
preserved across 23 hashed files in
`build/clock-fix-gate/release-audit-r11-build-failure-preserved-20260908.json`.
No second build or r11 runtime started. The full r12 tooling preflight passed
all twenty maintained prerequisite tasks in 23 seconds on unchanged inputs;
`build/clock-fix-gate/release-audit-r12-tooling-preflight-20260908.json` binds its
source and log. Both r12 forced builds passed in 511/508 seconds, executing all
88 tasks, passing all ten GameTests and inspecting all sixteen artifacts in
each. All eighteen bundle files are byte-identical across 831 unchanged inputs:
`build/clock-fix-gate/release-audit-r12-20260908.build-reproducibility.json`.
An independent closed-service check verified the source and retained/current
bundle hashes. The fresh 37-case runtime batch started at 08:24 Arizona under
`cinemarr-release-audit-r12-runtimes-20260908.service`; full direct/physical
runtime review, exact-byte production/native, final-commit and remote CI
certification remain pending.

R12 Forge 1.7.10 terminal acceptance is closed in 352 seconds. All 28 original
images were directly reviewed: both same-JVM world returns, queued playback and
restarted playback show moving video on both clients, without another avatar or
chat covering the program. Both away-world images have no stale television
picture. Initial build chat remains below the picture, and the close camera
crops the top counter band. Initial leader and saved pre-reconnect follower
images were taken at different times and are not simultaneous-sync proof.
Five independent eight-second PCM pairs measure 30/20/20/0/20 ms absolute
offset for initial/queue/restarted/world-return-1/world-return-2 respectively.
All terminal and away-follower drain measurements are -91 dB; the leader stays
audible while the follower is away. All six expected GUI roles launched once
and exited zero, with no owned-unit core dump and closed displays/sinks/port.
The scoped receipt is
`build/clock-fix-gate/release-audit-r12-terminal-1.7.10-forge-20260908.direct-review.json`.
The other 36 cases are not accepted by this legacy receipt.

R12 NeoForge 1.21.1 terminal acceptance is also closed in 249 seconds. All
fourteen original images were directly reviewed. Queue and restarted sequences
show advancing video and readable follower timestamps without avatars or chat
covering the program. Initial leader and saved pre-reconnect images retain
Minecraft's unsigned-chat warning; initial build chat also crosses the lower
leader picture. Those baseline captures have different capture times and are
not simultaneous-sync proof. Three independent eight-second PCM pairs measure
0 ms offset (correlations 0.999622/0.999907/0.999368). Five expected GUI roles
launched once and exited zero, with no owned-unit core dump and closed private
displays/sinks/port.

The initial strict drain review withheld acceptance: queued-follower mean/peak
levels are -63.9/-36.6 dB, not digital silence. Its entire measured 0.257125-second
tail matches the exact installed vanilla toast-dismissal asset at correlation
0.903363, starting at 3.692875 seconds. The replacement client was idle and had
received no media or scheduled audio before its subsequent first playback.
The older helper's fixed 3.7-second onset assumption also rejected this capture;
that helper is preserved as
`build/clock-fix-gate/review-r12-terminal-before-measured-toast-20260908.py`.
The measured-onset matcher checks the entire tail rather than truncating it to
a historical timing window. Five synthetic tests reject earlier program audio,
unrelated noise/tone and silence-as-toast, and accept matching tails at different
late onsets. No maintained source, live threshold or runtime capture changed.
The other three terminal drain captures are -91 dB. Diagnosis is retained in
`build/clock-fix-gate/release-audit-r12-modern-terminal-drain-diagnosis-20260908.json`;
the scoped acceptance receipt is
`build/clock-fix-gate/release-audit-r12-terminal-1.21.1-neoforge-20260908.direct-review.json`.
R12 Forge 1.7.10 pressure acceptance is closed in 494 seconds. The direct
control review covers 39 images and the pressure review covers 42, sharing six
post-reload images (75 distinct originals). All seven GUI roles launched once
and exited zero, with no owned-unit core dump. Independently recomputed
eight-second PCM pairs are 30 ms/0.992956 during browse pressure,
30 ms/0.993560 after browse recovery, 30 ms/0.992498 during segment pressure,
30 ms/0.993820 after segment recovery, and -10 ms/0.999753 after reconnect.
The browse flood sent 255 requests, hit the bounded 16-item queue and recovered
without publishing stale browse results. Media HTTP continued during the held
browse phase. The third, network-only peer sent 3,820 requests over 40 seconds,
remained digitally silent and reset cleanly; no orphaned grants/egress remained.
Owned private displays, audio sinks and game port are closed.

Direct observations show advancing video during both overloads and after two
resource reloads per client, a fixed paused frame, advancing resumed clock,
retained text selection/drafts and readable denial/expiry feedback. Repeated
queue-full chat obscures the follower's lower picture during browse saturation;
recovery captures are clear. Playing seek/stream-change captures and the small
non-owner UI have dark television tiles, with video restored in later captures.
The close camera crops the top counter band, and saved initial/final world
images from different capture times do not prove simultaneous synchronization.
These are explicit visual limitations, not hidden by the scoped pass.
Receipts:
`build/clock-fix-gate/release-audit-r12-pressure-1.7.10-forge-20260908.visual-review.json`
and
`build/clock-fix-gate/release-audit-r12-pressure-1.7.10-forge-20260908.pressure-review.json`.
R12 NeoForge 1.21.1 pressure acceptance is closed in 423 seconds. All 69
original images were directly reviewed (33 normal controls and 36 pressure).
All six GUI roles launched once and exited zero; no owned-unit core dump
occurred. All five independently recomputed eight-second PCM pairs have 10 ms
offsets: browse during/recovery correlations 0.992863/0.990430, segment
during/recovery 0.989529/0.994887, and post-reconnect 0.999989. The browse flood
sent 263 requests while 40 media HTTP requests continued in the held phase.
The network-only peer sent 3,730 requests over 40 seconds, remained digitally
silent and reset cleanly. Workload/recovery bounds, no orphaned grants/egress,
and owned display/sink/game-port closure pass.

Direct views show advancing video during overload and recovery, readable
controls and feedback, retained drafts/selection and a fixed paused frame.
Browse queue-full messages obscure the follower's lower picture; peer-join chat
initially crosses the lower picture during segment setup. Later recovery is
clear. Baseline unsigned-chat toast, cropped left timestamp on the angled
leader camera and dark-tile seek/stream transitions are explicitly retained.
Sequential screenshot pairs can differ one frame; unrelated initial/final world
captures are not simultaneous synchronization proof. The paused action receipt
records 220692 ms, seek 250692 ms and resume 250695 ms before advancement,
consistent with the displayed 3:40, 4:10 and subsequently advancing clock.
Receipts:
`build/clock-fix-gate/release-audit-r12-pressure-1.21.1-neoforge-20260908.visual-review.json`
and
`build/clock-fix-gate/release-audit-r12-pressure-1.21.1-neoforge-20260908.pressure-review.json`.
R12 Forge 1.7.10 fault acceptance is closed in 415 seconds. All 57 original
images were directly reviewed (39 normal/reload controls and 18 post-fault
captures). All six GUI roles launched once and exited zero, with no owned-unit
core dump. Independently recomputed eight-second post-fault PCM offsets and
correlations are transient -10 ms/0.991249, slow -20 ms/0.990858 and exhausted
-10 ms/0.999638; post-reconnect is 0 ms/0.998211. The actual fault sequence
records five transient HTTP attempts, four slow responses and six exhausted
attempts before recovery, advancing to generations 12/13/14. The review's
`actualFaultHttpRequests` field counts all segment requests tagged with each
phase, including recovered successful requests, not just failed responses.
Owned displays/sinks/port are closed.

Both clients show clear advancing test patterns after every fault and after
two resource reloads each. These are post-recovery images, not a claim of
uninterrupted output while retries were exhausted. Pause holds frame 301,
paused seek changes 1:00 to 1:30, and resume advances to 1:33. Permission
feedback and draft/selection retention remain readable. Playing seek/stream
changes and one selection capture have dark tiles, with video subsequently
restored. The small non-owner UI also has a dark-tile background. Cropped top
counter framing and nonsimultaneous initial/final world images remain explicit.
Receipts:
`build/clock-fix-gate/release-audit-r12-fault-1.7.10-forge-20260908.visual-review.json`
and
`build/clock-fix-gate/release-audit-r12-fault-1.7.10-forge-20260908.fault-review.json`.
R12 NeoForge 1.21.1 fault acceptance is closed in 337 seconds. All 51 original
images were directly reviewed (33 normal controls and 18 post-fault captures).
All five GUI roles launched once and exited zero, with no owned-unit core dump
and closed owned displays/sinks/port. Four independently recomputed eight-second
PCM pairs have 0 ms offsets: transient correlation 0.997172, slow 0.999037,
exhausted 0.998207 and post-reconnect 0.996292. The fault sequence records eight
transient attempts, eight slow responses and six exhausted attempts before
recovery in generations 12/13/14. As above, the review's phase-tagged request
counts include successful recovered traffic, not just failed responses.

All eighteen recovered images show unobscured changing video on both clients.
Paused frame 277 at 55.400 seconds remains fixed; the clock advances on resume,
and draft selection/replacement, queue/browse retention and feedback expiry are
visible. The stream-change capture has dark tiles, with video restored in later
images. Initial world captures retain the unsigned-chat warning and leader
build chat over the picture. Angled leader framing crops the left timestamp;
sequential pairs can differ one frame and initial/final world images are not
simultaneous sync proof. No claim of uninterrupted playback through exhausted
retries is made. Receipts:
`build/clock-fix-gate/release-audit-r12-fault-1.21.1-neoforge-20260908.visual-review.json`
and
`build/clock-fix-gate/release-audit-r12-fault-1.21.1-neoforge-20260908.fault-review.json`.

The r12 main Forge 1.7.10 case passed in 336 seconds. All 39 original images
were directly reviewed; six GUI roles launched once and exited zero, no
owned-unit core dump was recorded, and owned displays/audio sinks/game port
are closed. Independently recomputed eight-second PCM has -10 ms lag and
0.998685 correlation. Pause holds frame 373 at 1:14, paused seek reaches 1:44,
Commentary selection remains paused and resume advances. Selected text survives
state changes; replacement and queue/browse drafts behave correctly, and
permission feedback persists then expires. Both clients show changing colored
program geometry after two resource reloads each. Playing seek/stream-change
and the small non-owner UI contain dark transition tiles, with later video
restored. Initial leader build chat is below the picture, the close camera
crops the upper counter, and the saved initial leader/follower frames are not
simultaneous synchronization proof. Receipt:
`build/clock-fix-gate/release-audit-r12-matrix-1.7.10-forge-20260908.visual-review.json`.

The r12 main Fabric 1.20.1 case passed in 243 seconds. All 33 original images
were directly reviewed; five GUI roles launched once and exited zero, no
owned-unit core dump was recorded, and owned displays/audio sinks/game port
are closed. Independently recomputed eight-second PCM has 10 ms lag and
0.999663 correlation. Playing advances 0:34 to 0:37; pause retains identical
frame 188 at 37.600 seconds. Paused seek reaches 1:07, Commentary remains
paused, and resume advances to 1:10. Draft selection survives state changes,
replacement and queue/browse retention work, and permission feedback expires.
English stream-change and selection-after captures have dark tiles; subsequent
editing views restore visible video. Initial world captures show the vanilla
unsigned-chat warning over the upper picture, with build/join chat across the
leader's lower picture. The dedicated feedback row stays legible even when a
duplicate chat message remains behind lower controls. Saved leader controller
English at 1:49 and follower Commentary at 2:45 are different capture times,
not simultaneous selected-stream agreement. Angled leader framing crops the
left timestamp. Receipt:
`build/clock-fix-gate/release-audit-r12-matrix-1.20.1-fabric-20260908.visual-review.json`.

The r12 main Quilt 1.20.1 case passed in 302 seconds. All 33 original images
were directly reviewed; five GUI roles launched once and exited zero, no
owned-unit core dump was recorded, and owned displays/audio sinks/game port
are closed. Independently recomputed eight-second PCM has 0 ms lag and
0.990295 correlation. Playing advances 0:59 to 1:03; pause holds frame 316 at
63.200 seconds. Paused seek reaches 1:33, Commentary selection remains paused,
and resume advances to 1:36. Selected text survives the intervening state
changes, replacement and queue/browse drafts behave correctly, and feedback
expires. Stream-change and selection-after captures show dark tiles with video
restored on replacement and subsequent images. Initial unsigned-chat warnings
cover the upper picture, with leader build chat crossing the lower picture.
Dedicated feedback is legible despite a duplicate chat message behind lower
controls. Saved leader English at 2:14 and follower Commentary at 3:09 are
different capture times, not simultaneous stream-selection agreement. The
angled leader camera crops the left timestamp. Receipt:
`build/clock-fix-gate/release-audit-r12-matrix-1.20.1-quilt-20260908.visual-review.json`.

The r12 main Forge 1.20.1 case passed in 260 seconds, beyond the profile that
stopped r9 on configuration-cache serialization. The mismatch launcher completed
successfully in its single invocation; no retry was used. All 33 original
images were directly reviewed, five GUI roles launched once and exited zero,
no owned-unit core dump was recorded and owned displays/audio sinks/game port
are closed. Independent eight-second PCM has 10 ms lag and 0.997816 correlation.
Playing advances 0:34 to 0:38; pause retains frame 191 at 38.200 seconds.
Paused seek reaches 1:08, Commentary remains paused and resume advances 1:11.
Selected text survives state changes, replacement and queue/browse drafts work,
and permission feedback persists then expires. Playing seek, stream change and
selection-after captures show dark tiles; replacement and later editing images
restore video. Initial unsigned-chat warnings cover the upper picture and
leader build chat crosses the lower picture. Dedicated feedback remains
legible despite duplicate chat behind lower controls. Saved leader English at
1:49 and follower Commentary at 2:45 are different capture times, not proof of
simultaneous stream-selection agreement; angled leader framing crops the left
timestamp. Receipt:
`build/clock-fix-gate/release-audit-r12-matrix-1.20.1-forge-20260908.visual-review.json`.

The r12 main NeoForge 1.20.1 case passed in 261 seconds. All 33 original images
were directly reviewed, five GUI roles launched once and exited zero, no
owned-unit core dump was recorded and owned displays/audio sinks/game port
are closed. Independent eight-second PCM has 20 ms lag and 0.996184 correlation.
Playing advances 0:34 to 0:38, pause holds frame 190 at 38.000 seconds, paused
seek reaches 1:08, Commentary remains paused and resume advances 1:11. Selected
text survives state changes, replacement and queue/browse drafts work, and
permission feedback persists then expires. The stream-change capture at 1:44
has dark tiles; all seven editing captures, including the next at 1:45, show
program geometry. Initial unsigned-chat warnings cover the upper picture and
leader build chat crosses the lower picture. Dedicated feedback stays legible
despite duplicate chat behind lower controls. Saved leader English at 1:49 and
follower Commentary at 2:43 are different capture times, not simultaneous
stream-selection agreement; angled leader framing crops the left timestamp.
Receipt:
`build/clock-fix-gate/release-audit-r12-matrix-1.20.1-neoforge-20260908.visual-review.json`.

The r12 main Fabric 1.20.2 case passed in 251 seconds. All 33 original images
were directly reviewed, five GUI roles launched once and exited zero, no
owned-unit core dump was recorded and owned displays/audio sinks/game port
are closed. Independent eight-second PCM has 10 ms lag and 0.994242 correlation.
Playing advances 0:35 to 0:39; pause holds frame 195 at 39.000 seconds. Paused
seek reaches 1:09, Commentary remains paused and resume advances 1:12. Selected
text survives state changes, replacement and queue/browse drafts work, and
permission feedback persists then expires. The open controller substantially
dims the program behind it, while controls remain legible and world-view video
is clearly visible. Playing seek, stream change and selection-after captures
have dark tiles, with program geometry restored in later editing views. Initial
unsigned-chat warnings cover the upper picture; leader build/join chat crosses
the lower picture. Saved leader English at 1:49 and follower Commentary at
2:46 are different capture times, not simultaneous stream-selection agreement;
angled leader framing crops the left timestamp. Receipt:
`build/clock-fix-gate/release-audit-r12-matrix-1.20.2-fabric-20260908.visual-review.json`.

The r12 main Quilt 1.20.2 case passed in 292 seconds. All 33 original images
were directly reviewed, five GUI roles launched once and exited zero, no
owned-unit core dump was recorded and owned displays/audio sinks/game port
are closed. Independent eight-second PCM has 10 ms lag and 0.999676 correlation.
Playing advances 0:58 to 1:01; pause holds frame 310 at 62.000 seconds and clock
1:02. Paused seek reaches 1:32, Commentary remains paused and resume advances
1:35. Selected text survives state changes, replacement and queue/browse drafts
work, and permission feedback persists then expires. Open-controller backgrounds
are substantially dimmed while controls are readable and world video is clear.
Playing seek, stream change and selection-after have dark tiles, with geometry
restored in later editing captures. Initial unsigned-chat warnings cover the
upper picture and leader build chat crosses the lower picture. Saved leader
English at 2:13 and follower Commentary at 3:07 are different capture times,
not simultaneous stream-selection agreement; angled leader framing crops the
left timestamp. Receipt:
`build/clock-fix-gate/release-audit-r12-matrix-1.20.2-quilt-20260908.visual-review.json`.

The r12 main Forge 1.20.2 case passed in 254 seconds. All 33 original images
were directly reviewed, five GUI roles launched once and exited zero, no
owned-unit core dump was recorded and owned displays/audio sinks/game port
are closed. Independent eight-second PCM has 10 ms lag and 0.998223 correlation.
Playing advances 0:36 to 0:39; pause holds frame 199 at 39.800 seconds and clock
0:39. Paused seek reaches 1:09, Commentary remains paused and resume advances
1:13. Selected text survives state changes, replacement and queue/browse drafts
work, and permission feedback persists then expires. Controller backgrounds
are substantially dimmed while controls are readable and world video is clear.
The stream-change capture has dark tiles; all seven editing captures show
program geometry, including the next at 1:46. Initial unsigned-chat warnings
cover the upper picture and leader build/join chat crosses the lower picture.
Saved leader English at 1:51 and follower Commentary at 2:43 are different
capture times, not simultaneous stream-selection agreement; angled leader
framing crops the left timestamp. Receipt:
`build/clock-fix-gate/release-audit-r12-matrix-1.20.2-forge-20260908.visual-review.json`.

The r12 main NeoForge 1.20.2 case passed in 267 seconds under the documented
`earlyWindowControl = false` prerequisite. All three playback launch logs confirm
the splash screen was disabled; default splash-enabled startup remains
uncertified. All 33 original images were directly reviewed, five GUI roles
launched once and exited zero, no owned-unit core dump was recorded and owned
displays/audio sinks/game port are closed. Independent eight-second PCM has
10 ms lag and 0.995005 correlation. Playing advances 0:35 to 0:38; pause holds
frame 194 at 38.800 seconds and clock 0:38. Paused seek reaches 1:08, Commentary
remains paused and resume advances 1:12. Selected text survives state changes,
replacement and queue/browse drafts work, and permission feedback persists
then expires. Controller backgrounds substantially dim video while controls
are readable and world video is clear. Playing seek, stream change and
selection-after have dark tiles, with geometry restored on replacement and
later editing captures. Initial unsigned-chat warnings cover the upper picture;
leader build/join chat crosses the lower picture. Saved leader English at 1:50
and follower Commentary at 2:44 are different capture times, not simultaneous
stream-selection agreement; angled leader framing crops the left timestamp.
Receipt:
`build/clock-fix-gate/release-audit-r12-matrix-1.20.2-neoforge-20260908.visual-review.json`.

The r12 main Fabric 1.21.1 case passed in 248 seconds. All 33 original images
were directly reviewed, five GUI roles launched once and exited zero, no
owned-unit core dump was recorded and owned displays/audio sinks/game port
are closed. Independent eight-second PCM has 10 ms lag and 0.995635 correlation.
Playing advances 0:36 to 0:39; pause holds frame 198 at 39.600 seconds and clock
0:39. Paused seek reaches 1:09 while retaining the old paused picture (not a
decoded seek preview), Commentary remains paused and resume advances 1:13.
Selected text survives state changes, replacement and queue/browse drafts
work, and permission feedback persists then expires. Playing seek at 1:43 and
stream change at 1:46 have dark tiles; all seven editing captures show restored
video geometry. Controls remain readable against blurred video. Initial
unsigned-chat warnings cover the upper picture and leader build/join chat
crosses the lower picture. Initial leader frame 30 and follower 939 are
non-simultaneous captures; saved leader English at 1:52 and follower Commentary
at 2:46 likewise do not establish simultaneous stream-selection agreement.
Angled framing crops the upper/left timestamp. Receipt:
`build/clock-fix-gate/release-audit-r12-matrix-1.21.1-fabric-20260908.visual-review.json`.

The r12 main Quilt 1.21.1 case passed in 294 seconds. All 33 original images
were directly reviewed, five GUI roles launched once and exited zero, no
owned-unit core dump was recorded and owned displays/audio sinks/game port
are closed. Independent eight-second PCM has 0 ms lag and 0.990503 correlation.
Playing advances 1:00 to 1:03; pause holds frame 318 at 63.600 seconds and clock
1:03. Paused seek reaches 1:33 while retaining the old paused picture (not a
decoded seek preview), Commentary remains paused and resume advances 1:36.
Selected text survives state changes, replacement and queue/browse drafts
work, and permission feedback persists then expires. Playing seek at 2:07 and
stream change at 2:10 have dark tiles; all seven editing captures show video
geometry. Controls remain readable against blurred video. Initial unsigned-chat
warnings cover the upper picture and leader build chat crosses the lower
picture. Initial leader frame 30 and follower 1075 are non-simultaneous;
saved leader English at 2:15 and follower Commentary at 3:09 likewise do not
establish simultaneous stream-selection agreement. Angled framing crops the
upper/left timestamp. Receipt:
`build/clock-fix-gate/release-audit-r12-matrix-1.21.1-quilt-20260908.visual-review.json`.

The r12 main Forge 1.21.1 case passed in 263 seconds. All 33 original images
were directly reviewed, five GUI roles launched once and exited zero, no
owned-unit core dump was recorded and owned displays/audio sinks/game port
are closed. Independent eight-second PCM has 10 ms lag and 0.999263 correlation.
Playing advances 0:37 to 0:41; pause holds frame 206 at 41.200 seconds and clock
0:41. Paused seek reaches 1:11 while retaining the old paused picture (not a
decoded seek preview), Commentary remains paused and resume advances 1:14.
Selected text survives state changes, replacement and queue/browse drafts
work, and permission feedback persists then expires. Playing seek at 1:44 and
stream change at 1:48 have dark tiles; all seven editing captures show video
geometry. Controls remain readable against blurred video. Initial unsigned-chat
warnings cover the upper picture and leader build/join chat crosses the lower
picture. Initial leader frame 31 and follower 954 are non-simultaneous;
saved leader English at 1:54 and follower Commentary at 2:50 likewise do not
establish simultaneous stream-selection agreement. Angled framing crops the
upper/left timestamp; surrounding terrain does not obscure program picture.
Receipt:
`build/clock-fix-gate/release-audit-r12-matrix-1.21.1-forge-20260908.visual-review.json`.

The r12 main NeoForge 1.21.1 case passed in 254 seconds. All 33 original images
were directly reviewed, five GUI roles launched once and exited zero, no
owned-unit core dump was recorded and owned displays/audio sinks/game port
are closed. Independent eight-second PCM has 10 ms lag and 0.999147 correlation.
Playing advances 0:53 to 0:57; pause holds frame 286 at 57.200 seconds and clock
0:57. Paused seek reaches 1:27 while retaining the old paused picture (not a
decoded seek preview), Commentary remains paused and resume advances 1:30.
Selected text survives state changes, replacement and queue/browse drafts
work, and permission feedback persists then expires. Playing seek at 2:00 and
stream change at 2:04 have dark tiles; all seven editing captures show video
geometry. Controls remain readable against blurred video. Initial unsigned-chat
warnings cover the upper picture and leader build chat crosses the lower
picture. Initial leader frame 30 and follower 1016 are non-simultaneous;
saved leader English at 2:09 and follower Commentary at 3:03 likewise do not
establish simultaneous stream-selection agreement. Angled framing crops the
upper/left timestamp. Receipt:
`build/clock-fix-gate/release-audit-r12-matrix-1.21.1-neoforge-20260908.visual-review.json`.

Nineteen of 37 r12 cases are scoped accepted for their original inputs.
Fabric 26.1.2 subsequently failed and seventeen cases were not attempted, as
recorded at the current certification boundary above. Corrected-source runtime
and complete production/native/final-commit/remote gates remain open.

A fresh read-only review of the accumulated non-documentation diff confirmed
the shared grant cleanup is called in all three managers before replacement
screens are published, mismatch probes launch only once, all GUI exit paths
require successful waits and exactly one zero-status receipt, and freshness
checks still require eight seconds of new telemetry from both clients.
The new synthetic-clock fixture removes Bash's special `SECONDS` behavior only
inside its mock; production timing is unchanged. The new private-X close helper,
actual X protocol/crash regressions, all-target probe argument tests and
CI/Gradle wiring were also reviewed. This source review found no additional
implementation change to make; it does not close the still-pending full final
documentation/source binding or any runtime/remote gate.

Eleven isolated handoff/exit tests
pass; each real case independently requires its complete expected GUI-role
set, exactly one launch/zero exit per role and no owned-unit journal core dump.
Production/native and final-commit/remote certification remain outstanding.
The prepared final-commit review audit additionally rejects a mismatched or
missing review commit and images borrowed from another run/case. Four
isolated identity-guard tests pass; they are tooling fixtures, not acceptance
of the still-unrun final-commit gate.

The evidence scanner now streams Zstandard-compressed ELF core contents as
well as their compressed bytes, including literal matches across read
boundaries. Five synthetic tests cover hidden matches and fail-closed handling
of damaged or unsupported compressed payloads. A separate configured-secret
scan of the actual preserved r8 core subsequently passed: 298,821,744 compressed
bytes and 4,163,739,648 decoded ELF bytes, with no literal matches or decode
errors and the compressed core unchanged. It used read-only configuration
reads from the four stopped test servers. Receipt:
`build/clock-fix-gate/r8-preserved-core-configured-scan-20260908.json`.
This certifies only that single artifact against the configured scan values;
the final current-source/full-evidence scan remains required.

**R8 runtime acceptance is rejected.** The first legacy terminal case emitted
an automated pass, but its leader JVM (PID 680245) received SIGSEGV at 05:45:51
Arizona, four seconds after the disconnect/reset acknowledgement. The gate's
local `hs_err`/core-file search missed systemd's external core storage. The
37-case batch was deliberately interrupted during case two after 558 seconds;
zero cases are accepted, one receipt is a false positive, one case was
interrupted and 35 were not attempted. Original receipts are preserved without
rewriting their reported results. The overriding failure audit is
`build/clock-fix-gate/release-audit-r8-runtime-failure-preserved-20260908.json`:
466 hashed files include the copied compressed core and debugger output, with
all observed private X PIDs/audio sinks absent and both game ports closed.

The core shows the client in `_XDefaultIOErrorExit`/`_XIOError` during shutdown.
The gate previously broadcast TERM to the JVM and its private X server
simultaneously. A new regression runs the actual shell cleanup against a real
private-X client and fails because X is unavailable inside the client's TERM
handler. Signaling the private display owner first lets it stop the command
before X; all four private-X tests passed at that checkpoint. That narrow
correction did not itself close the missing native-crash oracle or certify
Minecraft teardown; the later exit oracle and corrective run above address it.
Fresh complete source-bound runtime and subsequent production/native/final
commit/remote checks remain required; r8 build receipts retain their earlier
source identity.

**Prerelease under hardening; final-commit certification remains open.**
The detailed sections below retain failed and superseded attempts as well as
accepted runs. A historical pass is not an assertion about later source.

- r3 has two byte-identical full builds, all ten required GameTests in each,
  and sixteen inspected JARs. All 37 development cases have scoped acceptance:
  21 main profiles, six terminal/pressure/fault supplements, five Quilt Mod
  Menu cases, and five minimum-loader startup cases. Startup-only checks are
  not playback certification; Mod Menu loading is not manual config-UI testing.
- The exact r3 candidate passed four packaged real-Plex cases (156 directly
  reviewed images and independent physical PCM comparisons), four recovery
  cases and two restart-mid-build lifecycle cases. Independent closure audits
  verified production launch identity, restored server state and teardown.
- Both retained native guests passed fresh three-resolution decoder tests,
  payload parity across all sixteen r3 JARs and the installed/ready/off audit.
  Native tests do not certify full Windows/ARM Minecraft clients; ARM is
  emulated functional/ABI coverage.
- Edits between r3 and r4 affect only three build/manifest validation inputs, not
  product Java. Sixteen manifest regressions and the complete dependency dry
  run pass. Both `release-audit-r4-20260908` frozen builds passed (536 and 508
  seconds), with 87 executed tasks and ten GameTests each. All eighteen bundle
  files match each other and r3. The independent continuity receipt explicitly
  binds the updated source to these unchanged, previously accepted bytes.
- The configured-secret scan passed across 57,675 files and 927,188 distinct
  decoded payloads, with no matches or scan errors. The three scoped commits
  were created, but the first final-commit runtime attempt failed as recorded
  below. The Quilt correction and r5 build pair passed, but the subsequent
  final gate failed as recorded next. A new corrected product bundle,
  exact-byte acceptance, a fresh complete local gate, exact-SHA
  green GitHub CI and downloaded/local artifact parity remain required.
  No tag or publication is authorized.

## Historical September 8 failure: abandoned world-return transfer

The `e7d5f005829ad151c086dc353ffc46bf55ccbc0c` final gate was interrupted
by the graphical-session/user-manager shutdown after 1,923 seconds and three
closed cases (exit 143, SIGTERM). Its output is hash-preserved under
`build/final-commit-session-interrupted-r5-20260908`; it is not a full pass.

The fresh `session2` attempt failed normally after 741 seconds (exit 1, no
signal). All sixteen artifact inspections and ten GameTests passed; no runtime
case reached complete acceptance. The legacy terminal follower failed physical
audio audibility after its second same-JVM world return. The aligned capture
contained only about 0.73 seconds of filtered program audio, against about seven
seconds on the leader. The original eight-second captures, metrics, logs,
screenshots and generated world remain hash-preserved under
`build/final-commit-failed-r5-session2-20260908`. The archive receipt is
`build/clock-fix-gate/final-commit-failed-r5-session2-20260908.archive.json`.
Its source-bound full-gate result remains false and unchanged.

Two distinct problems were found:

- The stability waiter reused the follower's 02:52:32 pre-unload audio timeline
  while it was returning to the overworld at 02:52:44. No new audio was scheduled
  until 02:53:11. The waiter now requires new samples from both clients after
  entry, continued freshness, and eight seconds of unchanged strict A/V bounds.
  Regression fixtures reject stale-only and stalled logs and delayed startup
  cannot borrow earlier healthy time. Physical audibility and sync limits are
  unchanged; failed world-return audibility now names the role and evidence.
- Screen departure removed viewer membership but retained the unacknowledged
  transfer grant. Returning clients received repeated window-rejection messages
  until its 30-second expiry. All three server managers now release a grant
  when its session is no longer tracked, removing that client's queued egress.
  Still-visible sessions and other viewers retain their flow-control ownership.
  Core regression coverage checks same-generation return, late old ownership /
  acknowledgement rejection and preservation of unrelated viewers. Wiring
  checks cover all three managers. The world-return observer now rejects this
  stall even if playback later recovers.

Fourteen log-polling, ten pressure and eight world-change tests pass. Current
unit reports contain 193 core, 40 modern and 238 legacy tests, zero failures /
errors and one/two/two opt-in skips respectively. The corrective private-X
legacy terminal run passed in 369 seconds on its recorded unchanged source.
All 28 required images were directly reviewed; five independently recomputed
eight-second PCM pairs passed at 10–50 ms absolute offset and correlations
0.991577–0.999743. Both world-return pairs measured 10 ms. Follower audio was
scheduled within about one to two seconds after each return, with no abandoned
window errors. All six expected-silent captures measured -91 dB maximum;
the leader remained audible during both follower absences. Queue, EOS,
reconnect, replay, stop and closed client/display/audio/port checks passed.
The baseline leader image includes a construction chat message below the TV;
the remaining reviewed program views are clear. The scoped receipt is
`build/clock-fix-gate/world-transfer-fix-1.7.10-forge-20260908.direct-review.json`.

A subsequent fixture audit found modern timelines do not emit the legacy
`started` field. The waiter now requires it only for legacy; realistic modern
fixtures first failed the unconditional check and pass after correction. The
corrective runtime keeps its original source identity; it is not relabelled
as a run of this later tooling edit. These edits change product Java, so
historical bundle continuity is no longer sufficient.
Fresh exact-byte real-Plex acceptance, full local and remote gates, artifact
parity and final cleanup remain required.

The first r6 build was subsequently stopped deliberately after 300 seconds
(SIGTERM/exit 143, source unchanged); no second build ran and no reproducibility
pass is claimed. The stopped receipt and prior bundle remain preserved under
`build/clock-fix-gate/release-audit-r6-20260908*` and
`build/reproducibility/release-audit-r6-20260908-previous-bundle`.
Further review found that removing every visible screen before publishing a
replacement can reset the client assembler even when both TVs belong to the
same party. A new deterministic core test fails the session-only retention
policy. The shared registry now retains a reservation only if its session has
a continuously visible TV, using current TV/session mappings and previous TV
IDs supplied by all three managers. A partial screen change that leaves one
old party TV visible preserves flow control. Root/core checks pass after the
correction; full updated validation remains required. This extension is later
source than the accepted 369-second corrective run and is not retroactively
certified by that receipt.

Both r7 forced full builds then passed in 521 and 501 seconds, with all 87
tasks executed and all ten required GameTests passing in each. Both inspected
all sixteen artifacts; all eighteen bundle files are byte-identical. The
independent candidate binding checks the 829 frozen non-documentation inputs,
the first-build snapshot and the current release bundle. The source-bound
37-case r7 runtime batch subsequently failed; no full runtime pass is claimed.
The build receipt is
`build/clock-fix-gate/release-audit-r7-20260908.build-reproducibility.json`.
Current exact-byte production/native acceptance and final-commit local/remote
certification are still required.

The first two r7 terminal cases now have closed scoped acceptance. Legacy
completed in 344 seconds: all 28 original PNGs directly reviewed, five
independent eight-second PCM comparisons at 0–20 ms absolute offset and
correlations 0.991832–0.999880, both same-JVM world returns, queue/EOS,
reconnect/replay/stop and complete owned-resource cleanup. The away images
show the Nether with no retained program; both return sequences show clear
advancing program frames without the abandoned-window error. Initial leader
construction chat is below the TV. The receipt is
`build/clock-fix-gate/release-audit-r7-terminal-1.7.10-forge-20260908.direct-review.json`.

Modern terminal completed in 249 seconds: fourteen directly reviewed PNGs,
three independent eight-second PCM pairs at 0 ms and correlations
0.994589–0.999363, and closed logs/displays/audio sinks/game port. Queue and
replay captures are clear. Initial leader and pre-reconnect follower images
contain the vanilla unverified-chat warning over the upper-right region; the
identifying program remains visible. The first strict review stopped on a
non-silent post-EOS follower sample. It was not discarded or rerun: direct
waveform attribution matches the hash-verified installed vanilla
`minecraft/sounds/ui/toast/out.ogg` asset at correlation 0.902725, after 3.7
seconds of digital silence. The capture measures -66.9 dB mean/-42 dB maximum;
the other three terminal captures measure -91 dB. It is explicitly not
described as digitally silent. The maintained gate thresholds are unchanged.
The resulting scoped receipt is
`build/clock-fix-gate/release-audit-r7-terminal-1.21.1-neoforge-20260908.direct-review.json`.
Neither terminal receipt accepts the complete batch or final release gates.

R7 legacy pressure then completed in 511 seconds and has both scoped reviews:
`build/clock-fix-gate/release-audit-r7-pressure-1.7.10-forge-20260908.pressure-review.json`
and the adjacent `.visual-review.json`. All 75 distinct images were directly
reviewed (42 pressure/reload references plus 39 normal-control references,
sharing six post-reconnect images). Four independent eight-second pressure
PCM pairs measure 40 ms; the final post-reload/reconnect pair measures 50 ms
absolute offset. Correlations are 0.998454–0.999890. The browse flood issued
262 requests while playback continued; the network-only third peer issued
3,450 requests over 40 seconds and produced no local media audio. All observed
orphaned grant/egress item/byte counts are zero. Two resource/sound reloads per
client, follower reconnect, controls, strict closed client logs and complete
owned display/audio/port cleanup passed. Browse-flood feedback covers part of
the follower's lower program view; recovery and all six post-reconnect views
are clear. Immediate playing-seek/stream-change UI captures show the backing
TV texture; later frames show recovered video. Paused frame retention, draft
selection/replacement, permission feedback lifetime and clock progression
are directly reviewed without claiming uninterrupted transition imagery.
Modern pressure subsequently completed in 421 seconds with all 69 distinct
images directly reviewed: 36 pressure captures and 33 normal-control captures.
All four independent eight-second pressure PCM pairs measure 0 ms; the final
reconnect comparison measures 10 ms, with correlations 0.996587–0.999695.
The browse flood issued 264 requests; the third network-only peer issued
3,720 requests over 40 seconds. Orphaned transfer/egress diagnostics remain
zero, the peer is locally silent, and all closed client logs/private displays,
owned audio sinks and the port pass cleanup. The scoped receipts are
`build/clock-fix-gate/release-audit-r7-pressure-1.21.1-neoforge-20260908.pressure-review.json`
and the adjacent `.visual-review.json`. Both world screenshots have vanilla
unverified-chat toasts, while the identifying fixture remains visible. Flood
feedback partly covers the requesting follower's lower view. Playing seek and
stream-change captures show backing texture; later editing/controller/world
captures show recovered video. Paused clock/frame retention, draft selection
and replacement, permission feedback lifetime and full small-window controls
are directly reviewed.

R7 legacy fault recovery subsequently passed in 418 seconds. All 57 original
PNGs were directly reviewed: eighteen post-fault images plus 39 normal-control,
baseline and post-reload/reconnect images. Each of the transient, slow-delivery
and exhausted-retry recovery sequences shows identifiable advancing video on
both clients without overlay/avatar obstruction. Independent eight-second PCM
pairs measure 10/10/30 ms absolute offset for these three phases; the final
post-reload/reconnect comparison is 30 ms. Correlations span 0.992030–0.997160.
The observer records actual HTTP phase traffic and distinct recovery
generations 12, 13 and 14. Both clients completed two resource/sound reloads,
and the follower reconnected. Permissions, edit retention, paused-frame and
clock behavior, closed client logs, private displays, owned audio sinks and
the game port pass. Immediate seek/stream-change and small-window transition
images retain their backing-texture caveat; later recovery and final UI/world
images are clear. Initial leader construction chat appears below the TV.
The receipts are
`build/clock-fix-gate/release-audit-r7-fault-1.7.10-forge-20260908.fault-review.json`
and the adjacent `.visual-review.json`.

R7 modern fault recovery passed in 354 seconds, with all 51 original images
directly reviewed. The eighteen post-fault images show clear, advancing fixture
patterns/counters on both clients after transient, slow and exhausted-retry
faults. Their independent eight-second PCM pairs measure 10/10/0 ms, with
correlations 0.992464/0.997077/0.991351; the final reconnect pair measures 0 ms
and 0.993019. Actual HTTP traffic in the three fault modes is 147/47/6 requests
(these are phase traffic counts, not claims that every request failed), with
distinct recovery generations 12/13/14. Controls, closed client logs, owned
private displays/audio sinks and the game port pass. Both baseline world images
retain vanilla unverified-chat toasts; the initial leader also has construction
chat below the program. All recovered-video captures are clear. The playing-seek
capture retains video; the immediate stream-change image shows backing texture,
followed by visible recovery in editing and later captures. Receipts are
`build/clock-fix-gate/release-audit-r7-fault-1.21.1-neoforge-20260908.fault-review.json`
and the adjacent `.visual-review.json`.

The main Forge 1.7.10 matrix case then passed in 338 seconds. Its 39 original
images have completed direct review, with two resource/sound reloads per client,
follower reconnect, closed client logs/displays/audio sinks and an independent
eight-second PCM pair at 10 ms (correlation 0.993577). The owner clock advances
1:04 to 1:07, stays paused at 1:07 with an unchanged frame, seeks while paused
to 1:37 and resumes to 1:41. Selected draft text survives updates and replacement;
search/session drafts survive navigation and explicit clearing works. Playing
seek/stream-change and non-owner small-window transition captures show backing
texture; subsequent editing, final controllers and all six post-reconnect
captures show recovered program. The close camera crops the fixture timestamp
and part of the counter, so these images establish identifying moving content,
not whole-screen framing. Initial leader construction chat is below the TV;
later recovered video has no warning or other-player obstruction. Receipt:
`build/clock-fix-gate/release-audit-r7-matrix-1.7.10-forge-20260908.visual-review.json`.

Main Fabric 1.20.1 passed in 255 seconds, with all 33 original images reviewed,
follower reconnect, closed client logs/displays/audio sinks and an independent
eight-second PCM comparison at 10 ms (correlation 0.998570). The owner clock
advances 0:35 to 0:38; pause holds identical fixture time 00:00:38.400/counter
192 across the world-image pair, paused seek reaches 1:08, Commentary selection
stays paused and resume reaches 1:11. Permission feedback persists and expires;
draft selection/replacement and navigation retention pass. Immediate playing
seek/stream and editing-state-change images show backing texture, with program
recovered in subsequent editing and final controller images. Both baseline
world images have vanilla unverified-chat toasts; leader construction/join chat
is below the TV, and no other player obstructs it. Separately timed leader UI
at 1:49 and follower UI at 2:41 are not presented as simultaneous synchronization
proof; the physical PCM pair supplies that measurement. Receipt:
`build/clock-fix-gate/release-audit-r7-matrix-1.20.1-fabric-20260908.visual-review.json`.
The refreshed focused checks pass all 14 log-polling, eight world-change and ten
segment-pressure tests, plus shell syntax and diff whitespace checks.

Main Quilt 1.20.1 passed in 305 seconds without the earlier cache-race startup
failure. All 33 original images were directly reviewed, with follower reconnect,
closed client logs/private displays/audio sinks and independently recomputed
eight-second PCM at 10 ms (correlation 0.998408). Playing clock advances 1:02 to
1:05; pause holds identical fixture 00:01:05.400/counter 327, paused seek reaches
1:35, Commentary remains paused and resume reaches 1:38. Playing seek at 2:09
shows backing texture, but stream-change at 2:12 and all seven editing captures
show program. Draft selection/replacement/navigation and permission-feedback
expiry pass. Both baseline world captures have vanilla unverified-chat toasts;
initial construction chat is below the TV, and no other player obstructs it.
The three contained 640x480 controller views have visible program; leader and
follower captures are separately timed, not contemporaneous clock measurements.
Receipt:
`build/clock-fix-gate/release-audit-r7-matrix-1.20.1-quilt-20260908.visual-review.json`.

Main Forge 1.20.1 passed in 261 seconds, with all 33 original images reviewed,
follower reconnect, closed client logs/private displays/audio sinks and an
independent eight-second PCM pair at 20 ms (correlation 0.997478). Playing
clock advances 0:35 to 0:39; pause holds fixture 00:00:39.000/counter 195,
paused seek reaches 1:09, Commentary remains paused and resume reaches 1:12.
Playing seek/stream images show backing texture, followed by visible program
in all seven editing captures and final controllers. Draft retention, selected
text replacement and permission-feedback expiry pass. Baseline world images
retain vanilla unverified-chat toasts and leader construction/join chat below
the TV; no other player obstructs the program. Separately timed leader 1:50
and follower 2:44 controller screenshots are not clock-sync proof. Receipt:
`build/clock-fix-gate/release-audit-r7-matrix-1.20.1-forge-20260908.visual-review.json`.

Main NeoForge 1.20.1 passed in 267 seconds with all 33 original images reviewed,
follower reconnect, closed client logs/private displays/audio sinks and an
independent eight-second PCM pair at 10 ms (correlation 0.995411). Clock advances
0:35 to 0:38 and remains paused at 0:38; the paired paused world images hold
identical fixture 00:00:39.000/counter 195. Paused seek reaches 1:08, Commentary
stays paused and resume reaches 1:12. Playing seek/stream captures show backing
texture; all seven editing and final controller images show program again.
Draft selection/replacement/navigation and permission-feedback expiry pass.
Both baseline world images have vanilla chat-warning toasts, leader construction
chat remains below the TV and no other player obstructs program. Controller
capture times are distinct (leader 1:50, follower 2:46); PCM supplies the
independent synchronization measurement. Receipt:
`build/clock-fix-gate/release-audit-r7-matrix-1.20.1-neoforge-20260908.visual-review.json`.

Main Fabric 1.20.2 passed in 248 seconds with 33 original images reviewed,
reconnect, closed client logs/private displays/audio sinks and independent
eight-second PCM at 10 ms (correlation 0.998523). Clock advances 0:35 to 0:38;
pause holds clear fixture 00:00:38.600/counter 193, paused seek reaches 1:08,
Commentary stays paused and resume reaches 1:11. Permissions, error lifetime,
selected-text replacement and navigation/clearing of drafts pass. Controller
background video is strongly dimmed, so those captures establish readable,
contained controls, not clear video; world captures identify the program.
Immediate playing seek/stream captures show backing texture before dimmed
program returns in editing/final UI. Baseline world views retain vanilla chat
toasts and initial leader construction/join chat below the TV; no other player
obstructs it. Receipt:
`build/clock-fix-gate/release-audit-r7-matrix-1.20.2-fabric-20260908.visual-review.json`.

Main Quilt 1.20.2 passed in 295 seconds, with all 33 images directly reviewed,
reconnect, closed client logs/private displays/audio sinks and independently
recomputed eight-second PCM at 10 ms (correlation 0.999878). Playing clock
advances 0:58 to 1:01; pause holds fixture 00:01:01.600/counter 308, paused
seek reaches 1:31, Commentary stays paused and resume reaches 1:35. Playing
seek at 2:05 retains dimmed video; stream change at 2:08 shows backing texture,
then editing/final UI have faint program again. Draft selection/replacement,
navigation retention/clearing and permission-feedback expiry pass. Background
video is strongly dimmed under the UI but controls remain readable; clear
paused-world captures provide unobscured fixture evidence. Baseline world
views retain vanilla chat toasts and initial construction chat below the TV;
no other player obstructs the program. Controller capture times differ and
are not simultaneous clock proof. Receipt:
`build/clock-fix-gate/release-audit-r7-matrix-1.20.2-quilt-20260908.visual-review.json`.

Main Forge 1.20.2 passed in 265 seconds: 33 original images reviewed, follower
reconnect, closed client logs/private displays/audio sinks and independent
eight-second PCM at 10 ms (correlation 0.995061). Clock advances 0:33 to 0:37;
pause holds identical fixture 00:00:37.200/counter 186, paused seek reaches
1:07 with Commentary still paused, and resume reaches 1:10. Playing seek/stream
captures show backing texture, followed by faint program behind strongly dimmed
editing/final UI. Controls, permission-feedback expiry, selected-text replacement,
draft navigation retention and clearing pass. Clear paused-world captures
establish unobscured fixture content; baseline world captures retain vanilla
chat-warning toasts and initial leader construction chat below the TV, with no
other player blocking program. Separately timed controller views are not
simultaneous clock evidence. Receipt:
`build/clock-fix-gate/release-audit-r7-matrix-1.20.2-forge-20260908.visual-review.json`.
Fourteen of 37 development cases passed their scoped reviews, comprising 564
distinct directly reviewed images. The batch then failed main NeoForge 1.20.2
follower startup: 107 seconds, exit 1, no interruption signal, unchanged source
and bundle. The first exception is `FileSystemNotFoundException` in
`UnionFileSystemProvider.getFileSystem`, reached from the early loading-window
renderer. Repeated `Already building` exceptions culminate in a rendering-overlay
crash. This is not a passed case or an accepted batch; twenty-two later cases
were not attempted. The 4,720-second terminal batch receipt is
`build/clock-fix-gate/release-audit-r7-runtime-batch-20260908.result.json`;
the failed case receipt is
`build/clock-fix-gate/release-audit-r7-matrix-1.20.2-neoforge-20260908.result.json`.
Original logs, crash report and generated world remain under
`build/release-audit-r7-runtimes-20260908/matrix/1.20.2-neoforge`.
Corrective verification, complete runtime certification and all external/final
gates remain open. No retry has been used to turn this failure into a pass.

The loader diagnosis reproduced missing-filesystem lookups without loading
Minecraft or Cinemarr: the installed `cpw.mods:securejarhandler:2.1.24` returned
2,668 missing-existing-filesystem results during 3,348,971 concurrent lookups;
the original filesystem was never closed and was still present afterward.
Its published source synchronizes writes to a `HashMap` but not the lookups.
This reproduces the same exception mechanism; it is not a replay of the exact
thread schedule of the failed Minecraft run. The source-only diagnostic is
`build/clock-fix-gate/UnionLookupRace.java`. A hash inventory preserves 3,363
failed-batch evidence files in place, with all four failed-case private X
processes absent, both owned audio sinks absent, port 25580 closed and the
generated world retained:
`build/clock-fix-gate/release-audit-r7-runtime-failure-preserved-20260908.json`.

NeoForge 20.2.93 acceptance now explicitly uses FML's supported
`earlyWindowControl = false` setting in each task-owned client's
`config/fml.toml`. This selects FML's normal no-splash window handoff instead
of its concurrent early-display renderer. It does not change Cinemarr bytes,
disable in-game rendering, or relax any playback/audio/UI/lifecycle check.
Configuration is scoped to the `1.20.2-neoforge` profile; reconnect preserves
existing settings, and conflicting or symlinked configuration is rejected.
Fifteen log-polling/configuration tests passed, after the new configuration and
fatal-error assertions failed on the previous implementation. The corrective
runtime subsequently passed as described below. This is a loader compatibility prerequisite, not
certification of the loader's default splash-enabled launch.
Users encountering this startup signature should close Minecraft and set
`earlyWindowControl = false` in that instance's `config/fml.toml`; retain its
other settings. Neither newer loaders nor unrelated instances are changed by
the gate. The authoritative implementation is in the published
[FML 1.0.16 sources](https://maven.neoforged.net/releases/net/neoforged/fancymodloader/loader/1.0.16/loader-1.0.16-sources.jar),
`ImmediateWindowHandler.load` and its `DummyProvider`; the lookup race is in
[SecureJarHandler 2.1.24 sources](https://maven.neoforged.net/releases/cpw/mods/securejarhandler/2.1.24/securejarhandler-2.1.24-sources.jar),
`UnionFileSystemProvider.getFileSystem`.

The corrective no-splash run completed in 263 seconds with unchanged source,
unchanged r7 bundle bytes, and original world/properties restored. All 33
required originals were directly reviewed; the independent eight-second PCM
comparison measured 0 ms lag with correlation 0.991556. All five GUI launches
(mismatch, command, leader, follower, reconnected follower) explicitly logged
the no-splash path and started exactly once; their private X processes and
owned audio sinks were absent after completion, and port 25580 was closed.
The modern missing-client check uses the separate protocol probe, not a sixth
GUI. Paused world views hold `00:00:39.400` / counter 197; control clocks
advance 0:36 to 0:39, hold while paused, seek to 1:09, resume to 1:12, and
seek while playing to 1:43. Immediate playing-seek/stream captures show backing
texture before later clear program. Baseline chat-warning toasts, construction
chat below the leader's TV, and strongly dimmed UI video remain explicit
caveats. Permission feedback and draft/selection retention pass. The receipt
is `build/clock-fix-gate/neoforge-splash-1.20.2-20260908.visual-review.json`;
this scoped correction does not convert failed r7 into a complete batch.

Follow-up inspection found an older automatic second launch in the
wrong-protocol GUI wrapper, reusing the same evidence paths. A standalone
execution of the actual wrapper produced two launches after one failure.
The wrapper now propagates the first result without retry. Sixteen focused
tests pass, including success/failure launch counts on all 21 profiles;
eight world-change and ten pressure regressions also pass. The no-splash
runtime above predates this final wrapper correction and retains its own
source identity. R8 will freshly bind both launcher corrections, rerun all
37 cases and still require all production/native/final-commit/remote gates.

Both r8 forced full builds passed in 505 and 494 seconds: all 87 actionable
tasks executed, all sixteen artifacts inspected, and all ten GameTests passed
in each actual GameTest server log. Both eighteen-file bundles match
byte-for-byte; an independent check verifies those files and all 829 unchanged
non-documentation inputs at that checkpoint. The serial 37-case runtime batch
was subsequently interrupted on the teardown finding at the top of this
document; no r8 runtime case is accepted. Reproducibility receipt:
`build/clock-fix-gate/release-audit-r8-20260908.build-reproducibility.json`.
Task-local r8 handoff checks require the independently closed 37-case/41-review
audit and unchanged referenced evidence before deployment/native preparation.
Six isolated guard tests pass; their temporary fixtures are not release
evidence. The r8 precommit check includes hash preservation of the failed r7
batch, and requires the failed/corrective NeoForge roots in the final deep scan.
The source-only configured-secret scan separately passed for its recorded
840 inputs and 739 decoded payloads with zero matches/errors, authenticating
the four stopped/autostart-off DiscPanel test servers from the designated
credential file. Receipt:
`build/clock-fix-gate/release-audit-r8-current-source-secret-scan-20260908.json`.
That scan predates this documentation checkpoint and does not replace the
post-acceptance deep source/artifact/evidence scan.

A read-only Proxmox audit at 11:46 UTC successfully authenticated via the supplied
password file and verified both retained native guests ready and powered off.
The Windows/ARM disk sizes remain 18,980,405,248 and 1,237,909,504 bytes. Nothing
was booted, reinstalled or modified. Observation receipt:
`build/native-smoke/retained-vm-audit-20260908-r7-runtime-readonly.json`.
This proves current availability/stopped state, not r7 native acceptance.

Task-local final-gate orchestration now recognizes r7 as a new acceptance
boundary, not historical byte continuity. The precommit binding requires
the complete r7 development closure, all four packaged reviews, six external
recovery/lifecycle cases, native payload parity and a fresh source-hash-bound
configured-secret scan before the final gate can start. Its negative check
currently stops on the absent full-runtime closure without writing a result
or launching anything. Syntax/argument checks pass; its positive acceptance
path is still unverified until the actual prerequisites finish. No final gate,
commit or push has occurred for these changes.

## September 8 earlier final-commit attempt: Quilt startup cache race

The complete local gate on `065b6c5352d7abd403e251bee270cd4ab689c3d3`
completed its build/inspection phase and eight runtime cases: both legacy
and modern terminal, pressure and fault supplements, then Forge 1.7.10 and
Fabric 1.20.1 in the main matrix. These eight cases have scoped direct-image,
independent PCM and closed-resource reviews. Quilt 1.20.1 then failed before
Cinemarr initialized. Its leader crashed at 22:38:18 Arizona time because
the intermediary Minecraft JAR in Loom's cache was absent. The concurrently
starting follower logged a missing previous cache-lock owner and a cache
rebuild; the missing JAR was recreated at 22:38:20. This is development
launcher failure, not successful playback or evidence of a packaged decoder
failure.

The matrix recorded the failure and began the next Forge profile. The parent
attempt was deliberately terminated before changing source. Its original
receipt records 3,765 seconds, exit 143, signal 15, unchanged source/HEAD and
a clean worktree, with `automatedPassed: false`. No runtime retry was made.
The original log, crash report, partial next case and all eight scoped reviews
remain under `build/clock-fix-gate/final-commit-*20260908*` and
`build/packaged-final-commit-local-gate-20260908/matrix`. Its three supplemental
roots were subsequently moved, without changing their 1,604 files, to
`build/final-commit-failed-065b6c5-20260908`. The hash-checked archive inventory
and original-to-archived path mapping are recorded in
`build/clock-fix-gate/final-commit-failed-065b6c5-20260908.archive.json` and its
`.archive-inputs.json` companion. Old receipts retain their original paths;
that map resolves them without misidentifying a later run as old evidence.
The stopped unit has
no main PID. Owned ports/audio sinks are absent. The interrupted Forge case's
29 original world files and server properties were restored and hash-checked;
the generated world/properties remain preserved. The restoration receipt is
`build/clock-fix-gate/final-commit-local-gate-20260908.manual-restoration.json`.

The correction extends the existing sequential-startup branch to all five
Quilt profiles. Both clients still remain connected throughout the complete
two-client controls/A/V/reconnect acceptance. A fatal Quilt loader exception
now fails bootstrap immediately instead of leaving its error dialog until the
180-second initialization deadline. Three regressions execute the actual
shell startup/error checks: all maintained Quilt launch orders, first-attempt
terminal failure for either role, and fatal-versus-benign loader diagnostics.
They fail on the old implementation; all twelve log-polling tests pass after
the correction. The fresh source-bound Quilt run completed in 293 seconds with
exit zero and unchanged source. Its 33 required images were directly reviewed,
and the independently recomputed eight-second PCM comparison passed at 10 ms
lag and 0.999732 correlation. Both initial clients started once, the leader
reaching readiness before follower startup. The follower reconnected normally;
all three closed logs, private displays, owned sinks and game port passed
cleanup checks. Immediate seek/stream-change captures show backing texture;
later edit captures visibly restore video. Vanilla chat-warning toasts overlap
the upper time area of initial/reconnected world views without obscuring the
identifying central program. These limitations are retained in the review.
Receipts are `build/clock-fix-gate/quilt-launch-order-1.20.1-20260908.result.json`
and its `.visual-review.json` sibling. This is separate corrective validation,
not a retry counted toward the failed candidate.

Both r5 forced builds subsequently passed in 542 and 517 seconds. Each executed
all 87 actionable tasks, all ten GameTests and all sixteen artifact inspections.
All eighteen bundle files are byte-identical to each other and r3/r4. The
independent `release-audit-r5-byte-continuity-20260908.json` audit checks the
exact five non-documentation changes since r3 (only two since r4), all 37 r3
runtime receipts and 41 scoped reviews, closed packaged/native evidence and
the new corrective Quilt review. No product Java changed; old source identities
are preserved. The build unit exited successfully with no remaining main PID.

The refreshed deep configured-secret scan passed across 60,242 files and
944,978 decoded payloads with no matches or scan errors. It includes the
archived failed supplements, partial matrix and corrective Quilt evidence.
The receipt is
`build/clock-fix-gate/release-audit-r5-configured-secret-scan-20260908.json`.
The corrected source is not yet final-commit or remote-CI certified.

## Historical September 8 r4 review pass

This section records the r4 review and its then-current evidence, not completion
of the later r12 source review, runtime certification or final-commit CI gates.

The complete changed-file inventory contains 187 plan-scoped paths: 121 Java
implementation/test files, 57 build/verification files and nine documents.
No unclassified change is included in the proposed commit groups. The latest
review rechecked the critical ownership boundaries against their regressions:

- Shared connection cleanup runs on the client executor and rejects delayed
  disconnects for replaced connections. Legacy world unload separately retains
  negotiation/clock state while closing media; its live same-JVM dimension
  tests and resource-reload evidence cover the platform boundary.
- Segment metadata reads do not wait for network fetch locks. Browse and
  playback have independent bounded workers; cached and decoded payloads have
  explicit byte/count limits. Coordinator responsiveness, stale-result
  retirement, disconnected transfer ownership and egress reentrancy have
  deterministic tests plus the r3 pressure/fault acceptance described below.
- Audio startup and physical cursor accounting, active-underrun versus terminal
  drain, context-owned legacy OpenAL cleanup and paused-texture transfer were
  checked with their tests and direct/physical r3 evidence. Buffer limits do
  not claim to cap every internal FFmpeg allocation or forcibly cancel a native
  call already executing.
- Controller geometry/feedback are shared policies, with normal-scale overlap
  tests, denial/expiry tests and real widget/capture evidence. Adapter drift
  checks enforce the intended differences in camera, renderer and loader APIs.
- Manifest routing, family ordering, canonical JAR handling, actual shell
  fault/cleanup checks, production classpath provenance, retained-guest reuse,
  and CI's required aggregate dependencies were reviewed. The task/project
  binding gap was fixed rather than dismissed because current targets happened
  to be correct. All 31 local links across eleven Markdown files resolve.

This closes that review pass, not release certification or a guarantee that
no defect remains. Both r4 full builds passed 87 executed tasks and all ten
GameTests, and the entire eighteen-file bundle matches r3. The build service
exited with status zero and an empty cgroup. The independent continuity audit
verified both build snapshots, current bundle, all 829 updated source inputs,
the exact three-file code change, all 37 runtime results, their 41 scoped
reviews, and closed packaged/native audits. Its receipt is
`build/clock-fix-gate/release-audit-r4-byte-continuity-20260908.json`; it does
not relabel the r3 source or certify a new runtime/remote CI run.
The fresh configured-secret scan also passed: 57,675 files, 927,188 distinct
decoded payloads, 111 evidence roots plus tracked/untracked source, zero
matches and zero errors. It includes nested archives, compressed logs and
decoded Minecraft region chunks. The receipt is
`build/clock-fix-gate/release-audit-r4-configured-secret-scan-20260908.json`;
credentials stayed in process memory/child stdin, not source or printed logs.
The final-commit gates remain open at this checkpoint. Latest unit reports
contain 191 core, 40 modern and 236
legacy tests with no failures/errors and one/two/two opt-in skips; scoped native
and credentialed acceptance remains separate from those opt-in unit tests.

## 2026-09-07 transfer ownership and decoder-buffer review

The `pressure-1.7.10-forge-inbox1-20260907` run failed after 258 seconds,
with unchanged source and exit 1. Browse overload and the third peer's real
network-pressure phase completed, but recovery did not observe a global
zero-transfer sample. Five post-departure samples showed two viewers, one
transfer grant, bounded queues and zero reported audio underruns. Those
aggregate counts cannot identify the grant's owner. This failed run is not
accepted, and no direct-image acceptance is claimed for it.

The recovery observer had required all transfers to reach zero while two
viewers continued requesting media. Ownership diagnostics now count grants,
queued items and bytes whose owner is absent from the actual server player
list. Every pressure/recovery sample must contain all three diagnostics and
report zero orphaned resources; ordinary per-viewer/session work bounds,
two-viewer playback, physical audio and direct-image requirements remain.
The regression fails on the old global-idle rule. Tests also reject missing
ownership fields and each kind of retained orphan, including late
resurrection. New registry/scheduler tests distinguish departed ownership
from a still-active viewer. This supplies a stronger ownership-specific
oracle, not retrospective acceptance of the failed run.

Source review also found the 26.x egress constructor still used only the
global byte limit. It now declares the same 2 MiB/client, 8 MiB/session and
16 MiB/global limits as modern and legacy, with a three-manager drift check.

Compressed input was limited to 32 MiB, but decoded audio lists had no
separate retained-byte/frame cap. The shared Java-8-compatible decoder budget
now reserves before allocating PCM: 64 MiB per segment, 16,384 audio frames
and 1 MiB per audio frame. Sample arithmetic uses a long. All software and
hardware RGBA conversion paths reject individual frames above 128 MiB before
allocating their output. These are Java/output-buffer bounds, not a claim
that all internal FFmpeg memory has been measured or capped. Interrupts are
checked before starting decode, between decoded frames and before software
publication; waiting for a hardware probe is interruptible. Native resources
still close through existing finally/try-with-resources paths; a native call
already executing is not forcibly interrupted by these checks.

The first legacy build caught a missing `HashSet` import in the new
diagnostics; that failed log is preserved and the import is corrected.
Updated reports contain 191 core / 40 modern / 236 legacy tests, zero
failures/errors and one/two/two opt-in skips. Legacy `verifyRelease` passed
in 25 seconds including reobfuscated artifact inspection
(`decoder-budget-legacy-verify-r2-20260907.log`). Final root verification
passed in nine seconds, including nine pressure, three private-X and nine
private-window regressions (`decoder-budget-root-final-tests-20260907.log`);
some tasks were cached/up-to-date, so this is not a forced full build.
The source-frozen legacy `ownership1` pressure attempt completed in 488
seconds with exit zero and unchanged source. Its `.pressure-review.json`
records all 42 directly inspected pressure/post-reload/reconnect captures,
four independently recomputed eight-second PCM comparisons at 20 ms
(correlations 0.996484 / 0.997183 / 0.996715 / 0.996894), four closed client
logs with strict health/reset checks, absent private X processes/game port
and no task audio modules. The service is inactive, main PID zero, exit zero.
The network-only peer issued 3,940 requests in 40,024 ms, received 204 chunks /
3,298,272 bytes, acknowledged 102 windows and exercised 2,310 rate plus 1,528
window rejections with zero unexpected errors. All five recovery samples
have two remaining viewers and zero orphaned grants/items/bytes. Both
ordinary clients completed two resource reloads; the six final reconnect
views have no debug overlay or avatar obstruction. This accepts only the
recorded development pressure/reload scope, not the additional ordinary
control images or final packaged bytes. Modern representative verification,
full candidate rebuilds and final certification remain open.

Modern `pressure-1.21.1-neoforge-ownership1-20260907` completed in 418 seconds,
exit zero, unchanged source. Its separate `.pressure-review.json` accepts 36
directly inspected pressure captures, four independently recomputed
eight-second PCM pairs at 0 ms (correlations 0.991758 / 0.994184 / 0.993775 /
0.992100), strict closed JOIN/reset/underrun checks on all four client logs,
absent private X processes/game port and no task audio modules. The service
is inactive with exit zero. Its network-only peer sent 3,240 requests in
40,048 ms, received 324 chunks / 5,238,432 bytes, acknowledged 162 windows,
and exercised 1,620 rate plus 1,458 window rejections with zero unexpected
errors. All five recovery samples have two remaining viewers and no
orphaned transfer resources, including a sample with a legitimate active
viewer grant/three queued items. This remains scoped development pressure
acceptance, not full control-image or final packaged certification.

`release-audit-r3-20260907` completed a fresh forced two-build attempt after
these corrections and both scoped pressure acceptances. Previous r1/r2 logs,
false receipts and bundle snapshots are preserved. The new r3 runtime helpers
are bound to the new attempt and require two successful unchanged-source
builds, all ten GameTests in each, and exact 18-file bundle hashes before
starting any of the 37 serial cases.

Both r3 builds succeeded (wrapper elapsed times 550 and 517 seconds): all
87 actionable tasks executed in each, all ten required GameTests passed
in each and all sixteen release artifacts were inspected. The reproducibility
receipt confirms unchanged 829 non-documentation source inputs and identical
hashes for all sixteen JARs, the manifest and SHA256SUMS. The serial 37-case
development-runtime batch started at 17:18 Arizona time; this is not
final-commit release certification.
Read-only credential preflights also
confirmed the four designated DiscPanel representatives stopped/autostart
off and both retained native guests ready/off, without deployment or boot.

The r3 legacy terminal case completed in 365 seconds with unchanged source
and candidate bundle. Its `release-audit-r3-terminal-1.7.10-forge-20260907.direct-review.json`
accepts all 28 listed captures: two baseline/pre-terminal world views,
twelve dimension-return views, two away-world views and twelve queue/replay
views. Every image was directly inspected; the follower's retained screenshot
was checked again after closure because a dimension reload replaced its
earlier live capture. The close camera crops TV edges/upper timecode but
retains identifiable moving program; no other avatar obscures the TV.
Five independently recomputed eight-second PCM pairs have signed offsets
20 / 50 / -20 / 30 / 50 ms, correlations 0.990549–0.999954. Both away-world
follower captures and all four EOS/stop drains independently measure -91 dB
maximum, while the unchanged leader continues at -23.1 dB mean during both
dimension absences. The two round trips retain JVM/session/generation identity.
All three closed client logs pass strict underrun/reset checks; their private
X processes, owned audio sinks and game port are absent.

The r3 modern terminal case passed automation in 244 seconds, with unchanged
source/bundle. All fourteen required images were directly reviewed. The two
baseline views contain vanilla unverified-chat toasts; all twelve later
queue/replay views clear these overlays and show advancing identifiable
program on both clients. Three independently recomputed eight-second PCM
pairs are at 0 ms, correlations 0.988640 / 0.999476 / 0.998199. Strict closed
JOIN/reset/underrun checks and private-X/owned-sink/port cleanup pass.

Its initial independent review did **not** pass the review helper's stricter
-60 dB peak check: the reconnected follower's EOS capture has -66.3 dB mean,
-42 dB peak, while the other three EOS/stop captures measure -91 dB maximum.
The failed review is retained separately. The anomalous capture's first
3.7 seconds are digitally silent; its final roughly 0.2-second transient
independently matches the installed Minecraft `ui/toast/out.ogg` waveform
(correlation 0.900653, verified asset SHA-1). The fresh follower is IDLE before
its first playback generation and has no earlier video frame/audio scheduling.
The explicit follow-up direct-review receipt accepts the transient as vanilla
toast dismissal, not retained Cinemarr playback, and does not call this capture
digitally silent. The maintained gate's -50 dB mean / -35 dB peak thresholds
are unchanged; no runtime rerun was used.

Both r3 pressure cases are accepted after ordinary controller review as well
as the pressure supplements. Legacy completed in 484 seconds. Its pressure
receipt covers 42 images, and its normal-controls receipt covers 39 images,
with six shared post-reload/reconnect images: **75 distinct images**, all
directly inspected. The four pressure PCM pairs are all at 10 ms (correlations
0.998169 / 0.996339 / 0.997977 / 0.997057); the final normal-control pair is
at 30 ms (0.989954). All are independently recomputed eight-second captures.
The third peer sent 3,970 requests in 40,042 ms, received 184 chunks /
2,974,912 bytes, acknowledged 92 windows, and exercised 2,330 rate plus
1,548 window rejections with zero unexpected errors. Five recovery samples
show two viewers and no orphaned grants/items/bytes. Four closed client
logs, exact owned audio sinks, private X processes and the game port passed
cleanup checks. Both clients completed two reloads, and all six final world
views are free of debug overlay and avatar obstruction.

Modern completed in 416 seconds: 36 pressure plus 33 ordinary control images,
**69 distinct images**, all directly inspected. Four pressure PCM pairs are
at -10 ms (correlations 0.999921 / 0.999900 / 0.999908 / 0.999917), and the
final control pair is at 10 ms (0.998558), independently recomputed over
eight seconds each. Its peer sent 3,740 requests in 40,000 ms, received 324
chunks / 5,238,432 bytes and acknowledged 162 windows, with 2,120 rate plus
1,458 window rejections and no unexpected errors. All five recovery samples
have no orphaned transfer resources, and all four closed client logs and
owned resources pass the same checks, including modern JOIN ordering.

Both cases directly demonstrate paused clocks/frozen pictures, seek and
audio changes while paused, resume/playing advancement, permission feedback
persistence and expiry, selection/caret retention across updates, draft
retention across Queue/Browse toggles and explicit text clearing. During
browse overload only the noisy follower has retry chat across the lower TV;
it clears in recovery. Modern initial/reconnect captures retain identifiable
video but have a vanilla toast across the upper-right picture, so they are
not described as wholly unobstructed; separate pressure world captures clear
the toast. Legacy immediate seek/stream-change controller captures show dark
TV tiles during transition; subsequent world/PCM captures prove restoration.
These limitations are recorded in the direct observations, not hidden by
the automated pass. The receipts are named
`release-audit-r3-pressure-{profile}-20260907.pressure-review.json` and
`.visual-review.json` under `build/clock-fix-gate`.

Both r3 fault cases now have separate fault and ordinary-control acceptance
receipts: `release-audit-r3-fault-{profile}-20260907.fault-review.json` and
`.visual-review.json`. Legacy completed in 407 seconds with **57 distinct
images** directly inspected (18 fault recovery, 39 ordinary/reload). Transient,
slow-delivery and exhausted retries generated 152 / 49 / 6 actual fault HTTP
requests in successive generations 12 / 13 / 14. Their independently recomputed
eight-second PCM pairs have signed offsets 20 / 50 / 20 ms, correlations
0.989368 / 0.999979 / 0.999813. The final ordinary-control pair is 50 ms,
correlation 0.998993. Both clients completed two resource reloads; all six
post-reload views show clear advancing program without debug overlays.

Modern completed in 342 seconds with **51 distinct images** directly inspected
(18 fault recovery, 33 ordinary controls). The same three fault types generated
143 / 46 / 6 actual fault HTTP requests in generations 12 / 13 / 14. Independent
eight-second PCM offsets are 10 / 10 / 0 ms, correlations 0.995296 / 0.989296 /
0.990741; the final ordinary-control pair is 0 ms, correlation 0.986653.
Both cases pass strict checks on all three closed client logs,
private-display teardown and exact owned audio-sink removal; the fault reviews
also checked the game port absent. Source inputs and candidate bundle stayed
unchanged.

All 18 post-fault world views on each platform show identifiable advancing
program without chat, menus, debug overlay or avatar obstruction. The modern
baseline pair has vanilla unverified-chat toasts in the upper-right area and
the leader's build notice below; those initial views are not unobstructed.
Ordinary controls demonstrate stable pause clocks/pictures, paused seek and
audio changes, resume, playing seek, readable permission feedback persistence
and expiry, retained text selection and Queue/Browse drafts, and explicit
clearing. Immediate seek/stream-switch controller captures can show dark TV
tiles; subsequent world and PCM captures establish recovery. The final modern
controllers show Commentary at 5:19 / 5:20, and the legacy pair at 6:29 / 6:30.

The main r3 `matrix/1.7.10-forge` case is also accepted: 336 seconds,
39 directly inspected images, unchanged source/bundle and independently
recomputed eight-second final PCM at 20 ms (correlation 0.996665). Its
`.visual-review.json` records all eleven timeline, ten feedback, seven editing,
two baseline-world, three controller and six post-reload/reconnect captures.
Playing advances from 1:10 to 1:13; paused clock/picture stay at 1:13, paused
seek and Commentary retain pause at 1:43, then resume reaches 1:46. Both final
controllers show Commentary at 4:30 / 4:31. All six final world views show
advancing program without debug/chat/avatar obstruction; close camera framing
still crops the upper counter and edges. Three closed logs pass strict
underrun/reset checks, private X processes are gone and owned sinks absent.

The main r3 `matrix/1.20.1-fabric` case is accepted after all 33 captures
were directly reviewed: 249 seconds, unchanged source/bundle and independently
recomputed eight-second PCM at 10 ms (correlation 0.997684). Owner playback
advances 0:34–0:37; pause retains clock 0:37 and world frame 187, seek/audio
change retain pause at 1:07, and resume reaches 1:10. The required feedback
and text-editing transitions pass. Initial/reconnect world captures have
vanilla upper-right toasts but retain identifiable program; the separate
paused-world pair has no toast. The leader controller is English at 1:49,
whereas the later follower/non-owner captures show Commentary at 2:40; these
are different capture times, not simultaneous conflicting stream state.
Strict checks pass on all three closed JOIN/reset/underrun logs, with private
X processes gone and exact owned audio sinks absent.

The main r3 Quilt and Forge 1.20.1 cases also have complete `.visual-review.json`
receipts, with all 33 captures directly inspected for each. Quilt completed
in 282 seconds with independently recomputed eight-second PCM at 0 ms
(correlation 0.997004); Forge completed in 266 seconds at 20 ms (0.999254).
Pause retains frame 190 / clock 0:38 on Quilt and frame 189 / clock 0:37 on
Forge. Paused seek/audio changes, resume, feedback persistence/expiry and
text-selection/draft retention pass. Both baseline/reconnect pairs have
vanilla upper-right toasts but retain identifiable program; owner paused-world
pairs are clear. Leader controllers show English at 1:49, whereas later
follower/non-owner captures show Commentary at 2:42. Immediate stream-change
frames can show dark tiles before later controller/editing frames restore
program. All three closed logs per case pass strict JOIN/reset/underrun checks;
private X processes and exact owned sinks are absent. Source/bundle unchanged.

The main r3 NeoForge 1.20.1 case is accepted: 280 seconds, all 33 required
captures directly reviewed and independently recomputed eight-second PCM at
10 ms (correlation 0.995135). Pause holds clock 0:38 and frame 191; paused
seek/audio changes retain pause at 1:08 and resume reaches 1:11. Feedback,
selection/draft retention and explicit clearing pass. Its baseline/reconnect
world views retain identifiable program beneath upper-right vanilla toasts;
the owner paused-world pair has no toast. The leader English controller at
1:49 precedes follower/non-owner Commentary captures at 2:42. The immediate
stream-change dark tiles clear in later controls/editing. Three closed logs
pass strict JOIN/reset/underrun checks; private X processes and owned sinks
are absent, with unchanged source/bundle. Every maintained 1.20.1 loader now
has its main r3 development-case review; Quilt Mod Menu remains a separate
required supplement.

The main r3 Fabric and Quilt 1.20.2 cases are accepted, each with all 33 required
captures directly reviewed. Fabric completed in 247 seconds; Quilt in 292.
Independently recomputed eight-second PCM offsets are both 10 ms, correlations
0.995158 / 0.999344. Fabric pause retains clock 0:37 and frame 189; Quilt
retains 1:01 and frame 306. Paused seek/audio changes, resume, feedback
persistence/expiry, text-selection retention, Queue/Browse drafts and clearing
pass on both. Their controllers heavily dim the background, so separate
world captures supply the video-visibility evidence. Baseline/reconnect worlds
retain identifiable program with vanilla upper-right toasts; owner paused
world pairs have no toast or avatar obstruction. Fabric leader English at
1:49 precedes follower/non-owner Commentary at 2:40; Quilt leader English at
2:12 precedes Commentary at 3:05. Immediate seek/stream transitions and the
UI dimming are recorded explicitly in each receipt. Three closed logs per
case pass strict JOIN/reset/underrun checks; private X processes and exact
owned audio sinks are absent, with unchanged source/bundle.

The main r3 Forge and NeoForge 1.20.2 cases are accepted (254 / 263 seconds),
each with all 33 required images directly inspected. Independently recomputed
eight-second PCM offsets are 10 / 0 ms, correlations 0.987384 / 0.987230.
Both preserve paused clocks and world frames, resume and seek correctly,
retain selection/drafts through state updates, and display readable permission
and server feedback with expiry. The immediate English stream-change captures
show temporary dark tiles; later editing captures restore the program under
strong UI dimming. World captures identify the acceptance program on both
clients, with vanilla chat-security toasts overlapping the upper right and
leader build notices below; the paused-world pairs are unobscured. Sequential
leader English and later follower Commentary captures are not simultaneous
state comparisons. All three closed client logs per case passed strict
JOIN/reset/underrun checks, their private X processes and owned audio sinks
were absent, and the frozen source/bundle checks passed. Separate
`release-audit-r3-matrix-1.20.2-forge-20260907.visual-review.json` and
`release-audit-r3-matrix-1.20.2-neoforge-20260907.visual-review.json` bind these
observations to image, log, PCM and candidate hashes.

The main r3 Fabric and Quilt 1.21.1 cases are accepted (256 / 279 seconds),
with all 33 required images directly inspected for each. Independently
recomputed eight-second PCM offsets are both 10 ms, correlations 0.992135 /
0.996574. Both paused clocks and program counters remain fixed; resume,
paused seek/audio selection, permission/server feedback expiry and selected
text/draft retention are visible. Immediate playing seek and English-change
captures show dark TV tiles, while the next editing captures restore the
colored program. Controllers fit at 640x480 with readable content over the
blurred world. Baseline/reconnected world views identify the program, with
the vanilla upper-right chat-security toast noted; paused-world pairs are
clear. Each case passed all three strict closed JOIN/reset/underrun log checks,
independent PCM comparison, absent private X/owned audio-resource checks and
unchanged source/bundle checks. The per-profile
`release-audit-r3-matrix-1.21.1-{fabric,quilt}-20260907.visual-review.json`
receipts bind the observations and exact evidence hashes.

The main r3 Forge 1.21.1 case is accepted (260 seconds), with all 33 images
directly inspected and an independently recomputed eight-second PCM offset
of 10 ms, correlation 0.999669. Paused clock/world counter 195 remain fixed;
paused seek/audio selection, resume, feedback expiry, selection replacement
and draft preservation are visible. Immediate playing seek/English-change
captures show dark tiles, and the next editing capture restores the colored
program. All small controllers fit; baseline/reconnected worlds identify the
program with vanilla chat-security toasts noted, and paused worlds are clear.
All three closed logs passed strict JOIN/reset/underrun checks, private X and
owned audio resources were absent, and source/bundle remained unchanged.
`release-audit-r3-matrix-1.21.1-forge-20260907.visual-review.json` records the
direct observations and exact image/log/PCM/candidate hashes.

The main r3 NeoForge 1.21.1 case is accepted (255 seconds), with all 33 images
directly inspected and an independently recomputed eight-second PCM offset
of 10 ms, correlation 0.996364. Paused clock 0:55/world counter 277 remain
fixed; paused seek/audio selection, resume, feedback expiry, selection and
draft preservation are visible. Playing seek retains the program; immediate
English change at 2:02 shows dark tiles, and the next editing capture at 2:02
restores the colored program. Both world views identify the program, with
vanilla upper-right toasts noted; paused worlds are clear. All three closed
logs passed strict JOIN/reset/underrun checks, private X/owned audio resources
were absent and source/bundle remained unchanged. The per-profile
`release-audit-r3-matrix-1.21.1-neoforge-20260907.visual-review.json` binds
these observations to exact evidence hashes.

The main r3 Fabric and Quilt 26.1.2 cases are accepted (262 / 306 seconds),
with all 33 images directly inspected for each. Independently recomputed
eight-second PCM offsets are 0 / 10 ms, correlations 0.991020 / 0.994557.
Both paused clocks/world counters remain fixed (Fabric 0:37/189, Quilt
1:00/303); paused seek/audio selection, resume, feedback expiry and
selection/draft retention are visible. Immediate playing seek and English
change show dark TV tiles. Fabric still shows tiles in its 1:44 selection
capture, then the 1:45 replacement capture restores the program; Quilt's
2:08 selection capture already restores it. All small controllers fit with
readable labels. Both clients show identifiable world video; Fabric's two
world captures and Quilt's follower have vanilla upper-right toasts, while
Quilt's leader and both paused-world pairs are clear. All three closed logs
per case passed strict JOIN/reset/underrun checks, private X/owned audio
resources were absent, and source/bundle remained unchanged. Per-profile
`release-audit-r3-matrix-26.1.2-{fabric,quilt}-20260907.visual-review.json`
receipts contain direct observations and exact image/log/PCM/candidate hashes.

The main r3 Forge and NeoForge 26.1.2 cases are accepted (257 / 252 seconds),
with all 33 images directly inspected for each. Independently recomputed
eight-second PCM offsets are both 10 ms, correlations 0.993940 / 0.998088.
Paused clocks remain at 0:38 and world counters remain fixed at 194 / 192.
Paused seek/audio selection, resume, feedback expiry and selected text/draft
preservation are visible. Both immediate playing-seek views show dark tiles;
Forge's English-change capture at 1:46 already shows the program, while
NeoForge's English-change view at 1:45 has tiles and its next editing capture
at 1:45 restores the program. Small controllers fit; both world views per
case identify the program, with vanilla upper-right toasts noted and clear
paused-world pairs. All three closed logs per case passed strict JOIN/reset/
underrun checks, private X/owned audio resources were absent and source/bundle
remained unchanged. Per-profile
`release-audit-r3-matrix-26.1.2-{forge,neoforge}-20260907.visual-review.json`
receipts bind direct observations to exact evidence hashes.

The main r3 Fabric 26.2 case is accepted (260 seconds), with all 33 images
directly inspected and independently recomputed eight-second PCM offset
20 ms, correlation 0.998888. Paused clock 0:41/world counter 207 remain
fixed. Paused seek/audio selection, resume, feedback expiry, selection
replacement and draft retention are visible. Immediate playing seek and
English change show dark tiles, with the next editing capture at 1:48
restoring the program. All small controllers fit and both world views identify
the program, with vanilla upper-right toasts noted; paused worlds are clear.
All three closed logs passed strict JOIN/reset/underrun checks, private X/
owned audio resources were absent and source/bundle remained unchanged.
`release-audit-r3-matrix-26.2-fabric-20260907.visual-review.json` records the
direct observations and exact image/log/PCM/candidate hashes.

The final three main r3 profiles, Quilt / Forge / NeoForge 26.2, are accepted
(297 / 249 / 253 seconds), with all 33 images directly inspected for each.
Independently recomputed eight-second PCM offsets are 0 / 10 / 20 ms and
correlations 0.999381 / 0.999530 / 0.988310. All paused clocks and world
counters remain fixed (Quilt 1:02/312, Forge 0:40/204, NeoForge 0:38/192).
Paused seek/audio selection, resume, feedback expiry, selected-text replacement
and draft preservation are visible. Immediate playing seek and English change
show dark tiles. Quilt and Forge restore the program in their next editing
captures; NeoForge's selection-after still shows tiles, then replacement at
1:45 restores the program. Small controllers fit on all three profiles.
Every world view identifies the program: Quilt's leader and all paused-world
pairs are clear, while the remaining baseline/reconnected views have vanilla
upper-right chat-security toasts. All three closed client logs per case passed
strict JOIN/reset/underrun checks, private X/owned audio resources were absent
and source/bundle remained unchanged. Per-profile
`release-audit-r3-matrix-26.2-{quilt,forge,neoforge}-20260907.visual-review.json`
receipts bind direct observations to exact evidence hashes.

The r3 Quilt 1.20.1 Mod Menu supplement is accepted (281 seconds). All 33
images were directly inspected; independently recomputed eight-second PCM
offset is 0 ms, correlation 0.995856. Paused clock 0:36/world counter 184
remain fixed; paused seek/Commentary selection stays at 1:06 and resume
advances to 1:10. Immediate playing seek shows dark tiles at 1:40, with
English selection at 1:43 already restoring the program. Permission feedback
and expiry, selected-text replacement and drafts survive updates and fit the
small controller. Baseline/reconnected world views identify the program but
have vanilla chat-security toasts; paused-world captures are clear. Three
closed client logs pass strict JOIN/reset/underrun checks and prove Mod Menu
7.2.2 loaded and registered resources. Its actual remapped JAR identity and
SHA-256 are recorded, as is the requested `AL_SOFT_source_latency` extension
filter in each client. This proves coexistence during Cinemarr playback, not
interaction with Mod Menu's own configuration screen. Private X and owned
audio sinks are absent. Receipt:
`release-audit-r3-quilt-modmenu-1.20.1-quilt-20260907.visual-review.json`.

All five Fabric minimum-loader cases have independently reviewed startup
acceptance on actual loader 0.19.2: Minecraft 1.20.1 / 1.20.2 / 1.21.1 /
26.1.2 / 26.2 (45 / 50 / 32 / 38 / 50 seconds). Both invalid-config and
valid launches use the exact loader. Invalid config identifies the rejected
key without printing the injected credential; valid servers reach readiness,
reject missing-mod clients, save players/worlds and close their game ports.
The first three reject explicitly with Cinemarr protocol 10. The 26.x probes
close with Fabric/API requirements and the Cinemarr registry namespace,
corroborated by server logs; they do not claim the protocol-10 rejection.
Retained warnings include offline test-server notices, duplicate-disconnect
warnings and 26.x development remapping/native-access warnings. These are
startup-only cases, not client GUI/audio/video certification. Per-profile
`release-audit-r3-minimum-loader-<profile>-20260907.startup-review.json`
receipts bind closed logs, probes and candidate hashes.

The remaining Mod Menu supplements are accepted: Quilt 1.20.2 / 1.21.1 /
26.1.2 / 26.2, with actual Mod Menu versions 8.0.1 / 11.0.4 / 18.0.0 /
20.0.1 confirmed in all three closed client logs per case. Durations are
292 / 284 / 300 / 275 seconds. Each has 33 directly inspected images and
independently recomputed eight-second PCM: offsets 10 / 20 / 0 / 10 ms,
correlations 0.995210 / 0.994258 / 0.995168 / 0.999879. Paused clocks and
world counters hold (1.20.2 1:02/315, 1.21.1 0:38/190, 26.1.2 1:03/315,
26.2 0:39/196). Paused seek/audio selection, resume, readable permission
feedback/expiry, selected-text replacement and draft retention are visible.
Immediate playing seek and English changes show dark TV tiles, with the
next editing captures restoring the program. All small controllers fit.
Every world capture identifies the program; paused pairs and the 26.1.2
leader are unobscured, while the remaining baseline/reconnected views have
vanilla chat-security toasts. Every client log passed strict JOIN/reset/
underrun checks; private X and owned audio sinks are absent. Per-profile
`release-audit-r3-quilt-modmenu-<profile>-20260907.visual-review.json`
receipts bind images, PCM, closed logs, plugin identities and candidate hashes.
An initial 26.1.2 review-helper assertion assumed repository-local remapped
plugin storage; it was corrected to allow the specific Mod Menu dependency
cache directory. No game runtime was restarted or failed evidence replaced.

The entire serial automated batch passed all 37 cases in 9,769 seconds and
its service exited. All 21 main profiles, six terminal/pressure/fault cases,
five Mod Menu supplements and five minimum-loader startup cases now have
scoped acceptance: all 37, including all 165 Mod Menu images directly reviewed.
An independent manifest-derived reconciliation matched all 37 expected cases
to their automated results and 41 scoped review receipts, rechecked 1,415
evidence hashes and found 1,158 distinct directly reviewed PNGs across the
whole batch. The unit is inactive/dead with main PID 0 and exit status 0.
The frozen source and bundle remain unchanged. This does not certify the
final commit, production clients, retained native guests or remote CI.

## r3 external candidate deployment and packaged preflight

Source review after the packaged checks found a manifest-validation
gap: an in-memory mutation of `1.20.1-fabric.task` from `verifyFabric1201` to
the existing `verifyForge1201` passed both `validate()` and
`verify_repository()`. No maintained source was changed by this reproduction.
The current manifest selects the correct task, so this does not invalidate
the recorded r3 artifact identity, but the promised wrong-target validation
was incomplete. After the native batch and stopped-state audit finished,
task/project binding and manifest-driven family/isolated-build wiring were
corrected. Sixteen manifest tests now pass, including three new regression
methods covering wrong projects, Quilt overrides and the root task. Source
layout, release hygiene and the full `releaseMatrixGate` dependency dry run
pass too. Only `build.gradle`, `scripts/target-matrix.py` and
`scripts/test-target-matrix.py` changed among the 829 frozen code inputs.
These checks were followed by the successful r4 frozen build pair and explicit
old/new bundle parity recorded above. The r3 runtime and native reviews remain
attributed to their original source and tested bytes.

`build/clock-fix-gate/release-audit-r3-20260907-deployment.result.json`
records successful deployment in 160 seconds. All four canonical server JARs
were downloaded/hash-checked against the frozen bundle, and a separately
hash-verified disabled rollback was retained for each. The deployment unit
exited with status zero. All four designated servers returned stopped with
autostart off and Cinemarr disabled; unrelated mod settings and Docker
overrides were preserved. An independent check matched all four deployment
log candidate hashes and the receipt's complete bundle map to the r3 build.

All four packaged launch prerequisites passed with the retained
`build/packaged-client-runtime-20260907` inputs. Initial read-only checks
identified missing shell runtime-root/Java-25 settings; the task runner now
selects the existing verified inputs explicitly, without reinstalling loaders
or falling back to development clients. Fresh metadata for the real-Plex
fixture confirms one audio track and an available subtitle. The real-Plex
phase uses explicit subtitle switching for the owner timeline probe.

The four serial packaged cases completed successfully in 915 seconds, and
their service is inactive/dead with PID 0 and exit status 0. All four now have
scoped direct acceptance: 39 images per case (156 reviewed images), including
six unobstructed post-reconnect film views each. Older per-profile evidence
remains preserved by recoverable directory renames. No native guest was
booted during this deployment or packaged playback work.

| Production profile | Automated seconds | Direct images | Recomputed PCM lag | Correlation |
| --- | ---: | ---: | ---: | ---: |
| Forge 1.7.10 | 248 | 39 | 20 ms | 0.976844 |
| Quilt 1.20.1 | 230 | 39 | 10 ms | 0.980151 |
| NeoForge 1.21.1 | 223 | 39 | 0 ms | 0.974794 |
| Fabric 26.2 | 204 | 39 | -10 ms | 0.962196 |

Every PCM pair contains eight seconds per client and was independently
recomputed with the real-program broadband spectral comparator, not the
synthetic-tone comparator. All twelve closed client logs passed underrun,
render-thread and media-reset checks; modern clients also passed explicit
join/reset ordering. All twelve indexed production launch records bind the
actual Cinemarr and server-matching Jammarr JARs, without development
classpaths. Original private X processes and owned audio modules/sinks were
absent after the batch; remote diagnostics returned exactly to baseline.
The legacy case additionally records two audio-resource reload cycles on
each client before the reviewed reconnect captures and PCM.

Immediate button-click frames are not always completed-action frames:
legacy screenshots can retain the previous clock/status briefly, and early
seek/stream-switch frames can show the underlying screen tiles. Later
captures and authoritative timeline records demonstrate the requested state
changes and restored film output. Initial modern world captures contain the
vanilla unsigned-chat toast; the six later world captures per profile provide
unobstructed views. These distinctions are explicit in each direct review.

Quilt's paused-world image was initially visually ambiguous. A read-only
source-film extraction at 36.95 seconds confirmed the same dark aerial
neighborhood scene, while both paused captures and the retained decoded-frame
hash stayed unchanged. The reference consumed 7,521,400 source bytes, created
no Plex transcode/session, and closed its loopback relay. Its scoped evidence
is `build/clock-fix-gate/r3-plex-pause-reference/result.json`; it supplements,
not replaces, the in-game evidence.

The four per-profile `real-plex-<profile>-release-audit-r3-20260907.visual-review.json`
receipts and `build/clock-fix-gate/release-audit-r3-real-plex-closed-20260907.json`
bind the reviews, PCM, launch records and closed resources. During task-local
review-helper adaptation, synthetic-tone/PipeWire assumptions were corrected
before acceptance. A legacy receipt with a shadowed image-count field was
preserved as `.visual-review.invalid-image-count.json` and replaced by a
validated 39-image receipt; the invalid receipt is not acceptance evidence.

The four serial server-recovery cases then passed in 438 seconds (legacy 90,
Quilt 139, NeoForge 103, Fabric 26.2 95). Exact-candidate evidence records
disabled-without-credential behavior, controlled HTTP-503 degradation,
operator retry, bounded automatic retry, ready diagnostics, redacted server
logs, and exact configuration/override restoration. Their service is also
inactive/dead with PID 0 and exit status 0. The fresh authenticated
`build/clock-fix-gate/discopanel-r3-post-recovery-20260907.json` audit found all
four servers stopped/autostart-off with Cinemarr disabled. The two serial
restart-mid-build lifecycle cases subsequently passed in 163 seconds (legacy
82, NeoForge 75), and their unit is inactive/dead with PID 0 and exit status 0.
The independent `build/clock-fix-gate/release-audit-r3-recovery-lifecycle-closed-20260907.json`
audit verified all six recovery/lifecycle cases, exact candidate and Jammarr
launch provenance, partial-build checkpoint/recovery completion, the first
placed pixel returning to air, and absent original private X/audio resources.
Legacy warmup/recovery clients used the explicitly configured 640x480 private
displays; lifecycle builders used 1280x720. The fresh authenticated
`build/clock-fix-gate/discopanel-r3-post-lifecycle-20260907.json` confirms all four
servers stopped/autostart-off with Cinemarr disabled after the complete batch.
Retained Windows/ARM native reuse subsequently passed serially, without
provisioning: Windows `20260908T035508Z` (252 seconds), ARM
`20260908T035922Z` (287 seconds). Both passed all three decoder resolutions.
`build/clock-fix-gate/release-audit-r3-native-checks-20260907.json` records
automated success and the passed stopped-state audit. The independent
`build/native-smoke/20260907-release-audit-r3-native-parity.json` matches
all 90 tested Windows/ARM native entries, decoder classes and the full shared
core payload across all sixteen candidate JARs. Both retained guests are
installed, ready and off in the post-run audit. ARM is QEMU-emulated ABI and
functional coverage, not physical-ARM performance or full client acceptance.
Final source/security/doc review,
final-commit local gate, exact-SHA GitHub CI and artifact parity remain separate
release requirements.

## 2026-09-07 legacy ingress budgets and duplicate hello ownership

Source audit found that legacy server/client incoming packets used unbounded
queues before tick-thread dispatch, so later rate limiting could not bound
the handoff backlog. Legacy hello processing also lacked the modern adapters'
pending-connection/duplicate guard. This was a source-level finding, not a
claim of a live flood already tested against the old code.

The running `release-audit-r2-20260907` build was intentionally stopped before
these source changes: 371 seconds, signal 15, child exit 143, unchanged source,
no second build and no successful reproducibility receipt. The service is
terminal with an empty cgroup. Its logs, manifest and previous bundle are
preserved. The prepared r2 runtime batch has not run and will reject this
unsuccessful build binding.

`BoundedPacketInbox` now supplies a synchronized byte/item budget and
round-robin handoff. Legacy server limits are 512 packets / 8 MiB globally and
64 packets / 256 KiB per connection. Client limits are 1,024 packets / 16 MiB.
These byte budgets count encoded payload size; decoded objects also have
codec bounds and a fixed item cap, not an exact heap-byte measurement. Both
tick drains stop at 128 packets. Overflow explicitly closes the offending
connection, and disconnect removes its retained queue state. Client dispatch
requires the current connection identity. Server hello negotiation now uses
the shared `HelloGate`, keyed by `NetworkManager`, with the existing bounded
30-second timeout; unsolicited, duplicate and obsolete-connection hellos do
not republish session state.

Six core inbox tests cover peer fairness, global/per-peer item and byte limits,
numeric overflow, removal/clear accounting, reentrant callbacks, exceptions,
an eight-thread 8,000-offer flood and 1,000 connection removals. A separate
hello test covers connection replacement and duplicate suppression. The full
legacy `verifyRelease` passed in 24 seconds with 229 tests, zero failures or
errors, two opt-in skips and reobfuscated artifact verification
(`legacy-inbox-verify-release-20260907.log`). Source-layout verification now
checks the actual legacy adapter's bounded-drain and hello wiring. Live
two-client/pressure/reload acceptance and final candidate verification remain
required; unit success does not establish those outcomes.

## 2026-09-07 gate coverage, modern post-fault acceptance and egress correction

The local `releaseMatrixGate` previously required only the representative
NeoForge release checks plus terminal/pressure supplements. It now requires
all 16 build/inspection tasks, all ten GameTests, all 21 runtimes, both
terminal/pressure/network-fault representatives, five Quilt Mod Menu cases and
five minimum-Fabric-Loader launches. The retained
`full-release-wiring-dry-run-20260907.log` proves dependency ordering only,
not execution. Nine new shell regressions cover each post-fault physical
capture/audio failure, evidence preservation, generation continuity, redaction
failure without secret output, and the required local/CI wiring. Existing
terminal (10), segment-pressure (9) and browse-pressure (6) regressions pass.

`fault-1.21.1-neoforge-physical1-20260907.result.json` records a 327-second
source-unchanged complete run with exit zero. Its separate `.fault-review.json`
accepts all eighteen directly reviewed post-fault images and three independent
eight-second PCM pairs: transient 10 ms / 0.988893 correlation, slow 0 ms /
0.992062, exhausted recovery 10 ms / 0.989996. The corresponding generations
are 12, 13 and 14, with 142/48/6 actual HTTP requests under each fault mode.
All three closed client logs pass underrun/JOIN/reset checks, and private
displays, game port, task audio modules and service cgroup are gone. This is
post-fault development-scope acceptance, not all normal-control image review
or final packaged certification. Legacy must also run. This receipt predates
the egress correction below and cannot certify its new bytes.

A requirement-5 source review reproduced a separate egress edge-case failure:
with at least two queued clients, clearing the scheduler from a send callback
leaves the current drain cycle using its old queue count. The next iteration
throws `NoSuchElementException` despite the queue being intentionally empty.
`egress-reentrant-clear-before-20260907.log` retains the standalone actual-class
failure. The existing reentrant-disconnect test has only one queued client and
does not cover this case. After the frozen test finished, two added multi-client
clear/disconnect regressions failed against the original implementation
(`egress-reentrant-clear-red-tests-20260907.log`). The corrected loop checks
that the active ring still contains an entry before each removal. A third
regression covers clear plus same-key replacement. The standalone reproducer
now passes; all eleven scheduler tests and the complete 178-test core / 39-test
modern suites pass (one/two opt-in skips) in the retained seven-second
`egress-reentrant-clear-green-tests-20260907.log`. The ordinary active-underrun
and fault-recovery regressions remain strict and green. Final affected runtime
and packaged-byte certification remain required after this correction.
The legacy `verifyRelease` then passed in 20 seconds, including all 222 tests
(two opt-in skips, zero failures/errors) and reobfuscated artifact verification.
The corrected-source legacy post-fault run completed in 405 seconds with
unchanged source and exit zero. Its `fault-1.7.10-forge-physical1-20260907`
`.fault-review.json` accepts eighteen directly reviewed post-fault images and
three independently recomputed eight-second PCM pairs, all at 10 ms absolute
separation: transient correlation 0.999406, slow 0.999493, exhausted recovery
0.996710. Generations 12/13/14 have 153/48/6 actual HTTP requests under the
respective fault modes. Closed client logs, private X processes, game port,
task audio modules and transient service cgroup passed cleanup checks. This
scope excludes direct review of additional normal-control/post-reload images
and does not certify final packaged bytes.

The forced `release-audit-r1-20260907` build pair failed its first build after
531 seconds, 70 executed tasks and all ten required GameTests. Seventeen
assertions in `verifyLogMarkerPolling` failed because three synthetic healthy
log fixtures predated the real-audio-timeline requirement. The unchanged
825-input source manifest and false build/reproducibility receipt are retained;
no second build started. Corrected fixtures and added negative coverage now
require missing timelines and active underruns to fail in each closed client
log, including the pre-reconnect follower. The runtime checker is unchanged.
The GameTest completion check now accepts exactly ten passing tests, not any
passing subset; counts 0/1/9/11/100 fail its new regression.

Phase-3 mutation review separately proved that the old manifest verifier
accepted unrelated Quilt versions, incorrect primary runtime names, legacy
Java 25 settings, nonexistent launch tasks and stale protocol 9. The actual
manifest was not mutated on disk. `target-manifest-red-mutations-20260907.json`
and `target-manifest-red-tests-20260907.log` preserve the failing probes/tests.
The corrected checker compares runtime identities, actual Java build
declarations, launch-task declarations and the real protocol constant.
Compatibility documentation rows are now generated internally from the
manifest rather than validated against a duplicate version table. All thirteen
manifest tests pass and are required by the local manifest gate as well as CI.

The complete twenty-task tooling preflight passed in twelve seconds:
`release-audit-preflight-20260907.log`. This includes nine log/health/GameTest
regressions, thirteen manifest tests, all observer/input tests, private-display
allocation/teardown tests, source layout and tracked release hygiene. Separate
canonical-JAR and evidence-redaction tests also pass. Full forced builds,
full-diff review and subsequent candidate acceptance remain open.

## 2026-09-07 third-peer pressure: both representatives accepted within pressure scope

`pressure-1.7.10-forge-segment3-20260907.result.json` records a complete
481-second source-unchanged run, exit zero and no interruption. Its separate
`.pressure-review.json` accepts 42 directly reviewed images and independently
recomputed eight-second PCM pairs: browse during/recovered correlations
0.997139/0.997351 and segment during/recovered 0.998379/0.998501, all at 20 ms
absolute separation. There were 253 real browse requests and 40 media HTTP
requests during the browse hold. The third peer sent 3,290 requests, received
3,589,296 bytes, acknowledged 111 windows, and observed 1,670 rate and 1,509
window rejections with zero unexpected errors. Fresh recovery samples have
two tracking clients, two entries in each rate map and zero transfers/egress.
Four closed client logs, private displays, game listener and audio sinks passed
their cleanup checks. Two resource/sound reloads per client completed; all six
post-reload/reconnect views are clear of the F3 debug overlay. A sheep partly
occludes the lower edge in some earlier browse/pre-segment views, without hiding
the identifying advancing program; this is recorded in the visual receipt.
This is development pressure-scope acceptance, not final packaged certification
or direct review of every additional normal-control image.

`pressure-1.21.1-neoforge-segment3-20260907.result.json` records a complete
413-second development run with unchanged source, exit zero and no interruption.
The separate `.pressure-review.json` accepts both pressure phases after direct
review of 36 images, independent recomputation of four eight-second PCM pairs,
and closed-log/process/display/port/audio checks. This is pressure-scope
acceptance, not final artifact certification or a full review of the additional
normal-control images. All four PCM comparisons have 10 ms separation;
browse during/recovered correlations are 0.991501/0.993668 and segment
during/recovered correlations are 0.994367/0.993454. The follower issued 257
real browse requests. The third peer sent 3,740 segment requests, received
5,173,760 bytes, acknowledged 160 windows, and observed 2,130 rate and 1,450
window rejections with zero unexpected errors. Recovery leaves two tracking
clients and two entries in each rate-limit map, with an observed empty transfer
queue/grant registry. The network-only peer's separate PCM is silent. Four
closed media-client logs, including the pre-reconnect follower and peer, pass
their role-appropriate checks; no active underrun in an ordinary viewer is
waived. The bounded transient service is terminal, its cgroup is gone, and the
game port and task audio sinks are absent. Both `segment3` runs include the
rate-limit cleanup correction; earlier failures below remain failures.

The new supplementary gate uses a real network-only third Minecraft peer on a
separate private display/audio sink. Two ordinary viewers retain strict active
underrun, physical PCM and visible-frame requirements. Server diagnostics now
include transfer-grant counts and rate-limit subject counts. The same pressure
supplement runs on legacy and NeoForge locally and in CI; adding it does not
certify the final candidate.

`pressure-1.7.10-forge-segment1-20260907.result.json` is a source-unchanged
255-second failure. Its recovery observer mixed a delayed pre-departure legacy
diagnostic into post-departure sampling. The corrected boundary has a regression;
the original run remains failed, despite passing during-load A/V.

`pressure-1.7.10-forge-segment2-20260907.interrupted.json` records the next
source-unchanged attempt's SIGTERM interruption (wrapper exit 143, sender not
established), not a successful final result. Both pressure/recovery phases
completed: the peer sent 3,140 requests, received 3,653,968 bytes and acknowledged
113 windows, with 1,520 rate and 1,507 outstanding-window rejections and zero
unexpected errors. Eight-second during/recovered PCM passed at 30/40 ms and
0.990452/0.987218 correlation. All 36 pressure images were directly reviewed;
both viewers show advancing identifiable synthetic content. Browse overload
shows actionable retry chat on the noisy follower; segment-pressure views are
clear. Remaining normal controls, legacy reload/reconnect and normal full-gate
teardown did not finish. Manual cleanup is independently recorded, including
restored world/properties and absent task processes, displays, port and sinks.
The fake service had already died, so manual server shutdown logged a
transcode-close connection-refused warning. Do not promote this interrupted
run or silently retry it as though it passed.

Subsequent review found a product resource-lifetime defect: all three managers
retained departed-player rate-limit entries. Disconnect and manager close now
release those entries, with two 1,000-identity core regressions and updated
live diagnostic bounds. Fresh complete affected-runtime and final-byte gates
remain required after this correction.

## 2026-09-07 browse overload: reproduced starvation and isolated legacy acceptance

`pressure-1.7.10-forge-shared1-20260907.result.json` records a failed,
source-unchanged 150-second development run, not acceptance. The real non-owner
sent 260 browse requests over 40 seconds while fake Plex held only browse HTTP
responses; media requests were not deliberately delayed. Server diagnostics
show 64 queued tasks, two occupied workers and 204 rejected tasks. Both clients
logged an active underrun; the during-load PCM comparison failed, and decoded
frames stopped advancing. The failure is retained under the matching
`build/pressure-...` directory. Its private X processes, game listener and task
audio modules were absent after teardown.

The product now isolates browse work (one worker, 16 queued) from playback work
(two workers, 64 queued) through a shared Java-8-compatible implementation used
by legacy, modern and 26.x managers. Unit tests exercise real saturated workers,
continued playback capacity, cancellation of both backlogs and rejection after
close. Typed overload/shutdown messages remain actionable through nested causes.
Current suites pass 210 legacy / 166 core / 39 modern tests, with two/one/two
opt-in skips. Six pressure-observer contracts require genuine sustained queue
saturation, independent bounds and correct routing; none claims physical A/V.
The corrected legacy run (`pressure-1.7.10-forge-isolated1-20260907`) completed
in 388 seconds with unchanged source inputs. It sent 220 actual browse requests,
kept the separate 16-slot browse backlog saturated, and rejected browse work
without rejecting playback work. Forty media HTTP requests were served during
the browse-only hold. During/after eight-second PCM comparisons passed at
50 ms with correlations 0.996041/0.995587. Both clients retained advancing frames
and zero active underruns; the new browse result superseded old work. Closed
logs, task displays, game listener and audio modules pass the cleanup audit.

All 57 images were directly reviewed, with eighteen accepted before/during/after
pressure views. The noisy viewer sees repeated actionable retry chat during
the load; the owner remains unobstructed and recovery views are clear. The six
later reload world images and two final controller views retained F3 debug text,
so **full visual acceptance remains false** in the adjacent `.review.json`.
The opt-in legacy capture preparation now explicitly clears debug display state
instead of guessing its state with another key toggle. Final live verification
of that capture correction remains required. Missing immediate underrun logging
in both 26.x camera shims was also caught by the source-layout gate, corrected,
and rechecked successfully.

The source-frozen NeoForge 1.21.1 pressure representative completed in 323
seconds with 264 real browse requests and forty media HTTP requests during the
hold. Its separate browse queue stayed saturated while playback reported zero
rejections and both clients retained zero active underruns. During/after PCM
measures 0 ms separation, with correlations 0.997288/0.997314. Fresh browsing
recovers without older results replacing it. All 51 images were directly
reviewed, including readable permission errors, preserved drafts, a fixed paused
world picture, advancing resumed playback and both viewers' pressure/recovery
views. Automatic initial/rejoined screenshots include the vanilla unsigned-chat
toast in one corner; the program remains identifiable, and later pressure
captures are clear apart from the noisy viewer's expected retry chat.
The exact pre-login missing-flite vanilla-narrator signature is retained as a
limitation, not mistaken for a Cinemarr native failure. All three client logs
pass explicit JOIN ordering, active-underrun checks and media reset, and the
private displays, game listener and task audio modules are gone. Receipt:
`build/clock-fix-gate/pressure-1.21.1-neoforge-isolated1-20260907.review.json`.
These are development-launcher acceptance results, not final bundle proof.
Unfair-segment pressure, exact final artifact acceptance and final-commit CI
remain open.

## 2026-09-07 legacy dimension lifecycle correction

The first same-JVM world-change run (`terminal-1.7.10-forge-world1`) failed
after 336 seconds: the returning follower never received its screen/session.
Forge's globally shared `mapStorage` made the supposedly dimension-local screen
registry identical in the overworld and Nether. This is a product defect, not
a reason to relaunch the follower or waive the test. The corrected
`perWorldStorage` path preserves an existing screen save in a non-overwritten
`cinemarr_screens.dat.before-dimension-isolation.bak` before migration. Existing
records remain in the overworld; the old format cannot identify their original
dimension, so non-overworld controllers can require reactivation/retuning.
Schema 3 and protocol 10 remain unchanged. Independent registry and backup
preservation tests bring the legacy suite to 206 tests (two opt-in skips).

The corrected `world2` run completed in 340 seconds with unchanged source
inputs. Both original client JVMs survived two Nether/overworld round trips.
Fresh closed-log checks prove zero TV/stream/pipeline/audio-source/decoder-thread
counts while away and restored single ownership after each return. The follower
is silent during each 3.95-second away recording while the owner remains
audible. Eight-second return PCM measures 20/30 ms separation with correlations
0.999857/0.988355. Fourteen transition images were directly reviewed: no TV in
either Nether view and advancing identifiable program content in all twelve
return views. Saved NBT independently contains one overworld television and
zero Nether/End televisions, each explicitly marked dimension-local.

The same run's twelve queue/replay images were also directly reviewed. Queue
and replay PCM measure 30/0 ms separation, with correlations 0.992930/0.998276;
empty-EOS and STOP silence, client media reset, private-display exit, absent
game listener and absent task audio modules pass. Closed evidence receipts:
`build/clock-fix-gate/terminal-1.7.10-forge-world2-20260907.acceptance.json`
and the adjacent `.world-acceptance.json`. These prove the development-launcher
case only. Fresh final-bundle acceptance, live unfair-client/queued-work stress,
final local gates and exact-commit hosted CI remain required.

## Historical September 7 checkpoint: recorded checks passed; requirement audit remained open

The 21-profile development matrix and four representative exact-production-JAR
real-Plex cases have passed their recorded checks and direct visual reviews.
Legacy's additional six-frame reconnect review closes the previously missing
picture evidence. Four recovery cases, two lifecycle cases and both retained
native guests have also passed their recorded checks and cleanup audits.
This is not release readiness. A requirement audit found missing live legacy
video resource/sound reload coverage: the old reload scenario only ran in the
audio-only gate. The new two-cycle-per-client video-path observer has eleven
passing regressions. Its first exact-production attempt failed because no fresh
resource reload followed the instantaneous key chord; it is not a product reload
pass. The revised input holds F3 across game ticks while delivering T and always
releases F3 on delivery failure, with two additional private-window regressions.
A separately frozen second attempt reached a real resource reload and crashed
inside Cinemarr's legacy sound handler. It is rejected; the earlier candidate
bytes fail this required lifecycle operation. The context-ownership fix now
passes focused local tests/runtime but needs a fresh canonical bundle and
exact-byte acceptance.
The observer requires fresh ordered resource/sound restart markers and advancing
frames before the existing physical PCM and direct image reviews.
The audit also found no explicit automatic queue-advance or near-EOS follower
reconnect tests. A dedicated deterministic terminal mode now exercises those
operations separately from the ordinary widget/reload matrix, with fresh state,
generation and session-identity checks, post-transition PCM and world captures,
and both-sink silence at empty EOS and STOP. Its ten observer/closed-log tests
pass, including an injected failure through the actual shell health function.
The first source-frozen terminal runs pass on both representatives, as recorded
below. Egress changes made afterward still require affected-runtime validation.
The remaining live pressure gaps, fresh final-artifact lifecycle acceptance, the complete diff
and documentation audit, commits, final-commit local validation and exact-SHA
hosted CI/artifact parity remain open. The evidence
sequence below preserves failures and their corrections explicitly.

The new legacy persistence boundary suite round-trips compressed NBT, retains
access-ordered eviction across two save/load cycles, caps sessions/queue entries,
and verifies that corrupt entries do not discard healthy neighbors. Its first
run caught a numeric session-name tag being accepted as a coerced string. The
reader now requires string tags for session/library identities and media text
fields. The envelope suite exercises every truncated hello prefix, 512 seeded
invalid lengths, malformed bodies across every packet type, direction rejection,
and bounded malformed VarInts. The initial failure remains in
`build/clock-fix-gate/legacy-persistence-fuzz-20260907.log`; the corrected focused
run is `legacy-persistence-fuzz-r2-20260907.log`.

With the terminal probe and immediate active-underrun diagnostics included,
`legacy-terminal-verify-release-20260907.log` records 198 legacy tests, zero
failures/errors and two opt-in skips, plus the reobfuscated artifact checks.
`modern-terminal-targeted-tests-20260907.log` records 157 core tests (one opt-in
skip), 39 modern tests (two opt-in skips), and the terminal/reload/reconnect
observer suites. These are local source/build checks, not acceptance of the
canonical release bundle. The terminal observer deliberately does not infer a
reconnect from IDLE alone: the shell must prove the old follower's fresh reset,
exit and archived log, then launch a new follower and establish matching IDLE.
The closed-log underrun gate checks all earlier generations, not just the last
healthy sample. Backend EOS drain counts are classified by the shared policy;
active underruns are emitted immediately so a later reset cannot erase them.

### Terminal lifecycle and subsequent egress checkpoint

The first terminal cases completed with zero exit status and unchanged frozen
source: legacy in 277 seconds and NeoForge 1.21.1 in 228 seconds. Receipts are
`build/clock-fix-gate/terminal-1.7.10-forge-r1-20260907.acceptance.json` and
`terminal-1.21.1-neoforge-r1-20260907.acceptance.json`. All 24 world captures were
directly reviewed: the identifiable program moves on both viewers after queue
advancement and replay, without a menu, chat, black TV or another avatar blocking
the picture. Legacy PCM measured 20/0 ms absolute lag at 0.994191/0.997780
correlation; NeoForge measured 0/10 ms at 0.999496/0.993998. Each pair contains
eight seconds of aligned physical PCM. Independent FFmpeg checks confirmed
silence after empty EOS and STOP on both sinks. All six client launches have
closed logs, media-reset acknowledgements and absent private-X processes; game
ports and task audio modules are absent. NeoForge's pre-login vanilla narrator
cannot load `flite`; the audit explicitly records that environment limitation
and rejects any other native-link error. This is development-launcher terminal
acceptance, not certification of final packaged bytes.

A separate compiled-code probe then reproduced an egress bookkeeping defect:
after a send exception, one item/ten bytes remained counted but the next drain
delivered zero items. The regression is preserved in
`egress-exception-regression-before-20260907.log` (one failure out of six tests).
The corrected scheduler restores round-robin membership in a `finally` block,
without swallowing the transport exception or resurrecting a disconnected
queue. It additionally accounts for independent per-client and per-session
bytes; both server adapters set 2 MiB/client, 8 MiB/session and 16 MiB/global
ceilings alongside the existing item limits. Eight scheduler tests now cover
atomic rejection, byte-budget release, exception/disconnect paths and 1,000 ticks
of greedy-client refill while another client is served each bounded tick.

`egress-byte-exception-tests-20260907.log` passes 161 core tests (one opt-in
skip), 39 modern tests (two opt-in skips), and ten terminal-observer tests.
`legacy-egress-byte-exception-verify-release-20260907.log` passes 202 legacy
tests (two opt-in skips) and reobfuscated release checks. The local
`verifyRuntimeMatrix` and representative `releaseMatrixGate` now also require
both supplemental terminal runtimes; `terminal-local-task-routing-20260907.log`
is a dry-run routing check only, not another runtime pass. CI runs the same two
supplemental cases and uploads their diagnostics. The terminal receipts above
precede these egress product changes; the affected runtime/full-candidate gates,
live pressure/overload and same-JVM world-change evidence remain open.

The `paused-frame-r2` matrix is terminal and rejected: seven automated passes,
one Forge 1.20.2 failure and thirteen unrun profiles. The failure is preserved in
`build/clock-fix-gate/paused-frame-r2-full-matrix-automated.json` and
`build/clock-fix-gate/paused-frame-r2-matrix-1.20.2-forge.log`.
Read-only cleanup receipts confirm the failed case and the preceding Quilt
case left no recorded gate/private-X processes, game listeners or audio modules.

Forge-family login handlers sent hello without a connection-owned reset. The
ordering verifier also accepted generic logout resets, including a pre-connect
logout on Forge 1.20.1. Therefore the earlier generic-marker checks are not
explicit JOIN evidence. All fifteen modern adapters now use the shared lifecycle
and emit a distinct JOIN-reset acknowledgement after reset and before hello.
Ten checker fixtures, seven shell-log tests and the all-adapter source-layout
guard pass. Root tests and the selected Fabric 1.20.2, Forge 1.20.2/26.2 and
NeoForge 1.20.2 builds passed in 1m51s. The first compile-only attempt rejected
an unnecessary diagnostic call unavailable in newer Minecraft; that call was
removed, and its failed log remains preserved. Evidence:
`build/clock-fix-gate/explicit-join-focus-build-v2-20260907.log`.
The focused Forge 1.20.2 runtime passed in 266 seconds against unchanged 797-input
source hashes, with 10 ms physical PCM separation and 0.993095 correlation. All
33 captures were directly reviewed. Three closed client logs prove explicit
JOIN-reset-before-media ordering and two reset acknowledgements each. Read-only
cleanup confirms recorded private-X/gate PIDs, the game listener and task audio
modules are absent. Evidence:
`build/clock-fix-gate/explicit-join-focus-1.20.2-forge.visual-review.json` and
`build/clock-fix-gate/explicit-join-focus-1.20.2-forge.cleanup-audit.json`.

The paused world pair retains frame 37.800 seconds/counter 189 across three
seconds. Paused seek retains that old displayed frame, not a decoded seek
preview. Playing seek/stream transitions briefly show bare screen pixels;
later captures show video again. Both final world captures show identifiable
program content despite a partially covering vanilla unsigned-chat toast.
The first fresh `explicit-join` full build passed in 610 seconds: all 76 tasks
executed, all ten required GameTests passed and all sixteen artifacts inspected.
The completed snapshot is `build/reproducibility/20260907-explicit-join-first/`.
Its separate packaged-class audit passed for all sixteen artifacts, including
shared connection ownership and the explicit JOIN-reset acknowledgement in
every modern client adapter:
`build/clock-fix-gate/explicit-join-packaged-path-audit-first.json`.
The independent second build passed in 537 seconds, also executing all 76 tasks
and passing all ten required GameTests and sixteen-artifact inspection. All
eighteen bundle files are byte-identical. Evidence:
`build/clock-fix-gate/explicit-join-build-reproducibility.json`.
Source membership and hashes, both complete bundles, free runtime ports and
absent task audio modules were checked after the build unit exited. The fresh
21-profile runtime matrix passed in 5,729 seconds and its batch exited successfully.
Accepted cases for that historical `explicit-join` candidate (not r12):

| Profile | Seconds | Reviewed captures | PCM separation | Correlation |
| --- | ---: | ---: | ---: | ---: |
| 1.7.10-forge | 293 | 33 | 20 ms | 0.995097 |
| 1.20.1-fabric | 249 | 33 | 20 ms | 0.986833 |
| 1.20.1-quilt | 284 | 33 | 10 ms | 0.993377 |
| 1.20.1-forge | 253 | 33 | 10 ms | 0.995529 |
| 1.20.1-neoforge | 253 | 33 | 10 ms | 0.999864 |
| 1.20.2-fabric | 240 | 33 | 10 ms | 0.998324 |
| 1.20.2-quilt | 292 | 33 | 10 ms | 0.998197 |
| 1.20.2-forge | 266 | 33 | 10 ms | 0.999042 |
| 1.20.2-neoforge | 266 | 33 | 10 ms | 0.992970 |
| 1.21.1-fabric | 260 | 33 | 10 ms | 0.994057 |
| 1.21.1-quilt | 307 | 33 | 10 ms | 0.998890 |
| 1.21.1-forge | 256 | 33 | 10 ms | 0.999471 |
| 1.21.1-neoforge | 248 | 33 | 10 ms | 0.998446 |
| 26.1.2-fabric | 280 | 33 | 10 ms | 0.989896 |
| 26.1.2-quilt | 273 | 33 | 10 ms | 0.997719 |
| 26.1.2-forge | 293 | 33 | 10 ms | 0.998047 |
| 26.1.2-neoforge | 252 | 33 | 30 ms | 0.992091 |
| 26.2-fabric | 253 | 33 | 0 ms | 0.989987 |
| 26.2-quilt | 299 | 33 | 0 ms | 0.989030 |
| 26.2-forge | 301 | 33 | 10 ms | 0.991204 |
| 26.2-neoforge | 254 | 33 | 30 ms | 0.999041 |

Each row has an automated result, direct image/closed-log review and read-only
process/port/audio cleanup audit under
`build/clock-fix-gate/explicit-join-matrix-<profile>.{result,visual-review,cleanup-audit}.json`.
These are frozen-source development-runtime checks; packaged real-Plex
certification remains separate. All three closed modern client logs require
explicit JOIN-reset-before-media ordering and two cleanup acknowledgements.
The final saved legacy controller captures show bare pixels during media
transitions; separate world captures show identifiable program video. They do
not prove uninterrupted video behind the UI. All twenty-one profiles are
accepted, with 693 reviewed captures. The automated batch result is
`build/clock-fix-gate/explicit-join-full-matrix-automated.json`; its earlier-stage
`directVisualReviewPending` flag is not rewritten by the separate completed
visual reviews. The supplementary runtime checks are recorded below; external
recovery/lifecycle, native, security and final-CI gates remain open. Everything below the earlier-candidate heading is evidence for preceding source/artifact hashes; no
old pass certifies this change.

Earlier real-Plex runs verified
the deployed server's exact JAR hash but used development launchers locally.
The maintained real-Plex and lifecycle gates now require prepared production
client inputs and retain exact server Jammarr dependencies on both clients.
Twenty input regression tests pass; all four current production-client visual
acceptances, including the corrected legacy follow-up, are recorded below.
Server hash parity or successful input preflight alone does
not close the exact-candidate release gate. Preparation and reuse are documented
in [production-client acceptance](PACKAGED_CLIENT_ACCEPTANCE.md).

Supplementary checkpoint (2026-09-07): Quilt with Mod Menu passed automatically
on 1.20.1 (286 seconds), 1.20.2 (301 seconds), 1.21.1 (273 seconds),
26.1.2 (276 seconds) and 26.2 (272 seconds). All five have completed direct
review: 33 captures each (165 total), all three closed JOIN/reset logs and
read-only cleanup checks. Their PCM results are respectively
10 ms / 0.990044, 10 ms / 0.997773, 10 ms / 0.996677,
10 ms / 0.993758 and 20 ms / 0.994885.
Evidence is
`build/clock-fix-gate/explicit-join-quilt-modmenu-<profile>.{result,visual-review,cleanup-audit}.json`.
The legacy repeat also completed all 33 captures, closed logs and cleanup
checks (296 seconds; 20 ms PCM lag / 0.995405 correlation). Both adverse cases
have completed baseline-image, closed-log and cleanup reviews: legacy took
181 seconds (20 ms / 0.994839), NeoForge 1.21.1 took 144 seconds
(10 ms / 0.959408). Their images and PCM precede the injected faults;
post-fault recovery is established by logged two-client stability, not by
post-fault physical captures. Separate reports retain that limitation under
`build/clock-fix-gate/explicit-join-adverse-<profile>.visual-review.json`.

The minimum-loader checks exposed another validation gap. Although all five
scripts returned success, independent inspection shows 1.20.1, 1.20.2 and
1.21.1 loaded Fabric **0.19.3**, not the requested **0.19.2**. Those three
automated results are rejected as minimum-version certification. Only 26.1.2
and 26.2 logged 0.19.2. The failed independent audit is
`build/clock-fix-gate/explicit-join-minimum-loader-independent-audit.json`;
its log/result hashes preserve the contradiction without rewriting the
original automated reports. The corrected override excludes only the Fabric
loader from the normal release lock and forces the requested test version;
writing release locks with an override is forbidden. A startup-log assertion
now rejects silently upgraded, missing, duplicate or mismatched loader markers
(ten regression fixtures pass). All five fresh cases pass, with actual 0.19.2
verified independently, in 234 seconds; the 798-input test source manifest and
both original 18-file bundles remain unchanged throughout that batch. Evidence:
`build/clock-fix-gate/minimum-loader-corrected-20260907.json`. The earlier
797-input freeze remains historical for the preceding runtime matrix; these
build/harness changes do not retroactively alter its recorded source identity.

Production-client preparation is distinct from acceptance: public cached
launcher metadata and upstream checksums verify 48 library entries for Forge
1.7.10, 63 for Quilt 1.20.1, 155 for NeoForge 1.21.1 and 79 for Fabric 26.2, with matching asset
indexes. These preparation records are
`build/clock-fix-gate/packaged-client-inputs-<profile>.json`. They do not prove
an actual packaged launch by themselves. Subsequent isolated staging completed for all four,
including 674, 3575, 3888 and 5057 unique asset objects respectively. NeoForge's
155 input entries deduplicate to 117 staged library files. No launcher account
stores or existing instances were copied or changed. Legacy Forge's initial
HTTP 403 was resolved with an explicit verification-client identifier; the
public checksum matches. A missing 22-byte LWJGL placeholder JAR was downloaded
and hash-checked only in the task-owned cache. The isolated staging and launch
helpers have passed syntax/help checks, and staging's live-test refusal check
passed. Staging exited successfully. All four prototype production launches
then reached the expected connection-refused screen on an unused local test
port; their captures were directly reviewed and private displays were stopped.
Those null-audio, disconnected launch prerequisites do not prove playback.
The maintained launcher's NeoForge 1.21.1 deterministic two-client integration
then passed in 218 seconds with all 802 non-document inputs and both 18-file
bundles unchanged. All 33 captures were directly reviewed. Physical PCM passed
at 10 ms lag / 0.999223 correlation; all three closed client logs show explicit
JOIN-before-media and two reset acknowledgements. Three production launch
records (leader, follower, reconnected follower) match the exact indexed JAR.
The unit exited successfully; original private-X PIDs, game/RCON listeners and
task audio modules are gone. This uses a deterministic development server,
not real Plex. Evidence is
`build/clock-fix-gate/packaged-local-neo-20260907.{json,visual-review.json,cleanup-audit.json}`.
The four disconnected-launch reviews are recorded separately in
`build/clock-fix-gate/packaged-focus-direct-review-20260907.json`.

The read-only DiscPanel preflight authenticated using the designated credential
file and confirmed all four authorized servers stopped, autostart off and
Cinemarr disabled. That state check is not deployment/playback evidence:
`build/clock-fix-gate/discopanel-packaged-preflight-20260907.json`.

Exact deployment subsequently passed for all four representatives in 159
seconds, including downloaded candidate-hash verification and disabled,
hash-verified rollback copies. The wrapper restored all servers stopped,
autostart off, Cinemarr disabled, unrelated mod settings unchanged and original
Docker overrides. Evidence:
`build/clock-fix-gate/packaged-client-deployment.result.json`.

The first packaged real-Plex batch is **failed**, not certified. Legacy Forge
stopped after 106 seconds at the owner's paused stream-switch assertion; the
other three profiles were not run. Closed session evidence preserves exactly
84388 ms across generation 3 to 4, but audio remains 205039. A read-only Plex
metadata check confirms item 70140 has one audio track (205039) and one subtitle
track (205040). The observer had incorrectly required an alternate audio track,
where the existing real-Plex stream contract permits audio or subtitles. The
owner observer now has an explicit stream-kind input and ten passing regression
tests; either mode still requires a real selection change and preserved cursor.
The failed report remains immutable. Its server was restored stopped with
Cinemarr disabled; the preceding evidence was recoverably moved under
`build/external-evidence-before-packaged-client-20260907/real-plex/1.7.10-forge`.
The earlier 802-input packaged-local freeze predates this observer/test change
and is not relabeled.

The corrected subtitle-mode production batch passed all four automated cases
against `packaged-stream-choice-inputs-20260907.sha256` (803 inputs), with both
original 18-file candidate bundles unchanged. Direct reviews cover all 132
images and the twelve closed client logs:

| Profile | Seconds | Post-reconnect PCM lag / correlation | Direct visual outcome |
| --- | ---: | --- | --- |
| 1.7.10-forge | 219 | 50 ms / 0.944307 | Incomplete: reconnect world image is black |
| 1.20.1-quilt | 224 | 20 ms / 0.989631 | Accepted, including reconnected movie footage |
| 1.21.1-neoforge | 216 | 10 ms / 0.993615 | Accepted, including reconnected movie footage |
| 26.2-fabric | 195 | 0 ms / 0.991603 | Accepted, including reconnected movie footage |

Reports are
`build/clock-fix-gate/real-plex-<profile>-packaged-stream-choice.{result,visual-review}.json`.
Each wrapper restored its server stopped, autostart off, Cinemarr disabled,
unrelated mods unchanged and Docker overrides restored. The reviews verify
original private-X PIDs are gone and reject render-thread/texture-close/health
errors; modern closed logs also prove explicit JOIN/reset ordering. Each
physical PCM pair is eight seconds and follows the follower reconnect.
Paused world pairs hold the prior displayed picture; they do not claim a
decoded paused-seek preview. Some stream/seek transitions briefly expose bare
TV pixels, with later movie footage restored. Immediate old-state captures are
distinguished from the strictly checked authoritative cursor/stream changes.

Legacy is not visually certified by its automated pass or 50 ms audio result.
Its black reconnect screenshot may be content/timing but is insufficient proof;
the final dimmed controllers show bare TV blocks. A maintained post-reconnect
capture now records three fixed world-view pairs after PCM, requires fresh UI
acknowledgements and advancing render timestamps, and never automatically
accepts pictures. The first 215-second follow-up did not execute that new hook:
it was mistakenly placed in the audio-only function. An independent routing
rejection preserves this false positive in
`build/clock-fix-gate/packaged-reconnect-capture-routing-rejection.json`.
The hook is now in the video path, an eleventh regression checks its placement
after PCM recording, and the external wrapper independently requires all six
hashed images before reporting success. The corrected focused follow-up binds
805 inputs in `packaged-reconnect-capture-r2-inputs-20260907.sha256` to the same
product JARs. The preceding legacy raw evidence is preserved under
`build/external-evidence-before-packaged-reconnect-capture-20260907/real-plex/1.7.10-forge`;
its earlier visual review remains explicitly incomplete.

The corrected legacy follow-up completed in 218 seconds with all 805 input
hashes and both original bundles unchanged. All 39 images have now been directly
reviewed: the six fixed post-reconnect captures show a person in a robe moving
along a front-garden path on both clients. The nearly black automatic first-frame
image remains preserved; it is not used as identifiable-video proof. Final
controllers at 3:43/3:44 show the same program behind readable controls.
Post-reconnect eight-second PCM passes at 50 ms lag / 0.847651 correlation.
The accepted follow-up report is
`build/clock-fix-gate/real-plex-1.7.10-forge-packaged-reconnect-capture-r2.visual-review.json`.

All four production representatives are now visually accepted on the unchanged
candidate bytes. Independent verification checks all twelve production launch
records, actual client Cinemarr/Jammarr JAR hashes against the server evidence,
all 138 accepted image hashes (39 legacy plus 33 on each modern target), closed
logs, absent private-X processes and audio modules, and exact return of server
diagnostics to baseline. A fresh authenticated audit confirms all four servers
stopped, autostart off and Cinemarr disabled. Evidence:
`build/clock-fix-gate/packaged-real-plex-closed-20260907.json` and
`build/clock-fix-gate/discopanel-packaged-post-plex-20260907.json`.
Recovery/lifecycle, fresh native smoke, secret scanning, final commits and
exact-SHA hosted CI/parity remain separate open gates.

The fresh four-profile Plex recovery matrix subsequently passed in 458 seconds
against the same 805-input freeze and unchanged candidate bundles: legacy 91,
Quilt 139, NeoForge 106 and Fabric 111 seconds. All four exercised disabled
configuration, a controlled HTTP 503 outage, operator retry and automatic retry,
with ready diagnostics returning to zero active streams and queued work.
Each gate checks configured credentials/endpoints are absent from captured logs
and restores its server configuration and Docker overrides exactly. Each wrapper
then restores Cinemarr disabled and unrelated mods unchanged. A separate
authenticated read-only audit confirms all four stopped/autostart-off baselines.
Evidence:
`build/clock-fix-gate/packaged-reconnect-capture-r2-plex-recovery-batch.json`,
`build/discopanel-plex-recovery/<profile>/<profile>.plex-recovery.evidence.txt`,
and `build/clock-fix-gate/discopanel-packaged-post-recovery-20260907.json`.
This is server recovery evidence, not an additional physical A/V run.

Fresh native checks also passed on the same candidate: retained Windows 11
x86-64 took 237 seconds and retained Debian ARM64 took 325 seconds, without
reinstalling either guest. Each accepted software decode at 144p, 480p and
1080p, with matching reference frame counts, zero video-PTS delta and zero A/V
drift delta. Windows reports AMD64 and PE machine `0x8664`; ARM reports
`aarch64`. Run IDs are `20260907T182033Z` and `20260907T182433Z` respectively.
An independent package audit matches all 90 Windows/ARM native entries in each
of the sixteen candidate JARs and the sixteen benchmark decoder classes against
the NeoForge 1.21.1 JAR. Both complete candidate bundles remain unchanged.
The final retained-state audit confirms both installed disks ready and powered
off; the native batch unit is terminal with exit zero. Evidence:
`build/clock-fix-gate/packaged-native-checks-20260907.json`,
`build/native-smoke/20260907-packaged-native-parity.json`, and
`build/native-smoke/retained-vm-audit-20260907-packaged.json`.
These are native decoder/ABI checks, not full Minecraft-on-Windows/ARM runtime
certification; ARM uses QEMU software emulation, not physical ARM performance.

The two production-client lifecycle cases passed in 163 seconds (legacy 83,
NeoForge 75 seconds). Both stop a Quick TV build at 256 placed / 8960 remaining,
restart with a pending 9216-position cleanup footprint, complete that cleanup
after chunks are reloaded, and verify the first placed pixel is air. Independent
inspection checks the exact candidate and production Jammarr hashes in all four
client launch records, four closed private-X logs, both terminal batch units and
absent task audio modules. A fresh authenticated audit confirms all four managed
servers stopped, autostart off and Cinemarr disabled. Evidence:
`build/clock-fix-gate/packaged-reconnect-capture-r2-lifecycle-batch.json`,
`build/clock-fix-gate/packaged-recovery-lifecycle-closed-20260907.json`, and
`build/clock-fix-gate/discopanel-packaged-post-lifecycle-20260907.json`.

The completed external/native checks retain their 805-input source identity.
A subsequent CI-only change adds minimum-loader, production-launcher, owner
stream-selection and post-reconnect capture regressions to GitHub preflight;
all 51 cases pass locally, along with manifest/layout/hygiene checks. No product
JAR changed. This is not a hosted CI pass or final-commit certification.

The first current deep scan examined 49,608 files and 866,307 unique decoded
payloads with no archive/save decoding errors. Its only matches were deployment
endpoints in two ignored task-helper scripts, not product source, JARs, logs,
saves or runtime evidence. Both helpers now accept the API endpoint as an
explicit invocation argument; no credential value was printed or persisted.
The failed report remains preserved as
`build/clock-fix-gate/packaged-final-configured-secret-scan-20260907.json`.
The corrected scan completed successfully with 49,610 files, 866,309 unique
decoded payloads, zero matches and zero decoding errors:
`build/clock-fix-gate/packaged-cleaned-configured-secret-scan-20260907.json`.
Its service is terminal with exit success and no main process. This clean
checkpoint does not cover later reload-observer work; a fresh final scan remains
required. The first failed result remains historical, not a clean certificate.

The first live resource-reload attempt is preserved in
`build/clock-fix-gate/real-plex-1.7.10-forge-packaged-reconnect-capture-reload1.result.json`
(247 seconds, failed). Its original private X PIDs are gone, and the authenticated
`discopanel-packaged-post-reload1-20260907.json` audit confirms four stopped,
autostart-off, Cinemarr-disabled servers. The two final client logs have no
media-reset acknowledgement after the failure; only the earlier reconnect log
does. Therefore forced-process cleanup is not described as graceful lifecycle
acceptance. Raw failed evidence is preserved under
`build/external-evidence-before-packaged-reconnect-capture-reload2-20260907/real-plex/1.7.10-forge/`.
The revised observer also rejects an empty-item IDLE packet after PLAYING rather
than accidentally accepting the older playing state. The revised attempt uses
`packaged-reconnect-capture-reload2-inputs-20260907.sha256` (807 non-document inputs)
and the unchanged eighteen-file candidate bundles.

The second attempt failed after 190 seconds. The real client log records
`Reloading ResourceManager`, sound-system shutdown and a new sound-library
startup, then `UnsatisfiedLinkError: org.lwjgl.openal.AL10.nalSourceStop(I)V`
from `LegacyVideoAudio.stopSource` through `audioEngineReloaded` and
`LegacyClient.soundLoaded`. Forge's local 1.7.10 source confirms `SoundLoadEvent`
is posted after unloading the old sound system but before the asynchronous new
one necessarily finishes. LWJGL's installed implementation clears its native
stubs when destroying the context. This is a product defect, not an accepted
transient or an unchanged-code retry. Evidence:
`build/clock-fix-gate/real-plex-1.7.10-forge-packaged-reconnect-capture-reload2.result.json`
and its raw leader crash report under the corresponding runtime directory.
The fresh `discopanel-packaged-post-reload2-20260907.json` authenticated audit
confirms all four servers are stopped, autostart-off and Cinemarr-disabled.
The fix discards obsolete native IDs on the post-destruction event, waits for a
live context before allocating, and permits native cleanup only in the exact
context that owns the source. Previous runtime/native receipts remain historical
evidence for their recorded bytes; they are not proof for the changed product.

The context-ownership fix now passes the isolated legacy `verifyRelease` gate:
190 tests in 54 suites, zero failures/errors and two explicit opt-in skips
(hardware decoder and credentialed Plex smoke). The focused context/reload and
backend-guard suites also pass. The first focused command used Java 21 and
failed before tests because current RFG requires newer build bytecode; rerunning
with the manifest's Java 26 build toolchain resolved that invocation mistake.
The first actual regression run then exposed LWJGL missing from RFG's test
runtime classpath. The tests now reuse the existing locked `lwjgl2Classpath`,
without initializing an OpenAL context or adding another dependency version.
The corrected full build log is
`build/clock-fix-gate/legacy-context-reload-verify-release-20260907.log`.
The reobfuscated legacy JAR hash is
`0204f41a5713c26814d1c0d5700c224d7ab28e7267e8fd88063736d9143822b0`;
the canonical sixteen-artifact release bundle has not yet been rebuilt.
A fresh development-launcher two-client local case ran under
`build/legacy-context-reload-local-20260907/`, bound to the 808-input
`legacy-context-reload-focused-inputs-20260907.sha256` freeze. This is a focused
runtime check, not exact-production acceptance or a final-commit release gate.
That first focused case failed. Its leader survived an actual reload, recreated
audio and continued rendering with zero underruns until the fixture ended, but
the second reload never reached the resource manager. Legacy's T binding can
leave chat open, so the observer now reopens and acknowledges the controller,
then closes it, before **each** reload rather than only before each client.
Its regression mock requires this per-cycle world reset. Partial successful
actions are now written to `progress.json` with `reloadCompleted=false`; only
all four completed actions can produce the final result. The failed first case
is not accepted from its partial success. A separately frozen local attempt is
ran under `build/legacy-context-reload-world-reset-20260907/`, with unchanged
product JARs and a new 808-input source manifest.

The world-reset attempt passed in 318 seconds. Both clients completed two fresh
resource/sound reloads in the same playing session, then eight-second physical
PCM passed at 50 ms lag and 0.999924 correlation. All six fixed post-reload world
images were directly reviewed: advancing test-pattern counters/shapes are
visible on both clients, without chat, pause menus, debug overlays or other
avatars obscuring the TV. The three closed client logs acknowledge media reset;
their original private-X PIDs, the original gate PID, game port and task audio
modules are absent. The 808-input source freeze still matches, as does the
rebuilt reobfuscated legacy JAR. Focused evidence:
`build/clock-fix-gate/legacy-context-reload-world-reset-20260907.acceptance-r2.json`.
Only these six world images are certified by this focused review; the other
controller captures were not re-certified here.

The first audit receipt incorrectly calculated zero elapsed time from default
systemd fields after the transient unit had been garbage-collected. It is
preserved and superseded by the `acceptance-r2` receipt, which uses the original
invocation's journal timestamps, the actual final gate-passed marker, closed
logs and absent owned resources. It explicitly does not claim that an exit
status remains queryable on the collected unit. No runtime retry was performed
for this audit-metadata correction.

The phase-7 audit additionally found that the current LRU test checks eviction
only in memory and the envelope tests have no fuzz cases. Explicit save/reload
eviction and bounded malformed-packet tests remain required, alongside the
already-open end-of-stream/queue/reconnect requirements. These checks must be
completed before the final production recertification, commit and CI gates.

## Earlier candidate evidence

Status: **1.0 prerelease; handshake, controller, generation-race and paused-frame
corrections implemented; final-byte certification pending**. The preceding disconnect candidate's
matrix ended with five passes and one failure (Fabric 1.20.2 reconnect); fifteen
profiles were not run. Its follower received media before JOIN reset and never
recovered. The preserved terminal rejection is
`build/clock-fix-gate/disconnect-candidate-reconnect-rejection.json`.

The current worktree gates modern tracking on the actual loader handshake,
synchronizes accepted players when Plex becomes available, centralizes controller
permission/error feedback, corrects completed play/seek status messages, and
rejects overflowing browse offsets before HTTP. Core/root tests passed; new
feedback tests and the browse-boundary regression are included. Representative
builds and real widget/reconnect checks for that correction passed as recorded
below; subsequent live-timeline changes still require new checks. All checklist boxes below
are reset for the changed candidate; older evidence remains historical and must
not be silently rebound to new artifact hashes.

The first corrected Fabric 1.20.2 focused run subsequently passed in 233 seconds
with unchanged 785-input source hashes, 10 ms physical audio separation and
correlation 0.991657. All thirteen captures were directly reviewed: real widget
denials/errors and their expiry are correct, the reconnected follower shows
identifiable video, and all three closed client logs contain both reset
acknowledgements. Evidence:
`build/clock-fix-gate/handshake-feedback-focus-1.20.2-fabric.visual-review.json`.
This review also found that controller elapsed time, relative seek, and stream
selection still use the last packet position rather than the synchronized live
playback position. The UI and CLI probes now use the synchronized clock. Server
stream changes already ignored the client-supplied position; they now snapshot
the current server cursor after metadata preparation and preserve pause. Seeking
while paused no longer starts media, and paused snapshots retain stream choices.
The new coordinator regression passed. The earlier focused pass does not
certify these subsequent changes or mark full controller acceptance complete.

The first owner-timeline attempt was rejected at paused stream selection:
the recovery-only restart method did not advance the playback generation.
Five reviewed captures did prove an advancing playing clock, a stable paused
clock and a paused +30-second seek. The failed attempt is retained in
`build/clock-fix-gate/live-timeline-first-attempt-rejection.json`.
A dedicated reconfiguration operation now replaces the active stream at the
server cursor, or updates a paused session without starting media, with a new
generation in either case. Its strengthened regression and representative
builds passed. The corrected frozen 788-input Fabric 1.20.2 run then passed
in 227 seconds with 10 ms physical audio separation and correlation 0.999708.
All 24 captures were directly reviewed: playing time advances, paused time is
stable, paused seek/stream changes retain pause and cursor, and resume plus
playing seek/stream changes follow the synchronized live timeline. Permission
denials and server errors are readable and expire back to Playing. All three
closed client logs contain both render-thread reset acknowledgements; task
unit, game port and audio modules were absent after teardown. This focused
development-runtime evidence is recorded in
`build/clock-fix-gate/live-timeline-v2-focus-1.20.2-fabric.visual-review.json`.
Other representative runtimes, final artifacts and full certification remain
unverified; no release checklist box is being checked from this focused pass.

The first legacy owner-widget attempt stopped before Play: its acceptance-only
open shortcut omitted the library request made by the real controller open,
leaving no movie row. The initial capture and failed 122-second run are retained
under `build/live-timeline-v2-focus-20260906/1.7.10-forge/`. The shortcut now
requests libraries like the real path; the source-layout guard checks both
modern and legacy shortcuts, and the legacy rebuild passed in 26 seconds.
A separate corrected attempt is required; this is a harness correction, not
evidence that the real legacy controller cannot browse.

That corrected legacy attempt passed the ten non-owner feedback captures and
reached owner pause, paused seek, paused stream selection and resume. It then
failed on playing seek: an in-flight segment request for the old generation
displayed a false tracking-permission error even though the seek completed.
The closed logs and seventeen reviewed captures are preserved in
`build/clock-fix-gate/live-timeline-v3-focus-1.7.10-forge.rejection.json`.
All three server implementations now suppress obsolete transport errors only
for an existing viewer of the same session with an older generation, validate
malformed requests before that distinction, and discard obsolete asynchronous
segment failures before notifying clients. The coordinator regression covers
current/future/negative generations, unknown sessions, unauthorized/departed
viewers and shutdown. Core/root tests and three representative builds passed
in 1m09s. The corrected 789-input legacy run passed in 308 seconds with
10 ms audio separation and correlation 0.997554. Eighteen images were directly
reviewed: owner timeline, follower editing, identifiable video on both clients
and the final controller. Selection survived ten session packets and twenty
UI rebuilds; replacement typing produced `drZ123`, and `session456` survived
queue/browser toggles. The review is recorded in
`build/clock-fix-gate/live-timeline-v4-focus-1.7.10-forge.visual-review.json`.

The owner-widget gate now also retains a follower text selection through real
owner state transitions and captures replacement typing and queue/browser
draft retention. The reviewed legacy edit sequence passed. Those captures
exposed a separate visual gap: pausing showed bare screen pixels instead of
the last program frame. That finding led to the next correction below.

A shared paused-frame policy now permits moving only the displayed texture
to a newer paused generation of the same session and movie, and only after
the old stream is no longer referenced. Decoder jobs/audio queues do not move;
the old pipeline is closed normally. Two policy tests reject other sessions,
movies, current/older generations, resume, stop, errors and missing metadata.
Core/root tests and three representative builds passed in 1m07s. The next
791-input focused runs require the held-frame hash to survive paused seek
and stream changes and captures the actual paused TV with the controller
closed, several seconds apart. Forge 1.7.10 passed in 316 seconds with -10 ms
measured lag and correlation 0.995265; Fabric 26.2 passed in 298 seconds with
10 ms lag and correlation 0.995626. Their 791-input source manifests match.
Twenty-one images per run were directly reviewed, including both paused-world
captures, owner controls, editing and cleared drafts, and both clients' program
images. The reviewed subset is explicit; it does not claim a complete widget
catalog audit. The closed client logs have the required cleanup acknowledgements,
and both task units, game ports and task audio modules were gone after teardown.
Evidence:
`build/clock-fix-gate/paused-frame-v5-focus-1.7.10-forge.visual-review.json` and
`build/clock-fix-gate/paused-frame-v5-focus-26.2-fabric.visual-review.json`.

The complete forced-build attempt froze 797 non-documentation repository inputs
(the focused 791 plus ignore rules, licenses, proposal and icon). Its first build
failed after 560 seconds and all ten passing GameTests: the log-marker test's
healthy fixture lacked the JOIN-reset evidence required by the strengthened gate.
The failed report is preserved as
`build/clock-fix-gate/paused-frame-build-reproducibility.json`; it contains no
certified bundle and no second build. The corrected fixtures pass all seven
tests, including wrong-order rejection in all three closed client logs and the
legacy disconnect-only reset case. The other lightweight harness checks pass.
The separate `paused-frame-r2` attempt froze the corrected 797 inputs. Both
forced builds passed in 512 and 481 seconds with all 76 root tasks executed,
all ten required GameTests passed and all sixteen artifacts inspected in each.
All 18 bundle files are byte-identical. A separate packaged-class audit confirms
the new controller, paused-frame and safety references in all sixteen JARs.
Evidence: `build/clock-fix-gate/paused-frame-r2-build-reproducibility.json` and
`build/clock-fix-gate/paused-frame-r2-packaged-path-audit-first.json`.
The build unit exited cleanly; all runtime ports and task audio modules were
clear before starting the new 21-profile serial matrix. These development
runtimes are bound to the frozen source and bundle integrity, not described as
packaged-JAR runs. Exact-artifact external/native acceptance, whole-evidence
security audit, scoped commits and exact-SHA hosted CI/artifact parity remain
unchecked.

Current frozen-source matrix progress (not packaged real-Plex certification):

| Profile | Runtime seconds | Directly reviewed captures | PCM lag | Correlation |
| --- | ---: | ---: | ---: | ---: |
| 1.7.10-forge | 296 | 33 | 60 ms | 0.988277 |
| 1.20.1-fabric | 239 | 33 | 10 ms | 0.999756 |
| 1.20.1-quilt | 294 | 33 | 10 ms | 0.993340 |
| 1.20.1-forge | 262 | 33 | 20 ms | 0.997284 |
| 1.20.1-neoforge | 250 | 33 | 20 ms | 0.986833 |
| 1.20.2-fabric | 242 | 33 | 10 ms | 0.999037 |

Every listed case has separate `paused-frame-r2-matrix-<profile>.result.json`,
`.visual-review.json` and `.cleanup-audit.json` records under
`build/clock-fix-gate/`. Each review covers both program images, three scaled
controllers, ten permission/error images, eleven owner-timeline images and
seven edit-retention images. The three closed client logs contain the required
reset acknowledgements; modern logs additionally pass JOIN ordering. Read-only
cleanup audits verify the recorded gate/private-X PIDs, game port and per-case
audio modules are absent.

Playing seek/stream-change captures include brief pixel backgrounds before
later captures show video again. Paused seek retains the previous displayed
frame, not a newly decoded seek preview. Vanilla unsigned-chat toasts partially
cover upper-right program areas in the modern world captures, but the counters
and main color pattern remain identifiable. Six of 21 profiles now have both
automated and direct-review evidence; the other matrix and acceptance gates
remain open.

Fabric 1.20.2 now passes the reconnect that rejected the previous disconnect
candidate. Its three closed logs pass JOIN ordering and each contains both
reset acknowledgements. This is fresh evidence for the current frozen source,
not a reinterpretation of the earlier failure; fifteen profiles still lack
completed current-candidate acceptance.

The preceding responsive-UI candidate passed focused GUI/edit checks on four runtimes (24 reviewed captures), reproducible builds, all ten GameTests, and Windows/ARM native checks with both retained guests off. Its legacy real-Plex case passed direct review, but its 26.2 case is explicitly rejected. Still earlier UI-visibility real-Plex/recovery/lifecycle results remain historical only. None of these results certifies the changed disconnect-cleanup tree.

Direct controller review subsequently found corrupt wide legacy buttons,
overlapping notices/episode controls, and missing Minecraft 26 text alpha.
The legacy leader also retained a dead test-player save from an earlier
lifecycle fall. Corrections now exist, including save preparation/restoration
and rejection of dead or GUI-obscured capture readiness. Current checkboxes
refer only to the new handshake/feedback-corrected worktree. Earlier responsive-UI, UI-visibility and
coordinator-candidate results remain regression evidence, not certification.
Current work is tracked in [the hardening plan](1.0_RELEASE_HARDENING_PLAN.md).

## Code and artifact gates

- [ ] The worktree is intentionally scoped and the release commit is identified.
- [ ] Unit tests and all 10 required GameTests pass under the required Java toolchain.
- [ ] All 16 canonical JARs build from the current hardening tree.
- [ ] Artifact inspection verifies identity/version, protocol, loader metadata, Java bytecode level, native classifiers, licenses, recipes/assets, and no credentials.
- [ ] No JAR contains the removed music/station runtime, JLayer, or Jump3r.
- [ ] SHA-256 digests are recorded for every artifact.

The frozen disconnect-corrected candidate passed two forced 74-task builds in
8m01s and 8m02s, each including all ten required GameTests and deep inspection
of all sixteen artifacts. All eighteen bundle files match
`build/reproducibility/20260906-disconnect-first/` byte for byte.
`build/clock-fix-gate/disconnect-build-reproducibility.json` records both runs
and every file hash; `disconnect-packaged-path-audit-first.json` confirms the
shared lifecycle helper and applicable Fabric adapter references as well as
the earlier UI/safety paths. These historical results are build evidence for
their recorded bytes, not current certification or release readiness.

The preceding frozen responsive-UI candidate passed two forced 74-task builds in 8m03s
and 7m56s, each including all ten required GameTests and deep inspection of
all sixteen artifacts. All eighteen bundle files match
`build/reproducibility/20260906-responsive-ui-first/` byte for byte.
`build/clock-fix-gate/responsive-ui-build-reproducibility.json` records both
runs and every file hash. The packaged-path audit additionally checks shared
layout/paging, edit-retention and safety references in all sixteen JARs.
Configured-secret scanning, full runtime/native/external acceptance, the final
release commit and hosted artifact parity remain separate unchecked gates.

The preceding UI-visibility-corrected candidate passed two forced 73-task builds in
7m56s and 7m59s, each covering all sixteen artifacts and all ten required
GameTests. All eighteen bundle files match
`build/reproducibility/20260906-ui-visibility-first/` byte for byte.
`build/clock-fix-gate/ui-visibility-build-reproducibility.json` records both
runs; `ui-visibility-packaged-path-audit-first.json` also verifies that all
sixteen packaged clients include the visibility guard and opaque text values,
and that the legacy artifact includes its tiled button class. These checks
do not establish visible runtime acceptance or final hosted parity.

The earlier September 6 coordinator-corrected candidate passed two forced 72-task builds in
8m06s and 7m55s: all 16 artifact targets, all ten required GameTests, source-layout and
target-manifest validation, release hygiene, and deep inspection. All eighteen
files in its then-current release bundle matched
`build/reproducibility/20260906-coordinator-first/` byte for byte, and every
JAR passes `SHA256SUMS`. Logs are
`build/clock-fix-gate/coordinator-build-{first,second}.log`. External/runtime
certification, the release commit, and hosted CI/parity remain unchecked.

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
- [ ] Removing the final television checkpoints and closes the Plex transcode; removing one of several attached TVs does not.

All four disconnect-corrected recovery cases passed controlled outages,
operator/automatic retry and exact configuration restoration. Both legacy and
NeoForge restart-mid-build cases retained pending work through unloaded chunks,
completed recovery when the chunks became available, and verified the first
placed pixel returned to air. `build/clock-fix-gate/disconnect-external-certification.json`
binds all ten external cases and a fresh stopped/disabled four-server audit.
The complete local matrix, broader lifecycle/adverse cases and final whole-evidence
scan are separate requirements, not inferred from these representative passes.

The pre-matrix whole-closed-evidence scan passed 28,380 files and 586,243
decoded payloads after redacting private-host addresses from three historical
ARM logs and distinguishing empty region placeholders from malformed nonempty
files. Its report is `build/clock-fix-gate/disconnect-all-closed-evidence-secret-scan-corrected.json`;
the initial rejected report and five scanner-regression results are retained.
Later matrix output and final source/documentation edits still require the
final scan before the credential/evidence checkbox can be completed.

## Native host gates

- [ ] The packaged `linux-arm64` classifier decodes 144p, 480p, and 1080p fixtures in software under an aarch64 kernel and aarch64 Java runtime.
- [ ] The packaged `windows-x86_64` classifier decodes the same fixtures in software under native Windows x86-64 and its FFmpeg DLL reports PE machine `0x8664`.
- [ ] Both runs stop their tagged transient QEMU units and leave their reusable installed guests powered off with ready identity markers.

The disconnect-corrected candidate passed fresh Windows and ARM runs at
`build/native-smoke/windows-x86_64/20260906T182100Z/` and
`build/native-smoke/linux-arm64/20260906T182455Z/`. Both reused their installed
guest and accepted all three fixtures. The report
`build/native-smoke/20260906-disconnect-native-parity.json` binds 90 tested
native payload entries across all sixteen JARs, 16 representative decoder
classes, fixture hashes and invocation IDs. The combined stopped-state audit
is `build/native-smoke/retained-vm-audit-20260906-disconnect.json`. These are
native decoder checks, not complete Windows/ARM Minecraft runtime certification.

The preceding responsive-UI candidate passed fresh runs at
`build/native-smoke/windows-x86_64/20260906T172630Z/` and
`build/native-smoke/linux-arm64/20260906T173057Z/`. Each accepted all three
fixture rows and reused its installed guest. Its parity report,
`build/native-smoke/20260906-responsive-ui-native-parity.json`, checks all 90
platform-native entries across all sixteen JARs (including nested dependencies),
16 representative decoder classes, fixture hashes, invocation identities,
and per-run stopped-state evidence. The combined fresh audit is
`build/native-smoke/retained-vm-audit-20260906-responsive-ui.json`.

The preceding UI-visibility-candidate runs completed on September 6 at
`build/native-smoke/windows-x86_64/20260906T151455Z/` and
`build/native-smoke/linux-arm64/20260906T151849Z/`. All three resolution rows
passed on each guest. That candidate's native parity report,
`build/native-smoke/20260906-ui-visibility-native-parity.json`, verifies all 90
Windows/ARM payload entries in every candidate JAR, 16 decoder classes in the
representative NeoForge JAR, and the fixture hashes used by both runs.
`build/native-smoke/retained-vm-audit-20260906-ui-visibility.json` confirms both
installed guests ready and off. See [the reuse procedure](NATIVE_TEST_GUESTS.md).
Hosted parity and full Minecraft acceptance remain separate requirements.

## Decisive real-Plex gate

- [ ] The in-game controller/UI browses and selects an identifiable item from an allowed real Plex library.
- [ ] Two independent clients show matching identifiable program video.
- [ ] Both clients produce synchronized audible program audio within the documented threshold.
- [ ] Pause, seek, stream selection, stop, disconnect/reconnect, and final-TV teardown behave correctly.
- [ ] Plex reports no leftover session/transcode and the host has no leftover game process, port, temporary credential file, or credential-bearing log.

All four disconnect-corrected representatives passed the exact-artifact controls,
physical audio, reconnect and explicit client cleanup gates. Direct review of
all twelve images confirms identifiable playback on the leader/reconnected
follower and readable, non-overlapping controller controls. Modern controllers
retain a stale `Buffering` notice; that status-text review item remains recorded
below rather than being hidden by the checked playback gates.
`build/clock-fix-gate/disconnect-real-plex-visual-review.json` binds image/log
hashes, cleanup acknowledgements, audio results and deployed artifact hashes
to `build/reproducibility/20260906-disconnect-first/`.

| Representative | Audio correlation | Absolute separation | Gate duration |
| --- | ---: | ---: | ---: |
| Forge 1.7.10 | 0.944327 | 40 ms | 192 s |
| Quilt 1.20.1 | 0.992182 | 10 ms | 220 s |
| NeoForge 1.21.1 | 0.976548 | 0 ms | 191 s |
| Fabric 26.2 | 0.995776 | 10 ms | 171 s |

Each case restored its test server stopped, autostart off, and Cinemarr
disabled with unrelated mod settings and Docker overrides unchanged. No
task-owned Java/Xvfb/audio-capture process or Cinemarr audio module remained
between cases. The legacy gate also verified both saved probe players alive
and restored their original NBT bytes. The final whole-evidence secret scan,
complete runtime/recovery/lifecycle matrix and final end-state audit remain open.

All four preceding UI-visibility representatives passed the automated controls,
audio, and cleanup assertions, and direct review accepted both playback
captures and the controller capture on each. The report
`build/clock-fix-gate/ui-visibility-real-plex-visual-review.json` binds those
images to deployed artifact hashes matching
`build/reproducibility/20260906-ui-visibility-first/`. Correlations
range from 0.917769 to 0.979617, with absolute audio separation 0–40 ms.
Their closed evidence was moved without byte changes into
`build/discopanel-{real-plex,plex-recovery,lifecycle}-ui-visibility-20260906/`;
`build/clock-fix-gate/ui-visibility-external-archive-index.json` records every
relocated file hash, and the visual-review report points to the archived images.
The current checklist remains open until the new candidate's external cases,
final whole-evidence scan, and end-state audit pass.

The opt-in command is:

```bash
CINEMARR_VIDEO_CLIENT_GATE=true \
CINEMARR_LIVE_PLEX_GATE=true \
CINEMARR_PLEX_URL='http://plex.example.invalid:32400' \
CINEMARR_PLEX_TOKEN='...' \
CINEMARR_LIVE_VIDEO_SECTION_ID='1' \
./scripts/run-dedicated-server-gate.sh 1.21.1-neoforge
```

Fake-Plex gates are regression evidence only. Publication must use the final artifact hashes tied to this completed checklist.

### Isolated scaled controller checks

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

The disconnect-corrected modern real-Plex controllers are readable and their
controls do not overlap, but direct review still shows a retained `Buffering`
notice beside the `playing` header. Code inspection also found that modern
Play/Queue can overwrite local permission-denial feedback, stream selection
bypasses that local UI guard, and legacy has no equivalent local notice guard.
Server permission enforcement remains separate and was not shown to be bypassed.
Generic server errors currently go only to chat rather than the open controller.
These feedback paths require correction and real widget-click checks before
final UI acceptance. The Java-8 feedback helper is now integrated across all four screens, with six
passing unit tests for state, expiry, dispatch and denial. Actual widget checks
and changed-artifact acceptance remain pending.
Follow-up log review also identified the server side of the stale notice:
completed play/seek paths publish `Buffering` with a `PLAYING` snapshot and do
not promise a later periodic snapshot to clear it. Correcting only the client's
retained notice would therefore be insufficient; server messages must agree
with the completed operation's authoritative paused/playing state.

A separate synthetic-transport probe of the compiled candidate confirmed a
browse-page arithmetic boundary defect: page `2147483647` with page size 20
becomes upstream offset `-20`. The wire page is not range checked before the
integer multiplication. The current code rejects offsets outside the supported integer range
before issuing HTTP, and its boundary regression passed; ordinary page navigation remains
covered by the existing 41-item regression. Probe source is retained at
`build/controller-feedback-draft/BrowseOffsetProbe.java`; it used no real Plex
endpoint or credentials and is not evidence of a deployed correction.

### Reusing legacy probe saves

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

## Superseded September 6 real-Plex assertions

All four architectural representatives passed automated controller-selected
playback, common submitted-frame hashes, physical program-audio comparison, non-owner
rejection at 640x360, pause/resume, seek, alternate streams, follower reconnect,
final-TV teardown, and clean server stop. Final capture results are:

| Runtime | Audio correlation | Absolute separation |
| --- | --- | --- |
| Forge 1.7.10 | 0.979987 | 40 ms |
| Quilt 1.20.1 | 0.992439 | 10 ms |
| NeoForge 1.21.1 | 0.963522 | 0 ms |
| Fabric 26.2 | 0.994584 | 10 ms |

Screenshot inspection subsequently found the reconnected Quilt and 26.2
followers were obscured by the other avatar. These assertions are not accepted
as proof of visible two-client playback. A stable-camera candidate must replace
them, and both framebuffers must be reviewed.

Evidence is under `build/discopanel-real-plex/<runtime>/`; each
`<runtime>.remote-server.evidence.txt` records the exact deployed Cinemarr hash,
matching the September 6 reproducible bundle. Invocation results are
`build/clock-fix-gate/real-plex-<runtime>-final.log`. These results do not
substitute for the remaining full-runtime, global-secret-scan, or hosted gates.

Both Forge 1.7.10 and NeoForge 1.21.1 also passed restart at the 256-pixel
construction checkpoint, persisted unloaded-footprint recovery, and the final
air-block assertion. The legacy fixture needed a separate chunk preloader;
its preceding obstruction rollback is preserved as a failed invocation, not
counted as a pass. See the hardening-plan checkpoint for both evidence paths.
`build/clock-fix-gate/discopanel-restored-state-20260906.json` verifies that all
four shared servers are stopped, autostart is off, Cinemarr is disabled as it
was before testing, other mod settings are unchanged, and disabled rollbacks
remain available.

## Superseded September 1 candidate evidence

On 2026-09-01 UTC, the rebuilt working-tree bundle was indexed and inspected before
external recertification. Representative SHA-256 digests are
`c3c0845295a80b6359d0ba9335f4fcee04e6012d1651ad4ac7b64fd4dee0be21`
(Forge 1.7.10),
`bd7ae3e9a90dde58135a5464fcb206091448ec95eca8acd63dc0d6edf94665bf`
(Fabric/Quilt 1.20.1),
`480467db73f22ec26d86e750141beb994119587f1162a2ad897f8c5cc901ba4e`
(NeoForge 1.21.1), and
`88bca91acc34eafdfed7e8289cc33b4068726882c046fe4618a8e86068037b46`
(Fabric 26.2). DiscPanel downloaded and compared the active bytes, retained a
disabled hash-verified predecessor for each server, and kept every managed
server stopped with autostart disabled outside its gate.

Those exact four artifacts passed the credentialed disabled/degraded/manual
retry/automatic retry/redaction matrix. The exact four also passed real-Plex
controller selection, matching identifiable video and synchronized audible
output on two clients, controls, non-owner rejection, follower reconnect, and
clean teardown. Forge 1.7.10 and NeoForge 1.21.1 passed server restart during
Quick-TV construction plus persisted unloaded-footprint recovery. Evidence is
retained under `build/discopanel-real-plex/`,
`build/discopanel-plex-recovery/`, and `build/discopanel-lifecycle/`.

The retained Windows and ARM guests reused their installed operating systems
with the current decoder bundle and then powered off. Current evidence is under
`build/native-smoke/windows-x86_64/20260901T182751Z/` and
`build/native-smoke/linux-arm64/20260901T183204Z/`; the combined read-only
audit is `build/native-smoke/retained-vm-audit-20260901T184323Z.json`. A scan
using the live Plex, DiscPanel, and Proxmox credentials and the real Plex
endpoint found zero matches in source, final retained text evidence, or any of
the 16 release JARs. The cleanup helpers also redact the managed server and
hypervisor endpoints before evidence is retained. No live value is recorded in
the evidence.

This section certifies the working tree and indexed bytes, not an unpublished
commit. The release commit, final GitHub run, and downloaded hosted-bundle
parity check remain required.

The decisive real-Plex reruns caught two release-blocking issues after earlier
green matrices: legacy prefetch could not cover an eight-second Plex segment
plus cold decode and lacked a bounded 404 materialization window, while modern
OpenAL startup could queue PCM before late compensating silence and displace
two listeners by 1.32 seconds. The shared prefetch/fetch policy and pre-play
audio scheduling fixes are covered by focused tests. The current final-byte
Forge 1.7.10, Quilt 1.20.1, NeoForge 1.21.1, and Fabric 26.2 captures all
passed at 0 ms measured lag, with correlations from 0.969625 through 0.998832
and zero underruns.

The fresh manifest-derived runtime rerun completed all 21 profiles in 53
minutes 35 seconds. Every profile passed configuration rejection, real server
startup, protocol and command probes, two-client identifiable video,
synchronized audible output, real disconnect cleanup, native-crash scanning,
and strict process/port/display/audio residue checks.

## Historical regression evidence

GitHub Actions run `33398719853` passed 15 artifact/runtime jobs, including
deterministic rebuilds and uploads, but correctly failed 1.21.1 Fabric because
the two clients' highly correlated program audio was 330 ms apart. The
aggregate gate was skipped rather than masking the failure with a rerun.
Commit `21431bfb44c051b6fb9d02a080afe1355f05eb0b` fixes the OpenAL
source-start cursor that could move backward when processed streaming buffers
were unqueued. Its private-Xvfb 1.21.1 Fabric gate passed with matching
identifiable frames, zero underruns, 0.996978 correlation, and 80 ms lag; unit
tests and the nine initially exercised 1.21.1/26.1.2/26.2 artifact targets also
passed. Full local `verifyAllTargets` subsequently covered all 16 artifacts,
both Quilt build profiles, Forge 1.7.10, all ten GameTests, and release hygiene.

Run `33401793006` then passed manifest validation, every one of the 16
artifact/runtime jobs, byte-identical rebuilds, and the non-skipped aggregate
gate for `4f6541f42abf72ec649c6d435e1677259badff74`. The bounded 1.20.2
NeoForge asset warm-up used its permitted infrastructure retry; no product or
runtime gate was rerun. The downloaded candidate has exactly 16 JARs and valid
`SHA256SUMS`; all 18 bundle files match the local index generated from the
hosted inputs. Updated representative hashes are
`d36fd1799feff72df36f59df39c086765afb27c8e3e23f1554cb6215538e4565`
(Forge 1.7.10),
`80d0bdd2f0f381ba617dff05700f85d7ba114739abad5a51bb6b45abe7243fd4`
(Fabric/Quilt 1.20.1),
`0a32999d13c60a0f7dfc4f36e559baf4821c4bbd2f9fd79272b5c0347b5430da`
(NeoForge 1.21.1), and
`a76afb77d23d0174da54d609ab99567ea622ce0fa68fd8768d79e70d78b55a63`
(Fabric 26.2). This does not check the remaining exact-byte external or native
boxes, and the final documentation-only SHA still requires clean hosted CI.

On 2026-08-31, code commit `1cbd341b88023bffce340963f1890428f5be9712` passed all 16 local artifact verification targets, all 10 required GameTests, and the complete 21-runtime/two-client matrix using private Xvfb displays. Final Forge 1.7.10 and NeoForge 1.21.1 adverse-network runs passed slow delivery, transient recovery, bounded exhaustion, redacted failure, same-session recovery, and cleanup. GitHub Actions run `33392358275` passed manifest validation, all 16 artifact/runtime jobs, byte-identical rebuilds, and the non-skipped aggregate `1.0 release gate`. The hosted 16-JAR bundle passed deep inspection and `SHA256SUMS`, and `build/releases` was indexed from and compared byte-for-byte with that bundle. Exact representative hashes are `d36fd1799feff72df36f59df39c086765afb27c8e3e23f1554cb6215538e4565` (Forge 1.7.10), `05cb545a741223fdcbe2b8affc3b5c42e671cd6429a55e3d2500e6610bb48350` (Fabric/Quilt 1.20.1), `ab22be1f8cf1498556414859803ebee49acf6776d17a7adfc5fa5b5018944f92` (NeoForge 1.21.1), and `87587a77f2c8361b5ee049c2c33699934334a647617b084333834836c6e3a7f3` (Fabric 26.2).

The exact hosted bundle was staged for the four stopped, autostart-disabled DiscPanel representatives. The first three replacements retained disabled, hash-verified rollback copies. The 26.2 rollback upload returned HTTP 500, and the subsequent 1.7.10 acceptance preparation could not update server state after five retries, so no client GUI was opened and no exact-byte external box is checked. Commit `63e0150` makes rollback and canonical upload/import failures explicit fatal boundaries so that this partial state cannot be reported as a successful deployment again. Commit `2de12a7` additionally requires exactly one active Cinemarr artifact and a disabled rollback whose downloaded bytes match its recorded hash prefix even when the active candidate is already exact. Commit `501c9c6` preserves the original nonzero status through the rollback trap, disables a failed replacement, re-enables the verified backup, and verifies that backup is the only active Cinemarr artifact. The DiscPanel credential-holder process exited with the failed workflow; current remote state must be re-audited read-only before any retry. The candidate and previously native-certified artifacts contain the same decoder classes and all 618 identical native entries, but exact-candidate real-Plex, recovery, lifecycle, configured-secret, and packaged-native boxes remain unchecked.

The same day, a real-Plex Forge 1.7.10 diagnostic exposed and then verified the fix for a multi-window video-transfer deadlock. Both clients rendered matching program video with audible synchronized audio and passed controls, reconnect, and cleanup. The server used the preceding candidate during that diagnostic, so this is regression evidence only and does not check the decisive exact-byte boxes.

On 2026-08-30, the protocol-10 hardening working tree passed the complete 21-runtime manifest matrix with private per-client Xvfb displays, identifiable video, correlated audible output, controls, protocol/command probes, and residue-free teardown. The finalized Forge 1.7.10 and NeoForge 1.21.1 adverse-network profiles then passed sustained 100 ms-per-segment latency, transient 503 recovery, bounded exhaustion, redacted diagnostics, same-session recovery, and clean teardown. All 16 artifacts passed deep inspection and checksum verification; forced rebuild evidence was byte-identical, including the four Forge-family archives whose local extended timestamps were normalized. This is precommit regression evidence only and does not check the remaining real-Plex or pushed-SHA boxes.

On 2026-08-29, the deterministic fake-Plex gate passed all 21 maintained profiles with two clients, identifiable matching video, synchronized audible output, command-permission and protocol-mismatch probes, software decoding, and clean process/port teardown. The canonical build also passed all 10 required GameTests, rebuilt and inspected 16 artifacts, and regenerated matching SHA-256 manifests.

On 2026-08-30, the exact indexed and deployed artifacts for Forge 1.7.10, Quilt 1.20.1, NeoForge 1.21.1, and Fabric 26.2 passed controller-selected playback against a credentialed real Plex server. Each run produced matching identifiable program video and correlated audible output on two clients, reported zero video drops and audio underruns, drained its television and Plex stream state to baseline, restored server settings exactly, and left the managed servers stopped with autostart disabled. Representative Forge 1.7.10 and NeoForge 1.21.1 runs additionally passed pause/resume, seek, alternate audio/subtitle selection, follower disconnect/reconnect, final-TV teardown, and a 640x360 non-owner UI with zero clipped widgets and rejected control mutation. Forge 1.7.10 then completed three fresh full-control/reconnect runs on exact digest `37232612d83ad36729cdfaab3eb36c5736c18b0b86d021136e000c2a05b5495b`; the paired program-audio correlations were 0.759449, 0.780179, and 0.752659, all at 0 ms measured lag. Evidence is retained under `build/discopanel-real-plex/`.

The exact final Forge 1.7.10 and NeoForge 1.21.1 artifacts also passed real server-stop/restart during Quick TV construction, persisted an unloaded recovery footprint, rolled back loaded pixels first, completed rollback after the footprint chunks were loaded, and left the first generated pixel as air. Evidence is retained under `build/discopanel-lifecycle/`.

Credentialed recovery passed disabled, degraded, operator-retry, automatic-retry, and restored-ready behavior on the exact four representative artifacts under `build/discopanel-plex-recovery/`. Legacy Forge 1.7.10 and modern NeoForge 1.21.1 passed transient segment recovery, bounded exhaustion, redacted failure, and same-session recovery under `build/adverse-network/`. Native Linux ARM64 evidence is retained under `build/native-smoke/linux-arm64/20260830T125759Z/`; native Windows x86-64 evidence is retained under `build/native-smoke/windows-x86_64/20260830T174136Z/`. A scan using the actual configured credentials and endpoint found zero matches across tracked and untracked source plus retained runtime/release evidence. All 21 managed servers were stopped with autostart disabled, and every native-test manifest was clean.

These entries are historical evidence only. They must not be converted back into checked current-release claims until exact final candidate bytes have passed the corresponding gates. Tagging and publication remain separate, explicitly authorized release operations.
