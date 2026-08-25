# Cinemarr

Cinemarr is a required client-and-server Minecraft mod for server-authoritative Plex movie and TV playback on player-built screens. The Minecraft server owns Plex credentials, library policy, transcodes, timelines, and media relay; tracking clients decode each compressed stream once and render it across the visible parts of a shared dynamic texture.

The complete product requirements and acceptance criteria live in [Proposal](Proposal). Cinemarr is a standalone sibling project and does not require Jammarr at runtime.

## Current implementation target

The initial runnable target is NeoForge 1.21.1 on Java 21. The repository also contains the clean Jammarr-derived loader/build scaffolding that will be converted as each later family reaches parity; those copied adapters are not considered released Cinemarr ports yet.

Implemented foundations include:

- Java-8-compatible, Minecraft-independent screen geometry for arbitrary four-connected planar silhouettes, holes, all six facings, exact visibility masks, and unloaded-chunk membership.
- The proposal defaults: 4–65,536 pixels, dimensions through 2,048, two active sessions, four screens per owner, all operator-configurable.
- Exact fit, fill, and stretch transforms plus bounded, even H.264 rendition selection.
- Server-only Plex video-library resolution, movie/show browsing, rating and permission filtering, HLS transcode start/fetch/stop, same-origin segment confinement, bounded responses, and token-safe client models.
- 16 KiB hash-addressed media payloads, keyframe seek policy, generation-safe asynchronous work, clock/drift policy inherited from the shared transport core, and authoritative watch-party/session lifecycle.
- NeoForge Screen Pixel, TV Controller, casing, and speaker blocks. Pixels remain ordinary blocks; placement/removal updates world saved data, while controller activation reconstructs and persists a screen without force-loading its chunks.

The in-world decoder/renderer, final control UI, subtitle/audio-track flow, positional sound, hardened persistence/reconnect behavior, remaining loaders, and real two-client visual acceptance are still implementation gates—not implied by a passing build.

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

Run the current NeoForge 1.21.1 unit and artifact gate under Java 21:

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
