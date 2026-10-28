package xyz.doikki.videoplayer.exo;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.BaseAudioProcessor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * App-level PCM gain for ExoPlayer.
 *
 * <p>ExoPlayer's {@code setVolume()} only accepts 0..1 (it ends up in AudioTrack volume),
 * so anything above 100 % has to be applied to the decoded samples.  This processor sits
 * in the audio sink's processor chain and simply multiplies every PCM sample by the current
 * gain, saturating to the sample range.</p>
 *
 * <p>The gain is read per buffer, so changing it while playing takes effect immediately
 * (one buffer of latency at most).</p>
 */
public final class AudioGainProcessor extends BaseAudioProcessor {

    public static final AudioGainProcessor INSTANCE = new AudioGainProcessor();

    private volatile float gain = 1f;
    /** 当前输出编码，由 onConfigure 记录（BaseAudioProcessor 没有公开的输出格式访问器）。 */
    private int encoding = C.ENCODING_PCM_16BIT;

    /** 0..2, where 1 = untouched. */
    public void setGain(float gain) {
        if (Float.isNaN(gain)) return;
        this.gain = Math.max(0f, Math.min(2f, gain));
    }

    public float getGain() {
        return gain;
    }

    @Override
    protected AudioFormat onConfigure(AudioFormat inputAudioFormat) throws UnhandledAudioFormatException {
        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT
                || inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) {
            encoding = inputAudioFormat.encoding;
            return inputAudioFormat;
        }
        throw new UnhandledAudioFormatException(inputAudioFormat);
    }

    @Override
    public void queueInput(ByteBuffer inputBuffer) {
        int remaining = inputBuffer.remaining();
        if (remaining <= 0) return;
        float g = gain;
        ByteBuffer output = replaceOutputBuffer(remaining);
        output.order(ByteOrder.nativeOrder());
        inputBuffer.order(ByteOrder.nativeOrder());

        if (encoding == C.ENCODING_PCM_FLOAT) {
            while (inputBuffer.remaining() >= 4) {
                float value = inputBuffer.getFloat() * g;
                if (value > 1f) value = 1f;
                else if (value < -1f) value = -1f;
                output.putFloat(value);
            }
        } else {
            while (inputBuffer.remaining() >= 2) {
                int value = Math.round(inputBuffer.getShort() * g);
                if (value > Short.MAX_VALUE) value = Short.MAX_VALUE;
                else if (value < Short.MIN_VALUE) value = Short.MIN_VALUE;
                output.putShort((short) value);
            }
        }
        output.flip();
    }
}
