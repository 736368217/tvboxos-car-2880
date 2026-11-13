import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.AudioProcessor;
import xyz.doikki.videoplayer.exo.AudioGainProcessor;
import xyz.doikki.videoplayer.exo.SourceLevelLock;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 静态片源补偿（SourceLevelLock）验收。
 *
 * <p>要证明三件事：
 * 1. 轻片源（−33 dBFS，幅度 720）在锁定后被显著抬升；
 * 2. 锁定之后，同一输入在不同时间窗输出<b>逐样本一致</b>（这是 r73 动态补偿失败的地方）；
 * 3. 本身够响的片源（峰值 ≥ TARGET）不被压。
 */
public final class SourceLevelLockHostTest {

    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static int[] run(AudioGainProcessor processor, int frames, int amplitude) {
        ByteBuffer in = ByteBuffer.allocateDirect(frames * 2 * 2).order(ByteOrder.nativeOrder());
        for (int i = 0; i < frames * 2; i++) {
            in.putShort((short) Math.round(amplitude * Math.sin((i / 2) * 0.05)));
        }
        in.flip();
        processor.queueInput(in);
        ByteBuffer out = processor.getOutput();
        int[] result = new int[out.remaining() / 2];
        for (int i = 0; i < result.length; i++) result[i] = out.getShort();
        return result;
    }

    /** 纯逻辑测试：不经过 processor，直接验证补偿算法。 */
    private static void testLockMath() {
        // 轻片源：峰值 720/32767 ≈ 0.022（−33 dBFS）→ 应抬到 4.0 倍上限
        SourceLevelLock lock = new SourceLevelLock();
        lock.begin(48000);
        float out = 1f;
        // 4 秒 = 192000 样本，按 4096/块 ≈ 47 块
        for (int i = 0; i < 50; i++) {
            out = lock.apply(0.022f, 4096);
        }
        check(lock.isLocked(), "should lock after 4s");
        check(Math.abs(lock.lockedValue() - SourceLevelLock.MAX_MAKEUP) < 0.01f,
                "quiet source must hit MAX_MAKEUP, got " + lock.lockedValue());

        // 锁定后淡入 0.5 秒才到满值；再喂 0.5 秒的块并确认达到上限。
        for (int i = 0; i < 12; i++) {
            out = lock.apply(0.022f, 4096);   // 12*4096 = 49152 > 0.5s*48000=24000
        }
        check(out > 3.9f, "applied makeup should reach 4.0 after fade, got " + out);

        // 锁定后继续喂同样的块，输出必须稳定（不再改变）
        float stable = lock.apply(0.022f, 4096);
        check(Math.abs(stable - out) < 0.001f, "post-lock makeup must be stable");

        // 响片源：峰值 0.8 → 不应补偿
        SourceLevelLock loud = new SourceLevelLock();
        loud.begin(48000);
        for (int i = 0; i < 50; i++) loud.apply(0.8f, 4096);
        check(Math.abs(loud.lockedValue() - 1f) < 0.001f,
                "loud source must not be boosted, got " + loud.lockedValue());

        // 静音：不抬底噪
        SourceLevelLock silent = new SourceLevelLock();
        silent.begin(48000);
        for (int i = 0; i < 50; i++) silent.apply(0.0001f, 4096);
        check(Math.abs(silent.lockedValue() - 1f) < 0.001f,
                "silence must not be amplified, got " + silent.lockedValue());

        // 换片源后重置
        SourceLevelLock reuse = new SourceLevelLock();
        reuse.begin(48000);
        for (int i = 0; i < 50; i++) reuse.apply(0.022f, 4096);
        check(reuse.isLocked(), "locked before reset");
        reuse.begin(48000);
        check(!reuse.isLocked(), "begin() must reset lock state");
        check(Math.abs(reuse.currentMakeup() - 1f) < 0.001f, "makeup resets to 1.0");

        System.out.println("  lock math: PASS");
    }

    /**
     * 开关验收：关闭后必须与 r77 行为完全一致（只应用用户固定增益，不做任何补偿）。
     */
    private static void testDisabledSwitch() throws Exception {
        AudioGainProcessor processor = AudioGainProcessor.create();
        processor.setGain(1f);
        processor.configure(new AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT));
        processor.flush();
        processor.setSourceLevelMatchEnabled(false);
        check(!processor.isSourceLevelMatchEnabled(), "switch should report disabled");

        int amplitude = 720;
        // 跑足够久的窗口，若补偿仍在工作会明显放大
        for (int i = 0; i < 70; i++) run(processor, 4096, amplitude);
        int[] out = run(processor, 4096, amplitude);
        int peak = 0;
        for (int v : out) peak = Math.max(peak, Math.abs(v));
        check(peak <= amplitude + 2,
                "disabled switch must passthrough at 1.0x, peak=" + peak);
        check(Math.abs(processor.sourceLevelMakeup() - 1f) < 0.001f,
                "disabled switch makeup must be 1.0, got " + processor.sourceLevelMakeup());

        // 重新开启：应当重新测量并补偿
        processor.setSourceLevelMatchEnabled(true);
        for (int i = 0; i < 70; i++) run(processor, 4096, amplitude);
        int[] again = run(processor, 4096, amplitude);
        int peak2 = 0;
        for (int v : again) peak2 = Math.max(peak2, Math.abs(v));
        check(peak2 > amplitude * 3, "re-enabled switch must boost again, peak=" + peak2);

        System.out.println("  switch off/on: PASS (off=" + peak + " on=" + peak2 + ")");
    }

    private static void testProcessorQuietSource() throws Exception {        AudioGainProcessor processor = AudioGainProcessor.create();
        processor.setGain(1f);   // 用户 100%，全靠静态补偿
        processor.configure(new AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT));
        processor.flush();

        int amplitude = 720;   // 实测 r70 的 -33 dBFS 电平
        int[] before = run(processor, 4096, amplitude);
        int peakBefore = 0;
        for (int v : before) peakBefore = Math.max(peakBefore, Math.abs(v));
        check(peakBefore <= amplitude + 2, "measurement window passes through unchanged");

        // 走完测量期
        for (int i = 0; i < 60; i++) run(processor, 4096, amplitude);

        int[] after1 = run(processor, 4096, amplitude);
        int peakAfter = 0;
        for (int v : after1) peakAfter = Math.max(peakAfter, Math.abs(v));
        check(peakAfter > peakBefore * 3, "quiet source must be boosted >3x, before="
                + peakBefore + " after=" + peakAfter);

        // ★核心断言：多个时间窗后输出逐样本一致（不会忽大忽小）
        for (int w = 0; w < 5; w++) {
            int[] again = run(processor, 4096, amplitude);
            for (int i = 0; i < again.length; i += 97) {
                check(again[i] == after1[i],
                        "window " + w + " sample " + i + " drifted: "
                                + after1[i] + " -> " + again[i]);
            }
        }

        // 用户把增益调到 0 仍应静音
        processor.setGain(0f);
        int[] muted = run(processor, 4096, amplitude);
        check(muted[0] == 0, "0% still mutes");

        System.out.println("  processor quiet source: PASS (boost "
                + peakBefore + " -> " + peakAfter + ")");
    }

    public static void main(String[] args) throws Exception {
        System.out.println("SourceLevelLockHostTest");
        testLockMath();
        testProcessorQuietSource();
        testDisabledSwitch();
        System.out.println("SourceLevelLockHostTest PASS checks=" + checks);
    }

    private SourceLevelLockHostTest() {
    }
}
