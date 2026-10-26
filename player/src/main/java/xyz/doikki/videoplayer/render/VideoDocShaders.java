package xyz.doikki.videoplayer.render;

/**

 * 方案 v2.0 原版 shader（GLSL ES 1.00 语法等价改写，算法/核函数/钳制与文档一致）。
 * 与 VideoTierShaders 并存，供多 Pass 管线使用。
 */
public final class VideoDocShaders {
    private VideoDocShaders() {}

    private static final String HEADER =
            "#ifdef GL_FRAGMENT_PRECISION_HIGH\nprecision highp float;\n#else\nprecision mediump float;\n#endif\n"
                    + "varying highp vec2 vTexCoord;\n";

    public static final String TEMPORAL_CUBIC = HEADER
            + "uniform sampler2D uFrameMinus1;\nuniform sampler2D uFrame0;\nuniform sampler2D uFrame1;\nuniform sampler2D uFrame2;\n"
            + "uniform float uAlpha;\nuniform float uMitchellB;\n"
            + "float cubicWeight(float x,float B,float C){float ax=abs(x);"
            + "if(ax<1.0){return ((12.0-9.0*B-6.0*C)*ax*ax*ax+(-18.0+12.0*B+6.0*C)*ax*ax+(6.0-2.0*B))/6.0;}"
            + "else if(ax<2.0){return ((-B-6.0*C)*ax*ax*ax+(6.0*B+30.0*C)*ax*ax+(-12.0*B-48.0*C)*ax+(8.0*B+24.0*C))/6.0;}"
            + "return 0.0;}\n"
            + "void main(){vec4 c0=texture2D(uFrame0,vTexCoord);"
            + "if(uAlpha<=0.01){gl_FragColor=c0;return;}"
            + "vec4 c1=texture2D(uFrame1,vTexCoord);"
            + "if(uAlpha>=0.99){gl_FragColor=c1;return;}"
            + "float diff01=dot(abs(c1.rgb-c0.rgb),vec3(0.299,0.587,0.114));"
            + "if(diff01>0.32){gl_FragColor=(uAlpha<0.5)?c0:c1;return;}"
            + "vec4 cm1=texture2D(uFrameMinus1,vTexCoord);vec4 c2=texture2D(uFrame2,vTexCoord);"
            + "float B=uMitchellB;float C=0.5*(1.0-B);"
            + "float wm1=cubicWeight(1.0+uAlpha,B,C);float w0=cubicWeight(uAlpha,B,C);"
            + "float w1=cubicWeight(1.0-uAlpha,B,C);float w2=cubicWeight(2.0-uAlpha,B,C);"
            + "float wSum=wm1+w0+w1+w2;"
            + "vec4 interp=(cm1*wm1+c0*w0+c1*w1+c2*w2)/wSum;"
            + "vec4 minBound=min(c0,c1)-vec4(0.015);vec4 maxBound=max(c0,c1)+vec4(0.015);"
            + "gl_FragColor=clamp(interp,max(minBound,vec4(0.0)),min(maxBound,vec4(1.0)));}\n";

