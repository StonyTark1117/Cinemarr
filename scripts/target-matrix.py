#!/usr/bin/env python3
"""Validate Cinemarr's target manifest and derive every release/runtime matrix."""

from __future__ import annotations

import argparse
import json
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
        if entry.get("quiltRuntime") is not None:
            if entry["loader"] != "fabric":
                fail(f"non-Fabric target declares Quilt compatibility: {name}")
            add_runtime(entry["quiltRuntime"], defaults, runtimes, ports)
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
    for entry in manifest["artifacts"]:
        target = root if entry["path"] == "." else root / entry["path"]
        if not target.is_dir():
            fail(f"target directory is missing: {entry['path']}")
        build = root / "build.gradle" if entry["path"] == "." else target / "build.gradle"
        if not build.is_file():
            fail(f"target build file is missing: {build.relative_to(root)}")
        if entry["task"] != "verifyRelease" and f"tasks.register('{entry['task']}'" not in root_build:
            fail(f"root verification task is missing: {entry['task']}")
        expected_artifact = f"cinemarr-{manifest['productVersion']}+mc{entry['minecraft']}-{entry['loader']}.jar"
        if entry["artifact"] != expected_artifact:
            fail(f"artifact filename does not match the target identity: {entry['name']}")
    compatibility_rows = {
        "1.7.10": "| 1.7.10 | Forge | 8 |",
        "1.20.1": "| 1.20.1 | Fabric, Quilt via Fabric artifact, Forge, NeoForge | 17 |",
        "1.20.2": "| 1.20.2 | Fabric, Quilt via Fabric artifact, Forge, NeoForge | 17 |",
        "1.21.1": "| 1.21.1 | Fabric, Quilt via Fabric artifact, Forge, NeoForge | 21 |",
        "26.1.2": "| 26.1.2 | Fabric, Quilt via Fabric artifact, Forge, NeoForge | 25 |",
        "26.2": "| 26.2 | Fabric, Quilt via Fabric artifact, Forge, NeoForge | 25 |",
    }
    compatibility = (root / "docs/COMPATIBILITY.md").read_text("utf-8")
    for version in sorted({entry["minecraft"] for entry in manifest["artifacts"]}):
        if compatibility_rows.get(version) not in compatibility:
            fail(f"compatibility documentation is stale for Minecraft {version}")
    for document in (root / "README.md", root / "docs/1.0_RELEASE_HARDENING_PLAN.md",
                     root / "docs/COMPATIBILITY.md"):
        text = document.read_text("utf-8")
        if "16-artifact" not in text or "21-runtime" not in text:
            fail(f"exact release counts are missing from {document.relative_to(root)}")


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
