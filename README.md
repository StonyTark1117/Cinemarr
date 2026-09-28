# Cinemarr

<img src="artwork/cinemarr-icon.png" alt="Cinemarr logo" width="160">

Cinemarr is a required client-and-server Minecraft mod for server-authoritative Plex movie and television playback on player-built screens. The server owns Plex credentials, library policy, transcodes, timelines, and media relay. Clients receive bounded HLS media, decode each visible TV stream once, and render it across that television's dynamic texture.

## 1.0.1 development status

This checkout targets **Cinemarr 1.0.1** across the **16-artifact / 21-runtime** matrix, retaining the existing decoder while moving native libraries to a user-installed dependency. The mod does not download them. Each playback client needs the matching libraries; dedicated servers do not.

Read the [decoder installation instructions](docs/INSTALLING_DECODER_LIBRARIES.md) and [1.0.1 release notes](docs/1.0.1_RELEASE_NOTES.md). Implementation and bounded validation are complete; see the [validation record](docs/validation/1.0.1-external-runtime-progress.md) for coverage and limits. No 1.0.1 release has been published. Historical 1.0 certification records below describe their original candidates and do not certify this update.

Protocol 11 separates watch-party and TV-stream identities. Saved-data migration
preserves existing screens; new custom TVs default to Fit, Detailed, Auto, and
Quick TV construction sizes and preset resolutions remain intact. Requested
quality and measured decoded dimensions are reported separately.

See the [release acceptance record](docs/RELEASE_ACCEPTANCE.md),
[artifact evidence and hashes](docs/RELEASE_CANDIDATE_EVIDENCE_20260914.json),
[full feature specification](docs/1.0_CUSTOM_TV_DISPLAY_PLAN.md) and
[ten-phase hardening plan](docs/1.0_RELEASE_HARDENING_PLAN.md).

The old global Plex-music queue, stations, MP3 transport, music UI, and their bundled JLayer/Jump3r libraries have been removed. Cinemarr 1.0 is a television/video mod; it does not require Jammarr.

Implemented release foundations include:

- Forge 1.7.10 and Fabric, Quilt-compatible Fabric, Forge, and NeoForge adapters for Minecraft 1.20.1, 1.20.2, 1.21.1, 26.1.2, and 26.2.
- Server-only Plex credentials and allowlisted movie/show libraries.
- Persistent player-built screens, controllers, remotes, redstone receivers, fit/fill/stretch presentation, named watch parties, queueing, track/subtitle selection, and synchronized positional audio.
- Hash-checked 16 KiB media chunks, bounded transfer windows, rate limits, HLS origin confinement, rendition caps, segment retries, and token-redacted failures.
- Transactional Quick TV construction in batches of at most 256 blocks per tick, with obstruction/unload rollback, persisted crash-recovery footprints, and generated-pixel teardown.
- Eight Quick TV recipes from 144p through 8K. `bounded` mode is the safe default. `literal` mode permits only a footprint that fits the 65,536-pixel ceiling; larger literal presets are refused before placement.
- Software video decoding by default. The only public decoder modes are `software`, `auto`, and `vaapi`; hardware failure falls back to software.

Supported external decoder platforms are Linux x86-64, Linux ARM64, and Windows x86-64. Linux clients also need host graphics/audio libraries; see the [installation requirements](docs/INSTALLING_DECODER_LIBRARIES.md). macOS is not supported.

## Display Settings

Open a TV controller and select **Display**. The TV owner or an operator can
change its picture; other viewers can inspect the settings. Apply waits for
server confirmation. Cancel leaves the TV unchanged.

The screen's block dimensions, pixel mapping and requested stream quality are
separate settings:

- **Fit** preserves the whole picture with bars, **Fill** crops to cover the
  screen, and **Stretch** uses the whole screen without preserving aspect ratio.
- **Detailed** displays the decoded picture. **One pixel per block** samples one
  color for each block in the screen's bounding rectangle; holes remain holes.
  A 4×4 screen therefore has a 4×4 displayed raster in pixel mode, regardless of
  the requested stream quality.
- **Auto**, presets from **144p to 8K**, and **Custom** specify a stream bounding
  box. Custom dimensions must be whole numbers from 2 to 8192 and fit the
  decoded-frame memory budget. Source aspect ratio, source quality and server
  limits can reduce the actual result. **Actual** shows the selected HLS
  rendition dimensions when available; **pending** means they are not known.

Large sparse screens count their full bounding rectangle toward picture memory,
so their effective stream quality may be reduced. If a received picture still
exceeds that limit, the client reports it and skips that TV's picture; lower its
quality or switch to Detailed mapping to recover.

Quick TVs keep their construction preset and Detailed mapping. Their layout
remains adjustable. Changing a custom TV's quality replaces that TV's stream;
layout and mapping changes redraw it without restarting playback. Paused or
idle quality changes take effect when its stream next starts.

