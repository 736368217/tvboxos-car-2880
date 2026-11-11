package com.github.tvbox.osc.car;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * 被动绘制层：不处理任何触摸，不设置 clickable，只根据状态机快照绘制。
 *
 * <p>EDGE_GESTURE 画静态细导引线，拖动时画受 BackDisplayMetrics 限制的 cubicTo 橡皮筋；
 * BACK_BAR 复刻旧返回杠的圆角竖杠 + 小箭头。所有尺寸都来自按屏幕自适应的 metrics。</p>
 */
final class BackAffordanceView extends View {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    private BackDisplayMetrics metrics;
    private EdgeBackPolicy.Mode mode = EdgeBackPolicy.Mode.EDGE_GESTURE;
    private EdgeBackPolicy.Side side = EdgeBackPolicy.Side.LEFT;
    private boolean visible = true;
    private boolean pressed;
    private boolean pullFromLeft = true;
    private float visualWidth;
    private float pullY;

    BackAffordanceView(android.content.Context context) {
        super(context);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        arrow.setStyle(Paint.Style.STROKE);
        arrow.setStrokeCap(Paint.Cap.ROUND);
        arrow.setStrokeJoin(Paint.Join.ROUND);
        arrow.setColor(0xFFFFFFFF);
        glow.setStyle(Paint.Style.FILL);
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

    void setGesture(float visualWidth, float pullY, boolean pullFromLeft) {
        this.visualWidth = Math.max(0f, visualWidth);
        this.pullY = pullY;
        this.pullFromLeft = pullFromLeft;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!visible || metrics == null) return;
        if (mode == EdgeBackPolicy.Mode.BACK_BAR) drawBar(canvas);
        else drawEdge(canvas);
    }

    private void drawBar(Canvas canvas) {
        boolean right = side == EdgeBackPolicy.Side.RIGHT && side != EdgeBackPolicy.Side.BOTH;
        float w = metrics.barWidthPx;
        float h = metrics.barHeightPx;
        float l = metrics.barLeft(right);
        float t = (metrics.heightPx - h) / 2f;
        rect.set(l, t, l + w, t + h);
        fill.setColor(pressed ? 0xFF0284C7 : 0xB01E293B);
        stroke.setColor(pressed ? 0xFFE0F2FE : 0x8C38BDF8);
        stroke.setStrokeWidth(Math.max(1f, metrics.guidePx * .4f));
        canvas.drawRoundRect(rect, w / 2f, w / 2f, fill);
        canvas.drawRoundRect(rect, w / 2f, w / 2f, stroke);

        float cx = l + w / 2f;
        float cy = t + h / 2f;
        float span = Math.min(metrics.guidePx * 2f, w * .36f);
        float reach = Math.min(metrics.guidePx * 1.2f, w * .24f);
        arrow.setStrokeWidth(Math.max(1.4f, metrics.guidePx));
        arrow.setAlpha(pressed ? 255 : 225);
        path.reset();
        if (right) {
            path.moveTo(cx - reach, cy - span);
            path.lineTo(cx + reach, cy);
            path.lineTo(cx - reach, cy + span);
        } else {
            path.moveTo(cx + reach, cy - span);
            path.lineTo(cx - reach, cy);
            path.lineTo(cx + reach, cy + span);
        }
        canvas.drawPath(path, arrow);
    }

    private void drawEdge(Canvas canvas) {
        float guideW = metrics.guidePx;
        float h = Math.min(metrics.barHeightPx, metrics.heightPx * BackDisplayMetrics.BAR_HEIGHT_FRACTION);
        float midY = metrics.heightPx / 2f;
        fill.setColor(0xFF38BDF8);
        fill.setAlpha(50);
        if (side != EdgeBackPolicy.Side.RIGHT) {
            rect.set(0f, midY - h / 2f, guideW, midY + h / 2f);
            canvas.drawRoundRect(rect, guideW / 2f, guideW / 2f, fill);
        }
        if (side != EdgeBackPolicy.Side.LEFT) {
            rect.set(metrics.widthPx - guideW, midY - h / 2f,
                    metrics.widthPx, midY + h / 2f);
            canvas.drawRoundRect(rect, guideW / 2f, guideW / 2f, fill);
        }

        if (visualWidth <= .5f) return;
        float w = Math.min(visualWidth, metrics.visualPx);
        float half = Math.min(metrics.leafHalfPx, Math.max(metrics.guidePx * 8f, w * 1.05f));
        float originX = pullFromLeft ? 0f : metrics.widthPx;
        float sign = pullFromLeft ? 1f : -1f;
        float progress = Math.min(1f, w / Math.max(1f, metrics.triggerPx));

        canvas.save();
        canvas.translate(originX, pullY);
        canvas.scale(sign, 1f);
        glow.setShader(new RadialGradient(w * .55f, 0f, Math.max(metrics.guidePx * 8f, w * 1.2f),
                0x887DD3FC, 0x0038BDF8, Shader.TileMode.CLAMP));
        glow.setAlpha(Math.round(90 + 140 * progress));
        canvas.drawCircle(w * .55f, 0f, Math.max(metrics.guidePx * 8f, w * 1.2f), glow);
        glow.setShader(null);

        fill.setShader(new LinearGradient(0f, -half, w, half,
                progress >= 1f ? 0xF40284C7 : 0xC00F172A,
                progress >= 1f ? 0xF27DD3FC : 0xD038BDF8,
                Shader.TileMode.CLAMP));
        fill.setAlpha(Math.round(150 + 80 * progress));
        path.reset();
        path.moveTo(0f, -half);
        path.cubicTo(0f, -half * .46f, w, -half * .38f, w, 0f);
        path.cubicTo(w, half * .38f, 0f, half * .46f, 0f, half);
        path.close();
        canvas.drawPath(path, fill);
        fill.setShader(null);

        stroke.setColor(0xFFE0F2FE);
        stroke.setStrokeWidth(Math.max(1.2f, metrics.guidePx * .5f));
        stroke.setAlpha(Math.round(160 + 80 * progress));
        canvas.drawPath(path, stroke);

        float span = Math.min(metrics.guidePx * 2.5f, w * .3f);
        arrow.setStrokeWidth(Math.max(1.4f, metrics.guidePx * .8f));
        arrow.setAlpha(Math.round(180 + 70 * progress));
        path.reset();
        path.moveTo(w * .62f + span * .4f, -span);
        path.lineTo(w * .62f - span * .6f, 0f);
        path.lineTo(w * .62f + span * .4f, span);
        canvas.drawPath(path, arrow);
        canvas.restore();
    }
}
