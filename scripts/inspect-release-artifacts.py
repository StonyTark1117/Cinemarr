#!/usr/bin/env python3
"""Fail closed when a Cinemarr release set is incomplete or incorrectly packaged."""

from __future__ import annotations

import hashlib
import io
import json
import os
import re
import struct
import sys
import zipfile
from pathlib import Path, PurePosixPath


PRODUCT_VERSION = "1.0.0"
PROTOCOL_VERSION = 8
TARGETS = (
    ("1.7.10", "forge", 8, 52),
    ("1.20.1", "fabric", 17, 61),
    ("1.20.1", "forge", 17, 61),
    ("1.20.1", "neoforge", 17, 61),
    ("1.20.2", "fabric", 17, 61),
    ("1.20.2", "forge", 17, 61),
    ("1.20.2", "neoforge", 17, 61),
    ("1.21.1", "fabric", 21, 65),
    ("1.21.1", "forge", 21, 65),
    ("1.21.1", "neoforge", 21, 65),
    ("26.1.2", "fabric", 25, 69),
    ("26.1.2", "forge", 25, 69),
    ("26.1.2", "neoforge", 25, 69),
    ("26.2", "fabric", 25, 69),
    ("26.2", "forge", 25, 69),
    ("26.2", "neoforge", 25, 69),
)
COMMON_ENTRIES = {
    "META-INF/LICENSE-Cinemarr-CC0-1.0.txt",
    "META-INF/LICENSE-LGPL-2.1-or-later.txt",
    "META-INF/THIRD_PARTY_NOTICES.md",
    "assets/cinemarr/lang/en_us.json",
    "cinemarr.png",
    "stonytark/cinemarr/Cinemarr.class",
}
QUICK_TV_IDS = ("144p", "240p", "480p", "720p", "1080p", "1440p", "4k", "8k")
TV_BLOCK_IDS = ("screen_pixel", "tv_controller", "tv_casing", "tv_speaker", "redstone_receiver")
TV_COMPONENT_IDS = TV_BLOCK_IDS + ("tv_remote",)
PRIVATE_ADDRESS = re.compile(rb"(?<![0-9])(?:10\.(?:[0-9]{1,3}\.){2}[0-9]{1,3}|192\.168\.(?:[0-9]{1,3}\.)[0-9]{1,3}|172\.(?:1[6-9]|2[0-9]|3[01])\.(?:[0-9]{1,3}\.)[0-9]{1,3})(?![0-9])")
TEXT_SUFFIXES = (".json", ".toml", ".info", ".lang", ".md", ".txt", ".properties", ".mf")


def fail(message: str) -> None:
    raise AssertionError(message)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def class_major(data: bytes, label: str) -> int:
    if len(data) < 8 or data[:4] != b"\xca\xfe\xba\xbe":
        fail(f"{label} is not a Java class")
    return struct.unpack(">H", data[6:8])[0]


def class_contract(data: bytes, label: str) -> tuple[set[tuple[str, str]], tuple[str, ...]]:
    """Returns declared method signatures and directly implemented interfaces."""
    class_major(data, label)
    offset = 8

    def take(size: int) -> bytes:
        nonlocal offset
        end = offset + size
        if end > len(data):
            fail(f"{label} has a truncated class structure")
        value = data[offset:end]
        offset = end
        return value

    def u1() -> int:
        return take(1)[0]

    def u2() -> int:
        return struct.unpack(">H", take(2))[0]

    def u4() -> int:
        return struct.unpack(">I", take(4))[0]

    constant_count = u2()
    constants: list[object | None] = [None] * constant_count
    index = 1
    while index < constant_count:
        tag = u1()
        if tag == 1:
            length = u2()
            constants[index] = take(length).decode("utf-8", "strict")
        elif tag in (3, 4):
            take(4)
        elif tag in (5, 6):
            take(8)
            index += 1
        elif tag in (7, 8, 16, 19, 20):
            constants[index] = (tag, u2())
        elif tag in (9, 10, 11, 12, 17, 18):
            take(4)
        elif tag == 15:
            take(3)
        else:
            fail(f"{label} has unknown constant-pool tag {tag}")
        index += 1

    def utf8(constant_index: int) -> str:
        value = constants[constant_index] if 0 < constant_index < len(constants) else None
        if not isinstance(value, str):
            fail(f"{label} has an invalid UTF-8 constant reference")
        return value

    def class_name(constant_index: int) -> str:
        value = constants[constant_index] if 0 < constant_index < len(constants) else None
        if not isinstance(value, tuple) or value[0] != 7:
            fail(f"{label} has an invalid class constant reference")
        return utf8(value[1])

    take(2)  # access_flags
    take(2)  # this_class
    take(2)  # super_class
    interfaces = tuple(class_name(u2()) for _ in range(u2()))

    def skip_attributes() -> None:
        for _ in range(u2()):
            take(2)
            take(u4())

    for _ in range(u2()):
        take(2)  # access_flags
        take(2)  # name_index
        take(2)  # descriptor_index
        skip_attributes()

    methods: set[tuple[str, str]] = set()
    for _ in range(u2()):
        take(2)  # access_flags
        name = utf8(u2())
        descriptor = utf8(u2())
        methods.add((name, descriptor))
        skip_attributes()
    return methods, interfaces


