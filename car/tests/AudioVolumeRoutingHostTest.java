import com.github.tvbox.osc.car.VolumeBoostMath;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.AudioProcessor;
import xyz.doikki.videoplayer.exo.AudioGainProcessor;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Real routing math plus PCM bytes, including repeat configure/reset at episode boundaries. */
public final class AudioVolumeRoutingHostTest {
    private static int checks;
    private static void eq(float actual, float expected, String label) {
        checks++;
        if (Math.abs(actual - expected) > .0001f) throw new AssertionError(label + ": " + actual + " != " + expected);
    }
    public static void main(String[] args) throws Exception {
        for (float gain : new float[]{0f, .05f, .5f, 1f, 1.5f, 2f}) {
            for (float factor : new float[]{0f, .1f, 1f}) {
                final float effective = VolumeBoostMath.pcmGain(gain, factor);
                AudioGainProcessor pcm = AudioGainProcessor.create(() -> effective);
                // Rebuild or reset the sink with unchanged user gain: no squaring below 100%.
                for (int episode = 0; episode < 4; episode++) {
                    pcm.reset();
                    pcm.configure(new AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT));
                    pcm.flush();
                    ByteBuffer input = ByteBuffer.allocateDirect(2).order(ByteOrder.nativeOrder());
                    input.putShort((short) 4000).flip();
                    pcm.queueInput(input);
                    float output = pcm.getOutput().getShort() * VolumeBoostMath.outputVolume(gain, factor, true);
                    eq(output, Math.round(4000 * gain * factor), "PCM episode " + episode + " gain=" + gain + " factor=" + factor);
                }
                float nativeOutput = VolumeBoostMath.outputVolume(gain, factor, false);
                eq(nativeOutput, Math.min(1f, gain) * factor, "non-PCM gain once");
            }
        }
        eq(VolumeBoostMath.gestureVolume(1f, 0f, 1000), 1f, "no movement");
        eq(VolumeBoostMath.gestureVolume(1f, 50f, 1000), 1.05f, "up one detent");
        eq(VolumeBoostMath.gestureVolume(1f, -50f, 1000), .95f, "down one detent");
        eq(VolumeBoostMath.gestureVolume(.97f, 50f, 1000), 1f, "legacy percent snapped");
        eq(VolumeBoostMath.gestureVolume(2f, 1000f, 1000), 2f, "upper clamp");
        eq(VolumeBoostMath.gestureVolume(0f, -1000f, 1000), 0f, "lower clamp");
        for (int dy = -2400; dy <= 2400; dy++) {
            float value = VolumeBoostMath.gestureVolume(1f, dy, 1620);
            eq(Math.round(value * 100) % 5, 0, "every gesture result is a 5% detent");
        }
        System.out.println("AudioVolumeRoutingHostTest PASS (" + checks + " checks)");
    }
}
