import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.AudioProcessor;
import xyz.doikki.videoplayer.exo.AudioGainProcessor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * r77：自动电平对齐删除后，固定用户增益必须长期稳定。
 *
 * 旧 AutoLevel 每约 1 秒按峰值改变 makeup，安静片段与响片段交替时会听成忽大忽小。
 * 这里用同一个 300% 设定连续处理多个 1 秒观察窗，要求 getGain() 恒等于 3.0，
 * 且相同小信号样本输出不随时间漂移。
 */
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
            // 每个窗口 96000 帧 = 旧 AutoLevel 的一个 1 秒测量窗。
            int[] quiet = run(processor, 96000, 720);
            int[] loud = run(processor, 96000, 6000);
            check(Math.abs(processor.getGain() - 3f) < 0.0001f,
                    "window " + window + " gain must stay at 300%, got " + processor.getGain());
            if (reference == null) {
                reference = quiet;
            } else {
                check(reference[0] == quiet[0] && reference[100] == quiet[100]
                                && reference[1000] == quiet[1000],
                        "window " + window + " quiet output must not drift");
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
