# Cinemarr

<img src="artwork/cinemarr-icon.png" alt="Cinemarr logo" width="160">

Cinemarr is a required client-and-server Minecraft mod for server-authoritative Plex movie and television playback on player-built screens. The server owns Plex credentials, library policy, transcodes, timelines, and media relay. Clients receive bounded HLS media, decode a visible session once, and render it across that television's dynamic texture.

## 1.0 prerelease status

This checkout targets **Cinemarr 1.0.0**, protocol **9**, and screen-data schema **3**. It is a prerelease development build, not a release candidate. A green compile or fake-Plex run is regression evidence only; release readiness requires the complete 16-artifact / 21-runtime matrix plus a credentialed, two-client controller/UI playback run against real Plex and clean teardown.

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

Current local evidence (2026-08-30): all 21 maintained fake-Plex runtime profiles, all 10 required GameTests, and all 16 canonical 1.0.0/protocol-9 artifact inspections pass. Exact deployed artifacts passed credentialed real-Plex two-client playback on Forge 1.7.10, Quilt 1.20.1, NeoForge 1.21.1, and Fabric 26.2, including controls, reconnect, ownership enforcement, lifecycle teardown, and synchronized audible output. The final Forge 1.7.10 digest `37232612d83ad36729cdfaab3eb36c5736c18b0b86d021136e000c2a05b5495b` passed three fresh full reconnect soaks at 0 ms measured lag. Representative legacy and modern profiles also passed bounded transient-segment retry, retry exhaustion with a redacted error, and same-session recovery. Credentialed disabled/degraded/manual-retry/automatic-retry recovery passed at the four representative adapter boundaries. Packaged native software decoding passed 144p, 480p, and 1080p on both an aarch64 Debian guest with `linux-arm64` and native Windows 11 AMD64 with `windows-x86_64`; the Windows FFmpeg DLL also reported PE machine `0x8664`. The final source/runtime credential scan found no configured credential or private endpoint, all 21 managed servers were stopped with autostart disabled, and the four representative servers retained the exact indexed artifacts. The scoped release tree rebuilt twice to the same complete SHA-256 manifest, so this checkout is release-ready; tagging and publication have not been performed.

The credentialed release gate uses the in-game controller with a real allowed Plex library, two independent clients, identifiable video, synchronized audible output, and a residue-free teardown. For the managed DiscPanel environment, run the exact-artifact wrapper on each representative boundary:

```bash
DISCOPANEL_TOKEN='...' ./scripts/run-discopanel-real-plex-gate.sh 1.7.10-forge
DISCOPANEL_TOKEN='...' ./scripts/run-discopanel-real-plex-gate.sh 1.20.1-quilt
DISCOPANEL_TOKEN='...' ./scripts/run-discopanel-real-plex-gate.sh 1.21.1-neoforge
DISCOPANEL_TOKEN='...' ./scripts/run-discopanel-real-plex-gate.sh 26.2-fabric
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
