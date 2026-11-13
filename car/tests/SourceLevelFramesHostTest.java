import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.AudioProcessor;
import xyz.doikki.videoplayer.exo.AudioGainProcessor;
import xyz.doikki.videoplayer.exo.SourceLevelLock;
import java.nio.*;

public final class SourceLevelFramesHostTest {
    static int checks;
    static void check(boolean yes,String name){checks++;if(!yes)throw new AssertionError(name);}
    static int block(AudioGainProcessor p,int amplitude){
        ByteBuffer b=ByteBuffer.allocateDirect(200*2).order(ByteOrder.nativeOrder());
        for(int i=0;i<200;i++)b.putShort((short)(i%2==0?amplitude:-amplitude));
        b.flip();p.queueInput(b);ByteBuffer out=p.getOutput();int peak=0;
        while(out.hasRemaining())peak=Math.max(peak,Math.abs((int)out.getShort()));
        return peak;
    }
    public static void main(String[] args)throws Exception{
        for(int channels:new int[]{1,2,6}){
            SourceLevelLock s=new SourceLevelLock();s.begin(1000,channels);
            for(int i=0;i<100;i++)s.apply(0f,100*channels);
            check(!s.isLocked()&&s.measuredFrames()==0,"silent opening channels="+channels);
            for(int i=0;i<39;i++)s.apply(.03f,100*channels);
            check(!s.isLocked(),"not locked at 3.9 seconds channels="+channels);
            s.apply(.03f,100*channels);
            check(s.isLocked()&&s.measuredFrames()==4000,"locks at 4 seconds channels="+channels);
            for(int i=0;i<5;i++)s.apply(.03f,100*channels);
            check(Math.abs(s.currentMakeup()-4f)<.001f,"half-second fade channels="+channels);
            s.apply(.9f,100*channels);
            check(s.currentMakeup()==4f,"locked even when subsequent content changes");
            s.setEnabled(false);check(s.currentMakeup()==1,"disabled immediately");
            s.setEnabled(true);check(!s.isLocked()&&s.measuredFrames()==0,"reenable starts fresh");
        }
        AudioGainProcessor p=AudioGainProcessor.create();
        AudioProcessor.AudioFormat format=new AudioProcessor.AudioFormat(1000,2,C.ENCODING_PCM_16BIT);
        p.configure(format);p.flush();p.setSourceLevelMatchEnabled(false);
        int disabled=block(p,1000);check(disabled==1000,"off exact passthrough");
        p.setSourceLevelMatchEnabled(true);
        for(int i=0;i<45;i++)block(p,1000);
        int enabled=block(p,1000);check(Math.abs(enabled-4000)<=1,"on reaches 4x for same PCM");
        p.configure(format);p.flush();
        check(Math.abs(block(p,1000)-enabled)<=1,"same format seek does not remeasure");
        p.setGain(0);check(block(p,1000)==0,"0 percent is silent");
        p.setGain(5);int at500=block(p,1000);check(at500>19000&&at500<21000,"500 percent plus compensation reaches PCM");
        p.setSourceLevelMatchEnabled(false);check(block(p,1000)==5000,"disable immediate next buffer");
        p.setGain(1);p.setSourceLevelMatchEnabled(true);p.beginSource();
        check(block(p,1000)==1000&&!p.sourceLevelLocked(),"new source resets at same format");
        System.out.println("SourceLevelFramesHostTest PASS checks="+checks+" PCM off="+disabled+" on="+enabled+" ratio="+((float)enabled/disabled)+" at500="+at500);
    }
}
