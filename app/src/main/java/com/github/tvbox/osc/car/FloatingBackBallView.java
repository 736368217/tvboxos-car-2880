package com.github.tvbox.osc.car;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

/** Reference-SVG chevron and independent touch box, scaled together for the car viewport. */
final class FloatingBackBallView extends View {
    private final InAppBackHostLayout host;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path chevron = new Path();
    private DashPathEffect dragOutline;
    private final Runnable idle = this::enterIdle;
    private BackNavPolicy.Bounds bounds;
    private ValueAnimator appearance, spring, scale;
    private VelocityTracker velocity;
    private float unitScale = 1, visualSize = 30, baseX, baseY, dockX, windowWidth;
    private float idleFraction, drawScale = 1, downX, downY, startX, startY;
    private long downTime;
    private int idleMillis = 4000, pointerId;
    private boolean active, touching, dragging, ignoreStream, dockLeft = true;

    FloatingBackBallView(InAppBackHostLayout host) {
        super(host.getContext());
        this.host = host;
        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(false);
        setContentDescription("\u5e94\u7528\u5185\u8fd4\u56de");
        fill.setColor(0xE60B1712);
        stroke.setColor(0xFF6FF2C6);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        setAlpha(.82f);
    }

    private void enterIdle() {
        if (active && !touching && spring == null) animateIdle(1f, 220);
    }

