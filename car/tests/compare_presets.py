"""Recompute car.5 pixel evidence. Requires Pillow and NumPy; no emulator operations."""
import argparse
import json
from pathlib import Path

import numpy as np
from PIL import Image

parser = argparse.ArgumentParser()
parser.add_argument('screenshots', type=Path, help='Directory containing preset-0.png ... preset-7.png')
parser.add_argument('--output', type=Path, help='Optional output JSON path')
args = parser.parse_args()
names = ['原画', '臻彩', '自然', '影院', '鲜艳', '柔和', '明亮', '暖色']
video_roi = (250, 180, 1050, 650)
ui_roi = (1250, 100, 2700, 700)
original = Image.open(args.screenshots / 'preset-0.png').convert('RGB')
video_original = np.asarray(original.crop(video_roi), dtype=float)
ui_original = np.asarray(original.crop(ui_roi), dtype=float)
rows = []
for mode, name in enumerate(names):
    path = args.screenshots / ('preset-%d.png' % mode)
    image = Image.open(path).convert('RGB')
    if image.size != (2880, 1620):
        raise ValueError('Expected a 2880x1620 capture: ' + str(path))
    rgb = np.asarray(image.crop(video_roi), dtype=float)
    hsv = np.asarray(image.crop(video_roi).convert('HSV'), dtype=float)
    row = dict(mode=mode, name=name, screenshot=path.name, size=list(image.size),
               mean_rgb=np.round(rgb.mean(axis=(0, 1)), 3).tolist(),
               mean_saturation=round(float(hsv[:, :, 1].mean()), 3),
               mean_value=round(float(hsv[:, :, 2].mean()), 3),
               video_mean_absolute_difference=round(float(np.abs(rgb-video_original).mean()), 3),
               ui_mean_absolute_difference=round(float(np.abs(np.asarray(image.crop(ui_roi), dtype=float)-ui_original).mean()), 3))
    rows.append(row)
    print(json.dumps(row, ensure_ascii=False))
result = dict(video_roi=video_roi, ui_roi=ui_roi, rows=rows)
if args.output:
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
assert all(row['video_mean_absolute_difference'] > .1 for row in rows[1:]), 'No video change'
assert all(row['ui_mean_absolute_difference'] == 0 for row in rows), 'Sampled UI changed'
assert rows[4]['mean_saturation'] > rows[0]['mean_saturation'], 'Vivid saturation did not increase'
assert rows[6]['mean_value'] > rows[0]['mean_value'], 'Bright value did not increase'
print('PASS: 8 screenshots; 7 distinct video changes, expected vivid/bright directions, sampled UI unchanged')
