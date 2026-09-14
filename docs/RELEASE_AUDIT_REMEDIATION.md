# September release-audit remediation

The [September 12 audit](RELEASE_AUDIT_20260912.md) remains unchanged as the
historical assessment of `8e1efda`. The table below records the remedies.
The [release acceptance record](RELEASE_ACCEPTANCE.md) and
[candidate evidence manifest](RELEASE_CANDIDATE_EVIDENCE_20260914.json) hold the
current artifact assessment and exact hashes. Final-commit local and hosted
acceptance, final scans and cleanup remain mandatory. No tag or publication is authorized.

| Finding | Implementation and verification status |
| --- | --- |
| 1. Fresh display edit rejected | Draft payload preserves the expected revision; the authoritative world adapter alone increments it. Packet-to-world regression accepts the first writer and rejects a stale second writer without changing layout or attachment. |
| 2. Missing/partial display pages | All GUI families, including Forge 1.7.10, use a shared persistent page model. All presets, Custom-only fields, current selections, read-only/Quick locks, pending/acknowledgement/error handling and explicit Reload are implemented. Direct minimum-viewport reviews cover all six display boundaries, including acknowledgement, Quick locks and follower read-only behavior. The root prototype and all five maintained boundary cases also passed actual-widget input, Cancel, pending, stale-error and Reload checks, with all 27 new original captures directly reviewed per case. |
| 3. Requested dimensions labeled Actual | Servers no longer publish requested dimensions as effective dimensions. Pages display dimensions measured from the current pipeline's presented decoded source frame, or unknown. During replacement, a retained frame reports its own dimensions. Server-side effective measurements remain unknown; requested manifest bounds are not measured output. |
| 4. Missing expanded acceptance | Main full-video gates and CI invoke the controlled three-TV/two-client scenario: production draft and SET_DISPLAY packet, all layouts, both mappings, paused edits, independent replacement, failed-replacement isolation and capacity waiting/cancellation/admission. All 21 main profiles and five Quilt/Mod Menu cases passed all 33 feature steps in hosted run 34815343612. Independent golden-color and original-framebuffer color/mask checks pass, with direct review across every distinct rendering adapter. The real-Plex variant covers 31 steps; synthetic failed-replacement injection remains in the 33-step controlled fixture. Four main real-Plex cases, four recovery cases and two lifecycle cases passed, including refreshed legacy evidence for the changed artifact. The `b99a5c2` local matrix passed; the final committed-source matrix and certification remain required. |
| 5. Hosted failures | Fixed empty-name X discovery skipping fallback; terminal observer now compares timeline identities to queue identities; paused observer requires retained evidence for the latest authoritative revision when intermediate packets coalesce. The 26.x metadata-start race is guarded before per-TV preparation. Local private-X infrastructure is available in an isolated extracted runtime. Hosted run 34815343612 on b99a5c2 passes all 21 main profiles, every artifact job and the aggregate. Follow-up run 34824565963 on 12ab064 completed with fifteen successful artifact jobs, one NeoForge terminal failure caused by stock toast audio, and the aggregate skipped; run 34827174909 on 2d8a0d3 now passes every artifact job and the aggregate, with the fixture correction directly verified; the `b99a5c2` local matrix passed; final-commit local and hosted certification remain required. |
| 6. Retained pixel storage | Detailed mode and invisible-display cleanup release the CPU raster; derived adapters no longer keep redundant raster references. Legacy upload reuses a bounded direct buffer. Adapter ownership regressions pass. Reviewed runtime receipts across all distinct rendering adapters confirm derived-storage release, paused redraw and reload/reconnect preservation; final local certification remains required. |
| 7. Stale presentation side effects | Presentation commands validate the existing attachment/generation and return before tune/restore/tracking mutation. Tests check attachment invariance. Display and presentation updates publish to all TV recipients so paused viewers receive edits. |
| 8. Stale records/guidance | Plan and acceptance introduction identify the latest completed remediation run and exact failure inventory, with the audited run retained historically. README explains requested versus decoded quality, block mapping, acknowledgements and per-TV capacity. |