TVs in one watch party share playback position and controls, but each active TV
uses a separate stream slot. Additional viewers of the same TV share that slot.
When the configured stream limit is full, another TV waits for capacity. Its
playback joins the current party position when capacity becomes available.

These integrated controls are still undergoing the complete 1.0 acceptance
matrix described above; the development build is not yet certified.

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

### Choosing which Plex libraries players can browse

Edit `cinemarr-libraries.toml` in the active Minecraft world's `serverconfig` folder. This is the **server's Plex library allowlist**: it controls which Plex movie and TV libraries appear in the TV Controller. Each world has its own list, and nothing is exposed by default.

For a dedicated server with the default world name, the file is `world/serverconfig/cinemarr-libraries.toml`. If you use another world name, substitute that world's folder. In singleplayer, use `saves/<your-world>/serverconfig/cinemarr-libraries.toml` inside your Minecraft instance. The client decoder-library folder and `cinemarr-client.toml` are separate settings; they do not select Plex content.

1. Start the server or open the singleplayer world once to generate the files, then stop the server or exit the world before editing.
2. In the same world's `cinemarr-server.toml`, set `plexUrl` to your Plex server's base address, including its port when needed. Supply its token through `CINEMARR_PLEX_TOKEN` in the Minecraft server process's environment, or through `plexToken` in that server file. The token must have access to the Plex libraries you choose. Keep credentials out of `cinemarr-libraries.toml`.
3. In Plex, find the name of the movie or TV library you want to expose, such as `Movies` or `TV Shows`. Use that name for `section`. It is a Plex library, not a movie title, playlist, collection, or media folder path.
4. Add an entry like the ones below to `cinemarr-libraries.toml`. The generated example is commented out: remove the leading `#` from **every line of the entry**, including `[[libraries]]`, to enable it. Leave explanatory comments commented. Replace the example names with your own.
5. Save the file and restart the Minecraft server or reopen the singleplayer world. Open a TV Controller and browse its libraries. Changes are read when the world starts; `/cinemarr retry` retries the existing connection settings and does **not** reload this file.

### Library entries and their fields

This example exposes a Plex library named `Movies` as **Family Movies** to all players, filtering its movies to Cinemarr's `PG-13` rating ceiling. A second entry exposes a Plex library named `TV Shows` as **Television**, with a `TV-14` ceiling. Keep only the entries for libraries that actually exist on your Plex server.

```toml
[[libraries]]
id = "family_movies"
section = "Movies"
displayName = "Family Movies"
allowMovies = true
allowShows = false
maximumContentRating = "PG-13"
permissionLevel = 0

[[libraries]]
id = "television"
section = "TV Shows"
displayName = "Television"
allowMovies = false
allowShows = true
maximumContentRating = "TV-14"
permissionLevel = 0
```

Repeat the full `[[libraries]]` block for each additional library. Use double quotes around text, unquoted `true`/`false` for switches, and an unquoted integer for `permissionLevel`.

| Setting | What to enter |
| --- | --- |
| `id` | A nonempty identifier you choose for this Cinemarr entry, such as `family_movies`. Use a different ID for each entry and keep it stable: saved playback refers to it. This is not a Plex ID or token. Required. |
| `section` | The existing Plex library's name, matched without regard to letter case, or its numeric Plex section key written as text, such as `"3"`. If two Plex libraries have the same name, use their distinct section keys. A section key identifies a whole library, not an individual media item's rating key. Required. |
| `displayName` | The label players see in Cinemarr. It can differ from the Plex name. If omitted, it defaults to `section`; an explicitly empty name is invalid. |
| `allowMovies` | Whether this entry allows movie items. Use `true` for a movie library and `false` for a TV library. Defaults to `true` if omitted. |
| `allowShows` | Whether this entry allows shows, seasons, and episodes. Use `true` for a TV library and `false` for a movie library. Defaults to `true` if omitted. At least one of the two switches must be `true`. |
| `maximumContentRating` | The highest rating allowed by Cinemarr's comparison below. Use `""` to disable rating filtering; that is also the default if omitted. |
| `permissionLevel` | Minimum Minecraft command permission level for browsing this library and selecting its media. `0` allows ordinary players; `2` requires level 2 or higher. Accepted values are `0` through `4`, with default `0`. This is a Minecraft permission level, not a Plex account role or a permission-plugin node. |

The movie/show switches filter the chosen Plex library; they do not combine separate libraries. Music and photo libraries are unsupported. Library selection permissions also do not make a screen private: other players can watch a TV already playing that content.

### How rating limits work

Cinemarr compares Plex's content-rating metadata using the following groups, from lowest to highest. A configured ceiling includes its own group and all groups above it in this table.

