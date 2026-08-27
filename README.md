# Cinemarr

Cinemarr is a required client-and-server Minecraft mod for server-authoritative Plex movie and TV playback on player-built screens. The Minecraft server owns Plex credentials, library policy, transcodes, timelines, and media relay; tracking clients decode each compressed stream once and render it across the visible parts of a shared dynamic texture.

The complete product requirements and acceptance criteria live in [Proposal](Proposal). Cinemarr is a standalone sibling project and does not require Jammarr at runtime.

## Current implementation

Cinemarr now has video-runtime adapters for Forge 1.7.10 and Fabric, Quilt-compatible Fabric, Forge, and NeoForge targets spanning Minecraft 1.20.1, 1.20.2, 1.21.1, 26.1.2, and 26.2. These targets are build- and dedicated-launch-tested to the levels recorded in [the compatibility matrix](docs/COMPATIBILITY.md). NeoForge, Fabric, Quilt, and Forge on 1.21.1 additionally have real two-client deterministic-HLS visual/audio evidence; no artifact is release-certified until its remaining gates, including live Plex validation, pass.

Implemented foundations include:

- Java-8-compatible, Minecraft-independent screen geometry for arbitrary four-connected planar silhouettes, holes, all six facings, exact visibility masks, and unloaded-chunk membership.
- The proposal defaults: 4–65,536 pixels, dimensions through 2,048, two active sessions, four screens per owner, all operator-configurable.
- Exact fit, fill, and stretch transforms plus bounded, even H.264 rendition selection.
- Server-only Plex video-library resolution, movie/show browsing, rating and permission filtering, HLS transcode start/fetch/stop, same-origin segment confinement, bounded responses, and token-safe client models.
- 16 KiB hash-addressed media payloads, keyframe seek policy, generation-safe asynchronous work, clock/drift policy inherited from the shared transport core, and authoritative watch-party/session lifecycle.
- Screen Pixel, TV Controller, casing, and speaker blocks on every target. Pixels remain ordinary blocks; placement/removal updates world saved data, while controller activation reconstructs and persists a screen without force-loading its chunks.
- Eight progressively crafted Quick TV Kit blocks: 144p, 240p, 480p, 720p, 1080p, 1440p, 4K, and 8K. Placing one preflights a loaded, unobstructed 16:9 footprint, builds a bounded dense screen, activates it, and persists the exact named rendition target. A literal 8K one-block-per-pixel wall would contain 33,177,600 blocks, so quick kits are deliberately bounded prefabs; hand-built Screen Pixels retain exact one-block/one-logical-pixel behavior.
- Global `quickTvKitsEnabled` and per-resolution `quickTv...Enabled` server settings. A disabled kit stays registered for world/save compatibility but cannot construct or activate its prefab.

The in-world decoder/renderer, control UI, subtitle/audio-track selection, positional sound, persistence/reconnect behavior, server-owned HLS relay, and clean protocol rejection paths are implemented. On NeoForge 1.21.1, two consecutive fresh gates made two real clients visibly render the same identifiable synthetic H.264/AAC program and produce synchronized audible output. The captures measured 0.993263 correlation at 70 ms lag and 0.993801 at 90 ms lag. Fabric 1.21.1 independently passed with 0.986383 correlation at -10 ms lag, Quilt with 0.999974 at 0 ms, and Forge with 0.992552 at 30 ms. All four loader profiles produced the same common decoded-frame hash, two visible screenshots, the full HLS path, and clean teardown. Other targets still require equivalent matching-client evidence, and the recorded synthetic results do not substitute for live Plex validation.

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

The opt-in real two-client gate creates a deterministic fake-Plex HLS program, launches two independent GUI clients and audio sinks, requires a common rendered frame plus visible screenshots, checks audible PCM synchronization, and verifies clean shutdown. It is recorded green for `1.21.1-neoforge`, `1.21.1-fabric`, `1.21.1-quilt`, and `1.21.1-forge`:

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
```
