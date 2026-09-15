# Cinemarr

<img src="artwork/cinemarr-icon.png" alt="Cinemarr logo" width="160">

Cinemarr is a required client-and-server Minecraft mod for server-authoritative Plex movie and television playback on player-built screens. The server owns Plex credentials, library policy, transcodes, timelines, and media relay. Clients receive bounded HLS media, decode each visible TV stream once, and render it across that television's dynamic texture.

## 1.0 prerelease status

This checkout targets **Cinemarr 1.0.0**, with custom TV display controls and
independent streams across the **16-artifact / 21-runtime** matrix.
The current handshake correction is awaiting artifact certification, so the
target manifest records `prerelease`. Earlier artifact evidence remains bound
to its recorded hashes. The final gate at `1ea796f` failed a protocol-rejection
check; the correction passed repeated focused modern and legacy checks.
Release readiness requires the new exact commit's complete local gate, fully
green hosted aggregate, matching downloaded artifacts and clean teardown.
No tag or publication is part of this work.

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
