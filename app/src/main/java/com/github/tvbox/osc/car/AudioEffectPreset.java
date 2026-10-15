package com.github.tvbox.osc.car;

/** Immutable audio preset data; Android effect lifetime remains in AudioEffectController. */
public final class AudioEffectPreset {
    public static final int COUNT = 9;
    private static final int[] FREQUENCIES = {30,60,100,150,200,250,500,1000,2000,5000,8000,16000,32000};
    private static final AudioEffectPreset[] PRESETS = {
        new AudioEffectPreset("关闭", null, 0,0,0),
        new AudioEffectPreset("影院", new float[]{7,8,6,3,2,1,0,0,1,2,3,3,2},900,850,900),
        new AudioEffectPreset("重低音", new float[]{6,7,5,4,3,2,0,-1,-1,0,0,0,0},700,150,300),
        new AudioEffectPreset("3D环绕", new float[]{-2,-1,0,1,2,3,2,1,2,3,4,4,3},450,1000,450),
        new AudioEffectPreset("HiFi", new float[]{1,1,0,0,0,0,0,1,1,1,0,0,1},250,300,400),
        new AudioEffectPreset("人声", new float[]{-4,-3,-1.5f,0,1,2,3,4,3,2,1,0,-1},0,50,750),
        new AudioEffectPreset("动漫·标准",new float[]{1,1.5f,-.5f,-2,-2.5f,-1,.5f,1.5f,2,-1.5f,.5f,1.5f,1},150,180,450),
        new AudioEffectPreset("动漫·剧场",new float[]{2.5f,3,.5f,-1.5f,-2,-.5f,.5f,1.5f,2.5f,-1,1.5f,2.5f,2},280,320,550),
        new AudioEffectPreset("动漫·对白",new float[]{-1.5f,-1,-1.5f,-2.5f,-3,-1.5f,1,2.5f,3,-2,0,1,.5f},0,80,650)
    };
    public final String name;
    public final int bass, virtualizer, loudness;
    private final float[] curve;
    private AudioEffectPreset(String name,float[] curve,int bass,int virtualizer,int loudness) {
        this.name=name; this.curve=curve; this.bass=bass; this.virtualizer=virtualizer; this.loudness=loudness;
    }
    public static AudioEffectPreset forMode(int mode) { return PRESETS[mode>=0 && mode<COUNT?mode:0]; }
    public float[] curve() { return curve==null?null:curve.clone(); }
    public static int[] frequencies() { return FREQUENCIES.clone(); }
    public static int modeFromPreference(Object value) {
        if(value instanceof Boolean) return (Boolean)value?1:0;
        int mode=-1;
        if(value instanceof Number) mode=((Number)value).intValue();
        else if(value instanceof String) {
            try { mode=Integer.parseInt(((String)value).trim()); } catch(NumberFormatException ignored) { }
        }
        return mode>=0 && mode<COUNT?mode:0;
    }
}
