#!/usr/bin/env python3
"""Copy already resolved test libraries into an acceptance client's local folder."""
import argparse
import runpy
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('libraries', type=Path)
    parser.add_argument('game', type=Path)
    args = parser.parse_args()
    launcher = runpy.run_path(str(Path(__file__).with_name('launch-packaged-client.py')))
    game = args.game.resolve()
    planned = launcher['decoder_inputs'](args.libraries, game)
    for source, destination, checksum in planned:
        destination.parent.mkdir(parents=True, exist_ok=True)
        launcher['install_mod'](source, destination, checksum)
    print(f'Staged {len(planned)} verified local decoder packages in {game}')


if __name__ == '__main__':
    main()
