# Release acceptance

**The local 1.0.1 candidate is release-ready but has not been published.** The
deleted v1.0.0 release failed against real Plex before playback began. GitHub
issue #1 proved that the controller path parsed Plex stream metadata differently
from the fake fixture, while the old credentialed smoke skipped
`metadataDetails()` and therefore skipped the failing path.

The replacement fixes boolean and numeric Plex `selected` stream flags and has
passed the shared production metadata, master/media playlist, segment-fetch,
and native H.264/AAC decoder path. On 2026-08-28, the decisive 1.21.1 NeoForge
gate used the in-game controller UI against the operator's real Plex server.
Both real clients rendered identifiable program video and reached a common
rendered-frame hash. Their 7.95-second physical audio captures correlated at
0.704172 with 0 ms measured lag; final decoder drift was 26 ms and 30 ms, with
zero fallbacks, recoveries, dropped frames, or audio underruns. Teardown removed
the clients, server, ports, Plex sessions/transcodes, and credential residue.

After the live run, the final 1.0.1 regression pass rebuilt and verified all 21
runtime profiles, passed all 10 GameTests, indexed the 16 canonical artifacts,
and byte-inspected every JAR for metadata, bytecode, libraries, assets, notices,
credentials, and checksums. `inspectReleaseArtifacts` completed successfully in
6 minutes 57 seconds under Java 21. The exact final 1.0.1 NeoForge JAR then
passed the deterministic two-client runtime gate with a common rendered-frame
hash, 0.990893 physical-audio correlation at -10 ms measured lag, zero decoder
fallbacks/recoveries/drops/underruns, and clean process/port teardown. The
existing `v1.0.0` tag points to older code, so any authorized publication must
use 1.0.1 rather than repurposing that tag.

Satisfied release checks:

* `./gradlew test` and `./gradlew verifyRelease` pass.
* `./scripts/run-dedicated-server-gate.sh 1.21.1-neoforge` passes and leaves
  no game process or port behind.
* The final JAR contains the native decoder facade and license notices, and
  contains no Plex URL or credential.
* Fake-Plex tests cover authenticated HLS, bounded transfer, malformed data,
  persistence/restart, and redacted diagnostics.
* Every enabled Quick TV Kit constructs only after its entire footprint is
  loaded and unobstructed, persists its named rendition target, honors global
  and per-resolution server switches, and tears down without stale TV state.
* Screen Pixel, controller, casing, speaker, TV Remote, and redstone receiver
  registrations, assets, translations, and survival recipes are present in
  every applicable artifact. Using the Remote on a controller follows the
  controller's authoritative activation/UI path. Receiver input toggles
  playback only on a rising edge and does not repeat while power remains high.
* Two real clients show the same identifiable frame with synchronized audible
  output on one named session. This is the decisive visual acceptance gate.
* A credentialed live Plex server creates and cleanly terminates a compatible
  H.264/AAC session without exposing its URL or token.

The decisive credentialed gate is opt-in and accepts exactly one runtime target:

```bash
CINEMARR_VIDEO_CLIENT_GATE=true \
CINEMARR_LIVE_PLEX_GATE=true \
CINEMARR_PLEX_URL='http://plex.example.invalid:32400' \
CINEMARR_PLEX_TOKEN='...' \
CINEMARR_LIVE_VIDEO_SECTION_ID='1' \
./scripts/run-dedicated-server-gate.sh 1.21.1-neoforge
```

It uses the in-game controller to browse and select live media, requires both
clients to render a common decoded frame and save screenshots, captures audible
program audio from both clients, compares their broadband envelopes within the
same 150 ms limit as the deterministic gate, and rejects evidence containing
the Plex URL or token.

## Historical invalidated live Plex evidence

The 2026-08-27 `PlexVideoLiveSmokeTest` result is not valid release evidence. It
proved that Plex could start and stop a transcode and return non-empty bytes,
but it selected a browse result directly. It did not call `metadataDetails()`,
did not exercise boolean `selected` stream flags, did not resolve HLS through
the production server-manager path, and did not decode the fetched segment.
Issue #1 then reproduced the omitted metadata failure on every tested video.

