import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.AudioProcessor;
import xyz.doikki.videoplayer.exo.AudioGainProcessor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Host-only checks for the PCM gain processor that makes app volume above 100 % audible.
 *
 * <p>ExoPlayer's own volume only accepts 0..1, so &gt;100 % has to be applied to the decoded
 * samples. This runs the real {@link AudioGainProcessor} on the JVM (no Android runtime) and
 * drives it exactly like {@code DefaultAudioSink} does: configure → queueInput → getOutput.</p>
 */
public final class AudioGainProcessorHostTest {

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static AudioProcessor.AudioFormat format(int encoding) {
        return new AudioProcessor.AudioFormat(48000, 2, encoding);
    }

    private static ByteBuffer pcm16(int... samples) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(samples.length * 2).order(ByteOrder.nativeOrder());
        for (int sample : samples) buffer.putShort((short) sample);
        buffer.flip();
        return buffer;
    }

    private static ByteBuffer pcmFloat(float... samples) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(samples.length * 4).order(ByteOrder.nativeOrder());
        for (float sample : samples) buffer.putFloat(sample);
        buffer.flip();
        return buffer;
    }

    private static short[] run16(AudioGainProcessor processor, float gain, int... samples) {
        processor.setGain(gain);
        processor.queueInput(pcm16(samples));
        ByteBuffer output = processor.getOutput();
        short[] result = new short[output.remaining() / 2];
        for (int i = 0; i < result.length; i++) result[i] = output.getShort();
        return result;
    }

    private static float[] runFloat(AudioGainProcessor processor, float gain, float... samples) {
        processor.setGain(gain);
        processor.queueInput(pcmFloat(samples));
        ByteBuffer output = processor.getOutput();
        float[] result = new float[output.remaining() / 4];
        for (int i = 0; i < result.length; i++) result[i] = output.getFloat();
        return result;
    }

    public static void main(String[] args) throws Exception {
        AudioGainProcessor processor = AudioGainProcessor.create();

        // 处理器必须对 16bit PCM「生效」——否则 DefaultAudioSink 会直接绕过它，音量增益等于没接。
        processor.reset();
        AudioProcessor.AudioFormat out = processor.configure(format(C.ENCODING_PCM_16BIT));
        check(out.encoding == C.ENCODING_PCM_16BIT, "16bit output format");
        check(processor.isActive(), "processor must be active for PCM 16bit");

        // 100%：样本原样通过
        short[] unity = run16(processor, 1f, 1000, -1000, 32767, -32768);
        check(unity[0] == 1000 && unity[1] == -1000, "gain 1.0 passes through, got " + unity[0]);
        check(unity[2] == 32767 && unity[3] == -32768, "gain 1.0 keeps full scale");

        // 200%：真正放大两倍，并在满量程处饱和（不能回绕成噪声）
        short[] doubled = run16(processor, 2f, 1000, -1000, 20000, -20000, 32767, -32768);
        check(doubled[0] == 2000, "200% must double positive samples, got " + doubled[0]);
        check(doubled[1] == -2000, "200% must double negative samples, got " + doubled[1]);
        check(doubled[2] == 32767, "200% saturates at +full scale, got " + doubled[2]);
        check(doubled[3] == -32768, "200% saturates at -full scale, got " + doubled[3]);
        check(doubled[4] == 32767 && doubled[5] == -32768, "already full scale stays clamped");

        // 150%：单调放大
        short[] oneAndHalf = run16(processor, 1.5f, 1000, -1000);
        check(oneAndHalf[0] == 1500, "150% → 1500, got " + oneAndHalf[0]);
        check(oneAndHalf[1] == -1500, "150% → -1500, got " + oneAndHalf[1]);

        // 0%：静音必须真的是静音
        short[] silent = run16(processor, 0f, 1000, -1000, 32767);
        check(silent[0] == 0 && silent[1] == 0 && silent[2] == 0, "gain 0 must mute every sample");

        // 逐缓冲区读取增益：播放中调音量立刻生效
        run16(processor, 2f, 100, 100);
        short[] afterChange = run16(processor, 1f, 100, 100);
        check(afterChange[0] == 100, "gain change applies on the next buffer, got " + afterChange[0]);

        // 越界输入被夹紧，NaN 不改变当前增益
        processor.setGain(5f);
        check(processor.getGain() == 2f, "gain clamps to 2.0");
        processor.setGain(-3f);
        check(processor.getGain() == 0f, "negative gain clamps to 0");
        processor.setGain(1.25f);
        processor.setGain(Float.NaN);
        check(processor.getGain() == 1.25f, "NaN gain is ignored");

        // float 输出：同样放大，并在 ±1 处饱和
        processor.reset();
        processor.configure(format(C.ENCODING_PCM_FLOAT));
        float[] floats = runFloat(processor, 2f, 0.25f, -0.25f, 0.9f, -0.9f);
        check(Math.abs(floats[0] - 0.5f) < 1e-6, "float 200% → 0.5, got " + floats[0]);
        check(Math.abs(floats[1] + 0.5f) < 1e-6, "float 200% → -0.5, got " + floats[1]);
        check(floats[2] == 1f && floats[3] == -1f, "float saturates at ±1");

        // 不支持的编码必须声明为「未处理」，让音频链路回落而不是静默丢采样
        boolean rejected = false;
        try {
            processor.reset();
            processor.configure(format(C.ENCODING_PCM_24BIT));
        } catch (AudioProcessor.UnhandledAudioFormatException expected) {
            rejected = true;
        }
        check(rejected, "24bit must be reported as unhandled");


        // Separate renderer sinks get separate buffers while sharing only the owning player's gain value.
        final float[] firstPlayerGain = {2f};
        final float[] secondPlayerGain = {1f};
        AudioGainProcessor firstSink = AudioGainProcessor.create(() -> firstPlayerGain[0]);
        AudioGainProcessor secondSink = AudioGainProcessor.create(() -> secondPlayerGain[0]);
        firstSink.configure(format(C.ENCODING_PCM_16BIT));
        secondSink.configure(format(C.ENCODING_PCM_16BIT));
        long firstBefore = firstSink.queueCount();
        long secondBefore = secondSink.queueCount();
        short[] firstPlayer = run16(firstSink, 1f, 1000);
        short[] secondPlayer = run16(secondSink, 1f, 1000);
        check(firstPlayer[0] == 2000, "first player's shared gain doubles samples");
        check(secondPlayer[0] == 1000, "second player's gain stays independent");
        firstPlayerGain[0] = 1.5f;
        short[] firstUpdated = run16(firstSink, 1f, 1000);
        check(firstUpdated[0] == 1500, "sink reads the owning player's live gain each buffer");
        check(secondSink.getGain() == 1f, "first player's gain update does not affect second player");
        check(firstSink.queueCount() == firstBefore + 2 && secondSink.queueCount() == secondBefore + 1,
                "renderer sink buffer counters are independent");
        System.out.println("AudioGainProcessorHostTest PASS");
    }
}
