#!/usr/bin/env python3
import unittest
import copy
from block_raster_capture import bind_evidence, expected_cell, inspect_capture, projective_map


class RasterCaptureTests(unittest.TestCase):
    source = bytes((255, 0, 0, 255, 0, 0, 255, 255))

    def test_source_and_capture_must_join_the_same_paused_tv_revision(self):
        annotation = {'phase': 'paused', 'role': 'leader', 'television': 'tv',
                      'capture': 'paused-leader.png', 'captureSha256': 'a'*64, 'sourceSha256': 'b'*64,
                      'layout': 'FIT', 'sourceWidth': 2, 'sourceHeight': 1, 'screenWidth': 4, 'screenHeight': 4}
        report = {'completed': True, 'phases': [{'phase': 'paused', 'televisions': {'tv': {'status': 'PAUSED',
                  'mapping': 'ONE_PIXEL_PER_BLOCK', 'revision': 2, 'layout': 'FIT', 'screen': '4x4'}},
                  'rendered': {'leader': {'tv': {'sha256': 'b'*64, 'revision': 2, 'width': 4, 'height': 4,
                                               'decodedWidth': 2, 'decodedHeight': 1}}}}],
                  'captures': [{'path': 'paused-leader.png', 'sha256': 'a'*64}]}
        bind_evidence(annotation, report)
        with self.assertRaises(ValueError): bind_evidence(annotation, dict(report, completed=False))
        for field, value in (('sourceSha256', 'c'*64), ('captureSha256', 'c'*64), ('layout', 'FILL'), ('screenWidth', 5)):
            wrong = dict(annotation, **{field: value})
            with self.assertRaises(ValueError): bind_evidence(wrong, report)
        for field, value in (('revision', 3), ('mapping', 'DETAILED'), ('status', 'PLAYING')):
            wrong = copy.deepcopy(report)
            wrong['phases'][0]['televisions']['tv'][field] = value
            with self.assertRaises(ValueError): bind_evidence(annotation, wrong)

    def test_fit_black_bands_and_bilinear_golden_colors(self):
        self.assertEqual((0, 0, 0), expected_cell(self.source, 2, 1, 4, 4, 'FIT', 0, 0))
        self.assertEqual((0, 0, 0), expected_cell(self.source, 2, 1, 4, 4, 'FIT', 3, 3))
        self.assertEqual((255, 0, 0), expected_cell(self.source, 2, 1, 4, 4, 'FIT', 0, 1))
        self.assertEqual((191, 0, 64), expected_cell(self.source, 2, 1, 4, 4, 'FIT', 1, 1))
        self.assertEqual((64, 0, 191), expected_cell(self.source, 2, 1, 4, 4, 'FIT', 2, 2))
        self.assertEqual((0, 0, 255), expected_cell(self.source, 2, 1, 4, 4, 'FIT', 3, 2))

    def test_fill_crops_and_stretch_uses_full_source(self):
        self.assertEqual((223, 0, 32), expected_cell(self.source, 2, 1, 4, 4, 'FILL', 0, 0))
        self.assertEqual((32, 0, 223), expected_cell(self.source, 2, 1, 4, 4, 'FILL', 3, 3))
        self.assertEqual((255, 0, 0), expected_cell(self.source, 2, 1, 4, 4, 'STRETCH', 0, 0))
        self.assertEqual((0, 0, 255), expected_cell(self.source, 2, 1, 4, 4, 'STRETCH', 3, 3))

    def test_perspective_projection_and_invalid_annotations(self):
        corners = ((10, 10), (90, 20), (70, 90), (20, 80))
        project = projective_map(corners)
        for uv, expected in zip(((0, 0), (1, 0), (1, 1), (0, 1)), corners):
            for actual, target in zip(project(*uv), expected): self.assertAlmostEqual(target, actual)
        for invalid in (((0, 0),)*4, ((0, 0), (10, 10), (0, 10), (10, 0))):
            with self.assertRaises(ValueError): projective_map(invalid)

    def test_original_interiors_must_be_uniform_and_match_the_golden(self):
        # Hand-painted 2x2 stretch of a red/blue source; independent of sampler.
        pixels = bytearray()
        for y in range(100):
            for x in range(100): pixels.extend((255, 0, 0) if x < 50 else (0, 0, 255))
        args = (100, 100, self.source, 2, 1, 2, 2, 'STRETCH', ((10, 10), (90, 10), (90, 90), (10, 90)))
        good = inspect_capture(pixels, *args)
        self.assertTrue(good['accepted'])
        self.assertEqual(20, sum(len(c['points']) for c in good['cells']))
        px, py = good['cells'][0]['points'][0]
        pixels[3*(py*100+px)] = 240
        bad = inspect_capture(pixels, *args)
        self.assertFalse(bad['accepted'])
        self.assertEqual([[0, 0]], bad['failedCells'])

    def test_exclusions_cannot_hide_most_cells_or_lack_a_reason(self):
        args = (bytes(100*100*3), 100, 100, self.source, 2, 1, 2, 2,
                'FIT', ((10, 10), (90, 10), (90, 90), (10, 90)))
        for excluded in ([{'cell': [0, 0]}], [{'cell': [0, 0], 'reason': 'controller'},
                                               {'cell': [1, 0], 'reason': 'hand'}]):
            with self.assertRaises(ValueError): inspect_capture(*args, excluded=excluded)


if __name__ == '__main__': unittest.main()
