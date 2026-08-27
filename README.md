# Cinemarr

<img src="artwork/cinemarr-icon.png" alt="Cinemarr logo" width="160">

Cinemarr is a required client-and-server Minecraft mod for server-authoritative Plex movie and TV playback on player-built screens. The Minecraft server owns Plex credentials, library policy, transcodes, timelines, and media relay; tracking clients decode each compressed stream once and render it across the visible parts of a shared dynamic texture.

The complete product requirements and acceptance criteria live in [Proposal](Proposal). Cinemarr is a standalone sibling project and does not require Jammarr at runtime.

## Current implementation

Cinemarr now has video-runtime adapters for Forge 1.7.10 and Fabric, Quilt-compatible Fabric, Forge, and NeoForge targets spanning Minecraft 1.20.1, 1.20.2, 1.21.1, 26.1.2, and 26.2. These targets are build- and dedicated-launch-tested to the levels recorded in [the compatibility matrix](docs/COMPATIBILITY.md). All 21 profiles additionally have repeatable real two-client deterministic-HLS visual/audio evidence. The shared server core has also passed its credentialed live Plex H.264/AAC start, fetch, and clean-stop gate, completing the recorded release criteria for all 16 artifacts.

Implemented foundations include:

- Java-8-compatible, Minecraft-independent screen geometry for arbitrary four-connected planar silhouettes, holes, all six facings, exact visibility masks, and unloaded-chunk membership.
- The proposal defaults: 4–65,536 pixels, dimensions through 2,048, two active sessions, four screens per owner, all operator-configurable.
- Exact fit, fill, and stretch transforms plus bounded, even H.264 rendition selection.
- Server-only Plex video-library resolution, movie/show browsing, rating and permission filtering, HLS transcode start/fetch/stop, same-origin segment confinement, bounded responses, and token-safe client models.
- 16 KiB hash-addressed media payloads, keyframe seek policy, generation-safe asynchronous work, clock/drift policy inherited from the shared transport core, and authoritative watch-party/session lifecycle.
- Screen Pixel, TV Controller, casing, speaker, and TV Redstone Receiver blocks plus a craftable TV Remote on every target, with survival recipes for the full construction set. Pixels remain ordinary blocks; placement/removal updates world saved data, while controller activation reconstructs and persists a screen without force-loading its chunks. Using the Remote on a controller follows that same authoritative activation/UI path. A powered receiver directly adjacent to a controller toggles its active named session on each low-to-high redstone edge; steady power does not repeatedly toggle playback.
- Eight progressively crafted Quick TV Kit blocks: 144p, 240p, 480p, 720p, 1080p, 1440p, 4K, and 8K. Placing one preflights a loaded, unobstructed 16:9 footprint, builds a bounded dense screen, activates it, and persists the exact named rendition target. A literal 8K one-block-per-pixel wall would contain 33,177,600 blocks, so quick kits are deliberately bounded prefabs; hand-built Screen Pixels retain exact one-block/one-logical-pixel behavior.
- Global `quickTvKitsEnabled` and per-resolution `quickTv...Enabled` server settings. A disabled kit stays registered for world/save compatibility but cannot construct or activate its prefab.

The in-world decoder/renderer, control UI, subtitle/audio-track selection, positional sound, persistence/reconnect behavior, server-owned HLS relay, and clean protocol rejection paths are implemented. On 2026-08-27, every one of the 21 listed runtime profiles built the gate screen through its real 144p Quick TV block and proved the persisted 16x9 geometry, 256x144 rendition, and owner. Every two-client pair visibly rendered the identifiable synthetic H.264/AAC program, produced synchronized audible output, exercised the full HLS path, and stopped cleanly. Modern clients measure the OpenAL source cursor and physical output latency during a silent preroll, map the rounded media position back to its server-owned wall-clock epoch, then compensate queued audio so the first program sample reaches each sink at that shared boundary. Startup keeps sampling the server clock until it has both a bounded sample count and a low-RTT estimate, with a bounded fallback for genuinely high-latency links. Representative post-compensation captures remained within 0–70 ms. Forge 1.7.10 passed consecutive fresh captures after its LWJGL 2 adapter began confirming physical backend progress inside a silent preroll and feeding larger buffers; the runs measured 0.986105 correlation at -10 ms and 0.978383 at -30 ms. The matrix includes a follower-first run that proves an already-watching client receives a television constructed later. Exact results are recorded in [release acceptance](docs/RELEASE_ACCEPTANCE.md).

The same decoder facade also loaded its Windows-x64 JavaCV/FFmpeg natives and decoded both video and audio under a Windows Temurin 21 JVM smoke. That is native-decoder evidence, not a full Windows Minecraft-client certification. Live Plex behavior is independently covered by the shared-core credentialed smoke.

The checked-in proposal is mapped requirement-by-requirement in [proposal implementation status](docs/PROPOSAL_STATUS.md).

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
On 2026-08-27 this smoke passed against the operator-supplied LAN Plex server:
one test executed with no skip, failure, or error, the report retained no output,
and the post-run residue scan found no credential in the checkout or Gradle logs.

The opt-in real two-client gate creates a deterministic fake-Plex HLS program, launches two independent GUI clients and audio sinks, requires a common rendered frame plus visible screenshots, checks audible PCM synchronization, and verifies clean shutdown. It is recorded green for every listed runtime, including consecutive fresh Forge 1.7.10 runs:

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
