package com.github.tvbox.osc.car;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;

import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

/**
 * 边缘返回：对标 Swipe Navigation Gesture 3.0 的「拉动有光芒」交互。
 *
 * <p>视觉：**静态只有一根极细的导引线**（简约、几乎不挡内容）；手指从屏幕边缘向内拉时，
 * 用 {@code Path.cubicTo} 画一条从边缘鼓向手指的水滴/拉伸手柄，叠加径向渐变光芒与箭头，
 * 越接近触发阈值越亮；松手若未达阈值则弹性收回。对标作品用的是一张
 * {@code swipe_stretch_left.png} 发光贴图，这里用纯 Canvas 渐变重画，避免引入第三方资源，
 * 效果等价且可随进度连续变化。</p>
 *
 * <p>命中区：r71 之前只认「小棍附近 36×140dp」一小块，播放页/全屏页手指稍偏就完全拉不出来。
 * 现在 DOWN 判定改为**整条屏幕边缘带**（x 在边缘热区内、y 覆盖可用高度），与对标作品的
 * 全高 Overlay 一致；页面中央触摸仍然完全穿透，不拦截内容。</p>
 */
final class EdgePullBackView extends View {
    private final InAppBackHostLayout host;
    private final BackNavPolicy.EdgeGesture gesture = new BackNavPolicy.EdgeGesture();
    private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path wave = new Path();
    private final Path arrow = new Path();
    private ValueAnimator retraction;
    private float unitScale = 1, centerY, minY, maxY, startCenterY, pullDp, progress;
    private float pullY;   // 光芒跟随手指所在高度，而不是固定画在导引线中心
    private int barWidthDp = 12, barHeightDp = 96, barColor = 0xFF38BDF8, barAlpha = 55;
    private boolean active, capturing, ignoreStream, pullLeft, armed;
    private String side = HawkConfig.BACK_SIDE_LEFT, skin = HawkConfig.BACK_SKIN_TEARDROP;
    private final Runnable longPress = this::beginReposition;
    private int idleMillis = 4000;
    private float visibleAlpha = 1f;
    private ValueAnimator fade;
    private final Runnable hideTask = this::fadeOut;

    private static final int GLOW_CORE = 0xFF7DD3FC;
    private static final int GLOW_EDGE = 0x0038BDF8;

    private void cancelHide() { removeCallbacks(hideTask); }

    private void fadeOut() {
        if (!active || capturing || visibleAlpha <= 0.01f) return;
        animateAlpha(0f, 220);
    }

    boolean isHidden() { return visibleAlpha <= 0.01f; }

    private void scheduleHide() {
        removeCallbacks(hideTask);
        if (active && !capturing && visibleAlpha > 0.01f && idleMillis > 0) {
            postDelayed(hideTask, idleMillis);
        }
    }

    /**
     * 页面 DOWN 唤醒：**只负责点亮导引线，不负责手势**。
     * 手势由本 View 自己的 onTouchEvent 判定，且必须始终可用（隐藏态也能一拉就出）。
     */
    void wake() {
        if (!active || capturing) return;
        cancelHide();
        if (visibleAlpha < 1f) animateAlpha(1f, 120);
        scheduleHide();
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
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        glow.setStyle(Paint.Style.FILL);
        arrowPaint.setStyle(Paint.Style.STROKE);
        arrowPaint.setStrokeCap(Paint.Cap.ROUND);
        arrowPaint.setStrokeJoin(Paint.Join.ROUND);
        arrowPaint.setColor(0xFFFFFFFF);
        text.setColor(0xFFE2E8F0);
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
    private float dp(float value) { return value * unitScale; }
    private boolean showsLeft() { return !HawkConfig.BACK_SIDE_RIGHT.equals(side); }
    private boolean showsRight() { return !HawkConfig.BACK_SIDE_LEFT.equals(side); }

    private int hitSide(float x, float y) {
        float w = EdgeGeometry.hitWidthPx(getWidth(), Math.max(dp(40), dp(barWidthDp) * 3f));
        return EdgeHitPolicy.hit(x, y, getWidth(),
                minY - dp(120), maxY + dp(120), w, showsLeft(), showsRight());
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!active) return false;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            int sideHit = hitSide(event.getX(), event.getY());
            if (sideHit == 0) return false;       // 空白区永远穿透
            stopMotion();
            cancelHide();
            if (fade != null) fade.cancel();
            visibleAlpha = 1f;                    // 隐藏态也能一拉即出
            capturing = true;
            pullLeft = sideHit < 0;
            startCenterY = centerY;
            pullY = event.getY();
            gesture.begin(event.getX() / unitScale, event.getY() / unitScale, pullLeft);
            postDelayed(longPress, BackNavPolicy.LONG_PRESS_MS);
            getParent().requestDisallowInterceptTouchEvent(true);
            invalidate();
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
        if (back) host.performBack();
    }

