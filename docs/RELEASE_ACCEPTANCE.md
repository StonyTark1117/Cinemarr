# Release acceptance

No artifact is currently release-certified. A successful Gradle build alone is
insufficient.

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

The 1.20.1 Fabric/Quilt, Forge, and transitional NeoForge adapters are build-
verified, dedicated-launch-tested, and protocol-client-tested. The 1.20.2
Fabric/Quilt, Forge, and NeoForge adapters have the same evidence. The 1.21.1
Fabric/Quilt and Forge adapters have compile and dedicated-launch evidence.
The 26.1.2 and 26.2 Fabric/Quilt, Forge, and NeoForge adapters are build-verified,
dedicated-launch-tested, and protocol-client-tested. Forge 1.7.10 is build-
verified and has dedicated/protocol-rejection launch evidence, but no matching-
client renderer/audio evidence. All remain unreleased until they pass the two-
client A/V gate.

Keep logs, JAR listings, checksums, and process/port evidence for each release.
Credentials are process-environment-only and must never appear in retained
evidence.
