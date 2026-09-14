# September release-audit remediation

The original [September 12 audit](RELEASE_AUDIT_20260912.md) remains the historical
assessment of `8e1efda`. This working checkout is still a prerelease, not a frozen
or certified release candidate. Historical failed/cancelled runs are not replaced
by claims based on unit tests.

| Finding | Implementation and verification status |
| --- | --- |
| 1. Fresh display edit rejected | Draft payload preserves the expected revision; the authoritative world adapter alone increments it. Packet-to-world regression accepts the first writer and rejects a stale second writer without changing layout or attachment. |
| 2. Missing/partial display pages | All GUI families, including Forge 1.7.10, use a shared persistent page model. All presets, Custom-only fields, current selections, read-only/Quick locks, pending/acknowledgement/error handling and explicit Reload are implemented. Minimum-viewport modern direct captures are readable and show acknowledgement, Quick locks and follower read-only behavior; remaining GUI-family reviews are required. |
| 3. Requested dimensions labeled Actual | Servers no longer publish requested dimensions as effective dimensions. Pages display dimensions measured from the current pipeline's presented decoded source frame, or unknown. During replacement, a retained frame reports its own dimensions. Server-side effective measurements remain unknown; requested manifest bounds are not measured output. |
| 4. Missing expanded acceptance | Main full-video gates, CI and the real-Plex wrapper now invoke the three-TV/two-client display state scenario. It uses the production draft and SET_DISPLAY packet, all layouts, both mappings, paused edits, independent replacement and capacity waiting/admission. The full raster matrix now checks independently derived colors at every output cell. Deterministic failed-replacement isolation and capacity cancellation/requeue are implemented and passed scoped steps locally. Independent original-framebuffer color and sparse-mask checks are implemented and have passed on NeoForge 1.21.1. Other rendering adapters and expanded recovery receipts still require certification. |
| 5. Hosted failures | Fixed empty-name X discovery skipping fallback; terminal observer now compares timeline identities to queue identities; paused observer requires retained evidence for the latest authoritative revision when intermediate packets coalesce. The 26.x metadata-start race is guarded before per-TV preparation. Local private-X infrastructure is now available in an isolated extracted runtime. All 21 fresh runtime results and final hosted CI remain required. |
| 6. Retained pixel storage | Detailed mode and invisible-display cleanup release the CPU raster; derived adapters no longer keep redundant raster references. Legacy upload reuses a bounded direct buffer. Modern adapter-level CPU ownership regression passes; runtime GL lifecycle coverage remains required. |
| 7. Stale presentation side effects | Presentation commands validate the existing attachment/generation and return before tune/restore/tracking mutation. Tests check attachment invariance. Display and presentation updates publish to all TV recipients so paused viewers receive edits. |
| 8. Stale records/guidance | Plan and acceptance introduction identify the latest completed remediation run and exact failure inventory, with the audited run retained historically. README explains requested versus decoded quality, block mapping, acknowledgements and per-TV capacity. |

Additional regression findings: idle pool updates no longer increment stream
generations on every tick, and raster rounding treats exact half-channel ties
consistently despite floating-point transform noise.

## Required scenario inventory

The maintained manifest still has 16 artifacts and 21 runtime profiles (including
five Quilt profiles). Every main full-video profile executes the new state
scenario before the existing controls, followed by a preservation check after
reconnect. Existing terminal, pressure, fault, loader,
resource-reload and native gates keep their thresholds and remain mandatory.
The new state scenario adds no runtime profiles and is not a substitute for the
following still-open feature supplements:

- Original framebuffer captures and independent interior-color comparisons on
  the remaining rendering adapters; NeoForge 1.21.1 now has a passing scoped
  receipt for all layouts, odd/L shapes, nearest filtering and paused redraw.
- Complete current-source runtime receipts for capacity cancellation/fairness
  and failed replacement with a healthy sibling; scoped NeoForge steps alone
  do not certify the full matrix.
