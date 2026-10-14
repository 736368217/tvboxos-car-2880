import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/**
 * Standalone port of com.github.catvod.crawler.ProtectedInitJar.Dex.isProtected()
 * used to prove the white-shell misclassification and the fix.
 *
 *   OLD scan : whitelist const-strings only from the method literally named "init"
 *   NEW scan : whitelist const-strings from every method of every Init* class
 */
public class WhitelistScanTest {

    static final String PKG = "com.github.tvbox.osc.jun";
    static final String AES_KEY = "1234123412341234";

    public static void main(String[] args) throws Exception {
        File jar = new File(args.length > 0 ? args[0] : "spider.jar");
        String pkg = args.length > 1 ? args[1] : PKG;
        ZipFile zip = new ZipFile(jar);
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry e = entries.nextElement();
            if (!e.getName().endsWith(".dex")) continue;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            InputStream is = zip.getInputStream(e);
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
            is.close();
            System.out.println("=== dex entry: " + e.getName() + " (" + baos.size() + " bytes)");
            new Dex(baos.toByteArray(), pkg).report();
        }
        zip.close();
    }

    static class Dex {
        final byte[] data;
        final int stringIdsSize, stringIdsOff, typeIdsSize, typeIdsOff,
                  methodIdsSize, methodIdsOff, classDefsSize, classDefsOff;
        final String pkg;
        final List<String> hits = new ArrayList<>();

        Dex(byte[] data, String pkg) {
            this.data = data;
            this.pkg = pkg;
            this.stringIdsSize = uint(0x38);
            this.stringIdsOff = uint(0x3c);
            this.typeIdsSize = uint(0x40);
            this.typeIdsOff = uint(0x44);
            this.methodIdsSize = uint(0x58);
            this.methodIdsOff = uint(0x5c);
            this.classDefsSize = uint(0x60);
            this.classDefsOff = uint(0x64);
        }

        void report() {
            boolean hasInit = false, hasKill = false, checkedWl = false;
            Boolean oldProtected = null, newProtected = null;
            int initClasses = 0;
            // ---- OLD algorithm (verbatim structure of upstream isProtected) ----
            for (int i = 0; i < classDefsSize; i++) {
                int off = classDefsOff + i * 32;
                String desc = typeDesc(uint(off));
                if (!desc.startsWith("Lcom/github/catvod/spider/Init")) continue;
                initClasses++;
                hasInit = hasInit || "Lcom/github/catvod/spider/Init;".equals(desc);
                int classDataOff = uint(off + 24);
                if (classDataOff <= 0) continue;
                if ("Lcom/github/catvod/spider/Init;".equals(desc)) {
                    checkedWl = true;
                    if (scanMethods(classDataOff, true)) { oldProtected = Boolean.FALSE; break; }
                }
                hasKill = hasKill || scanKillProcess(classDataOff);
                if (hasInit && hasKill && checkedWl) { oldProtected = true; break; }
            }
            if (oldProtected == null) oldProtected = hasInit && hasKill;

            // ---- NEW algorithm (all Init* classes, all methods) ----
            boolean hasInit2 = false, hasKill2 = false, checkedWl2 = false;
            for (int i = 0; i < classDefsSize; i++) {
                int off = classDefsOff + i * 32;
                String desc = typeDesc(uint(off));
                if (!desc.startsWith("Lcom/github/catvod/spider/Init")) continue;
                hasInit2 = hasInit2 || "Lcom/github/catvod/spider/Init;".equals(desc);
                int classDataOff = uint(off + 24);
                if (classDataOff <= 0) continue;
                checkedWl2 = true;
                if (scanMethods(classDataOff, false)) { newProtected = Boolean.FALSE; break; }
                hasKill2 = hasKill2 || scanKillProcess(classDataOff);
                if (hasInit2 && hasKill2 && checkedWl2) { newProtected = true; break; }
            }
            if (newProtected == null) newProtected = hasInit2 && hasKill2;

            System.out.println("  Init* classes found        : " + initClasses);
            System.out.println("  hasInit=" + hasInit + " hasKillProcess=" + hasKill + " checkedWhitelist=" + checkedWl);
            System.out.println("  OLD scan isProtected       : " + oldProtected + (oldProtected ? "  <-- white shell treats JAR as protected" : ""));
            System.out.println("  NEW scan isProtected       : " + newProtected + (newProtected ? "" : "  <-- fixed: normal Init.init(Context) path"));
            System.out.println("  package whitelist hits     : " + hits);
        }

        boolean scanMethods(int off, boolean initOnly) {
            int[] cursor = new int[]{off};
            int sf = uleb(cursor), inf = uleb(cursor), dm = uleb(cursor), vm = uleb(cursor);
            for (int i = 0; i < sf + inf; i++) { uleb(cursor); uleb(cursor); }
            return scanMethodList(cursor, dm, initOnly) || scanMethodList(cursor, vm, initOnly);
        }

        boolean scanMethodList(int[] cursor, int count, boolean initOnly) {
            int methodIdx = 0;
            for (int i = 0; i < count; i++) {
                methodIdx += uleb(cursor);
                uleb(cursor);
                int codeOff = uleb(cursor);
                if (codeOff <= 0 || methodIdx >= methodIdsSize) continue;
                String name = methodName(methodIdx);
                if (initOnly && !"init".equals(name)) continue;
                if (scanWhitelist(codeOff, name)) return true;
            }
            return false;
        }

        boolean scanWhitelist(int codeOff, String methodName) {
            int insnsSize = uint(codeOff + 12);
            int insnsOff = codeOff + 16;
            int end = insnsOff + insnsSize * 2;
            for (int off = insnsOff; off + 1 < end && off + 1 < data.length; off += 2) {
                int op = data[off] & 0xff;
                String value = null;
                if (op == 0x1a && off + 3 < end) value = string(ushort(off + 2));
                else if (op == 0x1b && off + 5 < end) value = string(uint(off + 2));
                if (value == null) continue;
                if (value.contains(pkg)) hits.add(methodName + ":PLAIN-HIT");
                String dec = decrypt(value);
                if (!dec.isEmpty()) {
                    boolean hit = containsItem(dec, pkg);
                    hits.add(methodName + ":AES(" + dec.length() + " chars)" + (hit ? "-HIT" : ""));
                    if (hit) return true;
                }
                if (containsItem(value, pkg)) return true;
            }
            return false;
        }

        boolean scanKillProcess(int off) {
            int[] cursor = new int[]{off};
            int sf = uleb(cursor), inf = uleb(cursor), dm = uleb(cursor), vm = uleb(cursor);
            for (int i = 0; i < sf + inf; i++) { uleb(cursor); uleb(cursor); }
            return scanKillList(cursor, dm) || scanKillList(cursor, vm);
        }

        boolean scanKillList(int[] cursor, int count) {
            int methodIdx = 0;
            for (int i = 0; i < count; i++) {
                methodIdx += uleb(cursor);
                uleb(cursor);
                int codeOff = uleb(cursor);
                if (codeOff > 0 && scanKillCode(codeOff)) return true;
            }
            return false;
        }

        boolean scanKillCode(int codeOff) {
            int insnsSize = uint(codeOff + 12);
            int insnsOff = codeOff + 16;
            int end = insnsOff + insnsSize * 2;
            for (int off = insnsOff; off + 3 < end && off + 3 < data.length; off += 2) {
                int op = data[off] & 0xff;
                if (op >= 0x6e && op <= 0x72) {
                    int mi = ushort(off + 2);
                    if (mi >= 0 && mi < methodIdsSize
                            && "Landroid/os/Process;".equals(methodClass(mi))
                            && "killProcess".equals(methodName(mi))) return true;
                }
            }
            return false;
        }

        String methodClass(int mi) { return typeDesc(ushort(methodIdsOff + mi * 8)); }
        String methodName(int mi) { return string(uint(methodIdsOff + mi * 8 + 4)); }
        String typeDesc(int ti) { return (ti < 0 || ti >= typeIdsSize) ? "" : string(uint(typeIdsOff + ti * 4)); }

        String string(int si) {
            if (si < 0 || si >= stringIdsSize) return "";
            int[] cursor = new int[]{uint(stringIdsOff + si * 4)};
            uleb(cursor);
            int start = cursor[0], end = start;
            while (end < data.length && data[end] != 0) end++;
            return new String(data, start, end - start, StandardCharsets.UTF_8);
        }

        int uleb(int[] c) {
            int r = 0, s = 0;
            while (c[0] < data.length) {
                int b = data[c[0]++] & 0xff;
                r |= (b & 0x7f) << s;
                if ((b & 0x80) == 0) break;
                s += 7;
            }
            return r;
        }

        int ushort(int off) { return (off < 0 || off + 1 >= data.length) ? 0 : (data[off] & 0xff) | ((data[off + 1] & 0xff) << 8); }
        int uint(int off) {
            if (off < 0 || off + 3 >= data.length) return 0;
            return (data[off] & 0xff) | ((data[off + 1] & 0xff) << 8) | ((data[off + 2] & 0xff) << 16) | ((data[off + 3] & 0xff) << 24);
        }
    }

    static boolean containsItem(String text, String pkg) {
        if (text == null || text.isEmpty()) return false;
        int start = 0;
        while (start <= text.length()) {
            int end = text.indexOf(',', start);
            if (end < 0) end = text.length();
            if (pkg.equals(text.substring(start, end).trim())) return true;
            start = end + 1;
        }
        return false;
    }

    static String decrypt(String value) {
        if (!isCipherText(value)) return "";
        try {
            byte[] key = AES_KEY.getBytes(StandardCharsets.UTF_8);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(key));
            byte[] raw = Base64.getDecoder().decode(value);
            return new String(cipher.doFinal(raw), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return "";
        }
    }

    static boolean isCipherText(String value) {
        int len = value.length();
        if (len < 24 || len % 4 != 0) return false;
        int padding = 0;
        for (int i = 0; i < len; i++) {
            char c = value.charAt(i);
            if (c == '=') { padding++; if (i < len - 2) return false; }
            else if (!((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '+' || c == '/')) return false;
        }
        int rawLen = len * 3 / 4 - padding;
        return rawLen > 0 && rawLen % 16 == 0;
    }
}
