#!/usr/bin/env python3
"""Validate Cinemarr's target manifest and derive every release/runtime matrix."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Any


def fail(message: str) -> None:
    raise SystemExit(message)


def load_manifest(path: Path) -> dict[str, Any]:
    manifest = json.loads(path.read_text("utf-8"))
    if manifest.get("schemaVersion") != 1:
        fail("unsupported target-manifest schema")
    if manifest.get("product") != "Cinemarr":
        fail("target manifest has the wrong product")
    artifacts = manifest.get("artifacts")
    if not isinstance(artifacts, list) or not artifacts:
        fail("target manifest contains no artifacts")
    validate(manifest)
    return manifest


def validate(manifest: dict[str, Any]) -> None:
    defaults = manifest.get("runtimeDefaults")
    if not isinstance(defaults, dict) or set(defaults) != {
            "clientTask", "serverTask", "disableConfigurationCache"}:
        fail("runtimeDefaults must define clientTask, serverTask, and disableConfigurationCache")
    validate_runtime_settings(defaults, "runtimeDefaults")
    names: set[str] = set()
    files: set[str] = set()
    runtimes: set[str] = set()
    ports: set[int] = set()
    allowed_statuses = {"prerelease", "builds", "launches", "runtime-certified"}
    for entry in manifest["artifacts"]:
        required = {"name", "minecraft", "loader", "status", "buildJava", "runtimeJava", "bytecodeJava",
                    "task", "path", "artifact", "runtime", "gameTests"}
        missing = required - entry.keys()
        if missing:
            fail(f"target is missing fields: {sorted(missing)}")
        name = entry["name"]
        if name != f"{entry['minecraft']}-{entry['loader']}":
            fail(f"target identity does not match minecraft/loader: {name}")
        if name in names:
            fail(f"duplicate target {name}")
        names.add(name)
        if entry["artifact"] in files:
            fail(f"duplicate artifact filename {entry['artifact']}")
        files.add(entry["artifact"])
        if entry["status"] not in allowed_statuses:
            fail(f"invalid certification status for {name}")
        if entry["buildJava"] not in {21, 26} or entry["runtimeJava"] not in {8, 17, 21, 25}:
            fail(f"unsupported Java mapping for {name}")
        if entry["bytecodeJava"] not in {8, 17, 21, 25} or entry["bytecodeJava"] > entry["runtimeJava"]:
            fail(f"unsupported bytecode mapping for {name}")
        if not isinstance(entry["gameTests"], bool):
            fail(f"gameTests must be boolean for {name}")
        for value in (name, entry["path"], entry["artifact"], entry["task"]):
            if not isinstance(value, str) or not value or "|" in value or "\n" in value:
                fail(f"unsafe or empty target field in {name}")
        add_runtime(entry["runtime"], defaults, runtimes, ports)
        if entry["runtime"]["name"] != name:
            fail(f"primary runtime identity does not match artifact: {name}")
        if entry.get("quiltRuntime") is not None:
            if entry["loader"] != "fabric":
                fail(f"non-Fabric target declares Quilt compatibility: {name}")
            add_runtime(entry["quiltRuntime"], defaults, runtimes, ports)
            if entry["quiltRuntime"]["name"] != f"{entry['minecraft']}-quilt":
                fail(f"Quilt runtime identity does not match artifact version: {name}")
    expected = manifest.get("expected", {})
    if expected.get("artifacts") != len(names):
        fail("expected artifact count does not match generated targets")
    if expected.get("runtimes") != len(runtimes):
        fail("expected runtime count does not match generated runtimes")
    if sum(bool(entry.get("gameTests")) for entry in manifest["artifacts"]) != 1:
        fail("exactly one artifact must own the GameTest gate")


def add_runtime(entry: Any, defaults: dict[str, Any], names: set[str], ports: set[int]) -> None:
    allowed = {"name", "port", "clientTask", "serverTask", "disableConfigurationCache"}
    if not isinstance(entry, dict) or not {"name", "port"}.issubset(entry) or not set(entry).issubset(allowed):
        fail("runtime entries require name/port and optional runtime-setting overrides")
    validate_runtime_settings({**defaults, **entry}, entry.get("name", "runtime"))
    if not isinstance(entry["name"], str) or "|" in entry["name"] or "\n" in entry["name"]:
        fail("runtime name is unsafe")
    if entry["name"] in names:
        fail(f"duplicate runtime {entry['name']}")
    if not isinstance(entry["port"], int) or not 1024 <= entry["port"] <= 64535:
        fail(f"invalid runtime port for {entry['name']}")
    if entry["port"] in ports:
        fail(f"duplicate runtime port {entry['port']}")
    names.add(entry["name"])
    ports.add(entry["port"])


def validate_runtime_settings(entry: dict[str, Any], name: str) -> None:
    for key in ("clientTask", "serverTask"):
        value = entry.get(key)
        if not isinstance(value, str) or not value or "|" in value or "\n" in value:
            fail(f"unsafe {key} for {name}")
    if not isinstance(entry.get("disableConfigurationCache"), bool):
        fail(f"configuration-cache policy must be boolean for {name}")


def artifact_path(entry: dict[str, Any]) -> str:
    prefix = "" if entry["path"] == "." else f"{entry['path']}/"
    return f"{prefix}build/libs/{entry['artifact']}"


def artifact_matrix(manifest: dict[str, Any]) -> dict[str, Any]:
    return {"include": [
        {
            "name": entry["name"],
            "task": entry["task"],
            "artifact": artifact_path(entry),
            "runtime": entry["runtime"]["name"],
            "quilt": entry.get("quiltRuntime", {}).get("name", ""),
            "game_tests": entry["gameTests"],
        }
        for entry in manifest["artifacts"]
    ]}


def runtime_entries(manifest: dict[str, Any]) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    defaults = manifest["runtimeDefaults"]
    for entry in manifest["artifacts"]:
        runtime = {**defaults, **entry["runtime"]}
        result.append({"name": runtime["name"], "path": entry["path"],
                       "buildJava": entry["buildJava"], "port": runtime["port"],
                       "clientTask": runtime["clientTask"], "serverTask": runtime["serverTask"],
                       "disableConfigurationCache": runtime["disableConfigurationCache"]})
        quilt = entry.get("quiltRuntime")
        if quilt:
            runtime = {**defaults, **quilt}
            result.append({"name": runtime["name"], "path": entry["path"],
                           "buildJava": entry["buildJava"], "port": runtime["port"],
                           "clientTask": runtime["clientTask"], "serverTask": runtime["serverTask"],
                           "disableConfigurationCache": runtime["disableConfigurationCache"]})
    return result


def verify_repository(manifest: dict[str, Any], root: Path) -> None:
    root_build = (root / "build.gradle").read_text("utf-8")
    protocol = (root / "core/src/main/java/stonytark/cinemarr/core/protocol/ProtocolLimits.java").read_text("utf-8")
    wire_version = re.search(r"public static final int VERSION\s*=\s*(\d+)\s*;", protocol)
    if wire_version is None or manifest.get("protocolVersion") != int(wire_version[1]):
        fail("manifest protocol version disagrees with ProtocolLimits.VERSION")
    for entry in manifest["artifacts"]:
        target = root if entry["path"] == "." else root / entry["path"]
        if not target.is_dir():
            fail(f"target directory is missing: {entry['path']}")
        build = root / "build.gradle" if entry["path"] == "." else target / "build.gradle"
        if not build.is_file():
            fail(f"target build file is missing: {build.relative_to(root)}")
        if entry["task"] != "verifyRelease" and f"tasks.register('{entry['task']}'" not in root_build:
            fail(f"root verification task is missing: {entry['task']}")
        build_text = build.read_text("utf-8")
        # Verify actual compiler declarations, not a second version/Java matrix.
        bytecode = re.search(r"targetCompatibility\s*=\s*JavaVersion.VERSION_(?:1_)?(\d+)", build_text)
        if bytecode is None:
            bytecode = re.search(r"toolchain.languageVersion\s*=\s*JavaLanguageVersion.of\((\d+)\)", build_text)
        if bytecode is None or entry["bytecodeJava"] != int(bytecode[1]):
            fail(f"manifest bytecode Java disagrees with target build: {entry['name']}")
        if entry["runtimeJava"] != int(bytecode[1]):
            fail(f"manifest runtime Java disagrees with target's pinned Java baseline: {entry['name']}")
        build_java = re.search(r"java.toolchain.languageVersion\s*=\s*JavaLanguageVersion.of\((\d+)\)", root_build)
        if build_java is None:
            fail("cannot verify root build Java declaration")
        expected_build_java = int(build_java[1])
        if entry["path"] != ".":
            task = re.search(r"(?ms)tasks.register\('" + re.escape(entry["task"])
                             + r"', Exec\) \{\n(.*?)^\}", root_build)
            if task is None:
                fail(f"cannot verify isolated build Java: {entry['name']}")
            project = re.search(r"workingDir\(layout\.projectDirectory\.dir\(['\"]([^'\"]+)['\"]\)\)", task[1])
            if project is None or project[1] != entry["path"]:
                fail(f"verification task project disagrees with target: {entry['name']}")
            if not re.search(r"commandLine\(['\"]\./gradlew['\"],\s*['\"]verifyRelease['\"]", task[1]):
                fail(f"verification task does not run the canonical release gate: {entry['name']}")
            override = re.search(r"environment\s+'JAVA_HOME',\s+java(\d+)Home", task[1])
            if "JAVA_HOME" in task[1] and override is None:
                fail(f"unrecognized isolated build Java override: {entry['name']}")
            if override is not None:
                expected_build_java = int(override[1])
        if entry["buildJava"] != expected_build_java:
            fail(f"manifest build Java disagrees with verification task: {entry['name']}")
        if entry["path"] == "." and entry["task"] != "verifyRelease":
            fail(f"root target must use the root verification task: {entry['name']}")
        for runtime in (entry["runtime"], entry.get("quiltRuntime")):
            if runtime is None:
                continue
            settings = {**manifest["runtimeDefaults"], **runtime}
            # All maintained loader plugins expose these conventional launch
            # tasks. Custom task names need an explicit declaration in their
            # target build; a typo must not silently enter generated CI routing.
            for field, conventional in (("clientTask", "runClient"), ("serverTask", "runServer")):
                launch_task = settings[field]
                declared = re.search(r"tasks\.(?:register|named)\(['\"]"
                                     + re.escape(launch_task) + r"['\"]", build_text)
                if launch_task != conventional and declared is None:
                    fail(f"runtime task is missing for {runtime['name']}: {launch_task}")
        expected_artifact = f"cinemarr-{manifest['productVersion']}+mc{entry['minecraft']}-{entry['loader']}.jar"
        if entry["artifact"] != expected_artifact:
            fail(f"artifact filename does not match the target identity: {entry['name']}")
    compatibility = (root / "docs/COMPATIBILITY.md").read_text("utf-8")
    table_rows = [line for line in compatibility.splitlines() if re.match(r"^\| \d", line)]
    if table_rows != compatibility_rows(manifest):
        fail("compatibility documentation is stale; derive rows from the target manifest")
    for document in (root / "README.md", root / "docs/1.0_RELEASE_HARDENING_PLAN.md",
                     root / "docs/COMPATIBILITY.md"):
        text = document.read_text("utf-8")
        if "16-artifact" not in text or "21-runtime" not in text:
            fail(f"exact release counts are missing from {document.relative_to(root)}")


def compatibility_rows(manifest: dict[str, Any]) -> list[str]:
    grouped: dict[str, list[dict[str, Any]]] = {}
    labels = {"fabric": "Fabric", "forge": "Forge", "neoforge": "NeoForge"}
    for entry in manifest["artifacts"]:
        grouped.setdefault(entry["minecraft"], []).append(entry)
    rows = []
    for version, targets in grouped.items():
        runtimes = {entry["runtimeJava"] for entry in targets}
        if len(runtimes) != 1:
            fail(f"inconsistent runtime Java baseline for Minecraft {version}")
        loaders = []
        for entry in targets:
            loaders.append(labels[entry["loader"]])
            if entry.get("quiltRuntime"):
                loaders.append("Quilt via Fabric artifact")
        rows.append(f"| {version} | {', '.join(loaders)} | {next(iter(runtimes))} |")
    return rows


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=("artifact-matrix", "gate-lines", "canonical-json", "summary", "verify"))
    parser.add_argument("manifest", nargs="?", type=Path, default=Path("gradle/targets.json"))
    args = parser.parse_args()
    manifest = load_manifest(args.manifest)
    if args.mode == "artifact-matrix":
        print(json.dumps(artifact_matrix(manifest), separators=(",", ":")))
    elif args.mode == "gate-lines":
        for entry in runtime_entries(manifest):
            print("|".join(map(str, (entry["name"], entry["path"], entry["buildJava"], entry["port"],
                                      entry["clientTask"], entry["serverTask"],
                                      str(entry["disableConfigurationCache"]).lower()))))
    elif args.mode == "canonical-json":
        print(json.dumps([{"minecraft": entry["minecraft"], "java": entry["runtimeJava"],
                           "bytecodeJava": entry["bytecodeJava"], "loader": entry["loader"],
                           "path": artifact_path(entry)}
                          for entry in manifest["artifacts"]], separators=(",", ":")))
    elif args.mode == "verify":
        verify_repository(manifest, args.manifest.resolve().parents[1])
        print("target manifest repository verification passed")
    else:
        print(json.dumps({"artifacts": len(manifest["artifacts"]),
                          "runtimes": len(runtime_entries(manifest)),
                          "quiltRuntimes": sum("quiltRuntime" in entry for entry in manifest["artifacts"])},
                         sort_keys=True))


if __name__ == "__main__":
    main()