- Feature scenes through reconnect, resource reload, server restart, physical
  A/V review, native guests, four real-Plex profiles and required recovery/lifecycle
  supplements against two matching full candidate bundles.
- Final committed-SHA local/hosted checks, successful aggregate, downloaded bundle
  inspection, parity, certification records and secret/evidence scans.

The local X tools were extracted under `/tmp/cinemarr-xvfb`, without modifying the
user's desktop X server or requiring a system package installation. Its launcher
regressions pass with the package library and ImageMagick module paths set. This
temporary runtime is an execution prerequisite, not a repository dependency or
release artifact.

## Fresh remediation checks (September 13)

- Core: 285 tests, zero failures/errors, one credentialed skip. Modern root:
  53 tests, zero failures/errors, two credential/hardware skips. Legacy:
  341 tests, zero failures/errors, two skips. Representative legacy, Forge
  26.1.2 and NeoForge 26.2 build/inspection tasks passed. Later UI probe edits
  still require the final complete matrix.
- The first local NeoForge 1.21.1 diagnostic run passed ordinary controls,
  owner timeline and reconnect checks, then failed the new display scenario.
  Its single-slot control file allowed the following snapshot to overwrite an
  unread edit. Sequence-specific consumption receipts and atomic command-file
  replacement now prevent this race; regression coverage passes. The failed
  evidence remains under `build/audit-remediation-runtime/`. The second run is
  recorded separately under `build/audit-remediation-runtime2/`.
- Display acceptance now captures owner and read-only pages at 320×240,
  attempts disabled follower controls, and checks Apply through the actual
  widget against both clients' authoritative revisions. It also checks queued
  capacity cancellation and requeue. These additions await runtime completion
  and direct review; they do not close framebuffer color/mask requirements.
- Read-only Proxmox preflight authenticated using the supplied credential file:
  both retained native guests have matching ready markers and are powered off.
  DiscPanel and Plex authentication succeeded. All four real-Plex representative
  servers are stopped with autostart disabled and Plex configuration present;
  their Cinemarr artifacts are currently disabled. No remote configuration,
  deployment or guest power state was changed. Sanitized preflight receipts are
  under `build/audit-remediation-evidence/`.

The second diagnostic attempt passed actual display-page Apply, independent
stream replacement, capacity cancellation/requeue and paused mapping/layout
changes, then failed an invalid harness assumption about resume priority. Pause
releases stream slots, so a waiting third TV can legitimately be admitted before
one of the formerly active TVs. That test now isolates the two-TV resume check.
The running shell was also edited during that diagnostic attempt, producing a
trailing shell parse error; attempt 2 is rejected as complete execution evidence.
Its original GUI images and partial review remain clearly scoped.

Attempt 3 held all 880 non-Markdown source inputs fixed and passed 23 named
feature steps, including deterministic failed replacement while both existing
streams kept playing, all paused edits, shared resume, capacity admission and
both custom detachments. It failed only the final harness restore expectation:
detaching the final TV pauses its party, so tuning the Quick TV back must be
followed by an explicit Resume. Both failed attempts remain preserved. Attempt 4
used that corrected restore sequence and a rendered-frame wait for the draft
preview capture. Its 880 non-Markdown source inputs remained fixed throughout.
The complete scoped NeoForge 1.21.1 invocation passed all 25 display feature
steps, ordinary controls, reconnect and teardown. All 13 original display-page
captures were directly reviewed and passed at the minimum viewport. Evidence is
under `build/audit-remediation-runtime4/`; this one-profile diagnostic does not
certify the release matrix, framebuffer colors or real-Plex behavior.

Independent original-framebuffer checks are now being added using gated decoded
source receipts and camera projection. A source inspection found directional
entity lighting on modern video quads; an unlit textured render path replaces
it. Both changes require fresh runtime validation before being accepted.