    void configure(float unitScale, boolean large, int idleMillis) {
        stopMotion();
        this.unitScale = unitScale;
        this.visualSize = (large ? 34 : 30) * unitScale;
        dragOutline = new DashPathEffect(new float[]{3 * unitScale, 2 * unitScale}, 0);
        this.idleMillis = idleMillis;
        int box = Math.round((large ? 48 : 44) * unitScale);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) getLayoutParams();
        if (params.width != box || params.height != box) {
            params.width = params.height = box;
            setLayoutParams(params);
        }
        invalidate();
    }

    void syncBounds(BackNavPolicy.Bounds next, int windowWidth, boolean defaultRight) {
        stopMotion();
        bounds = next;
        this.windowWidth = windowWidth;
        int x = InAppBackButton.integer(HawkConfig.BACK_POSITION_X, defaultRight ? 10000 : 0);
        int y = InAppBackButton.integer(HawkConfig.BACK_POSITION_Y, 5200);
        dockLeft = x < 5000;
        dockX = dockLeft ? bounds.minX : bounds.maxX;
        baseX = dockX;
        baseY = BackNavPolicy.restore(y, bounds.minY, bounds.maxY);
        renderPosition();
        scheduleIdle();
    }

    void setActive(boolean value) {
        if (active == value) return;
        active = value;
        if (!value) stopMotion();
        else {
            baseX = dockX;
            renderPosition();
            wake();
        }
    }

    void wake() {
        if (!active) return;
        removeCallbacks(idle);
        if (appearance != null) appearance.cancel();
        appearance = null;
        if (idleFraction != 0) animateIdle(0, 120);
        scheduleIdle();
    }

    private void scheduleIdle() {
        removeCallbacks(idle);
        if (active && idleMillis > 0 && !touching && spring == null) postDelayed(idle, idleMillis);
    }

    private void animateIdle(float target, long duration) {
        if (appearance != null) appearance.cancel();
        if (target == idleFraction) return;
        appearance = ValueAnimator.ofFloat(idleFraction, target);
        appearance.setDuration(duration);
        appearance.setInterpolator(new DecelerateInterpolator());
        appearance.addUpdateListener(animation -> {
            idleFraction = (float) animation.getAnimatedValue();
            renderPosition();
        });
        appearance.start();
    }

    private void renderPosition() {
        // The offset is always relative to the stable dock; repeated wake/idle cannot accumulate it.
        setX(baseX + BackNavPolicy.idleOffset(visualSize, dockLeft, idleFraction));
        setY(baseY);
        setAlpha(.9f + (.38f - .9f) * idleFraction);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        int save = canvas.save();
        canvas.scale(drawScale, drawScale, cx, cy);
        float radius = visualSize / 2f - 1.5f * unitScale;
        int rim = dragging ? 0xFFF6C177 : blend(0xFF6FF2C6, 0xFF91AA9E, idleFraction);
        int glyph = dragging ? 0xFFF6C177 : blend(0xFF6FF2C6, 0xFFF2FBF7, idleFraction);
        fill.setColor(isPressed() ? 0xFF264A3A : 0xFF12251D);
        canvas.drawCircle(cx, cy, radius, fill);
        fill.setColor(0xFF0B1712);
        canvas.drawCircle(cx, cy, radius - 3.5f * unitScale, fill);
        stroke.setPathEffect(null);
        stroke.setColor(dragging ? 0x70F6C177 : blend(0x596FF2C6, 0x3391AA9E, idleFraction));
        stroke.setStrokeWidth(unitScale);
        canvas.drawCircle(cx, cy, radius - 3.5f * unitScale, stroke);
        stroke.setColor(rim);
        stroke.setStrokeWidth(2.6f * unitScale);
        stroke.setPathEffect(dragging ? dragOutline : null);
        canvas.drawCircle(cx, cy, radius, stroke);
        stroke.setPathEffect(null);
        stroke.setColor(glyph);
        stroke.setStrokeWidth(2.6f * unitScale);
        float halfHeight = visualSize * 8f / 42f, halfWidth = visualSize * 5f / 42f;
        chevron.reset();
        chevron.moveTo(cx + halfWidth, cy - halfHeight);
        chevron.lineTo(cx - halfWidth, cy);
        chevron.lineTo(cx + halfWidth, cy + halfHeight);
        canvas.drawPath(chevron, stroke);
        canvas.restoreToCount(save);
    }
    private static int blend(int from, int to, float amount) {
        float t = BackNavPolicy.clamp(amount, 0, 1);
        return Color.argb(Math.round(Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * t),
                Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * t),
                Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * t),
                Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t));
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!active || bounds == null) return false;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            if (spring != null) {
                spring.cancel();
                spring = null;
            }
            touching = true;
            dragging = ignoreStream = false;
            pointerId = event.getPointerId(0);
            downX = event.getRawX();
            downY = event.getRawY();
            startX = getX();
            startY = getY();
            downTime = event.getEventTime();
            recycleVelocity();
            velocity = VelocityTracker.obtain();
            track(event);
            wake();
            setPressed(true);
            getParent().requestDisallowInterceptTouchEvent(true);
            return true;
        }
        if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP) {
            cancelTouch();
            ignoreStream = true;
            return true;
        }
        if (ignoreStream) return true;
        if (!touching) return false;
        if (event.findPointerIndex(pointerId) < 0) {
            cancelTouch();
            ignoreStream = true;
            return true;
        }
        track(event);
        float dx = event.getRawX() - downX, dy = event.getRawY() - downY;
        if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
            if (!dragging && Math.hypot(dx, dy) >= BackNavPolicy.TOUCH_SLOP_DP * unitScale) {
                dragging = true;
                setPressed(false);
                if (appearance != null) appearance.cancel();
                idleFraction = 0;
                animateScale(1.08f);
            }
            if (dragging) {
                baseX = BackNavPolicy.clamp(startX + dx, bounds.minX, bounds.maxX);
                baseY = BackNavPolicy.clamp(startY + dy, bounds.minY, bounds.maxY);
                renderPosition();
            }
        }
        if (action == MotionEvent.ACTION_UP) {
            boolean click = BackNavPolicy.ballTap((float) Math.hypot(dx, dy) / unitScale,
                    event.getEventTime() - downTime, dragging, false);
            velocity.computeCurrentVelocity(1000, ViewConfiguration.get(getContext()).getScaledMaximumFlingVelocity());
            float vx = velocity.getXVelocity(pointerId);
            touching = false;
            setPressed(false);
            recycleVelocity();
            if (dragging || Math.abs(baseX - dockX) > unitScale * .1f) snap(dragging ? vx : 0);
            else scheduleIdle();
            dragging = false;
            animateScale(1);
            getParent().requestDisallowInterceptTouchEvent(false);
            if (click) performClick();
        } else if (action == MotionEvent.ACTION_CANCEL) {
            cancelTouch();
        }
        return true;
    }

    @Override public boolean performClick() {
        super.performClick();
        if (!active) return false;
        host.feedback(this, true);
        host.performBack();
        return true;
    }

    private void track(MotionEvent event) {
        if (velocity == null) return;
        // A moving View's local coordinates hide finger velocity; feed screen-space coordinates.
        MotionEvent raw = MotionEvent.obtain(event);
        raw.offsetLocation(event.getRawX() - event.getX(), event.getRawY() - event.getY());
        velocity.addMovement(raw);
        raw.recycle();
    }

    private void cancelTouch() {
        boolean wasDragging = dragging;
        touching = dragging = false;
        setPressed(false);
        recycleVelocity();
        animateScale(1);
        if (wasDragging || Math.abs(baseX - dockX) > unitScale * .1f) snap(0);
        else scheduleIdle();
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
    }

    private void snap(float velocityX) {
        if (bounds == null) return;
        dockLeft = BackNavPolicy.dockLeft(baseX + getWidth() / 2f, windowWidth, velocityX / unitScale);
        dockX = dockLeft ? bounds.minX : bounds.maxX;
        Hawk.put(HawkConfig.BACK_POSITION_X, dockLeft ? 0 : 10000);
        Hawk.put(HawkConfig.BACK_POSITION_Y, BackNavPolicy.normalize(baseY, bounds.minY, bounds.maxY));
        if (spring != null) spring.cancel();
        final float start = baseX, target = dockX;
        spring = ValueAnimator.ofFloat(0, .8f);
        spring.setDuration(800);
        spring.setInterpolator(new LinearInterpolator());
        spring.addUpdateListener(animation -> {
            float seconds = (float) animation.getAnimatedValue();
            baseX = BackNavPolicy.clamp(BackNavPolicy.springPosition(start, target, velocityX, seconds),
                    bounds.minX, bounds.maxX);
            renderPosition();
        });
        spring.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(Animator animation) { cancelled = true; }
            @Override public void onAnimationEnd(Animator animation) {
                spring = null;
                if (!cancelled) {
                    baseX = target;
                    renderPosition();
                    scheduleIdle();
                }
            }
        });
        spring.start();
    }

    private void animateScale(float target) {
        if (scale != null) scale.cancel();
        scale = ValueAnimator.ofFloat(drawScale, target);
        scale.setDuration(100);
        scale.addUpdateListener(animation -> {
            drawScale = (float) animation.getAnimatedValue();
            invalidate();
        });
        scale.start();
    }

    private void recycleVelocity() {
        if (velocity != null) velocity.recycle();
        velocity = null;
    }

    private void stopMotion() {
        removeCallbacks(idle);
        if (appearance != null) appearance.cancel();
        if (spring != null) spring.cancel();
        if (scale != null) scale.cancel();
        appearance = spring = scale = null;
        touching = dragging = ignoreStream = false;
        drawScale = 1;
        setPressed(false);
        recycleVelocity();
    }

    @Override protected void onDetachedFromWindow() {
        active = false;
        stopMotion();
        super.onDetachedFromWindow();
    }
}
