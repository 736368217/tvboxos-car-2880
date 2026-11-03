"""Compile the production AudioFocusHelper against deterministic Android test doubles.
Usage: python car/tests/run_audio_focus_host_test.py [--java-home PATH]
No emulator, downloads or writes to the checkout; all generated files use a temporary directory.
"""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SOURCES = {
    'androidx/annotation/NonNull.java': 'package androidx.annotation; public @interface NonNull {}',
    'android/os/Looper.java': 'package android.os; public class Looper { public static Looper getMainLooper(){return new Looper();} }',
    'android/os/Handler.java': '''package android.os;
import java.util.*;
public class Handler {
 private static final List<Runnable> queue = new ArrayList<>();
 private final List<Runnable> owned = new ArrayList<>();
 public Handler(Looper l) {}
 public boolean post(Runnable r) {queue.add(r);owned.add(r);return true;}
 public void removeCallbacksAndMessages(Object token){queue.removeAll(owned);owned.clear();}
 public static void drain(){while(!queue.isEmpty()) queue.remove(0).run();}
}''',
    'android/util/Log.java': 'package android.util; public class Log {public static int i(String a,String b){return 0;}}',
    'android/content/Context.java': '''package android.content;
import android.media.AudioManager;
public class Context {
 public static final String AUDIO_SERVICE="audio";
 public final AudioManager manager=new AudioManager();
 public Context getApplicationContext(){return this;}
 public Object getSystemService(String name){return manager;}
}''',
    'android/media/AudioManager.java': '''package android.media;
public class AudioManager {
 public interface OnAudioFocusChangeListener {void onAudioFocusChange(int f);}
 public static final int STREAM_MUSIC=3,AUDIOFOCUS_GAIN=1,AUDIOFOCUS_GAIN_TRANSIENT=2,
 AUDIOFOCUS_LOSS=-1,AUDIOFOCUS_LOSS_TRANSIENT=-2,AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK=-3,
 AUDIOFOCUS_REQUEST_GRANTED=1;
 public int requests, abandons;
 public int requestAudioFocus(OnAudioFocusChangeListener l,int s,int g){requests++;return 1;}
 public int abandonAudioFocus(OnAudioFocusChangeListener l){abandons++;return 1;}
}''',
    'xyz/doikki/videoplayer/player/VideoView.java': '''package xyz.doikki.videoplayer.player;
import android.content.Context;
class VideoView {
 final Context context=new Context(); boolean playing=true, mute; float duck=1; int starts;
 AudioFocusHelper helper;
 Context getContext(){return context;}
 boolean isPlaying(){return playing;} boolean isMute(){return mute;}
 void start(){playing=true;starts++;helper.requestFocus();}
 void pauseForAudioFocus(){playing=false;}
 void duckVolume(float f){duck=f;}
 void restoreVolume(){duck=1;}
 float output(){return mute ? 0 : duck;}
}''',
    'xyz/doikki/videoplayer/player/AudioFocusHelperHostTest.java': '''package xyz.doikki.videoplayer.player;
import android.media.AudioManager;
import android.os.Handler;
public class AudioFocusHelperHostTest {
 static int checks;
 static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
 static void event(AudioFocusHelper h,int f){h.onAudioFocusChange(f);Handler.drain();}
 public static void main(String[] args){
  VideoView v=new VideoView(); AudioFocusHelper h=new AudioFocusHelper(v);v.helper=h;
  h.requestFocus(); check(v.output()==1,"first focus");
  for(int i=0;i<10;i++){
   event(h,AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK);check(v.output()==.1f,"duck");
   h.requestFocus();check(v.output()==1,"synchronous restore");
   h.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK);
   h.abandonFocus();h.requestFocus();Handler.drain();check(v.output()==1,"queued duck invalidated");
   int abandoned=v.context.manager.abandons;
   event(h,AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);
   check(!v.playing,"transient pauses");check(v.context.manager.abandons==abandoned,"transient keeps listener");
   event(h,AudioManager.AUDIOFOCUS_GAIN);check(v.playing,"transient resumes");check(v.output()==1,"transient restores");
  }
  event(h,AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);h.abandonFocus();
  int starts=v.starts;event(h,AudioManager.AUDIOFOCUS_GAIN);
  check(v.starts==starts && !v.playing,"manual pause never auto resumes");
  v.playing=true;h.requestFocus();v.mute=true;
  event(h,AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK);
  event(h,AudioManager.AUDIOFOCUS_GAIN);check(v.output()==0,"gain cannot unmute");
  v.mute=false;check(v.output()==1,"unmute after regained focus");
  v.playing=false;event(h,AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK);
  check(v.output()==.1f,"buffering retains duck");
  v.playing=true;check(v.output()==.1f,"buffered playback cannot discard duck");
  event(h,AudioManager.AUDIOFOCUS_GAIN);
  event(h,AudioManager.AUDIOFOCUS_LOSS);starts=v.starts;
  event(h,AudioManager.AUDIOFOCUS_GAIN);check(v.starts==starts,"permanent loss never auto resumes");
  System.out.println("AudioFocusHelperHostTest PASS ("+checks+" checks)");
 }
}'''
}

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--java-home', default=os.environ.get('JAVA_HOME', ''))
    args = parser.parse_args()
    java = Path(args.java_home) / 'bin' / ('java.exe' if os.name == 'nt' else 'java')
    javac = java.with_name('javac.exe' if os.name == 'nt' else 'javac')
    with tempfile.TemporaryDirectory(prefix='tvbox-focus-') as tmp:
        base = Path(tmp)
        paths = []
        for name, text in SOURCES.items():
            path = base / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding='utf-8')
            paths.append(str(path))
        paths.append(str(ROOT / 'player/src/main/java/xyz/doikki/videoplayer/player/AudioFocusHelper.java'))
        subprocess.run([str(javac), '-encoding', 'UTF-8', '-d', str(base), *paths], check=True)
        subprocess.run([str(java), '-cp', str(base), 'xyz.doikki.videoplayer.player.AudioFocusHelperHostTest'], check=True)

if __name__ == '__main__':
    main()