| Order | Ratings treated as equivalent |
| --- | --- |
| Lowest | Missing/empty rating, `NR`, `UNRATED`, `NOT RATED`, `TV-Y` |
| 1 | `G`, `TV-G`, `TV-Y7`, `TV-Y7-FV` |
| 2 | `PG`, `TV-PG` |
| 3 | `PG-13`, `TV-14` |
| 4 | `R`, `TV-MA` |
| Highest | `NC-17`, `X` |

For example, `maximumContentRating = "PG-13"` excludes `R` and `NC-17` movies, but **still allows unrated or missing-rating items**. It is not a way to exclude unrated content. When a ceiling is set, unrecognized item ratings are excluded, and an unrecognized ceiling excludes all items. Ratings are case-insensitive; a prefix ending in `/` is removed before comparison (for example, `us/PG-13` becomes `PG-13`). Other regional rating systems are not automatically translated. A blank ceiling allows all ratings.

### Checking the configuration

Use `/cinemarr diagnostics` to check the redacted Plex connection state: `disabled`, `connecting`, `ready`, or `degraded`. Degraded connections retry automatically; operators can also use `/cinemarr retry` after a temporary Plex outage.

- **No libraries appear:** check that your blocks are uncommented, you edited the active world's file, and you restarted the server or reopened the world. Also check the Plex URL/token and the player's `permissionLevel`.
- **No matching Plex library:** check `section` against the libraries visible to the configured token. Every configured entry must resolve to an existing movie or TV library; one invalid entry can prevent the connection becoming ready.
- **Library appears but has no expected titles:** check `allowMovies`/`allowShows` and Plex's rating metadata against `maximumContentRating`. If testing with `maximumContentRating = ""`, remember that this allows all ratings.
- **Server reports a configuration error:** check spelling, required `id`/`section` values, permission range, and that both content switches are not `false`. Unknown setting names are rejected. Do not remove the `[[libraries]]` line above each entry.

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

Earlier green automation missed real audio, concurrency, camera and controller defects. Subsequent live pressure, reload and lifecycle checks prompted further fixes. Their source-bound acceptance and earlier complete builds are regression evidence; the integrated remediation still requires final exact-byte certification.

The full local entry point is `./gradlew releaseMatrixGate --no-configuration-cache --max-workers=1` under Java 21, from a clean candidate checkout with fresh evidence directories. It requires all 16 artifacts, ten GameTests, all 21 runtimes and the required supplements. Direct image review, reproducibility, exact-artifact real-Plex/recovery/lifecycle and Windows/ARM checks, security/documentation review, scoped commits/push and exact-SHA green hosted CI with matching artifact hashes remain separate completion requirements. Installed Windows/ARM test guests are retained powered off between tests; see the [guest runbook](docs/NATIVE_TEST_GUESTS.md). See [the 1.0 hardening plan](docs/1.0_RELEASE_HARDENING_PLAN.md) for current evidence. The [current Jammarr comparison](docs/JAMMARR_COMPARISON_20260914.md), brought forward at the user's request, assesses features, versions, fixes and future improvements while leaving the 1.0 matrix and paused release gates unchanged.

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

### Display settings and per-TV quality

Open a TV controller and choose **Display**. Layout cycles through Fit, Fill and
Stretch. Mapping cycles between Detailed and One pixel per block. Quality cycles
through Auto, 144p, 240p, 480p, 720p, 1080p, 1440p, 4k, 8k and Custom. Width and
height are editable only in Custom (2–8192 per axis, within the decoded-frame
memory budget). Apply waits for the server to acknowledge the saved settings;
Cancel discards unsent changes. Reload fetches the latest received settings after
a concurrent edit or timeout. Only the owner or an operator can edit a TV.
Quick TVs keep their preset quality and Detailed mapping; their layout is editable.

**Screen size** is the block bounding rectangle. **Requested quality** is a stream
bounding box, limited by source resolution, source aspect ratio, server caps and
codec rounding. **Actual decoded** is measured from the most recently presented
source frame on this client; it stays unknown before a frame arrives. During a
quality replacement it can continue to show the old frame's dimensions until the
new stream presents a frame. It does not promise that Plex honored the request.
In One pixel per block mode, the output raster instead matches the screen's block
dimensions; each visible block displays one sampled color. Fit adds black bars,
Fill crops, and Stretch fills the rectangle. Screen holes stay absent.

Each TV consumes its own stream slot, including TVs in the same watch party.
Multiple viewers of one TV share that TV's server stream. A full server shows
**Waiting for stream capacity** on additional TVs; the next eligible TV starts
automatically at the party's current position when a slot is freed. Quality
changes replace only the edited TV's stream. Pause, resume, seek and track
selection remain shared by the watch party. Paused layout and mapping edits
redraw the retained frame.

See the release acceptance checklist for the artifact evidence and final-commit
certification requirements of each maintained platform.
