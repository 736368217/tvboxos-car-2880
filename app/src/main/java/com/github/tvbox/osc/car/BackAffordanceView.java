package com.github.tvbox.osc.car;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * carclash {@code EdgeSwipeView} 的原样移植（去掉 WindowManager：面板画在 Activity 内，不申请悬浮窗）。
 *
 * <p>几何、颜色、贝塞尔形状、勾选箭头与触发进度全部照搬：静止时**不画任何东西**（只有一条透明的
 * 36dp 触摸带），按下后拉出纯黑透镜面板，宽度 = min(180dp, 向内拖动距离 × 0.6)，上下各 180dp，
 * 箭头透明度随进度 80→230，进度 ≥ 0.62 时出现第二个箭头。
 */
final class BackAffordanceView extends View {
    static final float BAND_DP = 36f;
    static final float TRIGGER_DP = 45f;
    static final float PEAK_DP = 180f;
    static final float HALF_HEIGHT_DP = 180f;
    static final float PANEL_SCALE = 0.60f;

    private final Paint panelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path panelPath = new Path();
    private final Path arrowPath = new Path();
    private boolean visible = true;
    private boolean pullingLeft = true;
    private float distance;
    private float pullY;

    BackAffordanceView(Context context) {
        super(context);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        panelPaint.setStyle(Paint.Style.FILL);
        panelPaint.setColor(0xFF000000);
        arrowPaint.setStyle(Paint.Style.STROKE);
        arrowPaint.setStrokeWidth(dp(3f));
        arrowPaint.setStrokeCap(Paint.Cap.ROUND);
        arrowPaint.setStrokeJoin(Paint.Join.ROUND);
        arrowPaint.setColor(0xFFFFFFFF);
    }

    /** Kept for the host's call shape; carclash's geometry is density-based, not screen-metric based. */
    void configure(BackDisplayMetrics metrics, EdgeBackPolicy.Mode mode, EdgeBackPolicy.Side side) {
        if (side != null) pullingLeft = side != EdgeBackPolicy.Side.RIGHT;
        invalidate();
    }

    void setPresentation(boolean visible, boolean pressed) {
        this.visible = visible;
        invalidate();
    }

    /** {@code distance} is the inward drag in pixels, exactly what EdgeSwipeView tracks. */
    void setGesture(float distance, float y, boolean fromLeft) {
        this.distance = Math.max(0f, distance);
        this.pullY = y;
        this.pullingLeft = fromLeft;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!visible || distance <= 0f) return;

        float peak = dp(PEAK_DP);
        float half = dp(HALF_HEIGHT_DP);
        float panelWidth = Math.min(peak, distance * PANEL_SCALE);
        if (panelWidth < dp(2f)) return;

        int width = getWidth();
        float y = pullY;
        float top = y - half;
        float bottom = y + half;

        panelPath.reset();
        if (pullingLeft) {
            panelPath.moveTo(0f, top);
            panelPath.cubicTo(panelWidth, y, panelWidth, y, 0f, bottom);
        } else {
            panelPath.moveTo(width, top);
            panelPath.cubicTo(width - panelWidth, y, width - panelWidth, y, width, bottom);
        }
        canvas.drawPath(panelPath, panelPaint);

        float progress = Math.min(1f, distance / (peak / PANEL_SCALE));
        drawArrow(canvas, y, panelWidth, progress, width);
    }

    private void drawArrow(Canvas canvas, float y, float panelWidth, float progress, int width) {
        float inset = Math.min(dp(56f), Math.max(dp(14f), panelWidth * 0.52f));
        float cx = pullingLeft ? inset : width - inset;
        int direction = pullingLeft ? 1 : -1;
        arrowPaint.setAlpha((int) (80 + 150 * progress));

        drawChevron(canvas, cx, y, direction, dp(9f), dp(18f));
        if (progress >= 0.62f) {
            drawChevron(canvas, cx - direction * dp(9f), y, direction, dp(9f), dp(18f));
        }
    }

    private void drawChevron(Canvas canvas, float cx, float cy, int direction,
                             float width, float height) {
        arrowPath.reset();
        arrowPath.moveTo(cx - direction * width / 2f, cy - height / 2f);
        arrowPath.lineTo(cx + direction * width / 2f, cy);
        arrowPath.lineTo(cx - direction * width / 2f, cy + height / 2f);
        canvas.drawPath(arrowPath, arrowPaint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
