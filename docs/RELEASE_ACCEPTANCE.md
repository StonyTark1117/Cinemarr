# Release acceptance

## Current certification boundary — 2026-09-08 UTC

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
- Subsequent edits affect only three build/manifest validation inputs, not
  product Java. Sixteen manifest regressions and the complete dependency dry
  run pass. Both `release-audit-r4-20260908` frozen builds passed (536 and 508
  seconds), with 87 executed tasks and ten GameTests each. All eighteen bundle
  files match each other and r3. The independent continuity receipt explicitly
  binds the updated source to these unchanged, previously accepted bytes.
- The configured-secret scan passed across 57,675 files and 927,188 distinct
  decoded payloads, with no matches or scan errors. Scoped commits, the complete
  local gate on the final commit, exact-SHA green GitHub CI and downloaded/local
  artifact parity remain required. No tag or publication is authorized.

## September 8 final review pass

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

## Current checkpoint: recorded checks passed; requirement audit still open (2026-09-07)

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
Accepted current-candidate cases:

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