Attempt 5 passed the complete scoped NeoForge 1.21.1 invocation with all 884
non-Markdown inputs unchanged. Independent projection and decoded-source
sampling matched all 192 original-framebuffer interior samples across paused
4×4 FIT/FILL/STRETCH captures. Detailed mode reported exactly 57,600 retained
source bytes and zero derived textures, releasing its 64-byte block raster.
The four original world captures were directly reviewed; their hashes and
observations are in `build/audit-remediation-runtime5/`.

The next scene revision uses a 4×4 L shape and a 17×11 TV with a central hole.
Its oracle checks missing cells against both exterior background references;
a painted-over hole fails the regression. These masked scenes and their extra
paused transport checks still require a fresh runtime pass. The rectangular
attempt-5 result is preserved with its original scope.


Attempt 6 completed successfully with its 884 non-Markdown inputs unchanged.
All 31 feature steps passed. Six original masked pixel-mode captures passed
2,280 visible-cell color checks and 156 missing-cell background checks, with no
mismatch exceeding the explicit four-level RGB tolerance. The L shape and odd
central hole were directly reviewed in all six pixel captures and both Detailed
captures. Both Detailed transitions released derived storage. This remains one
rendering adapter's scoped evidence, not the full candidate matrix.

Read-only inspection of the cached 1.7.10 command implementation confirmed that
its teleport command does not support yaw/pitch arguments. The harness now
sends coordinates only for that profile, waits for the client to receive that
position, and uses a bounded, acceptance-only client look command. The transport
regression passes; actual legacy runtime validation remains required.


Attempt 7 (Forge 1.7.10) passed ordinary controls, edit retention, owner timeline,
reconnect and 14 feature steps, including actual display-page Apply and its
acknowledgement. Eight original page captures were directly reviewed and passed
at 320×240. Its first world capture failed: the legacy screen transition had
drained the second immediate Escape event, leaving the parent controller UI
open. The original failed capture and result are retained, and all 884 source
inputs remained unchanged during that run. No legacy framebuffer pass is claimed.
The harness now spaces the two screen transitions and requires explicit GUI/HUD
state in both render-thread receipts around a world capture. Fixture clouds are
also disabled so the missing-cell oracle has stationary sky references.


Attempt 8 verified that the legacy GUI and HUD were hidden. Its 48 visible
L-shape color samples passed, but seven missing-cell comparisons failed because
legacy sky color varies spatially between the hole and the two exterior
references. That failed result and all 884 unchanged input hashes are preserved.
The oracle now compares missing-cell pixels with an original capture from the
same camera after an acceptance-only switch suppresses that TV's video quads.
It verifies the suppression state, stable source/camera/presentation, hidden
GUI/HUD, and a visible difference between the video and background captures.
The regression uses a nonuniform background and rejects both painted holes and
an unchanged video image masquerading as a background. Detailed-mode holes use
the same check. Fresh runtime validation remains required; tolerances are not
increased to accommodate the old oracle's false failures.


Attempt 9 completed successfully on Forge 1.7.10 with all 884 non-Markdown
inputs unchanged. All 31 display steps passed, as did ordinary controls,
reconnect, both clients' two resource/sound reloads, physical PCM/health checks
and teardown. Its 2,280 pixel-color samples and 208 paired-background hole
samples passed with unchanged tolerance, including both Detailed scenes and
both derived-storage releases. All 16 original world/background captures were
directly reviewed. This certifies the scoped diagnostic invocation, not the
final candidate matrix or real-Plex bundle.

Attempt 10 (Fabric 26.2) kept all 884 non-Markdown inputs unchanged and passed
all 33 feature steps, including both clients' resource reloads and the paired
framebuffer checks. The complete invocation failed its unchanged zero-active-
underrun gate: the sound engine destroyed live handles before the Fabric
resource-listener callback retired them. No full-pass or reconnect-preservation
claim is made for this attempt. Modern adapters now retire their TV audio handles
at the start of Minecraft's actual sound-engine reload; a fresh runtime is
required to verify that lifecycle correction.

All 16 attempt-10 original world/background images and all 16 display-page
captures were directly reviewed. The minimum-viewport pages are readable,
Quick/read-only locks are visible, and requested quality remains distinct from
measured decoded dimensions. These reviews do not override the audio failure.

