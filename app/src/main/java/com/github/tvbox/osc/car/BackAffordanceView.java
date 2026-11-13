package com.github.tvbox.osc.car;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Passive decoration only; the host owns the complete touch stream. */
final class BackAffordanceView extends View {
    private static final int SURFACE = 0xEB18212C;
    private static final int ACCENT = 0xFF9AD8E8;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private BackDisplayMetrics metrics;
    private EdgeBackPolicy.Mode mode = EdgeBackPolicy.Mode.EDGE_GESTURE;
    private EdgeBackPolicy.Side side = EdgeBackPolicy.Side.LEFT;
    private boolean visible = true, pressed, pullFromLeft = true;
    private float visualWidth, pullY;

    BackAffordanceView(android.content.Context context) {
        super(context);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        stroke.setStyle(Paint.Style.STROKE);
        arrow.setStyle(Paint.Style.STROKE);
        arrow.setStrokeCap(Paint.Cap.ROUND);
        arrow.setStrokeJoin(Paint.Join.ROUND);
        arrow.setColor(0xFFF6F8FB);
    }

    void configure(BackDisplayMetrics metrics, EdgeBackPolicy.Mode mode, EdgeBackPolicy.Side side) {
        this.metrics = metrics;
        this.mode = mode;
        this.side = side;
        invalidate();
    }

    void setPresentation(boolean visible, boolean pressed) {
        this.visible = visible;
        this.pressed = pressed;
        invalidate();
    }

    void setGesture(float width, float y, boolean fromLeft) {
        visualWidth = Math.max(0f, width);
        pullY = y;
        pullFromLeft = fromLeft;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!visible || metrics == null) return;
        if (mode == EdgeBackPolicy.Mode.BACK_BAR) drawBar(canvas);
        else drawEdge(canvas);
    }

    private void drawBar(Canvas canvas) {
        boolean right = side == EdgeBackPolicy.Side.RIGHT;
        // The capsule stays entirely inside the existing touch target.
        float w = Math.min(metrics.barHitWidthPx * .8f, metrics.barWidthPx * 2f);
        float h = Math.min(metrics.barHeightPx, metrics.barHitHeightPx * .6f);
        float inset = Math.min(metrics.guidePx, metrics.barHitWidthPx - w);
        float l = right ? metrics.widthPx - inset - w : inset;
        float t = (metrics.heightPx - h) / 2f;
        rect.set(l, t, l + w, t + h);
        fill.setColor(pressed ? 0xFF304A59 : SURFACE);
        stroke.setColor(pressed ? ACCENT : 0x807F8D9D);
        stroke.setStrokeWidth(Math.max(1f, metrics.guidePx * .3f));
        canvas.drawRoundRect(rect, w / 2f, w / 2f, fill);
        canvas.drawRoundRect(rect, w / 2f, w / 2f, stroke);
        drawArrow(canvas, l + w / 2f, t + h / 2f, w * .19f, w * .28f, right,
                Math.max(2f, metrics.guidePx * .48f));
    }

    private void drawEdge(Canvas canvas) {
        float guide = metrics.guidePx * .55f;
        float h = Math.min(metrics.barHeightPx * .56f, metrics.heightPx * .12f);
        float middle = metrics.heightPx / 2f;
        fill.setColor(0x808E9AA7);
        if (side != EdgeBackPolicy.Side.RIGHT) {
            rect.set(0f, middle - h / 2f, guide, middle + h / 2f);
            canvas.drawRoundRect(rect, guide, guide, fill);
        }
        if (side != EdgeBackPolicy.Side.LEFT) {
            rect.set(metrics.widthPx - guide, middle - h / 2f, metrics.widthPx, middle + h / 2f);
            canvas.drawRoundRect(rect, guide, guide, fill);
        }
        if (visualWidth <= .5f) return;
        float progress = Math.min(1f, visualWidth / Math.max(1f, metrics.triggerPx));
        float w = Math.min(metrics.visualPx * .62f, metrics.hitPx * 1.6f);
        float height = w * 1.35f;
        float margin = Math.max(3f, guide * 2f);
        float travel = margin + Math.min(visualWidth * .32f, metrics.visualPx * .32f);
        float left = pullFromLeft ? travel - w * (1f - progress) :
                metrics.widthPx - travel - w * progress;
        float y = Math.max(height / 2f + margin, Math.min(metrics.heightPx - height / 2f - margin, pullY));
        rect.set(left, y - height / 2f, left + w, y + height / 2f);
        fill.setColor(SURFACE);
        stroke.setColor(progress >= 1f ? ACCENT : 0xA8798798);
        stroke.setStrokeWidth(Math.max(1.5f, metrics.guidePx * .35f));
        canvas.drawRoundRect(rect, w * .42f, w * .42f, fill);
        canvas.drawRoundRect(rect, w * .42f, w * .42f, stroke);
        drawArrow(canvas, rect.centerX(), y, w * .14f, w * .22f, !pullFromLeft,
                Math.max(2.5f, metrics.guidePx * .6f));
    }

    private void drawArrow(Canvas canvas, float cx, float cy, float reach, float span,
                           boolean right, float lineWidth) {
        float direction = right ? 1f : -1f;
        arrow.setStrokeWidth(lineWidth);
        path.reset();
        path.moveTo(cx - direction * reach, cy - span);
        path.lineTo(cx + direction * reach, cy);
        path.lineTo(cx - direction * reach, cy + span);
        canvas.drawPath(path, arrow);
    }
}
