# Compatibility

Cinemarr's 16 local 1.0.1 artifacts are release-ready but unpublished. A fresh
post-fix matrix passed all 21 loader/runtime profiles, all 10 GameTests, and
byte-level inspection of every canonical JAR. The shared production path is
additionally certified by a credentialed two-client controller/UI run on
1.21.1 NeoForge against real Plex. The per-target values below remain the
deterministic fake-Plex runtime evidence; they do not imply that credentials
were repeated independently on every loader.
The shared transport core is Java-8-compatible; loader networking, registries,
render events, native decoder packaging, and Quick TV Kit registration are
platform-specific.

| Minecraft | Loader | Status |
| --- | --- | --- |
| 1.21.1 | NeoForge | 1.0.1 verified; real-Plex controller A/V: 0.704172 correlation, 0 ms lag; exact-candidate deterministic A/V: 0.990893, -10 ms |
| 1.21.1 | Fabric | 1.0.1 verified; deterministic A/V: 0.990926 correlation, 90 ms lag |
| 1.21.1 | Quilt | 1.0.1 verified; deterministic A/V: 0.991250 correlation, 50 ms lag |
| 1.21.1 | Forge | 1.0.1 verified; deterministic A/V: 0.980633 correlation, 40 ms lag |
| 1.20.1 | Fabric / Quilt | 1.0.1 verified; deterministic A/V (Fabric: 0.994679 at -50 ms; Quilt: 0.993132 at 50 ms) |
| 1.20.1 | Forge / NeoForge | 1.0.1 verified; deterministic A/V (Forge: 0.989935 at -40 ms; NeoForge: 0.991707 at 20 ms) |
| 1.20.2 | Fabric / Quilt | 1.0.1 verified; deterministic A/V (Fabric: 0.993858 at 70 ms; Quilt: 0.993567 at -10 ms) |
| 1.20.2 | Forge / NeoForge | 1.0.1 verified; deterministic A/V (Forge: 0.994218 at -20 ms; NeoForge: 0.995144 at -10 ms) |
| 26.1.2 | Fabric / Quilt | 1.0.1 verified; deterministic A/V (Fabric: 0.990218 at 10 ms; Quilt: 0.997147 at -10 ms) |
| 26.1.2 | Forge / NeoForge | 1.0.1 verified; deterministic A/V (Forge: 0.987922 at 70 ms; NeoForge: 0.988920 at -40 ms) |
| 26.2 | Fabric / Quilt | 1.0.1 verified; deterministic A/V (Fabric: 0.999427 at 20 ms; Quilt: 0.996959 at -20 ms) |
| 26.2 | Forge / NeoForge | 1.0.1 verified; deterministic A/V (Forge: 0.996621 at 80 ms; NeoForge: 0.990375 at 40 ms) |
| 1.7.10 | Forge | 1.0.1 verified; deterministic A/V runs: 0.986105 at -10 ms and 0.978383 at -30 ms |

Every listed target contains the eight configurable Quick TV Kit registrations,
the complete survival-craftable Screen Pixel/controller/casing/speaker set, the
TV Remote, and a TV Redstone Receiver whose rising edge toggles the adjacent
controller's active session.
On 2026-08-27, each of the 21 exact runtime profiles built its acceptance screen
through the real 144p Quick TV block, then proved persisted 16x9 geometry, a
256x144 rendition, and owner `CinemarrVideoA`. All 21 profiles completed the
full fake-Plex HLS route, produced two screenshots with an identifiable test
card, passed their two-sink audible synchronization threshold, and left no
client, server, fake-Plex process, audio module, or target port behind. The matrix
also includes a follower-first 26.1.2 NeoForge run proving that a client already
in tracking range receives a television constructed later.

Modern targets additionally share physical OpenAL output-latency compensation.
Fresh compensation regression captures across Fabric 1.21.1 and
NeoForge 1.21.1, 26.1.2, and 26.2 measured 10 ms, 40 ms, 20 ms, and 30 ms
inter-client lag, respectively, with correlation from 0.968840 to 0.995487.
The exact Fabric 26.2 executor-delay regression also passed at 0.993323
correlation / 30 ms lag.
The final boundary calculation is server-epoch-based rather than join-time-
based; exact Fabric 26.1.2 and serial late-join NeoForge 1.21.1 regressions
passed at 0.986959 / 10 ms and 0.955958 / 70 ms, respectively. The latter also
proved that startup waits for a low-RTT clock estimate when the initial eight
samples remain noisy, while retaining a bounded 16-sample fallback.
An exact Quilt 26.2 gate with Mod Menu 20.0.1 then exercised Cinemarr's
clientbound error response, verified its Fabric codec/receiver registration,
and passed the two-client capture at 0.980075 correlation / 0 ms lag with clean
teardown.
The corresponding exact Forge 26.2 response-path regression passed at 0.991658
correlation / 0 ms lag with matching frame evidence and clean teardown.

