package xyz.doikki.videoplayer.render;

import android.opengl.GLES20;
import android.util.Log;

/** Production shaders, also compiled unchanged by the offscreen GPU acceptance probe.
 * SGSR1 kernel: Copyright (c) 2025 Qualcomm Innovation Center, Inc. BSD-3-Clause.
 * Adapted from snapdragon-gsr d926f074, sgsr/v1/include/glsl/sgsr1_shader_mobile_edge_direction.frag.
 * Full license is distributed in assets/licenses/snapdragon-gsr.txt and car/licenses/.
 */
public final class VideoShaders {
    private VideoShaders() {}
    public static final String OES_VERTEX =
            "attribute vec4 aPosition;\nattribute vec4 aTexCoord;\nuniform mat4 uTexMatrix;\n"
            + "varying highp vec2 vTexCoord;\nvoid main(){gl_Position=aPosition;vTexCoord=(uTexMatrix*aTexCoord).xy;}\n";
    public static final String OUTPUT_VERTEX =
            "attribute vec4 aPosition;\nattribute vec2 aTexCoord;\nvarying highp vec2 vTexCoord;\n"
            + "void main(){gl_Position=aPosition;vTexCoord=aTexCoord;}\n";
    private static final String PRECISION =
            "#ifdef GL_FRAGMENT_PRECISION_HIGH\nprecision highp float;\n#else\nprecision mediump float;\n#endif\n";
    private static final String COLOUR_BODY =
            "uniform float uSat;uniform float uCon;uniform float uBri;uniform vec3 uRgb;\n"
            + "varying highp vec2 vTexCoord;\n"
            + "void main(){vec3 c=texture2D(uTexture,vTexCoord).rgb*uRgb;"
            + "c=(c-0.5)*uCon+0.5+uBri;float y=dot(c,vec3(0.2126,0.7152,0.0722));"
            + "gl_FragColor=vec4(clamp(mix(vec3(y),c,uSat),0.0,1.0),1.0);}\n";

    public static String colourFragment(boolean externalOes) {
        return (externalOes ? "#extension GL_OES_EGL_image_external : require\n" : "") + PRECISION
                + (externalOes ? "uniform samplerExternalOES uTexture;\n" : "uniform sampler2D uTexture;\n") + COLOUR_BODY;
    }