    private void retract() {
        if (retraction != null) retraction.cancel();
        final float start = pullDp;
        final float startProgress = progress;
        if (start <= 0) { armed = false; invalidate(); return; }
        retraction = ValueAnimator.ofFloat(0, .45f);
        retraction.setDuration(220);
        retraction.setInterpolator(new LinearInterpolator());
        retraction.addUpdateListener(animation -> {
            float seconds = (float) animation.getAnimatedValue();
            pullDp = Math.max(0, BackNavPolicy.springPosition(start, 0, 0, seconds));
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
        drawTrack(canvas);
        if (pullDp <= .1f) return;
        int layer = Math.round(255f * BackNavPolicy.clamp(pullDp / 10f, 0f, 1f));
        int save = canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), layer);
        canvas.translate(pullLeft ? 0 : getWidth(), capturing ? pullY : centerY);
        if (!pullLeft) canvas.scale(-1, 1);
        drawStretch(canvas, dp(pullDp));
        canvas.restoreToCount(save);
    }

    /** 静态态：一根极细的半透明导引线 + 中心微光点，简约、不挡内容。 */
    private void drawTrack(Canvas canvas) {
        float pull = BackNavPolicy.clamp(pullDp / 10f, 0f, 1f);
        float alpha = Math.max(0f, 1f - pull) * visibleAlpha * barAlpha / 100f;
        float w = Math.max(dp(2), dp(barWidthDp) * .22f);
        float h = dp(Math.min(barHeightDp, 74));
        pill.setColor(barColor);
        pill.setAlpha(Math.round(255 * alpha * .55f));
        if (showsLeft()) {
            rect.set(dp(2), centerY - h / 2f, dp(2) + w, centerY + h / 2f);
            canvas.drawRoundRect(rect, w / 2f, w / 2f, pill);
        }
        if (showsRight()) {
            rect.set(getWidth() - dp(2) - w, centerY - h / 2f, getWidth() - dp(2), centerY + h / 2f);
            canvas.drawRoundRect(rect, w / 2f, w / 2f, pill);
        }
        if (pull < .01f && visibleAlpha > .5f) {
            // 中心一点冷光，提示「这里可以拉」，不做任何文字/大色块
            glow.setShader(new RadialGradient(dp(2) + w / 2f, centerY, dp(12),
                    GLOW_CORE, GLOW_EDGE, Shader.TileMode.CLAMP));
            glow.setAlpha(Math.round(255 * alpha));
            canvas.drawCircle(dp(2) + w / 2f, centerY, dp(12), glow);
            glow.setShader(null);
        }
    }

    /** 拉动态：对标作品的 cubicTo 拉伸手柄 + 径向光芒 + 箭头。 */
    private void drawStretch(Canvas canvas, float width) {
        float safeWidth = Math.min(width, EdgeGeometry.maxVisualWidthPx(getWidth()));
        float half = EdgeGeometry.leafHalfHeightPx(getHeight(),
                Math.min(dp(78), Math.max(dp(30), safeWidth * 1.05f)));
        wave.reset();
        wave.moveTo(0, -half);
        wave.cubicTo(0, -half * .46f, safeWidth, -half * .38f, safeWidth, 0);
        wave.cubicTo(safeWidth, half * .38f, 0, half * .46f, 0, half);
        wave.close();

        // 光芒：沿手柄推进的径向渐变，越接近触发越亮
        float glowRadius = Math.max(dp(28), safeWidth * 1.15f);
        float cx = safeWidth * .62f;
        glow.setShader(new RadialGradient(cx, 0, glowRadius,
                GLOW_CORE, GLOW_EDGE, Shader.TileMode.CLAMP));
        glow.setAlpha(Math.round(90 + 150 * progress));
        canvas.drawCircle(cx, 0, glowRadius, glow);
        glow.setShader(null);

        // 手柄本体：冷色渐变，armed 时更亮
        fill.setShader(new LinearGradient(0, -half, safeWidth, half,
                armed ? 0xF40284C7 : 0xC00F172A,
                armed ? 0xF27DD3FC : 0xD038BDF8,
                Shader.TileMode.CLAMP));
        canvas.drawPath(wave, fill);
        fill.setShader(null);

        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(dp(armed ? 2f : 1.4f));
        line.setColor(armed ? 0xFFE0F2FE : 0xB07DD3FC);
        line.setAlpha(Math.round(200 + 55 * progress));
        canvas.drawPath(wave, line);

        // 箭头：随进度放大，触发阈值后显示「松手返回」
        float span = Math.min(dp(11), safeWidth * .3f) * (.7f + .3f * progress);
        arrowPaint.setStrokeWidth(dp(3));
        arrowPaint.setAlpha(Math.round(220 * Math.min(1f, .3f + progress)));
        arrow.reset();
        arrow.moveTo(cx + span * .45f, -span);
        arrow.lineTo(cx - span * .55f, 0);
        arrow.lineTo(cx + span * .45f, span);
        canvas.drawPath(arrow, arrowPaint);
        if (armed && safeWidth > dp(64)) {
            String label = "\u677e\u624b\u8fd4\u56de";
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(dp(10));
            text.setAlpha(230);
            canvas.drawText(label, safeWidth * .5f, -half - dp(8), text);
        }
    }

    private void stopMotion() {
        removeCallbacks(longPress);
        if (retraction != null) retraction.cancel();
        retraction = null;
        gesture.end(true);
        capturing = ignoreStream = armed = false;
        pullDp = progress = 0f;
        setPressed(false);
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
    }
}
