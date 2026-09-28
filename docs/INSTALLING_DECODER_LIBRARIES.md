# Installing Cinemarr 1.0.1 decoder libraries

Cinemarr 1.0.1 retains its JavaCV/FFmpeg decoder but ships no native libraries or executables. **Each player who wants video playback must install the matching native libraries locally.** Dedicated servers do not need them. Cinemarr never downloads these dependencies, updates them, or runs the FFmpeg command-line programs.

Installing FFmpeg through a package manager alone is insufficient: Cinemarr also needs the matching JavaCPP JNI bridge libraries. Installing a JDK, Maven, or JavaCPP compiler is not necessary.

## Recommended installation

1. Find your Minecraft instance's game directory using your launcher's **Open folder** action. This is the directory containing `mods` and `config`.
2. Obtain these two official **non-GPL native JARs**, matching the operating system and architecture of your Minecraft Java runtime:

   | Platform | JavaCPP package | FFmpeg package |
   | --- | --- | --- |
   | Windows x64 | `javacpp-1.5.14-windows-x86_64.jar` | `ffmpeg-8.1.2-1.5.14-windows-x86_64.jar` |
   | Linux x64 | `javacpp-1.5.14-linux-x86_64.jar` | `ffmpeg-8.1.2-1.5.14-linux-x86_64.jar` |
   | Linux ARM64 | `javacpp-1.5.14-linux-arm64.jar` | `ffmpeg-8.1.2-1.5.14-linux-arm64.jar` |

   The upstream [Bytedeco distribution guide](https://bytedeco.org/download/) explains its Maven Central packages. The version directories are [JavaCPP 1.5.14](https://repo.maven.apache.org/maven2/org/bytedeco/javacpp/1.5.14/) and [FFmpeg 8.1.2-1.5.14](https://repo.maven.apache.org/maven2/org/bytedeco/ffmpeg/8.1.2-1.5.14/). Choose the exact filenames above, not `sources`, `javadoc`, `platform`, or `gpl` variants. The combined download is about 25–31 MiB; allow 150 MiB of free space for import and extracted libraries.
3. Create `cinemarr-libraries` inside the game directory and put both JARs there. **Do not put these JARs in `mods`.** Keep the original packages and their upstream license information.
4. Launch Minecraft and start playback. Cinemarr checks the packages against its pinned checksums and imports only the required shared libraries into `cinemarr-libraries/.runtime`. It does not import Java classes or FFmpeg/FFprobe command-line programs. Later launches can use this cache offline.

If you installed the packages while Minecraft was open, choose **Retry libraries** in Cinemarr's client settings, then retry playback. Restart Minecraft if settings report that native initialization has already failed. Hover over the retry button for details; the client log also records loading failures. **Library folder** opens the configured installation directory when your desktop supports that action.

macOS, Windows ARM64, and 32-bit Java runtimes are not supported by this runtime set.

On Linux, the upstream libraries also need system desktop/audio libraries. Minimal ARM64 installations may lack these even with both native packages present. On Debian/Ubuntu, install them manually with `sudo apt install libdrm2 libxcb1 libxcb-shm0 libpulse0`. A missing `libxcb-shm.so.0` prevents FFmpeg's required device library from loading, including for software playback. Other distributions use their equivalent packages. Cinemarr does not install system packages.

## Explicit library directory

Edit `config/cinemarr-client.toml` while Minecraft is closed:

```toml
nativeRuntimeMode = "local-only"
nativeRuntimeDirectory = "D:/Minecraft libraries/cinemarr"
```

Use forward slashes on Windows, or escape backslashes as `\\`. Spaces are supported. A relative path is resolved against this Minecraft instance's game directory. Leave the path blank for automatic local detection:

```toml
nativeRuntimeMode = "local-only"
nativeRuntimeDirectory = ""
```

An explicit path takes precedence. If it is missing or incompatible, Cinemarr reports that problem instead of selecting a different installation. The directory can contain the two named native JARs or the complete matching extracted libraries. For extracted packages, preserve the `org/bytedeco/<artifact>/<platform>/` hierarchy from both JARs under that directory. A flat directory containing the complete matching library set is also recognized. Do not mix libraries from different versions or platforms.

`nativeRuntimeMode = "disabled"` disables Cinemarr's local native decoding without uninstalling the mod. No mode enables downloading.

## Automatic local detection

With a blank override, Cinemarr checks these locations in order:

1. The instance's verified `.runtime` cache.
2. The instance's `cinemarr-libraries` folder.
3. The default JavaCPP cache (`~/.javacpp/cache`), the local Maven repository (`~/.m2/repository`), an explicitly configured JavaCPP cache, then directories already in `java.library.path`.

Detection checks only documented layouts for the complete pinned JavaCPP and FFmpeg set. It does not scan the whole disk, run commands from `PATH`, or accept an arbitrary system FFmpeg version. Existing JavaCPP caches must retain the package directory and `org/bytedeco/...` hierarchy. If detection cannot recognize your installation, supply its directory explicitly or use the two-package installation above.

## Troubleshooting and upgrading

- **Missing or mismatched libraries:** check both package filenames, runtime architecture, and configured directory. Cinemarr accepts only the pinned upstream bytes. Obtain clean matching packages if a checksum fails.
- **Corrupt extracted cache:** close Minecraft, remove only `cinemarr-libraries/.runtime`, retain or restore the two original packages in `cinemarr-libraries`, then restart to import them again.
- **Read-only game directory:** supply a complete extracted runtime in an accessible directory. Importing archives requires a writable instance cache.
- **Initialization failure or another mod loaded a different library:** use matching JavaCPP/FFmpeg versions across the instance, check the client log for missing host dependencies, then restart. Loaded native code cannot safely be replaced inside a running JVM.
- **Unable to open the library folder:** open the path manually in your file manager.
- **Upgrading from 1.0.0:** replace the Cinemarr mod JAR and install the libraries using these instructions. An existing matching JavaCPP cache may be detected, but do not rely on it when distributing a fresh instance. Screen saves, Plex server configuration, and protocol 11 remain unchanged.

Missing libraries affect local video decoding. Minecraft can still start and connect to the server.
