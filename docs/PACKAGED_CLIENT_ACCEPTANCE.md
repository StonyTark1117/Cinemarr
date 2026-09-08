# Production-client acceptance

The credentialed Plex and lifecycle gates require the indexed Cinemarr JAR on
both clients as well as the server. A Gradle `runClient` pass does not establish
that a distributed client JAR works. The ordinary 21-profile development matrix
remains a separate regression gate.

## Scope and isolation

The production-launch preparation covers the four architectural representatives:
Forge 1.7.10, Quilt 1.20.1, NeoForge 1.21.1 and Fabric 26.2. It uses installed
public Minecraft/loader metadata, libraries and assets plus PrismLauncher's Java
bootstrap. It never reads a launcher account store, copies existing instances,
changes launcher authentication, or uses development remapping/class directories.
The controlled offline test-server identities are `CinemarrVideoA`,
`CinemarrVideoB` and the lifecycle-only `CinemarrRecovery`.

All GUI launches go through `scripts/run-private-xvfb.sh`. Do not substitute the
user's `DISPLAY`, Wayland session, launcher instances or desktop. A null-audio
launch prerequisite is not audible-playback evidence; the full gates retain
their isolated audio sinks and physical PCM comparison.

## Complete local release entry point

From a clean candidate checkout with fresh output directories, use Java 21 and
`./gradlew releaseMatrixGate --no-configuration-cache --max-workers=1`.
This requires building and inspecting all 16 artifacts, the ten GameTests,
all 21 development runtimes, both terminal/pressure/network-fault
representatives, all five Quilt Mod Menu cases and all five minimum-loader
launches. The standalone `verifyDedicatedServers` task remains a representative
NeoForge check, not the complete release gate. Runtime supplements are ordered
serially because their build/runtime inputs are shared. Preserve previous failed
evidence and use a fresh checkout/output location for repeat certification.
An automated pass does not replace direct image review, byte-identical rebuild
proof, exact packaged real-Plex/native acceptance, security review or final-SHA
remote CI and artifact parity.

## Supplementary deterministic network-fault recovery

Run on both `1.7.10-forge` and `1.21.1-neoforge` in separate fresh output roots:

```bash
CINEMARR_ALSA_PCM_TYPE=pulse \
CINEMARR_GATE_OUTPUT_ROOT=build/video-fault-recovery-fresh-attempt \
CINEMARR_PROTOCOL_CLIENT_GATE=true \
CINEMARR_COMMAND_CLIENT_GATE=true \
CINEMARR_VIDEO_CLIENT_GATE=true \
CINEMARR_VIDEO_CONTROL_GATE=true \
CINEMARR_VIDEO_ADVERSE_NETWORK_GATE=true \
CINEMARR_GATE_VIDEO_DURATION_SECONDS=600 \
bash scripts/run-dedicated-server-gate.sh 1.7.10-forge
```

After normal controls/reload/reconnect, the fake service injects two transient
HTTP 503 responses, sustained bounded segment delay, and exhausted segment
retries followed by service restoration. Each case requires fresh fault HTTP
activity and playback stability before capturing six private-window images and
an eight-second calibrated physical PCM pair. Capture starts and finishes in the
expected shared playback generation; both outputs must be audible and correlated.
The transient and slow captures happen before clearing their fault modes.
Exhaustion recovery is captured after restoring transport without reconnecting
the viewers. Active-underrun checks remain strict across all closed client logs.
Any capture/audio failure fails the case and releases the fake service fault.
Redaction failures fail silently without printing the sensitive matching line.

Per-phase evidence is retained under `<profile>.video-adverse-network/` in
distinct `transient`, `slow` and `exhausted` directories; existing captures are
never overwritten. Direct review of all eighteen post-fault images is required
in addition to normal-control image review. Local `verifyRuntimeMatrix` requires
`verifyVideoFaultRecoveryRuntimes`; CI runs both representatives and retains PNG,
PCM, alignment, synchronization and log evidence. Test fixtures disable passive
mob spawning to keep incidental animals out of future screen captures.

## Supplementary deterministic terminal gate

The supplementary deterministic terminal gate is separate from packaged
real-Plex acceptance and the full widget/reload matrix. Run it on both
`1.7.10-forge` and `1.21.1-neoforge`, with a new output directory per attempt:

```bash
CINEMARR_ALSA_PCM_TYPE=pulse \
CINEMARR_GATE_OUTPUT_ROOT=build/video-terminal-fresh-attempt \
CINEMARR_PROTOCOL_CLIENT_GATE=true \
CINEMARR_COMMAND_CLIENT_GATE=true \
CINEMARR_VIDEO_CLIENT_GATE=true \
CINEMARR_VIDEO_CONTROL_GATE=true \
CINEMARR_VIDEO_TERMINAL_GATE=true \
bash scripts/run-dedicated-server-gate.sh 1.7.10-forge
```