    public static final String SAFE_FLOW_ESTIMATE = HEADER
            + "uniform sampler2D uPrevLuma480p;\nuniform sampler2D uCurrLuma480p;\nuniform vec2 uInvSize480p;\n"
            + "float getL(sampler2D t,vec2 uv){return texture2D(t,clamp(uv,vec2(0.001),vec2(0.999))).r;}\n"
            + "float patchSAD(vec2 uv0,vec2 uv1){float sad=0.0;"
            + "for(int y=-1;y<=1;y++){for(int x=-1;x<=1;x++){vec2 off=vec2(float(x),float(y))*uInvSize480p;"
            + "sad+=abs(getL(uCurrLuma480p,uv1+off)-getL(uPrevLuma480p,uv0+off));}}return sad;}\n"
            + "void main(){float zeroSad=patchSAD(vTexCoord,vTexCoord);"
            + "if(zeroSad<0.018){gl_FragColor=vec4(0.0,0.0,1.0,0.0);return;}"
            + "vec2 bestVec=vec2(0.0);float minSad=zeroSad*0.90;"
            + "for(int i=0;i<8;i++){vec2 d=vec2(0.0);"
            + "if(i==0)d=vec2(-2.0,0.0);else if(i==1)d=vec2(2.0,0.0);else if(i==2)d=vec2(0.0,-2.0);"
            + "else if(i==3)d=vec2(0.0,2.0);else if(i==4)d=vec2(-1.0,-1.0);else if(i==5)d=vec2(1.0,-1.0);"
            + "else if(i==6)d=vec2(-1.0,1.0);else d=vec2(1.0,1.0);"
            + "vec2 cand=d*uInvSize480p;float sad=patchSAD(vTexCoord-0.5*cand,vTexCoord+0.5*cand);"
            + "if(sad<minSad){minSad=sad;bestVec=cand;}}"
            + "float sumIx2=0.005,sumIy2=0.005,sumIxy=0.0,sumIxt=0.0,sumIyt=0.0;"
            + "for(int dy=-1;dy<=1;dy++){for(int dx=-1;dx<=1;dx++){vec2 off=vec2(float(dx),float(dy))*uInvSize480p;"
            + "vec2 uv0=vTexCoord-0.5*bestVec+off;vec2 uv1=vTexCoord+0.5*bestVec+off;"
            + "float Ix=0.5*((getL(uPrevLuma480p,uv0+vec2(uInvSize480p.x,0.0))-getL(uPrevLuma480p,uv0-vec2(uInvSize480p.x,0.0)))"
            + "+(getL(uCurrLuma480p,uv1+vec2(uInvSize480p.x,0.0))-getL(uCurrLuma480p,uv1-vec2(uInvSize480p.x,0.0))));"
            + "float Iy=0.5*((getL(uPrevLuma480p,uv0+vec2(0.0,uInvSize480p.y))-getL(uPrevLuma480p,uv0-vec2(0.0,uInvSize480p.y)))"
            + "+(getL(uCurrLuma480p,uv1+vec2(0.0,uInvSize480p.y))-getL(uCurrLuma480p,uv1-vec2(0.0,uInvSize480p.y))));"
            + "float It=getL(uCurrLuma480p,uv1)-getL(uPrevLuma480p,uv0);"
            + "sumIx2+=Ix*Ix;sumIy2+=Iy*Iy;sumIxy+=Ix*Iy;sumIxt+=Ix*It;sumIyt+=Iy*It;}}"
            + "float det=max(sumIx2*sumIy2-sumIxy*sumIxy,1e-6);"
            + "vec2 subPx=clamp(vec2(-(sumIy2*sumIxt-sumIxy*sumIyt)/det,-(sumIx2*sumIyt-sumIxy*sumIxt)/det),vec2(-1.5),vec2(1.5));"
            + "vec2 finalFlowUv=bestVec+subPx*uInvSize480p;"
            + "float conf=smoothstep(0.25,0.03,minSad/9.0);"
            + "gl_FragColor=vec4(finalFlowUv,conf,1.0);}\n";

