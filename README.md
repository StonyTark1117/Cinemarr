# Cinemarr

<img src="artwork/cinemarr-icon.png" alt="Cinemarr logo" width="160">

Cinemarr is a required client-and-server Minecraft mod for server-authoritative Plex movie and TV playback on player-built screens. The Minecraft server owns Plex credentials, library policy, transcodes, timelines, and media relay; tracking clients decode each compressed stream once and render it across the visible parts of a shared dynamic texture.

The complete product requirements and acceptance criteria live in [Proposal](Proposal). Cinemarr is a standalone sibling project and does not require Jammarr at runtime.

## Current implementation

Cinemarr now has video-runtime adapters for Forge 1.7.10 and Fabric, Quilt-compatible Fabric, Forge, and NeoForge targets spanning Minecraft 1.20.1, 1.20.2, 1.21.1, 26.1.2, and 26.2. These targets are build- and dedicated-launch-tested to the levels recorded in [the compatibility matrix](docs/COMPATIBILITY.md). Every listed runtime additionally has real two-client deterministic-HLS visual/audio evidence; no artifact is release-certified until its remaining gates, including live Plex validation, pass.

Implemented foundations include:

- Java-8-compatible, Minecraft-independent screen geometry for arbitrary four-connected planar silhouettes, holes, all six facings, exact visibility masks, and unloaded-chunk membership.
- The proposal defaults: 4–65,536 pixels, dimensions through 2,048, two active sessions, four screens per owner, all operator-configurable.
- Exact fit, fill, and stretch transforms plus bounded, even H.264 rendition selection.
- Server-only Plex video-library resolution, movie/show browsing, rating and permission filtering, HLS transcode start/fetch/stop, same-origin segment confinement, bounded responses, and token-safe client models.
- 16 KiB hash-addressed media payloads, keyframe seek policy, generation-safe asynchronous work, clock/drift policy inherited from the shared transport core, and authoritative watch-party/session lifecycle.
- Screen Pixel, TV Controller, casing, and speaker blocks on every target. Pixels remain ordinary blocks; placement/removal updates world saved data, while controller activation reconstructs and persists a screen without force-loading its chunks.
- Eight progressively crafted Quick TV Kit blocks: 144p, 240p, 480p, 720p, 1080p, 1440p, 4K, and 8K. Placing one preflights a loaded, unobstructed 16:9 footprint, builds a bounded dense screen, activates it, and persists the exact named rendition target. A literal 8K one-block-per-pixel wall would contain 33,177,600 blocks, so quick kits are deliberately bounded prefabs; hand-built Screen Pixels retain exact one-block/one-logical-pixel behavior.
- Global `quickTvKitsEnabled` and per-resolution `quickTv...Enabled` server settings. A disabled kit stays registered for world/save compatibility but cannot construct or activate its prefab.

The in-world decoder/renderer, control UI, subtitle/audio-track selection, positional sound, persistence/reconnect behavior, server-owned HLS relay, and clean protocol rejection paths are implemented. On NeoForge 1.21.1, two consecutive fresh gates made two real clients visibly render the same identifiable synthetic H.264/AAC program and produce synchronized audible output. The captures measured 0.993263 correlation at 70 ms lag and 0.993801 at 90 ms lag. Fabric 1.21.1 independently passed with 0.986383 correlation at -10 ms lag, Quilt with 0.999974 at 0 ms, and Forge with 0.992552 at 30 ms. Fabric, Quilt, Forge, and NeoForge 1.20.2 subsequently passed with correlations of 0.993816, 0.986201, 0.991231, and 0.991795 at -10 ms, 70 ms, 20 ms, and 0 ms, respectively. The four 1.20.1 profiles then passed at 0.995863, 0.995783, 0.994928, and 0.986825 correlation with -50 ms, 30 ms, -40 ms, and -30 ms measured lag. Fabric, Quilt, Forge, and NeoForge 26.1.2 passed at 0.994030, 0.991515, 0.998905, and 0.997870 correlation with -20 ms, 30 ms, 0 ms, and 10 ms lag. The four 26.2 profiles passed at 0.991396, 0.992901, 0.997424, and 0.992739 correlation with 20 ms, -10 ms, -10 ms, and 10 ms lag. Forge 1.7.10 passed at 0.983183 correlation and -110 ms lag. Every passing gate produced a common decoded frame, two visible screenshots, the full HLS path, and clean teardown. The recorded synthetic results do not substitute for live Plex validation.

## Server files

On first server start Cinemarr generates:

- `world/serverconfig/cinemarr-server.toml`
- `world/serverconfig/cinemarr-libraries.toml`

Keep the Plex token server-side. `CINEMARR_PLEX_TOKEN` overrides the file value and is recommended. Clients never receive a Plex URL or token.

An allowlist entry looks like:

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

No libraries are visible until an operator adds an entry.

## Verification

Run the NeoForge 1.21.1 unit and artifact gate under Java 21:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
PATH=/usr/lib/jvm/java-21-openjdk/bin:$PATH \
./gradlew verifyRelease --no-daemon --max-workers=1
```

The opt-in live Plex video smoke reads credentials only from its process environment. It resolves an allowed video library, starts a real H.264/AAC HLS session, fetches a media segment, and stops the transcode:

```bash
CINEMARR_LIVE_TEST=true \
CINEMARR_PLEX_URL='http://plex.example.invalid:32400' \
CINEMARR_PLEX_TOKEN='...' \
CINEMARR_LIVE_VIDEO_LIBRARY='Movies' \
./gradlew :core:test --tests stonytark.cinemarr.core.server.PlexVideoLiveSmokeTest \
  --no-daemon --max-workers=1 --rerun-tasks
```

Credentialed smoke results are deliberately non-cacheable. Credentials are not packaged into artifacts.

The opt-in real two-client gate creates a deterministic fake-Plex HLS program, launches two independent GUI clients and audio sinks, requires a common rendered frame plus visible screenshots, checks audible PCM synchronization, and verifies clean shutdown. It is recorded green for every listed runtime, including Forge 1.7.10; for example:

```bash
CINEMARR_VIDEO_CLIENT_GATE=true \
./scripts/run-dedicated-server-gate.sh 1.21.1-neoforge

# Or use the Fabric adapter:
CINEMARR_VIDEO_CLIENT_GATE=true \
./scripts/run-dedicated-server-gate.sh 1.21.1-fabric

# Or run that adapter against Quilt's own runtime profile:
CINEMARR_VIDEO_CLIENT_GATE=true \
./scripts/run-dedicated-server-gate.sh 1.21.1-quilt

# Or use the Forge adapter:
CINEMARR_VIDEO_CLIENT_GATE=true \
./scripts/run-dedicated-server-gate.sh 1.21.1-forge

# Or exercise the isolated Java 8 / LWJGL 2 implementation:
CINEMARR_VIDEO_CLIENT_GATE=true \
./scripts/run-dedicated-server-gate.sh 1.7.10-forge
```
