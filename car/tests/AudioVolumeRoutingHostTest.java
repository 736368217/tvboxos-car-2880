import com.github.tvbox.osc.car.VolumeBoostMath;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.AudioProcessor;
import xyz.doikki.videoplayer.exo.AudioGainProcessor;
import xyz.doikki.videoplayer.exo.CarAudioDynamicsEngine;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Real routing math plus PCM bytes, including repeat configure/reset at episode boundaries. */
public final class AudioVolumeRoutingHostTest {
    private static int checks;
    private static void eq(float actual, float expected, String label) {
        checks++;
        if (Math.abs(actual - expected) > .0001f) throw new AssertionError(label + ": " + actual + " != " + expected);
    }
    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
    public static void main(String[] args) throws Exception {
        for (float gain : new float[]{0f, .05f, .5f, 1f, 1.5f, 2f, 2.5f, 3f, 4f, 5f}) {
            for (float factor : new float[]{0f, .1f, 1f}) {
                AudioGainProcessor pcm = AudioGainProcessor.create(() -> gain);
                // Focus attenuation belongs to AudioTrack, not the nonlinear PCM limiter.
                for (int episode = 0; episode < 4; episode++) {
                    pcm.reset();
                    pcm.configure(new AudioProcessor.AudioFormat(48000, 1, C.ENCODING_PCM_16BIT));
                    pcm.flush();
                    ByteBuffer input = ByteBuffer.allocateDirect(2).order(ByteOrder.nativeOrder());
                    input.putShort((short) 4000).flip();
                    pcm.queueInput(input);
                    float output = pcm.getOutput().getShort() * VolumeBoostMath.outputVolume(gain, factor, true, true);
                    // Manual gain above 100% is capped by the -6 dBFS sample-peak ceiling.
                    float ceiling = Math.round(CarAudioDynamicsEngine.SAMPLE_PEAK_CEILING * 32768f);
                    float expected = Math.min(Math.round(4000 * gain), ceiling) * factor;
                    check(Math.abs(output - expected) <= 1.5f,
                            "PCM gain exactly once episode " + episode + ": " + output + " != " + expected);
                }
                eq(VolumeBoostMath.outputVolume(gain, factor, false, true), Math.min(1f, gain) * factor,
                        "non-PCM enhanced gain exactly once");
                eq(VolumeBoostMath.outputVolume(gain, factor, true, false), gain == 0f ? 0f : factor,
                        "original PCM ignores nonzero app gain, keeps mute/focus");
                eq(VolumeBoostMath.outputVolume(gain, factor, false, false), gain == 0f ? 0f : factor,
                        "original native ignores nonzero app gain, keeps mute/focus");
                check(VolumeBoostMath.outputVolume(gain, 0f, true, false) == 0f, "original honors explicit mute");
            }
        }
        eq(VolumeBoostMath.gestureVolume(1f, 0f, 1000), 1f, "no movement");
        eq(VolumeBoostMath.gestureVolume(1f, 50f, 1000), 1.05f, "up one detent");
        eq(VolumeBoostMath.gestureVolume(1f, -50f, 1000), .95f, "down one detent");
        eq(VolumeBoostMath.gestureVolume(.97f, 50f, 1000), 1f, "legacy percent snapped");
        eq(VolumeBoostMath.gestureVolume(2f, 1000f, 1000), 3f, "one full-screen gesture from 200% reaches 300%");
        eq(VolumeBoostMath.gestureVolume(5f, 1000f, 1000), 5f, "upper clamp 500%");
        eq(VolumeBoostMath.gestureVolume(0f, -1000f, 1000), 0f, "lower clamp");
        for (int dy = -2400; dy <= 2400; dy++) {
            float value = VolumeBoostMath.gestureVolume(1f, dy, 1620);
            eq(Math.round(value * 100) % 5, 0, "every gesture result is a 5% detent");
        }
        System.out.println("AudioVolumeRoutingHostTest PASS (" + checks + " checks)");
    }
}
