"""Generate local car.6 playback fixtures. Requires Pillow and ffmpeg on the host."""
import argparse
from pathlib import Path
import shutil
import subprocess
import tempfile
from PIL import Image, ImageDraw


def run(*args):
    subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', *map(str, args)], check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True, help='Explicit host scratch destination')
    parser.add_argument('--segments-from', type=Path, help='Reuse motion/1080/4k.mp4 short segments from an earlier run')
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    fixtures = Path(__file__).resolve().parent / 'fixtures' / 'car6'
    for f in fixtures.iterdir():
        if f.is_file():
            shutil.copy2(f, args.output / f.name)
    Image.new('RGB', (180, 260), (45, 100, 160)).save(args.output / 'poster.png')
    with tempfile.TemporaryDirectory(prefix='car6-media-') as folder:
        temp = Path(folder)
        still = Image.new('RGB', (640, 360), (70, 78, 85))
        draw = ImageDraw.Draw(still)
        for i, colour in enumerate([(90,70,70),(70,90,70),(70,70,90),(110,105,80),(50,65,75),(145,140,135)]):
            draw.rectangle((i*106,0,(i+1)*106,359), fill=colour)
        still.save(temp / 'static.png')
        run('-loop','1','-i',temp/'static.png','-f','lavfi','-i','sine=frequency=440:sample_rate=44100',
            '-t','32','-c:v','libx264','-preset','ultrafast','-crf','30','-pix_fmt','yuv420p',
            '-c:a','aac','-b:a','48k','-movflags','+faststart',args.output/'lowsat.mp4')
        for name, size, seconds, loops in [('motion','640x360',40,4),('1080','1920x1080',20,3),('4k','3840x2160',8,3)]:
            segment = (args.segments_from / (name+'.mp4')) if args.segments_from else temp/(name+'.mp4')
            if args.segments_from:
                if not segment.is_file():
                    raise FileNotFoundError(segment)
            else:
                run('-f','lavfi','-i',f'testsrc2=size={size}:rate=24','-f','lavfi','-i','sine=frequency=440:sample_rate=44100',
                    '-t',seconds,'-c:v','libx264','-preset','ultrafast','-crf','30','-pix_fmt','yuv420p',
                    '-c:a','aac','-b:a','48k','-movflags','+faststart',segment)
            run('-stream_loop',loops,'-i',segment,'-c','copy','-movflags','+faststart',args.output/(name+'.mp4'))
    print('Generated local fixtures:', args.output)
    print('Stage into guest /sdcard/TVBoxCar5 via the leased emulator plugin/shared folder.')
    print('Import http://127.0.0.1:9978/file/TVBoxCar5/api.json through Settings, and restore your subscription afterwards.')


if __name__ == '__main__':
    main()