    public static final String SAFE_FLOW_WARP = HEADER
            + "uniform sampler2D uFrameMinus1;\nuniform sampler2D uFrame0;\nuniform sampler2D uFrame1;\nuniform sampler2D uFrame2;\n"
            + "uniform sampler2D uFlowTex480p;\nuniform vec2 uFlowInvSize;\nuniform float uAlpha;\n"
            + "vec4 cubicFallback(vec2 uv,vec4 c0,vec4 c1){vec4 cm1=texture2D(uFrameMinus1,uv);vec4 c2=texture2D(uFrame2,uv);"
            + "float a=uAlpha,a2=a*a,a3=a2*a;"
            + "float wm1=-0.5*a+a2-0.5*a3;float w0=1.0-2.5*a2+1.5*a3;float w1=0.5*a+2.0*a2-1.5*a3;float w2=-0.5*a2+0.5*a3;"
            + "vec4 r=cm1*wm1+c0*w0+c1*w1+c2*w2;"
            + "return clamp(r,min(c0,c1)-vec4(0.01),max(c0,c1)+vec4(0.01));}\n"
            + "void main(){vec4 c0=texture2D(uFrame0,vTexCoord);"
            + "if(uAlpha<=0.01){gl_FragColor=c0;return;}"
            + "vec4 c1=texture2D(uFrame1,vTexCoord);"
            + "if(uAlpha>=0.99){gl_FragColor=c1;return;}"
            + "vec3 fc=texture2D(uFlowTex480p,vTexCoord).xyz;"
            + "vec2 fL=texture2D(uFlowTex480p,vTexCoord-vec2(uFlowInvSize.x,0.0)).xy;"
            + "vec2 fR=texture2D(uFlowTex480p,vTexCoord+vec2(uFlowInvSize.x,0.0)).xy;"
            + "vec2 fU=texture2D(uFlowTex480p,vTexCoord-vec2(0.0,uFlowInvSize.y)).xy;"
            + "vec2 fD=texture2D(uFlowTex480p,vTexCoord+vec2(0.0,uFlowInvSize.y)).xy;"
            + "vec2 minF=min(min(fL,fR),min(fU,fD));vec2 maxF=max(max(fL,fR),max(fU,fD));"
            + "vec2 clampedF=clamp(fc.xy,minF,maxF);vec2 flow=0.40*clampedF+0.15*(fL+fR+fU+fD);"
            + "vec2 uv0=clamp(vTexCoord-uAlpha*flow,vec2(0.001),vec2(0.999));"
            + "vec2 uv1=clamp(vTexCoord+(1.0-uAlpha)*flow,vec2(0.001),vec2(0.999));"
            + "vec4 w0=texture2D(uFrame0,uv0);vec4 w1=texture2D(uFrame1,uv1);"
            + "vec4 flowInterp=mix(w0,w1,uAlpha);"
            + "float warpError=dot(abs(w0.rgb-w1.rgb),vec3(0.299,0.587,0.114));"
            + "float occlusionTrust=fc.z*smoothstep(0.12,0.025,warpError);"
            + "float subtitleSafeMask=(vTexCoord.y>0.84)?0.25:1.0;"
            + "gl_FragColor=mix(cubicFallback(vTexCoord,c0,c1),flowInterp,occlusionTrust*subtitleSafeMask);}\n";

