# Release acceptance

No artifact is currently release-certified. NeoForge, Fabric, Quilt, and Forge
on 1.20.1, 1.20.2, 1.21.1, 26.1.2, and 26.2 have passed the deterministic real
two-client A/V gate, but live Plex validation is still outstanding. A successful
Gradle build alone is insufficient.

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
* Two real clients show the same identifiable frame with synchronized audible
  output on one named session. This is the decisive visual acceptance gate.
* A credentialed live Plex server creates and cleanly terminates a compatible
  H.264/AAC session without exposing its URL or token.

## Recorded two-client evidence

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

All four 1.20.1 and all four 1.20.2 loader profiles have the additional
two-client evidence above. The 1.21.1 Quilt, Fabric, and Forge profiles have the
additional two-client evidence recorded above.
Forge 1.7.10 is build-verified and has dedicated/protocol-rejection launch
evidence, but no matching-client renderer/audio evidence. NeoForge, Fabric,
Quilt, and Forge on every modern target from 1.20.1 through 26.2 have passed the
deterministic two-client A/V gate; all remain unreleased pending live Plex
validation. Forge 1.7.10 remains unreleased pending its matching-client A/V and
live Plex gates.

Keep logs, JAR listings, checksums, and process/port evidence for each release.
Credentials are process-environment-only and must never appear in retained
evidence.
