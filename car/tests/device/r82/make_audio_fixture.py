"""Generate two local H.264/AAC episodes for B01 audio acceptance (requires ffmpeg)."""
import argparse
import subprocess
from pathlib import Path


p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--ffmpeg', default='ffmpeg')
p.add_argument('--out', type=Path, required=True)
a = p.parse_args()
a.out.mkdir(parents=True, exist_ok=True)
# Repeating 12-second cycle: 6 seconds quiet (-30 dBFS RMS), 4 loud (-14), 2 quiet.
audio = r"aevalsrc=sin(2*PI*500*t)*if(lt(mod(t\,12)\,6)+gte(mod(t\,12)\,10)\,0.04472136\,0.2821727):s=48000"
for episode in (1, 2):
    destination = a.out / 'episode-{}.mp4'.format(episode)
    subprocess.run([a.ffmpeg, '-hide_banner', '-loglevel', 'error', '-nostdin', '-n',
                    '-f', 'lavfi', '-i', 'smptebars=size=960x540:rate=24', '-f', 'lavfi', '-i', audio,
                    '-t', '120', '-vf', 'hue=h={}'.format((episode - 1) * 45), '-c:v', 'libx264',
                    '-threads', '2', '-preset', 'veryfast', '-pix_fmt', 'yuv420p',
                    '-c:a', 'aac', '-b:a', '128k', '-ac', '2', '-movflags', '+faststart', destination], check=True)
    print('{}: {} bytes'.format(destination, destination.stat().st_size))
