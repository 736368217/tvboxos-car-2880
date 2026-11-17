package xyz.doikki.videoplayer.render;

/**
 * 多档位视效管线着色器（SR / MEMC 分档）。
 *
 * <p>与第一版单 Pass SGSR 相比，这里把管线拆成两步，避免在 4 帧时域采样上再叠 12 抽头超分：
 * 先在源分辨率把 4 帧合成一张（{@link #MEMC_FRAGMENT}），再在输出分辨率做超分
 * （{@link #SR_SGSR_FRAGMENT} / {@link #SR_FSR_FRAGMENT} / {@link #SR_ANIME_FRAGMENT}）。
 * 这样每帧的纹理采样次数是可控的，60Hz 下不会因为档位提高而爆掉。</p>
 *
 * <p>GLSL ES 1.00（GLES20），与现有 GlEnhanceRenderView 的 attribute/varying 约定一致。</p>
 */
public final class VideoTierShaders {

    private VideoTierShaders() {
    }

    private static final String HEADER =
            "#ifdef GL_FRAGMENT_PRECISION_HIGH\nprecision highp float;\n#else\nprecision mediump float;\n#endif\n"
                    + "varying highp vec2 vTexCoord;\n";

    private static String tex(String name) {
        return "uniform sampler2D " + name + ";\n";
    }

    /** 4 帧时域合成：1=两帧自适应混合，2=4 帧 Catmull-Rom，3=保守运动补偿（失败回退 2 档）。 */
    public static final String MEMC_FRAGMENT = HEADER
            + tex("uF0") + tex("uF1") + tex("uF2") + tex("uF3")
            + "uniform highp vec2 uTexel;\n"
            + "uniform float uAlpha;\n"
            + "uniform int uTier;\n"
            + "float luma(vec3 c){return dot(c, vec3(0.299,0.587,0.114));}\n"
            + "vec4 cubic4(vec2 uv){\n"
            + "  vec4 c0=texture2D(uF1,uv); vec4 c1=texture2D(uF2,uv);\n"
            + "  vec4 cm1=texture2D(uF0,uv); vec4 c2=texture2D(uF3,uv);\n"
            + "  float a=uAlpha, a2=a*a, a3=a2*a;\n"
            + "  float wm1=-0.5*a+a2-0.5*a3;\n"
            + "  float w0=1.0-2.5*a2+1.5*a3;\n"
            + "  float w1=0.5*a+2.0*a2-1.5*a3;\n"
            + "  float w2=-0.5*a2+0.5*a3;\n"
            + "  vec4 r=cm1*wm1+c0*w0+c1*w1+c2*w2;\n"
            + "  return clamp(r, min(c0,c1)-vec4(0.015), max(c0,c1)+vec4(0.015));\n"
            + "}\n"
            + "void main(){\n"
            + "  vec4 c1=texture2D(uF2,vTexCoord);\n"
            + "  if(uTier<=0 || uAlpha<=0.002){gl_FragColor=c1;return;}\n"
            + "  if(uAlpha>=0.998){gl_FragColor=c1;return;}\n"
            + "  vec4 c0=texture2D(uF1,vTexCoord);\n"
            + "  float diff=abs(luma(c1.rgb)-luma(c0.rgb));\n"
            + "  if(diff>0.32){gl_FragColor=(uAlpha<0.5)?c0:c1;return;}\n"
            + "  if(uTier==1){\n"
            + "    float a=uAlpha*(1.0-smoothstep(0.015,0.10,abs(c1.g-c0.g)));\n"
            + "    gl_FragColor=mix(c0,c1,a);return;\n"
            + "  }\n"
            + "  if(uTier==2){gl_FragColor=cubic4(vTexCoord);return;}\n"
            // 3 档：五点块匹配 + 双向重投影一致性，置信度低就直接用 2 档结果
            + "  float e0=luma(c0.rgb);\n"
            + "  float best=1.0; vec2 bv=vec2(0.0);\n"
            + "  for(int i=0;i<5;i++){\n"
            + "    vec2 o=vec2(0.0);\n"
            + "    if(i==1)o=vec2(2.0,0.0); else if(i==2)o=vec2(-2.0,0.0);\n"
            + "    else if(i==3)o=vec2(0.0,2.0); else if(i==4)o=vec2(0.0,-2.0);\n"
            + "    float e1=luma(texture2D(uF2,vTexCoord+o*uTexel).rgb);\n"
            + "    float err=abs(e0-e1);\n"
            + "    if(err<best){best=err;bv=o*uTexel;}\n"
            + "  }\n"
            + "  vec4 fallback=cubic4(vTexCoord);\n"
            + "  if(best>0.18){gl_FragColor=fallback;return;}\n"
            + "  vec2 uv0=clamp(vTexCoord-uAlpha*bv,vec2(0.001),vec2(0.999));\n"
            + "  vec2 uv1=clamp(vTexCoord+(1.0-uAlpha)*bv,vec2(0.001),vec2(0.999));\n"
            + "  vec4 w0=texture2D(uF1,uv0); vec4 w1=texture2D(uF2,uv1);\n"
            + "  float err=abs(luma(w0.rgb)-luma(w1.rgb));\n"
            + "  float trust=smoothstep(0.12,0.02,err);\n"
            + "  float guard=(vTexCoord.y>0.84)?0.25:1.0;\n"
            + "  gl_FragColor=mix(fallback, mix(w0,w1,uAlpha), trust*guard);\n"
            + "}\n";