Attempt 11 passed the complete Fabric 26.2 invocation with all 885 non-Markdown
inputs unchanged: 33 display steps, both active-TV resource reloads, ordinary
controls, reconnect, preservation of both custom TVs on both clients, physical
PCM/health checks and clean teardown. No active underruns were recorded. This
validates the sound-engine lifecycle correction for this profile; the final
matrix and fresh-server display persistence remain open.

The maintained local full-video gate now starts a fresh server process after the
clients exit and the first server saves cleanly. It compares successful saved-TV
registry restorations with the preceding three-TV client snapshot. The same
check is wired into the real-Plex wrapper. Its temporary chunk loading preserves
pre-existing force-load entries and is removed before the restart. Thirteen
Python harness regressions pass, including lost/changed/duplicate restore
records. Runtime validation of this new restart stage is pending in attempt 12
(Quilt 1.20.1).

Attempt 12 kept all 887 inputs unchanged. Its 33 display steps, original-image
reviews, controls, reconnect, custom-setting preservation, physical PCM/health,
and fresh-server registry comparison passed. Both server processes shut down
cleanly and the gate printed success, but the outer execution tool reported
exit 143. The overall invocation is therefore not accepted as a full pass.
Three isolated fake-service/EXIT-cleanup reproductions returned zero. Attempt 13
repeats Quilt with an independently persisted subprocess exit-code receipt to
investigate the discrepancy; the previous result remains preserved.

Attempt 13 repeated Quilt with the same 887 non-Markdown inputs and an independent
subprocess return-code receipt. The complete invocation returned zero after all
33 display steps, controls, reconnect, preservation, physical PCM/health checks,
fresh-server restoration and teardown passed. All inputs remained unchanged.
The earlier outer exit 143 was not reproduced; attempt 12 remains marked
incomplete rather than being retroactively accepted. A fresh all-target build
and inspection is now required before candidate certification.

The first forced all-target build passed all 16 platform builds, the Java suites
(core 285 / modern root 53 / legacy 341, no failures/errors) and all ten GameTests.
The aggregate failed on stale world-change test logs which omitted the newly
required timeline fields. Those fixtures now distinguish timeline identity
from TV-stream identity and reject a changed timeline on return. A browse-
pressure source assertion also needed to select its own UI-capture branch after
the new display-page branch was added. All 21 Python harness test scripts now
pass. Restart teardown now checks the same Plex-cleanup failure signatures as
the original server shutdown. The failed aggregate remains recorded; it is not
a complete candidate build. Two complete matching builds are still required.

The second all-target invocation completed successfully: all 16 artifacts were
built and inspected, all ten GameTests passed, and all aggregate harness checks
passed. Its independent receipt records return code zero with all 887
non-Markdown inputs unchanged. The complete 18-file bundle is preserved under
`build/audit-remediation-candidate/build2/`. A second complete forced build,
byte-for-byte comparison and the full candidate certification remain required.

The third invocation forced every all-target task and completed successfully
(91 executed tasks). All 887 non-Markdown inputs stayed fixed; all 18 bundle
files match build 2 byte for byte. These bundles contain the implementation
committed as `b381c7096d8b90a790e1fd4905335d8c2af2a320` on the remediation branch.

Hosted run `34754834080` exposed acceptance-environment defects after successful
artifact builds. All five Fabric main profiles failed owned-X discovery because
Loom 1.17.19 automatically launches a second `xvfb-run` when `CI` is present.
Its installed `AbstractRunTask` confirms this behavior; the runner's exact Ubuntu
X tools could discover a simple owned X11 window locally. Acceptance runClient
tasks now explicitly set Loom's `useXvfb` false. An actual Gradle configuration
check with `CI=true` confirmed the setting. The first configuration-only probe
used the wrong root wrapper and failed variant resolution; the corrected probe
used the platform wrapper and passed.

