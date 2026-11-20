import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.AudioProcessor;
import xyz.doikki.videoplayer.exo.AudioGainProcessor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Turning DRC off retains stable manual gain through alternating quiet and loud passages. */
public final class FixedGainHostTest {

    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static int[] run(AudioGainProcessor processor, int frames, int amplitude) throws Exception {
        ByteBuffer in = ByteBuffer.allocateDirect(frames * 2).order(ByteOrder.nativeOrder());
        for (int i = 0; i < frames; i++) {
            in.putShort((short) Math.round(amplitude * Math.sin(i * 0.05)));
        }
        in.flip();
        processor.queueInput(in);
        ByteBuffer out = processor.getOutput();
        int[] result = new int[out.remaining() / 2];
        for (int i = 0; i < result.length; i++) result[i] = out.getShort();
        return result;
    }

    public static void main(String[] args) throws Exception {
        AudioGainProcessor processor = AudioGainProcessor.create();
        processor.setGain(3f);
        processor.configure(new AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT));
        processor.flush();

        int[] reference = null;
        for (int window = 0; window < 8; window++) {
            // 96000 samples = 48000 stereo frames. Manual-only mode stays stable from the first buffer.
            int[] quiet = run(processor, 96000, 720);
            int[] loud = run(processor, 96000, 6000);
            check(Math.abs(processor.getGain() - 3f) < 0.0001f,
                    "window " + window + " gain must stay at 300%, got " + processor.getGain());
            if (reference == null) {
                reference = quiet;
            } else {
                check(reference[0] == quiet[0] && reference[100] == quiet[100]
                                && reference[1000] == quiet[1000],
                        "window " + window + " fixed gain output must not drift");
            }
            check(Math.abs(loud[0]) >= Math.abs(quiet[0]), "loud input remains louder");
        }

        // 0% 仍然静音，500% 仍可设置，但不会自动改变。
        processor.setGain(0f);
        int[] muted = run(processor, 4096, 4000);
        check(muted[0] == 0 && muted[100] == 0, "0% still mutes");
        processor.setGain(5f);
        int[] maxed = run(processor, 4096, 1000);
        check(Math.abs(processor.getGain() - 5f) < 0.0001f, "500% fixed getGain");
        // run() 用正弦波，首样本为 0；检查靠后的非零样本。
        check(Math.abs(maxed[100]) > 1000, "500% still amplifies small samples");

        System.out.println("FixedGainHostTest PASS checks=" + checks);
    }

    private FixedGainHostTest() {
    }
}