def verify_direct_interface(archive: zipfile.ZipFile, interface_name: str,
                            implementation_name: str, filename: str) -> None:
    """Ensures a reobfuscated adapter still declares every shared-interface method."""
    implementation_entry = implementation_name + ".class"
    try:
        implementation_data = archive.read(implementation_entry)
    except KeyError:
        fail(f"{filename} is missing mapped adapter {implementation_entry}")
    implementation_methods, _ = class_contract(
        implementation_data, f"{filename}:{implementation_entry}")

    interface_methods: set[tuple[str, str]] = set()
    pending = [interface_name]
    visited: set[str] = set()
    while pending:
        current = pending.pop()
        if current in visited:
            continue
        visited.add(current)
        entry = current + ".class"
        try:
            interface_data = archive.read(entry)
        except KeyError:
            fail(f"{filename} is missing shared interface {entry}")
        methods, parents = class_contract(interface_data, f"{filename}:{entry}")
        interface_methods.update(methods)
        pending.extend(parents)
    required = {signature for signature in interface_methods if not signature[0].startswith("<")}
    missing = required - implementation_methods
    if missing:
        rendered = ", ".join(name + descriptor for name, descriptor in sorted(missing))
        fail(f"{filename}:{implementation_entry} loses shared-interface methods after reobfuscation: {rendered}")


def safe_entries(archive: zipfile.ZipFile, filename: str) -> set[str]:
    names = [entry.filename for entry in archive.infolist()]
    if len(names) != len(set(names)):
        fail(f"{filename} contains duplicate ZIP entries")
    for name in names:
        path = PurePosixPath(name)
        if path.is_absolute() or ".." in path.parts or "\\" in name:
            fail(f"{filename} contains unsafe ZIP path {name!r}")
    bad = archive.testzip()
    if bad is not None:
        fail(f"{filename} has a corrupt ZIP entry: {bad}")
    return set(names)


def require_nested_class(archive: zipfile.ZipFile, nested_name: str, class_name: str, filename: str) -> bytes:
    try:
        nested_bytes = archive.read(nested_name)
    except KeyError:
        fail(f"{filename} is missing bundled library {nested_name}")
    try:
        with zipfile.ZipFile(io.BytesIO(nested_bytes)) as nested:
            return nested.read(class_name)
    except (KeyError, zipfile.BadZipFile) as error:
        fail(f"{filename}:{nested_name} is missing {class_name}: {error}")


def verify_png(data: bytes, filename: str) -> None:
    if len(data) < 33 or data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
        fail(f"{filename} has an invalid icon")
    width, height = struct.unpack(">II", data[16:24])
    colour_type = data[25]
    if width < 16 or height < 16 or colour_type not in (4, 6):
        fail(f"{filename} icon must be at least 16x16 and include an alpha channel")


def verify_no_deployment_secrets(archive: zipfile.ZipFile, filename: str) -> None:
    configured_token = os.environ.get("CINEMARR_PLEX_TOKEN", "").encode()
    for entry in archive.infolist():
        if entry.is_dir() or entry.file_size > 8 * 1024 * 1024:
            continue
        data = archive.read(entry)
        if configured_token and len(configured_token) >= 8 and configured_token in data:
            fail(f"{filename}:{entry.filename} embeds CINEMARR_PLEX_TOKEN")
        if PRIVATE_ADDRESS.search(data):
            fail(f"{filename}:{entry.filename} embeds an RFC1918 deployment address")
        if entry.filename.lower().endswith(TEXT_SUFFIXES):
            lowered = data.lower()
            if b"plex-token=" in lowered or b"plex_token=" in lowered:
                fail(f"{filename}:{entry.filename} appears to embed a Plex credential")


