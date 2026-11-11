package com.github.tvbox.osc.car;

import java.util.Locale;

/**
 * 方案 A：按「屏幕分辨率占比 + 可信密度换算的物理目标」计算返回组件几何。
 *
 * <p>不能把对标作品的 50/120/180px 写死：车机 2880×1620 / density 160 上，50px 是 1.7% 屏宽；
 * 换到 1280×720 或 3840×2160 后，同样 50px 会变成 3.9% / 1.3%，视觉和手感都跑偏。</p>
 *
 * <p>零跑 B01 的 {@code DisplayMetrics.xdpi/ydpi} 实测是 91.44 的虚拟化桩值；MuMu 在
 * density 被改成 160 后仍报告 xdpi=440。两者都不能直接当车机物理 PPI。这里只有当
 * xdpi/ydpi 与逻辑 densityDpi 落在正常设备范围（0.75–1.33 倍）时才采信为物理 PPI，
 * 否则回退 logical density。再用宽高百分比和最小可触摸像素双向夹取：
 * 大屏由毫米目标主导，小屏由分辨率占比主导，任何分辨率都只是边缘窄带。</p>
 */
public final class BackDisplayMetrics {

    /** 目标物理尺寸（毫米），对标 SNG 3.0 逆向报告与车机触摸可用性折中。 */
    public static final float HIT_MM = 8f;
    public static final float VISUAL_MM = 20f;
    public static final float TRIGGER_MM = 18f;
    public static final float FLING_MM_PER_SEC = 220f;
    public static final float LEAF_HALF_MM = 30f;
    public static final float BAR_WIDTH_MM = 4f;
    public static final float BAR_HEIGHT_MM = 38f;
    public static final float BAR_HIT_WIDTH_MM = 10f;
    public static final float BAR_HIT_HEIGHT_MM = 48f;
    public static final float SLOP_MM = 2f;

    /** 分辨率占比与绝对像素双上限；保证视觉永不接近半屏，也不因写死像素而缩小。 */
    public static final float HIT_FRACTION = .03f;
    public static final float VISUAL_FRACTION = .05f;
    public static final float LEAF_FRACTION = .11f;
    public static final float BAR_WIDTH_FRACTION = .015f;
    public static final float BAR_HEIGHT_FRACTION = .22f;
    public static final float BAR_HIT_WIDTH_FRACTION = .04f;
    public static final float BAR_HIT_HEIGHT_FRACTION = .28f;
    public static final float HIT_CAP_PX = 96f;
    public static final float VISUAL_CAP_PX = 160f;
    public static final float LEAF_CAP_PX = 210f;
    public static final float DPI_TRUST_MIN_RATIO = .75f;
    public static final float DPI_TRUST_MAX_RATIO = 1.33f;

    public final float widthPx, heightPx;
    public final float xdpi, ydpi, densityDpi;
    public final boolean trustedPhysicalPpiX, trustedPhysicalPpiY;
    public final float effectivePpiX, effectivePpiY;
    public final float pxPerMmX, pxPerMmY;
    public final float estimatedWidthMm, estimatedHeightMm;
    public final float hitPx, visualPx, triggerPx, leafHalfPx, slopPx;
    public final float guidePx, barWidthPx, barHeightPx;
    public final float barHitWidthPx, barHitHeightPx;

    private BackDisplayMetrics(float widthPx, float heightPx, float xdpi, float ydpi, float densityDpi,
                               boolean trustedX, boolean trustedY,
                               float effectivePpiX, float effectivePpiY,
                               float pxPerMmX, float pxPerMmY,
                               float estimatedWidthMm, float estimatedHeightMm,
                               float hitPx, float visualPx, float triggerPx, float leafHalfPx, float slopPx,
                               float guidePx, float barWidthPx, float barHeightPx,
                               float barHitWidthPx, float barHitHeightPx) {
        this.widthPx = widthPx;
        this.heightPx = heightPx;
        this.xdpi = xdpi;
        this.ydpi = ydpi;
        this.densityDpi = densityDpi;
        this.trustedPhysicalPpiX = trustedX;
        this.trustedPhysicalPpiY = trustedY;
        this.effectivePpiX = effectivePpiX;
        this.effectivePpiY = effectivePpiY;
        this.pxPerMmX = pxPerMmX;
        this.pxPerMmY = pxPerMmY;
        this.estimatedWidthMm = estimatedWidthMm;
        this.estimatedHeightMm = estimatedHeightMm;
        this.hitPx = hitPx;
        this.visualPx = visualPx;
        this.triggerPx = triggerPx;
        this.leafHalfPx = leafHalfPx;
        this.slopPx = slopPx;
        this.guidePx = guidePx;
        this.barWidthPx = barWidthPx;
        this.barHeightPx = barHeightPx;
        this.barHitWidthPx = barHitWidthPx;
        this.barHitHeightPx = barHitHeightPx;
    }

