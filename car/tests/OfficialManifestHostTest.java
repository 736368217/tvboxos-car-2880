import com.github.tvbox.osc.update.OfficialManifest;
import com.github.tvbox.osc.update.UpdateVersion;

/**
 * 宿主机检查：官方发布通道（阿里云 latest.json）的解析与版本比较。
 *
 * <pre>
 * javac -encoding UTF-8 -d out app/src/main/java/com/github/tvbox/osc/update/OfficialManifest.java \
 *       app/src/main/java/com/github/tvbox/osc/update/UpdateVersion.java car/tests/OfficialManifestHostTest.java
 * java -cp out OfficialManifestHostTest
 * </pre>
 */
public final class OfficialManifestHostTest {

    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void eq(Object actual, Object expected, String message) {
        checks++;
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " (expected=" + expected + ", actual=" + actual + ")");
        }
    }

    private static final String BASE = "https://dl.z736.vip/";
    private static final String SHA =
            "3489BA3CFFE491B1FF2A6D0AC3F41A6C3D12EEE86FEFED47616E44C0579F1976";

    public static void main(String[] args) {
        // 发布脚本生成的真实形状
        String json = "{ \"software\": \"tvbox-car\", \"version\": \"3.1.0-car.7-r65\",\n"
                + "  \"files\": [ { \"name\": \"TVBox-3.1.0-car.7-r65.apk\", \"size\": 52734169,\n"
                + "                 \"sha256\": \"" + SHA + "\",\n"
                + "                 \"url\": \"/tvbox-car/3.1.0-car.7-r65/TVBox-3.1.0-car.7-r65.apk\" } ],\n"
                + "  \"releasedAt\": \"2026-11-04T12:33:30+0800\" }";
        OfficialManifest manifest = OfficialManifest.parse(json, BASE);
        eq(manifest.software, "tvbox-car", "software");
        eq(manifest.version, "3.1.0-car.7-r65", "version");
        eq(manifest.apkName, "TVBox-3.1.0-car.7-r65.apk", "file name");
        eq(manifest.apkSize, 52734169L, "file size");
        eq(manifest.sha256, SHA, "sha256");
        eq(manifest.apkUrl,
                "https://dl.z736.vip/tvbox-car/3.1.0-car.7-r65/TVBox-3.1.0-car.7-r65.apk",
                "leading slash resolved against base");

        // 相对路径（无前导斜杠）与已绝对地址
        eq(OfficialManifest.parse(
                "{\"version\":\"1.0\",\"files\":[{\"url\":\"tvbox-car/1.0/a.apk\"}]}", BASE).apkUrl,
                "https://dl.z736.vip/tvbox-car/1.0/a.apk", "relative path resolved");
        eq(OfficialManifest.parse(
                "{\"version\":\"1.0\",\"files\":[{\"url\":\"https://cdn.example/a.apk\"}]}", BASE).apkUrl,
                "https://cdn.example/a.apk", "absolute url kept");
        eq(OfficialManifest.parse(
                "{\"version\":\"1.0\",\"files\":[{\"url\":\"https://cdn.example/a.apk\"}]}",
                "https://other/").apkUrl, "https://cdn.example/a.apk",
                "absolute url ignores base");

        // 缺 version / 非对象：必须报错，不能当成「已是最新」
        boolean threw = false;
        try {
            OfficialManifest.parse("{\"software\":\"x\"}", BASE);
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        check(threw, "missing version rejected");
        threw = false;
        try {
            OfficialManifest.parse("<html>404</html>", BASE);
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        check(threw, "html (captive portal / 404 page) rejected");
        threw = false;
        try {
            OfficialManifest.parse(null, BASE);
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        check(threw, "null rejected");

        // sha256 长度不对时忽略（不能拿半截哈希去判失败）
        eq(OfficialManifest.parse(
                "{\"version\":\"1.0\",\"files\":[{\"url\":\"/a.apk\",\"sha256\":\"abc\"}]}", BASE).sha256,
                null, "short sha256 ignored");

        // files 多个对象取第一个；字符串里的花括号不能把对象截断
        OfficialManifest multi = OfficialManifest.parse(
                "{\"version\":\"2.0\",\"files\":[{\"name\":\"a}.apk\",\"url\":\"/a.apk\",\"size\":7},"
                        + "{\"name\":\"b.apk\",\"url\":\"/b.apk\"}]}", BASE);
        eq(multi.apkName, "a}.apk", "brace inside string does not split object");
        eq(multi.apkSize, 7L, "first file wins");

        // notes 转义与可选性
        eq(OfficialManifest.parse(
                "{\"version\":\"1.0\",\"notes\":\"修 \\\"音量\\\"\\n第二行\",\"files\":[{\"url\":\"/a.apk\"}]}",
                BASE).notes, "修 \"音量\"\n第二行", "notes unescaped");
        eq(OfficialManifest.parse("{\"version\":\"1.0\"}", BASE).notes, "", "notes optional");
        // r75 真实发布形状：notes 位于 files/releasedAt 之后，且含转义换行。
        String published = "{\"software\":\"tvbox-car\",\"version\":\"3.1.0-car.7-r74\","
                + "\"files\":[{\"name\":\"TVBox.apk\",\"url\":\"/tvbox-car/r74/TVBox.apk\"}],"
                + "\"releasedAt\":\"2026-11-10T18:25:45+0800\","
                + "\"notes\":\"r74 更新说明\\n- 修复历史封面\\n- 用户需注意\"}";
        OfficialManifest notesAfterFiles = OfficialManifest.parse(published, BASE);
        eq(notesAfterFiles.notes, "r74 更新说明\n- 修复历史封面\n- 用户需注意",
                "notes after files array parsed");
        eq(notesAfterFiles.apkName, "TVBox.apk", "files metadata still parsed with trailing notes");

        // 版本比较仍按迭代号优先（官方清单用 versionName 写法）
        check(UpdateVersion.hasUpdate("3.1.0-car.7-r64", "3.1.0-car.7-r65"), "r64 -> r65 is an update");
        check(!UpdateVersion.hasUpdate("3.1.0-car.7-r65", "3.1.0-car.7-r65"), "same version is not an update");
        check(!UpdateVersion.hasUpdate("3.1.0-car.7-r66", "3.1.0-car.7-r65"), "older remote is not an update");
        check(UpdateVersion.hasUpdate("1.4.0", "1.4.1"), "semantic versions still work");
        check(!UpdateVersion.hasUpdate("3.1.0-car.7-r65", "garbage"), "unparsable remote is not an update");

        System.out.println("OfficialManifestHostTest PASS (" + checks + " checks)");
    }
}
