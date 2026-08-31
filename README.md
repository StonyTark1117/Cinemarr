# Cinemarr

<img src="artwork/cinemarr-icon.png" alt="Cinemarr logo" width="160">

Cinemarr is a required client-and-server Minecraft mod for server-authoritative Plex movie and television playback on player-built screens. The server owns Plex credentials, library policy, transcodes, timelines, and media relay. Clients receive bounded HLS media, decode a visible session once, and render it across that television's dynamic texture.

## 1.0 prerelease status

This checkout targets **Cinemarr 1.0.0**, protocol **10**, and screen-data schema **3**. It is a prerelease development build, not a release candidate. A green compile or fake-Plex run is regression evidence only; release readiness requires the complete 16-artifact / 21-runtime matrix plus a credentialed, two-client controller/UI playback run against real Plex and clean teardown.

The old global Plex-music queue, stations, MP3 transport, music UI, and their bundled JLayer/Jump3r libraries have been removed. Cinemarr 1.0 is a television/video mod; it does not require Jammarr.

Implemented release foundations include:

- Forge 1.7.10 and Fabric, Quilt-compatible Fabric, Forge, and NeoForge adapters for Minecraft 1.20.1, 1.20.2, 1.21.1, 26.1.2, and 26.2.
- Server-only Plex credentials and allowlisted movie/show libraries.
- Persistent player-built screens, controllers, remotes, redstone receivers, fit/fill/stretch presentation, named watch parties, queueing, track/subtitle selection, and synchronized positional audio.
- Hash-checked 16 KiB media chunks, bounded transfer windows, rate limits, HLS origin confinement, rendition caps, segment retries, and token-redacted failures.
- Transactional Quick TV construction in batches of at most 256 blocks per tick, with obstruction/unload rollback, persisted crash-recovery footprints, and generated-pixel teardown.
- Eight Quick TV recipes from 144p through 8K. `bounded` mode is the safe default. `literal` mode permits only a footprint that fits the 65,536-pixel ceiling; larger literal presets are refused before placement.
- Software video decoding by default. The only public decoder modes are `software`, `auto`, and `vaapi`; hardware failure falls back to software.

Supported packaged native targets are Linux x86-64, Linux ARM64, and Windows x86-64. Linux clients require the standard libudev, libdrm, and libva runtime libraries; see [compatibility](docs/COMPATIBILITY.md). macOS is not supported by the 1.0 artifact set.

## Configuration

On first start Cinemarr generates:

- `world/serverconfig/cinemarr-server.toml`
- `world/serverconfig/cinemarr-libraries.toml`
- `config/cinemarr-client.toml` on clients

Keep the Plex token server-side. `CINEMARR_PLEX_TOKEN` overrides the server file and is recommended. Clients never receive the Plex URL or token.

Important server settings include `quickTvBuildMode`, `maximumVideoWidth`, `maximumVideoHeight`, `maximumVideoBitrateKbps`, screen/owner/stream limits, and global/per-preset Quick TV switches.

Example allowlist:

```toml
[[libraries]]
id = "family_movies"
section = "Movies"
displayName = "Family Movies"
allowMovies = true
allowShows = false
maximumContentRating = "PG-13"
permissionLevel = 0
```

No library is visible until an operator adds an allowlist entry. Every adapter reports the redacted `disabled`, `connecting`, `ready`, or `degraded` Plex state through `/cinemarr diagnostics`, retries degraded connections automatically, and exposes operator-only `/cinemarr retry`.

## Verification

Run the primary unit gate under Java 21:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
PATH=/usr/lib/jvm/java-21-openjdk/bin:$PATH \
./gradlew test --no-daemon --max-workers=1 --no-configuration-cache
```

Build and inspect the canonical release set only when preparing a release candidate:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
PATH=/usr/lib/jvm/java-21-openjdk/bin:$PATH \
./gradlew verifyRelease --no-daemon --max-workers=1 --no-configuration-cache
```

The deterministic fake-Plex gate is useful for regressions, but is not release proof:

```bash
CINEMARR_VIDEO_CLIENT_GATE=true \
./scripts/run-dedicated-server-gate.sh 1.21.1-neoforge
```

Protocol-10 code commit `1cbd341` passed the original full matrix and hosted bundle gates. A later hosted run correctly found a 330 ms 1.21.1 Fabric audio offset; `21431bf` fixes the non-monotonic OpenAL source-start cursor, passes the private-Xvfb gate locally at 80 ms, and is covered by a complete local `verifyAllTargets` pass. GitHub Actions run `33401793006` is fully green through documentation SHA `4f6541f`: all 16 artifact/runtime jobs and the aggregate gate passed, and the downloaded 16-JAR bundle passed checksums, deep inspection, and local hosted-input index parity. Other later commits make DiscPanel artifact deployment fail closed and narrowly retry identified upstream dependency-download failures. Cinemarr remains a prerelease because the exact candidate still needs credentialed real-Plex, recovery, lifecycle, configured-secret, and packaged-native recertification, followed by green CI for the final documentation SHA. Earlier real-Plex and native evidence remains useful regression history but does not independently promote later bytes. See [the 1.0 hardening plan](docs/1.0_RELEASE_HARDENING_PLAN.md) and [the post-1.0 Jammarr target-feasibility assessment](docs/JAMMARR_TARGET_FEASIBILITY.md).

The credentialed release gate uses the in-game controller with a real allowed Plex library, two independent clients, identifiable video, synchronized audible output, and a residue-free teardown. For the managed DiscPanel environment, run the exact-artifact wrapper on each representative boundary:

```bash
DISCOPANEL_API_BASE='https://discopanel.example.invalid' \
DISCOPANEL_SERVER_HOST='minecraft.example.invalid' \
DISCOPANEL_TOKEN='...' \
./scripts/run-discopanel-real-plex-gate.sh 1.21.1-neoforge
```

Use `run-discopanel-plex-recovery-gate.sh` with the same four labels for the credentialed disabled/degraded/manual/automatic retry matrix. The bounded segment-failure gate is opt-in and should cover one legacy and one modern profile:

```bash
CINEMARR_VIDEO_CLIENT_GATE=true CINEMARR_VIDEO_ADVERSE_NETWORK_GATE=true \
./scripts/run-dedicated-server-gate.sh 1.7.10-forge
CINEMARR_VIDEO_CLIENT_GATE=true CINEMARR_VIDEO_ADVERSE_NETWORK_GATE=true \
./scripts/run-dedicated-server-gate.sh 1.21.1-neoforge
```

The packaged native classifiers are exercised by `run-linux-arm64-native-smoke.sh` and `run-windows-x64-native-smoke.sh`; both use disposable tagged VMs and audit their cleanup manifests.

For a locally managed server, the underlying opt-in gate remains:

```bash
CINEMARR_VIDEO_CLIENT_GATE=true \
CINEMARR_LIVE_PLEX_GATE=true \
CINEMARR_PLEX_URL='http://plex.example.invalid:32400' \
CINEMARR_PLEX_TOKEN='...' \
CINEMARR_LIVE_VIDEO_SECTION_ID='1' \
./scripts/run-dedicated-server-gate.sh 1.21.1-neoforge
```

See [release acceptance](docs/RELEASE_ACCEPTANCE.md), [compatibility](docs/COMPATIBILITY.md), and [proposal status](docs/PROPOSAL_STATUS.md) for the remaining 1.0 gates.