## Historical fake-Plex two-client regression evidence

### 2026-08-27 Quick TV placement matrix

All 21 exact runtime profiles built the server scene through that loader's real
144p Quick TV block. Each
server logged and persisted the same acceptance contract: a 16x9 physical
screen, 256x144 rendition, and owner `CinemarrVideoA`. Each modern row below also had
two saved screenshots with the numbered color test card visibly identifiable,
the complete fake-Plex master/media/MPEG-TS route, a passing audible-envelope
comparison, and clean teardown.

| Runtime profile | Audio correlation | Measured lag |
| --- | ---: | ---: |
| Fabric 1.20.1 | 0.994679 | -50 ms |
| Quilt 1.20.1 | 0.993132 | 50 ms |
| Forge 1.20.1 | 0.989935 | -40 ms |
| NeoForge 1.20.1 | 0.991707 | 20 ms |
| Fabric 1.20.2 | 0.993858 | 70 ms |
| Quilt 1.20.2 | 0.993567 | -10 ms |
| Forge 1.20.2 | 0.994218 | -20 ms |
| NeoForge 1.20.2 | 0.995144 | -10 ms |
| Fabric 1.21.1 | 0.990926 | 90 ms |
| Quilt 1.21.1 | 0.991250 | 50 ms |
| Forge 1.21.1 | 0.980633 | 40 ms |
| NeoForge 1.21.1 | 0.997385 | 100 ms |
| Fabric 26.1.2 | 0.990218 | 10 ms |
| Quilt 26.1.2 | 0.997147 | -10 ms |
| Forge 26.1.2 | 0.987922 | 70 ms |
| NeoForge 26.1.2 | 0.988920 | -40 ms |
| Fabric 26.2 | 0.999427 | 20 ms |
| Quilt 26.2 | 0.996959 | -20 ms |
| Forge 26.2 | 0.996621 | 80 ms |
| NeoForge 26.2 | 0.990375 | 40 ms |
| Forge 1.7.10, fresh run 1 | 0.986105 | -10 ms |
| Forge 1.7.10, fresh run 2 | 0.978383 | -30 ms |

The modern audio adapter now queries `AL_SOFT_source_latency` atomically with
the source cursor during a bounded silent preroll. It subtracts the measured
physical backend latency when choosing the remaining queued silence, then
anchors the logical media clock to the same physical boundary. Fresh local
regression gates on the compensation implementation passed for Fabric 1.21.1 at
0.989222 correlation / 10 ms lag, NeoForge 1.21.1 at 0.968840 / 40 ms,
NeoForge 26.1.2 at 0.986400 / 20 ms, and NeoForge 26.2 at 0.995487 / 30 ms.
An exact Fabric 26.2 regression after moving the authoritative-target sample
into the same audio-executor callback passed at 0.993323 / 30 ms.
The runtime gate also disables hostile-mob spawning after a hosted zombie moved
both positional-audio probes during capture; a hardened NeoForge 1.21.1 rerun
passed at 0.990618 / 40 ms without listener displacement.
One later hosted 26.1.2 Fabric run exposed that deriving wall time indirectly
from a render-thread media estimate could still leave 180 ms of skew. The
adapter now converts each rounded media position back to its exact server epoch
from the authoritative session snapshot, maps that epoch through the client
clock filter inside the OpenAL callback, and compensates against that boundary.
Exact local regressions then passed for Fabric 26.1.2 at 0.986959 / 10 ms and
the serial late-join NeoForge 1.21.1 profile at 0.955958 / 70 ms. That serialized
profile now keeps its 250 ms startup sampling cadence until at least eight
samples include a 50 ms-or-better RTT estimate, or a bounded 16-sample fallback
is reached; the passing clients anchored from 6 ms and 5 ms best-RTT estimates.
The exact Quilt 26.2 + Mod Menu 20.0.1 profile subsequently exposed an omitted
Fabric codec/receiver registration for the server's `cinemarr:error` response.
After auditing and registering that response across every modern loader
adapter, the same profile kept both clients connected, rendered matching frame
SHA-256 and PTS evidence, and passed at 0.980075 correlation / 0 ms lag. The
corresponding exact Forge 26.2 response-path regression passed at 0.991658 / 0
ms with matching frame evidence.
Each run also passed protocol and command-client checks and left no client,
server, fake-Plex process, audio module, or target port behind.

