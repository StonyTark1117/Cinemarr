# Historical checkpoints retained on 2026-09-10

This is a verbatim historical snapshot. Statements such as “current,” “pending,”
and “passed” describe their original checkpoint and do not certify the latest
candidate. Consult [the active plan](1.0_RELEASE_HARDENING_PLAN.md) and
[current acceptance](RELEASE_ACCEPTANCE.md) for release status.

---

# Cinemarr 1.0 release hardening plan

## Current assessment

**1.0 prerelease under hardening — not a release candidate.** Current code,
uncommitted changes, and final release bytes are not certified by earlier
successful runs. The scope remains 16 artifacts and 21 runtime profiles; none
of the ten phases below is removed or narrowed.

## Current Plex lifecycle investigation — 2026-09-10 UTC

Proxmox, DiscPanel, existing Plex credentials and both retained native guests
are accessible. There is no missing-access blocker. Earlier successful builds,
native checks and runtime matrices retain their original source and byte scope;
they do not certify the current correction or remove any required final gate.

The V9 transfer correction passed two identical full builds and both standalone
native checks, then reproduced a transfer-window failure in the real-Plex legacy
case. Its six read-only snapshots exposed replay of already completed media and
a six-second client retry conflicting with a thirty-second server grant. V10
corrected grant retirement and same-generation manifest recovery. Its tests
passed, but its focused live run failed with active audio underruns and a Plex
HTTP 404, without transfer-window rejections. All 128 files from that failure
are retained byte-identically; all 33 original PNGs were directly reviewed. The
closed failed-runtime evidence passes its fourteen-value configured scan.

Plex had generated the next video segments before a Jammarr music request
expired Cinemarr's shared playback resource and killed the active transcoder.
A separate diagnostic using the actual V10 Cinemarr and installed Jammarr
production classes reproduced this sequence after repeated stream replacements:
the next video fetch returned HTTP 404 after Jammarr's successful audio download.
The failed production-class run and original Plex server log are retained.
Earlier approximate probes were inconclusive or failed to establish their
trigger; none supplies corrective acceptance.