This queues the current fixture, seeks to its final twelve seconds, requires an
automatic next generation and emptied queue, then captures correlated audible
output and three paired world views. It seeks near the empty queue's EOS,
disconnects and archives the follower, starts a replacement follower, and
requires matching IDLE plus silence on both sinks. Replay must restore both
clients' video/audio; explicit STOP must silence both again. The retained JSON
does not automatically approve the twelve world images: direct review remains
required. Closed client logs are checked for every recorded active underrun,
including events before generation resets. This mode intentionally excludes
real Plex, adverse-network injection and the normal widget/reload scenario;
those remain independently required gates. CI runs both terminal representatives
in addition to its ordinary runtime matrix and uploads their diagnostics.
The local `verifyRuntimeMatrix` and `releaseMatrixGate` also depend on
`verifyVideoTerminalRuntimes`, which runs these two cases serially. Preserve or
archive previous task-owned output before a fresh run; existing terminal phase
reports are never overwritten to manufacture a pass.

The legacy terminal case also performs two world/dimension round trips before
its final EOS sequence. Only its isolated server/client launches receive
`cinemarr.acceptance.worldChangeProbe=true`. In conjunction with the existing
enabled/video-probe flags, that permits the operator-only
`cinemarr acceptance-dimension -1` / `0` fixture commands for the fixed
`CinemarrVideoB` test identity. Ordinary servers and other players/dimensions
cannot use this fixture operation. It creates a bounded Nether landing area
inside the disposable test world; it does not create portals or alter user
worlds. The follower's JVM PID/start identity must remain unchanged across both
round trips. While away, two fresh reports must show zero televisions, streams,
pipelines, audio sources and decoder threads, no old-world render submission,
and captured silence. The owner stays audible. Each return requires the same
session/generation, restored media resources, correlated PCM and six additional
world images; the two away images must also be directly reviewed.

### Legacy screen-save migration

Forge 1.7.10's old screen registry used server-wide `mapStorage` despite being
intended as dimension-local. It could expose the same TV identity at matching
coordinates in different dimensions and fail to resend playback after returning
to a world. The corrected registry uses `perWorldStorage`: the overworld keeps
`data/cinemarr_screens.dat`, while other dimensions use their own data directory.
Before marking an older screen file dimension-local, the mod preserves its
original bytes beside it as
`cinemarr_screens.dat.before-dimension-isolation.bak`; it never replaces that
recovery copy. Back up the whole world before upgrading a prerelease save.

The old shared format did not record which dimension owned each TV. Existing
overworld records remain available, but controllers originally built in other
dimensions may need reactivation and retuning. The mod does not guess ownership
from matching coordinates or copy the shared registry into every dimension.
The preserved file retains the original metadata for recovery; screen blocks
are not deleted by this storage migration.

## Supplementary deterministic browse and segment-pressure gate

Use a fresh output directory and the same isolated client configuration for each
representative (`1.7.10-forge`, `1.21.1-neoforge`):

```bash
CINEMARR_ALSA_PCM_TYPE=pulse \
CINEMARR_GATE_OUTPUT_ROOT=build/video-pressure-fresh-attempt \
CINEMARR_PROTOCOL_CLIENT_GATE=true \
CINEMARR_COMMAND_CLIENT_GATE=true \
CINEMARR_VIDEO_CLIENT_GATE=true \
CINEMARR_VIDEO_CONTROL_GATE=true \
CINEMARR_GATE_VIDEO_DURATION_SECONDS=600 \
CINEMARR_VIDEO_PRESSURE_GATE=true \
bash scripts/run-dedicated-server-gate.sh 1.7.10-forge
```

