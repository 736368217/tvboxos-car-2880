package com.github.tvbox.osc.car;

/**
 * r76：边缘返回的「实际像素」边界。
 *
 * <p>旧实现把 {@code 320dp × tokenScale(3.375)} 当成滑动视觉上限，2880px 车机上达到
 * 1080px（37.5%），再加上叶形上下 574px 的鼓包，用户实测「几乎占半个屏幕」。命中宽度
 * 也被 {@code max(40dp, barWidth×3)} 放大到数百像素，页面边缘大块内容无法点。</p>
 *
 * <p>这里全部按屏幕短边/长边百分比封顶，和 AutoSize tokenScale 解耦：
 * 触发距离最高 6% 屏宽（2880px 上约 173px），视觉最大 8%（230px），命中带最高 3%
 * （86px），叶形半高最高 11% 屏高（178px）。小屏仍保留最小可触摸尺寸。</p>
 */
public final class EdgeGeometry {

    /** 触发返回所需横滑距离，最多 6% 屏宽。 */
    private static final float TRIGGER_WIDTH_FRACTION = 0.06f;
    /** 视觉拉伸带最大宽度，最多 8% 屏宽。 */
    private static final float VISUAL_WIDTH_FRACTION = 0.08f;
    /** 边缘命中带宽，最多 3% 屏宽，且不小于 24px。 */
    private static final float HIT_WIDTH_FRACTION = 0.03f;
    /** 叶形上下半高，最多 11% 屏高。 */
    private static final float LEAF_HALF_HEIGHT_FRACTION = 0.11f;

    private EdgeGeometry() {
    }

    /** 返回手势「拉出」的最大像素宽度；swipe 与 edge-pull 共用。 */
    public static float maxVisualWidthPx(float screenWidth) {
        return Math.max(48f, Math.min(screenWidth * VISUAL_WIDTH_FRACTION, 240f));
    }

    /** 触发横滑返回所需像素距离，不超过视觉上限。 */
    public static float triggerPx(float screenWidth, float configuredPx) {
        float cap = Math.max(48f, Math.min(screenWidth * TRIGGER_WIDTH_FRACTION,
                maxVisualWidthPx(screenWidth)));
        return Math.max(36f, Math.min(configuredPx, cap));
    }

    /** 真正的边缘命中宽度；内容区从这条带之外开始，绝不接近半屏。 */
    public static float hitWidthPx(float screenWidth, float configuredPx) {
        float cap = Math.max(24f, Math.min(screenWidth * HIT_WIDTH_FRACTION, 96f));
        return Math.max(24f, Math.min(configuredPx, cap));
    }

    /** 叶形/水滴的上下半高；避免高度方向也铺满半屏。 */
    public static float leafHalfHeightPx(float screenHeight, float configuredPx) {
        float cap = Math.max(36f, Math.min(screenHeight * LEAF_HALF_HEIGHT_FRACTION, 190f));
        return Math.max(36f, Math.min(configuredPx, cap));
    }
}