Additional regression findings: idle pool updates no longer increment stream
generations on every tick, and raster rounding treats exact half-channel ties
consistently despite floating-point transform noise.

## September 14 current artifact reconciliation

All five maintained GUI cases passed with direct review. The fresh legacy
real-Plex main, outage recovery and lifecycle tests now also pass on JAR
`70b7eb7afa5c94b0297b7014703111b6e553fafcc5721732c1b6fc239ee620f5`.
The main has 96 directly reviewed originals and audio correlation 0.923318 at
0 ms lag. Lifecycle restart retained unloaded work and completed cleanup after
loading. Independent four-server, Plex and two-native-guest idle checks passed.
The owned recovery workspace was removed after export verification. The new
live scan passed 369 files/6807 payloads with no configured-secret findings.
All sixteen targets now record runtime-certified artifact evidence, scoped to
the hashes in the evidence manifest; final release readiness still requires
phase 10 on the exact containing commit.

## Historical progress ledger

The dated entries below preserve observations, pending work and failed attempts
as they were recorded. Their forward-looking statements describe that checkpoint;
use the current reconciliation above for completed artifact checks.

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


Hosted run `34810548231` for `d6e3566` completed with fifteen successful artifact
jobs, one NeoForge 1.20.2 owner-observer failure, and a skipped aggregate. All five
Fabric jobs completed their Quilt and minimum-loader checks, and both legacy and
root NeoForge terminal/pressure/fault boundaries passed. Its immutable final job
snapshot is `ci-d6e3566/final-status.json`. Follow-up `ae153de` contains the fresh
retention observer and fourteen passing owner/thirteen terminal regressions;
run `34812686752` is its new hosted check. The complete local legacy terminal
run-15b now also has a passing direct review of all 32 original captures. All 32
Fabric 26.2 hosted display/world captures passed direct review. These scoped
reviews do not certify unreviewed evidence or the complete final matrix.


Supervised runtime-17b returned 1 with all non-Markdown inputs unchanged. Its
expanded feature, owner controls, physical PCM, reconnect and media cleanup
checks passed; the final persistence restart never launched Minecraft because
NeoGradle failed downloading Mojang's launcher manifest. The full failure is
preserved under `build/audit-remediation-runtime17b/` and `runtime17b-exit.json`.
No live Plex case started. The persistence restart now requests Gradle offline
mode, reusing dependencies resolved by the already-completed initial server task.
A fresh NeoForge 1.20.2 offline configuration invocation passed; the complete
runtime repeat remains required. This only changes acceptance launch behavior,
not production code or persistence/shutdown criteria.


Runtime-17c on `ebd36c8` passed the complete NeoForge 1.20.2 main invocation in
653 seconds with unchanged sources and a verified zero exit. All33 expanded
feature steps, owner controls, physical audio, reconnect, cleanup and fresh
server restoration of all3 TVs passed. `runtime17c-reconciliation.json` records
its scope; original-image review and the final complete matrix remain separate.
The first live legacy attempt then stopped before Minecraft launch because the
service PATH lacked jq. Its return2 and complete restoration receipt are retained
in `live-main-1.7.10-forge-exit.json`. The private runner now uses the packaged jq
wrapper already used by native checks; jq/curl/Pulse tool preflight passes.
The six live cases are proceeding in a new supervised chain, with a distinct
legacy attempt2 log and result so the failed setup evidence remains intact.