This separate fake-Plex case must not run against a user server or real Plex,
or alongside terminal/adverse-network mode. The explicit enabled/video/pressure
opt-ins permit only the non-owner follower probe to issue paced real browse
packets through its existing control file. The fake service delays browse
responses only, with a 45-second per-request bound and immediate fault release;
normal HTTP timeouts and video prefetch limits are unchanged. The 40-second
load exceeds the 20-second client prefetch runway. Server snapshots must prove
sustained browse saturation/rejection without playback rejection or exceeding
either worker/backlog budget. Physical PCM and six framebuffer captures are
collected during pressure, not just afterward. After fault release, a fresh
browse result must supersede older work; another six views and PCM sample
prove recovery. A separate third-peer segment-pressure phase follows, then the
normal full control/reload/reconnect gate continues.
Review all saved images directly; JSON pass flags are not visual acceptance.
The acceptance-only legacy UI preparation clears the debug overlay that F3+T
can leave enabled, without blindly toggling a key. Reject debug-obscured final
world captures even if the underlying render logs advance.
The third client (`CinemarrVideoC`) uses its own private X display and null audio
sink. It is explicitly network-only: it receives the real session identity but
never creates a video/audio pipeline. The additional
`cinemarr.acceptance.segmentPressurePeer` opt-in is effective only with both
acceptance/video flags and never on the leader. It issues at most ten valid
segment requests per 100 ms for forty seconds, without a catch-up loop, and
acknowledges delivered windows through the normal transport. The observer
requires sustained actual requests, at least 50 acknowledgements and 1 MB of
delivered media, both rate/window rejection paths and zero unexpected errors.
The two ordinary viewers remain subject to the full underrun, advancing-frame,
audibility and correlated-PCM checks during and after pressure. The network-only
role must remain silent and must never emit a media timeline or rendered frame;
this is not an exemption for failed playback on an ordinary viewer.
Server snapshots require three tracking clients, bounded rate-limit subject
maps and outstanding grants,
work and watch-party egress, and no playback/egress rejection. Departure requires
a fresh server leave marker and client reset acknowledgement, termination of
the owned peer launch and two remaining tracking clients. Every fresh pressure
and recovery sample must include `orphanedTransferGrants`, `orphanedEgressItems`
and `orphanedEgressBytes`, all zero. These count retained owners absent from
the actual server player list, without expiring/removing them during
observation. The two continuing viewers may have bounded active transfers;
a coincidental global-idle sample is neither necessary nor sufficient to prove
departed-owner cleanup. Saved `.segment-pressure` captures require direct visual review.
Older browse-only receipts do not satisfy this new segment-pressure phase.
Failed and corrected runs must remain separate records; do not retry unchanged
product/runtime failures merely to obtain a green result.
Both local `verifyRuntimeMatrix` and `releaseMatrixGate` require the serial
`verifyVideoPressureRuntimes` supplement after terminal representatives. CI
executes both pressure representatives and uploads their images, PCM and logs.

## Prepare packaged inputs once

Use Python 3.11 or newer without `-O`. Install the matching public Minecraft and
loader components in a launcher normally before inventorying them. The
inventory lists its four pinned profile combinations and verifies metadata,
library checksums and the asset index. If public inputs are unavailable, stop
and provision those inputs; do not fall back to a development launcher.

Example from the repository root, with explicit operator-selected paths:

```bash
python3 scripts/inventory-packaged-client.py 1.21.1-neoforge \
  --public-root /path/to/PrismLauncher \
  --cache-dir build/packaged-input-cache \
  --verify-upstream --fetch-missing \
  --output build/packaged-inputs-1.21.1-neoforge.json

python3 scripts/prepare-packaged-client-runtime.py 1.21.1-neoforge \
  --inventory build/packaged-inputs-1.21.1-neoforge.json \
  --public-root /path/to/PrismLauncher \
  --cache-dir build/packaged-input-cache \
  --bootstrap /path/to/PrismLauncher/NewLaunch.jar \
  --destination build/packaged-runtimes/1.21.1-neoforge
```

Repeat for the other three profiles. Outputs must be new paths under `build`;
preserve earlier evidence instead of overwriting it. Missing verified libraries
are downloaded only to the task cache, never into the user's launcher cache.
Staging copies libraries and hash-checks every asset object, extracts native
libraries into the owned runtime, and records the bootstrap and inventory hashes.
It refuses to stage while Cinemarr audio modules are present. Run this I/O-heavy
step separately from physical audio acceptance. Loader installation processors
may populate additional files in that isolated runtime on first production
launch; do not share the runtime back into a user's installation.

## Select production runtime Java

The launcher derives Java **8, 17, 21 and 25** respectively from
`gradle/targets.json`; these are not necessarily the Java versions used to run
Gradle. By default it uses `/usr/lib/jvm/java-<version>-openjdk`. Override an
installation location with `--java-home` for a direct invocation, or with
`CINEMARR_PACKAGED_JAVA8_HOME`, `CINEMARR_PACKAGED_JAVA17_HOME`,
`CINEMARR_PACKAGED_JAVA21_HOME` and `CINEMARR_PACKAGED_JAVA25_HOME` for a gate.
It verifies the actual Java version before launching.

Set `CINEMARR_PACKAGED_CLIENT_RUNTIME_ROOT` to the absolute parent directory
containing the four profile directories. These are non-secret runtime settings.
DiscPanel credentials remain purpose-specific credential-file inputs to the
operator's task orchestration, supplied only to child processes; never add
tokens to these manifests, shell history, launch recipes or documentation.

