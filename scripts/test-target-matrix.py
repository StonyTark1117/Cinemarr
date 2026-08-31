#!/usr/bin/env python3
"""Unit tests for the manifest-derived Cinemarr release matrix."""

from __future__ import annotations

import copy
import importlib.util
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("target-matrix.py")
SPEC = importlib.util.spec_from_file_location("target_matrix", SCRIPT)
assert SPEC and SPEC.loader
target_matrix = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(target_matrix)


def fixture() -> dict:
    return {
        "schemaVersion": 1,
        "product": "Cinemarr",
        "productVersion": "1.0.0",
        "protocolVersion": 10,
        "runtimeDefaults": {"clientTask": "runClient", "serverTask": "runServer",
                            "disableConfigurationCache": False},
        "expected": {"artifacts": 1, "runtimes": 2},
        "artifacts": [{
            "name": "1.20.1-fabric", "minecraft": "1.20.1", "loader": "fabric",
            "status": "prerelease", "buildJava": 21, "runtimeJava": 17, "bytecodeJava": 17,
            "task": "verifyFabric1201", "path": "platforms/fabric",
            "artifact": "cinemarr-1.0.0+mc1.20.1-fabric.jar",
            "runtime": {"name": "1.20.1-fabric", "port": 25571},
            "quiltRuntime": {"name": "1.20.1-quilt", "port": 25648},
            "gameTests": True,
        }],
    }


class TargetMatrixTests(unittest.TestCase):
    def test_derives_artifact_and_quilt_runtime(self) -> None:
        manifest = fixture(); target_matrix.validate(manifest)
        matrix = target_matrix.artifact_matrix(manifest)["include"][0]
        self.assertEqual("1.20.1-quilt", matrix["quilt"])
        self.assertEqual(2, len(target_matrix.runtime_entries(manifest)))
        self.assertEqual("runClient", target_matrix.runtime_entries(manifest)[0]["clientTask"])
        self.assertEqual("platforms/fabric/build/libs/cinemarr-*+mc1.20.1-fabric.jar", matrix["artifact"])

    def test_duplicate_target_artifact_runtime_and_port_fail_closed(self) -> None:
        for mutation, message in (
            (lambda value: value["artifacts"].append(copy.deepcopy(value["artifacts"][0])), "duplicate target"),
            (lambda value: value["artifacts"][0].update(quiltRuntime={"name":"1.20.1-fabric","port":25648}), "duplicate runtime"),
            (lambda value: value["artifacts"][0].update(quiltRuntime={"name":"1.20.1-quilt","port":25571}), "duplicate runtime port"),
        ):
            manifest = fixture(); mutation(manifest); manifest["expected"] = {"artifacts": len(manifest["artifacts"]), "runtimes": 2}
            with self.assertRaisesRegex(SystemExit, message): target_matrix.validate(manifest)

    def test_non_fabric_target_cannot_claim_quilt(self) -> None:
        manifest = fixture(); manifest["artifacts"][0]["loader"] = "forge"; manifest["artifacts"][0]["name"] = "1.20.1-forge"
        with self.assertRaisesRegex(SystemExit, "non-Fabric"): target_matrix.validate(manifest)

    def test_expected_counts_and_single_gametest_owner_are_enforced(self) -> None:
        manifest = fixture(); manifest["expected"]["runtimes"] = 3
        with self.assertRaisesRegex(SystemExit, "runtime count"): target_matrix.validate(manifest)
        manifest = fixture(); manifest["artifacts"][0]["gameTests"] = False
        with self.assertRaisesRegex(SystemExit, "exactly one"): target_matrix.validate(manifest)

    def test_bytecode_and_runtime_configuration_policy_are_validated(self) -> None:
        manifest = fixture(); manifest["artifacts"][0]["bytecodeJava"] = 21
        with self.assertRaisesRegex(SystemExit, "bytecode"): target_matrix.validate(manifest)
        manifest = fixture(); manifest["artifacts"][0]["runtime"]["disableConfigurationCache"] = "yes"
        with self.assertRaisesRegex(SystemExit, "configuration-cache"): target_matrix.validate(manifest)


if __name__ == "__main__":
    unittest.main()
