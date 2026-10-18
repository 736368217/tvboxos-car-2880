"""Read native accessibility snapshots; report geometry without claiming visual inspection."""
import argparse
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET


def inspect(path):
    root = ET.parse(path).getroot()
    interactive = []
    outside = []
    small = []
    unnamed = []
    for node in root.iter('node'):
        if node.get('clickable') != 'true':
            continue
        match = re.fullmatch(r'\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]', node.get('bounds', ''))
        if not match:
            continue
        x1, y1, x2, y2 = map(int, match.groups())
        if x2 <= 0 or y2 <= 0 or x1 >= 2880 or y1 >= 1620:
            continue  # off-screen siblings are not an overlap finding
        label = node.get('text') or node.get('content-desc') or ''
        entry = {'resource': node.get('resource-id'), 'class': node.get('class'),
                 'label': label, 'bounds': [x1, y1, x2, y2], 'width': x2-x1, 'height': y2-y1}
        interactive.append(entry)
        if x1 < 0 or y1 < 0 or x2 > 2880 or y2 > 1620:
            outside.append(entry)
        if min(x2-x1, y2-y1) < 90:
            small.append(entry)
        if not label and not any(n.get('text') or n.get('content-desc') for n in node.iter('node')):
            unnamed.append(entry)
    return {'snapshot': path.name, 'interactive_count': len(interactive),
            'under_90px': small, 'partly_outside': outside, 'without_accessible_text': unnamed}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('evidence', type=Path)
    p.add_argument('--output', type=Path, required=True)
    args = p.parse_args()
    reports = [inspect(path) for path in sorted(args.evidence.rglob('*.xml'))]
    result = {'method': 'visible accessibility nodes; partial scroll items are observations, not automatically blockers',
              'display': [2880, 1620], 'minimum_reference_px': 90,
              'snapshots': reports}
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({'snapshots': len(reports),
                      'with_small_targets': sum(bool(x['under_90px']) for x in reports),
                      'with_partial_bounds': sum(bool(x['partly_outside']) for x in reports)}))


if __name__ == '__main__':
    main()
