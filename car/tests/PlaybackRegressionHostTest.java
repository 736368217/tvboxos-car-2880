import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Host gate for r85 regressions.
 *
 * <p>These checks read the real sources and assert the invariants that the device
 * reports broke: page switch must pause audio-only playback, resume must be limited to
 * lifecycle pauses, history and player must share one sourceKey, and resume must accept
 * the same title even when the sourceKey changed.</p>
 */
public final class PlaybackRegressionHostTest {

    private static int checks = 0;
    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args.length > 0 ? args[0] : ".");
        String play = read(root.resolve("app/src/main/java/com/github/tvbox/osc/ui/fragment/PlayFragment.java"));
        String detail = read(root.resolve("app/src/main/java/com/github/tvbox/osc/ui/activity/DetailActivity.java"));
        String adapter = read(root.resolve("app/src/main/java/com/github/tvbox/osc/ui/adapter/HistoryAdapter.java"));

        // 1. 切页面必须真正暂停，不能因为「纯音频」而跳过。
        String onPause = slice(play, "public void onPause()", "public void onResume()");
        check(!onPause.contains("!hasAudioOnlyPlayback()"),
                "onPause must not skip pausing audio-only playback");
        check(onPause.contains("mVideoView.pause()"),
                "onPause must call mVideoView.pause()");
        check(onPause.contains("pausedByLifecycle = mVideoView.isPlaying()"),
                "onPause must remember whether playback was running");

        // 2. 回来只在生命周期暂停后续播，用户手动暂停不能被自动续上。
        String onResume = slice(play, "public void onResume()", "public void onHiddenChanged");
        check(onResume.contains("if (pausedByLifecycle)"),
                "onResume must resume only after a lifecycle pause");
        check(onResume.contains("pausedByLifecycle = false"),
                "onResume must clear the lifecycle pause flag");

        // 3. 历史记录与播放器必须用同一个 sourceKey。
        String jump = slice(detail, "private void jumpToPlay()", "private void updatePreviewVodInfo()");
        check(!jump.contains("insertVod(firstsourceKey"),
                "history must not be written with firstsourceKey while the player gets sourceKey");
        check(jump.contains("String playKey = TextUtils.isEmpty(sourceKey) ? firstsourceKey : sourceKey;"),
                "history and player must share one resolved sourceKey");
        check(jump.contains("insertVod(playKey, vodInfo)"),
                "history must be written with the resolved key");
        check(jump.contains("bundle.putString(\"sourceKey\", playKey)"),
                "player must receive the same resolved key");

        // 4. 历史不再退化成「搜」占位。
        check(!adapter.contains("\"搜\""),
                "history must not fall back to the 搜 placeholder");
        check(adapter.contains("item.sourceName"),
                "history must fall back to the stored source name");

        // 5. 同一部片子在 sourceKey 变化后仍要恢复进度。
        String setData = slice(play, "public void setData(Bundle bundle)", "private void initData()");
        check(!setData.contains("TextUtils.equals(sourceKey, com.github.tvbox.osc.cache.PlaybackSnapshot.sourceKey(getContext()))\n                && mVodInfo"),
                "resume must not require an exact sourceKey match");
        check(setData.contains("PlaybackSnapshot.vodId(getContext())"),
                "resume must match on the title id");
        check(setData.contains("snapshotForSameVod"),
                "resume must be keyed by the same title, not the same source");

        // 6. 新字段必须可被 Gson 序列化（历史记录要能存下站点名）。
        String vodInfo = read(root.resolve("app/src/main/java/com/github/tvbox/osc/bean/VodInfo.java"));
        check(vodInfo.contains("public String sourceName;"),
                "VodInfo must carry sourceName for history display");

        if (failures.isEmpty()) {
            System.out.println("PlaybackRegressionHostTest PASS (" + checks + " checks)");
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
