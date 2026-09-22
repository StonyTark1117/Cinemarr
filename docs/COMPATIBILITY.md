# Compatibility

Cinemarr 1.0.0 requires the mod on both the server and every client. The integrated development tree uses protocol 11 for display settings and separate watch-party/TV stream identities. Protocol 10 clients are incompatible. The recorded candidate has adapter runtime evidence; final release readiness also requires the exact-commit acceptance gate. Cross-version or cross-loader networking is not supported.

| Minecraft | Loaders / runtime profiles | Java |
| --- | --- | ---: |
| 1.7.10 | Forge | 8 |
| 1.20.1 | Fabric, Quilt via Fabric artifact, Forge, NeoForge | 17 |
| 1.20.2 | Fabric, Quilt via Fabric artifact, Forge, NeoForge | 17 |
| 1.21.1 | Fabric, Quilt via Fabric artifact, Forge, NeoForge | 21 |
| 26.1.2 | Fabric, Quilt via Fabric artifact, Forge, NeoForge | 25 |
| 26.2 | Fabric, Quilt via Fabric artifact, Forge, NeoForge | 25 |

This is a 16-artifact / 21-runtime release matrix. Every row must independently pass compilation, unit tests, packaging inspection, dedicated-server startup, Quick TV construction, two-client video/audio playback, disconnect/reconnect, and cleanup before 1.0 can be called release-ready.

Evidence levels are distinct: **builds** means the target compiles and its packaged artifact passes inspection; **launches** also requires a dedicated-server/client launch and clean shutdown; **runtime-certified** additionally requires the complete playback, protocol, control, reconnect and lifecycle evidence for its exact candidate, including the representative real-Plex checks. A build or launch alone is not a supported-runtime claim. The manifest records `runtime-certified` for the exact artifact hashes in [the candidate evidence manifest](RELEASE_CANDIDATE_EVIDENCE_20260914.json). This is artifact evidence, while release readiness requires the containing commit to satisfy phase 10.

Packaged native decoding supports Linux x86-64, Linux ARM64, and Windows x86-64. macOS is unsupported for 1.0. Linux clients must provide the standard graphics/media ABI libraries `libudev.so.1`, `libdrm.so.2`, `libva.so.2`, and `libva-drm.so.2`; these are normally present on a graphical Minecraft installation, but minimal/server distributions may need their distribution's libudev, libdrm, and libva runtime packages.

The integrated candidate's Windows run `20260913T113700Z` and emulated Linux
ARM64 run `20260913T115705Z` passed software decoding of 144p, 480p and 1080p
fixtures. Windows FFmpeg reported PE machine `0x8664`; ARM used an aarch64
kernel and Java runtime. Both retained guests are powered off and original ARM
access was restored. Native payload parity is recorded in the evidence manifest;
the changed legacy JAR retains all 86 native members unchanged. These checks
establish decoder ABI/functionality, not complete Windows/ARM Minecraft playback
or physical-ARM performance. The complete Minecraft runtime and real-Plex
evidence uses the separately recorded Linux x86-64 architectural boundaries.

All original platform, loader and native requirements remain in scope. Earlier
V11 results and failed attempts are historical. Current evidence includes the
expanded feature scenarios and deeper capacity/failure/raster checks, with final
containing-commit local, hosted and downloaded-artifact parity required by
[release acceptance](RELEASE_ACCEPTANCE.md).

## Decoder modes

The client setting exposes only:

- `software` — supported default and release baseline.
- `auto` — experimental host probing with permanent per-playback software fallback.
- `vaapi` — experimental Linux VAAPI with optional `videoDecoderDevice`.

All decoded frames currently return to CPU-side RGBA for Minecraft's dynamic texture, so hardware decoding is not assumed to be faster. Hardware results never substitute for the software release gate.

## Evidence boundary

Historical fake-Plex and hardware benchmarks remain useful regression history, but they do not certify the integrated display/stream 1.0.0 candidate. Use the recorded exact hashes for artifact evidence and the containing commit’s local/hosted gates for final release readiness. See [release acceptance](RELEASE_ACCEPTANCE.md).
