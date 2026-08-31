# Compatibility

Cinemarr 1.0.0 uses protocol 9 and requires the mod on both the server and every client. Cross-version or cross-loader networking is not supported.

| Minecraft | Loaders / runtime profiles | Java |
| --- | --- | ---: |
| 1.7.10 | Forge | 8 |
| 1.20.1 | Fabric, Quilt via Fabric artifact, Forge, NeoForge | 17 |
| 1.20.2 | Fabric, Quilt via Fabric artifact, Forge, NeoForge | 17 |
| 1.21.1 | Fabric, Quilt via Fabric artifact, Forge, NeoForge | 21 |
| 26.1.2 | Fabric, Quilt via Fabric artifact, Forge, NeoForge | 25 |
| 26.2 | Fabric, Quilt via Fabric artifact, Forge, NeoForge | 25 |

This is a 16-artifact / 21-runtime release matrix. Every row must independently pass compilation, unit tests, packaging inspection, dedicated-server startup, Quick TV construction, two-client video/audio playback, disconnect/reconnect, and cleanup before 1.0 can be called release-ready.

Packaged native decoding supports Linux x86-64, Linux ARM64, and Windows x86-64. macOS is unsupported for 1.0. Linux clients must provide the standard graphics/media ABI libraries `libudev.so.1`, `libdrm.so.2`, `libva.so.2`, and `libva-drm.so.2`; these are normally present on a graphical Minecraft installation, but minimal/server distributions may need their distribution's libudev, libdrm, and libva runtime packages.

The exact packaged `linux-arm64` classifier passed software decoding at 144p, 480p, and 1080p under an aarch64 Debian 13 kernel and aarch64 Java 21 runtime. The exact `windows-x86_64` classifier passed the same rows under native Windows 11 AMD64 and its FFmpeg DLL reported PE machine `0x8664`. Evidence is retained under `build/native-smoke/linux-arm64/20260830T125759Z/` and `build/native-smoke/windows-x86_64/20260830T174136Z/`.

## Decoder modes

The client setting exposes only:

- `software` — supported default and release baseline.
- `auto` — experimental host probing with permanent per-playback software fallback.
- `vaapi` — experimental Linux VAAPI with optional `videoDecoderDevice`.

All decoded frames currently return to CPU-side RGBA for Minecraft's dynamic texture, so hardware decoding is not assumed to be faster. Hardware results never substitute for the software release gate.

## Evidence boundary

Historical fake-Plex and hardware benchmarks remain useful regression history, but they do not certify the current protocol-9, video-only 1.0.0 checkout. Current certification must be regenerated from the exact artifacts being proposed for release. See [release acceptance](RELEASE_ACCEPTANCE.md).
