# Compatibility

Cinemarr has no release-certified artifact yet because the decisive real two-
client audio/video test remains outstanding. The shared transport core is
Java-8-compatible; loader networking, registries, render events, native decoder
packaging, and Quick TV Kit registration are platform-specific.

| Minecraft | Loader | Status |
| --- | --- | --- |
| 1.21.1 | NeoForge | build-verified, dedicated-launch-tested, and protocol-client-tested; two-client A/V untested |
| 1.21.1 | Fabric / Quilt | video port compiled and dedicated-launch-tested; two-client A/V untested |
| 1.21.1 | Forge | video port compiled and dedicated-launch-tested; two-client A/V untested |
| 1.20.1 | Fabric / Quilt | video port build-verified, dedicated-launch-tested, and protocol-client-tested; two-client A/V untested |
| 1.20.1 | Forge / NeoForge | video port build-verified, dedicated-launch-tested, and protocol-client-tested; two-client A/V untested |
| 1.20.2 | Fabric / Quilt | video port build-verified, dedicated-launch-tested, and protocol-client-tested; two-client A/V untested |
| 1.20.2 | Forge / NeoForge | video port build-verified, dedicated-launch-tested, and protocol-client-tested; two-client A/V untested |
| 26.1.2 | Fabric / Quilt | video port build-verified, dedicated-launch-tested, and protocol-client-tested; two-client A/V untested |
| 26.1.2 | Forge / NeoForge | video port build-verified, dedicated-launch-tested, and protocol-client-tested; two-client A/V untested |
| 26.2 | Fabric / Quilt | video port build-verified, dedicated-launch-tested, and protocol-client-tested; two-client A/V untested |
| 26.2 | Forge / NeoForge | video port build-verified, dedicated-launch-tested, and protocol-client-tested; two-client A/V untested |
| 1.7.10 | Forge | video runtime build-verified, dedicated-launch-tested, and protocol rejection tested; matching-client A/V and two-client A/V untested |

Every listed target contains the eight configurable Quick TV Kit registrations;
the quick-build behavior has shared unit coverage and a passing NeoForge 1.21.1
in-game placement/persistence GameTest, but still needs per-target placement
acceptance in the final matrix. No target may be advertised as release-certified until it
passes compile, launch, runtime, clean-shutdown, and two-client A/V acceptance.
No cross-loader or cross-version compatibility is implied by the protocol
number or by a shared-core test. Forge 1.7.10 uses its own Java 8 bytecode,
LWJGL 2 renderer/audio adapter, and embedded native-decoder implementation.