    public static final String OUTPUT_FRAGMENT = PRECISION
            + "varying highp vec2 vTexCoord;\n"
            + "uniform sampler2D uCurrFrameTex;uniform sampler2D uPrevFrameTex;\n"
            + "uniform highp vec4 uViewportInfo;uniform vec3 uParams;\n"
            + "vec4 blended(vec2 uv){vec4 c=texture2D(uCurrFrameTex,uv);"
            + "if(uParams.z<=0.001)return c;vec4 p=texture2D(uPrevFrameTex,uv);"
            + "float a=uParams.z*(1.0-smoothstep(0.015,0.10,abs(c.g-p.g)));return mix(c,p,a);}\n"
            // Exact OpenGL gather component order: x=(i0,j1),y=(i1,j1),z=(i1,j0),w=(i0,j0).
            + "vec4 gatherGreen(highp vec2 uv){highp vec2 b=floor(uv*uViewportInfo.zw-0.5);"
            + "return vec4(blended((b+vec2(0.5,1.5))*uViewportInfo.xy).g,"
            + "blended((b+vec2(1.5,1.5))*uViewportInfo.xy).g,"
            + "blended((b+vec2(1.5,0.5))*uViewportInfo.xy).g,"
            + "blended((b+vec2(0.5,0.5))*uViewportInfo.xy).g);}\n"
            + "float fastLanczos2(float x){float a=x-4.0;float b=x*a-a;a*=a;return b*a;}\n"
            + "vec2 weightY(float dx,float dy,float c,vec3 data){float e=dx*data.z+dy*data.y;"
            + "float x=dx*dx+dy*dy+e*e*(clamp(c*c*data.x,0.0,1.0)*0.7-1.0);"
            + "float w=fastLanczos2(x);return vec2(w,w*c);}\n"
            + "vec2 edgeDirection(vec4 l,vec4 r){float a=r.x-l.z;float b=r.w-l.y;"
            + "vec2 d=vec2(a+b,a-b);return d*inversesqrt(dot(d,d)+3.075740e-05);}\n"
            + "void main(){vec4 base=blended(vTexCoord);"
            + "if(uParams.x>0.5){vec4 n=texture2D(uCurrFrameTex,vTexCoord+vec2(0.0,uViewportInfo.y));"
            + "vec4 s=texture2D(uCurrFrameTex,vTexCoord-vec2(0.0,uViewportInfo.y));"
            + "vec4 e=texture2D(uCurrFrameTex,vTexCoord+vec2(uViewportInfo.x,0.0));"
            + "vec4 w=texture2D(uCurrFrameTex,vTexCoord-vec2(uViewportInfo.x,0.0));"
            + "vec3 detail=base.rgb*4.0-n.rgb-s.rgb-e.rgb-w.rgb;"
            + "base.rgb=clamp(base.rgb+detail*0.35,0.0,1.0);}"
            + "if(uParams.x<0.5){gl_FragColor=vec4(base.rgb,1.0);return;}"
            + "highp vec2 q=vTexCoord*uViewportInfo.zw+vec2(-0.5,0.5);"
            + "highp vec2 pixel=floor(q);highp vec2 coord=pixel*uViewportInfo.xy;vec2 pl=q-pixel;"
            + "vec4 l=gatherGreen(coord);float vote=abs(l.z-l.y)+abs(base.g-l.y)+abs(base.g-l.z);"
            + "if(vote<=6.0/255.0){gl_FragColor=vec4(base.rgb,1.0);return;}"
            + "coord.x+=uViewportInfo.x;vec4 r=gatherGreen(coord+vec2(uViewportInfo.x,0.0));vec4 ud;"
            + "ud.xy=gatherGreen(coord+vec2(0.0,-uViewportInfo.y)).wz;"
            + "ud.zw=gatherGreen(coord+vec2(0.0,uViewportInfo.y)).yx;"
            + "float mean=(l.y+l.z+r.x+r.w)*0.25;l-=mean;r-=mean;ud-=mean;"
            + "float centre=base.g-mean;float sum=dot(abs(l)+abs(r)+abs(ud),vec4(1.0));"
            + "float inv=10.14185/max(sum,0.0001);vec3 data=vec3(inv*inv,edgeDirection(l,r));"
            + "vec2 w=weightY(pl.x,pl.y+1.0,ud.x,data);"
            + "w+=weightY(pl.x-1.0,pl.y+1.0,ud.y,data);w+=weightY(pl.x-1.0,pl.y-2.0,ud.z,data);"
            + "w+=weightY(pl.x,pl.y-2.0,ud.w,data);w+=weightY(pl.x+1.0,pl.y-1.0,l.x,data);"
            + "w+=weightY(pl.x,pl.y-1.0,l.y,data);w+=weightY(pl.x,pl.y,l.z,data);"
            + "w+=weightY(pl.x+1.0,pl.y,l.w,data);w+=weightY(pl.x-1.0,pl.y-1.0,r.x,data);"
            + "w+=weightY(pl.x-2.0,pl.y-1.0,r.y,data);w+=weightY(pl.x-2.0,pl.y,r.z,data);"
            + "w+=weightY(pl.x-1.0,pl.y,r.w,data);"
            + "if(abs(w.x)<0.0001){gl_FragColor=vec4(base.rgb,1.0);return;}"
            + "float hi=max(max(l.y,l.z),max(r.x,r.w));float lo=min(min(l.y,l.z),min(r.x,r.w));"
            + "float delta=clamp(uParams.y*1.8*w.y/w.x,lo,hi)-centre;delta=clamp(delta,-40.0/255.0,40.0/255.0);"
            + "gl_FragColor=vec4(clamp(base.rgb+delta,0.0,1.0),1.0);}\n";

    public static int createProgram(String vertexSource, String fragmentSource) {
        int vertex=compile(GLES20.GL_VERTEX_SHADER,vertexSource);
        int fragment=compile(GLES20.GL_FRAGMENT_SHADER,fragmentSource);
        if(vertex==0 || fragment==0){if(vertex!=0)GLES20.glDeleteShader(vertex);if(fragment!=0)GLES20.glDeleteShader(fragment);return 0;}
        int program=GLES20.glCreateProgram();
        GLES20.glAttachShader(program,vertex);GLES20.glAttachShader(program,fragment);GLES20.glLinkProgram(program);
        GLES20.glDeleteShader(vertex);GLES20.glDeleteShader(fragment);
        int[] ok=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,ok,0);
        if(ok[0]==0){Log.e("GlEnhance","shader link: "+GLES20.glGetProgramInfoLog(program));GLES20.glDeleteProgram(program);return 0;}
        return program;
    }
    private static int compile(int type,String source) {
        int shader=GLES20.glCreateShader(type);GLES20.glShaderSource(shader,source);GLES20.glCompileShader(shader);
        int[] ok=new int[1];GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,ok,0);
        if(ok[0]==0){Log.e("GlEnhance","shader compile: "+GLES20.glGetShaderInfoLog(shader));GLES20.glDeleteShader(shader);return 0;}
        return shader;
    }
}
