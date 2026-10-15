import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import xyz.doikki.videoplayer.render.VideoShaders;
import xyz.doikki.videoplayer.render.VideoEnhancePreset;
import xyz.doikki.videoplayer.render.VideoPostProcessPolicy;

/** Runs the production shader strings on Android EGL, without any test entry point in the app. */
public final class VideoGpuProbe {
    private static final int SW=16,SH=16,W=96,H=96;
    private static final FloatBuffer POS=floats(-1,-1,1,-1,-1,1,1,1),UV=floats(0,0,1,0,0,1,1,1);
    private static int program, current, previous;
    private static File output;
    public static void main(String[] args)throws Exception {
        output=new File(args[0]);output.mkdirs();
        EGLDisplay display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        int[] ver=new int[2];check(EGL14.eglInitialize(display,ver,0,ver,1),"EGL initialize");
        int[] attrs={EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_NONE};
        EGLConfig[] configs=new EGLConfig[1];int[] n=new int[1];
        check(EGL14.eglChooseConfig(display,attrs,0,configs,0,1,n,0)&&n[0]>0,"Choose RGBA8 EGL");
        EGLContext context=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
        EGLSurface surface=EGL14.eglCreatePbufferSurface(display,configs[0],new int[]{EGL14.EGL_WIDTH,W,EGL14.EGL_HEIGHT,H,EGL14.EGL_NONE},0);
        check(EGL14.eglMakeCurrent(display,surface,surface,context),"Make current");
        System.out.println("GPU="+GLES20.glGetString(GLES20.GL_RENDERER)+" GL="+GLES20.glGetString(GLES20.GL_VERSION));
        int oes=VideoShaders.createProgram(VideoShaders.OES_VERTEX,VideoShaders.colourFragment(true));
        check(oes!=0,"Production OES shader compile/link");GLES20.glDeleteProgram(oes);
        program=VideoShaders.createProgram(VideoShaders.OUTPUT_VERTEX,VideoShaders.OUTPUT_FRAGMENT);
        check(program!=0,"Production SGSR/temporal shader compile/link");
        int[] textures=new int[2];GLES20.glGenTextures(2,textures,0);current=textures[0];previous=textures[1];
        upload(current,solid(120));upload(previous,solid(108));
        byte[] off=draw(false,0,"off");byte[] smooth=draw(false,.28f,"smooth-low");
        float alpha=VideoPostProcessPolicy.adaptiveAlpha(.28f,12f/255f);
        double expected=120*(1-alpha)+108*alpha;
        check(Math.abs(mean(smooth)-expected)<1.1,"Low difference temporal numeric result");
        check(mean(smooth)<mean(off)-1,"Temporal toggle changes actual pixels");
        upload(current,solid(180));upload(previous,solid(40));
        check(difference(draw(false,0,"cut-off"),draw(false,.28f,"cut-on"))==0,"High difference cuts temporal blend");
        upload(current,solid(100));upload(previous,solid(100));
        check(difference(draw(false,0,"flat-off"),draw(true,.28f,"flat-all-on"))==0,"Flat fields preserved with both features on");
        byte[] edge=pattern();upload(current,edge);upload(previous,edge);
        byte[] bilinear=draw(false,0,"edge-bilinear"),sr=draw(true,0,"edge-sgsr");
        double delta=difference(bilinear,sr);
        check(delta>.05,"SR toggle changes edges");
        check(maxDifference(bilinear,sr)<=17,"SR delta bounded by 16/255 plus quantization");
        check(difference(sr,draw(true,.28f,"edge-all-on-static"))==0,"Static frames stay identical with temporal enabled");
        System.out.println("SGSR edge meanAbsDiff="+delta+" maxAbsDiff="+maxDifference(bilinear,sr));
        int colour=VideoShaders.createProgram(VideoShaders.OUTPUT_VERTEX,VideoShaders.colourFragment(false));
        check(colour!=0,"Shared production colour body compile");
        upload(current,colourPattern());
        for(int mode=0;mode<11;mode++) {
            VideoEnhancePreset p=VideoEnhancePreset.forMode(mode);
            GLES20.glUseProgram(colour);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,current);
            GLES20.glUniform1i(GLES20.glGetUniformLocation(colour,"uTexture"),0);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(colour,"uSat"),p.saturation);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(colour,"uCon"),p.contrast);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(colour,"uBri"),p.brightness);
            GLES20.glUniform3f(GLES20.glGetUniformLocation(colour,"uRgb"),p.red,p.green,p.blue);
            quad(colour);byte[] pixels=read("colour-"+mode);
            float r=(100/255f*p.red-.5f)*p.contrast+.5f+p.brightness;
            float g=(130/255f*p.green-.5f)*p.contrast+.5f+p.brightness;
            float b=(160/255f*p.blue-.5f)*p.contrast+.5f+p.brightness;
            float l=.2126f*r+.7152f*g+.0722f*b;
            float[] rgb={r,g,b};
            for(int c=0;c<3;c++) {
                int wanted=Math.round(Math.max(0,Math.min(1,l+(rgb[c]-l)*p.saturation))*255);
                check(Math.abs((pixels[c]&255)-wanted)<=1,"Exact GPU colour preset "+mode+" channel "+c);
            }
        }
        GLES20.glDeleteProgram(colour);GLES20.glDeleteProgram(program);GLES20.glDeleteTextures(2,textures,0);
        check(GLES20.glGetError()==GLES20.GL_NO_ERROR,"No GL error");
        EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
        EGL14.eglDestroySurface(display,surface);EGL14.eglDestroyContext(display,context);EGL14.eglTerminate(display);
        System.out.println("CAR6_GPU_PASS: OES compile, 4 switch combinations, adaptive temporal cutoff, flat protection, SGSR edge change/clamp, 11 colour presets, cleanup");
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);System.out.println("PASS "+message);}
    private static FloatBuffer floats(float... data){FloatBuffer b=ByteBuffer.allocateDirect(data.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();b.put(data).position(0);return b;}
    private static void upload(int texture,byte[] data){
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
        ByteBuffer b=ByteBuffer.allocateDirect(data.length);b.put(data).position(0);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,SW,SH,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,b);
    }
    private static byte[] draw(boolean sr,float alpha,String name)throws Exception {
        GLES20.glUseProgram(program);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,current);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,previous);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"uCurrFrameTex"),0);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"uPrevFrameTex"),1);
        GLES20.glUniform4f(GLES20.glGetUniformLocation(program,"uViewportInfo"),1f/SW,1f/SH,SW,SH);
        GLES20.glUniform3f(GLES20.glGetUniformLocation(program,"uParams"),sr?1:0,1.25f,alpha);
        quad(program);return read(name);
    }
    private static void quad(int p){
        GLES20.glViewport(0,0,W,H);int a=GLES20.glGetAttribLocation(p,"aPosition"),b=GLES20.glGetAttribLocation(p,"aTexCoord");
        POS.position(0);UV.position(0);GLES20.glEnableVertexAttribArray(a);GLES20.glEnableVertexAttribArray(b);
        GLES20.glVertexAttribPointer(a,2,GLES20.GL_FLOAT,false,0,POS);GLES20.glVertexAttribPointer(b,2,GLES20.GL_FLOAT,false,0,UV);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);GLES20.glDisableVertexAttribArray(a);GLES20.glDisableVertexAttribArray(b);
    }
    private static byte[] read(String name)throws Exception {
        ByteBuffer b=ByteBuffer.allocateDirect(W*H*4);GLES20.glReadPixels(0,0,W,H,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,b);
        byte[] data=new byte[b.capacity()];b.position(0);b.get(data);
        try(FileOutputStream f=new FileOutputStream(new File(output,name+".rgba"))){f.write(data);}return data;
    }
    private static byte[] solid(int gray){byte[] b=new byte[SW*SH*4];for(int i=0;i<b.length;i+=4){b[i]=b[i+1]=b[i+2]=(byte)gray;b[i+3]=(byte)255;}return b;}
    private static byte[] colourPattern(){byte[] b=solid(0);for(int i=0;i<b.length;i+=4){b[i]=100;b[i+1]=(byte)130;b[i+2]=(byte)160;}return b;}
    private static byte[] pattern(){byte[] b=solid(0);for(int y=0;y<SH;y++)for(int x=0;x<SW;x++){int i=(y*SW+x)*4;int v=x>y?210:50;b[i]=b[i+1]=b[i+2]=(byte)v;}return b;}
    private static double mean(byte[] b){double s=0;for(int i=1;i<b.length;i+=4)s+=b[i]&255;return s/(b.length/4);}
    private static double difference(byte[] a,byte[] b){double s=0;for(int i=0;i<a.length;i++)s+=Math.abs((a[i]&255)-(b[i]&255));return s/a.length;}
    private static int maxDifference(byte[] a,byte[] b){int max=0;for(int i=0;i<a.length;i++)max=Math.max(max,Math.abs((a[i]&255)-(b[i]&255)));return max;}
}