    /** 超分 1 档：现有 SGSR v1 等价的轻量边缘重建（保持基线观感）。 */
    public static final String SR_SGSR_FRAGMENT = HEADER
            + tex("uTexture")
            + "uniform highp vec2 uTexel;\n"
            + "uniform highp vec4 uViewportInfo;\n"
            + "uniform float uSharpness;\n"
            + "void main(){\n"
            + "  vec4 c=texture2D(uTexture,vTexCoord);\n"
            + "  vec4 n=texture2D(uTexture,vTexCoord+vec2(0.0,uTexel.y));\n"
            + "  vec4 s=texture2D(uTexture,vTexCoord-vec2(0.0,uTexel.y));\n"
            + "  vec4 e=texture2D(uTexture,vTexCoord+vec2(uTexel.x,0.0));\n"
            + "  vec4 w=texture2D(uTexture,vTexCoord-vec2(uTexel.x,0.0));\n"
            + "  vec3 detail=c.rgb*4.0-n.rgb-s.rgb-e.rgb-w.rgb;\n"
            + "  float edge=abs(c.g-n.g)+abs(c.g-s.g)+abs(c.g-e.g)+abs(c.g-w.g);\n"
            + "  float k=clamp(1.0-edge*3.0,0.0,1.0);\n"
            + "  gl_FragColor=vec4(clamp(c.rgb+detail*(0.35*k+0.10),0.0,1.0),1.0);\n"
            + "}\n";

