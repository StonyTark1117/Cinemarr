# Compatibility

Cinemarr's video runtime is release-certified only on NeoForge 1.21.1 with
Java 21. The shared transport core is Java-8-compatible, but loader networking,
registries, render events, and native decoder packaging remain platform-
specific.

| Minecraft | Loader | Status |
| --- | --- | --- |
| 1.21.1 | NeoForge | release-certified baseline |
| 1.7.10, 1.20.1, 1.20.2, 1.21.1, 26.1.2, 26.2 | Fabric/Quilt/Forge/NeoForge as present | adapter scaffolding; video unsupported |

The `platforms/` directories are retained Jammarr-derived scaffolding. They
must not be advertised as working Cinemarr video artifacts until each target
passes compile, launch, runtime, clean-shutdown, and two-client A/V acceptance.
No cross-loader or cross-version compatibility is implied by the protocol
number or by a shared-core test. Forge 1.7.10 remains last because its Java 8
and LWJGL runtime require a separate native-decoder implementation.
