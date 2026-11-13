"""Exercise production PlaybackSession, including Back -> pause -> Home lifecycle ordering.
No Android device/dependencies: minimal in-memory Android storage doubles, production session source.
Run: python car/tests/run_playback_session_host_test.py [--java-home PATH]
"""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
STUBS = {
 'android/content/Context.java': '''package android.content;
public class Context {
 public static final int MODE_PRIVATE=0;
 public final SharedPreferences prefs=new SharedPreferences();
 public Context getApplicationContext(){return this;}
 public SharedPreferences getSharedPreferences(String n,int m){return prefs;}
}''',
 'android/content/SharedPreferences.java': '''package android.content;
import java.util.*;
public class SharedPreferences {
 private final Map<String,Object> values=new HashMap<>();
 public boolean getBoolean(String k,boolean d){Object v=values.get(k);return v==null?d:(Boolean)v;}
 public long getLong(String k,long d){Object v=values.get(k);return v==null?d:(Long)v;}
 public int getInt(String k,int d){Object v=values.get(k);return v==null?d:(Integer)v;}
 public String getString(String k,String d){Object v=values.get(k);return v==null?d:(String)v;}
 public Editor edit(){return new Editor();}
 public class Editor {
  private final Map<String,Object> pending=new HashMap<>();
  public Editor putBoolean(String k,boolean v){pending.put(k,v);return this;}
  public Editor putLong(String k,long v){pending.put(k,v);return this;}
  public Editor putInt(String k,int v){pending.put(k,v);return this;}
  public Editor putString(String k,String v){pending.put(k,v);return this;}
  public void apply(){values.putAll(pending);}
 }
}''',
 'android/text/TextUtils.java': '''package android.text;
public class TextUtils {public static boolean isEmpty(CharSequence s){return s==null||s.length()==0;}}''',
 'com/github/tvbox/osc/bean/VodInfo.java': '''package com.github.tvbox.osc.bean;
public class VodInfo {public String id="movie",name="Title",pic="",playFlag="route";public int playIndex;}
''',
 'com/github/tvbox/osc/cache/PlaybackSnapshot.java': '''package com.github.tvbox.osc.cache;
import android.content.Context;import com.github.tvbox.osc.bean.VodInfo;
public class PlaybackSnapshot {
 public static long position;
 public static void save(Context c,VodInfo i,String s,long p,long d){position=p;}
 public static void clear(Context c){position=0;}
}''',
 'PlaybackSessionLifecycleHostTest.java': '''import android.content.Context;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.PlaybackSession;
import com.github.tvbox.osc.cache.PlaybackSnapshot;
public class PlaybackSessionLifecycleHostTest {
 static int checks;
 static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
 public static void main(String[] args){
  Context c=new Context();VodInfo v=new VodInfo();
  PlaybackSession.begin(c,v,"source",12345,900000,true);
  check(PlaybackSession.isActive(c),"explicit play arms session");
  check(!PlaybackSession.needsRestore(c),"foreground play never reopens");
  PlaybackSession.progress(c,v,"source",15000,900000,true);
  PlaybackSession.background(c);
  check(PlaybackSession.needsRestore(c),"external background permits recovery");
  check(PlaybackSession.claimRestore(c),"recovery claimed once");
  check(!PlaybackSession.claimRestore(c),"second Home resume cannot duplicate page");
  check(PlaybackSession.position(c)==15000,"position preserved");
  PlaybackSession.foreground(c);
  PlaybackSession.clear(c); // Detail.finish before fragment pause
  for(int n=0;n<5;n++){
   PlaybackSession.progress(c,v,"source",17000+n,900000,true); // delayed onPause/progress
   PlaybackSession.background(c);PlaybackSession.foreground(c);
   check(!PlaybackSession.isActive(c),"Back stays inactive cycle="+n);
   check(!PlaybackSession.needsRestore(c),"Home never relaunches after Back cycle="+n);
   check(!PlaybackSession.claimRestore(c),"no restore token after Back cycle="+n);
  }
  check(PlaybackSnapshot.position==0,"late pause never rewrites cleared snapshot");
  PlaybackSession.begin(c,v,"source",20000,900000,false);
  check(PlaybackSession.isActive(c),"new explicit play re-arms after exit");
  PlaybackSession.background(c);
  check(PlaybackSession.claimRestore(c),"next new playback still recovers");
  PlaybackSession.foreground(c);PlaybackSession.background(c);
  check(PlaybackSession.needsRestore(c),"later real background recovers again");
  PlaybackSession.clear(c);
  PlaybackSession.begin(c,null,"source",1,1,false);
  check(!PlaybackSession.isActive(c),"invalid callbacks cannot re-arm");
  System.out.println("PlaybackSessionLifecycleHostTest PASS checks="+checks);
 }
}'''
}

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--java-home',default=os.environ.get('JAVA_HOME',''))
    args=parser.parse_args()
    exe='.exe' if os.name=='nt' else ''
    java=str(Path(args.java_home)/'bin'/('java'+exe)) if args.java_home else 'java'
    javac=str(Path(args.java_home)/'bin'/('javac'+exe)) if args.java_home else 'javac'
    with tempfile.TemporaryDirectory(prefix='tvbox-session-',dir=os.environ.get('PI_SCRATCH_DIR')) as tmp:
        out=Path(tmp); files=[]
        for name,text in STUBS.items():
            p=out/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,encoding='utf-8');files.append(str(p))
        files.append(str(ROOT/'app/src/main/java/com/github/tvbox/osc/cache/PlaybackSession.java'))
        subprocess.run([javac,'-encoding','UTF-8','-d',str(out)]+files,check=True)
        subprocess.run([java,'-cp',str(out),'PlaybackSessionLifecycleHostTest'],check=True)

if __name__=='__main__':main()
