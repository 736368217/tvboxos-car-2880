package com.github.tvbox.osc.car;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.github.tvbox.osc.util.HawkConfig;

/**
 * 贴边横滑返回：复刻 Swipe Navigation Gesture 3.0 的「拉伸手柄」手感。
 *
 * <p>参考实现（SNG_CornerStretchView）：贴边 50px 内按下 → 跟手画一条从屏幕边缘拉到手指的
 * 拉伸带（cubic 路径）+ 箭头；松手时按拖动距离决定是否触发返回，未达阈值则回弹。</p>
 *
 * <p>本类是普通 Activity 窗口内的子 View：只有贴边那一条窄带会接收按下事件，
 * 其余区域 onTouchEvent 返回 false，事件照常落到页面本身，空白区域不会被拦截。</p>
 */
final class EdgeSwipeBackView extends View {

    private final InAppBackHostLayout host;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path leaf = new Path();
    private final Path chevron = new Path();

    private ValueAnimator retraction;
    private float unitScale = 1f;
    private float density = 1f;
    private float triggerDp = BackNavPolicy.SWIPE_TRIGGER_DP;
    private int color = 0xFF38BDF8;
    private String side = HawkConfig.BACK_SIDE_LEFT;

    private boolean active, capturing, fromLeft, armedOnce;
    private float downX, downY, dragPx, progress, velocityPx;
    private long lastMoveTime;

    EdgeSwipeBackView(InAppBackHostLayout host) {
        super(host.getContext());
        this.host = host;
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        for (Paint paint : new Paint[]{fill, stroke, arrow}) paint.setAntiAlias(true);
        arrow.setStyle(Paint.Style.STROKE);
        arrow.setStrokeCap(Paint.Cap.ROUND);
        arrow.setStrokeJoin(Paint.Join.ROUND);
    }

    void configure(float unitScale, String side, int color, int triggerDp) {
        stopMotion();
        this.unitScale = unitScale > 0 ? unitScale : 1f;
        this.density = Math.max(.1f, getResources().getDisplayMetrics().density);
        this.side = side;
        this.color = color;
        this.triggerDp = Math.max(40f, Math.min(140f, triggerDp));
        invalidate();
    }

    void setActive(boolean value) {
        if (active == value) return;
        active = value;
        if (!value) stopMotion();
        invalidate();
    }

    void wake() {
        // 无常驻图形：贴边滑动是纯手势入口，静止时不画任何东西。
    }

    private float dp(float value) { return value * density; }
    private float token(float value) { return value * unitScale; }

    private boolean showsLeft() { return !HawkConfig.BACK_SIDE_RIGHT.equals(side); }
    private boolean showsRight() { return !HawkConfig.BACK_SIDE_LEFT.equals(side); }