    public static final String FSR_EASU = HEADER
            + "uniform sampler2D uInputTex;\nuniform vec4 uSrcSize;\n"
            // GLSL ES 1.00 没有无符号整数与位运算，这里用精确的倒数/逆平方根等价替代文档里的
            // APrxLoRcpF1 / APrxLoRsqF1 位技巧（同一数学函数，差别仅在指令数）。
            + "float rcp(float a){return 1.0/max(a,1e-6);}\n"
            + "float rsq(float a){return inversesqrt(max(a,1e-6));}\n"
            + "float lum(vec3 c){return dot(c,vec3(0.2126,0.7152,0.0722));}\n"
            + "void tap(inout vec3 aC,inout float aW,vec2 off,vec2 dir,vec2 len,float lob,float clp,vec3 c){"
            + "vec2 v;v.x=(off.x*dir.x)+(off.y*dir.y);v.y=(off.x*(-dir.y))+(off.y*dir.x);v*=len;"
            + "float d2=min(dot(v,v),clp);float wB=(2.0/5.0)*d2-1.0;float wA=lob*d2-1.0;"
            + "wB*=wB;wA*=wA;wB=(25.0/16.0)*wB-(25.0/16.0-1.0);float w=wB*wA;aC+=c*w;aW+=w;}\n"
            + "void main(){vec2 pp=vTexCoord*uSrcSize.zw-vec2(0.5);vec2 fp=floor(pp);pp-=fp;vec2 pt=uSrcSize.xy;"
            + "vec3 c_b=texture2D(uInputTex,(fp+vec2(0.5,-0.5))*pt).rgb;"
            + "vec3 c_c=texture2D(uInputTex,(fp+vec2(1.5,-0.5))*pt).rgb;"
            + "vec3 c_e=texture2D(uInputTex,(fp+vec2(-0.5,0.5))*pt).rgb;"
            + "vec3 c_f=texture2D(uInputTex,(fp+vec2(0.5,0.5))*pt).rgb;"
            + "vec3 c_g=texture2D(uInputTex,(fp+vec2(1.5,0.5))*pt).rgb;"
            + "vec3 c_h=texture2D(uInputTex,(fp+vec2(2.5,0.5))*pt).rgb;"
            + "vec3 c_i=texture2D(uInputTex,(fp+vec2(-0.5,1.5))*pt).rgb;"
            + "vec3 c_j=texture2D(uInputTex,(fp+vec2(0.5,1.5))*pt).rgb;"
            + "vec3 c_k=texture2D(uInputTex,(fp+vec2(1.5,1.5))*pt).rgb;"
            + "vec3 c_l=texture2D(uInputTex,(fp+vec2(2.5,1.5))*pt).rgb;"
            + "vec3 c_n=texture2D(uInputTex,(fp+vec2(0.5,2.5))*pt).rgb;"
            + "vec3 c_o=texture2D(uInputTex,(fp+vec2(1.5,2.5))*pt).rgb;"
            + "float bL=lum(c_b),cL=lum(c_c),eL=lum(c_e),fL=lum(c_f),gL=lum(c_g),hL=lum(c_h);"
            + "float iL=lum(c_i),jL=lum(c_j),kL=lum(c_k),lL=lum(c_l),nL=lum(c_n),oL=lum(c_o);"
            + "vec4 w=vec4((1.0-pp.x)*(1.0-pp.y),pp.x*(1.0-pp.y),(1.0-pp.x)*pp.y,pp.x*pp.y);"
            + "float lA=dot(w,vec4(bL,cL,fL,gL));float lB=dot(w,vec4(eL,fL,iL,jL));"
            + "float lC=dot(w,vec4(fL,gL,jL,kL));float lD=dot(w,vec4(gL,hL,kL,lL));float lE=dot(w,vec4(jL,kL,nL,oL));"
            + "float dc=lD-lC,cb=lC-lB;float lenX=clamp(abs(lD-lB)*rcp(max(abs(dc),abs(cb))),0.0,1.0);lenX*=lenX;"
            + "float ec=lE-lC,ca=lC-lA;float lenY=clamp(abs(lE-lA)*rcp(max(abs(ec),abs(ca))),0.0,1.0);lenY*=lenY;"
            + "vec2 dir=vec2(lD-lB,lE-lA);float len=(lenX+lenY)*0.5;len*=len;float dirR=dot(dir,dir);"
            + "if(dirR<(1.0/64.0)){gl_FragColor=vec4(c_f*w.x+c_g*w.y+c_j*w.z+c_k*w.w,1.0);return;}"
            + "dir*=rsq(dirR);float stretch=(dir.x*dir.x+dir.y*dir.y)*rcp(max(abs(dir.x),abs(dir.y)));"
            + "vec2 len2=vec2(1.0+(stretch-1.0)*len,1.0-0.5*len);float lob=0.5+((0.25-0.04)-0.5)*len;float clp=rcp(lob);"
            + "vec3 aC=vec3(0.0);float aW=0.0;"
            + "tap(aC,aW,vec2(0.0,-1.0)-pp,dir,len2,lob,clp,c_b);tap(aC,aW,vec2(1.0,-1.0)-pp,dir,len2,lob,clp,c_c);"
            + "tap(aC,aW,vec2(-1.0,1.0)-pp,dir,len2,lob,clp,c_i);tap(aC,aW,vec2(0.0,1.0)-pp,dir,len2,lob,clp,c_j);"
            + "tap(aC,aW,vec2(0.0,0.0)-pp,dir,len2,lob,clp,c_f);tap(aC,aW,vec2(-1.0,0.0)-pp,dir,len2,lob,clp,c_e);"
            + "tap(aC,aW,vec2(1.0,1.0)-pp,dir,len2,lob,clp,c_k);tap(aC,aW,vec2(2.0,1.0)-pp,dir,len2,lob,clp,c_l);"
            + "tap(aC,aW,vec2(2.0,0.0)-pp,dir,len2,lob,clp,c_h);tap(aC,aW,vec2(1.0,0.0)-pp,dir,len2,lob,clp,c_g);"
            + "tap(aC,aW,vec2(1.0,2.0)-pp,dir,len2,lob,clp,c_o);tap(aC,aW,vec2(0.0,2.0)-pp,dir,len2,lob,clp,c_n);"
            + "vec3 mn=min(min(c_f,c_g),min(c_j,c_k));vec3 mx=max(max(c_f,c_g),max(c_j,c_k));"
            + "gl_FragColor=vec4(clamp(aC/aW,mn,mx),1.0);}\n";

