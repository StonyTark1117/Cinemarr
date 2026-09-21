#!/usr/bin/env python3
"""Synthetic physical mixtures must retain real delay despite carrier cancellation."""
import importlib.util
import math
import tempfile
import unittest
from array import array
from pathlib import Path

spec = importlib.util.spec_from_file_location('pcm', Path(__file__).with_name('compare-pcm-sync.py'))
pcm = importlib.util.module_from_spec(spec)
spec.loader.exec_module(pcm)


def capture(path, delay, offsets, tones=pcm.MULTITV_TONES):
    values = array('h')
    for frame in range(48000 * 8):
        t = frame / 48000 - delay
        sample = 0.0
        for offset in offsets:
            at = t - offset
            step = math.floor(at / .23)
            amplitude = 350 + ((step * 7919 + step * step * 101) % 1500)
            sample += amplitude * sum(math.sin(2 * math.pi * hz * at) for hz in tones)
        values.extend((round(sample), round(sample)))
    path.write_bytes(values.tobytes())


class MixtureTests(unittest.TestCase):
    def test_mixed_sources_keep_delay_and_excessive_delay_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            # Half a 997 Hz cycle nearly cancels that carrier on one client.
            capture(root/'left', 0, (0, .5/997))
            left = pcm.envelope(root/'left', frequencies=pcm.MULTITV_TONES)
            for delay in (.08, .21):
                capture(root/'right', delay, (0, .0001))
                right = pcm.envelope(root/'right', frequencies=pcm.MULTITV_TONES)
                value, lag = max((pcm.correlation(left, right, k), k*10) for k in range(-30,31))
                self.assertGreater(value, .55)
                self.assertLessEqual(abs(abs(lag)-delay*1000), 10)
                self.assertEqual(abs(lag)<=150, delay==.08)

    def test_silence_does_not_correlate(self):
        self.assertEqual(-1, pcm.correlation([0.0]*800, [0.0]*800, 0))

    def test_constant_unmodulated_tone_is_not_a_program_envelope(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            capture(root/'program',0,(0,))
            program=pcm.envelope(root/'program',frequencies=pcm.MULTITV_TONES)
            tone=array('h')
            for frame in range(48000*8):
                value=round(4000*math.sin(2*math.pi*997*frame/48000))
                tone.extend((value,value))
            (root/'tone').write_bytes(tone.tobytes())
            wrong=pcm.envelope(root/'tone',frequencies=pcm.MULTITV_TONES)
            self.assertLess(max(pcm.correlation(program,wrong,k)for k in range(-20,21)),.55)


if __name__=='__main__': unittest.main()
