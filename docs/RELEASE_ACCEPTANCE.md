# Release acceptance

The only currently releasable artifact is NeoForge 1.21.1 (Java 21). A
successful Gradle build alone is insufficient.

Required checks:

* `./gradlew test` and `./gradlew verifyRelease` pass.
* `./scripts/run-dedicated-server-gate.sh 1.21.1-neoforge` passes and leaves
  no game process or port behind.
* The final JAR contains the native decoder facade and license notices, and
  contains no Plex URL or credential.
* Fake-Plex tests cover authenticated HLS, bounded transfer, malformed data,
  persistence/restart, and redacted diagnostics.
* Two real clients show the same identifiable frame with synchronized audible
  output on one named session. This is the decisive visual acceptance gate.

Every directory under `platforms/` other than the NeoForge baseline is an
untested Jammarr-derived adapter scaffold. It must independently pass compile,
launch, runtime, clean shutdown, and the two-client A/V gate before being
published or added to a release matrix.

Keep logs, JAR listings, checksums, and process/port evidence for each release.
Credentials are process-environment-only and must never appear in retained
evidence.
