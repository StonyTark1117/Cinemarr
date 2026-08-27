# Release acceptance

All 16 artifacts have completed the recorded release gates. All 21 runtime
profiles passed the deterministic real two-client A/V gate, including
consecutive fresh Forge 1.7.10 runs, and the shared server core passed the
credentialed live Plex H.264/AAC start, fetch, and clean-stop smoke. A successful
Gradle build alone remains insufficient.

Required checks:

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

## Recorded live Plex evidence

On 2026-08-27, `PlexVideoLiveSmokeTest` ran against the operator-supplied LAN
Plex server with its credential handed directly into the process environment.
It resolved the real `Movies` library, selected a media item, created a 640x360
H.264/AAC universal-transcode session, fetched the referenced playlist media,
and completed the clean-stop request without error. The final JUnit report recorded one
executed test in 0.783 seconds with zero skips, failures, or errors and empty
stdout/stderr. The process exited, the worktree remained clean, and a post-run
byte scan found zero credential residue in the checkout or Gradle daemon logs.

## Recorded two-client evidence

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
current A/V certification. The modern profiles all exceeded 0.98 correlation and
remained within 100 ms. Visual review caught a
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

### Windows decoder smoke

The repository's `FfmpegVideoDecoder` was run under Wine 11.15 with an x64
Windows Eclipse Temurin 21.0.12.1 JVM and the release JavaCV 1.5.14 / FFmpeg
8.1.2 Windows classifiers. It loaded the Windows native libraries and decoded
three video frames plus 30 audio frames from the deterministic fixture. This is
a native decoder loading/decoding smoke only; it does not certify a complete
Windows Minecraft client or its OpenGL/audio integration.

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
All 16 artifacts now meet the recorded release-gate criteria. Publication is a
separate operator decision and is not implied by this repository certification.

Keep logs, JAR listings, checksums, and process/port evidence for each release.
Credentials are process-environment-only and must never appear in retained
evidence.
