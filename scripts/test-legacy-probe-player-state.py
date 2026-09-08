#!/usr/bin/env python3
import gzip
import importlib.util
import pathlib
import struct
import unittest

spec = importlib.util.spec_from_file_location("player_state", pathlib.Path(__file__).with_name("legacy-probe-player-state.py"))
state = importlib.util.module_from_spec(spec)
spec.loader.exec_module(state)


def tag(kind, key, value):
    name = key.encode()
    return bytes([kind]) + struct.pack(">H", len(name)) + name + value


def fixture(alive):
    data = b"\x0a\x00\x00"
    for key, (kind, value) in state.FIELDS.items():
        if key in ("Health", "HealF") and not alive:
            value = 0
        if key == "DeathTime" and not alive:
            value = 82
        data += tag(kind, key, struct.pack(">" + state.FORMATS[kind], value))
    # Nested health-like keys and arbitrary inventory bytes must not change.
    data += tag(10, "nested", tag(2, "Health", struct.pack(">h", 7)) + b"\x00")
    data += tag(7, "InventoryFixture", struct.pack(">i", 5) + b"abcde")
    data += b"\x00"
    return gzip.compress(data, mtime=0)


class PlayerStateTest(unittest.TestCase):
    def test_healthy_player_is_byte_identical(self):
        original = fixture(True)
        self.assertEqual(original, state.prepare(original))
        self.assertEqual(original, state.prepare(original, True))

    def test_dead_player_fails_unless_explicitly_prepared(self):
        original = fixture(False)
        with self.assertRaises(ValueError):
            state.prepare(original)
        prepared = state.prepare(original, True)
        self.assertEqual(gzip.decompress(fixture(True)), gzip.decompress(prepared))
        self.assertEqual(prepared, state.prepare(prepared))
        self.assertNotEqual(original, prepared)

    def test_corrupt_and_oversized_inputs_fail_closed(self):
        for original in [b"not gzip", gzip.compress(b"\x0a\x00\x00\x00"),
                         gzip.compress(b"x" * (state.LIMIT + 1)), fixture(False)[:-8]]:
            with self.assertRaises((ValueError, OSError, EOFError)):
                state.prepare(original, True)

    def test_trailing_data_is_not_rewritten(self):
        with self.assertRaises(ValueError):
            state.prepare(gzip.compress(gzip.decompress(fixture(False)) + b"unexpected"), True)

    def test_prior_fall_distance_is_cleared_before_teleport_not_during_playback(self):
        data = gzip.decompress(fixture(True))
        old = tag(5, "FallDistance", struct.pack(">f", 0))
        new = tag(5, "FallDistance", struct.pack(">f", 120))
        original = gzip.compress(data.replace(old, new), mtime=0)
        self.assertEqual(original, state.prepare(original))
        self.assertEqual(data, gzip.decompress(state.prepare(original, True)))


if __name__ == "__main__":
    unittest.main()
