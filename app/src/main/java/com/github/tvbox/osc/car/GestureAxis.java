package com.github.tvbox.osc.car;

/**
 * 播放手势主轴判定：一次手势只做一件事。
 *
 * <p>旧逻辑用「首个 MOVE 的逐帧 dx/dy」一次性定轴，垂直调音量时起始的一点横向抖动
 * 就会被判成进度，之后整段锁死进度。这里改成：累计位移 + 最小位移阈值 + 主轴明显占优，
 * 45° 左右的含糊方向直接放弃（宁可不动也不误触），锁定后不再重判。</p>
 */
public final class GestureAxis {

    public static final int NONE = 0;
    public static final int X = 1;
    public static final int Y = 2;
    public static final int IGNORE = 3;

    private GestureAxis() {
    }

    /** @param slopPx 最小位移，低于它一律不定轴；@param dominance 主轴需占优的倍数（1.2）。 */
    public static int decide(float deltaX, float deltaY, float slopPx,
                              float dominance, boolean canChangePosition) {
        float absX = Math.abs(deltaX);
        float absY = Math.abs(deltaY);
        if (Math.max(absX, absY) < slopPx) return NONE;
        if (absX > absY * dominance) return canChangePosition ? X : IGNORE;
        if (absY > absX * dominance) return Y;
        return IGNORE;
    }
}