    public static BackDisplayMetrics from(float widthPx, float heightPx,
                                          float xdpi, float ydpi, float densityDpi) {
        float w = Math.max(1f, finiteOr(widthPx, 2880f));
        float h = Math.max(1f, finiteOr(heightPx, 1620f));
        float logical = clamp(finiteOr(densityDpi, 160f), 80f, 640f);
        float reportedX = finiteOr(xdpi, logical);
        float reportedY = finiteOr(ydpi, logical);
        boolean trustedX = plausiblePhysicalPpi(reportedX, logical);
        boolean trustedY = plausiblePhysicalPpi(reportedY, logical);
        float dx = trustedX ? reportedX : logical;
        float dy = trustedY ? reportedY : logical;
        float pxPerMmX = dx / 25.4f;
        float pxPerMmY = dy / 25.4f;

        float hit = fit(HIT_MM * pxPerMmX, 36f, Math.min(w * HIT_FRACTION, HIT_CAP_PX));
        float visual = fit(VISUAL_MM * pxPerMmX, 72f, Math.min(w * VISUAL_FRACTION, VISUAL_CAP_PX));
        float trigger = Math.min(visual, fit(TRIGGER_MM * pxPerMmX, 64f, visual));
        float leafHalf = fit(LEAF_HALF_MM * pxPerMmY, 72f, Math.min(h * LEAF_FRACTION, LEAF_CAP_PX));
        float slop = clamp(SLOP_MM * pxPerMmX, 8f, 24f);

        float barWidth = fit(BAR_WIDTH_MM * pxPerMmX, 7f, Math.min(w * BAR_WIDTH_FRACTION, 30f));
        float barHeight = fit(BAR_HEIGHT_MM * pxPerMmY, 90f, Math.min(h * BAR_HEIGHT_FRACTION, 260f));
        float barHitWidth = fit(BAR_HIT_WIDTH_MM * pxPerMmX, 44f, Math.min(w * BAR_HIT_WIDTH_FRACTION, 130f));
        float barHitHeight = fit(BAR_HIT_HEIGHT_MM * pxPerMmY, 110f, Math.min(h * BAR_HIT_HEIGHT_FRACTION, 320f));
        float guide = clamp(barWidth * .25f, 2f, 7f);

        return new BackDisplayMetrics(w, h, reportedX, reportedY, logical,
                trustedX, trustedY, dx, dy, pxPerMmX, pxPerMmY,
                w / dx * 25.4f, h / dy * 25.4f,
                hit, visual, trigger, leafHalf, slop,
                guide, barWidth, barHeight, Math.max(barHitWidth, barWidth),
                Math.max(barHitHeight, barHeight));
    }

    /** 只有与逻辑密度同量级的 xdpi/ydpi 才可信；车机 91.44 桩值与 MuMu 440 都会被拒绝。 */
    private static boolean plausiblePhysicalPpi(float reported, float logical) {
        float ratio = reported / Math.max(1f, logical);
        return ratio >= DPI_TRUST_MIN_RATIO && ratio <= DPI_TRUST_MAX_RATIO;
    }

    /** 物理目标优先，但用「最小可触摸像素」和「屏幕占比/绝对上限」双向夹取。 */
    private static float fit(float physical, float minPx, float fractionCapPx) {
        return clamp(physical, minPx, Math.max(minPx, fractionCapPx));
    }

    private static float finiteOr(float value, float fallback) {
        return Float.isNaN(value) || Float.isInfinite(value) || value <= 0f ? fallback : value;
    }

    private static float clamp(float value, float min, float max) {
        if (Float.isNaN(value)) return min;
        return Math.max(min, Math.min(Math.max(min, max), value));
    }

    /** 返回杠视觉左边界：贴屏幕边，右边缘时贴右边。 */
    public float barLeft(boolean right) {
        return right ? widthPx - barWidthPx : 0f;
    }

    /** 返回杠点击热区左边界：以视觉杠为基准包住它，且不越界。 */
    public float barHitLeft(boolean right) {
        float left = right ? widthPx - barHitWidthPx : 0f;
        return clamp(left, 0f, Math.max(0f, widthPx - barHitWidthPx));
    }

    public String log() {
        return String.format(Locale.US,
                "screen=%.0fx%.0f xdpi=%.1f ydpi=%.1f densityDpi=%.0f dpiTrusted=%s/%s effPpi=%.0fx%.0f"
                        + " estSize=%.0fx%.0fmm hit=%.0fpx(%.1fmm,%.2f%%)"
                        + " visual=%.0fpx(%.1fmm,%.2f%%) trigger=%.0fpx(%.1fmm,%.2f%%)"
                        + " leafHalf=%.0fpx(%.1fmm,%.2f%%) slop=%.0fpx"
                        + " bar=%.0fx%.0fpx barHit=%.0fx%.0fpx guide=%.0fpx",
                widthPx, heightPx, xdpi, ydpi, densityDpi,
                trustedPhysicalPpiX, trustedPhysicalPpiY, effectivePpiX, effectivePpiY,
                estimatedWidthMm, estimatedHeightMm,
                hitPx, hitPx / pxPerMmX, hitPx / widthPx * 100f,
                visualPx, visualPx / pxPerMmX, visualPx / widthPx * 100f,
                triggerPx, triggerPx / pxPerMmX, triggerPx / widthPx * 100f,
                leafHalfPx, leafHalfPx / pxPerMmY, leafHalfPx / heightPx * 100f,
                slopPx, barWidthPx, barHeightPx, barHitWidthPx, barHitHeightPx, guidePx);
    }
}
