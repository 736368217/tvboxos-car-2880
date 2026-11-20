import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.AudioProcessor.AudioFormat;
import xyz.doikki.videoplayer.exo.AudioGainProcessor;
import xyz.doikki.videoplayer.exo.CarAudioDynamicsEngine.Mode;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Random;

/** Original must preserve every decoded PCM byte, including limits, position and float bit patterns. */
public final class AudioOriginalHostTest {
    private static int checks;
    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
    private static byte[] process(AudioGainProcessor p, byte[] pcm) {
        ByteBuffer input = ByteBuffer.allocateDirect(pcm.length + 7).order(ByteOrder.nativeOrder());
        input.position(3);
        input.put(pcm);
        input.flip();
        input.position(3);
        p.queueInput(input.asReadOnlyBuffer());
        ByteBuffer out = p.getOutput();
        byte[] actual = new byte[out.remaining()];
        out.get(actual);
        return actual;
    }
    private static AudioGainProcessor open(int encoding, int rate, int channels) throws Exception {
        AudioGainProcessor p = AudioGainProcessor.create();
        p.configure(new AudioFormat(rate, channels, encoding));
        p.flush();
        return p;
    }
    public static void main(String[] args) throws Exception {
        AudioGainProcessor p = open(C.ENCODING_PCM_16BIT, 48000, 2);
        byte[] reference = new byte[4096];
        new Random(749401L).nextBytes(reference);
        ByteBuffer.wrap(reference).order(ByteOrder.nativeOrder()).putShort(Short.MIN_VALUE).putShort(Short.MAX_VALUE);
        p.setGain(5f);
        p.setMode(Mode.CAR_BALANCED);
        byte[] firstEnhanced = process(p, reference);
        check(!Arrays.equals(reference, firstEnhanced), "enhanced really modifies PCM");
        for (int i = 0; i < 100; i++) process(p, reference);
        check(!Arrays.equals(firstEnhanced, process(p, reference)), "envelope really carries across buffers");
        p.setMode(Mode.ORIGINAL);
        for (float gain : new float[]{5f, .35f, 0f, 1f}) {
            p.setGain(gain);
            check(Arrays.equals(reference, process(p, reference)), "Original at saved gain " + gain);
        }
        p.beginSource();
        p.flush();
        check(Arrays.equals(reference, process(p, reference)), "new source while Original");
        p.setGain(5f);
        p.setMode(Mode.CAR_BALANCED);
        check(Arrays.equals(firstEnhanced, process(p, reference)), "reenable restores preset with fresh envelope");
        for (int i = 0; i < 60; i++) process(p, reference);
        p.beginSource();
        check(Arrays.equals(firstEnhanced, process(p, reference)), "same-format episode resets envelope");
        for (int i = 0; i < 60; i++) process(p, reference);
        p.flush();
        check(Arrays.equals(firstEnhanced, process(p, reference)), "seek/flush resets envelope");
        for (int i = 0; i < 60; i++) process(p, reference);
        p.setMode(Mode.ORIGINAL);
        p.setMode(Mode.CAR_BALANCED); // paused A/B/A, without a buffer in Original
        check(Arrays.equals(firstEnhanced, process(p, reference)), "paused A/B/A invalidates previous envelope");
        p.setMode(Mode.ORIGINAL);
        p.reset();
        p.configure(new AudioFormat(44100, 2, C.ENCODING_PCM_16BIT));
        p.flush();
        check(Arrays.equals(reference, process(p, reference)), "format reset does not reenable DSP");
        AudioGainProcessor floatPcm = open(C.ENCODING_PCM_FLOAT, 96000, 8);
        floatPcm.setMode(Mode.ORIGINAL);
        floatPcm.setGain(5f);
        byte[] floatBits = ByteBuffer.allocate(32).order(ByteOrder.nativeOrder())
                .putFloat(-1f).putFloat(1f).putFloat(-0f).putInt(0x7fc00123)
                .putFloat(Float.POSITIVE_INFINITY).putFloat(0.55f).putFloat(-0.3f).putFloat(0f).array();
        check(Arrays.equals(floatBits, process(floatPcm, floatBits)), "32-bit float raw bits preserved");
        System.out.println("AudioOriginalHostTest PASS checks=" + checks);
    }
}
