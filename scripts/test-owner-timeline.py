#!/usr/bin/env python3
"""Owner-stream assertions stay strict for either fixture stream type; no GUI."""
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('owner_timeline', Path(__file__).with_name('observe-owner-timeline.py'))
owner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(owner)


class StreamChangeTests(unittest.TestCase):
    def test_owner_actions_follow_timeline_revision_not_media_revision(self):
        text = ('Acceptance video session: controller=1 session=tv generation=3 status=PAUSED '
                'item=movie positionMs=12000 canControl=true streams=2 audio=1 subtitle=-1 '
                'timeline=party timelineGeneration=8 durationMs=300000 message=Paused')
        self.assertEqual(8, owner.states(text)[0]['generation'])
        self.assertEqual(3, owner.states(text.replace(' timeline=party timelineGeneration=8', ''))[0]['generation'])

    def setUp(self):
        self.before = dict(generation=3, status='PAUSED', positionMs=84388, audio=205039, subtitle=-1)
        self.subtitle = dict(self.before, generation=4, subtitle=205040)
        self.audio = dict(self.before, generation=4, audio=205041)

    def test_subtitle_widget_and_authoritative_change(self):
        self.assertEqual((350, 'subtitle', 'audio'), owner.stream_control('subtitle'))
        owner.verify_stream_change(self.before, self.subtitle, 'subtitle', 84388)

    def test_audio_widget_and_authoritative_change(self):
        self.assertEqual((125, 'audio', 'subtitle'), owner.stream_control('audio'))
        owner.verify_stream_change(self.before, self.audio, 'audio', 84388)

    def test_wrapped_audio_cycle_may_retain_selection_with_new_generation(self):
        owner.verify_stream_change(self.before, dict(self.before, generation=4), 'audio', 84388)

    def test_stream_change_may_retain_generation_when_selection_changes(self):
        owner.verify_stream_change(self.before, dict(self.before, audio=205041), 'audio', 84388)

    def test_wrong_stream_change_rejected(self):
        with self.assertRaises(RuntimeError): owner.verify_stream_change(self.before, self.subtitle, 'audio', 84388)

    def test_paused_cursor_must_be_exact(self):
        with self.assertRaises(RuntimeError):
            owner.verify_stream_change(self.before, dict(self.subtitle, positionMs=84389), 'subtitle', 84388)

    def test_unrequested_stream_must_be_preserved(self):
        with self.assertRaises(RuntimeError):
            owner.verify_stream_change(self.before, dict(self.subtitle, audio=999), 'subtitle', 84388)

    def test_stale_generation_or_resumed_state_rejected(self):
        for change in (dict(status='PLAYING'),):
            with self.subTest(change=change), self.assertRaises(RuntimeError):
                owner.verify_stream_change(self.before, dict(self.subtitle, **change), 'subtitle', 84388)

    def test_playing_cursor_uses_bounded_advancing_clock(self):
        before = dict(self.before, status='PLAYING')
        after = dict(self.subtitle, status='PLAYING', positionMs=88388)
        owner.verify_stream_change(before, after, 'subtitle', 88388, 5000)
        with self.assertRaises(RuntimeError):
            owner.verify_stream_change(before, dict(after, positionMs=93389), 'subtitle', 88388, 5000)

    def test_subtitles_can_be_turned_off_again(self):
        owner.verify_stream_change(self.subtitle, dict(self.subtitle, generation=5, subtitle=-1), 'subtitle', 84388)

    def test_unknown_stream_type_rejected(self):
        with self.assertRaises(ValueError): owner.stream_control('video')


if __name__ == '__main__': unittest.main()