A historical Forge 1.7.10 run measured 0.657605 correlation at 120 ms, but that
result did not reproduce: later fresh runs rendered the same program and emitted
audible output yet measured approximately 680 ms and 400 ms sink separation.
The legacy adapter now starts with silent preroll, waits for confirmed physical
backend progress, accounts for consumed preroll when choosing the shared future
media boundary, keeps a four-second queue runway, and coalesces one-second raw
buffers through Paulscode's asynchronous command thread. Two consecutive fresh
runs then passed at 0.986105 correlation / -10 ms and 0.978383 / -30 ms, restoring
current A/V certification. The original complete modern matrix exceeded 0.98
correlation and remained within 100 ms; the later startup-clock quality
regression remained strongly correlated at 0.955958 and 70 ms. Visual review caught a
Forge 1.21.1 event-matrix defect even though decoded-frame and audio checks had
passed; after the adapter switched to an explicit camera-relative world
transform, both rerun screenshots visibly showed the test card. This is why
saved image inspection remains part of the gate.

The matrix also found two lifecycle races. A delayed viewer-leave notification
is now harmless after the last television detaches from its session, and the
tracking refresh now rescans the player's actual radius instead of relying on a
possibly stale chunk cache. A follower-first 26.1.2 NeoForge run launched and
fully joined client B before client A constructed the Quick TV; both clients
then completed synchronized A/V playback and clean teardown.

After the TV Remote, survival construction recipes, and rising-edge redstone
receiver were added, a fresh 1.21.1 NeoForge regression gate again launched two
real clients. Both screenshots visibly showed the same numbered color test
card, the clients reported the common rendered-frame SHA-256
`028918a15a341e02f28328967c02c350685ed4b88e9cae106bcea6b6268618e0`,
and the audio envelopes measured 0.988031 correlation at 50 ms lag. Fake Plex
served the master playlist, media playlist, and MPEG-TS segments, and the gate
left no game process or port behind.

### Hardware-decoder experiment

On 2026-08-28, the standalone schema-3 benchmark exercised the realistic 144p
through 4K range with two warmups and five measured samples per backend. Linux
software/auto client gates then passed all 21 maintained runtime profiles with
the existing two-client identifiable-frame, screenshot, PCM synchronization,
and clean-teardown requirements. The packaged Linux ARM64 classifier also
passed software decoding and unsupported-hardware fallback using an actual
ARM64 JVM and JavaCPP runtime under QEMU.

Native Windows 11 testing used a Quadro P600 passed through to a disposable
Q35/OVMF VM, NVIDIA driver 576.02, Temurin 21.0.12.1, and the packaged
JavaCV 1.5.14 / FFmpeg 8.1.2 Windows-x86_64 classifier. Software and the
internal D3D11VA, DXVA2, and CUDA candidates plus `auto` each completed all 35
measured samples from 144p through 4K with SSIM at least 0.999754, matching
timestamps, and zero fallback. The evaluator kept `software` as the default
because D3D11VA increased median decoder-thread CPU
by about 41% at 1080p and 32% at 4K, while low-resolution wall time regressed by
42% to 170%.