The second legacy live attempt reached real Plex playback but failed its first
Display Settings observation. The original log proves that the page opened at
320x240. The observer sliced decoded text using the file's byte size; packaged
legacy XML logs use CRLF, so the last pre-action snapshot's byte offset already
exceeded the following UI marker's character offset. The redacted reproduction
records 7,698 CRLF pairs and that offset mismatch. The observer now takes its
offset from decoded text, consistent with the other maintained observers. A
regression covers CRLF and multibyte prefixes and still rejects stale markers;
the historical implementation fails both fresh-marker cases, while all14 display
tests pass with the fix. The failed attempt's208seconds, unchanged sources and
JAR, and complete original-server restoration remain recorded. Its runtime
directory is preserved under `live-legacy-attempt2-runtime` with a file-hash map
before retrying the complete scenario. No production artifact changed.


## September 14 live and hosted checkpoint

Run [34815343612](https://github.com/StonyTark1117/Cinemarr/actions/runs/34815343612)
is fully green on `b99a5c27708c118548112ca7ad21c1520a881968`: sixteen artifact jobs,
the target validator and the aggregate all succeeded. The sixteen downloaded
diagnostic archives have GitHub digest verification. Their feature results cover
sixteen dedicated mains, five Quilt mains and five Quilt/Mod Menu cases, with
all thirty-three steps passing in every case. Hosted original-image review,
final local gate and downloaded/local bundle parity remain separate.

The four real-Plex main cases passed with unchanged sources and candidate JARs.
All 284 original screenshots were directly reviewed and hash-verified. Measured
physical-output correlations were 0.969041 (legacy), 0.985972 (Quilt), 0.978220
(NeoForge) and 0.998881 (Fabric 26.2), with measured lags at most 20 ms. Each fresh
server process restored both custom TVs and its Quick TV, and each case restored
its original configuration, overrides, mod state and stopped/autostart-off state.
The recorded pre-redaction restart-log digests were verified by reversing the
known literal mask in memory; the exported redacted logs independently pass the
same three-TV persistence checker. These are measured physical audio checks, not
subjective listening claims. The full exported-evidence secret scan remains open.

A root NeoForge pre-launch attempt failed when DiscPanel returned intermittent
401 responses for a valid token. Its service journal showed database-lock errors
alongside those responses. After restoring the stopped test configuration, the
panel service was restarted with no running Minecraft containers. The complete
baseline check then passed without retries, and the new NeoForge main invocation
passed. Failed-attempt evidence, recovery checks and redaction provenance remain
under `build/audit-remediation-evidence/`; no acceptance threshold was weakened.

Both legacy and NeoForge restart-mid-build/unloaded-chunk lifecycle cases passed
on their exact candidate JARs. All six main/lifecycle exit receipts pass. The
supervised final job has started the complete build pair, followed by the fresh
37-case local matrix in the isolated checkout. Final metadata, final-commit gates,
security review and synchronized main remain open.


## September 14 direct boundary review

Current-SHA hosted evidence now has direct review of 84 main program/controller
images across all 21 profiles, six additional legacy reconnect images, 192
display images across six source boundaries, and 150 terminal/pressure/network
phase images. Original images and their source receipts are hash-bound in the
ignored evidence ledger. The 21 main physical PCM pairs pass with correlations
0.988485–1.0 and absolute lag at most 20 ms. All 20 boundary PCM pairs pass.
These are measured output checks; sequential screenshots are not simultaneous
synchronization measurements, and no subjective listening is claimed.

Both legacy world-change cycles release the departing follower's television,
stream, pipeline, audio-source and decoder-thread counters to zero. Its output
measures −91 dB while the remaining leader stays audible. Both same-process
returns restore advancing program and passing PCM synchronization. Browse
pressure remains bounded at 16 queued and one active request; fresh requests
recover and stale results stay rejected. Aggressive segment-peer departure
leaves no orphaned grants or egress. Healthy viewers can still have legitimate
active work and transfer grants during that check.

The actual Gradle source-root/exclusion map identifies five Display Settings
source variants, four renderers and three texture adapters. The six reviewed
boundaries cover each. A separate actual-widget supplement is prepared for the
remaining input-error, Cancel, focus/draft and pending-state visual requirements;
it has not run and those requirements remain open.

The first forced local build passed with no source changes and every one of its
18 files matches the downloaded current-SHA CI bundle. The second forced build
also passed in 1,344.99 seconds with no source changes and an identical bundle.
The full 37-case local matrix is now running under supervision. The completed live-evidence and hosted
archive secret scans passed; new local evidence, final docs and final source
still require the final security scan.


The complete changed non-Markdown diff review found a remaining weakness in the
persistence-restart harness: it discarded a failing launcher exit and trusted
the game-port listener without verifying process ancestry before cleanup.
Controlled extracted-function fixtures reproduce both false passes; modern
foreign game/RCON listener cases also attempted the mocked RCON stop. Commit
`12ab064ef943c8285a041670c4f3465962601771` fixes these cases in the primary checkout,
checks ownership before RCON/process-group handling, and keeps launcher failure
fatal. The permanent regression passes with the fix and rejects the old harness
in four negative cases; the complete feature-harness suite passes all 15 tests.
The hardening plan explicitly permits independent edits outside the frozen
checkout. Its separate regular files, clean Git state and all 888 source hashes
were verified unchanged, so the ongoing isolated `b99a5c2` matrix retains its
original scope. The follow-up commit is pushed and its CI run `34824565963` is
in progress. All 26 current hosted restart logs independently show
`BUILD SUCCESSFUL` and no `BUILD FAILED`; that scoped evidence does not remove
the requirement to harden and retest the final harness.


## September 14 requirements reconciliation

The requirement-to-evidence map now separates eleven behavior areas, their
actual passing test suites and candidate receipts, and their remaining final
checks. It is retained as `requirements-evidence-reconciliation.json` under
`build/audit-remediation-evidence/`. Optional credentialed Plex and hardware
unit cases remain skips, with separate real-Plex and software-native evidence;
legacy totals include repeated core suites and must not be summed as unique tests.
All ten required GameTests have fresh completion markers in both forced builds
and the ongoing local matrix.

Both local terminal supplements have passed. All fifty original program,
controller and phase images are directly reviewed, and eight physical audio
pairs pass with absolute lag at most 20 ms. Both legacy world-away cycles show
zero owned media counters and quiet follower output, followed by advancing
program and synchronized audio on the same client processes. The full matrix
remains in progress; these two completed cases do not close it.

The detailed requirements review identified missing direct regressions for old
display data's deferred custom/Quick classification and preservation through
reactivation. New test-only modern and legacy cases exercise unavailable versus
loaded controller access, all Quick preset identities, malformed display fields,
schema migration and preserved registration/recovery records. Their focused
verification is in progress. The first modern attempt failed in test setup
because it constructed a block after registry freeze; the corrected fixture
uses the already registered blocks. The isolated product candidate is unchanged.


The focused migration verification is now complete: commit `7126563` tests
54 modern/legacy old/corrupt payload/controller combinations,
plus the ten existing world-data regressions. All twelve tests pass. The modern
fixture's registry setup and the Gradle task selector were corrected after their
failed attempts; the legacy fixture's final provider field uses reflected setup.
These are test-only changes and preserve the product artifact inputs.

Run `34824565963` subsequently failed NeoForge's terminal supplement after the
fresh follower reached idle with zero streams. Its PCM measured −58.5 dB mean
but −31.9 dB maximum, correctly failing the existing −35 dB peak threshold.
The transient's waveform matches Minecraft 1.21.1 `ui/toast/out.ogg` with
correlation 0.999995. Minecraft plays this toast through the master category,
so the fixture's disabled ambient/player categories do not suppress it.

Commit `2d8a0d3` gives only the temporary NeoForge 1.21.1 terminal clients a
format-34 resource pack with empty `ui.toast.in` and `ui.toast.out` sound lists.
No TV asset, decoder, master/record gain, terminal timing or audio threshold
changes. All seventeen harness tests pass, including audible-PCM rejection by
the unchanged silence checker and the narrow pack contents/profile routing.
[Run 34827174909](https://github.com/StonyTark1117/Cinemarr/actions/runs/34827174909)
is validating the correction. The failed run and digest-verified diagnostics
remain preserved; a retry alone cannot certify the fix.

The isolated matrix still runs on clean `b99a5c2`, with all 888 input hashes
unchanged after these primary-only commits. Full local/final committed-source
certification and the additional GUI review remain open.


Both local pressure supplements have now passed. Direct review covers their
86 listed original program/controller and pressure-phase images (46 legacy,
40 NeoForge); ten physical eight-second audio pairs pass. Legacy correlation
ranges from 0.993218 to 0.998521 with absolute lag at most 30 ms; NeoForge ranges
from 0.994628 to 0.997013 with 10 ms lag. Both network-only peers measure −91 dB
mean and peak. The browse queue bounds, fresh recovery, stale-result rejection
and peer-owned grant/egress cleanup reconcile with the preserved receipts.
These scoped reviews leave other widget/controller evidence and the complete
local/final-commit gates separate. The isolated matrix has moved to legacy
fault recovery. Run `34824565963` is now terminal: fifteen artifact jobs passed,
NeoForge failed terminal audio, and the aggregate was skipped.


The corrected NeoForge terminal step in run `34827174909` has now succeeded.
This verifies the maintained runtime gate with the narrow toast-audio fixture
change; its complete job, aggregate and downloaded PCM/resource-pack evidence
still require reconciliation.

The local legacy fault supplement also passed through clean shutdown. Its
28 listed original main, reconnect and recovered-phase images are directly
reviewed. All four eight-second physical audio pairs pass with absolute lag
at most 20 ms; the three recovered phases have correlations 0.999684, 0.999930
and 0.999883. Request logs confirm transient, slow and offline modes; the
exhaustion path records a redacted failure and recovers in session. The first
five local runtime supplements are complete; the complete matrix remains open.
The root NeoForge GUI edge-case supplement is queued to begin only after the
isolated full matrix passes and its process exits. It has not executed yet.


`build/audit-remediation-evidence/candidate-evidence-selection.json` now maps
all twenty-one profiles to their selected, previously reviewed exact-product
candidate cases, sixteen artifact hashes and six deep display boundaries.
Ninety original image hashes and supporting current/prior logs were revalidated;
this is evidence selection, not a new visual review or final certification.
The legacy receipt's later six-frame reconnect review supersedes its earlier
pending observation. Final owned-restart execution and all final-commit gates
remain separately required.


The local NeoForge fault supplement is complete through clean shutdown. All
22 listed main/recovered-phase images are directly reviewed, and four physical
eight-second audio pairs pass with zero measured lag. The transient, slow and
exhausted recovery correlations are 0.998429, 0.999547 and 0.997583; the main pair
is 0.999818. The request ledger, redacted exhaustion failure and recovery
transitions reconcile with the test fixture. All six local terminal, pressure
and fault supplements have passed. The matrix has moved to the twenty-one main
runtime profiles; the GUI supplement remains queued behind the complete run.


The corrected NeoForge terminal diagnostics are downloaded and GitHub-digest
verified (`10342036029`, archive SHA-256
`3a5403e5c271dca81ee776c814114c33782f0d51d7d6946a2369edab6accde1e`).
All eighteen original terminal images are directly reviewed. Actual runtime logs
show both audio clients loading the narrow resource pack; the fresh follower's
`ui.toast.in` and `ui.toast.out` events resolve to empty sounds while master and
record gains remain 1.0. All four terminal PCM captures contain only zero samples
(−91 dB mean/peak). All three eight-second playback pairs remain audible and
synchronized with zero measured lag. Queue advance, idle, restart and explicit
stop reconcile with generations 3, 5, 6 and 7. This closes the scoped fixture-fix
runtime review without changing TV audio or silence thresholds.

All sixteen artifact jobs in run `34827174909` have passed; the aggregate is
uploading the inspected bundle. Its final status and downloaded exact-byte
parity remain pending. A supervised low-priority collector will preserve all
sixteen diagnostic archives, check all twenty-six main/Mod Menu feature results,
and compare all eighteen bundle files with the two inspected candidate builds.


Run `34827174909` is now complete and fully successful, including all sixteen
artifact jobs and the aggregate release gate. The collector has begun verifying
the full downloaded diagnostics and bundle; exact-byte parity remains open
until its receipt completes. The local matrix is still running and does not
inherit a pass from hosted CI.


Full collection for `34827174909` completed successfully. All sixteen diagnostic
archive digests are verified. All twenty-six main/Mod Menu feature results pass
thirty-three steps; their fresh-server persistence receipts match the actual
feature/log hashes and restore all seventy-eight TV records. The current restart
harness checks owned listeners and successful launcher exit before publishing
these receipts; raw PID ancestry snapshots are not separately archived.

The downloaded bundle (`10342181677`, archive SHA-256
`8512eb357a59f11db203210b0888230876e3b553b8c1093848e0ec88257b9a3e`)
contains exactly sixteen JARs, the manifest and checksums. All eighteen files
match both guarded local builds and the previously inspected `b99a5c2` bundle.
Native, four main real-Plex, four recovery and two lifecycle evidence therefore
remain associated with unchanged product bytes. The full local matrix, actual
GUI edge checks, final scans/cleanup, metadata and final committed-source
certification remain open.

September 14 hosted build-log review found an additional verification gap.
Run `34827174909` executed the root and legacy migration test tasks, but its
legacy second build reused the outer Gradle configuration cache. That cached
task graph restored the child command without the conditionally appended
`--rerun-tasks` argument: child compilation, JAR assembly and tests reported
`UP-TO-DATE`. The successful hosted comparison proves byte equality, but that
case does not prove a forced child rebuild. The detailed test-case records are
the retained local JUnit XML; the hosted diagnostic upload excludes JUnit XML.

Both guarded local builds used `--no-configuration-cache`; their logs each
show twenty-one executed compilation and test tasks with no reused outcomes
for those tasks. Their reproducibility evidence remains valid. An isolated
proposal marks nested release commands incompatible with configuration caching,
and a small Gradle fixture is validating normal and forced invocations for all
twenty nested gates. The proposal is not yet applied to the tracked source;
the running candidate and queued GUI inputs remain unchanged. Final hosted
certification must exercise the corrected rebuild path.

Commit `a462cd8` on the separate `codex/rebuild-cache-fix-20260914` branch
implements that repair. The original configuration reproduced missing forced
arguments for all twenty nested gates; the repaired configuration and maintained
regression both passed normal and forced invocations for all twenty. The local
release gate and root hosted job now run the regression and retain its logs.
Sixteen manifest tests and tracked endpoint hygiene also passed. Hosted run
`34835244372` completed successfully, including every artifact job and the
aggregate. The sixteen downloaded job logs confirm actual second-build
compilation, test and JAR execution without reused task outcomes. The archived
new regression matches the committed source and verifies all twenty nested
gates. All eighteen downloaded bundle files match both guarded local builds. The primary
`2d8a0d3` checkout and isolated `b99a5c2` runtime candidate remain unchanged.
Integrating this fix and completing final committed-source certification remain
required after the queued GUI candidate runs.


The isolated `b99a5c2` local matrix completed successfully in 5h 47m 5s.
All thirty-seven cases report ready state and clean process/port shutdown:
twenty-one main profiles, six adverse supplements, five Quilt/Mod Menu cases,
and five minimum-Fabric-loader cases. Independent reconciliation verifies all
twenty-six feature results (thirty-three steps and eight framebuffer checks
each), preserved-result and restart-log hashes, restoration of three TVs per
case, audio/alignment receipts, and all 888 unchanged source inputs. All ten
required GameTests passed in both forced builds and the matrix. The first
reconciliation attempt referenced `.server.log`; the frozen harness actually
writes `.console.log`. Correcting that filename resolved the reconciliation
without changing an assertion or rerunning a runtime workload.

The queued NeoForge 1.21.1 GUI supplement has started with its original guarded
inputs. The other five GUI boundary cases, maintained-helper integration,
rebuild-fix integration, final metadata/security/cleanup and exact final-commit
local/hosted certification remain open. The release remains a prerelease.


The root NeoForge GUI prototype completed in 640 seconds with unchanged source
inputs and candidate JAR. All twenty-seven original edge-case images are directly
reviewed and hash-verified. Pending Apply is visibly disabled, validation errors
fit the viewport, all named presets and legal dimension boundaries are present,
and Cancel/stale rejection/Reload preserve the intended authority and draft
behavior. The full thirty-three-step feature scenario and eight framebuffer
checks also pass, alongside physical audio (correlation 0.988718, lag 10 ms),
reconnect, fresh-process persistence and clean shutdown.

The primary branch now includes the identical build-cache fix as `aea2ac3` and
the maintained GUI helper as `4ff3fcd`. The helper runs automatically from the
existing feature observer; its local fixture alone uses a bounded owned-server
pause to expose pending acknowledgement. Live Plex exercises the other widgets
without suspending its server. Five remaining local boundary cases now run
serially using the maintained helper; their captures still require direct
review. No final certification or release designation is claimed.


The first maintained legacy GUI attempt failed `frame-budget-not-sent`. Direct
capture inspection shows the input driver entered `192x8192`, losing the leading
8 from the intended `8192x8192`; that smaller request is legal and was saved.
Earlier fields likewise lost leading characters (`oops` became `ops`, and `240`
became `40`). This does not demonstrate a missing frame-budget guard because
the intended invalid draft was never entered. The suspected sequencing issue is
Select All followed immediately by typing on the LWJGL 2 client. Commit
`6d540c3` waits 300 ms after selection before typing, without changing product
code or any acceptance assertion. The failed case and four reviewed error
captures remain preserved. A fresh `1.7.10-forge-attempt2` is running; success
is required before the other four boundary cases proceed.


The second legacy attempt reproduced the same missing characters despite the
300 ms pause. Source inspection and a deterministic regression identified the
actual cause: vanilla `GuiTextField.writeText` calculates insertion capacity
before deleting a backwards selection. A full four-character selection has zero
capacity, so the first replacement character removes the selection but inserts
nothing. The negative control reproduces `8192` becoming `192`, an empty pasted
replacement, and a partial-selection replacement becoming `14` instead of `1894`.

Commit `8668435` adds `LegacyDisplayTextField` for both dimension fields. It deletes
selected text before delegating insertion to vanilla, preserving filtering and
the four-character limit. All four new regression tests and the existing
text/focus/cursor/selection-state test pass. The ineffective timing change is
removed; the GUI test still types directly over selected text so it can detect
this product defect. The changed legacy artifact is being rebuilt and the whole
sixteen-artifact index inspected. Its GUI, live-boundary and reproducibility
proof must be refreshed; the completed `b99a5c2` matrix and native/live receipts
retain their exact original scope. Both failed GUI attempts remain preserved.


The fresh legacy release gate passed with all eighteen child tasks executed and
346 tests (zero failures/errors, two existing skips). Indexing and structural
inspection of all sixteen artifacts passed, but the candidate-hash assertion
caught stale 1.20.2 Fabric, Forge and NeoForge canonical platform outputs. All
three match the earlier build3 bundle; the prior synchronized `build/releases`
copy had masked those older platform-directory outputs. That failed index is
retained and cannot be used for the next GUI case.

A full member-by-member SHA-256 comparison of the new legacy JAR confirms only
`LegacyDisplaySettingsScreen.class` and the added `LegacyDisplayTextField.class`
changed. All 1,166 other entries, including eighty-six native-library entries,
match the earlier legacy JAR. This preserves native-byte ABI evidence while
requiring fresh runtime/live UI verification for the changed artifact.

Two full forced builds and complete artifact inspections are now running from a
new clean `8668435` checkout. They must produce matching eighteen-file bundles,
with only the legacy JAR, manifest and checksums differing from `b99a5c2`. The
five-case GUI batch waits for that result and synchronization of the primary
bundle; it does not inherit success from the failed narrow reindex attempt.


The first clean `8668435` build attempt reached all ten passing GameTests, then
failed `verifyPrivateXvfb`: the new wrapper had omitted the bundled X11 tool and
library paths. Its source guard remained unchanged and the queued GUI job
correctly refused to start. The failed build log/result are preserved. With the
same environment used by the earlier successful matrix restored, direct
private-Xvfb, private-window ownership and feature-harness preflights all pass.
A new two-build attempt is running with that environment and separate evidence.

Two additional configured-secret scans completed without findings, errors or
input changes. The twenty completed follow-up CI archives (including both full
bundles and the preserved terminal failure) contain 43,754 scanned payloads,
17,517,453,556 bytes. The completed frozen local matrix, root GUI prototype and
two failed legacy GUI attempts contain 73,016 scanned payloads across 10,379
files, 7,501,797,472 bytes. Both scans used all fourteen configured values and
four baseline Plex URL entries. New candidate/runtime and final evidence remain
outside these scoped passes.

The corrected first full `8668435` build passed in 22m57s, with all 92 top-level
tasks executed, sixteen inspected artifacts and unchanged source inputs. All ten
GameTests, seventeen display-harness tests, twenty-three private-window tests and
five Xvfb tests passed. Its first bundle differs from `b99a5c2` only in the legacy
JAR, manifest and checksums. The second forced build is still running; neither
bundle reproducibility nor new runtime certification is claimed yet.

The new legacy JAR, manifest/checksums and completed first-build evidence passed
a configured-secret scan: 1,176 payloads, 428,203,029 bytes, no findings, errors or
input changes. A separate 25-file handoff/environment preparation scan passed.
Both used all fourteen configured values and four baseline Plex URL entries.
The Proxmox workspace contains five verified helper scripts; artifact transfer,
deployment and new live cases have not run. User credential files remain intact.

The second corrected full build passed in 22m19s with all 92 top-level tasks
executed and all sixteen artifacts inspected. Both builds preserve all 894
non-Markdown source inputs and produce identical eighteen-file bundles. The
primary bundle is synchronized to these verified bytes; the failed narrow index
is preserved separately. Only the legacy JAR, manifest and checksums differ
from `b99a5c2`. Their exact hashes match the completed new-artifact secret scan.
The queued five-case GUI batch has started with Forge 1.7.10; direct review and
new live validation remain required.

The corrected legacy maintained GUI/runtime case passed in 637 seconds with
unchanged source inputs and the exact new legacy JAR. All 98 original captures
were directly reviewed: 59 display/rendering, seven controller-edit, eleven
owner-timeline, ten widget-feedback, six post-reconnect, four main client views
and one minimum-viewport non-owner view. The original dropped-character defect
is resolved in the actual GUI, including rejection of the intact 8192×8192
frame-budget request. All 33 feature steps and eight framebuffer checks passed.
Physical audio correlation was 0.999889 with 30 ms lag. A fresh server restored
all three TVs' settings, and owned processes, port and audio modules were gone.
The separate complete-review receipt closes this case's review; the batch is now
running Quilt 1.20.1. Four GUI boundaries and new legacy real-Plex checks remain.