def verify_remappable_keybinding(archive: zipfile.ZipFile, minecraft: str,
                                 loader: str, filename: str) -> None:
    translations = json.loads(archive.read("assets/cinemarr/lang/en_us.json"))
    for key in ("key.cinemarr.open", "key.categories.cinemarr"):
        if not isinstance(translations.get(key), str) or not translations[key].strip():
            fail(f"{filename} is missing the Controls translation {key}")

    legacy = minecraft == "1.7.10"
    class_name = ("stonytark/cinemarr/client/LegacyClient.class" if legacy
                  else "stonytark/cinemarr/client/CinemarrClient.class")
    try:
        client = archive.read(class_name)
    except KeyError:
        fail(f"{filename} is missing keybinding owner {class_name}")
    if b"key.cinemarr.open" not in client:
        fail(f"{filename} does not construct the translated Cinemarr menu binding")

    if legacy:
        required = (b"net/minecraft/client/settings/KeyBinding", b"registerKeyBinding",
                    b"InputEvent$KeyInputEvent")
        consumes_binding = b"isPressed" in client or b"func_151468_f" in client
        if any(marker not in client for marker in required) or not consumes_binding:
            fail(f"{filename} does not register and consume a remappable legacy KeyBinding")
        legacy_lang = archive.read("assets/cinemarr/lang/en_US.lang")
        if b"key.cinemarr.open=" not in legacy_lang or b"key.categories.cinemarr=" not in legacy_lang:
            fail(f"{filename} is missing legacy Controls translations")
        return

    # Fabric remaps released pre-26.x classes to stable intermediary names;
    # class_304 and method_1436 are KeyMapping and consumeClick respectively.
    if b"net/minecraft/client/KeyMapping" not in client and b"net/minecraft/class_304" not in client:
        fail(f"{filename} does not construct a remappable KeyMapping")
    if loader == "fabric":
        if not ((b"KeyBindingHelper" in client and b"registerKeyBinding" in client)
                or (b"KeyMappingHelper" in client and b"registerKeyMapping" in client)):
            fail(f"{filename} does not register the menu binding with Fabric")
        if b"consumeClick" not in client and b"method_1436" not in client:
            fail(f"{filename} does not consume the configured Fabric menu binding")
    elif b"RegisterKeyMappingsEvent" not in client:
        fail(f"{filename} does not register the menu binding with its loader Controls event")
    elif b"consumeClick" not in client and b"getKey" not in client and b"m_90859_" not in client:
        fail(f"{filename} does not consume the configured menu binding")


