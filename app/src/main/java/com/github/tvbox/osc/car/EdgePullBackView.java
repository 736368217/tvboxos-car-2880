package com.github.tvbox.osc.car;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;

import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

/** Full-window drawing surface that only accepts DOWN inside a local 28x120dp pill hit box. */
final class EdgePullBackView extends View {
    private final InAppBackHostLayout host;
    private final BackNavPolicy.EdgeGesture gesture = new BackNavPolicy.EdgeGesture();
    private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path wave = new Path();
    private final Path arrow = new Path();
    private ValueAnimator retraction;
    private float unitScale = 1, centerY, minY, maxY, startCenterY, pullDp, progress;
    private int barWidthDp = 12, barHeightDp = 96, barColor = 0xFF38BDF8, barAlpha = 55;
    private boolean active, capturing, ignoreStream, pullLeft, armed;
    private String side = HawkConfig.BACK_SIDE_LEFT, skin = HawkConfig.BACK_SKIN_TEARDROP;
    private final Runnable longPress = this::beginReposition;
    private int idleMillis = 4000;
    private float visibleAlpha = 1f;
    private ValueAnimator fade;
    private final Runnable hideTask = this::fadeOut;

    private void cancelHide() {
        removeCallbacks(hideTask);
    }

    /** 闲置到点后淡出；点按/拖动/任何页面触摸都会重新唤醒。 */
    private void fadeOut() {
        if (!active || capturing || visibleAlpha <= 0.01f) return;
        animateAlpha(0f, 220);
    }

    private void scheduleHide() {
        removeCallbacks(hideTask);
        if (active && !capturing && visibleAlpha > 0.01f && idleMillis > 0) {
            postDelayed(hideTask, idleMillis);
        }
    }

    private void animateAlpha(float target, long duration) {
        if (fade != null) fade.cancel();
        fade = ValueAnimator.ofFloat(visibleAlpha, target);
        fade.setDuration(duration);
        fade.setInterpolator(new LinearInterpolator());
        fade.addUpdateListener(a -> {
            visibleAlpha = (float) a.getAnimatedValue();
            invalidate();
        });
        fade.start();
    }

    /** 供宿主在页面触摸/按键时唤醒。 */
    void wake() {
        if (!active || capturing) return;
        cancelHide();
        if (visibleAlpha < 1f) {
            animateAlpha(1f, 120);
        }
        scheduleHide();
    }

    private void beginReposition() {
        if (active && capturing && gesture.longPress()) {
            host.feedback(this, false);
            invalidate();
        }
    }

    EdgePullBackView(InAppBackHostLayout host) {
        super(host.getContext());
        this.host = host;
        setClickable(false);
        setFocusable(false);
        // This full-window drawing surface must not mask the underlying accessibility tree.
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        text.setColor(0xFFFFFFFF);
        text.setFakeBoldText(true);
    }

    void configure(float unitScale, String side, String skin,
                   int barWidthDp, int barHeightDp, int barColor, int barAlpha, int hideSeconds) {
        stopMotion();
        this.unitScale = unitScale;
        this.side = side;
        this.skin = skin;
        this.barWidthDp = Math.max(4, barWidthDp);
        this.barHeightDp = Math.max(48, barHeightDp);
        this.barColor = barColor;
        this.barAlpha = Math.max(10, Math.min(100, barAlpha));
        this.idleMillis = Math.max(0, hideSeconds) * 1000;
        cancelHide();
        if (visibleAlpha < 1f && !capturing) animateAlpha(1f, 120);
        scheduleHide();
        invalidate();
    }

    void syncBounds(float min, float max) {
        stopMotion();
        minY = min;
        maxY = Math.max(min, max);
        centerY = BackNavPolicy.restore(InAppBackButton.integer(HawkConfig.BACK_EDGE_POSITION_Y, 5500), minY, maxY);
        invalidate();
    }

    void setActive(boolean value) {
        if (active == value) return;
        active = value;
        if (!value) {
            stopMotion();
            cancelHide();
        } else {
            visibleAlpha = 1f;
            scheduleHide();
        }
        invalidate();
    }

