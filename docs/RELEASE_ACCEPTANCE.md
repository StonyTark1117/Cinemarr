# Release acceptance

No artifact is currently release-certified. NeoForge 1.21.1 has passed the
deterministic real two-client A/V gate, but live Plex validation is still
outstanding. A successful Gradle build alone is insufficient.

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

The 1.20.1 Fabric/Quilt, Forge, and transitional NeoForge adapters are build-
verified, dedicated-launch-tested, and protocol-client-tested. The 1.20.2
Fabric/Quilt, Forge, and NeoForge adapters have the same evidence. The 1.21.1
Fabric/Quilt and Forge adapters have compile and dedicated-launch evidence.
The 26.1.2 and 26.2 Fabric/Quilt, Forge, and NeoForge adapters are build-verified,
dedicated-launch-tested, and protocol-client-tested. Forge 1.7.10 is build-
verified and has dedicated/protocol-rejection launch evidence, but no matching-
client renderer/audio evidence. NeoForge 1.21.1 alone has passed the deterministic
two-client A/V gate; it remains unreleased pending live Plex validation. All other
targets remain unreleased pending their own two-client A/V and live Plex gates.

Keep logs, JAR listings, checksums, and process/port evidence for each release.
Credentials are process-environment-only and must never appear in retained
evidence.
