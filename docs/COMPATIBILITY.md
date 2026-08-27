# Compatibility

Cinemarr has no release-certified artifact yet because live Plex validation
remains incomplete. Deterministic real-client acceptance is complete for all 21
runtime profiles, including the isolated Forge 1.7.10 implementation.
The shared transport core is Java-8-compatible; loader networking, registries,
render events, native decoder packaging, and Quick TV Kit registration are
platform-specific.

| Minecraft | Loader | Status |
| --- | --- | --- |
| 1.21.1 | NeoForge | build-verified, dedicated-launch-tested, protocol-client-tested, and real two-client 144p Quick TV deterministic-HLS A/V-certified (0.994063 correlation, -20 ms measured lag); live Plex untested |
| 1.21.1 | Fabric | build-verified, dedicated-launch-tested, protocol-client-tested, and real two-client 144p Quick TV deterministic-HLS A/V-certified (0.990926 correlation, 90 ms measured lag); live Plex untested |
| 1.21.1 | Quilt | Fabric-port runtime build-verified, dedicated-launch-tested, protocol-client-tested, and real two-client 144p Quick TV deterministic-HLS A/V-certified (0.991250 correlation, 50 ms measured lag); live Plex untested |
| 1.21.1 | Forge | build-verified, dedicated-launch-tested, protocol-client-tested, and real two-client 144p Quick TV deterministic-HLS A/V-certified (0.980633 correlation, 40 ms measured lag); live Plex untested |
| 1.20.1 | Fabric / Quilt | video port build-verified, dedicated-launch-tested, protocol-client-tested, and real two-client 144p Quick TV deterministic-HLS A/V-certified (Fabric: 0.994679 correlation at -50 ms; Quilt: 0.993132 at 50 ms); live Plex untested |
| 1.20.1 | Forge / NeoForge | video port build-verified, dedicated-launch-tested, protocol-client-tested, and real two-client 144p Quick TV deterministic-HLS A/V-certified (Forge: 0.989935 correlation at -40 ms; NeoForge: 0.991707 at 20 ms); live Plex untested |
| 1.20.2 | Fabric / Quilt | video port build-verified, dedicated-launch-tested, protocol-client-tested, and real two-client 144p Quick TV deterministic-HLS A/V-certified (Fabric: 0.993858 correlation at 70 ms; Quilt: 0.993567 at -10 ms); live Plex untested |
| 1.20.2 | Forge / NeoForge | video port build-verified, dedicated-launch-tested, protocol-client-tested, and real two-client 144p Quick TV deterministic-HLS A/V-certified (Forge: 0.994218 correlation at -20 ms; NeoForge: 0.995144 at -10 ms); live Plex untested |
| 26.1.2 | Fabric / Quilt | video port build-verified, dedicated-launch-tested, protocol-client-tested, and real two-client 144p Quick TV deterministic-HLS A/V-certified (Fabric: 0.990218 correlation at 10 ms; Quilt: 0.997147 at -10 ms); live Plex untested |
| 26.1.2 | Forge / NeoForge | video port build-verified, dedicated-launch-tested, protocol-client-tested, and real two-client 144p Quick TV deterministic-HLS A/V-certified (Forge: 0.987922 correlation at 70 ms; NeoForge: 0.988920 at -40 ms); live Plex untested |
| 26.2 | Fabric / Quilt | video port build-verified, dedicated-launch-tested, protocol-client-tested, and real two-client 144p Quick TV deterministic-HLS A/V-certified (Fabric: 0.999427 correlation at 20 ms; Quilt: 0.996959 at -20 ms); live Plex untested |
| 26.2 | Forge / NeoForge | video port build-verified, dedicated-launch-tested, protocol-client-tested, and real two-client 144p Quick TV deterministic-HLS A/V-certified (Forge: 0.996621 correlation at 80 ms; NeoForge: 0.990375 at 40 ms); live Plex untested |
| 1.7.10 | Forge | isolated video runtime build-verified, dedicated-launch-tested, protocol-client-tested, and real two-client 144p Quick TV deterministic-HLS A/V-certified in consecutive fresh runs (0.986105 correlation at -10 ms; 0.978383 at -30 ms); live Plex untested |

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

The repository decoder also passed a Windows-x64 native smoke under a Windows
Temurin 21 JVM: JavaCV/FFmpeg loaded and decoded three video frames and 30 audio
frames. This proves the packaged Windows decoder path, not a full Windows
Minecraft-client launch.

No target may be advertised as release-certified until it passes compile,
launch, runtime, clean-shutdown, two-client A/V, and live Plex acceptance.
No cross-loader or cross-version compatibility is implied by the protocol
number or by a shared-core test. Forge 1.7.10 uses its own Java 8 bytecode,
LWJGL 2 renderer/audio adapter, and embedded native-decoder implementation.