The September 7 workspace retains all four prepared production profiles under
`build/packaged-client-runtime-20260907`. Reuse this directory after running
the read-only prerequisite check against the new candidate; do not inventory
or install the same loader inputs again merely because a shell lost its
non-secret runtime settings. Set `CINEMARR_PACKAGED_CLIENT_RUNTIME_ROOT` to
that directory's absolute path for the shell or task runner. Java 25 may be
in a Gradle-managed toolchain rather than `/usr/lib/jvm/java-25-openjdk`;
select its verified installation using `CINEMARR_PACKAGED_JAVA25_HOME`.
Do not replace it with Java 26 simply because Java 26 runs the build.
Preserve previous per-run game directories separately; reusing the prepared
libraries does not authorize overwriting earlier evidence or installed mods.

Read-only prerequisite example (no game-directory writes or X server):

```bash
python3 scripts/launch-packaged-client.py 1.21.1-neoforge \
  --runtime-root build/packaged-runtimes \
  --game-dir build/packaged-client-focus/preflight-neo \
  --username CinemarrVideoA --server test-host:25565 \
  --expected-server-host test-host --check-only
```

The declared server must be an authorized, isolated offline acceptance host.
The launcher checks the host against the explicit expected host, the candidate
against `build/releases/SHA256SUMS`, and prepared classpath hashes against the
inventory. Fabric/Quilt also require the pinned, unremapped Fabric API artifact
in the Gradle dependency cache (`--gradle-cache` can select another cache).
Existing conflicting candidate files or development Jammarr JARs cause failure;
they are not silently replaced. Minimum-loader and Mod Menu override gates
cannot use these ordinary production inputs as substitutes.

Select a fixture with an actual alternate stream. The owner widget probe uses
`CINEMARR_OWNER_STREAM_KIND=audio` by default for the deterministic two-audio-track
fixture. For real media with one audio track and available subtitles, explicitly
set `CINEMARR_OWNER_STREAM_KIND=subtitle` after checking its Plex metadata. Both
modes require the selected stream ID to change, the other stream ID to remain
unchanged, a new authoritative generation and the correct paused/advancing
cursor. No-op selection, unavailable alternatives or cursor drift still fail;
there is no automatic skip or fallback. Record which stream kind was exercised.

## Run and prove acceptance

`run-discopanel-real-plex-gate.sh` and `run-discopanel-lifecycle-gate.sh` perform
the production-input preflight before contacting a managed server. They retain
the exact server Jammarr dependency on both clients, including legacy Forge.
Their shared `start_audio_client` path uses the production launcher when the
runtime root is present, with no fallback on failure. The existing server,
audio, UI, ownership and cleanup checks remain required.

Each client records its candidate hash, runtime-manifest hash, Java, classpath
and installed-mod hashes under `packaged-launch-records`. These are launch
provenance, not a success certificate. Acceptance still requires closed logs,
directly reviewed identifiable program video and controls, correlated physical
audio, reconnect, cleanup, exact deployed-server parity and restored remote
configuration. Confirm task client/X processes, listeners and audio modules are
gone. Preserve original failed reports and append corrected evidence rather
than rewriting failures as passes.

The real-Plex full-control path also saves three fixed pairs of world-view
captures under `<profile>.post-reconnect-video` after reconnect and physical
PCM recording have finished. Both clients must acknowledge a fresh correctly
privileged controller before it is closed; this avoids opening a pause menu by
sending Escape blindly to a newly joined world. Each pair requires advancing
render timestamps on the same television. The capture helper validates the
gate-owned private displays before input and capture, refuses existing output,
and does not select or retry frames based on their content. Its result records
`directVisualReviewPending=true`: all six pictures still need direct review
for identifiable program content. A successful PCM correlation or decoded-frame
hash cannot certify a black, obscured or menu-covered television image.

The input regressions run with `./gradlew verifyPackagedClientInputs` and are
included in `verifyAllTargets`. Preparation and launch prerequisites never
replace real-Plex acceptance or the final exact-SHA hosted CI/parity gate.
The owner-stream assertion regressions run with `./gradlew verifyOwnerTimeline`.
Post-reconnect capture regressions run with `./gradlew verifyPostReconnectCapture`.

Legacy full-control video cases, including deterministic local/CI cases, now
exercise two F3+T resource/sound reloads per client before physical PCM capture.
F3 is held across game ticks while T is delivered and released in a finally
block; an instantaneous chord did not reach the legacy resource manager. Before
each cycle the observer reopens and acknowledges the controller, then closes it
to restore the world view, since T can leave legacy chat open after a reload.
`exercise-video-resource-reload.py` requires fresh ordered reload/shutdown/startup
markers, the same active session/generation/item, and advancing render timestamps
after sound startup. It does not claim audible or visible recovery. The following
PCM check and six fixed world-view images are mandatory; each image still needs
direct review. Run the observer regressions with `./gradlew verifyVideoResourceReload`.

This test exposed a real context-destruction crash in the previous legacy JAR.
The fix must pass fresh runtime and exact-byte acceptance before that lifecycle
operation can be certified; see [release acceptance](RELEASE_ACCEPTANCE.md).
