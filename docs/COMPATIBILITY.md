# Compatibility

Cinemarr has no release-certified artifact yet because live Plex validation and
per-target real-client acceptance remain incomplete. The shared transport core is
Java-8-compatible; loader networking, registries, render events, native decoder
packaging, and Quick TV Kit registration are platform-specific.

| Minecraft | Loader | Status |
| --- | --- | --- |
| 1.21.1 | NeoForge | build-verified, dedicated-launch-tested, protocol-client-tested, and real two-client deterministic-HLS A/V-certified across consecutive fresh runs (70 ms and 90 ms measured lag); live Plex untested |
| 1.21.1 | Fabric | build-verified, dedicated-launch-tested, protocol-client-tested, and real two-client deterministic-HLS A/V-certified (0.986383 correlation, -10 ms measured lag); live Plex untested |
| 1.21.1 | Quilt | Fabric-port runtime build-verified, dedicated-launch-tested, protocol-client-tested, and real two-client deterministic-HLS A/V-certified (0.999974 correlation, 0 ms measured lag); live Plex untested |
| 1.21.1 | Forge | build-verified, dedicated-launch-tested, protocol-client-tested, and real two-client deterministic-HLS A/V-certified (0.992552 correlation, 30 ms measured lag); live Plex untested |
| 1.20.1 | Fabric / Quilt | video port build-verified, dedicated-launch-tested, protocol-client-tested, and real two-client deterministic-HLS A/V-certified (Fabric: 0.995863 correlation at -50 ms; Quilt: 0.995783 at 30 ms); live Plex untested |
| 1.20.1 | Forge / NeoForge | video port build-verified, dedicated-launch-tested, protocol-client-tested, and real two-client deterministic-HLS A/V-certified (Forge: 0.994928 correlation at -40 ms; NeoForge: 0.986825 at -30 ms); live Plex untested |
| 1.20.2 | Fabric / Quilt | video port build-verified, dedicated-launch-tested, protocol-client-tested, and real two-client deterministic-HLS A/V-certified (Fabric: 0.993816 correlation at -10 ms; Quilt: 0.986201 at 70 ms); live Plex untested |
| 1.20.2 | Forge / NeoForge | video port build-verified, dedicated-launch-tested, protocol-client-tested, and real two-client deterministic-HLS A/V-certified (Forge: 0.991231 correlation at 20 ms; NeoForge: 0.991795 at 0 ms); live Plex untested |
| 26.1.2 | Fabric / Quilt | video port build-verified, dedicated-launch-tested, protocol-client-tested, and real two-client deterministic-HLS A/V-certified (Fabric: 0.994030 correlation at -20 ms; Quilt: 0.991515 at 30 ms); live Plex untested |
| 26.1.2 | Forge / NeoForge | video port build-verified, dedicated-launch-tested, protocol-client-tested, and real two-client deterministic-HLS A/V-certified (Forge: 0.998905 correlation at 0 ms; NeoForge: 0.997870 at 10 ms); live Plex untested |
| 26.2 | Fabric / Quilt | video port build-verified, dedicated-launch-tested, protocol-client-tested, and real two-client deterministic-HLS A/V-certified (Fabric: 0.991396 correlation at 20 ms; Quilt: 0.992901 at -10 ms); live Plex untested |
| 26.2 | Forge / NeoForge | video port build-verified, dedicated-launch-tested, protocol-client-tested, and real two-client deterministic-HLS A/V-certified (Forge: 0.997424 correlation at -10 ms; NeoForge: 0.992739 at 10 ms); live Plex untested |
| 1.7.10 | Forge | isolated video runtime build-verified, dedicated-launch-tested, protocol-client-tested, and real two-client deterministic-HLS A/V-certified (0.983183 correlation at -110 ms); live Plex untested |

Every listed target contains the eight configurable Quick TV Kit registrations;
the quick-build behavior has shared unit coverage and a passing NeoForge 1.21.1
in-game placement/persistence GameTest, but still needs per-target placement
acceptance in the final matrix. NeoForge 1.21.1 passed consecutive fresh real
two-client visual/audio gates with visible test-card screenshots, an exact common
decoded-frame hash, and audio-envelope results of 0.993263 correlation at 70 ms
lag and 0.993801 at 90 ms. Fabric 1.21.1 independently passed the same gate with
visible screenshots, that exact common frame hash, and 0.986383 correlation at
-10 ms lag. Quilt passed through its own runtime profile with visible screenshots,
the same common frame hash, and 0.999974 correlation at 0 ms lag. Forge 1.21.1
also passed with visible screenshots, the same common frame hash, and 0.992552
correlation at 30 ms lag. Fabric and Quilt 1.20.2 independently passed the real-client gate with visible
test-card screenshots, exact common decoded frames, and audio-envelope results
of 0.993816 correlation at -10 ms and 0.986201 at 70 ms, respectively.
Forge and NeoForge 1.20.2 passed with the same visual/HLS evidence and
audio-envelope results of 0.991231 correlation at 20 ms and 0.991795 at 0 ms.
Fabric, Quilt, Forge, and NeoForge 1.20.1 also passed their own real-client
profiles with visible screenshots, a common decoded-frame hash, the complete
fake-Plex HLS route, clean teardown, and correlations of 0.995863, 0.995783,
0.994928, and 0.986825 at -50 ms, 30 ms, -40 ms, and -30 ms, respectively.
The four 26.1.2 profiles passed the same complete gate with correlations of
0.994030, 0.991515, 0.998905, and 0.997870 at -20 ms, 30 ms, 0 ms, and 10 ms.
The four 26.2 profiles also passed the complete gate with correlations of
0.991396, 0.992901, 0.997424, and 0.992739 at 20 ms, -10 ms, -10 ms, and 10 ms.
Forge 1.7.10 passed its matching-client gate with visible screenshots, common
decoded-frame SHA-256
`028918a15a341e02f28328967c02c350685ed4b88e9cae106bcea6b6268618e0`,
0.983183 correlation at -110 ms, the complete HLS route, and clean teardown.
No target may be advertised as release-certified until it passes compile,
launch, runtime, clean-shutdown, two-client A/V, and live Plex acceptance.
No cross-loader or cross-version compatibility is implied by the protocol
number or by a shared-core test. Forge 1.7.10 uses its own Java 8 bytecode,
LWJGL 2 renderer/audio adapter, and embedded native-decoder implementation.
