package com.github.tvbox.osc.backbar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * 竖向“杠”返回键。视觉上只有一条圆角竖杠和一枚小箭头，
 * 没有悬浮球、没有拖动、没有边缘手势冲突。
 */
final class BackBarView extends View {

    private final BackBarHost host;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();

    private boolean left = true;
    private boolean pressed;
    private boolean cancelled;
    private boolean multiTouch;
    private float downX;
    private float downY;
    private long downTime;
    private float barWidthDp = 10f;
    private float barHeightDp = 84f;
    private final int touchSlop;
    BackBarView(BackBarHost host) {
        super(host.getContext());
        this.host = host;
        Context context = host.getContext();
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClickable(true);
        setFocusable(false);
        setContentDescription("返回");
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        fill.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        arrow.setStyle(Paint.Style.STROKE);
        arrow.setStrokeCap(Paint.Cap.ROUND);
        arrow.setStrokeJoin(Paint.Join.ROUND);
        arrow.setColor(0xFFFFFFFF);
    }

    void configure(boolean left, float barWidthDp, float barHeightDp) {
        this.left = left;
        this.barWidthDp = Math.max(2f, barWidthDp);
        this.barHeightDp = Math.max(24f, barHeightDp);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = dp(barWidthDp);
        float height = Math.min(dp(barHeightDp), getHeight());
        float margin = 0f;
        float l = left ? margin : getWidth() - margin - width;
        float r = l + width;
        float t = (getHeight() - height) / 2f;
        float b = t + height;
        float radius = width / 2f;

        fill.setColor(pressed ? 0xFF0284C7 : 0xB01E293B);
        stroke.setColor(pressed ? 0xFFE0F2FE : 0x8C38BDF8);
        stroke.setStrokeWidth(dp(1.2f));
        rect.set(l, t, r, b);
        canvas.drawRoundRect(rect, radius, radius, fill);
        canvas.drawRoundRect(rect, radius, radius, stroke);

        // 小箭头：左杠指向左，右杠指向右。
        float cx = (l + r) / 2f;
        float cy = (t + b) / 2f;
        float span = Math.min(dp(4f), width * 0.36f);
        float reach = Math.min(dp(2.4f), width * 0.24f);
        arrow.setStrokeWidth(dp(1.8f));
        arrow.setAlpha(pressed ? 255 : 225);
        path.reset();
        if (left) {
            path.moveTo(cx + reach, cy - span);
            path.lineTo(cx - reach, cy);
            path.lineTo(cx + reach, cy + span);
        } else {
            path.moveTo(cx - reach, cy - span);
            path.lineTo(cx + reach, cy);
            path.lineTo(cx - reach, cy + span);
        }
        canvas.drawPath(path, arrow);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressed = true;
                cancelled = false;
                multiTouch = false;
                downX = event.getX();
                downY = event.getY();
                downTime = event.getDownTime();
                host.onBarPressed(true);
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                invalidate();
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                // A second finger makes the gesture ambiguous. Never navigate from it.
                multiTouch = true;
                cancelled = true;
                pressed = false;
                host.onBarPressed(false);
                invalidate();
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!cancelled
                        && (Math.abs(event.getX() - downX) > touchSlop
                        || Math.abs(event.getY() - downY) > touchSlop)) {
                    cancelled = true;
                    pressed = false;
                    host.onBarPressed(false);
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                boolean click = BackBarInteractionPolicy.isTap(
                        (float) Math.hypot(dx, dy),
                        event.getEventTime() - downTime,
                        cancelled,
                        multiTouch,
                        touchSlop);
                pressed = false;
                host.onBarPressed(false);
                if (click) {
                    performClick();
                    host.performBack();
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                pressed = false;
                cancelled = true;
                host.onBarPressed(false);
                invalidate();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density * host.viewportScale();
    }
}
