#!/usr/bin/env python3
"""Unit tests for the manifest-derived Cinemarr release matrix."""

from __future__ import annotations

import copy
import importlib.util
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("target-matrix.py")
ROOT = SCRIPT.resolve().parents[1]
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
        self.assertEqual("platforms/fabric/build/libs/cinemarr-1.0.0+mc1.20.1-fabric.jar", matrix["artifact"])

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
        manifest["artifacts"][0]["runtime"]["name"] = "1.20.1-forge"
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

    def test_runtime_identity_and_quilt_version_must_match_the_artifact(self) -> None:
        for field, wrong in (("runtime", "1.20.1-nonexistent"),
                             ("runtime", "1.20.2-fabric"),
                             ("quiltRuntime", "1.19.4-quilt"),
                             ("quiltRuntime", "1.20.1-forge")):
            with self.subTest(field=field, wrong=wrong):
                manifest = fixture(); manifest["artifacts"][0][field]["name"] = wrong
                with self.assertRaisesRegex(SystemExit, "runtime identity"):
                    target_matrix.validate(manifest)

    def test_repository_rejects_java_settings_that_disagree_with_actual_builds(self) -> None:
        for index, values in ((0, {"runtimeJava": 25, "bytecodeJava": 25}),
                              (1, {"runtimeJava": 21}),
                              (10, {"buildJava": 21})):
            with self.subTest(index=index, values=values):
                manifest = target_matrix.load_manifest(ROOT / "gradle/targets.json")
                manifest["artifacts"][index].update(values)
                with self.assertRaisesRegex(SystemExit, "Java|bytecode"):
                    target_matrix.validate(manifest)
                    target_matrix.verify_repository(manifest, ROOT)

    def test_repository_rejects_missing_default_and_overridden_launch_tasks(self) -> None:
        for field in ("clientTask", "serverTask"):
            for overridden in (False, True):
                with self.subTest(field=field, overridden=overridden):
                    manifest = target_matrix.load_manifest(ROOT / "gradle/targets.json")
                    destination = manifest["artifacts"][0]["runtime"] if overridden else manifest["runtimeDefaults"]
                    destination[field] = "doesNotExist"
                    with self.assertRaisesRegex(SystemExit, "runtime task"):
                        target_matrix.validate(manifest)
                        target_matrix.verify_repository(manifest, ROOT)

    def test_repository_rejects_manifest_protocol_drift(self) -> None:
        manifest = target_matrix.load_manifest(ROOT / "gradle/targets.json")
        manifest["protocolVersion"] -= 1
        with self.assertRaisesRegex(SystemExit, "protocol"):
            target_matrix.verify_repository(manifest, ROOT)

    def test_repository_rejects_existing_verification_task_for_a_different_project(self) -> None:
        for name, wrong_task in (("1.20.1-fabric", "verifyForge1201"),
                                 ("1.20.1-forge", "verifyForge1202"),
                                 ("26.2-fabric", "verifyNeoForge262")):
            with self.subTest(name=name, wrong_task=wrong_task):
                manifest = target_matrix.load_manifest(ROOT / "gradle/targets.json")
                next(entry for entry in manifest["artifacts"] if entry["name"] == name)["task"] = wrong_task
                with self.assertRaisesRegex(SystemExit, "verification task project"):
                    target_matrix.verify_repository(manifest, ROOT)

    def test_repository_rejects_quilt_override_as_canonical_fabric_build(self) -> None:
        manifest = target_matrix.load_manifest(ROOT / "gradle/targets.json")
        next(entry for entry in manifest["artifacts"] if entry["name"] == "1.20.1-fabric")["task"] = "verifyQuilt1201"
        with self.assertRaisesRegex(SystemExit, "canonical release gate"):
            target_matrix.verify_repository(manifest, ROOT)

    def test_repository_rejects_isolated_task_for_root_project(self) -> None:
        manifest = target_matrix.load_manifest(ROOT / "gradle/targets.json")
        next(entry for entry in manifest["artifacts"] if entry["path"] == ".")["task"] = "verifyForge1211"
        with self.assertRaisesRegex(SystemExit, "root verification task"):
            target_matrix.verify_repository(manifest, ROOT)

    def test_current_repository_and_complete_matrix_pass(self) -> None:
        manifest = target_matrix.load_manifest(ROOT / "gradle/targets.json")
        target_matrix.verify_repository(manifest, ROOT)
        self.assertEqual(16, len(target_matrix.artifact_matrix(manifest)["include"]))
        self.assertEqual(21, len(target_matrix.runtime_entries(manifest)))

    def test_missing_project_and_verification_task_fail(self) -> None:
        for values, message in (({"path": "platforms/does-not-exist"}, "directory is missing"),
                                ({"task": "verifyDoesNotExist"}, "task is missing")):
            manifest = target_matrix.load_manifest(ROOT / "gradle/targets.json")
            manifest["artifacts"][0].update(values)
            with self.assertRaisesRegex(SystemExit, message):
                target_matrix.verify_repository(manifest, ROOT)

    def test_duplicate_artifact_filename_is_rejected_independently(self) -> None:
        manifest = fixture()
        duplicate = copy.deepcopy(manifest["artifacts"][0])
        duplicate.update(name="1.20.1-forge", loader="forge")
        manifest["artifacts"].append(duplicate)
        with self.assertRaisesRegex(SystemExit, "duplicate artifact filename"):
            target_matrix.validate(manifest)

    def test_compatibility_rows_are_derived_instead_of_a_second_target_matrix(self) -> None:
        manifest = fixture()
        self.assertEqual(["| 1.20.1 | Fabric, Quilt via Fabric artifact | 17 |"],
                         target_matrix.compatibility_rows(manifest))
        manifest["artifacts"][0].pop("quiltRuntime")
        manifest["artifacts"][0]["minecraft"] = "fixture-version"
        manifest["artifacts"][0]["runtimeJava"] = 25
        self.assertEqual(["| fixture-version | Fabric | 25 |"],
                         target_matrix.compatibility_rows(manifest))


if __name__ == "__main__":
    unittest.main()