The native Windows Minecraft-client gate also reached acceptance on Forge
1.7.10. With `auto` and no explicit device selector, the client selected
D3D11VA (`deviceType=default`), rendered frame SHA-256
`12a15e93bbd8e954fcea6c69fc615a6cd808679ee902c4ef3407ac00e9c56294`,
saved a non-empty 854x480 in-game screenshot, established AAC audio, and
reported zero fallbacks, recoveries, video drops, and audio underruns. The
original PowerShell gate rejected this otherwise valid result because it
incorrectly expected the device-selector diagnostic to equal the backend name;
that assertion was fixed but was not rerun to a formal green result. Native
Windows Minecraft-client behavior remains untested for 1.21.1 NeoForge and
26.2 Fabric. Linux ARM64 hardware decoding, macOS, AMD Windows, Intel Windows,
and other GPU/driver combinations are also untested.

On 2026-08-26, two consecutive fresh invocations of
`CINEMARR_VIDEO_CLIENT_GATE=true ./scripts/run-dedicated-server-gate.sh
1.21.1-neoforge` each launched two independent GUI clients and PipeWire sinks
against the deterministic fake-Plex HLS fixture. Both runs saved visible
test-card screenshots and both client pairs shared the exact decoded-frame
SHA-256 `028918a15a341e02f28328967c02c350685ed4b88e9cae106bcea6b6268618e0`.
The captured 997 Hz program envelopes measured 0.993263 correlation at 70 ms
lag in the first run and 0.993801 at 90 ms in the second. Fake Plex served the
master playlist, media playlist, and MPEG-TS segments each time. The clients,
server, fake Plex process, audio modules, and target ports were gone after both
gates.

On the same date, `1.21.1-fabric` independently passed the full gate after its
server adapter created the acceptance television and dispatched client payloads
on the client thread. Both clients saved visible test-card screenshots, shared
the same decoded-frame SHA-256 above, and produced 997 Hz captures with 0.986383
correlation at -10 ms lag. The server then saved every dimension and stopped;
its clients, fake Plex process, audio modules, and target ports were absent after
the gate. The same launch also loaded all Quick TV recipes without parse errors.

Also on 2026-08-26, `1.21.1-forge` passed the full gate after its adapter added
the same acceptance-TV scene and client screenshot readiness proof. Both clients
saved visible test-card screenshots, shared decoded-frame SHA-256
`028918a15a341e02f28328967c02c350685ed4b88e9cae106bcea6b6268618e0`,
and produced 997 Hz captures with 0.992552 correlation at 30 ms lag. Fake Plex
served its master playlist, media playlist, and MPEG-TS program segments. The
server saved cleanly, and the clients, fake Plex process, audio modules, and
target ports were absent after the gate.

The `1.21.1-quilt` runtime profile then passed the same independent gate. Both
clients saved visible test-card screenshots and reached readiness on the exact
same frame at 4.8 seconds. They shared decoded-frame SHA-256
`028918a15a341e02f28328967c02c350685ed4b88e9cae106bcea6b6268618e0`
and produced 997 Hz captures with 0.999974 correlation at 0 ms lag. Fake Plex
served the complete HLS path, the server saved cleanly, and no client, fake Plex
process, audio module, or target port remained.

Fabric and Quilt 1.20.2 passed separate invocations of the same full gate on
2026-08-26. Fabric shared decoded-frame SHA-256
`028918a15a341e02f28328967c02c350685ed4b88e9cae106bcea6b6268618e0`
and measured 0.993816 correlation at -10 ms. Quilt shared decoded-frame SHA-256
`02dc1a1b96d86550a8c14714789352a1c3ed28d5df6366418f4f4f6bcd97a513`
and measured 0.986201 correlation at 70 ms. Both client pairs saved visible
test-card screenshots, exercised the complete fake-Plex HLS route, and left no
client, server, fake Plex process, audio module, or target port behind.

Forge and NeoForge 1.20.2 then passed their own full gates. Forge shared
decoded-frame SHA-256
`028918a15a341e02f28328967c02c350685ed4b88e9cae106bcea6b6268618e0`
and measured 0.991231 correlation at 20 ms; NeoForge shared that same frame and
measured 0.991795 at 0 ms. Both pairs saved unobstructed test-card screenshots,
completed the fake-Plex HLS path without decoder rejection, and stopped cleanly.
Their Forge-family dev runs install the minimal generated JavaCV facade into the
normal classes output so runtime testing exercises the same decoder facade that
is packaged in each release JAR.

