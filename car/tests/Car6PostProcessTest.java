import com.github.tvbox.osc.car.AudioEffectPreset;
import xyz.doikki.videoplayer.render.VideoEnhancePreset;
import xyz.doikki.videoplayer.render.VideoPostProcessPolicy;
import java.util.Arrays;

public final class Car6PostProcessTest {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        float[][] audio={null,{7,8,6,3,2,1,0,0,1,2,3,3,2},{6,7,5,4,3,2,0,-1,-1,0,0,0,0},
            {-2,-1,0,1,2,3,2,1,2,3,4,4,3},{1,1,0,0,0,0,0,1,1,1,0,0,1},{-4,-3,-1.5f,0,1,2,3,4,3,2,1,0,-1},
            {1,1.5f,-.5f,-2,-2.5f,-1,.5f,1.5f,2,-1.5f,.5f,1.5f,1},
            {2.5f,3,.5f,-1.5f,-2,-.5f,.5f,1.5f,2.5f,-1,1.5f,2.5f,2},
            {-1.5f,-1,-1.5f,-2.5f,-3,-1.5f,1,2.5f,3,-2,0,1,.5f}};
        int[] bass={0,900,700,450,250,0,150,280,0},virt={0,850,150,1000,300,50,180,320,80},loud={0,900,300,450,400,750,450,550,650};
        check(AudioEffectPreset.COUNT==9,"9 audio modes");
        for(int i=0;i<9;i++){
            AudioEffectPreset p=AudioEffectPreset.forMode(i);
            check(Arrays.equals(p.curve(),audio[i]),"Exact curve "+i);
            check(p.bass==bass[i] && p.virtualizer==virt[i] && p.loudness==loud[i],"Exact audio strengths "+i);
            check(AudioEffectPreset.modeFromPreference(i)==i,"Audio persisted "+i);
        }
        float[] defensive=AudioEffectPreset.forMode(6).curve();defensive[0]=999;
        check(AudioEffectPreset.forMode(6).curve()[0]==1,"Audio curve immutable");
        for(Object invalid:new Object[]{null,-1,9,999,"broken"}) check(AudioEffectPreset.modeFromPreference(invalid)==0,"Audio fallback");
        check(AudioEffectPreset.modeFromPreference(true)==1,"Legacy audio true");
        float[][] video={{1,1,0,1,1,1},{1.35f,1.12f,.03f,1,1,1},{1.08f,1.04f,.01f,1,1,1},
            {1.08f,1.16f,-.015f,1.03f,1,.97f},{1.5f,1.16f,.015f,1,1,1},{.90f,.94f,.02f,1.015f,1,.985f},
            {1.12f,1.02f,.08f,1,1,1},{1.06f,1.04f,.02f,1.06f,1,.92f},
            {1.08f,1.05f,-.01f,1,.98f,.94f},{1.14f,1.03f,.015f,1,.99f,.96f},{1.12f,1.08f,-.005f,.99f,.99f,1}};
        for(int i=0;i<11;i++){
            VideoEnhancePreset p=VideoEnhancePreset.forMode(i);
            check(Arrays.equals(new float[]{p.saturation,p.contrast,p.brightness,p.red,p.green,p.blue},video[i]),"Exact video "+i);
        }
        check(VideoEnhancePreset.forMode(8).sgsrSharpness==1.25f && VideoEnhancePreset.forMode(9).sgsrSharpness==1.30f
            && VideoEnhancePreset.forMode(10).sgsrSharpness==1.45f && VideoEnhancePreset.forMode(1).sgsrSharpness==1.60f,"Linked SR sharpness");
        VideoPostProcessPolicy timing=new VideoPostProcessPolicy();
        long t=1_000_000_000L,step=41_708_333L;
        timing.onSourceFrame(t,t);check(timing.blendAlpha(t,true,true)==0,"First frame no blend");
        timing.onSourceFrame(t+step,t+step);
        check(Math.abs(timing.blendAlpha(t+step,true,true)-.28f)<.00001f,"Max previous weight");
        check(timing.blendAlpha(t+step+20_000_000L,true,true)>0,"Temporal blend remains active through source interval");
        check(timing.blendAlpha(t+step,true,false)==0 && timing.blendAlpha(t+step,false,true)==0,"Pause/off no blend");
        timing.onSourceFrame(t-1,t+2*step);check(timing.blendAlpha(t+2*step,true,true)==0,"Seek backwards cuts history");
        timing.reset();check(timing.blendAlpha(t,true,true)==0,"Explicit seek reset");
        check(VideoPostProcessPolicy.adaptiveAlpha(.28f,.11f)==0,"High motion/cut no blend");
        check(VideoPostProcessPolicy.adaptiveAlpha(.28f,.01f)==.28f,"Low motion blend");
        check(VideoPostProcessPolicy.adaptiveAlpha(.28f,.05f)>0 && VideoPostProcessPolicy.adaptiveAlpha(.28f,.05f)<.28f,"Adaptive transition");
        check(VideoPostProcessPolicy.frameBufferBytes(1920,1080)==16_588_800L,"1080p bytes");
        check(VideoPostProcessPolicy.frameBufferBytes(3840,2160)==66_355_200L,"4K actual bytes");
        check(VideoPostProcessPolicy.superResolutionActive(true,1920,1080,2880,1620),"1080p->3K SR");
        check(!VideoPostProcessPolicy.superResolutionActive(false,640,360,2880,1620),"SR off");
        check(VideoPostProcessPolicy.superResolutionActive(true,3840,2160,2880,1620),"4K SR remains active when output exists");
        check(VideoPostProcessPolicy.superResolutionActive(true,1920,1080,1057,594),"SR remains active at 1:1 or smaller viewport");
        check(VideoPostProcessPolicy.enabledFromPreference(null,true),"New switch default on");
        check(!VideoPostProcessPolicy.enabledFromPreference(0,true),"Persisted off");
        System.out.println("PASS: exact old/new audio and video curves, immutable data, linked SGSR sharpness, temporal cut/pause/seek, 4K policy and switches");
    }
}
