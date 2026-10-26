package xyz.doikki.videoplayer.render;

import java.util.ArrayList;
import java.util.List;

/**
 * 轻量 mpv {@code //!HOOK} 着色器转译器（方案 v2.0 §3.2）。
 *
 * <p>把社区单文件多 Pass 模型（ACNetGLSL 的 {@code acnet_f8b4_hdn.glsl}、CuNNy 等）
 * 拆成一组 GLES2 Fragment Shader。只做语法桥接：为每个 {@code //!BIND} 生成 sampler 与
 * {@code <NAME>_pos/_pt/_size/_tex/_texOff} 宏，和 mpv 的语义保持一致；模型权重与算法原样保留。</p>
 */
public final class MpvHookShaderCompiler {

    private MpvHookShaderCompiler() {
    }

    public static final class PassSpec {
        public String desc = "";
        public String hook = "MAIN";
        public String save = "";           // 空表示最终 Pass，输出到屏幕
        public final List<String> binds = new ArrayList<>();
        public int scaleX = 1;
        public int scaleY = 1;
        public int program;
        public String fragmentShaderSource;
    }

    private static final String HEADER =
            "#ifdef GL_FRAGMENT_PRECISION_HIGH\nprecision highp float;\n#else\nprecision mediump float;\n#endif\n"
                    + "varying highp vec2 vTexCoord;\n";

    public static List<PassSpec> parse(String rawGlsl) {
        List<PassSpec> passes = new ArrayList<>();
        PassSpec current = null;
        StringBuilder body = new StringBuilder();
        for (String rawLine : rawGlsl.split("\n")) {
            String line = rawLine.trim();
            if (line.startsWith("//!DESC")) {
                if (current != null && body.length() > 0) {
                    current.fragmentShaderSource = build(current, body.toString());
                    passes.add(current);
                }
                current = new PassSpec();
                current.desc = line.length() > 7 ? line.substring(7).trim() : "";
                body.setLength(0);
            } else if (current == null) {
                continue;
            } else if (line.startsWith("//!BIND")) {
                String name = line.substring(7).trim();
                if (!name.isEmpty() && !current.binds.contains(name)) current.binds.add(name);
            } else if (line.startsWith("//!SAVE")) {
                current.save = line.substring(7).trim();
            } else if (line.startsWith("//!HOOK")) {
                current.hook = line.substring(7).trim();
            } else if (line.startsWith("//!WIDTH")) {
                if (line.contains("2 *")) current.scaleX = 2;
            } else if (line.startsWith("//!HEIGHT")) {
                if (line.contains("2 *")) current.scaleY = 2;
            } else if (line.startsWith("//!")) {
                // //!WHEN / //!COMPONENTS 等：条件恒成立，分量数对 RGBA8 目标无影响
            } else {
                body.append(rawLine).append('\n');
            }
        }
        if (current != null && body.length() > 0) {
            current.fragmentShaderSource = build(current, body.toString());
            passes.add(current);
        }
        return passes;
    }

    private static String build(PassSpec spec, String hookBody) {
        StringBuilder sb = new StringBuilder(HEADER);
        for (String bind : spec.binds) {
            String safe = bind.replaceAll("[^A-Za-z0-9_]", "_");
            sb.append("uniform sampler2D uTex_").append(safe).append(";\n");
            sb.append("uniform highp vec2 uPt_").append(safe).append(";\n");
            sb.append("uniform highp vec2 uSize_").append(safe).append(";\n");
            sb.append("#define ").append(bind).append("_pos vTexCoord\n");
            sb.append("#define ").append(bind).append("_pt uPt_").append(safe).append("\n");
            sb.append("#define ").append(bind).append("_size uSize_").append(safe).append("\n");
            sb.append("#define ").append(bind).append("_tex(p) texture2D(uTex_").append(safe).append(", p)\n");
            sb.append("#define ").append(bind).append("_texOff(off) texture2D(uTex_").append(safe)
                    .append(", vTexCoord + (off) * uPt_").append(safe).append(")\n");
            sb.append("#define ").append(bind).append("_mul vec4(1.0)\n");
        }
        // mpv 的 hook() 返回值；部分模型写法用 max(x, 0.0) 与 vec 混用，ES 严格类型下补成 vec4。
        String fixed = hookBody
                .replaceAll("max\\(([^,()]+),\\s*([0-9.]+)\\)", "max($1, vec4($2))")
                .replaceAll("min\\(([^,()]+),\\s*([0-9.]+)\\)", "min($1, vec4($2))");
        sb.append(fixed);
        sb.append("void main() { gl_FragColor = hook(); }\n");
        return sb.toString();
    }
}