    public static final String FSR_RCAS = HEADER
            + "uniform sampler2D uEasuTex;\nuniform vec2 uInvDstSize;\nuniform float uSharpnessStop;\n"
            + "#define FSR_RCAS_LIMIT (0.25 - (1.0/16.0))\n"
            + "void main(){vec3 b=texture2D(uEasuTex,vTexCoord+vec2(0.0,-uInvDstSize.y)).rgb;"
            + "vec3 d=texture2D(uEasuTex,vTexCoord+vec2(-uInvDstSize.x,0.0)).rgb;"
            + "vec3 e=texture2D(uEasuTex,vTexCoord).rgb;"
            + "vec3 f=texture2D(uEasuTex,vTexCoord+vec2(uInvDstSize.x,0.0)).rgb;"
            + "vec3 h=texture2D(uEasuTex,vTexCoord+vec2(0.0,uInvDstSize.y)).rgb;"
            + "float bL=b.g+0.5*(b.r+b.b),dL=d.g+0.5*(d.r+d.b),eL=e.g+0.5*(e.r+e.b),fL=f.g+0.5*(f.r+f.b),hL=h.g+0.5*(h.r+h.b);"
            + "float nz=0.25*(bL+dL+fL+hL)-eL;"
            + "float rangeL=max(max(max(bL,dL),max(eL,fL)),hL)-min(min(min(bL,dL),min(eL,fL)),hL);"
            + "nz=clamp(abs(nz)/max(rangeL,1e-4),0.0,1.0);nz=1.0-0.5*nz;"
            + "vec3 mn4=min(min(b,d),min(f,h));vec3 mx4=max(max(b,d),max(f,h));"
            + "vec3 hitMin=min(mn4,e)/max(4.0*mx4,vec3(1e-4));"
            + "vec3 hitMax=(1.0-max(mx4,e))/max(4.0*mn4-4.0,vec3(-4.0));"
            + "vec3 lobeRGB=max(-hitMin,hitMax);"
            + "float lobe=max(-FSR_RCAS_LIMIT,min(max(lobeRGB.r,max(lobeRGB.g,lobeRGB.b)),0.0))*exp2(-clamp(uSharpnessStop,0.0,2.0))*nz;"
            + "vec3 res=(lobe*(b+d+f+h)+e)/(4.0*lobe+1.0);"
            + "gl_FragColor=vec4(clamp(res,0.0,1.0),1.0);}\n";

    /**
     * 动漫档合成：CNN 只重建亮度（ACNet 的 hook 输出是单分量 luma，与 mpv 里
     * LUMA hook 一致），色度沿用源的双线性放大，按亮度比例回填，避免偏色。
     */
    public static final String ANIME_COMPOSITE = HEADER
            + "uniform sampler2D uLumaTex;\nuniform sampler2D uSrcTex;\n"
            + "void main(){vec3 s=texture2D(uSrcTex,vTexCoord).rgb;"
            + "float sy=dot(s,vec3(0.299,0.587,0.114));"
            + "float ny=texture2D(uLumaTex,vTexCoord).r;"
            + "vec3 rgb=s*(ny/max(sy,0.004));"
            + "gl_FragColor=vec4(clamp(rgb,0.0,1.0),1.0);}\n";
}
