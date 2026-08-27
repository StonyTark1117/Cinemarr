# Compatibility

Cinemarr's 16 artifacts have completed the recorded release gates. Deterministic
real-client acceptance is complete for all 21 runtime profiles, including the
isolated Forge 1.7.10 implementation, and the shared server core passed its
credentialed live Plex H.264/AAC start, fetch, and clean-stop smoke.
The shared transport core is Java-8-compatible; loader networking, registries,
render events, native decoder packaging, and Quick TV Kit registration are
platform-specific.

| Minecraft | Loader | Status |
| --- | --- | --- |
| 1.21.1 | NeoForge | release-gate complete: build, dedicated launch, protocol client, shared live Plex, and real two-client 144p Quick TV deterministic-HLS A/V (0.997385 correlation, 100 ms measured lag) |
| 1.21.1 | Fabric | release-gate complete: build, dedicated launch, protocol client, shared live Plex, and real two-client 144p Quick TV deterministic-HLS A/V (0.990926 correlation, 90 ms measured lag) |
| 1.21.1 | Quilt | release-gate complete for the Fabric-port runtime: build, dedicated launch, protocol client, shared live Plex, and real two-client A/V (0.991250 correlation, 50 ms measured lag) |
| 1.21.1 | Forge | release-gate complete: build, dedicated launch, protocol client, shared live Plex, and real two-client 144p Quick TV deterministic-HLS A/V (0.980633 correlation, 40 ms measured lag) |
| 1.20.1 | Fabric / Quilt | release-gate complete: build, dedicated launch, protocol client, shared live Plex, and real two-client A/V (Fabric: 0.994679 correlation at -50 ms; Quilt: 0.993132 at 50 ms) |
| 1.20.1 | Forge / NeoForge | release-gate complete: build, dedicated launch, protocol client, shared live Plex, and real two-client A/V (Forge: 0.989935 correlation at -40 ms; NeoForge: 0.991707 at 20 ms) |
| 1.20.2 | Fabric / Quilt | release-gate complete: build, dedicated launch, protocol client, shared live Plex, and real two-client A/V (Fabric: 0.993858 correlation at 70 ms; Quilt: 0.993567 at -10 ms) |
| 1.20.2 | Forge / NeoForge | release-gate complete: build, dedicated launch, protocol client, shared live Plex, and real two-client A/V (Forge: 0.994218 correlation at -20 ms; NeoForge: 0.995144 at -10 ms) |
| 26.1.2 | Fabric / Quilt | release-gate complete: build, dedicated launch, protocol client, shared live Plex, and real two-client A/V (Fabric: 0.990218 correlation at 10 ms; Quilt: 0.997147 at -10 ms) |
| 26.1.2 | Forge / NeoForge | release-gate complete: build, dedicated launch, protocol client, shared live Plex, and real two-client A/V (Forge: 0.987922 correlation at 70 ms; NeoForge: 0.988920 at -40 ms) |
| 26.2 | Fabric / Quilt | release-gate complete: build, dedicated launch, protocol client, shared live Plex, and real two-client A/V (Fabric: 0.999427 correlation at 20 ms; Quilt: 0.996959 at -20 ms) |
| 26.2 | Forge / NeoForge | release-gate complete: build, dedicated launch, protocol client, shared live Plex, and real two-client A/V (Forge: 0.996621 correlation at 80 ms; NeoForge: 0.990375 at 40 ms) |
| 1.7.10 | Forge | release-gate complete for the isolated runtime: build, dedicated launch, protocol client, shared live Plex, and consecutive fresh real two-client A/V runs (0.986105 correlation at -10 ms; 0.978383 at -30 ms) |

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
passed at 0.986959 / 10 ms and 0.990159 / 70 ms, respectively.

The repository decoder also passed a Windows-x64 native smoke under a Windows
Temurin 21 JVM: JavaCV/FFmpeg loaded and decoded three video frames and 30 audio
frames. This proves the packaged Windows decoder path, not a full Windows
Minecraft-client launch.

Every listed target has passed compile, launch, runtime, clean-shutdown, and
two-client A/V acceptance. The environment-only live Plex smoke validates the
shared `PlexVideoService` packaged through every server adapter.
No cross-loader or cross-version compatibility is implied by the protocol
number or by a shared-core test. Forge 1.7.10 uses its own Java 8 bytecode,
LWJGL 2 renderer/audio adapter, and embedded native-decoder implementation.