def verify_metadata(archive: zipfile.ZipFile, names: set[str], minecraft: str, loader: str,
                    java: int, filename: str) -> None:
    if loader == "fabric":
        metadata = json.loads(archive.read("fabric.mod.json"))
        if metadata.get("id") != "cinemarr" or metadata.get("version") != PRODUCT_VERSION:
            fail(f"{filename} has incorrect Fabric identity/version")
        if metadata.get("environment") != "*" or not metadata.get("entrypoints", {}).get("client"):
            fail(f"{filename} is not declared for both client and server")
        if metadata.get("depends", {}).get("minecraft") != f"~{minecraft}":
            fail(f"{filename} has incorrect Minecraft dependency")
        if metadata.get("depends", {}).get("java") != f">={java}":
            fail(f"{filename} has incorrect Java dependency")
        expected_fabric_loader = ">=0.19.2"
        if metadata.get("depends", {}).get("fabricloader") != expected_fabric_loader:
            fail(f"{filename} has incorrect Fabric Loader compatibility metadata")
        if "quilt.mod.json" in names or any("qsl" in name.lower() or "quilted_fabric_api" in name.lower()
                                            for name in names):
            fail(f"{filename} must not embed Quilt metadata, QSL, or Quilted Fabric API")
        if metadata.get("mixins") != ["cinemarr.mixins.json"]:
            fail(f"{filename} does not declare the Cinemarr Mixin config")
        if minecraft in ("26.1.2", "26.2"):
            required_quilt_hooks = {
                "stonytark/cinemarr/mixin/QuiltServerBootstrapMixin.class",
                "stonytark/cinemarr/mixin/client/QuiltClientBootstrapMixin.class",
                "stonytark/cinemarr/quilt/QuiltNetworkingCodecRepair.class",
            }
            if not required_quilt_hooks.issubset(names):
                fail(f"{filename} is missing its guarded Quilt 26.x compatibility hooks")
        expected_jars = {
            "META-INF/jars/core-1.0.0.jar",
            "META-INF/jars/jlayer-1.0.1.jar",
            "META-INF/jars/jump3r-1.0.5.jar",
            "META-INF/jars/javacpp-1.5.14.jar",
            "META-INF/jars/javacpp-1.5.14-linux-x86_64.jar",
            "META-INF/jars/javacpp-1.5.14-linux-arm64.jar",
            "META-INF/jars/javacpp-1.5.14-windows-x86_64.jar",
            "META-INF/jars/ffmpeg-8.1.2-1.5.14.jar",
            "META-INF/jars/ffmpeg-8.1.2-1.5.14-linux-x86_64.jar",
            "META-INF/jars/ffmpeg-8.1.2-1.5.14-linux-arm64.jar",
            "META-INF/jars/ffmpeg-8.1.2-1.5.14-windows-x86_64.jar",
        }
        declared_jars = {value.get("file") for value in metadata.get("jars", [])}
        if declared_jars != expected_jars:
            fail(f"{filename} has incorrect nested-library metadata: {sorted(declared_jars)}")
        return

    if minecraft == "1.7.10":
        metadata = json.loads(archive.read("mcmod.info"))
        if len(metadata) != 1 or metadata[0].get("modid") != "cinemarr":
            fail(f"{filename} has incorrect legacy mod identity")
        if metadata[0].get("version") != PRODUCT_VERSION or metadata[0].get("mcversion") != minecraft:
            fail(f"{filename} has incorrect legacy version metadata")
        if "cinemarr.mixins.json" in names:
            fail(f"{filename} must not advertise unsupported modern Mixins on Forge 1.7.10")
        legacy_class = archive.read("stonytark/cinemarr/Cinemarr.class")
        if b"NetworkCheckHandler" not in legacy_class or b"acceptableRemoteVersions" not in legacy_class:
            fail(f"{filename} does not contain required-client FML negotiation")
        return

    metadata_name = "META-INF/neoforge.mods.toml" if loader == "neoforge" and minecraft in ("1.21.1", "26.1.2", "26.2") else "META-INF/mods.toml"
    text = archive.read(metadata_name).decode("utf-8")
    compact = re.sub(r"\s+", "", text)
    if 'modId="cinemarr"' not in compact or f'version="{PRODUCT_VERSION}"' not in compact:
        fail(f"{filename} has incorrect loader metadata identity/version")
    if minecraft not in text or 'side="BOTH"' not in compact:
        fail(f"{filename} is not constrained to Minecraft {minecraft} on both sides")
    if loader == "neoforge" and minecraft in ("1.21.1", "26.1.2", "26.2"):
        if 'type="required"' not in compact:
            fail(f"{filename} does not declare required NeoForge dependencies")
    elif 'displayTest="MATCH_VERSION"' not in compact:
        fail(f"{filename} does not require a matching remote mod version")


