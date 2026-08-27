# Compatibility

Cinemarr's video runtime is release-certified only on NeoForge 1.21.1 with
Java 21. The shared transport core is Java-8-compatible, but loader networking,
registries, render events, and native decoder packaging remain platform-
specific.

| Minecraft | Loader | Status |
| --- | --- | --- |
| 1.21.1 | NeoForge | release-certified baseline |
| 1.21.1 | Fabric / Quilt | video port compiled and dedicated-launch-tested; two-client A/V untested |
| 1.21.1 | Forge | video port compiled and dedicated-launch-tested; two-client A/V untested |
| 1.20.1 | Fabric / Quilt | video port build-verified, dedicated-launch-tested, and protocol-client-tested; two-client A/V untested |
| 1.20.1 | Forge / NeoForge | video port build-verified, dedicated-launch-tested, and protocol-client-tested; two-client A/V untested |
| 1.20.2 | Fabric / Quilt | video port build-verified, dedicated-launch-tested, and protocol-client-tested; two-client A/V untested |
| 1.20.2 | Forge / NeoForge | video port build-verified, dedicated-launch-tested, and protocol-client-tested; two-client A/V untested |
| 26.1.2 | Fabric / Quilt | video port build-verified, dedicated-launch-tested, and protocol-client-tested; two-client A/V untested |
| 26.1.2 | Forge / NeoForge | video port build-verified, dedicated-launch-tested, and protocol-client-tested; two-client A/V untested |
| 1.7.10, 26.2 | Fabric/Quilt/Forge/NeoForge as present | adapter scaffolding; video unsupported |

The unported `platforms/` directories are retained Jammarr-derived scaffolding.
No target may be advertised as release-certified until it
passes compile, launch, runtime, clean-shutdown, and two-client A/V acceptance.
No cross-loader or cross-version compatibility is implied by the protocol
number or by a shared-core test. Forge 1.7.10 remains last because its Java 8
and LWJGL runtime require a separate native-decoder implementation.
