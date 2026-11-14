package xyz.doikki.videoplayer.exo;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.BaseAudioProcessor;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** App-only PCM gain, with one measured and fixed compensation per source. */
public final class AudioGainProcessor extends BaseAudioProcessor {
    public interface GainProvider { float getGain(); }
    private final GainProvider gainProvider;
    private volatile float gain = 1f;
    private int encoding = C.ENCODING_PCM_16BIT;
    private int sampleRate = 48000, channels = 1;
    private boolean configured;
    private volatile long queueCalls;
    private volatile float lastAppliedGain = -1f;
    /** 日志限频：最多每秒一条，避免音频线程被 logcat 拖到欠载。 */
    private static final long REPORT_MIN_INTERVAL_MS = 1000L;
    private volatile float lastReportedGain = -1f;
    private volatile long lastReportAtMs;
    private final SourceLevelLock sourceLevel = new SourceLevelLock();
    public AudioGainProcessor() { this(null); }
    public AudioGainProcessor(GainProvider provider) { gainProvider = provider; }
    public static AudioGainProcessor create() { return new AudioGainProcessor(); }
    public static AudioGainProcessor create(GainProvider provider) { return new AudioGainProcessor(provider); }
    public void setGain(float value) { if(!Float.isNaN(value)) gain = clampGain(value); }
    public float getGain() { return gainProvider == null ? gain : clampGain(gainProvider.getGain()); }
    public void setSourceLevelMatchEnabled(boolean value) { sourceLevel.setEnabled(value); }
    public boolean isSourceLevelMatchEnabled() { return sourceLevel.isEnabled(); }
    public float sourceLevelMakeup() { return sourceLevel.currentMakeup(); }
    public float sourceLevelDbfs() { return sourceLevel.measuredDbfs(); }
    public boolean sourceLevelLocked() { return sourceLevel.isLocked(); }
    public void beginSource() { sourceLevel.begin(sampleRate, channels); queueCalls = 0; }
    public String sourceLevelStatus() {
        if (!sourceLevel.isEnabled()) return "已关闭";
        if (queueCalls == 0) return "等待声音";
        if (!sourceLevel.isLocked()) return "测量中";
        return String.format(java.util.Locale.US, "已锁定 ×%.2f", sourceLevel.currentMakeup());
    }
    private static float clampGain(float value) { return Float.isNaN(value) ? 1f : Math.max(0f, Math.min(5f, value)); }
    @Override protected AudioFormat onConfigure(AudioFormat input) throws UnhandledAudioFormatException {
        if (input.encoding != C.ENCODING_PCM_16BIT && input.encoding != C.ENCODING_PCM_FLOAT)
            throw new UnhandledAudioFormatException(input);
        boolean formatChanged = !configured || input.sampleRate != sampleRate || input.channelCount != channels;
        encoding = input.encoding;
        sampleRate = input.sampleRate > 0 ? input.sampleRate : 48000;
        channels = Math.max(1, input.channelCount);
        if(formatChanged) sourceLevel.begin(sampleRate, channels);
        configured = true;
        log("configure enc=" + encoding + " sampleRate=" + sampleRate + " channels=" + channels + " accepted=true gain=" + getGain());
        return input;
    }
    @Override public void queueInput(ByteBuffer input) {
        int remaining = input.remaining();
        if(remaining <= 0) return;
        boolean floats = encoding == C.ENCODING_PCM_FLOAT;
        int samples = remaining / (floats ? 4 : 2);
        ByteBuffer output = replaceOutputBuffer(remaining).order(ByteOrder.nativeOrder());
        input.order(ByteOrder.nativeOrder());
        float peak = 0f;
        int pos = input.position();
        for(int i=0; i<samples; i++) {
            float value = floats ? Math.abs(input.getFloat(pos+i*4)) : Math.abs((int)input.getShort(pos+i*2))/32768f;
            if(!Float.isNaN(value) && !Float.isInfinite(value)) peak = Math.max(peak, value);
        }
        float user = getGain();
        float makeup = sourceLevel.apply(peak, samples);
        float combined = user * makeup;
        boolean muted = combined < .0001f, boosted = combined > 1.0001f;
        // 日志限频：前几个缓冲 + 每 500 个缓冲 + 增益变化后最多每秒一条。
        // 不能按「增益变了就打」：音量手势每 5% 一档，一行 logcat 就是一次跨进程写，
        // 在音频渲染线程上会把缓冲拖到欠载 —— 用户听到/看到的就是调音量时卡顿。
        boolean report = queueCalls < 3 || queueCalls % 500 == 0;
        if (!report && Math.abs(combined - lastReportedGain) > .04f) {
            long now = System.currentTimeMillis();
            if (now - lastReportAtMs >= REPORT_MIN_INTERVAL_MS) report = true;
        }
        if (report) {
            lastReportedGain = combined;
            lastReportAtMs = System.currentTimeMillis();
        }
        long levelIn=0, levelOut=0;
        for(int i=0; i<samples; i++) {
            if(floats) {
                float value = input.getFloat();
                float amplified = value * combined;
                float result = muted ? 0f : boosted && Math.abs(amplified) > .72f ? softClip(amplified) : amplified;
                if(Float.isNaN(result) || Float.isInfinite(result)) result=0f;
                output.putFloat(result);
                if(report) { levelIn += Math.round(Math.abs(value)*1000000f); levelOut += Math.round(Math.abs(result)*1000000f); }
            } else {
                int value=input.getShort();
                int result;
                if(muted) result=0;
                else if(Math.abs(combined-1f)<.0001f) result=value;
                else {
                    float amplified=(value/32768f)*combined;
                    float clipped=boosted && Math.abs(amplified)>.72f ? softClip(amplified) : amplified;
                    result=Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(clipped*32767f)));
                }
                output.putShort((short)result);
                if(report) { levelIn += Math.abs(value); levelOut += Math.abs(result); }
            }
        }
        input.position(input.limit());
        output.flip();
        queueCalls++;
        lastAppliedGain=combined;
        if(report) log("queue n=" + queueCalls + " user_gain=" + user + " makeup=" + makeup
                + " makeup_locked=" + sourceLevel.isLocked() + " locked_value=" + sourceLevel.lockedValue()
                + " source_dbfs=" + sourceLevel.measuredDbfs() + " combined=" + combined
                + " peak=" + peak + " samples=" + samples + " frames=" + samples/channels
                + " level_in=" + levelIn + " level_out=" + levelOut
                + " measured_ratio=" + (levelIn==0 ? 0f : (float)levelOut/levelIn));
    }
    public float appliedGain() { return lastAppliedGain; }
    public long queueCount() { return queueCalls; }
    private static float softClip(float value) {
        if(Float.isNaN(value)) return 0;
        float x=Math.abs(value);
        if(x<=.72f) return value;
        return Math.signum(value)*(.72f+.28f*(1f-(float)Math.exp(-(x-.72f)/.42f)));
    }
    private static void log(String message) {
        try { android.util.Log.i("CarAudioGain", message); } catch(Throwable ignored) { }
    }
}