def verify_jar(path: Path, minecraft: str, loader: str, java: int, expected_major: int) -> None:
    filename = path.name
    with zipfile.ZipFile(path) as archive:
        names = safe_entries(archive, filename)
        missing = COMMON_ENTRIES - names
        if missing:
            fail(f"{filename} is missing required entries: {sorted(missing)}")
        if loader == "fabric" and "fabric.mod.json" not in names:
            fail(f"{filename} is missing Fabric metadata")
        if minecraft == "1.7.10" and "mcmod.info" not in names:
            fail(f"{filename} is missing legacy Forge metadata")

        verify_metadata(archive, names, minecraft, loader, java, filename)
        json.loads(archive.read("assets/cinemarr/lang/en_us.json"))
        verify_remappable_keybinding(archive, minecraft, loader, filename)
        verify_png(archive.read("cinemarr.png"), filename)
        verify_tv_components(archive, names, minecraft, filename)
        quick_assets = {
            entry
            for quick_id in QUICK_TV_IDS
            for entry in (
                f"assets/cinemarr/blockstates/quick_tv_{quick_id}.json",
                f"assets/cinemarr/models/block/quick_tv_{quick_id}.json",
                f"assets/cinemarr/models/item/quick_tv_{quick_id}.json",
            )
        }
        if quick_assets - names:
            fail(f"{filename} is missing Quick TV assets: {sorted(quick_assets - names)}")
        verify_quick_tv_recipes(archive, names, minecraft, filename)
        for notice in ("META-INF/LICENSE-Cinemarr-CC0-1.0.txt", "META-INF/LICENSE-LGPL-2.1-or-later.txt",
                       "META-INF/THIRD_PARTY_NOTICES.md"):
            if len(archive.read(notice).strip()) < 32:
                fail(f"{filename}:{notice} is unexpectedly empty")

        main_major = class_major(archive.read("stonytark/cinemarr/Cinemarr.class"), f"{filename}:Cinemarr.class")
        if main_major != expected_major:
            fail(f"{filename} targets class major {main_major}, expected {expected_major} for Java {java}")

        if minecraft == "1.7.10":
            required_legacy = {
                "assets/cinemarr/lang/en_US.lang",
                "stonytark/cinemarr/core/server/ChunkTransferPolicy.class",
                "stonytark/cinemarr/core/server/CoordinatorRuntime.class",
                "stonytark/cinemarr/core/server/GlobalPlaybackCoordinator.class",
                "stonytark/cinemarr/core/server/PlaybackStore.class",
                "stonytark/cinemarr/network/LegacyNetwork.class",
                "stonytark/cinemarr/server/LegacyGlobalPlayer.class",
                "stonytark/cinemarr/client/LegacyAudioPlayer.class",
                "stonytark/cinemarr/client/LegacyScreen.class",
                "stonytark/cinemarr/server/LegacySavedData.class",
                "stonytark/cinemarr/screen/LegacyQuickTvBlock.class",
                "stonytark/cinemarr/core/screen/QuickTvPreset.class",
                "javazoom/jl/decoder/Decoder.class",
                "de/sciss/jump3r/mp3/Lame.class",
            }
            if required_legacy - names:
                fail(f"{filename} is missing legacy runtime entries: {sorted(required_legacy - names)}")
            for name in names:
                if name.startswith("stonytark/cinemarr/") and name.endswith(".class"):
                    major = class_major(archive.read(name), f"{filename}:{name}")
                    if major != 52:
                        fail(f"{filename}:{name} is class major {major}, expected Java 8 major 52")
            # The shared core is compiled against stable names while Forge 1.7.10
            # reobfuscates Minecraft methods in adapters. Require each adapter to
            # declare its full shared contract so an inherited MCP-named method
            # cannot disappear from the production linkage (as markDirty once did).
            for interface_name, implementation_name in (
                ("stonytark/cinemarr/core/server/PlaybackStore",
                 "stonytark/cinemarr/server/LegacySavedData"),
                ("stonytark/cinemarr/core/server/CoordinatorRuntime",
                 "stonytark/cinemarr/server/LegacyGlobalPlayer$1"),
                ("stonytark/cinemarr/core/platform/CoreLogger",
                 "stonytark/cinemarr/server/LegacyGlobalPlayer$1$1"),
                ("stonytark/cinemarr/core/server/PlexGateway",
                 "stonytark/cinemarr/core/server/PlexService"),
                ("stonytark/cinemarr/core/network/HttpTransport",
                 "stonytark/cinemarr/core/network/UrlConnectionHttpTransport"),
            ):
                verify_direct_interface(archive, interface_name, implementation_name, filename)
        else:
            required_modern = {
                "stonytark/cinemarr/screen/QuickTvBlock.class",
                "stonytark/cinemarr/client/CinemarrVideoAudio.class",
                "stonytark/cinemarr/mixin/client/ChannelAccessor.class",
            }
            if required_modern - names:
                fail(f"{filename} is missing modern runtime entries: {sorted(required_modern - names)}")
            if "cinemarr.mixins.json" not in names:
                fail(f"{filename} is missing Mixin metadata")
            mixin = json.loads(archive.read("cinemarr.mixins.json"))
            if "client.ChannelAccessor" not in mixin.get("client", []):
                fail(f"{filename} does not register the synchronized audio Channel accessor")
            # Forge 26.1.2 still embeds Mixin 0.8.7, whose highest declared
            # compatibility constant is JAVA_21. The classes themselves are
            # independently required to be Java 25 bytecode above.
            mixin_java = 21 if minecraft in ("26.1.2", "26.2") and loader == "forge" else java
            if mixin.get("compatibilityLevel") != f"JAVA_{mixin_java}":
                fail(f"{filename} has incorrect Mixin Java compatibility")
            nested_prefix = "META-INF/jars" if loader == "fabric" else "META-INF/jarjar"
            if loader != "fabric":
                flattened_video_runtime = {
                    "org/bytedeco/javacpp/Loader.class",
                    "org/bytedeco/ffmpeg/global/avcodec.class",
                    "org/bytedeco/javacpp/linux-x86_64/libjnijavacpp.so",
                    "org/bytedeco/javacpp/linux-arm64/libjnijavacpp.so",
                    "org/bytedeco/javacpp/windows-x86_64/jnijavacpp.dll",
                    "org/bytedeco/ffmpeg/linux-x86_64/libjniavutil.so",
                    "org/bytedeco/ffmpeg/linux-arm64/libjniavutil.so",
                    "org/bytedeco/ffmpeg/windows-x86_64/avcodec-62.dll",
                }
                if flattened_video_runtime - names:
                    fail(f"{filename} is missing flattened JavaCPP/FFmpeg runtime entries")
                if any(name.startswith("META-INF/jarjar/javacpp-")
                       or name.startswith("META-INF/jarjar/ffmpeg-") for name in names):
                    fail(f"{filename} contains module-path JavaCPP/FFmpeg JARs")
                if "module-info.class" in names or any(
                        name.startswith("META-INF/versions/") and name.endswith("/module-info.class")
                        for name in names):
                    fail(f"{filename} contains a shaded dependency module descriptor")
                jarjar_metadata = json.loads(archive.read("META-INF/jarjar/metadata.json"))
                core_identifiers = {
                    (entry.get("identifier", {}).get("group"), entry.get("identifier", {}).get("artifact"))
                    for entry in jarjar_metadata.get("jars", [])
                    if entry.get("identifier", {}).get("artifact") == "core"
                }
                if core_identifiers != {("stonytark.cinemarr", "core")}:
                    fail(f"{filename} does not isolate Cinemarr's private core coordinate")
            core_candidates = sorted(name for name in names if name.startswith(f"{nested_prefix}/")
                                     and name.endswith("core-1.0.0.jar"))
            if len(core_candidates) != 1:
                fail(f"{filename} must bundle exactly one shared core JAR, found {core_candidates}")
            for core_entry in (
                "stonytark/cinemarr/core/server/ChunkTransferPolicy.class",
                "stonytark/cinemarr/core/server/CoordinatorRuntime.class",
                "stonytark/cinemarr/core/server/GlobalPlaybackCoordinator.class",
                "stonytark/cinemarr/core/server/PlaybackStore.class",
                "stonytark/cinemarr/core/screen/QuickTvPreset.class",
            ):
                core_class = require_nested_class(archive, core_candidates[0], core_entry, filename)
                if class_major(core_class, f"{filename}:{core_entry}") != 52:
                    fail(f"{filename} shared core is not Java 8 bytecode")
            require_nested_class(archive, f"{nested_prefix}/jlayer-1.0.1.jar",
                                 "javazoom/jl/decoder/Decoder.class", filename)
            require_nested_class(archive, f"{nested_prefix}/jump3r-1.0.5.jar",
                                 "de/sciss/jump3r/mp3/Lame.class", filename)

        verify_no_deployment_secrets(archive, filename)


