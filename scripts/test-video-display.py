#!/usr/bin/env python3
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('display', Path(__file__).with_name('observe-video-display.py'))
display = importlib.util.module_from_spec(spec)
spec.loader.exec_module(display)


def line(tv, owner=True, stream=None, generation=3):
    return (f'Acceptance TV display: controller={tv} television=tv{tv} timeline=party timelineGeneration=7 '
            f'stream={stream or "stream" + str(tv)} streamGeneration={generation} status=PLAYING owner={str(owner).lower()} '
            'origin=CUSTOM revision=2 layout=FIT mapping=DETAILED requested=144p effective=160x90 screen=4x4\n')


class DisplayTests(unittest.TestCase):
    def test_three_tvs_require_separate_streams_and_same_tv_viewers_share_identity(self):
        leader = display.states(''.join(line(i) for i in range(3)))
        follower = display.states(''.join(line(i, False) for i in range(3)))
        self.assertTrue(display.matching(leader, follower, 'PLAYING'))
        follower['tv1']['stream'] = 'other'
        self.assertFalse(display.matching(leader, follower, 'PLAYING'))
        leader['tv1']['stream'] = follower['tv1']['stream'] = 'stream0'
        self.assertFalse(display.matching(leader, follower, 'PLAYING'))

    def test_matching_requires_all_current_settings_and_shared_timeline(self):
        for field, value in [('revision', 4), ('mapping', 'ONE_PIXEL_PER_BLOCK'), ('timelineGeneration', 9), ('status', 'PAUSED')]:
            leader = display.states(''.join(line(i) for i in range(3)))
            follower = display.states(''.join(line(i, False) for i in range(3)))
            follower['tv1'][field] = value
            self.assertFalse(display.matching(leader, follower, 'PLAYING'), field)

    def test_pause_requires_every_tv_media_retirement_not_just_shared_clock(self):
        before = display.states(''.join(line(i) for i in range(3)))
        transitional = display.states(''.join(line(i, generation=4 if i != 2 else 3) for i in range(3)))
        self.assertFalse(display.streams_advanced(before, transitional))
        transitional['tv2']['streamGeneration'] = 4
        self.assertTrue(display.streams_advanced(before, transitional))
        transitional['tv2']['stream'] = 'different-tv'
        self.assertFalse(display.streams_advanced(before, transitional))

    def test_local_change_cannot_restart_or_revise_sibling(self):
        before = display.states(''.join(line(i) for i in range(3)))
        after = display.states(''.join(line(i, generation=4 if i == 0 else 3) for i in range(3)))
        self.assertTrue(display.unchanged_siblings(before, after, 'tv0'))
        after['tv2']['streamGeneration'] += 1
        self.assertFalse(display.unchanged_siblings(before, after, 'tv0'))

    def test_render_receipt_requires_current_revision_and_exact_mapping_raster(self):
        current = display.states(line(1))
        text = ('Acceptance video rendered: television=tv1 frameSha256=' + 'a' * 64 +
                ' ptsUs=123 rectangles=1 revision=2 raster=160x90 decoded=160x90')
        receipts = display.rendered(text)
        frames = display.decoded('Acceptance video frame: session=stream1 generation=3 ptsUs=123 sha256=' + 'a' * 64 + ' dimensions=160x90')
        self.assertTrue(display.render_matches(current, receipts, frames))
        current['tv1']['mapping'] = 'ONE_PIXEL_PER_BLOCK'
        self.assertFalse(display.render_matches(current, receipts, frames))
        receipts = display.rendered(text.replace('raster=160x90', 'raster=4x4'))
        self.assertTrue(display.render_matches(current, receipts, frames))
        current['tv1']['revision'] += 1
        self.assertFalse(display.render_matches(current, receipts, frames))
        current['tv1']['revision'] -= 1
        current['tv1']['effective'] = '256x144'
        self.assertFalse(display.render_matches(current, receipts, frames))

    def test_retained_frame_cannot_certify_a_playing_replacement(self):
        current = display.states(line(1))
        receipts = display.rendered('Acceptance video rendered: television=tv1 frameSha256=' + 'a' * 64 +
                                   ' ptsUs=123 rectangles=1 revision=2 raster=160x90 decoded=160x90')
        old = 'Acceptance video frame: session=stream1 generation=2 ptsUs=123 sha256=' + 'a' * 64 + ' dimensions=160x90'
        frames = display.decoded(old)
        self.assertFalse(display.render_matches(current, receipts, frames))
        frames = display.decoded(old.replace('generation=2', 'generation=3'))
        self.assertTrue(display.render_matches(current, receipts, frames))
        current['tv1']['status'] = 'PAUSED'
        current['tv1']['streamGeneration'] += 1
        self.assertTrue(display.render_matches(current, receipts, frames))

    def test_display_playback_cannot_hide_segment_rate_errors(self):
        display.require_clean_transfer_logs({'follower': 'Only the TV owner or an operator can control this TV'})
        with self.assertRaises(RuntimeError):
            display.require_clean_transfer_logs({'leader': 'Cinemarr: Invalid or excessive segment request'})

    def test_malformed_evidence_is_rejected_and_latest_state_wins(self):
        with self.assertRaises(ValueError): display.states('Acceptance TV display: television=tv\n')
        self.assertEqual(5, display.states(line(1) + line(1, generation=5))['tv1']['streamGeneration'])


if __name__ == '__main__': unittest.main()
