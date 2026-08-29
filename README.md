# Cinemarr

<img src="artwork/cinemarr-icon.png" alt="Cinemarr logo" width="160">

Cinemarr is a required client-and-server Minecraft mod for server-authoritative Plex movie and TV playback on player-built screens. The Minecraft server owns Plex credentials, library policy, transcodes, timelines, and media relay; tracking clients decode each compressed stream once and render it across the visible parts of a shared dynamic texture.

The complete product requirements and acceptance criteria live in [Proposal](Proposal). Cinemarr is a standalone sibling project and does not require Jammarr at runtime.

## Current implementation

Cinemarr has video-runtime adapters for Forge 1.7.10 and Fabric, Quilt-compatible Fabric, Forge, and NeoForge targets spanning Minecraft 1.20.1, 1.20.2, 1.21.1, 26.1.2, and 26.2. **The local 1.0.1 candidate is release-ready but unpublished.** The original v1.0.0 checks did not exercise the same real-Plex metadata and playback path used by the controller UI; issue #1 demonstrated that every real playback request could fail before playback began. The replacement candidate fixes that parser defect and has now passed the production metadata/HLS path, real controller selection, native decoding, two-client identifiable-frame and physical-audio synchronization checks, clean teardown, the complete 21-profile build/test matrix, and inspection of all 16 release JARs.

Implemented foundations include:

- Java-8-compatible screen geometry for solid planar rectangles by default, including odd sizes and straight lines; connected masks/holes are an operator opt-in.
- The proposal defaults: 4–65,536 pixels, dimensions through 2,048, four concurrent Plex streams, and eight globally counted TVs per owner.
- Exact fit, fill, and stretch transforms plus bounded, even H.264 rendition selection.
- Server-only Plex video-library resolution, movie/show browsing, rating and permission filtering, HLS transcode start/fetch/stop, same-origin segment confinement, bounded responses, and token-safe client models.
- 16 KiB hash-addressed media payloads, keyframe seek policy, generation-safe asynchronous work, clock/drift policy inherited from the shared transport core, and authoritative watch-party/session lifecycle.
- Screen Pixel, TV Controller, casing, speaker, and TV Redstone Receiver blocks plus a craftable TV Remote on every target, with survival recipes for the full construction set. Pixels remain ordinary blocks; placement/removal updates world saved data, while controller activation reconstructs and persists a screen without force-loading its chunks. Using the Remote on a controller follows that same authoritative activation/UI path. A powered receiver directly adjacent to a controller toggles its active named session on each low-to-high redstone edge; steady power does not repeatedly toggle playback.
- Eight progressively crafted Quick TV Kit blocks: 144p, 240p, 480p, 720p, 1080p, 1440p, 4K, and 8K. Placing one preflights a loaded, unobstructed 16:9 footprint, builds a bounded dense screen, activates it, and persists the exact named rendition target. A literal 8K one-block-per-pixel wall would contain 33,177,600 blocks, so quick kits are deliberately bounded prefabs; hand-built Screen Pixels retain exact one-block/one-logical-pixel behavior.
- Global `quickTvKitsEnabled` and per-resolution `quickTv...Enabled` server settings. A disabled kit stays registered for world/save compatibility but cannot construct or activate its prefab.
- Breaking a controller, Quick TV, or registered pixel immediately unregisters that TV. The final TV in a watch party checkpoints paused state and closes its Plex stream; `/cinemarr tv list|locate|unregister` recovers lost registrations without removing blocks.

The in-world decoder/renderer, control UI, subtitle/audio-track selection, positional sound, persistence/reconnect behavior, server-owned HLS relay, and clean protocol rejection paths are implemented. On 2026-08-27, every one of the 21 listed runtime profiles built the gate screen through its real 144p Quick TV block and proved the persisted 16x9 geometry, 256x144 rendition, and owner. Those clients visibly rendered and synchronized a synthetic H.264/AAC program served by the deterministic fake-Plex fixture. On 2026-08-28, the 1.21.1 NeoForge gate additionally used the in-game controller against the operator's real Plex server: both clients rendered identifiable program video, the physical audio captures correlated at 0.704172 with 0 ms measured lag, decoder drift was 26 ms and 30 ms, and teardown left no process, port, Plex transcode, session, or credential residue. Exact evidence and the certification boundary are recorded in [release acceptance](docs/RELEASE_ACCEPTANCE.md).

The same decoder facade also completed the through-4K standalone benchmark
under a native Windows 11 Temurin 21 JVM with the packaged Windows-x64
JavaCV/FFmpeg classifier. The release-default software decoder and real-Plex
controller path are certified independently of the optional hardware-decoder
experiment.

Client hardware decoding is experimental and opt-in. `videoDecoderBackend` in
`config/cinemarr-client.toml` accepts only `software` (the default), `auto`, or
`vaapi`; `videoDecoderDevice` may select an explicit VAAPI render node such as
`/dev/dri/renderD128`. The in-game selector cycles through the same three modes.
Cinemarr probes away from the render thread and permanently falls back to
software for that playback if probing or decoding fails. `auto` may use an
internal platform backend, but those backends are deliberately not exposed as
explicit configuration values.

Hardware frames still have to be downloaded and converted to CPU-side RGBA for
Minecraft's dynamic texture, so hardware decoding may be slower. In the
2026-08-28 through-4K validation, AMD VAAPI reduced decoder-thread CPU by about
24% at 1080p but only 1% at 4K and more than doubled 144p wall time. Intel VAAPI
reduced CPU by about 25% at 1080p and 37% at 4K, but regressed 144p wall time by
about 14%. NVIDIA P600 acceleration increased CPU on Linux and Windows, so it is
available only through `auto`, with the same software fallback. No tested host
met every default-enablement threshold; `software` therefore remains the
default. Run `./gradlew benchmarkVideoDecoder` with the
`cinemarrDecoderBackend`, `cinemarrDecoderDevice`, and
`cinemarrDecoderOutput` properties to capture comparable JSON and CSV results.
See
[compatibility](docs/COMPATIBILITY.md#experimental-client-hardware-decoding) for
the full results and untested boundary.

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

The deterministic two-client gate creates a fake-Plex HLS program, launches two independent GUI clients and audio sinks, requires a common rendered frame plus visible screenshots, checks audible PCM synchronization, and verifies clean shutdown. It is recorded green for every listed runtime, including consecutive fresh Forge 1.7.10 runs:

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

For final release acceptance, the same controller/UI gate can target one credentialed live Plex library. The token stays process-environment only; the numeric section ID avoids writing the private library name into evidence:

```bash
CINEMARR_VIDEO_CLIENT_GATE=true \
CINEMARR_LIVE_PLEX_GATE=true \
CINEMARR_PLEX_URL='http://plex.example.invalid:32400' \
CINEMARR_PLEX_TOKEN='...' \
CINEMARR_LIVE_VIDEO_SECTION_ID='1' \
./scripts/run-dedicated-server-gate.sh 1.21.1-neoforge
```