def verify_quick_tv_recipes(archive: zipfile.ZipFile, names: set[str], minecraft: str,
                            filename: str) -> None:
    if minecraft == "1.7.10":
        return
    directory = "recipes" if minecraft in ("1.20.1", "1.20.2") else "recipe"
    expected = {f"data/cinemarr/{directory}/quick_tv_{quick_id}.json" for quick_id in QUICK_TV_IDS}
    if expected - names:
        fail(f"{filename} is missing active Quick TV recipes: {sorted(expected - names)}")
    for entry in sorted(expected):
        recipe = json.loads(archive.read(entry))
        ingredients = recipe.get("key")
        result = recipe.get("result")
        if not isinstance(ingredients, dict) or not ingredients or not isinstance(result, dict):
            fail(f"{filename}:{entry} has an invalid shaped-recipe structure")
        if minecraft in ("26.1.2", "26.2"):
            if not all(isinstance(ingredient, str) for ingredient in ingredients.values()):
                fail(f"{filename}:{entry} does not use Minecraft 26.x string ingredients")
        elif not all(isinstance(ingredient, dict) and "item" in ingredient
                     for ingredient in ingredients.values()):
            fail(f"{filename}:{entry} does not use object-form ingredients")
        result_key = "item" if minecraft in ("1.20.1", "1.20.2") else "id"
        if not isinstance(result.get(result_key), str):
            fail(f"{filename}:{entry} does not use result.{result_key}")


