# Cinemarr

<img src="artwork/cinemarr-icon.png" alt="Cinemarr logo" width="160">

Cinemarr is a required client-and-server Minecraft mod for server-authoritative Plex movie and television playback on player-built screens. The server owns Plex credentials, library policy, transcodes, timelines, and media relay. Clients receive bounded HLS media, decode a visible session once, and render it across that television's dynamic texture.

## 1.0 prerelease status

This checkout targets **Cinemarr 1.0.0**. It is a prerelease development build,
**not a release candidate**. Custom TV display controls and independent per-TV
streams are now required 1.0 work across all **16 artifacts / 21 runtimes**.
See the [full feature specification](docs/1.0_CUSTOM_TV_DISPLAY_PLAN.md) and
[release hardening plan](docs/1.0_RELEASE_HARDENING_PLAN.md).

The earlier V11 candidate used protocol 10 and screen-data schema 3. The new
feature requires deliberate protocol and saved-data migrations; the integrated
versions and all adapters must be verified together before certification.
New custom TVs must default to Fit, Detailed, Auto, while Quick TV construction
sizes and preset resolutions remain intact. Implementation is in progress.

The earlier runtime batch stopped when source changes superseded its frozen
candidate. Prior matching builds, partial runtime passes, native checks and
Plex-session diagnostics remain historical evidence. They do not certify the
changed implementation, and that batch is no longer running.

Fresh certification must include the existing 37 local cases, feature smoke on
all 21 profiles, additional capacity/failure/raster supplements, two matching
builds, all ten GameTests, native guests, real-Plex multi-TV/two-client playback,
recovery/lifecycle and cleanup. The reviewed final commit must pass the full
local release gate and all required GitHub jobs with matching downloaded
artifacts. See [release acceptance](docs/RELEASE_ACCEPTANCE.md).

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

NeoForge 20.2.93 (Minecraft 1.20.2) acceptance requires
`earlyWindowControl = false` in the instance's `config/fml.toml` to avoid a
loader splash-screen race. Close Minecraft before changing that key and retain
the other settings. This does not disable in-game rendering; splash-enabled
startup is not certified. See [release acceptance](docs/RELEASE_ACCEPTANCE.md).

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
./gradlew inspectReleaseArtifacts --no-daemon --max-workers=1 --no-configuration-cache
```

The deterministic fake-Plex gate is useful for regressions, but is not release proof:

```bash
CINEMARR_VIDEO_CLIENT_GATE=true \
./scripts/run-dedicated-server-gate.sh 1.21.1-neoforge
```

Earlier green automation missed real audio, concurrency, camera and controller defects. Subsequent live pressure, reload and lifecycle checks prompted further fixes. Their source-bound acceptance and earlier complete builds are regression evidence, not certification of the current uncommitted code or final release bytes.

The full local entry point is `./gradlew releaseMatrixGate --no-configuration-cache --max-workers=1` under Java 21, from a clean candidate checkout with fresh evidence directories. It requires all 16 artifacts, ten GameTests, all 21 runtimes and the required supplements. Direct image review, reproducibility, exact-artifact real-Plex/recovery/lifecycle and Windows/ARM checks, security/documentation review, scoped commits/push and exact-SHA green hosted CI with matching artifact hashes remain separate completion requirements. Installed Windows/ARM test guests are retained powered off between tests; see the [guest runbook](docs/NATIVE_TEST_GUESTS.md). See [the 1.0 hardening plan](docs/1.0_RELEASE_HARDENING_PLAN.md) for current evidence. The [earlier Jammarr target-feasibility assessment](docs/JAMMARR_TARGET_FEASIBILITY.md) concerns later expansion, does not change the 1.0 matrix, and will be refreshed only after the preceding hardening and CI gates finish.

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

The packaged native classifiers are exercised by `run-linux-arm64-native-smoke.sh` and `run-windows-x64-native-smoke.sh`. Both reuse dedicated headless guests, stop their uniquely tagged QEMU units after every run, and retain a stopped-state audit with the evidence. Provisioning, reuse, failure recovery, and the read-only infrastructure audit are documented in [retained native test guests](docs/NATIVE_TEST_GUESTS.md).

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
