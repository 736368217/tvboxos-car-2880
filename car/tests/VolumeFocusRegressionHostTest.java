import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * r90 回归闸门：预览小窗诊断徽标、失焦暂停、换集后音量立即到位。
 *
 * <p>这三条都是「设备上才看得见」的行为，宿主机没法真的跑播放器，所以这里守住源码层的
 * 不变量：一旦有人把判断删掉（比如又把徽标在预览里显示、或让焦点丢失不再暂停、或让
 * 换集后的增益下发继续被幂等短路吞掉），测试立刻失败。</p>
 */
public final class VolumeFocusRegressionHostTest {

    private static int checks = 0;
    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args.length > 0 ? args[0] : ".");
        String vod = read(root.resolve(
                "app/src/main/java/com/github/tvbox/osc/player/controller/VodController.java"));
        String play = read(root.resolve(
                "app/src/main/java/com/github/tvbox/osc/ui/fragment/PlayFragment.java"));
        String detail = read(root.resolve(
                "app/src/main/java/com/github/tvbox/osc/ui/activity/DetailActivity.java"));
        String baseDialog = read(root.resolve(
                "app/src/main/java/com/github/tvbox/osc/ui/dialog/BaseDialog.java"));
        String videoView = read(root.resolve(
                "app/src/main/java/com/github/tvbox/osc/player/MyVideoView.java"));
        String exo = read(root.resolve(
                "app/src/main/java/com/github/tvbox/osc/player/ExoPlayer.java"));
        String enhance = read(root.resolve(
                "app/src/main/java/com/github/tvbox/osc/car/CarEnhance.java"));

        // ── 1. 预览小窗不显示诊断徽标 ──────────────────────────────────────────
        check(vod.contains("private void applyDiagnosticsVisibility()"),
                "VodController must gate diagnostics on preview mode");
        check(vod.contains("if (previewMode && mSysUsageRightTop != null)"),
                "usage badge must be hidden in preview");
        check(vod.contains("mVideoFrameRate.setVisibility(previewMode ? GONE : VISIBLE)"),
                "frame-rate badge must follow preview mode");
        check(vod.contains("if (!previewMode) mVideoSize.setText"),
                "resolution badge must not be refreshed in preview");
        check(vod.contains("this.previewMode = previewMode;"),
                "setPreviewMode must record the flag");
        check(vod.contains("applyDiagnosticsVisibility();"),
                "setPreviewMode must apply the visibility immediately");
        String sizeText = slice(vod, "private String buildVideoSizeText", "private String buildUsageText");
        check(sizeText.contains("if (previewMode) return text;"),
                "SR suffix must be skipped in preview");

        // ── 2. 别的窗口抢焦点 → 暂停；自家弹窗不算 ─────────────────────────────
        check(detail.contains("public void onWindowFocusChanged(boolean hasFocus)"),
                "DetailActivity must react to window focus changes");
        check(detail.contains("playFragment.onWindowFocusLost()")
                        && detail.contains("playFragment.onWindowFocusGained()"),
                "focus changes must be forwarded to the play fragment");
        String lost = slice(play, "public void onWindowFocusLost()", "public void onWindowFocusGained()");
        check(lost.contains("BaseDialog.isAnyVisible()"),
                "own dialogs must not be treated as background playback");
        check(lost.contains("if (!mVideoView.isPlaying()) return;"),
                "focus loss must only pause an actually playing player");
        check(lost.contains("pausedByFocusLoss = true;"),
                "focus-loss pause must be remembered");
        String gained = slice(play, "public void onWindowFocusGained()", "@Override");
        check(gained.contains("if (!pausedByFocusLoss) return;"),
                "focus gain must not resume a manual pause");
        check(baseDialog.contains("public static boolean isAnyVisible()"),
                "BaseDialog must expose the visible counter");
        check(baseDialog.contains("visibleCount++") && baseDialog.contains("visibleCount - 1"),
                "BaseDialog must count shown dialogs");

        // ── 3. 换集/新起播时增益立即到位 ───────────────────────────────────────
        check(enhance.contains("public static void applyAudio(MyVideoView view, boolean force)"),
                "CarEnhance must offer a forced apply path");
        check(enhance.contains("if (!force && Math.abs(gain - lastAppliedGain)"),
                "idempotent short-circuit must be bypassable by force");
        check(videoView.contains("CarEnhance.applyAudio(this, true);"),
                "prepare path must force-apply the saved gain");
        check(videoView.contains("boolean readyOrPlaying = state == STATE_PREPARED || state == STATE_PLAYING;"),
                "PREPARED/PLAYING must force-apply the gain");
        check(exo.contains("readVolumePercent()"),
                "ExoPlayer must seed audioGain from the saved preference");
        check(exo.contains("audioGain = com.github.tvbox.osc.car.VolumeBoostMath.clampGain"),
                "ExoPlayer must initialise audioGain on initPlayer");

        if (failures.isEmpty()) {
            System.out.println("VolumeFocusRegressionHostTest PASS (" + checks + " checks)");
        } else {
            for (String failure : failures) System.out.println("FAIL: " + failure);
            throw new AssertionError(failures.size() + " of " + checks + " checks failed");
        }
    }

    private static String slice(String text, String from, String to) {
        int start = text.indexOf(from);
        if (start < 0) return "";
        int end = text.indexOf(to, start + from.length());
        return end < 0 ? text.substring(start) : text.substring(start, end);
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) failures.add(message);
    }
}