The current V11 correction obtains a Plex transcode decision for each unique
playback-session identity and carries that identity through playlist, segment
and stop requests. It reports the authoritative playing/paused timeline every
ten seconds and on pause changes through a separate bounded worker queue.
Updates coalesce per stream, and stop serializes with an in-flight report so
queued heartbeats cannot revive retired playback. A failed final timeline still
stops the transcoder; a failed initial playlist releases its allocated session.
These lifecycle requirements are documented in the [official Plex PMS API](https://developer.plex.tv/pms/).

A new independent-stream regression fails against V10 and passes after the
identity correction. Current suites pass 222 core, 47 modern and 274 legacy
tests, with their existing one, two and two opt-in skips. Tests cover coalescing,
cadence, pause updates, rejected admission, queued-close ordering and failed
cleanup. The 843 non-Markdown source inputs are frozen, nine changed from V10.
The corrected legacy production artifact passes the previously failing live
production-class sequence: five starts, four replacements, fifteen successful
timeline reports, a successful Jammarr download and a successful subsequent
video fetch. The owned transcodes are absent after cleanup. This comparison
validates the combined lifecycle correction, not each individual change in
isolation. A fresh full two-client legacy real-Plex diagnostic also passed,
including follower reconnect, two resource/sound reloads per client and normal
shutdown. All 39 original PNGs were directly reviewed and strictly validated.
The retained post-reload eight-second physical audio pair scored 0.940593 in
the maintained comparison and 0.784292 in the independent check, both at zero
measured lag. All three closed client logs have zero active underruns; all four
servers and Plex session state are restored. The closed diagnostic evidence
passes its fourteen-value scan. This accepts the combined legacy correction.
Two complete sixteen-artifact builds also passed in 1379.879 and 1346.776
seconds: all sixteen JARs, the manifest and checksum file are byte-identical.
Each executed all ninety outer tasks and passed all ten required GameTests;
both raw GameTest logs and the 222/47/274 test totals were independently
reviewed. The build unit closed normally with no owned processes or recorded
cores. The first full bundle passes its fourteen-value deep scan, and its
legacy JAR exactly matches the accepted diagnostic. Current Windows x86-64
and emulated Linux ARM64 standalone native checks also pass: three fixtures
each, exact native payload parity across all sixteen artifacts, independently
verified cleanup and restored guest access. Both retained guests are stopped.
These checks certify software-decoder ABI/functionality, not full Minecraft
Windows/ARM playback or physical ARM performance. The fresh matrix has two
independently accepted lifecycle cases: all fifty original PNGs reviewed,
eight physical audio pairs synchronized within thirty milliseconds, ten
silence checks, normal client exits and clean diagnostics. Raw modern backend
exhaustion occurs only during final-input draining; active underruns stay zero.
Both closed native cases and both lifecycle cases pass fourteen-value scans.
The remaining thirty-five runtime cases, full-bundle real-Plex acceptance and
final-commit/hosted gates remain pending.

The fresh Jammarr review is pinned to `8eccf06e972689315932072dd42aab7c4ff315a8`.
Its duplicate acknowledgements, atomic retry bounds, stale-window filtering and
bounded background work informed this review. Its five-second client health
reports are separate from Plex timeline reports. Its released Plex service
fully downloads bounded MP3 files; the reviewed Plex service history contains
no heartbeat implementation to transplant into Cinemarr's long-lived HLS path.
No Jammarr source or deployed Jammarr artifact was changed.

All four designated servers are stopped with original configs/overrides and
Cinemarr disabled after the successful V11 diagnostic. The fresh 37-case matrix, all four
real-Plex profiles and supplements, both native checks, clean final-commit full
release gate, exact-SHA hosted CI and artifact parity remain required. The
subsequent fresh Jammarr target-feasibility review also remains required.

## Automatic screenshot publication — current correction, 2026-09-09 UTC

The 841-input candidate batch is stopped after eleven independently accepted
cases. Fabric 1.20.2 completed gameplay and normal client/server shutdown, but
its reconnected follower UI PNG contains 182 bytes after its first valid IEND.
The raw file is retained unchanged with SHA-256
`cdd4e3ebed3e71efe120fe259068a810ba4e853697ff957dbadadd4b48358a4f`.
This is a failed evidence-integrity gate, so that case is rejected. The following
Quilt 1.20.2 case was interrupted and 24 cases remain unattempted. All eleven
prior scoped acceptances, both forced builds and Windows native acceptance keep
their original source/byte identity; they do not certify this correction.

The installed Minecraft writer opens with WRITE, CREATE and TRUNCATE_EXISTING,
then writes asynchronously. Current logs show repeated UI captures targeting the
same file from multiple I/O workers. An isolated pair of overlapping truncating
writers reproduces the trailing-data failure. This supports concurrent writes
as the cause; lack of truncation is not the explanation. The gate had validated
private-window captures but omitted final validation of automatic client PNGs.

Automatic UI and world captures now write to distinct temporary files and
atomically replace the destination only after the encoder closes its output.
Publication failure retains the staging file and reports failure. The gate now
validates every profile PNG after client shutdown, requires both clients' world
and UI images, and rejects unpublished staging files without repairing evidence.
Three core publication tests, all 23 private-window tests, all six client-exit
tests and the family source-layout check pass. The new gate check rejects the
exact retained malformed PNG. A fresh full Fabric 1.20.2 corrective case passed
in 521 seconds and is independently accepted for its 843 unchanged source inputs.
All 33 original captures were validated and directly reviewed. A ten-second live
publication check also observed 175 complete PNG reads during 26 completed writes,
with twelve distinct images. The audible eight-second physical audio pair passes
at 0 ms measured lag (10 ms resolution) and correlation 0.986641. All five client
launches exit zero, both final clients acknowledge reset, and owned processes,
displays, sinks and ports close with no unit core. Closed logs contain no rejected
segment, nonzero underrun/starvation, broken pipe or screenshot publication
failure. Current optional audio/narrator, offline profile authentication and
client command-lookup diagnostics were reviewed. The whole closed-root limited
four-value scan passes 179 files and 2,251 decoded payloads, including its
intentional configuration crash and complete outer log. Initial chat overlays,
TV cropping, darkened UI backgrounds, temporary seek/stream reset/stale labels
and missing retained paused PCM remain explicit. This corrective case does not
count toward the fresh 37-case matrix. Both forced full builds are now
independently accepted on all 843 unchanged source inputs. They passed in
1,337 and 1,332 seconds, each with all 90 tasks executed, sixteen artifacts
inspected and all ten required GameTests passed. Both immutable snapshots and
the current sixteen-JAR/two-manifest bundle match byte-for-byte. The complete
original GameTest logs show ten tests passing in 1.195 and 1.046 seconds with
normal saved shutdowns. The build unit closed with no remaining owned processes
or recorded cores. Final suites contain 212 core, 44 modern and 261 legacy tests,
zero failures/errors, and only five explicit Plex/hardware opt-in exclusions.
The legacy suite also runs the three new shared screenshot tests. Those skips
do not provide real-Plex or experimental hardware acceptance. All 127 original
suite XML files are retained. The first complete bundle/log scan passes 24 files
and 18,344 decoded payloads for the four available private values; the independent
byte comparison binds both matching bundles to that scan. The second closed
log/review/XML scan passes 140 files. The full configured scan, fresh runtime
matrix, native/Plex acceptance and final-commit local/hosted gates remain pending.
No new release candidate is certified.

The refreshed Windows native run `20260909T174733Z` is independently accepted
for this 843-input candidate. All 281 uploaded and used files match the current
portable bundle; all 69 Windows native entries match all sixteen release JARs.
The 144p, 480p and 1080p software fixtures match their references exactly, with
18, 18 and 16 retained frames and 0, 0 and 2 matching bounded drops. Relative
video PTS and A/V drift deltas are zero; absolute A/V offsets are not claimed
to be zero. The complete console and actual unit logs were directly reviewed.
Both retained guests are ready and stopped, transient mounts/loops/NBD and the
run directory are gone, and local owned-unit core checks pass. The remote host
has no coredumpctl; its plain-core configuration, clean journal and absence of
retained core files are recorded. The final limited four-value scan passes all
24 native evidence/log/review files. This is standalone Windows software-decoder
ABI/functionality acceptance, not a full Minecraft or hardware-acceleration
claim. The fresh 37-case runtime matrix is active; ARM and real-Plex acceptance
remain pending.

The fresh matrix has two independently accepted terminal cases, with legacy
pressure active and 34 cases unattempted. All 32 legacy and 18 modern original
images were validated and directly reviewed. Five legacy physical audio pairs
pass at 10–30 ms lag; three modern pairs pass at -20–10 ms, with correlations
of at least 0.987. Terminal and world-away silence, continued legacy leader
audio, queue/restart motion, six legacy and five modern normal client exits,
and owned resource/core checks pass. All 843 source inputs remain unchanged.
The closed-root limited four-value scans pass 236 legacy and 203 modern files.

Legacy world-away chat contains acknowledgement/tracking rejection messages
after departure and zero-resource cleanup; same-process recovery, silence and
continued leader audio pass. These visible messages remain documented. Modern
raw backend starvation counts occur during final-input draining while active
underruns stay zero, as the existing terminal policy requires. An extra review
filter initially conflated these counters; source inspection and all ten existing
terminal regressions resolved that review error without a runtime rerun or gate
change. Initial toast overlays, TV cropping, dark UI backgrounds and absence of
continuous near-EOS physical PCM remain explicit limitations. Neither terminal
case supplies ordinary widget coverage or real-Plex acceptance.

Legacy pressure is also independently accepted, bringing the current matrix
to three of 37 cases, with modern pressure active and 33 unattempted. All 75
original images were directly reviewed, including both clients under browse
and segment pressure and after reconnect/reload. All five audible eight-second
physical pairs pass at -20–30 ms lag and correlations of 0.989554–0.999438.
The test peer remains silent. Raw evidence confirms 250 browse requests, 3,150
peer segment requests over 40 seconds, bounded queues/grants/egress and zero
unexpected peer errors. Four resource reloads preserve the session and restore
advancing video. All seven clients exit zero; owned displays, sinks, ports and
core checks pass. The limited four-value closed scan passes 278 files and 984
decoded payloads. Two fixture JSON broken pipes were directly reviewed in their
held-browse context; no client/server playback failure appears. Visible overload
chat, TV cropping, temporary seek/stream reset textures and missing retained
paused PCM remain explicit limitations. All 843 source inputs are unchanged.

Modern pressure is independently accepted as the fourth current matrix case;
legacy fault recovery is active and 32 cases are unattempted. All 69 originals
were directly reviewed. Both clients show advancing program video during
browse/segment pressure and recovery. Five audible eight-second physical pairs
pass at 0–10 ms lag, with correlations of 0.989949–0.999760; the network-only
peer stays silent. Raw counters confirm 254 browse requests and 3,090 peer
segment requests over 40 seconds, bounded work/egress/grants and no unexpected
peer errors. All six clients exit normally, both final media clients acknowledge
reset, and owned display/sink/port/core checks pass. The limited four-value
closed scan passes 296 files and 2,159 decoded payloads. Current optional audio
library diagnostics, two fixture JSON broken pipes and the intentional invalid
configuration crash were directly reviewed. No rejected segment, nonzero
underrun/starvation or screenshot publication failure appears. Initial toast
and overload-chat overlays, immediate stale UI labels/reset textures, separately
timed leader/follower UI captures and missing retained paused PCM are documented.
The candidate still has 843 unchanged source inputs; full certification remains
pending.


Legacy fault recovery is independently accepted as the fifth current matrix
case; modern fault recovery is active and 31 cases are unattempted. All 57
original captures were validated and directly reviewed. Both clients show
advancing program video after transient, slow and exhausted segment faults
and reconnect. Four audible eight-second physical pairs pass at 20 ms measured
lag, with correlations of 0.992530–0.999090. Raw requests and capture metadata
bind the three recovery generations to the same playback session. Two actual
resource/sound reloads per client are independently bound to fresh log hashes
and advancing frames; the final audio follows all recoveries and reloads.
All six clients exit normally, both final media clients acknowledge reset,
and owned display/sink/port/core checks pass. The whole closed-root limited
four-value scan passes 231 files and 941 decoded payloads. Current legacy
loader, update-check, optional Twitch/splash and intentional invalid-configuration
diagnostics were reviewed. A command-client connection reset coincides with
its normal shutdown; no playback segment rejection, nonzero underrun/starvation,
broken pipe or screenshot publication failure appears. Cropped TV edges,
immediate stale labels/reset textures, separately timed UI captures and missing
retained paused PCM remain explicit limitations. All 843 source inputs remain
unchanged. Full configured scanning and release certification remain pending.

Modern fault recovery is independently accepted as the sixth current matrix
case; main Forge 1.7.10 is active and 30 cases are unattempted. All 51 original
captures were validated and directly reviewed. Eighteen recovery frames show
advancing video on both clients, with raw fault attempts and frame hashes bound
to the same playback session. Four audible eight-second physical pairs pass at
0–10 ms lag, with correlations of 0.987584–0.999240. All five clients exit normally,
both final media clients acknowledge reset, and owned process/display/sink/port
and core checks pass. The limited four-value closed scan passes 241 files and
1,446 decoded payloads, including the retained intentional configuration crash.
Current optional ALSA/narrator diagnostics and interleaved healthy playback
lines were reviewed. No rejected segment, nonzero underrun/starvation, broken
pipe or screenshot publication failure appears. Initial unsigned-chat toasts,
cropped TV edges, immediate stale UI labels/reset textures, separately timed UI
captures and missing retained paused PCM remain explicit. No modern resource
reload claim is made. All 843 frozen source inputs remain unchanged; full
configured scanning, remaining acceptance and final local/hosted gates are pending.

Main Forge 1.7.10 is independently accepted as the seventh current matrix
case; Fabric 1.20.1 is active and 29 cases are unattempted. All 39 original
captures were validated and directly reviewed, including six advancing
post-reconnect world captures. The audible eight-second physical pair passes
at 20 ms lag and correlation 0.991467 after reconnect and two resource/sound
reloads per client. Four reload actions are independently bound to fresh raw
log hashes and advancing frames in the same session. The ten-second decoder
stall injection is present on both clients. All six client launches exit zero,
both final media clients acknowledge reset, and owned X processes, displays,
sinks and ports close with no recorded core. The limited four-value closed
scan passes 181 files and 903 decoded payloads. Current legacy loader/update,
optional splash/Twitch, intentional invalid-configuration and command-client
shutdown-reset diagnostics were reviewed. No rejected segment, nonzero
underrun/starvation, broken pipe or screenshot publication failure appears.
TV cropping, lower chat, temporary stale UI labels/reset textures, separately
timed final UI captures and missing retained paused PCM remain explicit.
All 843 frozen source inputs remain unchanged; release certification is pending.

Main Fabric 1.20.1 is independently accepted as the eighth current matrix
case; Quilt 1.20.1 is active and 28 cases are unattempted. All 33 original
captures were validated and directly reviewed. The audible eight-second
physical pair passes at 10 ms lag and correlation 0.993374. All five clients
exit zero, both final media clients acknowledge reset, and owned displays,
sinks, ports and core checks pass. The limited four-value closed scan passes
175 files and 2,232 decoded payloads. Current optional audio/narrator, offline
authentication, Mixin debug, client command-lookup and intentional configuration
crash diagnostics were reviewed. No rejected segment, nonzero underrun/starvation,
broken pipe or screenshot publication failure appears. Initial toast/chat,
TV cropping, immediate stale labels/seek reset textures and differently timed
leader/follower UI captures are recorded; no retained paused-PCM or modern
resource-reload claim is made. All 843 source inputs remain unchanged.

The stopped unit has no remaining processes or cores. All 67 recorded X
processes are gone, its three displays and audio sinks are absent, and all 42
game/RCON ports are available. All 2,833 runtime evidence objects were moved
byte-identically under
`build/release-resume-20260909/packet-origin-lifecycle/runtime-evidence/`.
The limited four-value scan passes over 2,594 regular files and 21,812 decoded
payloads, including the closed batch log. Twenty-four prior source files affected
by this correction were reconstructed by reversing only these edits, then
verified against every original frozen SHA before retention. The complete
configured/all-evidence scan and fresh reproducibility, full runtime matrix,
ARM/Plex acceptance and final-commit local/hosted gates remain required.

The main Quilt 1.20.1 case is independently accepted, bringing this candidate to
nine of 37 runtime cases. All 33 original PNGs were directly reviewed; the
audible eight-second physical pair measures 10 ms lag and 0.997450 correlation.
All five clients exit normally, both final media resets are recorded, and
owned processes, displays, sinks and ports close without a unit core. Closed
logs have no rejected segments or nonzero underrun/starvation counters. The
limited four-value scan passes 190 files and 3,089 decoded payloads. Forge
1.20.1 is running, with 27 later cases unattempted. Full configured-secret,
retained-ARM, real-Plex and final local/hosted release gates remain outstanding.

The main Forge 1.20.1 case is also independently accepted: ten of 37 cases now
pass. All 33 original captures were directly reviewed, and the audible
eight-second physical pair measures 10 ms lag with 0.999932 correlation. Five
normal client exits, final media resets, absent owned resources and no unit
core are verified. The vanilla rejection is the required missing-client probe;
other exceptions are the deliberate invalid configuration and optional
platform capability/narrator diagnostics. No active playback failures appear.
The limited four-value scan passes 178 files and 2,257 decoded payloads.
NeoForge 1.20.1 is active; 26 later cases remain unattempted.

The main NeoForge 1.20.1 case is independently accepted, bringing the current
matrix to eleven of 37 cases. All 33 original captures were directly reviewed.
The audible eight-second physical pair measures 10 ms lag and 0.994878
correlation; all five clients exit normally and release their media resources.
Owned displays, sinks and ports are absent, with no unit core or active
playback failure. The missing-client probe closes with an explicit NeoForge
requirement in the transcript and server log, using the maintained server-log
fallback. The limited four-value scan passes 179 files and 2,236 payloads.
Fabric 1.20.2 is active; 25 later cases remain unattempted.

The main Fabric 1.20.2 case is independently accepted for the 843-input
candidate, bringing the matrix to twelve of 37 cases. All 33 original PNGs
pass integrity validation and direct visual review, including the reconnected
follower UI that failed for the earlier candidate. The audible eight-second
physical pair measures -30 ms lag and 0.999994 correlation. All five clients
exit normally, both final media resets are recorded, and owned displays,
sinks and ports close without a unit core. Closed logs show no active
playback or screenshot-publication failure. The limited four-value scan
passes 177 files and 2,263 decoded payloads. Quilt 1.20.2 is active, with
24 later cases unattempted. Earlier rejected evidence remains preserved.

The main Quilt 1.20.2 case is independently accepted: thirteen of 37 cases
now pass for this candidate. All 33 original PNGs pass integrity and direct
visual review. Actual 250 ms audio-setup stalls are recorded for both clients;
the final audible eight-second pair passes at -10 ms measured lag and
0.993518 correlation. Five normal client exits, both final media resets and
absent owned displays, sinks and ports are verified, with no unit core or
active playback failure. The limited four-value scan passes 184 files and
2,253 decoded payloads. Forge 1.20.2 is active; 23 later cases are unattempted.

The main Forge 1.20.2 case is independently accepted: fourteen of 37 cases
now pass. All 33 original PNGs pass integrity and direct visual review. The
audible eight-second physical pair measures 0 ms lag at 10 ms resolution,
with 0.986215 correlation. Five normal client exits, both final media resets
and absent owned displays, sinks and ports are verified, with no unit core
or active playback failure. The required-client probe rejects an unmodified
client with a clear Forge requirement. The limited four-value scan passes
178 files and 2,235 decoded payloads. NeoForge 1.20.2 is active, with
22 later cases unattempted.

The current candidate now has fifteen independently accepted runtime cases, including main NeoForge 1.20.2. All 33 original captures passed structural and direct visual review; the eight-second physical audio pair measured 0 ms lag and 0.9998 correlation. Client teardown, scoped diagnostics and the available four-value secret scan passed, with all 843 source hashes unchanged. Main Fabric 1.21.1 is running; 21 later cases remain unattempted. The full release boundary remains pending.

The current candidate now has sixteen independently accepted runtime cases, including main Fabric 1.21.1. All 33 original captures were validated and directly reviewed; the eight-second audio pair measured 10 ms lag and 0.9972 correlation. Several immediate control screenshots precede visible state updates; the retained later sequence and authoritative transitions support the controls, with those capture limits recorded explicitly. Cleanup, scoped diagnostics and the available four-value secret scan passed; all 843 source hashes remain unchanged. Main Quilt 1.21.1 is running, with 20 later cases unattempted. The full release boundary remains pending.

The current candidate now has seventeen independently accepted runtime cases, including main Quilt 1.21.1. All 33 original captures were validated and directly reviewed; the eight-second audio pair measured 20 ms lag and 0.9964 correlation. Immediate-capture limitations are retained explicitly alongside the later images and authoritative control transitions. Cleanup, scoped diagnostics and the available four-value secret scan passed, and all 843 source hashes remain unchanged. Main Forge 1.21.1 is next in the running batch, with 19 later cases unattempted. The full release boundary remains pending.

The current candidate now has eighteen independently accepted runtime cases, including main Forge 1.21.1. All 33 original captures were validated and directly reviewed; the eight-second audio pair measured 0 ms lag and 0.9998 correlation. Immediate-capture timing limits and the vanilla probe’s server-log-only disconnect reason are recorded explicitly. Cleanup, scoped diagnostics and the available four-value secret scan passed, and all 843 source hashes remain unchanged. Main NeoForge 1.21.1 is running, with 18 later cases unattempted. The full release boundary remains pending.

The current candidate now has nineteen independently accepted runtime cases, including main NeoForge 1.21.1. All 33 original captures were validated and directly reviewed; the eight-second audio pair measured 20 ms lag and 0.9975 correlation. Immediate UI-capture timing limits remain explicit alongside later frames and authoritative transitions. Cleanup, scoped diagnostics and the available four-value secret scan passed, and all 843 source hashes remain unchanged. Main Fabric 26.1.2 is running, with 17 later cases unattempted. The full release boundary remains pending.

The current candidate now has twenty independently accepted runtime cases, including main Fabric26.1.2:33 original captures directly reviewed,8-second audible physical PCM lag−10ms/correlation0.9952281783625554,five clean client exits and no owned core. The paused television frame holds while background clouds move; the complete captures are not byte-identical. Current cursor-shape, offline-account, narrator and command-lookup diagnostics are classified in the retained review. All843 frozen inputs remain unchanged. Main Quilt26.1.2 has closed and awaits independent review; main Forge26.1.2 is active. Full configured-secret, designated ARM/Plex inputs and final release gates remain pending.

Main Quilt26.1.2 is independently accepted, bringing the current candidate to twenty-one of37 runtime cases. All33 original captures were directly reviewed; the8-second audible PCM pair has20ms lag and0.9986083077968949 correlation. Bothclients reset media at14:11:58, allfive client exits arezero, old privateX processes aregone, ports25651/26651 and owned sinks areclosed, and no owned core is recorded. Current offline-account, cursor, narrator, command lookup and negative-config diagnostics are reviewed. The closed-profile four-known-value scan passes200files/1407payloads/311982615bytes; the full configured scan remains pending. All843 source inputs remain unchanged. Main Forge26.1.2 is active; real-Plex/ARM inputs and final release gates remain pending.

Main Forge26.1.2 is independently accepted, bringing the current candidate to twenty-two of37 runtime cases. All33 original captures were directly viewed; the8-second audible PCM pair has10ms lag and0.9944745699497137 correlation. Bothclients reset media at14:27:05, allfive client exits arezero, old privateX processes aregone, ports25643/26643 and owned sinks areclosed, and no owned core is recorded. The retained review classifies a Log4j DebugFile formatting error during the optional Netty kqueue probe on Linux; raw console stacks, exact dependency bytecode and subsequent epoll connections substantiate the classification. The closed-profile four-known-value scan passes209files/2542payloads/192092868bytes. All843 source inputs remain unchanged. Main NeoForge26.1.2 is active; full configured-secret, real-Plex/ARM inputs and final release gates remain pending.

Main NeoForge 26.1.2 is independently accepted, bringing the current candidate to twenty-three of 37 runtime cases. All 33 original captures were directly viewed; the eight-second audible PCM pair has 40 ms lag and 0.9864225227736381 correlation. Both clients reset media at14:34:43, all five client exits are zero, old private X processes are gone, ports25644/26644 and owned sinks are closed, and no owned core is recorded. Current narrator, cursor, profile lookup and negative-config diagnostics are reviewed. The missing-client transcript explicitly requires NeoForge26.1.2.97. The closed-profile four-known-value scan passes219files/4153payloads/194006304bytes. All843 source inputs remain unchanged. Main Fabric26.2 is active; the full configured-secret scan, real-Plex/ARM inputs and final release gates remain pending.

Main Fabric26.2 is independently accepted, bringing the current candidate to twenty-four of37 runtime cases. All33 original captures were directly viewed; the eight-second audible PCM pair has−20ms lag and0.9896088054895419 correlation. Bothclients reset media at14:42:27, allfive client exits arezero, old privateX processes anddisplays90/91 aregone, ports25645/26645 and owned sinks areclosed, andno ownedcore isrecorded. Current offline-account, narrator, cursor, commandlookup andnegative-config diagnostics are reviewed. The closed-profile four-known-value scan passes192files/1392payloads/173122372bytes. All843 sourceinputs remainunchanged. MainQuilt26.2 isactive; full configured-secret scan, real-Plex/ARM inputs andfinal releasegates remainpending.

Quilt 26.2 is the twenty-fifth independently accepted case on the unchanged 843-input atomic-screenshot candidate: all 33 original captures reviewed, eight seconds of audible two-client PCM at 30 ms lag / 0.997728 correlation, five normal client exits, final media resets, no owned core, and closed case resources. The paused TV counter holds while clouds move; immediate stream-change captures retain the documented rendering-timing limitation. Full current diagnostic chains and the intentional invalid-config crash were reviewed. The limited four-value evidence scan passed (200 files, 1,457 payloads, 315,201,817 bytes); the complete configured-value scan remains pending. Forge 26.2 has closed and awaits independent review; remaining matrix and external acceptance are still outstanding.

Forge 26.2 is the twenty-sixth independently accepted case on the unchanged 843-input atomic-screenshot candidate: all 33 original captures reviewed, eight seconds of audible two-client PCM at 20 ms lag / 0.999314 correlation, five normal client exits, final media resets, and closed resources with no owned core. Current Netty 4.2.15 / Log4j 2.26.0 optional KQueue diagnostic formatting failures were classified from full client/server stacks, exact installed bytecode and subsequent successful Epoll protocol-10 traffic; the DebugFile formatting limitation is retained. The limited four-value evidence scan passed (203 files, 2,064 payloads, 185,840,893 bytes). NeoForge 26.2 and the remaining compatibility cases continue; real-Plex/ARM, full configured-value scanning, clean-commit final gate and exact-SHA CI acceptance remain outstanding.

NeoForge 26.2 is the twenty-seventh independently accepted case on the unchanged 843-input atomic-screenshot candidate, completing review of all 21 main fake-Plex runtime profiles plus the six adverse/lifecycle cases. Its 33 original captures, eight seconds of audible two-client PCM (20 ms lag / 0.987326 correlation), five normal exits, final media resets and closed resources passed review. Current diagnostic chains and the intentional invalid-config crash were classified; no active underrun/starvation or rejected segment occurred. The limited four-value scan passed (216 files, 3,997 payloads, 189,761,864 bytes). Five Quilt Mod Menu A/V cases and five minimum-Fabric startup cases remain, alongside real-Plex/ARM, complete configured-value scanning and final exact-commit release/CI acceptance.

Quilt 1.20.1 with Mod Menu is the twenty-eighth independently accepted case on the unchanged 843-input candidate. All five client launches loaded the expected Mod Menu 7.2.2 JAR, verified against its metadata and maintained version catalog. All 33 original captures, eight seconds of audible two-client PCM (10 ms lag / 0.995688 correlation), five normal exits, final media resets and closed resources passed review. The limited four-value scan passed (188 files, 2,261 payloads, 316,835,353 bytes). Four further Quilt Mod Menu A/V cases, five minimum-Fabric startup cases, real-Plex/ARM, full configured-value scanning and final exact-commit release/CI acceptance remain outstanding.

Quilt 1.20.2 with Mod Menu is the twenty-ninth independently accepted case on the unchanged 843-input candidate. All five client launches loaded the expected Mod Menu 8.0.1 JAR, verified against its metadata and maintained version catalog. All 33 original captures, eight seconds of audible two-client PCM (10 ms lag / 0.996175 correlation), five normal exits, final media resets and closed resources passed review. The limited four-value scan passed (188 files, 2,243 payloads, 306,240,415 bytes). Three further Quilt Mod Menu A/V cases, five minimum-Fabric startup cases, real-Plex/ARM, full configured-value scanning and final exact-commit release/CI acceptance remain outstanding.

The current 843-input candidate now has **31 of 37 runtime cases independently accepted**. Quilt with Mod Menu 1.21.1 (11.0.4) and 26.1.2 (18.0.0) each passed all 33 original-image reviews, eight-second audible PCM (0/20 ms lag, 0.996379/0.987929 correlation), five normal client exits, closed-case cleanup/core review, and strict playback diagnostics. Immediate UI capture timing limits and optional narrator, account, ALSA and cursor diagnostics remain documented in their retained reviews. Review-helper fixes account for abbreviated home paths and display reuse by later Mod Menu cases; no runtime rerun or product source change was needed. Limited four-value scans passed for both cases; the full configured-value scan, remaining compatibility cases, designated real-Plex/ARM inputs and checks, final clean-commit gate, exact-SHA CI/parity, and subsequent Jammarr feasibility remain outstanding.

The **complete 37-case runtime matrix is independently accepted** for the current 843-input candidate. The original batch finished successfully at 2026-09-09T23:08:04.651461Z after 5h20m32s of Gradle runtime work. This includes 21 main profiles, six adverse/lifecycle cases, five Quilt-with-Mod-Menu A/V cases, and five Fabric0.19.2 dedicated-server startup-only cases. Cross-review revalidated all 1,166 directly reviewed original PNGs and 52 eight-second physical PCM pairs; measured lag is at most40ms and correlation at least0.986215. All166 recorded private X processes are gone, all42game/RCON ports are free, owned sinks are absent, and no owned core dumps were recorded. The closed runtime evidence scan passed across6,632files/69,056decoded payloads/7,577,206,977bytes for the four available private values. This remains a limited scan until all designated inputs are provided. Quilt26.2 startup chunk-loading churn before its first rendered frame, immediate UI capture timing limits, optional environment diagnostics and reviewer-only retention fixes remain recorded. Real-Plex/retained-ARM acceptance, full configured-value scanning, final clean-commit gate, exact-SHA hosted CI/parity and subsequent Jammarr feasibility are still required; no release-ready claim follows from this matrix checkpoint.

## Queued packet origin — preceding candidate, 2026-09-09 UTC

A deterministic isolated Java regression exposed a remaining Forge/NeoForge
boundary defect: a packet queued by an old connection could reach client state
after a replacement connection joined. The adapter checked the current live
connection but discarded the packet's origin. The probe compiled the actual
bridge and lifecycle with the exact adapter bodies and a mocked Minecraft and
loader context; this is a source regression, not an observed game failure.

The preceding 840-input batch was deliberately stopped. Its two terminal cases
remain independently accepted for their original inputs. Legacy pressure passed
automation but its 75 images remain unreviewed; modern pressure was interrupted,
and 33 cases were unattempted. Cleanup confirms no owned processes, displays,
sinks or cores and all 42 game/RCON ports available. All 903 runtime evidence
objects were moved byte-identically into
`build/release-resume-20260909/disconnect-tick-lifecycle/runtime-evidence/`.
The limited four-value scan passes over 834 regular files and 4,632 decoded
payloads, including the closed batch log; the complete configured scan remains
open. Earlier reproducibility and Windows receipts keep their original identity.

All ten Forge/NeoForge packet adapters now preserve the originating connection.
The shared bridge checks its eligibility at delivery time, and each client also
requires it to be the current live transport. Fabric retains its existing
origin-handler guard. The exact isolated ordering probe now passes; four new
maintained tests cover replacement, immediate disconnect, missing origin and
prevalidated adapter delivery. All four tests passed. The separate NeoForge
corrective case then passed in 501 seconds with 841 source inputs unchanged.
All 33 original captures were directly reviewed; physical audio passes over
eight seconds at 10 ms lag and correlation 0.992619. All five clients exit zero,
both final media clients acknowledge cleanup, and owned displays, sinks, ports
and processes close with no recorded unit core. The closed whole-root limited
four-value scan passes over 192 files and 1,428 decoded payloads, including the
retained intentional configuration crash. No rejected segment, nonzero active
underrun/starvation or broken pipe appears in the closed playback logs.

This corrective acceptance retains explicit immediate-UI-state, transient reset
texture, initial chat-overlay and missing retained paused-PCM limitations.
It does not claim real-game injection of the isolated delayed-packet regression
or count toward the full matrix. The first forced build stopped after 571
seconds and 37 executed outer tasks: NeoForge 1.20.2 supplies its packet context
directly, while the adapter incorrectly used the older supplier-style `get()`.
The failed source, logs and launch receipts are retained under
`packet-origin-lifecycle/failed-build-attempt-1/`. No second build, candidate
snapshot or GameTest run was reached; its unit has no remaining members or cores.

That one adapter now invokes `getNetworkManager()` directly. The installed
NeoForge callback interface confirms this signature, and the focused release
check passes in 53 seconds. Only this adapter differs from the accepted
NeoForge 1.21.1 corrective source; its shared/root inputs and original receipt
remain unchanged. The first build of the fresh pair passed in 1,324 seconds,
executing all 90 outer tasks, passing all ten required GameTests and inspecting
all sixteen release artifacts. Independent hashing verifies its saved sixteen
JARs and two manifests. The entire original GameTest log was directly reviewed:
all ten required tests pass in 1.114 seconds, followed by normal shutdown and
world saves. Its exact completion lines match the closed outer build log.
All 841 candidate source inputs remain unchanged. The second forced build
passed in 1,330 seconds, again executing all 90 outer tasks, passing all ten
required GameTests and inspecting all sixteen artifacts. Its entire original
GameTest log was directly reviewed: ten required tests pass in 1.085 seconds,
followed by normal shutdown and world saves. Both raw logs match their exact
timestamped completion lines in the corresponding full-build logs.

Direct byte comparison confirms all sixteen JARs and both manifests match across
both snapshots and the current release directory. The latest core, modern and
legacy suites contain 209, 44 and 258 tests, with no failures/errors and one, two
and two skips. The completed build unit's recorded invocation and journal
confirm clean completion, with no remaining members or recorded cores.
Reproducibility is independently accepted for this candidate. The limited
four-value scan passes across its eighteen saved files and 18,322 decoded
payloads; byte identity binds that scan to the identical current release files.

The portable native bundle was refreshed and verified against the current
source-set outputs: all 268 class/resource files match, and its main classes and
core JAR match the candidate artifact. The core was already current at preflight;
this refresh also covers the changed main classes and added test classes. The
full 37-case runtime matrix is active. Its legacy terminal case is independently
accepted after direct review of all 32 original images, five eight-second audio
pairs (10–20 ms lag, correlations 0.987240–0.999884), six quiet captures and two
leader-continuity captures during follower world changes. All six clients exit
zero; their X processes, audio sinks and ports close, with no recorded unit core.
The closed logs contain no rejected segment or nonzero underrun/starvation.
A command-probe connection reset coincides with its normal zero-exit shutdown;
current legacy discovery/version-check warnings were independently classified.
The limited four-value scan passes across all 234 case files and 952 decoded
payloads.

Modern terminal is also independently accepted after direct review of all
eighteen originals, three eight-second audio pairs (10–40 ms lag, correlations
0.984724–0.992427), four quiet captures and five actual zero client exits. Both
final media clients acknowledge reset before closing. Closed logs contain no
rejected segment or nonzero active underrun/starvation. Twenty-one duplicated
raw timeline entries record starvation within the final three seconds of the
900-second item; that end-of-stream observation remains explicit. Current
startup ALSA/narrator warnings and deliberate disconnect probes were reviewed.
The limited scan passes all 203 case files and 1,356 decoded payloads, including
the retained intentional configuration crash and closed batch section. Both
terminal cases are accepted.

Legacy pressure is independently accepted after all 75 originals were directly
reviewed. Five eight-second audio pairs pass at 30–40 ms lag, with correlations
0.986558–0.999962; the network-only peer remains silent. Raw logs confirm 251
browse requests over 40.45 seconds and 3,270 segment requests over 40.01 seconds,
with 214 chunks, 3,459,952 bytes, 107 acknowledgements and no unexpected peer
errors. Queue and egress bounds, fresh browse recovery and peer cleanup pass.
All seven clients exit zero, both clients complete two resource/sound reloads,
and post-reconnect video advances. Owned X processes, sinks and ports close,
with no recorded unit core or rejected segment/nonzero underrun/starvation.
Two fixture broken pipes occur in JSON response writes during browse release;
current source and request logs support abandoned metadata responses, without
an exact source-port-to-request mapping. Visible queue-full chat, cropped TV
edges, immediate seek/stream UI states and unretained paused PCM remain explicit
limitations. The limited four-value scan passes across all 278 case files and
988 decoded payloads, including the closed batch section.

Modern pressure is independently accepted after direct review of all 69
originals. Five eight-second audio pairs pass with 10–20 ms lag and correlations
0.984357–0.997538; the network-only peer remains silent. Raw logs confirm 254
browse requests over 40.43 seconds and 3,140 segment requests over 40.06 seconds,
with 328 chunks, 5,303,104 bytes and 164 acknowledgements. Queue/egress bounds,
fresh recovery and peer cleanup pass. All six clients exit zero; both final
media clients complete reset, and owned X processes, sinks and ports close,
with no recorded unit core. Closed logs contain no rejected segment or nonzero
underrun/starvation. Two fixture JSON broken pipes during browse release are
consistent with abandoned metadata responses; individual request mapping is
not retained. Cropped TV edges, initial chat toasts, browse queue-full chat,
immediate stale UI/reset texture and unretained paused PCM remain explicit.
The limited four-value scan passes all 292 case files and 1,470 decoded payloads,
including the intentional configuration crash and closed batch section.

Legacy fault recovery is independently accepted after all 57 originals were
directly reviewed. Four eight-second audio pairs pass with −30 to +10 ms lag
and correlations 0.986542–0.999944. All 24 recovery/post-reconnect world captures
advance. Raw requests confirm 165 transient-phase segment attempts, 51 slow
responses and six outage attempts; generation 11 advances through 12, 13 and
14 within one session, with online requests restored. All eighteen fault-frame
receipts match the actual generation logs. Both clients perform two resource
reloads and acknowledge final reset; all six clients exit zero. Owned displays,
sinks and ports close, and no unit core, rejected segment, nonzero
underrun/starvation or broken pipe is found. Current legacy discovery/version
warnings and intentional redacted HTTP 503/configuration failures were reviewed.
The limited scan passes all 233 case files and 1,081 decoded payloads.

Modern fault recovery is independently accepted after all 51 originals were
directly reviewed. Four audible eight-second audio pairs measure 0 ms lag at
10 ms analysis resolution, with correlations 0.991640–0.999666. All eighteen
fault-recovery world captures advance. Raw requests confirm 155 transient-phase
segment attempts, 52 slow responses and six outage attempts, with generation
11 advancing through 12, 13 and 14 in the same session. Restored online requests
and every recovery frame match the raw records. Both final clients acknowledge
reset, all five clients exit zero, and owned displays, sinks and ports close.
No unit core, rejected segment, nonzero underrun/starvation or broken pipe is
found. Current optional ALSA/narrator warnings and intentional sanitized HTTP
503 and configuration failures were directly reviewed. The limited four-value
scan passes all 241 case files and 1,427 decoded payloads, including the retained
configuration crash and closed batch log. Immediate UI, crop/toast and paused-PCM
limitations remain explicit. All six special runtime cases are accepted.

The main Forge 1.7.10 profile is independently accepted after direct review of
all 39 originals and an audible eight-second audio pair: 0 ms measured lag at
10 ms resolution, correlation 0.990673. All six post-reconnect/reload world
captures advance, and four reload actions match ordered raw sound events and
before/after frames within the same session. Each client has three deliberate
ten-second decoder stalls across its recorded lifetimes. All six clients exit
zero, final media resets complete, and owned displays, sinks and ports close.
No unit core, rejected segment, nonzero underrun/starvation or broken pipe is
found. A command-probe connection reset matches its normal shutdown before the
media clients joined. Current legacy startup and negative-configuration stacks
were directly reviewed. The limited four-value scan passes all 181 closed
profile files and 874 decoded payloads; the shared live fixture log awaits the
final batch scan.

Main Fabric 1.20.1 is independently accepted after all 33 originals were directly
reviewed. Its audible eight-second audio pair passes at 10 ms lag and correlation
0.999409. Both final clients acknowledge media reset, all five clients exit zero,
and owned displays, audio sinks and ports close, with no unit core. Closed logs
contain no rejected segment, nonzero underrun/starvation or broken pipe. Current
optional audio/narrator, offline test-profile authentication and Fabric Mixin
startup messages were reviewed. A client command lookup syntax message is
immediately followed by the successful server diagnostics response. The limited
four-value scan passes all 176 profile files and 2,245 decoded payloads, including
the retained intentional configuration crash. Initial chat toasts, cropped TV
edges, temporary seek/stream reset textures and missing retained paused PCM
remain explicit.

Main Quilt 1.20.1 is independently accepted after direct review of all 33
originals. Its audible eight-second audio pair passes at 0 ms measured lag
(10 ms resolution) and correlation 0.999234. Both final clients complete media
reset, all five launches exit zero, and owned X processes, sinks and ports close
with no recorded unit core. Closed logs contain no rejected segment, nonzero
underrun/starvation or broken pipe. Current optional audio/narrator, offline
profile authentication, Mixin startup and client command-lookup messages were
reviewed; the command lookup is followed by successful server diagnostics.
The limited four-value scan passes all 187 profile files and 2,517 decoded
payloads, including the retained intentional configuration crash. Initial toast,
TV cropping, temporary seek/stream reset texture and unretained paused PCM
limitations remain.

Main Forge 1.20.1 is independently accepted after direct review of all 33
originals. Its audible eight-second audio pair passes at 10 ms lag and
correlation 0.988480. The paused world frame is identical across three seconds;
initial pause/seek/stream reset textures recover in subsequent captures. Both
final clients complete media reset, all five launches exit zero, and owned
processes, sinks and ports close with no unit core. Its former display numbers
are now used by the next owned case. Closed logs contain no rejected segment,
nonzero underrun/starvation or broken pipe. Current optional audio/narrator,
OpenAL priority and Netty reflective-access startup diagnostics were reviewed;
the vanilla-connection rejection belongs to the intentional protocol probe.
The limited four-value scan passes all 178 profile files and 2,337 decoded
payloads, including the retained intentional configuration crash. Initial toast,
TV cropping and missing retained paused PCM remain explicit.

Main NeoForge 1.20.1 is independently accepted after direct review of all 33
originals. Its audible eight-second audio pair passes at 10 ms lag and
correlation 0.999177. Both final media clients complete reset, all five launches
exit zero, and owned X processes, displays, sinks and ports close with no unit
core. Closed logs contain no rejected segment, nonzero underrun/starvation or
broken pipe. Current optional audio/narrator, OpenAL priority and Netty startup
messages and the intentional vanilla rejection were independently reviewed.
The limited four-value scan passes all 179 profile files and 2,243 decoded
payloads, including the retained intentional configuration crash. Paused world
frames are identical; temporary seek/stream reset and stale stream label recover
in later captures. Initial toast/chat, TV cropping and missing paused PCM remain
explicit. Those eleven scoped acceptances retain their 841-input identity.
The later automatic-screenshot integrity failure stopped this batch as described
above; it does not establish acceptance of the corrected source.

Windows native run `20260909T143334Z` is independently accepted for standalone
Windows 11 x86-64 software decoding. All 281 uploaded/used inputs match the
current portable bundle; all 69 native entries match all sixteen candidate JARs.
The three fixtures pass with SSIM 1 and zero relative PTS/drift differences;
1080p retains 16 frames and drops two, exactly matching its reference. The full
console was directly reviewed. Both retained guests are ready and stopped;
transient work directories, mounts, loop devices and NBD are cleaned up, with
no local unit core or remote journal/core-file failure. Remote coredumpctl is
unavailable and that limitation is recorded. The final limited four-value scan
passes across all 24 evidence/log/review files. This certifies standalone native
ABI/functionality, not a full Windows Minecraft client or hardware acceleration.
The candidate remains uncertified pending the remaining matrix, ARM/Plex
acceptance, the complete configured scan and final-commit local/hosted gates.

## Disconnect crash — 2026-09-09 UTC

The 840-input runtime batch is stopped with eleven scoped acceptances, one
failed case (main Fabric 1.20.2), one interrupted case (main Quilt 1.20.2) and
24 unattempted cases. During final teardown, the reconnected Fabric follower
continues media ticks after the network-thread disconnect notification. A
render tick polls a deferred segment after the transport detaches and throws
`IllegalStateException: Cannot send packets when not in game!`. The client
exits nonzero without the final media-reset acknowledgement; a teardown active
underrun is also recorded. This case is rejected and its images are not accepted.

The gate's cleanup handler stopped the batch. All 70 recorded private X
processes and their sockets are gone, owned sinks are absent, all 42 manifest
game/RCON ports are available, and no unit core is recorded. All 840 source
inputs were unchanged. The 2,925 evidence objects were moved byte-identically
to `build/release-resume-20260909/png-capture-integrity/runtime-evidence/`, with
original-path mappings retained. The limited four-value scan passes across
2,676 files and 23,726 decoded payloads, including the closed batch log and
retained expected/unexpected crash reports. This is not the complete configured
secret scan. A lifecycle correction and fresh candidate validation remain open;
earlier build and acceptance receipts retain their original source identity.

The correction now invalidates connection eligibility as soon as disconnect is
reported. Before each modern client media tick, stale state is reset on the
render thread even if Minecraft has not drained the queued disconnect task.
Cancelled queued joins cannot send a delayed hello. All fifteen modern clients
use the tick guard and reject payload delivery while disconnected; Fabric also
checks the originating play handler at its queued packet boundary. Eight
lifecycle tests pass, including a delayed-join regression that first failed
against the old implementation. All 209 core tests have no failures/errors
(one skip), ten JOIN-order fixtures and six client-exit tests pass, and the
Fabric 1.20.2 release build passes. At this focused checkpoint the later
Forge/NeoForge payload guards had not been built; full validation follows below.

The fresh Fabric 1.20.2 corrective case passed in 521 seconds with unchanged
840-input source. All 33 original captures were directly reviewed; the complete
non-owner capture matches its immutable initial widget image. Physical audio
passes over eight seconds at 20 ms lag and correlation 0.987146. All five
clients exit zero, both final clients acknowledge render-thread teardown cleanup,
and all owned displays, sinks and ports close without a recorded unit core.
Closed logs have no rejected segment or nonzero active underrun/starvation.
The limited four-value scan passes across all 178 corrective-root/log files,
including the retained intentional invalid-configuration crash. Initial chat
overlays, stale immediate controller states, transient reset textures and the
missing retained pause-PCM limitation remain explicit in the review.

Evidence is under `disconnect-tick-lifecycle/`. This separate corrective case
does not count toward the new candidate's required 37-case matrix.

The first forced build then failed in 207 seconds at Forge 1.20.1 compilation:
the new payload guard referenced an instance field from a static registration
method. All 27 executed outer tasks and the failed build/source logs are
retained under `disconnect-tick-lifecycle/failed-build-attempt-1/`. No second
build or candidate snapshot was created; GameTests had not been reached.
The stopped build unit has no remaining members or recorded cores. The limited
four-value scan passes across all five retained attempt files.

All seven static Forge/NeoForge registration methods now qualify their existing
singleton. The three constructor-based NeoForge registrations retain instance
references. Forge 1.20.1's focused release build passes in 74 seconds. The only
source differences from the corrective Fabric runtime are those seven isolated
registration qualifiers; its shared/Fabric inputs are unchanged, and its runtime
receipt keeps its original source identity. The first build of the fresh pair
passed in 1,334 seconds: all 90 outer tasks executed, all ten required GameTests
passed, and all sixteen release artifacts were inspected. Independent hashing
verifies all sixteen saved JARs and both manifests. The complete raw GameTest
log is retained and bound to its exact timestamped completion lines. All 840
candidate inputs remained unchanged. The second forced build passed in
1,322 seconds, again executing all 90 outer tasks, passing all ten required
GameTests and inspecting all sixteen artifacts. Both complete raw GameTest
logs match their exact timestamped completion lines. Direct byte comparison
confirms all sixteen JARs and both manifests match across both snapshots and
the current release directory. All 840 inputs remain unchanged. The latest
core, modern and legacy suites contain 209, 40 and 258 tests, with no failures
or errors and one, two and two skips. The completed transient build unit is
unloaded; its recorded invocation and completion journal match the successful
launcher, with no remaining members or recorded cores. Reproducibility is
independently accepted for this candidate.

The limited four-value scan passes across all 18 candidate files and 18,322
decoded payloads, all 840 frozen source files and the eleven current Markdown
files. It remains narrower than the required complete configured-value scan.
Before the Windows run, preflight found that the generated portable benchmark
bundle still held the prior candidate's core JAR. The bundle was refreshed
successfully in eleven seconds. Its 266 class/resource files exactly match
current compiled outputs; its main classes and core JAR match the verified
release artifact. Release hashes and source inputs remain unchanged. No VM run
used the stale portable input.

The fresh 37-case runtime matrix has two independently scoped acceptances.
Legacy terminal passed direct review of all 32 original images, five audible
eight-second PCM pairs (20–50 ms absolute lag; correlations 0.980937–0.999996),
six quiet checks and six actual zero client exits. Both clients visibly advance
after both follower world returns, queue advancement and restart; the away
captures show the Nether without a television. Close camera crop and sequential
capture timing remain explicit limits. Owned displays, sinks and ports are
closed, with no recorded unit core, rejected segment or nonzero active
underrun/starvation. Display 90 was reused by the following owned modern probe,
verified through its new PID, cgroup and launch log. The limited four-value scan
passes all 236 closed case files, including the retained batch section.
A command-probe connection reset follows successful diagnostics and its normal
zero exit; legacy module-info discovery and optional startup warnings are
classified in the review.

Modern terminal is independently accepted after direct review of all eighteen
original captures, three audible eight-second PCM pairs (0–20 ms lag;
correlations 0.986239–0.996730), four quiet captures and five actual zero exits.
Both clients visibly advance after queue advancement and restart. Initial
unsigned-chat overlays and camera crop remain explicit limits. Nine raw console
starvation rows occur only within the final three seconds of the fifteen-minute
fixture, with empty Java/pending/decoded queues and zero active underruns; all
21 copies across nested logs were checked. No rejected segment or unit core is
recorded. Owned displays, sinks and ports close; display 90's reuse by the next
owned probe is verified. The limited scan passes all 203 case files, including
the retained intentional invalid-config crash and closed batch section.
Two of the 37 current cases are accepted. Legacy pressure is active and
34 cases are unattempted.

Windows native run `20260909T123353Z` is independently accepted for standalone
software-decoder compatibility. All 279 uploaded and used input files match the
refreshed bundle, and all 69 native entries match all sixteen candidate JARs.
The 144p, 480p and 1080p fixtures match reference images, PTS and relative drift;
the two bounded 1080p retention drops also match the reference. Both retained
guests remain installed, ready and powered off. Independent local/remote checks
confirm cleaned task directories, mounts, loop/NBD devices and no recorded
local core or remote journal/core-file failure. The final limited scan passes
all 22 native evidence/log/review files. This does not certify a full Minecraft
client or hardware acceleration on Windows. Real-Plex, ARM, complete configured
scanning and final-commit local/hosted acceptance remain open.

## Empty screenshot evidence defect — 2026-09-09 UTC

The 839-input candidate's batch is stopped after independent review rejected
main Fabric 1.20.1: its required non-owner small-window PNG is zero bytes,
despite an automated pass. The original is preserved without replacement.
Eight cases passed automation; seven were independently scoped accepted,
one is rejected, the following Quilt 1.20.1 case is interrupted and 28 were
unattempted. The stopped unit has no remaining processes or recorded core
dumps; all 47 recorded private X processes, their sockets and owned audio sinks
are gone, and all 42 manifest game/RCON ports are available.

The gate checked a live client screenshot early, then copied it much later
while the client could be rewriting it. The corrected harness retains the
observer's completed initial non-owner capture before owner commands begin.
Capture and retention now validate bounded PNG structure, chunk checksums and
complete decompressed scanlines; invalid captures fail and retained paths
cannot be overwritten. The focused regression first reproduced three failures.
All 19 focused tests, five real private-X test methods (including six actual
captures across both supported geometries), and all 23 tooling tasks now pass.
The integrity check of the stopped batch found 373 valid PNGs and this one
empty original. Integrity checks do not replace direct visual review.

The failure and original 839-input build/runtime receipts remain under
`build/release-resume-20260909/legacy-decode-retirement/`. The seven scoped
runtime acceptances and Windows acceptance retain that source identity.
The harness correction is under `png-capture-integrity/`; its corrective
acceptance and completed build pair are recorded below. Full runtime certification
remains required. Real-Plex access,
the retained ARM key, complete configured-secret scanning and final-commit
local/remote gates remain open. No release readiness is claimed.

The corrective Fabric 1.20.1 case passed in 511 seconds with unchanged
840-input source. All 33 original PNGs were directly reviewed after closure;
the retained non-owner screenshot is complete and byte-identical to the
observer's initial capture. The audible eight-second physical PCM pair aligns
at 20 ms with correlation 0.997049 (both RMS levels above -23 dBFS). All five
clients exited zero, owned resources are closed and no unit core was recorded.
Draft retention/replacement/clearing, readable permission errors and stationary
paused video are visible; immediate seek/stream reset textures recover in later
originals. Initial chat overlays and the missing retained pause-PCM limitation
are explicit in the review. Closed logs have no rejected segment or nonzero
underrun/starvation. The all-evidence 176-file scan passes for the four available
values only, including the retained intentional invalid-config crash.

The first full-build attempt failed after 1,254 seconds at the new real-X
capture tests: its task-local ImageMagick coder/configuration paths were absent
from the build service. All six capture commands exited nonzero. GameTests
reported all ten required tests passed, but the full build failed before the
sixteen-artifact inspection and no second build started. The unchanged
840-input source and failed logs/raw GameTest output are preserved under
`png-capture-integrity/failed-build-attempt-1/` with a relocation receipt.
The stopped build unit has no remaining members or recorded cores.

A private-X diagnostic reproduces the capture failure without those environment
settings and produces a valid PNG with them. The previously failing Gradle task
then passed all five test methods. No repository source change was needed.
A fresh serial build pair then ran on the same frozen inputs through a
launcher that loads and checks the required environment first. Its completed
results follow below; the failed attempt never grants build certification.

The first build of the corrected-environment pair passed in 1,304 seconds:
all 90 outer tasks executed, all ten GameTests passed, and all sixteen artifacts
were inspected. Its raw GameTest log is retained with both exact timestamped
completion lines. Independent hashing verifies the sixteen JARs and two
manifests in the saved snapshot; all 18 files also match the prior 839-input
candidate byte-for-byte. Earlier acceptance receipts retain their original
source identity. All 840 frozen inputs were unchanged at that checkpoint;
completed pair results follow below.

The corrected-environment pair is now independently accepted for
reproducibility. The second build passed in 1,327 seconds with all 90 tasks,
all ten GameTests and all sixteen artifact inspections. Actual-byte comparison
confirms that all sixteen JARs and both manifests match across both snapshots
and the current release directory. Both raw GameTest logs are retained. All
840 inputs remain unchanged; the core, modern and legacy suites report 204,
40 and 253 tests with no failures/errors and one, two and two skips. The build
service exited zero with no remaining processes or recorded cores. The limited
four-value scan passes across all 18 candidate files. Complete runtime, native,
real-Plex, configured-secret and final-commit CI acceptance remain required.

The fresh 37-case matrix has eleven independently accepted cases.
For legacy terminal, all 32 originals were directly reviewed. Five audible eight-second PCM pairs
align within 30–50 ms with correlations 0.986991–0.995361; all six quiet checks
pass, the leader continues during both follower world changes, and all six
clients exit zero. Owned private displays/sockets, sinks and ports are closed,
with no recorded unit core or rejected video segment/active underrun/starvation.
The limited four-value scan passes across all 235 case files.

Modern terminal also passed direct review of all eighteen originals, three
audible eight-second PCM pairs (0–10 ms, correlations 0.989530–0.997777), four
quiet captures and five actual zero exits. Queue/restart video advances on both
clients; initial chat overlays are explicitly recorded. Nine raw starvation
rows occur only within the final three seconds of the fifteen-minute fixture,
with empty queues and all active underruns zero. No segment rejection or unit
core was recorded. All old private X processes are gone; display 90 was already
reused by the following owned case, verified by its new PID, cgroup and launch
log. Sinks and game/RCON ports are closed. The limited scan passes across all
201 files, including the retained intentional invalid-config crash.

Legacy pressure passed all 75 directly viewed originals, five audible
eight-second PCM pairs (0–50 ms, correlations 0.985390–0.996834), peer silence
and seven actual zero client exits. Both viewers advance during browse/segment
load, recovery and post-reconnect/reload captures. Draft selection survives
state changes, replacement/clearing are visible, and permission feedback is
readable and expires. The retained pause-PCM limitation and transient reset
textures remain explicit. Raw logs confirm 247 browse requests over 40.35
seconds, queue/active bounds of 16/1 and 228 browse rejections. The segment
peer sent 2,980 requests over 40.03 seconds, received 3,686,304 bytes and 114
acknowledgements, and hit both rejection paths without unexpected errors or
orphaned transfer resources. All private displays/sockets, sinks and ports
closed; no unit core, rejected segment or active underrun/starvation was found.
Two fixture broken pipes are the deliberately delayed JSON metadata responses.
The limited four-value scan passes across all 277 files, including a retained
copy of the closed batch section.

Modern pressure passed direct review of all 69 originals, five audible
eight-second PCM pairs (all 0 ms, correlations 0.996502–0.999804), peer silence
and six actual zero exits. Both viewers advance before, during and after both
floods. Selected draft text survives state changes; later captures show its
replacement and both drafts retained across queue/browse toggles. Immediate
post-click images sometimes retain the prior display state; the review names
the later frames that confirm changes. The cleared-drafts image still has the
session field selected, while the final follower UI shows both fields empty.
Paused world frames stay identical for three seconds; the retained pause-PCM
limitation remains explicit. Raw workload verification confirms 253 browse
requests, queue/active bounds of 16/1 and 234 browse rejections, plus 3,070 peer
requests, 5,206,096 bytes, 161 acknowledgements and both bounded rejection paths
over 40.055 seconds, without unexpected errors or orphaned resources. All
private displays/sockets, sinks and ports close; no unit core, rejected segment
or nonzero underrun/starvation was found. Two broken pipes are delayed JSON
metadata responses. The limited four-value scan passes across all 297 files,
including the closed batch section and intentional invalid-config crash.
Legacy fault recovery also passed all 57 directly viewed originals, four audible
eight-second PCM pairs (0–40 ms, correlations 0.992881–0.999974) and six actual
zero exits. Both clients advance after transient failures, slow delivery and
exhausted retries, then after reconnect and two resource/sound reloads. Raw
requests confirm 164 transient-phase, 54 slow-phase and six offline segment
attempts, followed by restored online delivery. The earlier phase receipts
record the first observed minimums of five, three and six attempts. Generations
12, 13 and 14 stay in one session; all eighteen recovery-frame records match
actual client logs. Two redacted HTTP 503 warnings belong to the exhausted
phase, and no rejected decoded segment or active underrun/starvation appears.
Private displays/sockets, sinks and ports close with no recorded unit core.
The limited four-value scan passes all 232 retained files, including the closed
batch section. Immediate stale controller states, reset textures, camera crop
and the retained pause-PCM limitation remain explicit. Modern fault recovery passed all 51 original images, four audible eight-second
PCM pairs (0–10 ms, correlations 0.987868–0.997820) and five actual zero exits.
Both clients visibly advance after each fault. Raw requests confirm 157
transient-phase, 50 slow-phase and six offline segment attempts; first observed
minimums were eight, eight and six. All eighteen recovery-frame records match
generation-specific client logs in one session, followed by online delivery.
The two redacted HTTP 503 warnings belong to exhausted retries. No rejected
segment, active underrun/starvation or unit core was found. Old private X
processes are gone; display 90 was reused by the next owned legacy probe,
verified by its new PID, cgroup and launch log. Sinks and game/RCON ports close.
The limited scan passes all 240 files, including the intentional invalid-config
crash and closed batch section. Camera crop, initial chat overlays, stale
immediate controller captures and the pause-PCM limitation remain explicit.
All six terminal/pressure/fault supplements are independently scoped accepted.
The main Forge 1.7.10 case is independently scoped accepted. All 39 original
images were directly reviewed, including the reconnected follower and six
post-reload world frames. Both clients visibly advance; the eight-second PCM
pair aligns at 10 ms with correlation 0.988141. Each role exercised three
ten-second decoder stalls, both completed two resource/sound reloads, and all
six clients exited zero. Closed logs contain no rejected legacy or modern video
segment and no active underrun/starvation. A command-probe connection reset at
03:14:44 accompanies its normal zero exit. Old private X processes are gone;
display 90 is now owned by the following Fabric probe, verified by PID, cgroup
and launch log. Sinks and game/RCON ports close with no unit core. The limited
four-value scan passes all 182 profile-prefixed files, including its retained
closed batch section; the shared live fixture remains in the final scan scope.
Camera crop, transient reset textures and the retained pause-PCM limitation
remain explicit.
Main Fabric 1.20.1 is now independently scoped accepted. All 33 original PNGs
were directly reviewed; the 48,462-byte non-owner capture exactly matches its
immutable initial UI image. Draft selection, replacement/clearing, permission
feedback and stationary paused frames pass visual review. The eight-second
PCM pair aligns at 10 ms with correlation 0.997328, and all five clients exit
zero. No rejected video segment, nonzero underrun/starvation or owned core is
recorded. All old private X processes are gone; displays 90 and 91 have been
reused by the next owned Quilt clients, verified by PID, cgroup and launch logs.
Sinks and ports close. The limited scan passes all 176 profile-prefixed files,
including the closed batch section and intentional invalid-config crash.
Development authentication, optional narrator/ALSA probes and the successful
server fallback after client command parsing are classified in the review.
Initial chat overlays, transient reset textures and the retained pause-PCM
limitation remain explicit.
Main Quilt 1.20.1 also passes independent review of all 33 originals, an audible
eight-second PCM pair (0 ms, correlation 0.999527) and five actual zero exits.
The complete non-owner capture matches the initial widget; drafts survive
selection/state changes and queue/browse toggles, then clear. Both paused world
frames remain identical for three seconds. Permission feedback is readable and
expires. Initial chat overlays and seek/stream reset textures are recorded;
later editing images restore the program. All private displays/sockets, sinks
and ports close, with no recorded unit core, rejected segment or nonzero
underrun/starvation. The limited scan passes all 184 profile-prefixed files,
including the retained closed batch section and intentional invalid-config
crash. Development authentication, optional narrator/ALSA probes, command-parser
fallback and an ignored decoder input option are classified in the review.
Main Forge 1.20.1 is independently scoped accepted: all 33 original captures
were directly reviewed, with draft retention, controls, permission feedback and
stationary paused video confirmed. The complete non-owner capture matches its
initial widget. The eight-second PCM pair aligns at 10 ms with correlation
0.999431. All five clients exit zero, private displays/sockets, sinks and ports
close, and no owned core, rejected segment or nonzero underrun/starvation is
recorded. Netty optimization probes, optional narrator/ALSA/priority messages
and the deliberate vanilla-client rejection are classified in the review.
The limited scan passes all 186 profile-prefixed files, including the retained
closed batch section and intentional invalid-config crash. Initial follower
chat overlay, camera crop, transient seek/stream reset textures and retained
pause-PCM limitations remain explicit.
Main NeoForge 1.20.1 passes independent review of all 33 original images,
an audible eight-second PCM pair (10 ms, correlation 0.995555), five actual
zero exits and owned-resource cleanup. Drafts, controls, stationary paused
video and permission feedback are visible; the complete non-owner capture
matches its initial widget. Initial follower chat, camera crop, temporary
seek/stream reset textures and the retained pause-PCM limitation are recorded.
No rejected segment, nonzero underrun/starvation or owned core is found.
A residual pause command reports missing state after the old follower's
acknowledged disconnect/reset, immediately before its normal exit. The
intentional vanilla-client rejection and baseline dependency probes are
classified separately. Old private X processes are gone; display 90 is reused
by the next owned Fabric probe, verified by PID, cgroup and launch log. Sinks
and ports close. The limited scan passes all 183 profile-prefixed files,
including the retained closed batch section and intentional invalid-config
crash. Fabric 1.20.2 subsequently failed during disconnect; 26 of 37 cases remain unaccepted.

Retained-Windows
native run `20260909T090849Z` is independently scoped accepted: all 279 uploaded
and used inputs match the current bundle, and all 69 Windows native entries
match all sixteen candidate artifacts. Three software-decoder fixtures have
SSIM 1, zero PTS/drift/duration delta, zero underruns and zero fallbacks. The
1080p row has two bounded retention drops, exactly matching its reference. Both
retained guests are ready and powered off; task mounts, loops, NBD and the
transient run directory are closed. The unit exited zero, no local unit core
was recorded, and the remote journal and dedicated guest-state core search are
clean (remote coredumpctl is unavailable). The limited four-value scan passes
across fifteen evidence/log files. This certifies standalone Windows software
decoding only. ARM, real-Plex and complete configured-value acceptance remain
open.

## Legacy decode retirement defect — 2026-09-09 UTC

Independent review rejected the main Forge 1.7.10 case despite its automated
pass. All 39 originals show recovered video, its audible eight-second PCM pair
aligns at 10 ms (correlation 0.997668), and all six clients exited zero. However,
both old decoders logged an interrupted segment as rejected when a seek replaced
generation 9. The legacy decoder lacked the modern completion guard: obsolete
successful returns could also refill cleared queues. The automated rejection
pattern missed the word `legacy` in these diagnostics.

The batch was deliberately stopped during the following Fabric 1.20.1 case.
Seven cases passed automation, six were independently scoped accepted, the main
legacy case is rejected, one case is interrupted and 29 were unattempted. All
owned processes, displays, sinks and representative ports are closed with no
recorded unit core dumps. Original logs/media and immutable review receipts are
preserved under `native-payload-identity/`, with a runtime relocation map. These
results and the reproducible 838-input build pair remain historical evidence
for that source; they do not certify the corrected candidate.

Four new deterministic legacy tests failed before correction, demonstrating
obsolete cancellation reporting and late result publication after close/reset.
The legacy pipeline now uses the shared completion guard for both successful
results and errors, retiring publication before interrupting workers. Active
failures remain reportable. Linux and Windows runtime rejection patterns now
recognize both legacy and modern diagnostics; the normal-exit oracle also
rejects segment failures appearing after the last playback check. The four
legacy regressions, three shared guard tests and six client-exit tests pass.
The new exit regression first failed for both diagnostic forms. The corrective
two-client legacy run passed in 551 seconds with unchanged 839-file source.
All 39 originals were directly reviewed; both programs advance after reconnect
and two reloads, and the audible eight-second PCM pair aligns at 10 ms
(correlation 0.998075). Six clients exited zero, both clients show three stall
injections across replaced pipelines, and all closed logs are free of rejected
legacy or modern segments. Private resources are closed with no unit core dumps.
The scan of 183 files passes for the available credential/host values only.
The new 839-input frozen pair passed in 1,320 and 1,299 seconds. Each build
executed all 90 tasks, explicitly passed all ten required GameTests and inspected
all sixteen artifacts. Independent actual-byte comparison confirms all sixteen
JARs and both manifests match across both snapshots and the current release
bundle; all frozen inputs are unchanged. Core, modern and legacy suites report
204, 40 and 253 tests respectively, with zero failures/errors and one, two and
two skips. The owned build service exited zero with no recorded core dumps.
Evidence is under `build/release-resume-20260909/legacy-decode-retirement/`.
The first legacy terminal case is independently scoped accepted: all 32
original images were directly reviewed, all five audible eight-second PCM pairs
align within 10–40 ms (correlations 0.992487–0.999719), six quiet captures pass
and all six clients exited zero. Both world returns, queue advancement and replay
show advancing visible video. Owned displays, sinks and ports are closed, with
no recorded owned-unit core dump or rejected decode. The 235-file scan passes
for the four available values only. Modern terminal is also scoped accepted:
all 18 originals were directly viewed, three audible eight-second PCM pairs
align within 0–10 ms (correlations 0.989751–0.999662), four quiet captures are
silent and all five clients exited zero with clean resources and no unit core
dump. Nine raw stream-exhaustion rows occur only during the last three seconds
of the program, with zero underruns. The expected invalid-configuration Java
crash report is retained and included in its passing 201-file limited scan.
Legacy pressure is also independently scoped accepted after direct review of
all 75 originals, five audible eight-second PCM pairs within 0–10 ms
(correlations 0.990965–0.999961), a silent traffic-only peer capture and seven
actual zero client exits with clean resources and no unit core dump. The
251 browse requests and 3,230 peer segment requests recover with no unexpected
peer errors or orphaned grants/egress. Both held-metadata response closures are
classified explicitly; no Cinemarr segment is rejected. The 277-file limited
scan passes. Modern pressure is independently scoped accepted after all 69
originals and five audible eight-second PCM pairs: offsets 0–50 ms, correlations
0.983820–0.999942. All six clients exited zero, the traffic-only peer is silent,
and owned resources are closed without a recorded core dump. Browse load made
254 requests; the peer made 3,120 requests with zero unexpected errors, and
recovery left no orphaned grants or egress. Repeated ALSA device-control probe
warnings and two expected held-metadata broken pipes are explicitly classified.
The expected invalid-config crash is retained in the passing 291-file limited
scan. Legacy fault recovery is also independently scoped accepted: all 57
originals show visible recovery, all four audible eight-second PCM pairs align
within 10–20 ms (correlations 0.981317–0.999977), and all six clients exited zero
with clean owned resources and no recorded core dump. Transient, slow and
exhausted retry phases recover in-session; the two expected HTTP 503 warnings
are redacted. No decoded segment is rejected and active underrun/starvation
fields remain zero. The 233-file limited scan passes. Modern fault recovery
is scoped accepted after all 51 originals and four audible eight-second PCM
pairs within 0–10 ms (correlations 0.984878–0.991927). All five clients exited
zero with clean resources and no recorded unit core dump. All three adverse
phases recover; expected redacted HTTP 503 and audio-device probe diagnostics
are classified. Its expected invalid-config crash is retained in the passing
240-file limited scan. The main Forge 1.7.10 case is now independently scoped
accepted: all 39 originals reviewed, the audible eight-second PCM pair aligns
at 10 ms (correlation 0.999026), and all six clients exited zero with clean
resources and no recorded core dump. Three leader and four follower decoder
stalls were injected across replaced pipelines, with no rejected segment or
active underrun/starvation in any closed profile log. The 181-file limited scan
passes; shared main-matrix files still require their final closed scan. Seven
of 37 cases were accepted before the Fabric screenshot failure described
above stopped the batch; the remaining cases are unaccepted.

Fresh retained-Windows run `20260909T060846Z` is independently scoped accepted
for this candidate. All 279 uploaded and used files match the current bundle;
three software-decoder fixtures pass, and all 69 Windows native entries match
all sixteen release artifacts. Both retained guests are ready and stopped;
the transient run, mounts, loop devices and task NBD connection are closed.
No local unit core dump was recorded. The remote host lacks coredumpctl; its
unit journal shows successful deactivation without crash entries, its core
pattern is `core`, and the dedicated state root contains no core file. The
four-available-value scan passes for all fourteen evidence files. This covers
standalone Windows software decoding, not a Windows Minecraft client or hardware
acceleration. ARM, full runtime, real-Plex and final release gates remain open.

## Retained Windows payload defect — 2026-09-09 UTC

The new-device Windows run `20260909T023852Z` passed its automated decoder
checks but is rejected by independent input review. Read-only inspection of
the stopped retained guest proved that PowerShell copied the current bundle
into `bundle/bundle` while the classpath and fixture directory still used the
older outer bundle. All three reported fixture hashes match those stale files;
the used core JAR differs from the current candidate. This is not exact-byte
native acceptance. The failed evidence and read-only inspection are preserved.

The local batch was deliberately stopped for correction after five automated
cases. Four were directly reviewed and scoped accepted for the previous frozen
source (both terminal and both pressure cases); legacy fault recovery finished
its automated gate but has not been directly reviewed. The remaining 32 cases
were unattempted. No private displays or task audio sinks remained at the stop.
The source correction invalidates those results as certification of the next
candidate, and requires another frozen build pair and fresh runtime evidence.

The Windows runner correction now uses fresh per-run work directories,
a retained bootstrap that loads the current payload runner, and manifests that
bind every used bundle file and reported fixture to the uploaded payload. Five
regressions reject stale core files/fixtures, stale identities and incomplete
or duplicated manifests. All 23 tooling tasks passed. The dedicated retained
runner was hash-checked and backed up, then replaced with the current bootstrap
while the guest was off. Diagnostic run `20260909T025605Z` passed with all 279
used bundle files and all three fixture identities matching the current input.
Independent review matched all 69 Windows native entries across all sixteen
candidate artifacts. Both guests are stopped; task NBD/mounts and both Windows
transient run directories are absent. This is correction validation, not final
candidate acceptance. The subsequent 838-input pair passed in 1,325 and
1,314 seconds: both builds executed all 90 tasks, explicitly passed ten required
GameTests, inspected sixteen artifacts and produced identical eighteen-file
bundles. Formal Windows run `20260909T035031Z` passed independent review of
279 used files, three fixtures and 69 Windows native entries across those
sixteen artifacts. Both retained guests were stopped and transient resources
were closed. The remote host lacks coredumpctl; its unit journal recorded
successful shutdown without crash entries. This covers the standalone software
decoder, not a Windows Minecraft client or hardware acceleration.

That candidate's runtime batch was then stopped for the legacy decode defect
above: six of 37 cases were independently accepted, the main legacy case was
rejected, the next Fabric case interrupted and 29 cases unattempted. Its original
receipts and detailed visual/audio findings remain under
`build/release-resume-20260909/native-payload-identity/`; runtime paths are resolved
through its immutable relocation map. None certifies the corrected 839-input
source. The limited configured-value scans passed; the complete final scan,
real-Plex/ARM work, final-commit gate and exact-SHA green CI remain outstanding.

## New-device resumption — 2026-09-09 UTC

The user resumed this plan from clean handoff commit
`d967a43de5a6f43115018ba0c8189b2c7f16d12d`. Original-device ignored evidence is
absent here and remains historical. The unchanged 16-artifact/21-runtime scope
and every completion requirement below still apply; no publication is authorized.

The handoff's [GitHub run 34289433197](https://github.com/StonyTark1117/Cinemarr/actions/runs/34289433197)
failed seven artifact/runtime jobs and skipped the aggregate gate. All five
Fabric runtime failures occur at private-window lookup after protocol rejection.
Legacy reaches the 300-second fixture end after resource reload, before its
last fresh stability window. Forge 26.1.2 logs an obsolete audio decode's
`CancellationException` as a rejected segment during stream replacement.
The 1,358 downloaded diagnostic files are preserved by hash under the ignored
`build/release-resume-20260909/` evidence directory. These failures are not rerun
or reclassified as acceptance.

Corrections in progress give normal-close window discovery a bounded wait,
serialize decode completion with retirement/reset, and increase the default
control fixture to 900 seconds. Active decode failures and A/V timing limits
remain strict. Fourteen private-window unit tests and five real-X test methods
(including delayed mapping and native-crash exits) pass. Core tests report
203 passed/1 skipped; root tests report 38 passed/2 skipped. These counts
are scoped deterministic checks, not Minecraft runtime acceptance.

The first tooling attempt failed because this host lacked Xvfb; its log is
retained. With task-local utilities provisioned, all 22 tooling tasks passed.
An initial diagnostic build passed root/core checks and the first Fabric/Quilt
builds, then was deliberately stopped to implement the observed CI corrections.
Its interruption receipt is retained, and it is not a complete release build.
The first frozen build passed in 1,647 seconds, with all 89 tasks executed,
all ten GameTests passed and all sixteen artifacts inspected. Its 835 inputs
were unchanged and its eighteen-file bundle is retained. The second build was
deliberately interrupted after 278 seconds for a newly discovered harness
credential-isolation defect: exported Plex/DiscPanel credentials reached the
client-launch environment. All four shell launch boundaries and the packaged
Java probe/launch now strip those credentials; canary regressions first failed
and then passed (five client-exit tests and 21 packaged-input tests). The first
build retains its original source identity. The fresh tooling preflight passed
all 22 tasks. The first build of the new frozen pair under
`credential-isolation/` passed in 1,384 seconds: all 89 tasks executed, all
sixteen artifacts inspected, and all ten required GameTests explicitly passed.
The second forced build passed in 1,331 seconds with the same 89-task and
explicit ten-GameTest outcomes. All sixteen JARs and both manifests are
byte-identical, and all 835 non-documentation inputs remained unchanged. The
owned build service exited zero with no recorded core dumps. This certifies
the two-build reproducibility check; runtime acceptance remains outstanding.
Four exact-bundle packaged-client preflights passed without launching clients.
Runtime preparation passed after five Fabric asset warms and a parallel
NeoForge asset-only warm; two deliberately interrupted cold-cache preparation
attempts are preserved. NeoGradle cleared an externally seeded cache, so the
maintained download task populated it using sixteen network workers. The
native decoder benchmark bundle is prepared. The serial 37-case runtime batch
has started. The first legacy terminal case is scoped accepted after direct
review of all 32 gate images, five independently checked eight-second PCM
pairs (10–20 ms offsets; correlations 0.988554–0.999412), six quiet captures,
six actual zero client exits and closed private X/sinks/game ports, with no
owned-unit core dump. Queue advance, two same-JVM world changes, near-EOS
follower replacement, empty-queue idle, replay and stop are observed. This is
one case. Modern terminal is also scoped accepted: all 18 images directly
reviewed, three independent eight-second PCM pairs at 0–20 ms (correlations
0.867982–0.999972), four quiet checks, five actual zero client exits and clean
owned resources. Initial join toasts cover part of the TV, but all sustained
queue/replay captures show unobstructed advancing video. One raw stream-read
exhaustion per original client occurs only during terminal input drain in the
last three seconds; active underruns remain zero. The source/EOS classification
audit is retained. Legacy pressure is the third scoped accepted case: all 75
original images reviewed, five independent eight-second PCM pairs at 20–30 ms
with correlations 0.987536–0.999201, a silent network-only peer, seven normal
client exits and clean owned resources. Main PCM and final world images follow
reconnect and two resource/sound reloads per client. The maintained control
harness overwrites paused PCM with resumed audio, so pause silence remains an
automated assertion, not an independent retained-PCM claim. Both held-browse
BrokenPipe responses are preserved and classified as timed-out metadata
requests, not segment failures. Modern pressure is the fourth scoped accepted case: all 69 originals
reviewed, five independent eight-second PCM pairs at 0–10 ms with correlations
0.986625–0.996910, silent network-only peer, six normal exits and clean owned
resources. Its immediate-action UI captures sometimes show the preceding state;
later frames show retained edits and cleared fields. No modern resource-reload
claim is made. This is 4 of 37 cases; legacy fault recovery is running.
The retained Windows/ARM read-only preflight independently verified both exact
ready markers and powered-off guests, without starting or modifying either.
Read-only discovery also confirmed the four designated DiscPanel test servers
stopped, autostart off and Cinemarr disabled. The public Windows installer copy
was verified against its source SHA-256; it has not been booted.
Credentialed/packaged/native runs, direct reviews, final-commit local gate,
exact-SHA remote CI and artifact parity remain outstanding.

## Historical paused device-migration handoff — 2026-09-08

**User-directed pause: commit/push the current work and continue on another
device. This is a WIP handoff, not a release-candidate certification.** No
Cinemarr test units were running at the pause check. Do not automatically
resume testing on the original device. No tag or publication is authorized.

### Objective and non-negotiable completion boundary

Implement all ten phases in this document, keep documentation truthful, commit
the work, run the complete local gate on the final clean commit, push that exact
SHA, and verify fully green GitHub CI and downloaded/local artifact parity.
**Only after those documentation and CI tasks are complete**, compare the then
current Jammarr maintained targets and report which newer versions/loaders
Cinemarr can support, which need work, and which are infeasible for its video
scope. The sister checkout was `/home/braydon/PAmpMod`, repository
`Jammarr/PAmpmod`; inspect it afresh then. Do not expand the current certification
matrix or substitute Jammarr's optional-client model while finishing this plan.

Current scope remains **16 artifacts, 21 main runtimes and 16 supplemental
cases (37 total)**, with 41 explicit reviews covering 1,164 image references /
1,158 distinct original images. It also requires four exact-byte real-Plex
two-client cases, four Plex-recovery cases, two lifecycle cases, Windows x86-64
and Linux ARM64 native-payload checks, deep configured-secret scanning and
owned-resource cleanup. A build, process running, decoder frame hash, automated
pass flag, or older green CI does not replace visible video, measured physical
PCM, actual clean client exits, or final-SHA certification.

### Exact stopping point and recent changes

- The queue-feedback correction has scoped legacy terminal acceptance:
  348 seconds, all 28 originals directly viewed, five independently recomputed
  eight-second PCM pairs (-20 to +40 ms, correlations 0.993038–0.999923), six
  normal client exits, no owned core dump and restored/closed resources.
  It preserves contextual queue/episode feedback only for playing snapshots;
  paused, suspended and idle snapshots retain truthful messages.
- The underlying suspension fix separates playback-metadata revision from
  wire generation. Late completion may retain metadata after pause/suspension,
  but must publish the returned current snapshot. Stop, replacement, restore,
  removal and close still invalidate obsolete metadata. All three server
  families use this path; eight coordinator tests and six cross-family layout
  tests cover the correction. Other handoff changes include abandoned transfer
  grant cleanup on world/screen departure, fresh audio-readiness telemetry,
  one-shot protocol probes, manifest cache-policy routing, and normal private-X
  client-close/exit checks (including native crashes without local core files).
- R13's first runtime failed because the earlier fix replaced contextual queue
  feedback with generic `Playing`. Preserve that failure; actual queue
  advancement alone did not satisfy the unchanged observer. No r13 runtime
  case was accepted. Its earlier successful builds certify only those bytes.
- R14 tooling preflight passed in 24 seconds, with 833 unchanged non-Markdown
  inputs. Its **first full build was interrupted after 489 seconds** by the
  user-session shutdown around 11:50 Arizona: SIGTERM 15, child exit 143.
  There was no completed root build, second build, reproducibility pass, or
  r14 runtime case. The interruption is not a demonstrated product test failure.
  The user manager/audio services subsequently returned, but **no replacement
  build was launched before this pause**.
- The previous base HEAD was `e7d5f005829ad151c086dc353ffc46bf55ccbc0c`.
  This handoff commit includes the accumulated changes; its existence/push
  does not satisfy phase 10. CI triggered by the handoff push must be inspected,
  not presumed green. Resume from the pushed branch's actual HEAD.

### Evidence portability and safe restart

The source, maintained scripts, this plan, `RELEASE_ACCEPTANCE.md` and
`NATIVE_TEST_GUESTS.md` are tracked. **Raw logs, images, PCM, JAR snapshots,
failure archives and temporary audit helpers under `build/` are ignored and
are not uploaded by this commit.** A fresh clone must not claim those local
files are present or relabel earlier receipts as evidence for its own build.
Either arrange a separate secure transfer of the original evidence, or produce
fresh full acceptance on the new device; the old results remain historical.
Never add raw deployment credentials, VM disks, crash dumps or unscanned
evidence archives to Git to make the handoff self-contained.

Useful original-device locations (not prerequisites available from Git):

- `build/clock-fix-gate/queue-feedback-fix-1.7.10-forge-20260908.direct-review.json`
  and `.preserved.json`: accepted corrective review and 258 hashed references,
  including a retained `.development.jar`; artifact SHA-256
  `7879ad0f2375bc9e191252fa1cdad36b7744b9c9af55b47d62015be061f27302`.
- `build/clock-fix-gate/release-audit-r13-runtime-failure-preserved-20260908.json`:
  152 failed-attempt evidence files. Earlier r7–r12 failure archives and the
  r8 native core remain in place; do not erase or count them as passing cases.
- `build/clock-fix-gate/release-audit-r14-build-interruption-preserved-20260908.json`:
  24 hashed references including the original build receipt/log, source inputs,
  previous bundle and journal evidence of the session shutdown.
- Temporary `*r14*.py` helpers in `build/clock-fix-gate/` were prepared; 15
  handoff/exit/final-review-identity tests passed. The candidate helper was
  changed to expect `release-audit-r14-session2-20260908` build outputs, and the
  build-pair helper gained `--run-tag session2`. **Those outputs do not exist.**
  These machine-specific helpers are not maintained product tools or portable
  evidence. Do not invoke them on another device assuming their old paths,
  unit history or dependencies exist. The tracked Gradle/scripts are the
  reproducible entry points; preserve distinct output identities for new runs.

On the new device, check Git status, toolchains and `gradle/targets.json` first.
The root wrapper needs JDK 21; the manifest declares Java 8/17/21/25 runtime
levels, with JDK 26 also required for designated builds/development launchers.
Packaged 26.x clients use a Java 25 toolchain. Supply
correct local paths rather than copying this machine's JDK cache paths.
Forge-family command/protocol probes must honor the manifest's configuration
cache policy. `JAMMARR_LEGACY_BUILD_DIR`, where needed, pointed to the isolated
`build/jammarr-legacy-snapshot-8eccf06`, not a mutable sibling checkout.

Run the deterministic tooling/unit checks, then two forced full builds using
`test inspectReleaseArtifacts verifyGameTests` with `--rerun-tasks --no-daemon
--no-configuration-cache --max-workers=1`. Freeze the non-documentation inputs,
inspect all sixteen JARs and require byte identity of all sixteen JARs plus two
manifests before runtime certification. Complete the maintained runtime and
supplemental checks, including direct image/physical-PCM reviews. Preserve and
diagnose failures rather than rerunning to hide them. The final clean commit
must still run **the complete `releaseMatrixGate`**, not a subset, followed by
exact-SHA remote CI and downloaded artifact parity. If code changes, earlier
candidate evidence does not certify the new source/bytes.

### Private resources and cleanup contract

- Credentials are purpose-specific files on the original host. Obtain their
  locations privately from the user; do not put credential locations or values
  in this public repository. They were not persisted environment variables.
  Read files only for the approved task; do not print contents or
  search histories, ordinary app configs, backups or unrelated authentication
  stores for replacements. On the new device verify that authorized files and
  endpoints were securely provisioned; ask the user if genuinely unavailable.
- Four owned DiscPanel test servers are `Jammarr 1.7.10 Forge Test`,
  `Jammarr 1.20.1 Quilt Test`, `Jammarr 1.21.1 NeoForge Test` and
  `Jammarr 26.2 Fabric Test`. Use fresh discovery/status checks. Preserve other
  mods, overrides and saves; deploy only the current exact release bundle.
  Restore state and leave them stopped, autostart off and Cinemarr disabled.
  Their earlier stopped-state checks are historical, not a migration-time audit.
- Reuse the installed Windows/ARM guests; do not reinstall or delete them.
  They are **host-managed headless QEMU guests, not Proxmox `qm` registrations**:
  `/var/lib/cinemarr-hwtest/windows-x86_64/windows.qcow2` and
  `/var/lib/cinemarr-hwtest/linux-arm64/guest.qcow2`. Require fresh ready/off
  preflight and a post-run stopped-state audit. See `NATIVE_TEST_GUESTS.md`
  for commands, retained identity/key requirements and installer checksum input.
  ARM emulation is ABI/functionality coverage, not physical ARM performance;
  neither native check certifies a full Minecraft client on that OS.
- All GUI work must use a separate private X server/hidden workspace; never
  attach to the active user's desktop, audio controls or browser session.
  Use task-owned audio sinks and identity-checked normal client shutdown.
  Do not change login/session persistence settings to keep tests alive.
- Refresh the deep secret/endpoint scan after all evidence is closed and again
  for final-gate/hosted artifacts. Include retained failed/interrupted evidence
  and decoded compressed core/archive payloads. The prior full scan predates
  these new attempts. A clean source-only handoff check is not that full scan.

## Evidence checkpoint (2026-09-08 UTC)

- R13 failed its first runtime case after 229 seconds: zero accepted, one
  failed and 36 unattempted. Both real clients advanced from near-EOS generation
  2 to playing generation 3, with item 9001 near position zero and an empty
  queue, but the suspension correction had replaced `Playing next queued video`
  with generic `Playing`. The unchanged terminal observer rejected that missing
  contextual feedback. All 152 evidence files are preserved by hash, four private
  displays and owned sinks/game/RCON ports are closed, and no owned-unit core
  dump occurred. Failed playback cleanup lacks normal exit receipts and is not
  accepted. R13's successful builds below do not certify its failed runtime.
  State-aware contextual feedback is now restored in all three managers,
  with a retained red regression followed by eight passing publication/feedback
  tests and six source-layout mutation tests. Core 200 passed/1 skipped, modern
  38 passed/2 skipped and legacy 244 passed/2 skipped; suites overlap. Three
  representative release builds pass. The corrective legacy terminal run
  passed in 348 seconds: all 28 original captures directly reviewed, five
  independently recomputed eight-second PCM pairs at -20 to +40 ms offset
  (correlations 0.993038–0.999923), four terminal drains and both away-follower
  captures at -91 dBFS, with the away leader still audible. All six clients
  exited normally, no owned core dump occurred, and world/configuration,
  private displays, audio sinks and ports are restored/closed. The original
  development JAR and 258 evidence references are preserved before rebuilding.
  The images retain top-cropped counters, HUD/hand and initial leader chat;
  paired exposures are sequential. This is scoped corrective acceptance,
  not full certification. The terminal observer and timing limits are unchanged.

- R14 tooling preflight passed in 24 seconds with unchanged inputs. Its first
  forced full build was interrupted by user-session shutdown after 489 seconds
  under `cinemarr-release-audit-r14-build-20260908.service`; second build and
  runtime were not attempted. A distinct resumed attempt was prepared but not
  launched before the user-directed device-migration pause. Reproducibility and the
  complete 37-case matrix, production/native work, final-commit gate and remote
  CI are not yet accepted.

- R13 tooling preflight passed in 23 seconds on the corrective source, including
  the new source-layout mutation dependency. Both fresh forced full builds
  passed in 521/507 seconds, each executing all 89 tasks, passing all ten
  GameTests and inspecting sixteen artifacts. Both eighteen-file bundles match
  byte-for-byte across 833 unchanged non-documentation inputs. The build service
  exited zero with no owned-unit core dump. The fresh 37-case runtime batch
  started at 11:09 Arizona under
  `cinemarr-release-audit-r13-runtimes-20260908.service`; it subsequently failed
  as described above. Fifteen handoff/GUI-exit/final-review
  identity guard tests pass. No r13 production/native work has started.

- The suspension/publication correction is implemented across all three
  server managers. Its frozen-source corrective Fabric 26.1.2 runtime passed
  in 252 seconds, with all 33 original captures directly reviewed, independently
  recomputed eight-second PCM at 0 ms offset (correlation 0.990489), all five
  single-launch GUI roles exiting zero, no owned-unit core dump and closed
  private displays, audio sinks and ports. Metadata completion
  accepts only the same playback revision and returns the current snapshot,
  preserving pause/suspension without publishing an obsolete playing state.
  Stream-option replacement and restore receive distinct revisions; stop,
  replacement, removal and close still reject old completions. Seven new
  deterministic coordinator tests and five cross-family source-layout mutation
  tests pass. Broader checks report core 199 passed/1 existing live-test skip,
  modern 38 passed/2 skips and legacy 243 passed/2 skips; all three representative
  release builds passed. The corrective case retains visible seek/stream
  transition dark frames, followed by restored video; initial vanilla toast
  and leader chat obscure parts of the picture. This is scoped corrective
  acceptance, not a new reproducible bundle or full certification. The
  deterministic tests exercise the failed ordering; the corrective runtime
  was not forced to reproduce that exact race. R13 subsequently failed a
  separate feedback regression; this earlier corrective pass retains its
  original source identity.

- R12 failed its twentieth runtime case, Fabric 26.1.2, after 6,595 seconds:
  nineteen scoped cases accepted, one failed and seventeen not attempted.
  The original batch reached its playback-ready timeout and exited 1 without
  a retry. The leader was suspended at 23 ms while TV chunks were unloaded;
  the follower later received the same idle generation and never decoded video.
  Read-only checks subsequently found both players at the intended camera
  positions and the TV chunks loaded, but no active stream or pending work.
  An isolated ordering model using the unchanged compiled coordinator reproduces
  rejection of the initial metadata completion after suspension advances the
  session generation. Without that metadata, the manager cannot checkpoint or
  restart the retained item. This exposed a product race, not acceptance.
  The follower also received `status=IDLE` with misleading `message=Playing`.
  The correction above has scoped corrective runtime acceptance; complete
  recertification remains pending.
  All 4,386 evidence files are hashed in place; 105 private displays and owned
  audio sinks/game ports are gone, the failed RCON port is closed and no
  owned-unit core dump was recorded. The failed playback clients lack normal
  exit receipts and are not counted as successful clean exits.

- The first r11 build failed after 487 seconds and 71 executed tasks; all ten
  GameTests passed. Its synthetic-clock polling fixture left Bash `SECONDS`
  special, so real elapsed time could advance the mocked clock and finish at
  tick 27 instead of 28. An explicit real-time-boundary regression reproduced
  this for both client families. Unsetting `SECONDS` before assigning the
  fixture clock makes it deterministic; the extracted production waiter and
  its eight-second requirement are unchanged. All seventeen polling tests and
  five focused Gradle tasks pass. The original failure, exact pre-fix test
  source, red regression and prior bundle are preserved across 23 hashed files.
  No second build or r11 runtime started. The full r12 tooling preflight passed
  all twenty maintained prerequisite tasks in 23 seconds on unchanged inputs.
  Both r12 forced builds passed in 511/508 seconds: all 88 tasks executed,
  all ten GameTests passed and sixteen artifacts inspected in each. All eighteen
  bundle files are byte-identical across 831 unchanged inputs, independently
  verified against the retained and current bundles after the build service
  exited zero. The fresh 37-case runtime batch started at 08:24 Arizona under
  `cinemarr-release-audit-r12-runtimes-20260908.service`. All direct/physical
  reviews and subsequent exact-byte/final-commit/remote certification remain
  required. Fifteen isolated runtime-handoff/exit/final-review guards pass.

- R12 legacy terminal acceptance passed in 352 seconds: all 28 original images
  directly reviewed, all six GUI clients launched once and exited zero, no
  owned-unit core dump, and five independently recomputed eight-second PCM
  pairs at 0–30 ms absolute offset. Both same-JVM world returns show restored
  moving video; away-world views have no stale television image, and follower
  audio drains while the leader continues. EOS/reconnect/restart and closed
  displays/sinks/port checks pass. Initial build chat is below the picture;
  the close camera still crops the top counter band. This is one scoped case
  of 37, not complete runtime or production certification.

- R12 modern terminal acceptance passed in 249 seconds: fourteen original
  images directly reviewed, five single-launch GUI clients exiting zero, no
  owned-unit core dump and three independent eight-second PCM pairs at 0 ms.
  Queue and replay images are unobscured; baseline images retain the vanilla
  chat warning and initial leader build chat. The first strict drain review
  withheld acceptance for nonzero queued-follower audio. The entire measured
  transient matches the installed toast-dismissal asset (correlation 0.903363),
  with onset 3.692875 seconds and a 0.257125-second captured tail. The original
  fixed-3.7-second review helper and diagnosis are preserved. Measured-onset
  attribution has five negative/positive fixture tests and leaves live audio
  thresholds and idle-before-first-playback checks unchanged. This capture is
  explicitly not digitally silent; the other three drain captures are -91 dB.
  All owned displays/sinks/port are closed. This is scoped terminal acceptance;
  full certification remains open.

- R12 legacy pressure acceptance passed in 494 seconds, with both the
  39-image control review and 42-image pressure review complete (75 distinct
  originals, six shared reload images). All seven GUI roles launched once and
  exited zero; no owned-unit core dump occurred. Four independently recomputed
  eight-second pressure PCM pairs have 30 ms offsets and correlations
  0.992498–0.993820; post-reconnect PCM is -10 ms/0.999753. The 255-request
  browse flood reached its bounded 16-item queue and recovered; the network-only
  peer made 3,820 requests over 40 seconds, remained digitally silent and left
  no orphaned grants/egress. All owned displays/sinks/port closed. Direct images
  show advancing video during both overloads and after two resource reloads
  per client. Repeated queue-full chat obscures the follower's lower picture
  during browse saturation; recovery is clear. Seek/stream transitions retain
  dark-tile captures, followed by restored video. These limitations are recorded,
  not claims of flawless presentation.

- R12 modern pressure acceptance passed in 423 seconds: all 69 original
  images reviewed (33 control plus 36 pressure), six single-launch GUI roles
  exiting zero and no owned-unit core dump. All five independently recomputed
  eight-second PCM pairs have 10 ms offsets (correlations 0.989529–0.999989).
  The 263-request browse flood and 3,730-request network-only peer test recover
  within the checked workload/grant bounds; the peer stays digitally silent.
  All owned displays/sinks/port are closed. Both clients' video advances during
  overload and recovery. Queue-full chat covers part of the follower picture
  during browse saturation; peer-join chat initially crosses the lower picture.
  Baseline unsigned-chat toast and seek/stream dark-tile transitions remain
  explicit, with clear later playback.

- R12 legacy fault acceptance passed in 415 seconds: 57 original images
  directly reviewed (39 normal/reload plus 18 recovery), all six GUI roles
  launched once and exited zero, no owned-unit core dump and closed owned
  displays/sinks/port. Three independent eight-second post-fault PCM pairs
  have -10/-20/-10 ms offsets, with post-reconnect PCM at 0 ms. Actual transient,
  slow and exhausted-retry phases advance generations 12/13/14 and recover
  visible moving video on both clients; two resource reloads per client also
  restore clear playback. Dark seek/stream-transition tiles and cropped camera
  framing remain explicit.

- R12 modern fault acceptance passed in 337 seconds: 51 original images
  directly reviewed, all five GUI roles launched once and exited zero, no
  owned-unit core dump and closed owned displays/sinks/port. All four independent
  eight-second PCM pairs have 0 ms offsets (correlations 0.996292–0.999037).
  Actual transient, slow and exhausted-retry phases recover moving video on
  both clients in generations 12/13/14. The baseline unsigned-chat warning and
  dark stream-transition capture remain explicit; later recovery is clear.
  All six terminal/pressure/fault supplements are scoped accepted.

- R12 main Forge 1.7.10 acceptance passed in 336 seconds: all 39 images
  directly reviewed, six single-launch GUI roles exited zero, no owned-unit
  core dump, and independently recomputed eight-second PCM at -10 ms with
  0.998685 correlation. Both clients show clear moving video after two resource
  reloads each; dark seek/stream-transition captures remain documented.
  Main Fabric 1.20.1 acceptance passed in 243 seconds: all 33 images reviewed,
  five single-launch GUI roles exited zero, no owned-unit core dump and
  eight-second PCM at 10 ms with 0.999663 correlation. Paused frame 188 stays
  fixed, resume advances, draft selection persists and feedback expires.
  Initial vanilla chat overlays and non-simultaneous saved controller views
  remain explicit. Both cases have closed owned displays, audio sinks and game
  ports.

- R12 main Quilt 1.20.1 acceptance passed in 302 seconds: all 33 images
  reviewed, five single-launch GUI roles exited zero, no owned-unit core dump,
  closed owned displays/sinks/game port and independently recomputed eight-second
  PCM at 0 ms with 0.990295 correlation. Paused frame 316 remains fixed,
  resume advances, selected text survives state changes and feedback expires.
  Initial vanilla chat overlays, dark stream-transition tiles and different-time
  saved controller selections remain explicit; later video is restored.

- R12 main Forge 1.20.1 acceptance passed in 260 seconds, beyond the profile
  where r9 stopped on configuration-cache serialization. Its mismatch launcher
  now finishes successfully without a retry. All 33 original images were
  reviewed, five single-launch GUI roles exited zero, no owned-unit core dump
  was recorded and owned displays/sinks/game port are closed. Independent
  eight-second PCM has 10 ms lag and 0.997816 correlation. Paused frame 191
  stays fixed; resume, input retention and feedback expiry are visible. Initial
  vanilla chat overlays and dark seek/stream-transition tiles remain explicit,
  with video restored in later captures.

- R12 main NeoForge 1.20.1 acceptance passed in 261 seconds: all 33 images
  reviewed, five single-launch GUI roles exited zero, no owned-unit core dump,
  closed owned displays/sinks/game port, and independent eight-second PCM at
  20 ms with 0.996184 correlation. Paused frame 190 stays fixed; resume,
  retained selection and permission-feedback expiry are visible. Initial chat
  overlays and the dark stream-transition capture remain explicit, with video
  restored in the next editing capture.

- R12 main Fabric 1.20.2 acceptance passed in 251 seconds: all 33 images
  reviewed, five single-launch GUI roles exited zero, no owned-unit core dump,
  closed owned displays/sinks/game port and independent eight-second PCM at
  10 ms with 0.994242 correlation. Paused frame 195 stays fixed; resume,
  retained selection and feedback expiry are visible. The open controller
  substantially dims its background, while world-view video is clear. Initial
  chat overlays and dark seek/stream-transition tiles remain explicit, with
  geometry restored in subsequent editing captures.

- R12 main Quilt 1.20.2 acceptance passed in 292 seconds: all 33 images
  reviewed, five single-launch GUI roles exited zero, no owned-unit core dump,
  closed owned displays/sinks/game port and independent eight-second PCM at
  10 ms with 0.999676 correlation. Paused frame 310 stays fixed, resume advances,
  selected text survives state changes and permission feedback expires. Dimmed
  controller backgrounds, initial chat overlays and dark seek/stream-transition
  tiles remain explicit; later video is restored.

- R12 main Forge 1.20.2 acceptance passed in 254 seconds: all 33 images
  reviewed, five single-launch GUI roles exited zero, no owned-unit core dump,
  closed owned displays/sinks/game port and independent eight-second PCM at
  10 ms with 0.998223 correlation. Paused frame 199 remains fixed, resume
  advances, drafts survive state changes and permission feedback expires.
  Dimmed controller backgrounds, initial chat overlays and the dark stream-change
  capture remain explicit, with video restored in the next editing capture.

- R12 main NeoForge 1.20.2 acceptance passed in 267 seconds under the documented
  `earlyWindowControl = false` prerequisite, confirmed in all three playback
  launches; default splash startup remains uncertified. All 33 images were
  reviewed, five single-launch GUI roles exited zero, no owned-unit core dump
  was recorded and owned displays/sinks/game port are closed. Independent
  eight-second PCM has 10 ms lag and 0.995005 correlation. Paused frame 194 stays
  fixed; resume, draft retention and feedback expiry are visible. Dimmed
  controller backgrounds, initial chat overlays and dark seek/stream-transition
  tiles remain explicit, with later video restored.

- R12 main Fabric and Quilt 1.21.1 acceptance passed in 248/294 seconds.
  All 33 original images per case were directly reviewed, five single-launch
  GUI roles per case exited zero, no owned-unit core dumps were recorded and
  owned displays/sinks/game ports are closed. Independent eight-second PCM
  has 10/0 ms lag and 0.995635/0.990503 correlation. Paused frames 198/318
  remain fixed; resume, draft selection retention and feedback expiry are
  visible. Dark seek/stream-change tiles precede restored video; initial chat
  overlays and non-simultaneous saved controller captures remain explicit.

- R12 main Forge 1.21.1 acceptance passed in 263 seconds: all 33 original
  images directly reviewed, five single-launch GUI roles exited zero, no
  owned-unit core dump and owned displays/sinks/game port closed. Independent
  eight-second PCM has 10 ms lag and 0.999263 correlation. Pause holds frame
  206, resume advances, draft selection survives state changes and feedback
  expires. Dark seek/stream-change captures precede restored video; initial
  chat overlays and non-simultaneous saved controller captures remain explicit.

- R12 main NeoForge 1.21.1 acceptance passed in 254 seconds: all 33 original
  images directly reviewed, five single-launch GUI roles exited zero, no
  owned-unit core dump and owned displays/sinks/game port closed. Independent
  eight-second PCM has 10 ms lag and 0.999147 correlation. Pause holds frame
  286, resume advances, draft selection survives state changes and feedback
  expires. Dark seek/stream-change captures precede restored video; initial
  chat overlays and non-simultaneous saved controller captures remain explicit.
  Nineteen of 37 cases are scoped accepted for r12. Fabric 26.1.2 subsequently
  failed as recorded above; seventeen cases were not attempted. Corrected-source
  production/native/final-commit/remote certification is still required.

- The first r10 build failed after 552 seconds and 80 executed tasks, with
  all ten GameTests passed. The README checkpoint rewrite had removed the
  validator's literal `16-artifact` / `21-runtime` labels; the counts were still
  stated in prose, but the documentation contract correctly failed. The labels
  are restored without changing the validator or release scope. The failed log,
  831-input manifest, false result, closure-time README and prior eighteen-file
  bundle are preserved across 22 hashed files. No second build or r10 runtime
  started. The corrected documentation passes all sixteen manifest tests and
  the three focused Gradle manifest/hygiene tasks. Fresh r11 full builds started
  at 07:51 Arizona under `cinemarr-release-audit-r11-builds-20260908.service`;
  its first build failed the independent clock-fixture assertion recorded above.
  No maintained code changed between r10 and r11; the subsequent r12 correction
  changes only the polling test fixture.

- The 14:53 UTC r11 read-only availability checks authenticated from the
  designated credential files. Both retained Windows/ARM guests are installed,
  ready and stopped; all four designated DiscPanel servers are stopped with
  autostart off and Cinemarr disabled. No guest startup, provisioning or server
  mutation occurred. These are prerequisites, not current-candidate runtime
  or native acceptance.

- R9 failed its tenth runtime case after 3,299 seconds: nine scoped accepted
  cases, one failed and 27 not attempted. Forge 1.20.1 correctly rejected the
  protocol-mismatch client, but its Gradle launcher exited 1 when attempting
  unsupported configuration-cache storage. The protocol and command launchers
  omitted the manifest's cache-disable flag, although audio/server launchers
  honored it. The new regression failed on both launchers for all five affected
  targets, then passed after the flag was wired through. All 21 profiles and
  their Quilt/Mod Menu/minimum-loader arguments are checked. Two focused Gradle
  tasks pass (20 tests). The failed run, original receipts and copied Gradle report
  are preserved across 2,288 hashed files. All 52 owned private displays,
  audio sinks and game ports are closed; no owned-unit core dump occurred.
  Neither prior r9 builds nor scoped passes certify the changed launch scripts.
  A separate source-bound corrective Forge 1.20.1 runtime passed in 258 seconds:
  all 33 images directly reviewed, five single-launch GUI clients exiting zero,
  independent eight-second reconnect PCM at 20 ms (correlation 0.993393), no
  owned-unit core dump and restored world/properties. The r9 artifact bytes
  remained unchanged. Baseline vanilla chat warnings and transient seek/stream
  dark tiles remain explicit; later captures show restored video. This is
  scoped corrective evidence only. Fresh r10 forced builds started at 07:39
  Arizona; the first failed documentation validation as recorded above, and
  the second did not run. Full certification remains pending. The failed r9 batch has not been
  resumed or retried.

- The clean-exit gate now sends a normal `WM_DELETE_WINDOW` request only to
  an identity-checked, owned private-X Minecraft window, waits for the actual
  launcher status, and requires exactly one successful command-exit receipt.
  Forced TERM/KILL remains failure cleanup, not acceptance. Both protocol and
  command clients, playback reconnect/pressure peers and final playback clients
  use the stricter exit check. A new Gradle/CI test rejects nonzero exits even
  with a misleading success marker and no core file; real-X tests also trigger
  a deliberate SIGSEGV after the close request without writing a core. All
  seven focused Gradle tasks pass (63 tests). The fresh legacy corrective
  terminal run passed in 357 seconds, with all six GUI launchers exiting zero,
  no systemd core dump, all 28 original images directly reviewed and five
  independently recomputed eight-second PCM pairs at 10–20 ms absolute offset.
  Both world returns, EOS/reconnect/restart, audio drain and closed private
  displays/sinks/port pass; original world/properties and the r8 bundle are
  unchanged. This is scoped corrective evidence, not full certification.
  Both frozen-source r9 builds passed at 06:27 Arizona: 505/495 seconds,
  88 executed tasks and ten required GameTests each, sixteen inspected JARs,
  and all eighteen bundle files byte-identical across 831 unchanged inputs.
  The subsequent 37-case runtime batch failed as recorded above. Its first legacy terminal case
  is accepted in 362 seconds: all six GUI exits zero, no owned-unit core dump,
  all 28 original images reviewed, five independent eight-second PCM pairs
  at 0–40 ms absolute offset, both world returns and closed displays/sinks/
  port. The close camera still crops the top counter band and initial build
  chat is below the picture. The modern terminal case also passed in 243
  seconds: five zero-exit GUI clients, fourteen directly reviewed images,
  three independent PCM pairs at 0–10 ms and terminal drain at -91 dB without
  a toast-audio exception. Initial captures retain the unsigned-chat warning;
  queue/restarted sequences are clear of it. Legacy pressure also passed in
  487 seconds: seven zero-exit GUI clients, 75 distinct reviewed PNGs across
  controls and pressure, four during/recovery PCM pairs at 0 ms and the
  post-reload pair at 20 ms. Browse overload chat partially covers the follower
  picture during the flood; recovery/reload captures are clear. Transient dark
  tiles in control-change snapshots are followed by restored video. Work
  bounds, silent third-peer behavior and cleanup passed. Modern pressure passed
  in 427 seconds: six zero-exit GUI clients, all 69 control/pressure images
  reviewed, four during/recovery PCM pairs at 0 ms and the post-reconnect pair
  at 10 ms, with bounded work, silent third peer and closed resources. Initial
  warning/overload-chat and brief seek-transition dark-picture caveats remain
  explicit. Legacy fault recovery passed in 422 seconds: six zero-exit GUI
  clients, 57 directly reviewed images, three independent post-fault PCM pairs
  at 0/10/0 ms and the final post-reload pair at 0 ms. Distinct recovery
  generations, actual fault HTTP traffic, controls, both clients' two reloads
  and cleanup pass. Modern fault recovery passed in 337 seconds: five zero-exit
  GUI clients, 51 directly reviewed images, three independent recovered PCM
  pairs at 10/0/0 ms and final reconnect PCM at 0 ms. Actual HTTP faults,
  distinct generations, recovered moving video and cleanup pass. Baseline
  vanilla chat warnings and brief seek-transition dark tiles remain explicit.
  The main Forge 1.7.10 case passed in 336 seconds: six zero-exit GUI clients,
  all 39 images reviewed, both clients' two reloads, follower reconnect and
  independent eight-second PCM at 0 ms (correlation 0.987970). The paused
  original world captures are byte-identical; later controls/reload captures
  show recovered video after transient seek/stream dark tiles. Main Fabric
  1.20.1 passed in 263 seconds: five zero-exit GUI clients, all 33 images
  reviewed, follower reconnect and independent eight-second PCM at 10 ms
  (correlation 0.993608). Paused frame/clock and draft/permission controls
  pass; baseline chat warnings and temporary seek-transition dark tiles
  remain explicit. Main Quilt 1.20.1 passed in 303 seconds: five zero-exit
  GUI clients, 33 reviewed images and independent eight-second PCM at 10 ms
  (correlation 0.997900), with follower reconnect and closed resources. Its
  paused clock/frame holds and video is restored by the stream-change capture
  after a dark seek snapshot. Nine scoped cases preceded the failed Forge
  1.20.1 case; the batch is stopped and no full runtime acceptance is claimed.
  Independent handoff/exit guards pass eleven isolated tests,
  requiring every expected GUI role to have one launch and one zero exit,
  with no owned-unit journal core dump. Production/native and final-commit/
  remote gates remain required. The failed r8 evidence remains unchanged.

- R8 is now interrupted and not accepted: its first legacy terminal case
  reported automated success but leader PID 680245 segfaulted four seconds
  after acknowledging disconnect. The local-file-only crash check missed a
  systemd-owned core. The batch was deliberately stopped during its second
  case after 558 seconds: zero accepted, one false-positive receipt, one
  interrupted and 35 not attempted. All original receipts remain unchanged;
  466 evidence files, including the copied core and debugger output, are
  hash-preserved. Private displays, audio sinks and game ports are closed.
  The debugger shows X connection-loss handling during native shutdown. A
  real private-X regression reproduces display loss under the actual cleanup
  function without Minecraft; signaling the private launcher owner first
  passes that regression and all four private-X tests. This is only a
  cleanup-order correction: at that checkpoint the crash-detection gap was
  still open. The subsequent exit oracle and scoped corrective proof above
  address it; fresh complete certification remains open. No subsequent source
  is certified by r8 builds.

- The subsequent `e7d5f00` final gate was interrupted by graphical-session /
  user-manager shutdown after three closed cases (1,923 seconds, SIGTERM).
  Its fresh `session2` attempt failed the legacy terminal case after the second
  world return (741 seconds, exit 1, no signal, zero completed runtime cases).
  Both attempts remain preserved and neither counts as full acceptance.
  The follower retained a healthy pre-unload timeline, which the stability
  waiter incorrectly accepted without fresh samples. Separately, server viewer
  departure did not release the client's unacknowledged transfer reservation;
  returning playback stalled until its 30-second expiry. New regressions
  reproduce stale/stalled readiness and require eight seconds of fresh bounded
  telemetry. All three managers now release grants for sessions no longer
  tracked, retaining other viewers' and still-visible streams' reservations.
  The world-return observer rejects the abandoned-window error even after
  eventual recovery. Fourteen log-polling, ten pressure and eight world-change
  tests pass; core, modern and legacy unit checks pass. The corrective legacy
  terminal run passed in 369 seconds: 28 images directly reviewed, five
  independent eight-second PCM comparisons at 10–50 ms, both world-return
  cycles without window rejection, and complete cleanup. The subsequent
  fixture audit corrected the readiness check to require `started=true` only
  in legacy logs, where that field exists; all fourteen polling tests pass
  using the distinct modern/legacy formats. Product Java changed: historical r3/r4/r5 bundle
  acceptance cannot certify the next build. Phases 8–10 remain open.
- The first r6 build was deliberately interrupted after 300 seconds, with
  unchanged inputs, SIGTERM/exit 143 and no reproducibility pass. Review found
  that replacing every visible TV with another TV in the same watch party
  also resets the client's assembler, while session-only retention would keep
  its old grant. A new core regression fails the session-only policy. All
  managers now provide both current screen/session identities and previous
  screen IDs; only a continuously visible screen can retain a window. The
  regression also preserves ownership when one old party screen remains.
  The earlier 369-second corrective run retains its narrower source identity;
  fresh r7 build/runtime certification is required after this extension.
- Both r7 forced builds passed (521 and 501 seconds), executing all 87 tasks
  and all ten required GameTests each. All sixteen artifacts were inspected
  in both builds and the complete eighteen-file bundles match byte-for-byte.
  Independent binding verifies all 829 frozen non-documentation inputs and
  both retained/current bundles. The serial 37-case runtime batch failed its
  fifteenth case after 4,720 seconds; it is not accepted. Exact-byte production/native acceptance,
  final documentation/commit, the final local gate and exact-SHA remote CI
  remain outstanding.
- Both r7 terminal cases now have closed scoped acceptance: legacy completed
  in 344 seconds with all 28 images directly reviewed and five independent
  eight-second PCM pairs at 0–20 ms absolute offset; modern completed in 249
  seconds with fourteen images and three PCM pairs at 0 ms. Both legacy world
  returns have clear resumed video without abandoned-window errors. Modern
  initial views retain the vanilla chat-warning toast; a brief post-EOS
  follower sound independently matches the installed toast-dismissal asset
  (correlation 0.902725), not digital silence. No runtime was retried and no
  maintained threshold changed. Closed logs, displays, audio sinks and ports
  pass. Neither receipt certifies the complete batch or exact-byte
  production/native acceptance.
- R7 legacy pressure is also accepted in 511 seconds: 75 distinct images
  directly reviewed across pressure and normal controls, four independent
  pressure PCM pairs at 40 ms and the final post-reload/reconnect pair at
  50 ms absolute offset. The 262-request browse flood and 3,450-request
  network-only peer leave zero orphaned transfer/egress diagnostics. Both
  clients completed two reloads; all owned displays, audio sinks and the
  port are closed. Browse-overload feedback partly covers the follower's
  lower view; later recovery and post-reconnect video are clear.
- R7 modern pressure is accepted in 421 seconds: all 69 images directly
  reviewed, four independent eight-second pressure PCM pairs at 0 ms and
  the final reconnect pair at 10 ms. The browse flood issued 264 requests;
  the network-only peer issued 3,720 requests over 40 seconds, without
  orphaned transfer/egress work or local media audio. Closed logs, displays,
  audio sinks and the game port pass. The recorded visual limitations remain
  explicit: browse-overload feedback covers part of the follower's lower view,
  baseline/reconnected world views have vanilla chat-warning toasts, and
  immediate seek/stream-change images show backing texture before later
  recovery.
- R7 legacy fault recovery is accepted in 418 seconds, with all 57 images
  directly reviewed. The three post-fault eight-second PCM pairs measure
  10/10/30 ms absolute offset; the final post-reload/reconnect pair is 30 ms.
  Actual HTTP phase traffic, distinct recovery generations, two reloads per
  client, permissions/edit retention, clear later recovery video and closed
  logs/displays/audio/port checks pass.
- R7 modern fault recovery is accepted in 354 seconds: all 51 images directly
  reviewed, three post-fault eight-second PCM pairs at 10/10/0 ms and the final
  reconnect pair at 0 ms. Distinct recovery generations and actual phase HTTP
  traffic, controls and cleanup pass. Baseline world images retain vanilla
  chat-warning toasts; all eighteen recovered-video images are unobstructed.
- R7 main Forge 1.7.10 is accepted in 338 seconds: 39 images, two resource/sound
  reloads per client, follower reconnect and independently recomputed eight-second
  PCM at 10 ms (correlation 0.993577), with closed logs/displays/audio. Paused
  frames and draft selection remain stable; immediate seek/stream transitions
  show backing texture before later recovery. The close camera crops the fixture
  timestamp, but moving patterns and counters identify the program.
- R7 main Fabric 1.20.1 is accepted in 255 seconds: 33 directly reviewed images,
  follower reconnect, closed client logs/displays/audio, independent eight-second
  PCM at 10 ms (correlation 0.998570). Pause/seek, permissions and retained drafts
  pass, with vanilla baseline toast and temporary seek/stream backing-texture
  caveats explicitly retained. The focused regression recheck
  passes 14 log-polling, eight world-change and ten segment-pressure tests.
- R7 main Quilt 1.20.1 is accepted in 305 seconds without the earlier startup
  cache race: 33 images directly reviewed, independent eight-second PCM at
  10 ms (correlation 0.998408), follower reconnect and closed client logs,
  private displays and audio sinks. Pause/draft/permission checks pass;
  baseline chat-warning toasts and immediate playing-seek backing texture
  remain explicit limitations. Video is visible again in the stream-change
  and editing captures.
- R7 main Forge 1.20.1 is accepted in 261 seconds: 33 original images reviewed,
  independent eight-second PCM at 20 ms (correlation 0.997478), follower
  reconnect and closed client logs/private displays/audio sinks. Controls,
  pause and drafts pass; baseline chat-warning toasts and immediate playing
  seek/stream backing texture remain explicit. Subsequent editing and final
  controllers show recovered video.
- R7 main NeoForge 1.20.1 is accepted in 267 seconds: all 33 images reviewed,
  independent eight-second PCM at 10 ms (correlation 0.995411), reconnect and
  closed client logs/private displays/audio sinks. Pause/draft/permission checks
  pass, with baseline toast and temporary seek/stream backing-texture caveats.
  A read-only guest audit at 11:46 UTC confirms the
  retained Windows/ARM disks are ready and stopped, without starting either;
  this does not certify the r7 native payload.
- R7 main Fabric 1.20.2 is accepted in 248 seconds: 33 images, independent
  eight-second PCM at 10 ms (correlation 0.998523), reconnect and closed logs,
  private displays/audio. UI strongly dims background video but controls stay
  readable; clear paused-world views identify the fixture and held frame.
  Baseline chat toasts and temporary seek/stream backing texture are retained
  as caveats.
- R7 main Quilt 1.20.2 is accepted in 295 seconds: all 33 images reviewed,
  independent eight-second PCM at 10 ms (correlation 0.999878), reconnect and
  closed logs/private displays/audio sinks. Strong UI background dimming does
  not obscure controls; paused world frames are stable and identifiable.
  Baseline toast and immediate stream-change backing texture remain caveats.
- R7 main Forge 1.20.2 is accepted in 265 seconds: all 33 images reviewed,
  independent eight-second PCM at 10 ms (correlation 0.995061), reconnect and
  closed logs/private displays/audio. Pause/seek, feedback lifetime and draft
  retention pass, with the strong UI dimming, baseline toast and temporary
  seek/stream backing-texture caveats retained. Fourteen of 37 cases have
  completed scoped reviews (564 distinct images).
- R7 stopped on main NeoForge 1.20.2 follower startup (107 seconds, exit 1,
  no signal). Its first exception is `FileSystemNotFoundException` in
  SecureJarHandler's union-filesystem lookup from the early loading-window
  renderer; repeated `Already building` errors follow. The source and bundle
  were unchanged. Fourteen cases passed their scoped reviews, one failed and
  twenty-two were not attempted. The failed batch, crash report and original
  logs remain preserved. Root-cause correction and fresh certification are
  required; no retry or full-runtime pass is claimed.
- A standalone probe of the installed SecureJarHandler library reproduced
  missing-existing-filesystem lookups without Minecraft/Cinemarr. The
  NeoForge 1.20.2 gate now explicitly selects FML's supported no-splash
  configuration, preserving full in-game test coverage and strict A/V limits.
  Fifteen configuration/log-polling tests pass after new assertions failed
  against the prior implementation. The corrective runtime passed in 263
  seconds, with all 33 original images reviewed, independent eight-second PCM
  at 0 ms/correlation 0.991556, all five GUI launches using the explicit
  no-splash path once, and original world/properties restored. Splash-enabled
  startup remains uncertified. Failed-batch evidence is
  hash-preserved in place and its owned X/audio/game-port resources are closed.
- Follow-up review reproduced an older wrong-protocol wrapper retry: one
  failed launch invoked a second launch into the same evidence paths. It now
  propagates the first result, with success/failure coverage on all 21 profiles.
  Sixteen focused tooling tests, eight world-change and ten pressure tests pass.
  Fresh r8 certification is required after these launcher-only changes; the
  corrective runtime retains its earlier source binding and r7 remains failed.
  Both r8 forced builds passed in 505 and 494 seconds, each executing all 87
  tasks, inspecting all sixteen artifacts and passing all ten GameTests. Both
  complete eighteen-file bundles match byte-for-byte, and all 829 frozen
  non-documentation inputs were unchanged at that checkpoint. The fresh
  37-case runtime batch was subsequently interrupted on the teardown finding
  above; no r8 runtime acceptance is claimed.
- R8 operational handoff checks require the full independently closed
  37-case/41-review development audit before any external deployment or native
  reuse. Six isolated guard tests reject absent reviews, live/failed batches,
  stale source bindings and changed evidence. The precommit guard also checks
  the preserved r7 failure hashes and requires both failed r7 and corrective
  NeoForge evidence in the deep scan. A separate source-only scan passed for
  its recorded 840 inputs/739 decoded payloads, with no matches or errors;
  it authenticated DiscPanel from the designated file and found all four test
  servers stopped/autostart-off. This is not the final deep evidence scan.
- The final-commit gate on `065b6c5` failed Quilt 1.20.1 startup: the second
  client's Loom cache rebuild removed the intermediary JAR while the first
  client's Quilt remapper was reading it. Eight preceding cases completed
  cleanly with scoped direct-image/PCM reviews; they do not make this a full
  gate pass. The attempt was deliberately stopped after the failure (3,765
  seconds, exit 143), with unchanged source and original logs preserved.
  The interrupted next Forge case's original world/properties were restored
  and hash-checked; its generated world remains retained. All Quilt profiles
  now complete startup sequentially before full simultaneous A/V testing.
  Three new tests cover every maintained Quilt startup order, terminal
  failure without retries, and early fatal-loader detection. All twelve
  log-polling tests pass. The fresh corrective Quilt run passed in 293 seconds
  on unchanged source: all 33 images directly reviewed, independent eight-second
  PCM offset 10 ms/correlation 0.999732 and complete cleanup. Both clients
  started once, with the first ready before the second began. Both r5 builds
  passed (542 and 517 seconds), each executing all 87 tasks and all ten
  GameTests. Their complete eighteen-file bundles match r3/r4. An independent
  continuity audit preserves the old source identities and verifies the new
  corrective review. The refreshed deep secret scan passed across 60,242
  files and 944,978 decoded payloads with zero matches/errors. The corrective
  commit and fresh complete final-commit local/remote gates remain open.
- Latest local reports contain 193 core, 40 modern, and 238 legacy tests with
  zero failures/errors and one/two/two opt-in skips. Source-layout and focused
  observer checks pass. These are targeted verification, not final release gates.
- Live legacy resource reload, terminal/queue behavior, and same-JVM dimension
  transitions exposed and prompted real fixes. Their accepted source-bound
  receipts remain historical after subsequent transport changes.
- Real browse overload reproduced playback starvation. Independent bounded
  browse/playback workers corrected it; legacy and modern browse-only runs
  passed direct image and physical PCM review on their recorded source.
- The pressure supplement now includes a third, network-only real Minecraft
  peer. Ordinary viewers retain strict physical A/V and underrun checks.
  The first legacy attempt failed an observer departure-boundary bug; its
  correction is regression-tested. The second completed both pressure/recovery
  phases but was interrupted by SIGTERM before normal controls/reload/reconnect
  finished. It is not an accepted full run. Manual cleanup and original
  world/properties restoration are recorded.
- Review also found departed-player rate-limit entries retained by all three
  server managers. Disconnect and close now release them; tests and live
  diagnostic bounds cover the fix. Both complete `segment3` pressure runs below
  were made after this correction.
- Modern `segment3` completed in 413 seconds with unchanged source. Its scoped
  pressure review accepts 36 directly reviewed images, all four eight-second
  PCM comparisons at 10 ms, real peer overload and complete cleanup. It does
  not certify final packaged bytes or substitute for full control-image review.
  Legacy `segment3` completed in 481 seconds with unchanged source and 42
  directly reviewed images, all four PCM comparisons at 20 ms, bounded real
  peer traffic and clean teardown. Its six post-reload/reconnect views have no
  debug overlay. Incidental sheep at the lower edge of some earlier pictures
  are recorded; the identifying moving program remains visible.
- The local `releaseMatrixGate` now requires all 16 artifact inspections,
  GameTests, the complete 21-profile runtime gate and existing Quilt Mod Menu /
  minimum Fabric Loader supplements. The successful Gradle dry run verifies
  dependency wiring only. Newly added post-fault physical checks and both
  corresponding runtime supplements are required locally and in CI; nine
  focused shell regressions pass. Modern `physical1` finished in 327 seconds:
  eighteen post-fault images directly reviewed, three PCM pairs at 0–10 ms,
  and clean shutdown. Legacy `physical1` passed on the corrected egress source
  in 405 seconds: eighteen post-fault images directly reviewed, all three PCM
  pairs at 10 ms and complete cleanup. These scoped reviews do not replace
  full control-image or final packaged acceptance.
- Further requirement-5 review reproduced a multi-client reentrant-clear
  egress exception. Two new regressions failed on the old implementation;
  the active-ring guard now passes all eleven scheduler tests plus complete
  core/modern suites. The three added tests also cover same-key replacement.
  Earlier runtime receipts predate this latest correction, so final affected
  runtime and packaged acceptance remain required.
- The forced `release-audit-r1-20260907` attempt failed its first build after
  531 seconds and 70 executed tasks. All ten GameTests passed, but seventeen
  assertions in the log-polling tests failed because their synthetic healthy
  logs lacked the audio timeline required by the stricter runtime checker.
  No second build ran; the failed log, unchanged 825-input manifest, false
  reproducibility receipt and previous bundle remain preserved. The fixtures
  now include that prerequisite, with independent negative checks for missing
  timelines and active underruns in all three closed client logs. The live
  checker was not weakened. The GameTest gate also now rejects passing subsets
  instead of accepting any passing count. Nine tooling tests pass locally.
- The continuing phase-3 audit reproduced five validator omissions without
  modifying the manifest: an unrelated Quilt version, an invalid primary
  runtime name, legacy Java 25 runtime/bytecode, a nonexistent client launch
  task and protocol version 9 all passed both manifest and repository checks.
  The current manifest still contains the intended values. These are missing
  rejection guarantees, not evidence that the actual builds used those mutated
  settings. Thirteen manifest tests now pass: runtime identities must match
  their artifact/version, Java declarations are checked against the actual
  target/root builds, runtime task typos fail, and the protocol must match the
  wire constant. Compatibility rows are derived from the manifest, removing
  the validator's second hand-maintained version table. These regression tests
  are now required by the local manifest gate as well as CI. A fresh full
  preflight/build attempt is required; the full-diff review remains open.
- The complete tooling preflight passed all twenty tasks in twelve seconds,
  including private-display isolation/teardown. `release-audit-r2-20260907`
  was deliberately stopped after 371 seconds to address the subsequent
  legacy ingress/hello finding below. Its unchanged-input receipt records
  SIGTERM/exit 143 and no reproducibility pass; the service cgroup is empty.
  The prepared runtime batch is still bound to this unsuccessful attempt,
  refuses to start, and must be rebound only after a new successful build pair.
- Phase-5/6 source audit found both legacy Netty-to-tick inboxes unbounded,
  ahead of the application rate limiter, and repeated legacy hellos accepted
  without a pending-connection check. The new shared inbox caps item and
  encoded-payload-byte retention globally/per connection and drains peers
  fairly, at most 128 packets per tick. Overflow closes only the offending
  connection. Legacy hello now uses `HelloGate<NetworkManager>` so duplicate
  hellos and old connections cannot publish a new hello/session. Disconnect
  removes owned inbox state; client dispatch also checks current connection
  identity. Six inbox tests cover fair drain, byte/item/overflow bounds,
  callback teardown/failure/refill, concurrent floods and 1,000 disconnects;
  a shared hello regression covers replacement/duplicate connections. The
  legacy release build passes 229 tests (two opt-in skips) in 24 seconds,
  including reobfuscated artifact verification. Live acceptance remains open.
- The subsequent `inbox1` pressure run failed its recovery oracle after 258
  seconds: it required a global zero-transfer sample while both ordinary
  viewers were still streaming. Its five recovery samples cannot identify
  the remaining grant's owner, so the failed run is not accepted. Diagnostics
  now independently count disconnected owners' grants/egress items/bytes;
  every pressure/recovery sample requires all three counts to be zero. Tests
  reject missing fields, retained or resurrected orphan work, without
  requiring connected viewers to become idle. The 26.x egress byte budgets
  now explicitly match the modern/legacy per-client and per-session limits.
- Decoder review added pre-allocation PCM retention limits (64 MiB/segment,
  16,384 audio frames, 1 MiB/frame), overflow-safe sample arithmetic, a
  128 MiB individual RGBA-output limit and interrupt checks around decode
  work. This bounds retained Java/output buffers, not all internal FFmpeg
  memory or the duration of an already-running native call. Updated root and
  legacy verification pass. Legacy `ownership1` completed in 488 seconds on
  unchanged source: 42 pressure/post-reload images directly reviewed, all four
  eight-second PCM comparisons at 20 ms, zero orphaned transfer diagnostics,
  two reloads per client, follower reconnect and clean teardown. This is
  scoped development acceptance, not all normal-control image review or final
  artifact/runtime certification. Modern `ownership1` also passed in 418
  seconds on unchanged source: 36 pressure images directly reviewed, all four
  eight-second PCM comparisons at 0 ms, zero orphaned resources and complete
  closed-log/private-display/audio/port cleanup. The subsequent `release-audit-r3`
  forced build pair passed; r1/r2 failures remain preserved and neither
  is counted as a completed candidate build. The r3 runtime batch checked
  both new builds and bundle hashes before starting.
- Both r3 builds passed (550 and 517 seconds): all 87 actionable tasks
  executed in each, all ten required GameTests passed in each, and all sixteen
  artifacts were inspected. The complete eighteen-file bundle is byte-identical
  across both builds and the 829 non-documentation source inputs are unchanged.
  The serial 37-case r3 development-runtime batch has started. Legacy terminal
  acceptance passed in 365 seconds: all 28 required images directly reviewed,
  five independently recomputed eight-second PCM comparisons at 20–50 ms,
  both same-JVM dimension cycles, queue/EOS/reconnect/replay/stop and closed
  resource checks passed. Modern terminal acceptance passed in 244 seconds:
  fourteen images reviewed and three PCM pairs at 0 ms. One EOS capture
  contains a measured vanilla toast-dismissal transient, independently matched
  to the installed asset; it is not described as digitally silent. The
  maintained thresholds were unchanged and the runtime was not retried.
  Both pressure cases are now accepted, including their ordinary controls:
  legacy 484 seconds / 75 distinct images, modern 416 seconds / 69 images.
  Each has five independently recomputed PCM pairs (legacy 10–30 ms,
  modern 10 ms), strict closed logs and zero orphaned transfer resources.
  Both fault cases are accepted with their ordinary controls: legacy 407
  seconds / 57 distinct images, modern 342 seconds / 51 images. All three
  injected-fault recoveries on each platform have independently recomputed
  eight-second PCM checks (legacy 20–50 ms, modern 0–10 ms), clear later
  recovery pictures and strict closed-log/owned-resource checks. The main
  legacy matrix case is also accepted (336 seconds, 39 images, final PCM
  20 ms), as is Fabric 1.20.1 (249 seconds, 33 images, PCM 10 ms).
  Quilt 1.20.1 (282 seconds, 33 images, PCM 0 ms) and Forge 1.20.1
  (266 seconds, 33 images, PCM 20 ms) are also accepted.
  NeoForge 1.20.1 is accepted too (280 seconds, 33 images, PCM 10 ms).
  Fabric and Quilt 1.20.2 are accepted (247 / 292 seconds, 33 images each,
  PCM 10 ms each). Forge and NeoForge 1.20.2 are accepted (254 / 263 seconds,
  33 images each, PCM 10 / 0 ms). Fabric and Quilt 1.21.1 are accepted
  (256 / 279 seconds, 33 images each, PCM 10 ms each).
  Forge 1.21.1 is accepted (260 seconds, 33 images, PCM 10 ms).
  NeoForge 1.21.1 is accepted (255 seconds, 33 images, PCM 10 ms), as are
  Fabric and Quilt 26.1.2 (262 / 306 seconds, 33 images each, PCM 0 / 10 ms).
  Forge and NeoForge 26.1.2 are accepted (257 / 252 seconds, 33 images each,
  PCM 10 ms each), as is Fabric 26.2 (260 seconds, 33 images, PCM 20 ms).
  Quilt, Forge and NeoForge 26.2 are accepted (297 / 249 / 253 seconds,
  33 images each, PCM 0 / 10 / 20 ms). All 21 main-matrix profiles now have
  complete direct-image, PCM and closed-resource reviews. Together with the
  six terminal/pressure/fault cases, all five Mod Menu cases and all five
  minimum-loader startup cases, all 37 cases have scoped acceptance.
  The serial automated batch completed all 37 cases successfully in 9,769
  seconds and exited. Quilt 1.20.1 with Mod Menu 7.2.2 has 33 directly
  reviewed images, independently recomputed PCM offset 0 ms, and three
  closed client logs proving Mod Menu loaded; the source-latency extension
  filter was requested in all three clients. Fabric 0.19.2 startup is
  independently verified on all five maintained Minecraft versions, including
  invalid-config rejection and clean shutdown; this is not playback coverage.
  The other four Mod Menu cases also have 33 images each directly reviewed,
  independently recomputed PCM offsets of 0–20 ms, actual plugin-load proof,
  strict closed client logs and absent private X/owned audio resources.
  These development acceptances do not replace production/native tests or
  the final-commit local and remote release gates.
  Read-only Proxmox and DiscPanel preflights authenticated from the
  designated files and found both installed guests ready/off and all four
  designated servers stopped/autostart-off. No external deployment or guest
  startup occurred during these preflights.
  Subsequent r3 deployment completed in 160 seconds: all four canonical
  server JAR hashes match the frozen candidate, hash-verified disabled
  rollbacks remain available, and all servers returned stopped/autostart-off
  with Cinemarr disabled, unrelated mod settings unchanged and overrides
  restored. The four serial packaged real-Plex cases subsequently passed in
  915 seconds, with all 156 images directly reviewed, independently recomputed
  eight-second PCM pairs at -10 to 20 ms lag, exact production launch/Jammarr
  provenance, returned-to-baseline diagnostics and closed private resources.
  The four server-recovery cases also passed in 438 seconds and a fresh
  authenticated audit verified all four servers stopped/autostart-off with
  Cinemarr disabled. The two restart-mid-build lifecycle cases then passed in
  163 seconds; all six recovery/lifecycle cases have an independent closed
  audit and a fresh four-server stopped-state check. Retained Windows/ARM
  native reuse subsequently passed: Windows run `20260908T035508Z` and ARM
  run `20260908T035922Z`, without reprovisioning. All three decoder resolutions
  passed on each guest; native payloads and shared decoder/core classes match
  all sixteen r3 JARs. Both retained guests passed the installed/ready/off
  audit. ARM is emulated functional/ABI coverage, not hardware performance.
  These results do not certify the final commit or remote CI.
  The retained
  production runtime inputs passed all four read-only launch prerequisites,
  with explicit Java 25 selection for 26.2. Fresh Plex metadata confirms the
  film's single audio track and available subtitle, so these cases explicitly
  exercise subtitle switching rather than a nonexistent alternate audio track.

Historical r4 checkpoint (superseded by the r7 product changes above):

The task/project validation gap found after r3 is now fixed: wrong-project,
Quilt-override-as-primary and isolated-task-for-root mappings are rejected.
All sixteen manifest tests pass, as do source layout and release hygiene.
Verification families and isolated build ordering now derive from the manifest;
the complete `releaseMatrixGate` dry run passes with all required dependencies.
This dry run executes no tests or builds. Exactly three non-documentation
inputs changed after the closed r3 native batch: `build.gradle`,
`scripts/target-matrix.py` and `scripts/test-target-matrix.py`. Both new frozen
`release-audit-r4-20260908` builds passed (536 and 508 seconds), each executing
all 87 actionable tasks and all ten GameTests. All eighteen bundle files match
each other and r3. `release-audit-r4-byte-continuity-20260908.json` explicitly
binds the new source to the unchanged bytes and verifies the 41 scoped r3
development reviews plus closed packaged/native audits. The r3 reviews retain
their original source identity; the old source manifest is not relabelled.
The final source/diff review pass is recorded in release acceptance. The deep
configured-secret scan passed across 57,675 files and 927,188 decoded payloads,
with no matches or scan errors. Final-commit local/remote gates remain open.

Current remaining execution sequence (after the failed r13 batch):

1. The observed suspension/metadata-publication race and misleading idle
   status message have an implemented fix and scoped corrective acceptance.
   Its r13 contextual-feedback regression also has an implemented correction
   and completed 28-image/five-PCM-pair corrective terminal acceptance.
   R14's first full build was session-interrupted after the tooling preflight;
   no resumed build is running. Resume on the new device only when directed.
   Retain the regression and corrective evidence while certifying the full
   changed candidate. The fix must continue to preserve immediate unloaded-TV
   media retirement and strict rejection of superseded completions.
   Original implementation requirement: fix the race and misleading idle
   status message. Preserve immediate unloaded-TV media retirement and strict
   rejection of completions superseded by stop, replacement, removal or close.
   Cover initial play, seek/stream changes, pause/suspend and subsequent recovery
   with deterministic ordering regressions and a corrective real-client case.
   Preserve r12's failed batch, its red regression and scoped pressure evidence;
   repeat the required supplements for the corrected candidate.
2. Preserve both scoped post-fault acceptances and repeat required supplements
   with the final candidate. Older adverse runs without post-fault PCM/views
   remain weaker historical evidence, not substitutes for these checks.
3. Refresh the source/test/full-diff/documentation review for the accumulated changes.
   Any new failure returns to its owning implementation phase; added scenarios
   alone are not passing evidence.
4. Both r13 forced builds passed with identical eighteen-file bundles, all
   sixteen artifact inspections and all ten GameTests, but r13 runtime failed.
   Complete r14 reproducibility from the corrected frozen inputs and certify all
   37 runtime cases with their required direct-image/physical-PCM/closure reviews.
   Historical builds and scoped corrective passes do not close this requirement.
   The complete local gate must still run again on the final commit.
5. Repeat exact-byte production-client real-Plex, recovery/lifecycle and native
   acceptance for the changed bundle. Reuse the retained installed Windows/ARM
   guests, leaving them stopped afterward with an independently verified audit.
6. Refresh the deep configured-secret scan over current source, new artifacts
   and closed evidence, retaining failed/interrupted attempts in its scope.
   Update candidate-bound documentation and scan new final-gate/hosted evidence.
7. Commit logically scoped changes, run the complete local release gate on the
   final commit, push it, and require fully green exact-SHA GitHub CI including
   the aggregate release gate and downloaded/local artifact hash parity.
8. Only after all preceding documentation and CI work is complete, report
   feasibility of the newer Jammarr loaders/versions. Do not tag or publish.

Detailed failed, partial and accepted receipts are distinguished in
[release acceptance](RELEASE_ACCEPTANCE.md). Superseded checkpoint narratives
are preserved verbatim in [historical evidence](HARDENING_EVIDENCE_HISTORY.md);
they do not certify the current source or final artifacts.

## 1. Restore truthful prerelease status

- Downgrade all release-ready claims to "1.0 prerelease under hardening."
- Record the exact failing Cinemarr run and identify completed, failed, and skipped gates.
- Preserve the current 16-artifact/21-runtime scope rather than importing Jammarr's version expansion.
- Define three distinct target states: builds, launches, and runtime-certified. Only runtime-certified targets may be advertised as supported.

Completion criteria:

- Documentation, GitHub status, and local evidence no longer contradict one another.
- No document describes the current commit as release-ready while a required gate is red or skipped.

## 2. Fix the known 1.21.1 NeoForge A/V failure without weakening the gate

- Convert the downloaded CI failure into a reproducible regression case.
- Account for the observed failure correctly: both clients remained synchronized within approximately 10 ms until the 60-second fixture ended, then normal buffer exhaustion was counted as an underrun while the gate still required another eight-second stable-playback window.
- Distinguish normal end-of-stream from starvation during active playback.
- Give the fixture enough playback runway for every control operation and stability window.
- Continue requiring zero post-startup underruns and starvations while playback is active.
- Do not retry runtime failures merely to obtain a green result.
- Add explicit tests for active starvation, normal EOS, queue advancement, stop, seek near EOS, and follower reconnect near EOS.

Completion criteria:

- Repeated local `1.21.1-neoforge` runs pass.
- Injected starvation still fails the gate.
- Normal EOS produces a clean terminal transition without being misreported as active-playback starvation.

## 3. Introduce one maintained-target manifest

- Add a Cinemarr equivalent of Jammarr's `gradle/targets.json` for all 16 artifacts and 21 runtimes.
- Include Minecraft version, loader, build/runtime Java, bytecode level, verification task, artifact path, Quilt reuse, runtime tasks, configuration-cache policy, GameTest applicability, and certification status.
- Generate or validate the following from the manifest:
  - Gradle verification families and the canonical artifact index.
  - GitHub Actions artifact/runtime matrices.
  - Dedicated-server gate routing.
  - Artifact count and release-bundle inspection.
  - Supported-version documentation.
- Validate duplicate IDs and artifact paths, absent projects or tasks, invalid Quilt mappings, stale counts, unsupported Java combinations, and undocumented targets.

Completion criteria:

- Changing a target in one place either updates every consumer or fails validation.
- No second hand-maintained release matrix remains.
- The manifest deterministically describes exactly 16 artifacts and 21 runtimes.

## 4. Consolidate duplicated platform sources

- Consolidate byte-for-byte-identical sources shared by the 1.20.1/1.20.2 family and the 26.1.2/26.2 family into family-shared source sets.
- Keep Minecraft-version and loader differences in narrow adapter or shim modules.
- Add a drift check that rejects reintroduced duplicate copies or unexplained divergence.
- Do not force Forge 1.7.10 through modern abstractions where that would make its Java 8 boundary brittle.

Completion criteria:

- Shared behavior has one implementation.
- Platform adapters remain thin and contain only genuine loader/version differences.
- All existing artifacts retain their current feature coverage and continue to build.

## 5. Bound asynchronous work and transport resources

- Replace modern and legacy unbounded fixed-thread-pool queues with a shared bounded executor.
- Define limits for queued Plex metadata work, playback starts, segment fetches, retries, and decoder/probe work.
- Surface overload as a redacted, actionable response and diagnostic counter.
- Add generation and cancellation ownership so late browse, start, restart, seek, or segment completions cannot mutate a superseded session.
- Ensure shutdown rejects new work, cancels pending work, closes returned media handles, and drains main-thread completions safely.
- Introduce fair video egress scheduling with per-client, per-session, and global item/byte limits plus bounded per-tick draining.
- Ensure one client cannot monopolize segment delivery.
- Bound cached segment bytes as well as entry count; cap indexes, concurrent fetches, retry delay, retained buffers, and file/HTTP resources.

Completion criteria:

- Overload, cancellation, disconnect, restart, and server-stop tests prove bounded queues and memory.
- Stale completions cannot publish state or media from an obsolete generation.
- No media handles, executors, buffers, or transfer grants survive their owning lifecycle.

## 6. Strengthen protocol negotiation

- Preserve Cinemarr's required-client policy; Jammarr's optional-client behavior does not fit clients that must render and decode television media.
- Extend hello negotiation with explicit feature bits and negotiated limits for chunk size, transfer window, and health reporting.
- Reject incompatible or excessive peers consistently across Fabric, Forge, NeoForge, Quilt runtime reuse, and legacy Forge.
- If the wire format changes, deliberately bump the protocol and update every adapter, fixture, mismatch probe, release manifest, and documentation reference together.
- Add tests for disconnect/reset, duplicate hello, timeout, malformed capabilities, overflow, and negotiated-limit enforcement.

Completion criteria:

- No media request is accepted before a valid hello.
- Every adapter enforces the same negotiated bounds and mismatch behavior.
- A protocol change cannot leave a stale adapter or fixture silently using the prior format.

## 7. Apply the confirmed legacy improvements

- Fix `LegacyVideoScreen.initGui()` so rebuilding the UI does not discard unsubmitted search/session text, selection, cursor position, or focus.
- Cover state updates, scrolling, queue toggles, volume changes, screen toggles, and other rebuild paths.
- Keep sound-system reload detection. Measured two-client evidence showed that Paulscode's raw-stream cursor could not satisfy the legacy A/V synchronization gate, so the PCM queue now uses a bounded, lifecycle-owned OpenAL source while preserving positional gain and reload identity checks.
- Add a backend queue-state guard so a stopped or replaced source cannot be restarted by stale buffered state.
- Exercise sound reload, resource-pack reload, world change, disconnect/reconnect, pause/resume, underrun recovery, EOS, and repeated source teardown.
- Preserve the already-hardened legacy envelope and saved-data behavior; extend them with malformed/fuzz cases, schema migration, corrupt/oversized NBT, LRU durability, and reobfuscated restart tests rather than rewriting them.

Completion criteria:

- Forge 1.7.10 passes sustained two-client playback and repeated reload/reconnect/persistence cycles.
- UI rebuilds do not lose in-progress input.
- No duplicate audio, stale playback, or leaked OpenAL resources remain after lifecycle transitions.

## 8. Expand deterministic verification before release evidence

- Add unit and concurrency tests for the bounded executor, fair egress, cache byte limits, generation ownership, terminal A/V semantics, capability negotiation, and legacy UI/backend guards.
- Run all 10 GameTests and inspect their required outcomes rather than relying only on task exit codes.
- Run all 21 fake-Plex profiles with two real clients, identifiable video, correlated audio, controls, mismatch and missing-hello probes, permissions, recovery, and clean teardown.
- Inspect both clients' captured framebuffers on every profile, including the reconnected follower. A common decoded-frame hash or renderer submission log is not proof that the TV is visible rather than obscured by another avatar or UI.
- Run adverse-network cases for transient failure, slow delivery, exhaustion, recovery, unfair-client pressure, and queued-work overload.
- Rebuild all 16 artifacts twice, inspect contents, metadata, natives, and bytecode, and require identical SHA-256 manifests.
- Scan exact source, artifacts, logs, saves, and retained evidence for credentials and private endpoints.

Completion criteria:

- Every local gate is deterministic and green from a clean checkout.
- No process, port, temporary server, credential, or private endpoint remains in the repository or evidence.
- Deliberately injected failures continue to produce red gates with useful diagnostics.

## 9. Re-certify the exact candidate bytes

- Deploy only artifacts from the final local release bundle.
- Re-run credentialed real-Plex controller/UI playback on the representative architectural boundaries: Forge 1.7.10, Quilt/Fabric 1.20.1, NeoForge 1.21.1, and Fabric 26.2.
- Require two clients, identifiable program video, synchronized audible output, controls, alternate streams, ownership rejection, disconnect/reconnect, lifecycle cleanup, and clean server stop.
- Retain exact hashes and classify every maintained target accurately as build-, launch-, or runtime-certified.
- Update release documentation only after these exact bytes pass.
- Do not tag or publish as part of this plan.

Completion criteria:

- Documented evidence points to the final candidate hashes.
- The exact candidate bytes pass real-Plex acceptance and clean teardown.
- No unsupported release or compatibility claim remains.

## 10. Final completion gate: commit, push, and clean GitHub CI

This is the final goal and must happen only after every preceding phase is complete.

- Confirm a clean worktree, review the complete diff, and stage only plan-owned changes.
- Commit the hardening in logically reviewable units.
- Run the complete local release gate once more against the final commit.
- Push the exact tested commit.
- Monitor the GitHub Actions run for that SHA until every required job reaches a terminal state.
- Require all of the following:
  - All 16 artifact jobs succeed.
  - All 21 runtime profiles are represented and succeed.
  - No flaky rerun masks an A/V, lifecycle, transport, or runtime failure.
  - The final `1.0 release gate` runs and succeeds rather than being skipped.
  - The downloadable GitHub candidate contains exactly 16 inspected artifacts.
  - Its manifest and artifact hashes match the locally certified candidate.
  - Local `HEAD` equals `origin/main` and the worktree remains clean.

Any failure returns the work to its owning implementation phase. A pushed commit, a successful build, or a mostly-green matrix is not completion.

## Release-readiness definition

Cinemarr may be described as a 1.0 release candidate only when all ten phases are complete and the final exact commit has a fully green GitHub Actions run, a successful aggregate release gate, matching local/hosted artifact hashes, current real-Plex two-client evidence, and clean teardown. Publication and tagging require separate authorization.

## Historical evidence

Superseded checkpoint notes are preserved in
[Historical hardening evidence](HARDENING_EVIDENCE_HISTORY.md). Their results
remain bound to the candidates and hashes recorded there; they do not check off
any current release gate.
