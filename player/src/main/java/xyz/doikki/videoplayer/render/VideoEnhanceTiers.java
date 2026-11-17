package xyz.doikki.videoplayer.render;

/** 超分 / 补帧档位常量与显示名（0=关闭，1=当前已验证基线）。 */
public final class VideoEnhanceTiers {
    public static final int SR_OFF = 0;
    public static final int SR_SGSR = 1;
    public static final int SR_FSR = 2;
    public static final int SR_ANIME = 3;

    public static final int MEMC_OFF = 0;
    public static final int MEMC_OVERSAMPLE = 1;
    public static final int MEMC_CUBIC = 2;
    public static final int MEMC_SAFE_FLOW = 3;

    private static final String[] SR_NAMES = {"关闭", "标准全能 (SGSR1)", "影视专精 (FSR)", "动漫线条 (Anime)"};
    private static final String[] MEMC_NAMES = {"关闭", "基础平滑 (Oversample)", "高阶时域 (4帧 Cubic)", "保守光流 (Safe-Flow)"};

    private VideoEnhanceTiers() {
    }

    public static int clampSr(int tier) {
        return tier < SR_OFF ? SR_OFF : Math.min(tier, SR_ANIME);
    }

    public static int clampMemc(int tier) {
        return tier < MEMC_OFF ? MEMC_OFF : Math.min(tier, MEMC_SAFE_FLOW);
    }

    public static String srName(int tier) {
        int t = clampSr(tier);
        return SR_NAMES[t];
    }

    public static String memcName(int tier) {
        int t = clampMemc(tier);
        return MEMC_NAMES[t];
    }

    /** 旧布尔开关 -> 档位：开=1 档基线，关=0 档。 */
    public static int fromLegacy(boolean enabled) {
        return enabled ? 1 : 0;
    }
}