Original hosted framebuffer evidence also showed terrain enclosing a fixed
camera or occupying sparse-screen sight lines. The temporary isolated local
world now uses flat generation with structures disabled; original server
properties and developer worlds are still restored. Five modern profiles passed
all 33 display steps and subsequent client checks but could not shut down the
fresh restart through Gradle console input. That stage now uses the same modern
RCON stop transport as the original server. The two fully completed hosted Forge
profiles (1.21.1 and 26.1.2) remain scoped successes, not an aggregate pass.
Attempt 14 runs the complete Fabric 1.21.1 gate with `CI=true` and the hosted
Ubuntu X tool versions to validate these changes. Source inputs are frozen for
that invocation; previous CI failures and original captures remain preserved.

Windows native run `20260913T113700Z` passed the 144p, 480p and 1080p software
fixtures on Windows 11/amd64 with the windows-x86_64 classifier and verified PE
machine identity. Its input bundle stayed unchanged, all compared production
classes and the embedded core JAR match build 2, and its retained guest is
stopped. An earlier setup attempt failed because the packaged jq library path
was missing; its receipt and verified empty-run-directory cleanup are retained.
The corrected invocation exposes only the required packaged helpers. ARM native
validation is running with a scoped temporary key and exact original-key
restoration checks; no ARM pass is claimed yet.

Attempt 14 completed with return code zero and all 887 inputs unchanged under
`CI=true` and Ubuntu noble's hosted X tool versions. All 33 display steps, eight
framebuffer checks, ordinary controls, reconnect, both clients' resource reloads,
physical PCM/health, three saved-registration comparisons and clean teardown
passed. All 32 original display/world PNGs and 18 controller/feedback PNGs were
directly reviewed. This validates the Loom override and deterministic scene in
a complete Fabric invocation; the final matrix must still cover every adapter.

The attempt-14 outer execution tool subsequently reported 143, after the Python
wrapper persisted and printed the gate subprocess's zero return code and
unchanged-source receipt. The successful gate result and anomalous outer-tool
exit are recorded separately. Subsequent aggregate wrappers give the gate its
own process session so teardown cannot signal the receipt-writing parent.

ARM native run `20260913T115705Z` completed successfully with the frozen bundle:
all three software fixtures passed, the retained guest bundle matches the input
hashes, and the guest is stopped. The scoped SSH key was removed; the original
authorized-keys bytes match the preflight hash exactly, and temporary local key
files were deleted. Together with Windows run `20260913T113700Z`, native decoding
is verified for the production decoder/core bytes shared with build 3.

The final local aggregate on `d823945` failed after 1,463 seconds at the legacy
terminal supplement, with all source inputs unchanged. Both clients received
the queue-advance event and resumed the next generation; immediate preparation
and playback updates replaced the event as the latest log entry before polling.
The observer now requires each client's advance event in the fresh log interval
and matching current playback at that same generation. Missing events, stale
generations and stopped playback remain failures. Hosted legacy acceptance hit
the same timeout; neither failed invocation is accepted as a pass.

Hosted Forge 1.20.2 completed its feature/client stages but the new fresh-process
check exposed a saved-data defect: its Minecraft disk loader dereferences a null
data-fix type for both TV registrations and saved playback. The 1.20.2 factories
now supply a non-null level data-fix type, retaining custom schema decoding. A
regression exercises compressed-file loading through fresh Minecraft storage,
including old-version metadata. This changes the 1.20.2 artifacts, so the earlier
full-bundle equality remains historical evidence and a new matching pair is
required. The native decoder/core and four Proxmox-profile artifacts are outside
this source change; their exact-byte evidence must still be reconciled with the
final bundle.

The compressed saved-data regression passed for both registries and both data
versions. The initial `test --tests ...` invocation subsequently failed because
Gradle also applied the filter to `:core:test`, which has no matching test; the
explicit platform `:test --tests ...` invocation completed successfully using
that test result. Thirteen terminal-harness regressions pass. Of the 21 Python
scripts, only the private-X test failed when invoked without its isolated tool
environment; it passes with the complete X and ImageMagick environment.

