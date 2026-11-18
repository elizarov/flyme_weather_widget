#!/usr/bin/env python3
"""Copy a local 5120×1600 Flyme screenshot into ignored debug-only resources."""
import argparse
from pathlib import Path
import shutil
import struct

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('screenshot', type=Path)
args = parser.parse_args()
try:
    with args.screenshot.open('rb') as f:
        header = f.read(24)
    if header[:8] != b'\x89PNG\r\n\x1a\n' or struct.unpack('>II', header[16:24]) != (5120, 1600):
        raise ValueError('Expected a full 5120×1600 PNG screenshot')
    target = Path(__file__).resolve().parent.parent / 'local-assets/res/drawable-nodpi/preview_flyme_screen.png'
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(args.screenshot, target)
    print('Prepared local debug backdrop; rebuild the debug APK.')
except (OSError, ValueError, struct.error) as error:
    parser.exit(1, str(error) + '\n')
