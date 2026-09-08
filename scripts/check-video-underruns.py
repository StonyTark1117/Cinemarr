#!/usr/bin/env python3
"""Reject active underruns anywhere in a closed client log, including before resets.

The product's AudioUnderrunPolicy classifies paused/terminal backend drains.
Raw backend starvation counts are not interchangeable with active underruns.
"""
import argparse
from pathlib import Path
import re

COUNTER = re.compile(r'\b(?:underruns|audioUnderruns)=(\d+)\b')


def check(text):
    if 'Acceptance video audio timeline:' not in text and 'Acceptance legacy video audio timeline:' not in text:
        raise ValueError('Missing real video audio timeline')
    for number, line in enumerate(text.splitlines(), 1):
        if 'audio active underrun:' in line or any(int(value) > 0 for value in COUNTER.findall(line)):
            raise ValueError('Active video audio underrun at log line ' + str(number))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('log', type=Path)
    args = parser.parse_args()
    try:
        check(args.log.read_text(errors='replace'))
    except ValueError as failure:
        parser.exit(1, str(args.log) + ': ' + str(failure) + '\n')


if __name__ == '__main__': main()