The next local legacy attempt (15) was interrupted by a long host pause and
failed before the wrong-protocol client initialized. Its 55,836-second receipt
records unchanged sources and failure; it does not validate the terminal fix.
Hosted run `34756506555` finished with all five Fabric jobs (including Quilt and
minimum-loader checks) and four Forge jobs successful. Forge 1.20.2 and legacy
hit the two failures above; five NeoForge builds failed on upstream HTTP 502.
The aggregate was skipped. The upstream metadata endpoint subsequently returned
HTTP 200, but that is not a replacement for successful job execution.

Proxmox candidate deployment passed exact-JAR checks. The legacy, Quilt and
NeoForge recovery cases passed; Fabric 26.2 failed when DiscPanel returned HTTP
500 on server start. Its automatic override restoration also failed. After the
local SSH connection timed out, the remote journal established that the runner
had finished. Follow-up restoration independently verified all four servers
stopped with Cinemarr disabled, exact original configuration bytes/overrides,
and other mod settings preserved. Three scoped recovery passes remain valid;
the failed Fabric case and failed outer transaction are retained separately.

Fabric recovery retry completed successfully on September 14 with its candidate
JAR unchanged. Its independent outer receipt returned zero and the remote
transaction verified original configuration bytes, overrides and other mod
settings, stopped state and disabled Cinemarr. All four recovery profiles now
have scoped successes. Both transient units are inactive, no process retains
the owned remote workspace, and marker-verified workspace removal is recorded.
All four packaged-client recipes pass a fresh check-only preflight on `d6e3566`;
full live A/V and lifecycle acceptance remain pending.

Legacy terminal attempt 15b passed in 553 seconds on `d6e3566` with all source
inputs unchanged and an independent zero exit receipt. Queue advancement, two
world-change cycles, EOS reconnect, replay and stop passed, as did all five
physical PCM-pair checks and final process/port teardown. Its original framebuffer
review remains separate. A scoped sleep inhibitor was held only for the run to
prevent another automatic host suspend; it was released when the process exited.

Forge 1.20.2 attempt 16 passed the full 33-step display/client scenario and fresh
server restoration in 645 seconds, with unchanged sources and clean teardown.
Its hosted `d6e3566` job also passed. NeoForge 1.20.2 in that hosted run exposed
another observer race: PAUSE first published stream generation 6 before 7, and
the observer accepted an earlier TV's generation-6 retention record from before
the widget action. The actual pause and subsequent seek retained the same frame
hash; paired original pause captures were directly reviewed and stayed fixed.
The exact old-log prefix reproduces the false match. Retention checks now begin
at the corresponding widget action's log offset, including paused seek/stream
changes which need not advance the TV-stream generation. Fourteen owner-timeline
regressions pass; runtime attempt 17 exercises the corrected NeoForge boundary.

A configured-value scan found one private value in the Windows VM shutdown log.
The original was retained privately and the exported log was redacted with
before/after hashes; native results and candidate payloads were not changed.
The repeated source/native/recovery scan passed over 939 files and 1,472 decoded
payloads (7,306,962 bytes), with zero findings and zero audit errors. This scope
does not yet include the final artifact bundles or complete runtime evidence.


The NeoForge 1.20.2 runtime-17 gate logged successful owner pause/seek/stream
checks, reconnect, restoration, clean shutdown and a final gate pass, with all
non-Markdown inputs unchanged. Its launch process was terminated with 143 before
the independent child return code could be saved. The child completed after
reparenting; this is scoped observer evidence, not a verified zero-exit run.
`runtime17-outer-execution.json` records the distinction. A user-service
supervisor will preserve a clean repeat's exit receipt independently of the
interactive tool lifetime. All 32 original Forge 1.20.2 hosted Display Settings
and paired world captures were directly reviewed and passed; the receipt is
under `ci-d6e3566/1.20.2-forge/`, separate from pending controller/timeline review.
