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
        // carclash-style edge panel: a narrow resting band plus a full-height sheet that follows
        // the drag (36dp band, 45dp trigger, 180dp peak, width = drag x 0.6).
        float guide = Math.max(3f, metrics.guidePx * .55f);
        float band = Math.min(metrics.guidePx * 2.2f, metrics.widthPx * .04f);
        float middle = metrics.heightPx / 2f;
        float reach = Math.min(metrics.barHeightPx * 1.2f, metrics.heightPx * .28f);
        fill.setColor(0x668E9AA7);
        if (side != EdgeBackPolicy.Side.RIGHT) {
            rect.set(0f, middle - reach / 2f, band, middle + reach / 2f);
            canvas.drawRoundRect(rect, band, band, fill);
        }
        if (side != EdgeBackPolicy.Side.LEFT) {
            rect.set(metrics.widthPx - band, middle - reach / 2f, metrics.widthPx, middle + reach / 2f);
            canvas.drawRoundRect(rect, band, band, fill);
        }
        if (visualWidth <= .5f) return;
        boolean left = pullFromLeft;
        float progress = Math.min(1f, visualWidth / Math.max(1f, metrics.triggerPx));
        float peak = Math.min(metrics.visualPx, metrics.widthPx * .18f);
        float width = Math.max(peak * .18f, Math.min(peak, visualWidth * .6f));
        float margin = Math.max(4f, guide);
        float top = margin, bottom = metrics.heightPx - margin;
        float edgeX = left ? 0f : metrics.widthPx;
        float outer = left ? width : metrics.widthPx - width;
        rect.set(left ? edgeX : outer, top, left ? outer : edgeX, bottom);
        fill.setColor(0xE62A3138);
        stroke.setColor(progress >= 1f ? ACCENT : 0x997F8D9D);
        stroke.setStrokeWidth(Math.max(1.5f, guide * .5f));
        float radius = Math.min(width * .3f, metrics.guidePx * 3f);
        canvas.drawRoundRect(rect, radius, radius, fill);
        canvas.drawRoundRect(rect, radius, radius, stroke);
        float cx = left ? outer - width * .32f : metrics.widthPx - width + width * .32f;
        float cy = Math.max(top + width * .4f, Math.min(bottom - width * .4f, pullY));
        float size = Math.min(width * .22f, metrics.guidePx * 1.6f);
        drawArrow(canvas, cx, cy, size * .8f, size, !left, Math.max(3f, size * .45f));
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