    /** 超分 2 档：FSR1 风格 EASU（各向异性 12 抽头）+ 带噪声抑制的 RCAS 轻锐化。 */
    public static final String SR_FSR_FRAGMENT = HEADER
            + tex("uTexture")
            + "uniform highp vec2 uTexel;\n"
            + "uniform highp vec4 uViewportInfo;\n"
            + "uniform float uSharpness;\n"
            + "float lum(vec3 c){return dot(c,vec3(0.2126,0.7152,0.0722));}\n"
            + "void tap(inout vec3 acc, inout float wsum, vec2 off, vec2 dir, vec2 len, float lob, float clp, vec3 c){\n"
            + "  vec2 v=vec2(off.x*dir.x+off.y*dir.y, off.x*(-dir.y)+off.y*dir.x);\n"
            + "  v*=len;\n"
            + "  float d2=min(dot(v,v),clp);\n"
            + "  float wB=(2.0/5.0)*d2-1.0; float wA=lob*d2-1.0;\n"
            + "  wB*=wB; wA*=wA; wB=(25.0/16.0)*wB-(25.0/16.0-1.0);\n"
            + "  float w=wB*wA;\n"
            + "  acc+=c*w; wsum+=w;\n"
            + "}\n"
            + "void main(){\n"
            + "  vec2 pt=uTexel;\n"
            + "  vec2 pp=vTexCoord/pt - vec2(0.5);\n"
            + "  vec2 fp=floor(pp); pp-=fp;\n"
            + "  vec3 cB=texture2D(uTexture,(fp+vec2(0.5,-0.5))*pt).rgb;\n"
            + "  vec3 cC=texture2D(uTexture,(fp+vec2(1.5,-0.5))*pt).rgb;\n"
            + "  vec3 cE=texture2D(uTexture,(fp+vec2(-0.5,0.5))*pt).rgb;\n"
            + "  vec3 cF=texture2D(uTexture,(fp+vec2(0.5,0.5))*pt).rgb;\n"
            + "  vec3 cG=texture2D(uTexture,(fp+vec2(1.5,0.5))*pt).rgb;\n"
            + "  vec3 cH=texture2D(uTexture,(fp+vec2(2.5,0.5))*pt).rgb;\n"
            + "  vec3 cI=texture2D(uTexture,(fp+vec2(-0.5,1.5))*pt).rgb;\n"
            + "  vec3 cJ=texture2D(uTexture,(fp+vec2(0.5,1.5))*pt).rgb;\n"
            + "  vec3 cK=texture2D(uTexture,(fp+vec2(1.5,1.5))*pt).rgb;\n"
            + "  vec3 cL=texture2D(uTexture,(fp+vec2(2.5,1.5))*pt).rgb;\n"
            + "  vec3 cN=texture2D(uTexture,(fp+vec2(0.5,2.5))*pt).rgb;\n"
            + "  vec3 cO=texture2D(uTexture,(fp+vec2(1.5,2.5))*pt).rgb;\n"
            + "  vec4 w=vec4((1.0-pp.x)*(1.0-pp.y), pp.x*(1.0-pp.y), (1.0-pp.x)*pp.y, pp.x*pp.y);\n"
            + "  float lA=dot(w,vec4(lum(cB),lum(cC),lum(cF),lum(cG)));\n"
            + "  float lB=dot(w,vec4(lum(cE),lum(cF),lum(cI),lum(cJ)));\n"
            + "  float lC=dot(w,vec4(lum(cF),lum(cG),lum(cJ),lum(cK)));\n"
            + "  float lD=dot(w,vec4(lum(cG),lum(cH),lum(cK),lum(cL)));\n"
            + "  float lE=dot(w,vec4(lum(cJ),lum(cK),lum(cN),lum(cO)));\n"
            + "  float dc=lD-lC, cb=lC-lB;\n"
            + "  float lenX=clamp(abs(lD-lB)/max(max(abs(dc),abs(cb)),1e-5),0.0,1.0); lenX*=lenX;\n"
            + "  float ec=lE-lC, ca=lC-lA;\n"
            + "  float lenY=clamp(abs(lE-lA)/max(max(abs(ec),abs(ca)),1e-5),0.0,1.0); lenY*=lenY;\n"
            + "  vec2 dir=vec2(lD-lB,lE-lA);\n"
            + "  float len=(lenX+lenY)*0.5; len*=len;\n"
            + "  float dirR=dot(dir,dir);\n"
            + "  if(dirR<0.015625){\n"
            + "    vec3 bi=cF*w.x+cG*w.y+cJ*w.z+cK*w.w;\n"
            + "    gl_FragColor=vec4(bi,1.0); return;\n"
            + "  }\n"
            + "  dir*=inversesqrt(dirR);\n"
            + "  float stretch=(dir.x*dir.x+dir.y*dir.y)/max(max(abs(dir.x),abs(dir.y)),1e-5);\n"
            + "  vec2 len2=vec2(1.0+(stretch-1.0)*len, 1.0-0.5*len);\n"
            + "  float lob=0.5+((0.25-0.04)-0.5)*len;\n"
            + "  float clp=1.0/lob;\n"
            + "  vec3 acc=vec3(0.0); float wsum=0.0;\n"
            + "  tap(acc,wsum,vec2(0.0,-1.0)-pp,dir,len2,lob,clp,cB);\n"
            + "  tap(acc,wsum,vec2(1.0,-1.0)-pp,dir,len2,lob,clp,cC);\n"
            + "  tap(acc,wsum,vec2(-1.0,1.0)-pp,dir,len2,lob,clp,cI);\n"
            + "  tap(acc,wsum,vec2(0.0,1.0)-pp,dir,len2,lob,clp,cJ);\n"
            + "  tap(acc,wsum,vec2(0.0,0.0)-pp,dir,len2,lob,clp,cF);\n"
            + "  tap(acc,wsum,vec2(-1.0,0.0)-pp,dir,len2,lob,clp,cE);\n"
            + "  tap(acc,wsum,vec2(1.0,1.0)-pp,dir,len2,lob,clp,cK);\n"
            + "  tap(acc,wsum,vec2(2.0,1.0)-pp,dir,len2,lob,clp,cL);\n"
            + "  tap(acc,wsum,vec2(2.0,0.0)-pp,dir,len2,lob,clp,cH);\n"
            + "  tap(acc,wsum,vec2(1.0,0.0)-pp,dir,len2,lob,clp,cG);\n"
            + "  tap(acc,wsum,vec2(1.0,2.0)-pp,dir,len2,lob,clp,cO);\n"
            + "  tap(acc,wsum,vec2(0.0,2.0)-pp,dir,len2,lob,clp,cN);\n"
            + "  vec3 mn=min(min(cF,cG),min(cJ,cK));\n"
            + "  vec3 mx=max(max(cF,cG),max(cJ,cK));\n"
            + "  vec3 easu=clamp(acc/max(wsum,1e-5),mn,mx);\n"
            // RCAS：噪声检测后只锐化有结构的区域
            + "  vec3 nb=texture2D(uTexture,vTexCoord+vec2(0.0,uTexel.y)).rgb;\n"
            + "  vec3 nd=texture2D(uTexture,vTexCoord-vec2(uTexel.x,0.0)).rgb;\n"
            + "  vec3 nf=texture2D(uTexture,vTexCoord+vec2(uTexel.x,0.0)).rgb;\n"
            + "  vec3 nh=texture2D(uTexture,vTexCoord-vec2(0.0,uTexel.y)).rgb;\n"
            + "  float lb=lum(nb),ld=lum(nd),le=lum(easu),lf=lum(nf),lh=lum(nh);\n"
            + "  float nz=abs(0.25*(lb+ld+lf+lh)-le);\n"
            + "  float range=max(max(max(lb,ld),max(le,lf)),lh)-min(min(min(lb,ld),min(le,lf)),lh);\n"
            + "  float nzw=1.0-0.5*clamp(nz/max(range,1e-4),0.0,1.0);\n"
            + "  vec3 lobe=clamp((nb+nd+nf+nh)*0.25-easu,-0.2,0.2);\n"
            + "  vec3 sharp=clamp(easu+lobe*uSharpness*nzw,0.0,1.0);\n"
            + "  gl_FragColor=vec4(sharp,1.0);\n"
            + "}\n";