The repository decoder also passed the through-4K standalone benchmark under a
native Windows 11 Temurin 21 JVM with the packaged Windows-x64 classifier. Each
run used two warmups and five measured samples per rendition.

## Experimental client hardware decoding

Hardware video decoding is implemented as an opt-in client setting. User
configuration and the in-game selector expose only `software`, `auto`, and
`vaapi`, with an optional VAAPI render-node string. Explicit QSV, CUDA,
D3D11VA, and DXVA2 configuration values are not accepted; `auto` may select an
internal host-appropriate backend for capability probing and diagnostics.
Startup probing runs away from the render thread. A failed probe or decode
permanently selects software for that playback. Audio remains software-decoded.
Video frames are always downloaded and converted to CPU-side RGBA because
Minecraft's dynamic texture requires system memory.

The 2026-08-28 H.264 MPEG-TS benchmark covered the packaged Linux-x64 and
Windows-x64 FFmpeg classifiers:

* AMD Radeon RX 7900 XTX on Linux: VAAPI produced matching output from 144p
  through 4K. It reduced decoder-thread CPU by about 24% at 1080p but only 1%
  at 4K; 144p and 240p wall time regressed by about 105% and 62%.
  Hardware/software timestamp comparisons reported zero microseconds of
  video-PTS difference.
* Intel UHD 630 on Linux: VAAPI decoded 144p through 4K with SSIM at least
  0.999778. QSV device creation failed and transparently retried in software;
  `auto` continued to VAAPI. VAAPI reduced CPU by about 25% at 1080p and 37% at
  4K, but 144p wall time regressed by about 14%.
* NVIDIA Quadro P600 on Linux: the internal CUDA candidate decoded 144p through
  4K with SSIM at least 0.999774, but CPU RGBA conversion dominated the result.
  Decoder-thread CPU increased about 4% at 1080p and 40% at 4K, while wall time
  increased about 8% and 46%. CUDA is therefore not an explicit setting.
* NVIDIA Quadro P600 passed through to a native Windows 11 VM with driver
  576.02: the internal D3D11VA, DXVA2, and CUDA candidates and `auto` each
  hardware-decoded all 35 measured samples from 144p through 4K with SSIM at
  least 0.999754, zero fallback, and matching timestamps. D3D11VA increased
  decoder-thread CPU by about 41% at 1080p and 32% at 4K, and low-resolution
  wall time regressed by 42% to 170%. These backends are not explicit settings.

Both `software` and `auto` also passed the deterministic two-client
identifiable-frame, screenshot, PCM synchronization, and teardown gate across
all 21 maintained Linux runtime profiles. On the RX 7900 XTX host, `auto`
selected VAAPI without fallback in the final runs. The packaged Linux ARM64
classifier passed real ARM64 Java/JavaCPP software decoding under QEMU and the
unsupported-hardware fallback check; no ARM GPU was available to test.

A native Windows 11 Forge 1.7.10 client using the passed-through P600 selected
D3D11VA in `auto` mode, rendered the expected frame, saved a non-empty in-game
screenshot, established AAC audio, and recorded zero fallback, recovery, video
drop, or audio-underrun events. The corrected wrapper was not rerun to a formal
green result. Native Windows Minecraft-client behavior remains untested for
NeoForge 1.21.1 and Fabric 26.2. Hardware decoding is also untested on Linux
ARM64 GPUs, macOS, AMD Windows drivers, Intel Windows drivers, and GPUs or driver
versions other than those listed above.

No backend met the threshold for default enablement across machines and
renditions, so `software` remains the default. Reconsidering that default
requires a backend/driver combination to improve CPU use and frame time without
introducing dropped frames or measurable A/V drift in a real Minecraft-client
run.

Every listed target passed the fresh post-fix compile, test, packaging, and
artifact-inspection matrix. The shared controller/UI path also passed the
credentialed real-Plex two-client A/V gate on 1.21.1 NeoForge.
No cross-loader or cross-version compatibility is implied by the protocol
number or by a shared-core test. Forge 1.7.10 uses its own Java 8 bytecode,
LWJGL 2 renderer/audio adapter, and embedded native-decoder implementation.