def verify_tv_components(archive: zipfile.ZipFile, names: set[str], minecraft: str,
                         filename: str) -> None:
    assets = {
        *(f"assets/cinemarr/blockstates/{component}.json" for component in TV_BLOCK_IDS),
        *(f"assets/cinemarr/models/block/{component}.json" for component in TV_BLOCK_IDS),
        *(f"assets/cinemarr/models/item/{component}.json" for component in TV_COMPONENT_IDS),
    }
    if assets - names:
        fail(f"{filename} is missing TV component assets: {sorted(assets - names)}")

    translations = json.loads(archive.read("assets/cinemarr/lang/en_us.json"))
    translation_keys = {*(f"block.cinemarr.{component}" for component in TV_BLOCK_IDS),
                        "item.cinemarr.tv_remote"}
    missing_translations = {key for key in translation_keys
                            if not isinstance(translations.get(key), str) or not translations[key].strip()}
    if missing_translations:
        fail(f"{filename} is missing TV component translations: {sorted(missing_translations)}")

    registry_entries = (["stonytark/cinemarr/screen/LegacyBlocks.class"] if minecraft == "1.7.10" else [
        "stonytark/cinemarr/registry/CinemarrBlocks.class",
        "stonytark/cinemarr/registry/CinemarrItems.class",
    ])
    registry = b"".join(archive.read(entry) for entry in registry_entries)
    missing_ids = {component for component in TV_COMPONENT_IDS if component.encode() not in registry}
    if missing_ids:
        fail(f"{filename}:{registry_entries} is missing TV component registrations: {sorted(missing_ids)}")

    manager_entry = ("stonytark/cinemarr/server/LegacyVideoManager.class" if minecraft == "1.7.10"
                     else "stonytark/cinemarr/server/ServerVideoManager.class")
    manager = archive.read(manager_entry)
    if b"Paused by redstone receiver" not in manager or b"RedstoneControlPolicy" not in manager:
        fail(f"{filename}:{manager_entry} is missing rising-edge receiver control")

    if minecraft == "1.7.10":
        legacy_lang = archive.read("assets/cinemarr/lang/en_US.lang")
        if b"tile.cinemarr.redstone_receiver.name=" not in legacy_lang \
                or b"item.cinemarr.tv_remote.name=" not in legacy_lang:
            fail(f"{filename} is missing legacy receiver/remote translations")
        return

    if minecraft not in ("1.20.1", "1.20.2"):
        controller = archive.read("stonytark/cinemarr/screen/TvControllerBlock.class")
        if b"tvRemote" not in controller:
            fail(f"{filename} does not route TV Remote use through the controller")

    directory = "recipes" if minecraft in ("1.20.1", "1.20.2") else "recipe"
    expected = {f"data/cinemarr/{directory}/{component}.json" for component in TV_COMPONENT_IDS}
    if expected - names:
        fail(f"{filename} is missing TV component recipes: {sorted(expected - names)}")
    for entry in sorted(expected):
        recipe = json.loads(archive.read(entry))
        ingredients = recipe.get("key")
        result = recipe.get("result")
        if not isinstance(ingredients, dict) or not ingredients or not isinstance(result, dict):
            fail(f"{filename}:{entry} has an invalid shaped-recipe structure")
        if minecraft in ("26.1.2", "26.2"):
            if not all(isinstance(ingredient, str) for ingredient in ingredients.values()):
                fail(f"{filename}:{entry} does not use Minecraft 26.x string ingredients")
        elif not all(isinstance(ingredient, dict) and "item" in ingredient
                     for ingredient in ingredients.values()):
            fail(f"{filename}:{entry} does not use object-form ingredients")
        result_key = "item" if minecraft in ("1.20.1", "1.20.2") else "id"
        expected_result = "cinemarr:" + PurePosixPath(entry).stem
        if result.get(result_key) != expected_result:
            fail(f"{filename}:{entry} has result.{result_key}={result.get(result_key)!r}, expected {expected_result!r}")