    /** 超分 3 档：二次元线条导向重建（方向加权 8 抽头 + 抗振铃钳制 + 描边强化）。 */
    public static final String SR_ANIME_FRAGMENT = HEADER
            + tex("uTexture")
            + "uniform highp vec2 uTexel;\n"
            + "uniform highp vec4 uViewportInfo;\n"
            + "uniform float uSharpness;\n"
            + "float lum(vec3 c){return dot(c,vec3(0.2126,0.7152,0.0722));}\n"
            + "void main(){\n"
            + "  vec3 c=texture2D(uTexture,vTexCoord).rgb;\n"
            + "  vec3 n=texture2D(uTexture,vTexCoord+vec2(0.0,uTexel.y)).rgb;\n"
            + "  vec3 s=texture2D(uTexture,vTexCoord-vec2(0.0,uTexel.y)).rgb;\n"
            + "  vec3 e=texture2D(uTexture,vTexCoord+vec2(uTexel.x,0.0)).rgb;\n"
            + "  vec3 w=texture2D(uTexture,vTexCoord-vec2(uTexel.x,0.0)).rgb;\n"
            + "  vec3 ne=texture2D(uTexture,vTexCoord+uTexel).rgb;\n"
            + "  vec3 sw=texture2D(uTexture,vTexCoord-uTexel).rgb;\n"
            + "  vec3 nw=texture2D(uTexture,vTexCoord+vec2(-uTexel.x,uTexel.y)).rgb;\n"
            + "  vec3 se=texture2D(uTexture,vTexCoord+vec2(uTexel.x,-uTexel.y)).rgb;\n"
            + "  float gx=abs(lum(e)-lum(w)); float gy=abs(lum(n)-lum(s));\n"
            + "  float diag=abs(lum(ne)-lum(sw))+abs(lum(nw)-lum(se));\n"
            + "  float axis=clamp(max(gx,gy)/(max(gx,gy)+diag+1e-4),0.0,1.0);\n"
            + "  vec3 along=(gx>gy)?(e+w):(n+s);\n"
            + "  vec3 across=(gx>gy)?(n+s):(e+w);\n"
            + "  vec3 base=mix((along+c*2.0)/4.0, (along+across+c*2.0)/6.0, axis);\n"
            + "  vec3 line=(along*2.0+across+c*4.0)/9.0;\n"
            + "  vec3 detail=c*4.0-along*2.0;\n"
            + "  vec3 res=base+detail*(0.30*uSharpness+0.10)+ (c-line)*0.15;\n"
            + "  vec3 mn=min(min(n,s),min(e,w)); vec3 mx=max(max(n,s),max(e,w));\n"
            + "  res=clamp(res, mn-vec3(0.02), mx+vec3(0.02));\n"
            + "  gl_FragColor=vec4(clamp(res,0.0,1.0),1.0);\n"
            + "}\n";

    /** 直通上屏（超分 0 档）。 */
    public static final String SR_PASSTHROUGH_FRAGMENT = HEADER
            + tex("uTexture")
            + "void main(){gl_FragColor=vec4(texture2D(uTexture,vTexCoord).rgb,1.0);}\n";
}