    private float triggerPx() { return Math.max(dp(40f), token(triggerDp)); }
    private float edgePx() { return Math.max(dp(16f), token(BackNavPolicy.SWIPE_EDGE_DP)); }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!active) return false;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            float edge = edgePx();
            float x = event.getX();
            if (x <= edge && showsLeft()) fromLeft = true;
            else if (x >= getWidth() - edge && showsRight()) fromLeft = false;
            else return false;
            stopMotion();
            capturing = true;
            armedOnce = false;
            downX = x;
            downY = event.getY();
            dragPx = progress = velocityPx = 0;
            lastMoveTime = event.getEventTime();
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            invalidate();

            return true;
        }
        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            finish(true);
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            float x = event.getX();
            float inward = fromLeft ? x - downX : downX - x;
            float next = Math.max(0, Math.min(inward, token(BackNavPolicy.SWIPE_MAX_DP)));
            long now = event.getEventTime();
            long dt = Math.max(1, now - lastMoveTime);
            velocityPx = (next - dragPx) / dt * 1000f;
            lastMoveTime = now;
            dragPx = next;
            progress = Math.min(1f, dragPx / triggerPx());
            boolean armed = progress >= 1f;
            if (armed && !armedOnce) {
                armedOnce = true;
                host.feedback(this, true);
            } else if (!armed) {
                armedOnce = false;
            }
            invalidate();
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            boolean back = progress >= 1f
                    || (progress > .45f && velocityPx > dp(900f));
            finish(!back);
            if (back) host.performBack();
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            finish(true);
            return true;
        }
        return true;
    }

    /** cancelled 只表示这次手势不触发返回；视觉上都要回弹到边缘。 */
    private void finish(boolean cancelled) {
        capturing = false;
        armedOnce = false;
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        retract(dragPx);
    }

    private void retract(float start) {
        if (retraction != null) retraction.cancel();
        if (start <= 0.5f) {
            dragPx = progress = 0;
            invalidate();
            return;
        }
        final float begin = start;
        retraction = ValueAnimator.ofFloat(0f, .45f);
        retraction.setDuration(240);
        retraction.setInterpolator(new DecelerateInterpolator());
        retraction.addUpdateListener(animation -> {
            float t = (float) animation.getAnimatedValue();
            dragPx = Math.max(0, BackNavPolicy.springPosition(begin, 0, 0, t));
            progress = Math.min(1f, dragPx / triggerPx());
            invalidate();
        });
        retraction.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                dragPx = progress = 0;
                retraction = null;
                invalidate();
            }
        });
        retraction.start();
    }

    private void stopMotion() {
        if (retraction != null) retraction.cancel();
        retraction = null;
        capturing = false;
        dragPx = progress = velocityPx = 0;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (dragPx <= .5f) return;

        float y = downY;
        float w = dragPx;
        boolean left = fromLeft;
        float sign = left ? 1 : -1;
        float originX = left ? 0 : getWidth();

        // 1) 拉伸带：边缘上下两点 → 手指位置的 cubic 曲线（SNG 的「橡胶带」形状）。
        float half = Math.min(token(170f), Math.max(token(56f), w * .92f));
        float tipX = originX + sign * w;
        leaf.reset();
        leaf.moveTo(originX, y - half);
        leaf.cubicTo(tipX, y, tipX, y, originX, y + half);
        leaf.close();
        float ramp = Math.min(1f, w / Math.max(1f, triggerPx() * .55f));
        fill.setColor(color);
        fill.setAlpha((int) (ramp * 210));
        canvas.drawPath(leaf, fill);

        stroke.setColor(0xFFFFFFFF);
        stroke.setAlpha((int) (ramp * 70));
        stroke.setStrokeWidth(dp(1.5f));
        canvas.drawPath(leaf, stroke);

        // 2) 起点锚点：告诉手指「拉的是这条边」。
        fill.setColor(color);
        fill.setAlpha((int) (ramp * 235));
        canvas.drawCircle(originX, y, dp(3.2f), fill);

        // 3) 箭头：越拉越亮，越过阈值变成实心提示。
        float chevronX = originX + sign * w * .66f;
        float span = dp(armedOnce ? 13f : 10f);
        arrow.setColor(0xFFFFFFFF);
        arrow.setAlpha((int) (Math.min(1f, ramp * 1.25f) * 255));
        arrow.setStrokeWidth(dp(armedOnce ? 3.4f : 2.8f));
        chevron.reset();
        float dir = sign;
        chevron.moveTo(chevronX - dir * span * .45f, y - span);
        chevron.lineTo(chevronX + dir * span * .55f, y);
        chevron.lineTo(chevronX - dir * span * .45f, y + span);
        canvas.drawPath(chevron, arrow);

        // 4) 越过阈值：指尖一圈柔光，替代文字提示。
        if (progress >= 1f) {
            fill.setColor(color);
            fill.setAlpha(70);
            canvas.drawCircle(chevronX, y, dp(20f), fill);
        }
    }

    @Override protected void onDetachedFromWindow() {
        active = false;
        stopMotion();
        super.onDetachedFromWindow();
    }
}
