"""Stage only local acceptance fixtures; destination must be an explicit scratch directory."""
import argparse
from pathlib import Path
import shutil
import subprocess
from PIL import Image

parser = argparse.ArgumentParser()
parser.add_argument('--output', required=True, type=Path)
parser.add_argument('--long-clip', action='store_true', help='Loop 8-second test clip to 32 seconds using ffmpeg')
args = parser.parse_args()
tests = Path(__file__).resolve().parent
car = tests.parent
args.output.mkdir(parents=True, exist_ok=True)
for name in ('api.json', 'check.json', 'car5_python_probe.py', 'car5_js_probe.js'):
    shutil.copy2(tests / 'fixtures' / name, args.output / name)
Image.new('RGB', (180, 260), (45, 100, 160)).save(args.output / 'poster.png')
if args.long_clip:
    subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-stream_loop', '3',
                    '-i', str(car / 'cartest-lowsat.mp4'), '-c', 'copy', str(args.output / 'lowsat.mp4')], check=True)
else:
    shutil.copy2(car / 'cartest-lowsat.mp4', args.output / 'lowsat.mp4')
print('Staged:', args.output)
print('Copy the staged contents to guest /sdcard/TVBoxCar5 using the leased emulator plugin/shared folder.')
print('Then import http://127.0.0.1:9978/file/TVBoxCar5/api.json through Settings > configuration address.')
