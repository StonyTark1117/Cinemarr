#!/usr/bin/env python3
"""Mutation coverage for cross-family playback-publication integration guards."""
import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("layout", ROOT / "scripts/check-source-layout.py")
layout = importlib.util.module_from_spec(spec)
spec.loader.exec_module(layout)
SOURCES = (
    "src/main/java/stonytark/cinemarr/server/ServerVideoManager.java",
    "platforms/mc26/common/src/main/java/stonytark/cinemarr/server/ServerVideoManager.java",
    "platforms/mc1.7.10/forge/src/main/java/stonytark/cinemarr/server/LegacyVideoManager.java",
)


class PlaybackPublicationLayoutTest(unittest.TestCase):
    def test_all_families_use_current_playback_publication(self):
        for path in SOURCES:
            with self.subTest(path=path):
                layout.verify_playback_publication((ROOT / path).read_text(), path)

    def test_each_completion_rejects_discarding_the_returned_snapshot(self):
        needle = "state=recordPlayback(prepared);if(state==null)return;"
        for path in SOURCES:
            source = (ROOT / path).read_text()
            self.assertEqual(5, source.count(needle))
            for index in range(5):
                pieces = source.split(needle)
                mutated = needle.join(pieces[:index + 1]) + "if(recordPlayback(prepared)==null)return;" + needle.join(pieces[index + 1:])
                with self.subTest(path=path, completion=index), self.assertRaises(SystemExit):
                    layout.verify_playback_publication(mutated, path)

    def test_old_generation_guard_is_rejected(self):
        for path in SOURCES:
            source = (ROOT / path).read_text().replace("applyPlaybackMetadataIfCurrent", "applyIfCurrent")
            with self.subTest(path=path), self.assertRaises(SystemExit):
                layout.verify_playback_publication(source, path)

    def test_stale_restore_revision_is_rejected(self):
        for path in SOURCES:
            source = (ROOT / path).read_text().replace("restored.playbackGeneration()", "tuned.generation()", 1)
            with self.subTest(path=path), self.assertRaises(SystemExit):
                layout.verify_playback_publication(source, path)

    def test_suspended_playing_label_is_rejected(self):
        for path in SOURCES:
            source = (ROOT / path).read_text().replace("state.playbackMessage()", 'state.paused() ? "Paused" : "Playing"', 1)
            with self.subTest(path=path), self.assertRaises(SystemExit):
                layout.verify_playback_publication(source, path)

    def test_contextual_queue_and_episode_feedback_cannot_be_replaced_by_generic_playing(self):
        for path in SOURCES:
            for message in ("Playing next queued video", "Continuing with next episode"):
                source = (ROOT / path).read_text()
                call = 'state.playbackMessage("' + message + '")'
                with self.subTest(path=path, message=message):
                    self.assertEqual(1, source.count(call))
                    with self.assertRaises(SystemExit):
                        layout.verify_playback_publication(source.replace(call, "state.playbackMessage()"), path)


if __name__ == "__main__":
    unittest.main()