    /** Reference SVG token in the current car viewport, not the Android density bucket. */
    private float dp(float value) { return value * unitScale; }
    private boolean showsLeft() { return !HawkConfig.BACK_SIDE_RIGHT.equals(side); }
    private boolean showsRight() { return !HawkConfig.BACK_SIDE_LEFT.equals(side); }


    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!active) return false;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            int hitSide = BackNavPolicy.edgeHit(event.getX() / unitScale, event.getY() / unitScale,
                    getWidth() / unitScale, centerY / unitScale, showsLeft(), showsRight(),
                    Math.max(36f, barWidthDp * 3f), Math.max(140f, barHeightDp + 56f));
            if (hitSide == 0) return false;
            stopMotion();
            cancelHide();
            if (visibleAlpha < 1f) {
                if (fade != null) fade.cancel();
                visibleAlpha = 1f;
            }
            capturing = true;
            pullLeft = hitSide < 0;
            startCenterY = centerY;
            gesture.begin(event.getX() / unitScale, event.getY() / unitScale, pullLeft);
            postDelayed(longPress, BackNavPolicy.LONG_PRESS_MS);
            getParent().requestDisallowInterceptTouchEvent(true);
            return true;
        }
        if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP) {
            if (capturing) finish(true);
            ignoreStream = true;
            return true;
        }
        if (ignoreStream) return true;
        if (!capturing) return false;
        if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
            if (gesture.move(event.getX() / unitScale, event.getY() / unitScale)) host.feedback(this, true);
            if (gesture.state() != BackNavPolicy.EdgeGesture.State.WAITING) removeCallbacks(longPress);
            if (gesture.state() == BackNavPolicy.EdgeGesture.State.REPOSITIONING) {
                // Keep the original finger-to-pill offset instead of teleporting the center to the finger.
                centerY = BackNavPolicy.clamp(startCenterY + dp(gesture.deltaY()), minY, maxY);
            } else if (gesture.state() == BackNavPolicy.EdgeGesture.State.PULLING) {
                pullDp = BackNavPolicy.pullWidth(gesture.rawPull());
                progress = BackNavPolicy.clamp(gesture.rawPull() / BackNavPolicy.TRIGGER_DP, 0, 1);
                armed = gesture.isArmed();
            }
            invalidate();
        }
        if (action == MotionEvent.ACTION_UP) finish(false);
        else if (action == MotionEvent.ACTION_CANCEL) finish(true);
        return true;
    }

    private void finish(boolean cancelled) {
        removeCallbacks(longPress);
        boolean repositioning = gesture.state() == BackNavPolicy.EdgeGesture.State.REPOSITIONING;
        if (repositioning) {
            if (cancelled) centerY = startCenterY;
            else Hawk.put(HawkConfig.BACK_EDGE_POSITION_Y, BackNavPolicy.normalize(centerY, minY, maxY));
        }
        boolean back = gesture.end(cancelled);
        capturing = false;
        scheduleHide();
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        retract();
        if (back) host.performBack(); // Dispatch on UP, not at the end of the 180ms return animation.
    }

    private void retract() {
        if (retraction != null) retraction.cancel();
        final float start = pullDp;
        final float startProgress = progress;
        if (start <= 0) {
            armed = false;
            invalidate();
            return;
        }
        retraction = ValueAnimator.ofFloat(0, .4f);
        retraction.setDuration(180);
        retraction.setInterpolator(new LinearInterpolator());
        retraction.addUpdateListener(animation -> {
            pullDp = Math.max(0, BackNavPolicy.springPosition(start, 0, 0, (float) animation.getAnimatedValue()));
            progress = startProgress * BackNavPolicy.clamp(pullDp / start, 0, 1);
            invalidate();
        });
        retraction.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                pullDp = progress = 0;
                armed = false;
                retraction = null;
                invalidate();
            }
        });
        retraction.start();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (visibleAlpha <= 0.01f) return;
        if (showsLeft()) drawPill(canvas, true);
        if (showsRight()) drawPill(canvas, false);
        if (pullDp <= .1f) return;
        int save = canvas.save();
        canvas.translate(pullLeft ? 0 : getWidth(), centerY);
        if (!pullLeft) canvas.scale(-1, 1);
        float width = dp(pullDp);
        boolean capsule = HawkConfig.BACK_SKIN_CAPSULE.equals(skin);
        line.setStrokeWidth(dp(armed ? 2.2f : 1.8f));
        line.setColor(capsule ? 0xFF38BDF8 : armed ? 0xFF7DD3FC : 0xFF94A3B8);
        line.setAlpha(255);
        fill.setShader(armed && !capsule ? new LinearGradient(0, 0, Math.max(1, width), 0,
                0xF20284C7, 0xF238BDF8, Shader.TileMode.CLAMP) : null);
        fill.setColor(capsule ? 0xF21E293B : 0xE01E293B);
        if (capsule) drawCapsule(canvas, width);
        else drawTeardrop(canvas, width);
        canvas.restoreToCount(save);
    }

    private void drawPill(Canvas canvas, boolean left) {
        float inset = dp(2);
        float width = dp(barWidthDp);
        float height = dp(barHeightDp);
        float x = left ? inset : getWidth() - inset - width;
        rect.set(x, centerY - height / 2f, x + width, centerY + height / 2f);
        boolean adjusting = capturing && left == pullLeft
                && gesture.state() == BackNavPolicy.EdgeGesture.State.REPOSITIONING;
        pill.setColor(adjusting ? 0xFFFBBF24 : barColor);
        // 透明度可配置；调整位置时临时满不透明，便于看清拖动。
        pill.setAlpha(adjusting ? 255 : Math.round(255f * barAlpha / 100f * visibleAlpha));
        canvas.drawRoundRect(rect, width / 2f, width / 2f, pill);
    }

    private void drawTeardrop(Canvas canvas, float width) {
        float halfHeight = Math.min(dp(80), Math.max(dp(38), width * 1.2f));
        wave.reset();
        wave.moveTo(0, -halfHeight);
        wave.cubicTo(0, -halfHeight * .57f, width, -halfHeight * .405f, width, 0);
        wave.cubicTo(width, halfHeight * .405f, 0, halfHeight * .57f, 0, halfHeight);
        wave.close();
        canvas.drawPath(wave, fill);
        canvas.drawPath(wave, line);
        float cx = width * .56f;
        if (armed) {
            float radius = Math.min(dp(16), width * .36f);
            pill.setColor(0x600F172A);
            canvas.drawCircle(cx, 0, radius, pill);
            line.setColor(0xFFE0F2FE);
            canvas.drawCircle(cx, 0, radius, line);
        }
        drawChevron(canvas, cx, Math.min(dp(8) * (armed ? 1.2f : .85f + .15f * progress), width * .36f));
    }

    private void drawCapsule(Canvas canvas, float width) {
        float capsuleWidth = dp(BackNavPolicy.capsuleWidth(pullDp, progress));
        float halfHeight = Math.min(dp(22), capsuleWidth * .25f);
        rect.set(0, -halfHeight, capsuleWidth, halfHeight);
        canvas.drawRoundRect(rect, halfHeight, halfHeight, fill);
        canvas.drawRoundRect(rect, halfHeight, halfHeight, line);
        int save = canvas.save();
        if (!pullLeft) {
            canvas.translate(capsuleWidth, 0);
            canvas.scale(-1, 1);
        }
        if (armed && capsuleWidth >= dp(76)) {
            float iconRadius = dp(14), iconX = pullLeft ? capsuleWidth - dp(23) : dp(23);
            pill.setColor(0xFF0284C7);
            canvas.drawCircle(iconX, 0, iconRadius, pill);
            line.setColor(0xFF7DD3FC);
            line.setStrokeWidth(dp(1));
            canvas.drawCircle(iconX, 0, iconRadius, line);
            float textLeft = pullLeft ? dp(10) : iconX + iconRadius + dp(5);
            float textRight = pullLeft ? iconX - iconRadius - dp(5) : capsuleWidth - dp(10);
            String label = "\u677e\u624b\u8fd4\u56de";
            text.setColor(0xFFE2E8F0);
            text.setTextSize(dp(11));
            float available = Math.max(1, textRight - textLeft);
            text.setTextSize(dp(11) * Math.min(1, available / Math.max(1, text.measureText(label))));
            text.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(label, (textLeft + textRight) / 2f, -(text.ascent() + text.descent()) / 2f, text);
            drawChevron(canvas, iconX, dp(7), 1);
        } else drawChevron(canvas, capsuleWidth * .5f, Math.min(dp(8), capsuleWidth * .16f), 1);
        canvas.restoreToCount(save);
    }

    private void drawChevron(Canvas canvas, float cx, float span) {
        drawChevron(canvas, cx, span, pullLeft ? 1 : -1);
    }

    private void drawChevron(Canvas canvas, float cx, float span, float direction) {
        line.setColor(0xFFFFFFFF);
        line.setAlpha(Math.round(255 * progress * Math.min(1, pullDp / 16f)));
        line.setStrokeWidth(dp(3));
        arrow.reset();
        arrow.moveTo(cx + direction * span * .45f, -span);
        arrow.lineTo(cx - direction * span * .55f, 0);
        arrow.lineTo(cx + direction * span * .45f, span);
        canvas.drawPath(arrow, line);
    }

    private void stopMotion() {
        removeCallbacks(longPress);
        if (retraction != null) retraction.cancel();
        retraction = null;
        gesture.end(true);
        capturing = ignoreStream = armed = false;
        pullDp = progress = 0;
    }

    @Override protected void onDetachedFromWindow() {
        active = false;
        stopMotion();
        super.onDetachedFromWindow();
    }
}