All four 1.20.1 runtime profiles also passed the full gate on 2026-08-26. Fabric,
Quilt, Forge, and transitional NeoForge shared decoded-frame SHA-256
`028918a15a341e02f28328967c02c350685ed4b88e9cae106bcea6b6268618e0`
and measured correlations of 0.995863, 0.995783, 0.994928, and 0.986825 at
-50 ms, 30 ms, -40 ms, and -30 ms, respectively. Every client pair saved
visible test-card screenshots and exercised the complete fake-Plex HLS route.
The Forge-family captures were unobstructed; Quilt's leader capture retained a
pause overlay but the video remained visibly identifiable. Every client,
server, fake Plex process, audio module, and target port stopped cleanly.

Fabric, Quilt, Forge, and NeoForge 26.1.2 then passed separate full-gate runs.
Every pair visibly rendered the test card, shared decoded-frame SHA-256
`028918a15a341e02f28328967c02c350685ed4b88e9cae106bcea6b6268618e0`,
and completed the fake-Plex master/media/segment path. Their audio captures
measured 0.994030, 0.991515, 0.998905, and 0.997870 correlation at -20 ms,
30 ms, 0 ms, and 10 ms lag, respectively. Fabric and Quilt each retained one
pause-overlay capture, and the NeoForge scene was night-darkened, but the test
program remained clearly identifiable in every image. All processes, audio
modules, and target ports stopped cleanly.

On 2026-08-27, Fabric, Quilt, Forge, and NeoForge 26.2 passed their separate
full-gate runs. Every client pair shared decoded-frame SHA-256
`028918a15a341e02f28328967c02c350685ed4b88e9cae106bcea6b6268618e0`,
saved two screenshots with the numbered color-bar program visibly identifiable,
and completed the fake-Plex master-playlist, media-playlist, and MPEG-TS path.
Their audio captures measured 0.991396, 0.992901, 0.997424, and 0.992739
correlation at 20 ms, -10 ms, -10 ms, and 10 ms lag, respectively. Fabric's
follower, Quilt's leader, and NeoForge's leader captures retained pause overlays;
the NeoForge scene was also night-darkened, but the program remained visible.
Every client, server, fake Plex process, audio module, and target port stopped
cleanly.

Forge 1.7.10 first passed its isolated Java 8 / LWJGL 2 gate on 2026-08-27. Two real
clients visibly rendered the synthetic program and shared decoded-frame SHA-256
`028918a15a341e02f28328967c02c350685ed4b88e9cae106bcea6b6268618e0`.
The 997 Hz captures measured 0.983183 correlation at -110 ms lag. The gate also
proved master-playlist, media-playlist, and MPEG-TS relay, saved both rendered-TV
screenshots, and left no client, server, fake Plex process, audio module, or
target port behind. That historical pass was superseded by the failed fresh
synchronization reruns documented above. After the physical-start correction,
two further consecutive invocations again shared the same identifiable frame,
passed the strict audible-envelope comparison at -10 ms and -30 ms, and left no
client, server, fake Plex process, audio module, or target port behind.

All four 1.20.1 and all four 1.20.2 loader profiles have the additional
two-client evidence above. The 1.21.1 Quilt, Fabric, and Forge profiles have the
additional two-client evidence recorded above.
NeoForge, Fabric, Quilt, and Forge on every modern target from 1.20.1 through
26.2 have passed the deterministic two-client A/V gate, as has Forge 1.7.10.
These historical fake-Plex gates do not satisfy the replacement-release gate.
No artifact is release-certified until real-Plex controller playback and the
full post-fix build/runtime matrix are freshly verified.

Keep logs, JAR listings, checksums, and process/port evidence for each release.
Credentials are process-environment-only and must never appear in retained
evidence.