def main() -> int:
    release_dir = Path(sys.argv[1] if len(sys.argv) > 1 else "build/releases").resolve()
    manifest_path = release_dir / "manifest.json"
    sums_path = release_dir / "SHA256SUMS"
    if not manifest_path.is_file() or not sums_path.is_file():
        fail(f"{release_dir} is missing manifest.json or SHA256SUMS")

    manifest = json.loads(manifest_path.read_text("utf-8"))
    if manifest.get("schemaVersion") != 2 or manifest.get("modId") != "cinemarr":
        fail("release manifest has incorrect schema or mod ID")
    if manifest.get("productVersion") != PRODUCT_VERSION or manifest.get("protocolVersion") != PROTOCOL_VERSION:
        fail("release manifest has incorrect product or protocol version")

    expected_names = {f"cinemarr-{PRODUCT_VERSION}+mc{mc}-{loader}.jar" for mc, loader, _, _ in TARGETS}
    actual_names = {path.name for path in release_dir.glob("*.jar")}
    if actual_names != expected_names:
        fail(f"release JAR set mismatch; missing={sorted(expected_names - actual_names)}, extra={sorted(actual_names - expected_names)}")

    entries = manifest.get("artifacts", [])
    if len(entries) != len(TARGETS) or {entry.get("filename") for entry in entries} != expected_names:
        fail("release manifest does not map exactly the 16 canonical artifacts")
    manifest_by_name = {entry["filename"]: entry for entry in entries}

    sums = {}
    for line in sums_path.read_text("utf-8").splitlines():
        match = re.fullmatch(r"([0-9a-f]{64})  (cinemarr-[^/]+\.jar)", line)
        if not match or match.group(2) in sums:
            fail(f"invalid SHA256SUMS line: {line!r}")
        sums[match.group(2)] = match.group(1)
    if set(sums) != expected_names:
        fail("SHA256SUMS does not cover exactly the 16 canonical artifacts")

    for minecraft, loader, java, major in TARGETS:
        filename = f"cinemarr-{PRODUCT_VERSION}+mc{minecraft}-{loader}.jar"
        path = release_dir / filename
        digest = sha256(path)
        entry = manifest_by_name[filename]
        expected_identity = (minecraft, loader, java)
        actual_identity = (entry.get("minecraftVersion"), entry.get("loader"), entry.get("javaVersion"))
        if actual_identity != expected_identity:
            fail(f"{filename} manifest identity is {actual_identity}, expected {expected_identity}")
        expected_loaders = ["fabric", "quilt"] if loader == "fabric" else [loader]
        if entry.get("compatibleLoaders") != expected_loaders:
            fail(f"{filename} has incorrect compatible loader declaration")
        if entry.get("productVersion") != PRODUCT_VERSION or entry.get("sha256") != digest or sums[filename] != digest:
            fail(f"{filename} manifest/checksum does not match artifact bytes")
        if not isinstance(entry.get("dependencies"), dict) or not entry["dependencies"]:
            fail(f"{filename} has no locked dependency versions in the manifest")
        if loader == "fabric" \
                and entry["dependencies"].get("quilt-loader") != "0.30.0":
            fail(f"{filename} does not record the certified Quilt Loader version")
        verify_jar(path, minecraft, loader, java, major)

    print(f"Inspected 16 Cinemarr release artifacts in {release_dir}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, KeyError, ValueError, zipfile.BadZipFile, json.JSONDecodeError) as error:
        print(f"release artifact inspection failed: {error}", file=sys.stderr)
        raise SystemExit(1)
