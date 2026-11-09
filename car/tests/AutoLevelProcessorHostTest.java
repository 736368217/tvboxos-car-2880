import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.AudioProcessor;
import xyz.doikki.videoplayer.exo.AudioGainProcessor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Drive the real processor with a quiet (-33 dBFS) track: makeup must raise the output. */
public final class AutoLevelProcessorHostTest {
    private static int checks;
    private static void check(boolean c, String m) { checks++; if (!c) throw new AssertionError(m); }

    private static short[] run(AudioGainProcessor p, int frames, int amplitude) throws Exception {
        p.reset();
        p.configure(new AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT));
        p.flush();
        ByteBuffer in = ByteBuffer.allocateDirect(frames * 2).order(ByteOrder.nativeOrder());
        for (int i = 0; i < frames; i++) {
            in.putShort((short) Math.round(amplitude * Math.sin(i * 0.05)));
        }
        in.flip();
        p.queueInput(in);
        ByteBuffer out = p.getOutput();
        short[] result = new short[out.remaining() / 2];
        long sumIn = 0, sumOut = 0;
        for (int i = 0; i < result.length; i++) {
            result[i] = out.getShort();
            sumOut += Math.abs(result[i]);
        }
        for (int i = 0; i < frames; i++) sumIn += Math.abs(Math.round(amplitude * Math.sin(i * 0.05)));
        System.out.println("  frames=" + frames + " amp=" + amplitude
                + " level_in=" + sumIn + " level_out=" + sumOut
                + " makeup=" + p.makeup());
        check(sumOut >= sumIn, "output must not be quieter than input");
        return result;
    }

    public static void main(String[] args) throws Exception {
        AudioGainProcessor p = AudioGainProcessor.create();
        p.setGain(1f);   // 用户 100%，全靠自动对齐
        // 96000 帧 = 1 秒观察窗的整数倍，触发一次窗口更新；多跑几轮让它收敛
        short[] first = run(p, 96000, 720);   // 现场日志的平均电平量级
        check(first[0] != 0 || first.length > 0, "produced output");
        // 连续多窗口后 makeup 应明显 >1
        for (int i = 0; i < 300; i++) {
            ByteBuffer b = ByteBuffer.allocateDirect(96000 * 2).order(ByteOrder.nativeOrder());
            for (int j = 0; j < 96000; j++) b.putShort((short) Math.round(720 * Math.sin(j * 0.05)));
            b.flip();
            p.queueInput(b);
            p.getOutput();
        }
        check(p.makeup() > 3f, "quiet track makeup should converge >3, got " + p.makeup());
        // 验证输出确实被放大：取最终一个 buffer
        ByteBuffer b = ByteBuffer.allocateDirect(4096 * 2).order(ByteOrder.nativeOrder());
        long inLevel = 0;
        for (int j = 0; j < 4096; j++) {
            short s = (short) Math.round(720 * Math.sin(j * 0.05));
            b.putShort(s); inLevel += Math.abs(s);
        }
        b.flip();
        p.queueInput(b);
        ByteBuffer out = p.getOutput();
        long outLevel = 0;
        while (out.remaining() >= 2) outLevel += Math.abs(out.getShort());
        System.out.println("  final level_in=" + inLevel + " level_out=" + outLevel
                + " ratio=" + (outLevel / (double) inLevel) + " makeup=" + p.makeup());
        check(outLevel > inLevel * 2.5, "quiet track must end up >2.5x louder at 100% user gain");

        // 关掉自动对齐后，100% 应完全直通（误差 0）
        AudioGainProcessor off = AudioGainProcessor.create();
        off.setGain(1f);
        off.setAutoLevelEnabled(false);
        ByteBuffer c = ByteBuffer.allocateDirect(4096 * 2).order(ByteOrder.nativeOrder());
        for (int j = 0; j < 4096; j++) c.putShort((short) (j % 2000 - 1000));
        c.flip();
        off.queueInput(c);
        ByteBuffer co = off.getOutput();
        int diffs = 0;
        for (int j = 0; j < 4096 && co.remaining() >= 2; j++) {
            short expect = (short) (j % 2000 - 1000);
            if (co.getShort() != expect) diffs++;
        }
        System.out.println("  passthrough diffs=" + diffs);
        check(diffs == 0, "auto-level off + 100% must be exact passthrough");

        System.out.println("AutoLevelProcessorHostTest PASS (" + checks + " checks)");
    }
}
