package com.github.tvbox.osc.player.controller;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.tvbox.osc.car.VolumeBoostMath;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LOG;
import com.orhanobut.hawk.Hawk;

import java.util.Map;

import xyz.doikki.videoplayer.controller.BaseVideoController;
import xyz.doikki.videoplayer.controller.IControlComponent;
import xyz.doikki.videoplayer.controller.IGestureComponent;
import xyz.doikki.videoplayer.player.VideoView;
import xyz.doikki.videoplayer.util.PlayerUtils;

public abstract class BaseController extends BaseVideoController implements GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener, View.OnTouchListener {
    private GestureDetector mGestureDetector;
    private AudioManager mAudioManager;
    private boolean mIsGestureEnabled = true;
    private float mAppVolume = 1f;
    /** 一次音量手势起手时的增益；滑动过程中用它做基准，避免把累计位移重复叠加。 */
    private float mVolumeStart = -1f;
    private float mBrightness;
    private int mSeekPosition = -1;
    private boolean mFirstTouch;
    private boolean mChangePosition;
    private boolean mChangeBrightness;
    private boolean mChangeVolume;
    /** 一次手势的主轴锁定：NONE=尚未定轴，X=进度，Y=亮度/音量，IGNORE=放弃。 */
    private static final int AXIS_NONE = 0, AXIS_X = 1, AXIS_Y = 2, AXIS_IGNORE = 3;
    private int mGestureAxis = AXIS_NONE;
    private float mDownX, mDownY;
    private int mAxisSlopPx;
    private boolean mCanChangePosition = true;
    private boolean mEnableInNormal;
    private boolean mCanSlide;
    private int mCurPlayState;

    protected Handler mHandler;

    protected HandlerCallback mHandlerCallback;

    protected interface HandlerCallback {
        void callback(Message msg);
    }

    private boolean mIsDoubleTapTogglePlayEnabled = true;


    public BaseController(@NonNull Context context) {
        super(context);
        mAppVolume = loadAppVolume();
        mHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(@NonNull Message msg) {
                int what = msg.what;
                switch (what) {
                    case 100: { // 亮度+音量调整
                        mSlideInfo.setVisibility(VISIBLE);
                        mSlideInfo.setText(msg.obj.toString());
                        break;
                    }

                    case 101: { // 亮度+音量调整 关闭
                        mSlideInfo.setVisibility(GONE);
                        break;
                    }
                    default: {
                        if (mHandlerCallback != null)
                            mHandlerCallback.callback(msg);
                        break;
                    }
                }
                return false;
            }
        });
    }

    public BaseController(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mAppVolume = loadAppVolume();
    }

    public BaseController(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mAppVolume = loadAppVolume();
    }

    private TextView mSlideInfo;
    private ProgressBar mLoading;
    private ViewGroup mPauseRoot;
    private TextView mPauseTime;

    @Override
    protected void initView() {
        super.initView();
        mAudioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        mGestureDetector = new GestureDetector(getContext(), this);
        setOnTouchListener(this);
        mSlideInfo = findViewWithTag("vod_control_slide_info");
        mLoading = findViewWithTag("vod_control_loading");
        mPauseRoot = findViewWithTag("vod_control_pause");
        mPauseTime = findViewWithTag("vod_control_pause_t");
    }

    /**
     * 当前播放器是否真的能放大到 100% 以上。
     *
     * <p>只有走 PCM 采样增益的引擎（ExoPlayer）能保证 &gt;100% 真的变响；IJK 等只能依赖
     * LoudnessEnhancer，很多车机上并不生效。这类播放器下音量上限必须压回 100%，
     * 否则用户拖到 200% 却听不出任何变化 —— 那不是功能，是误导。</p>
     */
    private boolean canBoostVolume() {
        return mControlWrapper != null && mControlWrapper.supportsVolumeBoost();
    }

    private float maxVolume() {
        return canBoostVolume() ? VolumeBoostMath.MAX_GAIN : 1f;
    }

    private float clampVolume(float v) {
        return Math.max(0f, Math.min(maxVolume(), v));
    }

    private float loadAppVolume() {
        int percent = Hawk.get(HawkConfig.VOLUME_GAIN, 100);
        // No engine exists during construction; do not discard a saved boost here.
        return VolumeBoostMath.clampGain(percent / 100f);
    }

    /** 应用内音量增益（0..3）：0=静音，1=原始，>1=增幅；不改系统音量。 */
    public void setAppVolume(float volume) {
        mAppVolume = clampVolume(volume);
        if (mControlWrapper != null) mControlWrapper.setVolume(mAppVolume);
        Hawk.put(HawkConfig.VOLUME_GAIN, Math.round(mAppVolume * 100));
    }

    public float getAppVolume() { return mAppVolume; }

    @Override
    public void setMediaPlayer(xyz.doikki.videoplayer.controller.MediaPlayerControl player) {
        super.setMediaPlayer(player);
        mControlWrapper.setVolume(mAppVolume);
    }

    /** 亮度手势默认关闭：亮度交给系统。 */
    public boolean isBrightnessGestureEnabled() {
        return Hawk.get(HawkConfig.BRIGHTNESS_GESTURE, false);
    }

    @Override
    protected void setProgress(int duration, int position) {
        super.setProgress(duration, position);
        mPauseTime.setText(PlayerUtils.stringForTime(position) + " / " + PlayerUtils.stringForTime(duration));
    }

    @Override
    protected void onPlayStateChanged(int playState) {
        super.onPlayStateChanged(playState);
        switch (playState) {
            case VideoView.STATE_IDLE:
                mPauseRoot.setVisibility(GONE);
                mLoading.setVisibility(GONE);
                break;
            case VideoView.STATE_PLAYING:
                mPauseRoot.setVisibility(GONE);
                mLoading.setVisibility(GONE);
                break;
            case VideoView.STATE_PAUSED:
                mPauseRoot.setVisibility(VISIBLE);
                mLoading.setVisibility(GONE);
                break;
            case VideoView.STATE_PREPARED:
            case VideoView.STATE_ERROR:
            case VideoView.STATE_BUFFERED:
                mPauseRoot.setVisibility(GONE);
                mLoading.setVisibility(GONE);
                break;
            case VideoView.STATE_PREPARING:
            case VideoView.STATE_BUFFERING:
                mPauseRoot.setVisibility(GONE);
                mLoading.setVisibility(VISIBLE);
                break;
            case VideoView.STATE_PLAYBACK_COMPLETED:
                mLoading.setVisibility(GONE);
                mPauseRoot.setVisibility(GONE);
                break;
        }
    }

    /**
     * 设置是否可以滑动调节进度，默认可以
     */
    public void setCanChangePosition(boolean canChangePosition) {
        mCanChangePosition = canChangePosition;
    }

    /**
     * 是否在竖屏模式下开始手势控制，默认关闭
     */
    public void setEnableInNormal(boolean enableInNormal) {
        mEnableInNormal = enableInNormal;
    }

    /**
     * 是否开启手势控制，默认开启，关闭之后，手势调节进度，音量，亮度功能将关闭
     */
    public void setGestureEnabled(boolean gestureEnabled) {
        mIsGestureEnabled = gestureEnabled;
    }

    /**
     * 是否开启双击播放/暂停，默认开启
     */
    public void setDoubleTapTogglePlayEnabled(boolean enabled) {
        mIsDoubleTapTogglePlayEnabled = enabled;
    }

    public void hidePauseRoot() {
        if (mPauseRoot != null) {
            mPauseRoot.setVisibility(GONE);
        }
    }

    public void showPauseRoot() {
        if (mPauseRoot != null) {
            mPauseRoot.setVisibility(VISIBLE);
        }
    }

    @Override
    public void setPlayerState(int playerState) {
        super.setPlayerState(playerState);
        if (playerState == VideoView.PLAYER_NORMAL) {
            mCanSlide = mEnableInNormal;
        } else if (playerState == VideoView.PLAYER_FULL_SCREEN) {
            mCanSlide = true;
        }
    }

    @Override
    public void setPlayState(int playState) {
        super.setPlayState(playState);
        mCurPlayState = playState;
        // 播放器就绪/切换（含切窗口回来 onResume 触发的 PLAYING）后，必须把**用户偏好**
        // 重新下发给当前引擎。r68 在这里只 clamp 到能力上限，结果能力探测短暂返回 false
        // 时把 view/processor 压成 1f 却仍显示 300%，用户听到 1x —— 这就是「300% 和
        // 100% 一个声音」的状态分裂。现在改为：能力可用就直接下发保存值；不可用则
        // 只压本次运行值（不写 Hawk），并保留偏好让下次 EXO 就绪时恢复。
        if (playState == VideoView.STATE_PREPARED || playState == VideoView.STATE_PLAYING) {
            reapplyAppVolume();
        }
    }

    /** 重下发用户音量偏好；能力不支持时仅收敛运行值，绝不改写持久化偏好。 */
    private void reapplyAppVolume() {
        float preferred = com.github.tvbox.osc.car.VolumeBoostMath.clampGain(
                Hawk.get(HawkConfig.VOLUME_GAIN, 100) / 100f);
        boolean supported = canBoostVolume();
        float effective = supported ? preferred : Math.min(1f, preferred);
        mAppVolume = preferred;   // 保持 controller 与 UI 显示的是用户偏好
        if (mControlWrapper != null) {
            mControlWrapper.setVolume(effective);
            if (supported && effective > 1.001f) {
                // 某些引擎在 setVolume 之后会重建 sink；补一次增益广播保证 processor 拿到 3f。
                mControlWrapper.setVolume(effective);
            }
        }
        LOG.i("volume_reapply preferred=" + Math.round(preferred * 100)
                + "% effective=" + Math.round(effective * 100) + "% supported=" + supported);
    }

    protected boolean isInPlaybackState() {
        return mControlWrapper != null
                && mCurPlayState != VideoView.STATE_ERROR
                && mCurPlayState != VideoView.STATE_IDLE
                && mCurPlayState != VideoView.STATE_PREPARING
                && mCurPlayState != VideoView.STATE_PREPARED
                && mCurPlayState != VideoView.STATE_START_ABORT
                && mCurPlayState != VideoView.STATE_PLAYBACK_COMPLETED;
    }

    protected boolean canHandleGesture(MotionEvent event) {
        return isInPlaybackState()
                && mIsGestureEnabled
                && mCanSlide
                && !isLocked()
                && !PlayerUtils.isEdge(getContext(), event);
    }


    /**
     * 手指按下的瞬间
     */
    @Override
    public boolean onDown(MotionEvent e) {
        if (!isInPlaybackState() //不处于播放状态
                || !mIsGestureEnabled //关闭了手势
                || PlayerUtils.isEdge(getContext(), e)) //处于屏幕边沿
            return true;
        mAppVolume = loadAppVolume();
        Activity activity = PlayerUtils.scanForActivity(getContext());
        if (activity == null) {
            mBrightness = 0;
        } else {
            mBrightness = activity.getWindow().getAttributes().screenBrightness;
        }
        mFirstTouch = true;
        mChangePosition = false;
        mChangeBrightness = false;
        mChangeVolume = false;
        mGestureAxis = AXIS_NONE;
        mDownX = e.getX();
        mDownY = e.getY();
        mAxisSlopPx = Math.max(12, android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop() * 2);
        mVolumeStart = clampVolume(mAppVolume);
        return true;
    }

    /**
     * 单击
     */
    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        // 缓冲/报错时也允许唤出控制栏：否则全屏下暂停、选集、退出全都不可达
        // （原来只在 isInPlaybackState() 为真时才切换，卡缓冲就永远出不来控制栏）。
        if (mControlWrapper != null && !isLocked()) {
            mControlWrapper.toggleShowState();
        }
        return true;
    }

    /**
     * 双击
     */
    @Override
    public boolean onDoubleTap(MotionEvent e) {
        if (mIsDoubleTapTogglePlayEnabled && !isLocked() && isInPlaybackState()) togglePlay();
        return true;
    }

    /**
     * 在屏幕上滑动
     */
    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        if (!canHandleGesture(e1)) return true;
        // 用「按下点 → 当前点」的累计位移定轴，绝不用单个 MOVE 的逐帧增量。
        // 起始抖动在垂直手势里常常带一点横向噪声，旧写法第一个 MOVE 就把它判成进度，
        // 然后整段手势锁死进度 —— 用户想上下拉调音量却跳了播放位置。
        float deltaX = mDownX - e2.getX();
        float deltaY = mDownY - e2.getY();
        float absX = Math.abs(deltaX), absY = Math.abs(deltaY);
        if (mGestureAxis == AXIS_NONE) {
            if (Math.max(absX, absY) < mAxisSlopPx) return true;  // 轻微抖动：不定轴、不动作
            int axis = com.github.tvbox.osc.car.GestureAxis.decide(
                    deltaX, deltaY, mAxisSlopPx, 1.2f, mCanChangePosition);
            if (axis == com.github.tvbox.osc.car.GestureAxis.NONE) return true;
            mGestureAxis = axis == com.github.tvbox.osc.car.GestureAxis.X ? AXIS_X
                    : axis == com.github.tvbox.osc.car.GestureAxis.Y ? AXIS_Y : AXIS_IGNORE;
            mChangePosition = mGestureAxis == AXIS_X;
            if (mGestureAxis == AXIS_Y) {
                int halfScreen = PlayerUtils.getScreenWidth(getContext(), true) / 2;
                if (mDownX > halfScreen) {
                    mChangeVolume = true;
                } else {
                    mChangeBrightness = isBrightnessGestureEnabled();
                }
                if (!mChangeBrightness && !mChangeVolume) mGestureAxis = AXIS_IGNORE;
            }
            if (mChangePosition || mChangeBrightness || mChangeVolume) {
                for (Map.Entry<IControlComponent, Boolean> next : mControlComponents.entrySet()) {
                    IControlComponent component = next.getKey();
                    if (component instanceof IGestureComponent) {
                        ((IGestureComponent) component).onStartSlide();
                    }
                }
                LOG.i("gesture_axis axis=" + (mChangePosition ? "X" : "Y")
                        + " action=" + (mChangePosition ? "position"
                            : mChangeVolume ? "volume" : "brightness")
                        + " dx=" + Math.round(deltaX) + " dy=" + Math.round(deltaY));
            }
            mFirstTouch = false;
        }
        // 锁定之后整段手势只执行这一个分支，中途方向变化也不切换功能。
        if (mChangePosition) {
            slideToChangePosition(deltaX);
        } else if (mChangeBrightness) {
            slideToChangeBrightness(deltaY);
        } else if (mChangeVolume) {
            slideToChangeVolume(deltaY);
        }
        return true;
    }

    protected void slideToChangePosition(float deltaX) {
        deltaX = -deltaX;
        int width = getMeasuredWidth();
        int duration = PlayerUtils.safeTimeMs(mControlWrapper.getDuration());
        int currentPosition = PlayerUtils.safeTimeMs(mControlWrapper.getCurrentPosition());
        int position = (int) (deltaX / width * 120000 + currentPosition);
        if (position > duration) position = duration;
        if (position < 0) position = 0;
        for (Map.Entry<IControlComponent, Boolean> next : mControlComponents.entrySet()) {
            IControlComponent component = next.getKey();
            if (component instanceof IGestureComponent) {
                ((IGestureComponent) component).onPositionChange(position, currentPosition, duration);
            }
        }
        updateSeekUI(currentPosition, position, duration);
        mSeekPosition = position;
    }

    protected void updateSeekUI(int curr, int seekTo, int duration) {

    }

    protected void slideToChangeBrightness(float deltaY) {
        Activity activity = PlayerUtils.scanForActivity(getContext());
        if (activity == null) return;
        Window window = activity.getWindow();
        WindowManager.LayoutParams attributes = window.getAttributes();
        int height = getMeasuredHeight();
        if (mBrightness == -1.0f) mBrightness = 0.5f;
        float brightness = deltaY * 2 / height * 1.0f + mBrightness;
        if (brightness < 0) {
            brightness = 0f;
        }
        if (brightness > 1.0f) brightness = 1.0f;
        int percent = (int) (brightness * 100);
        attributes.screenBrightness = brightness;
        window.setAttributes(attributes);
        for (Map.Entry<IControlComponent, Boolean> next : mControlComponents.entrySet()) {
            IControlComponent component = next.getKey();
            if (component instanceof IGestureComponent) {
                ((IGestureComponent) component).onBrightnessChange(percent);
            }
        }
        Message msg = Message.obtain();
        msg.what = 100;
        msg.obj = "亮度" + percent + "%";
        mHandler.sendMessage(msg);
        mHandler.removeMessages(101);
        mHandler.sendEmptyMessageDelayed(101, 1000);
    }

    protected void slideToChangeVolume(float deltaY) {
        int height = Math.max(1, getMeasuredHeight());
        if (mVolumeStart < 0f) mVolumeStart = clampVolume(mAppVolume);
        // deltaY 是「按下点 → 当前点」的累计位移，必须基于起手音量算，
        // 不能再叠加到 mAppVolume（旧写法每个 move 事件都加一次累计值，几十像素就 0%→200%）。
        float requestedVolume = com.github.tvbox.osc.car.VolumeBoostMath.gestureVolume(
                mVolumeStart, deltaY, height);
        if (!canBoostVolume() && deltaY > 0 && requestedVolume > 1.001f
                && mControlWrapper != null && mControlWrapper.requestVolumeBoost(requestedVolume)) {
            mAppVolume = Math.max(1f, VolumeBoostMath.clampGain(requestedVolume));
            mControlWrapper.setVolume(mAppVolume);
            Hawk.put(HawkConfig.VOLUME_GAIN, Math.round(mAppVolume * 100));
            int targetPercent = Math.round(mAppVolume * 100);
            LOG.i("volume_boost_requested target=" + targetPercent + "% engine_switch=exo");
            Message switching = Message.obtain();
            switching.what = 100;
            switching.obj = "正在切换 EXO，目标音量 " + targetPercent + "%";
            mHandler.sendMessage(switching);
            mHandler.removeMessages(101);
            mHandler.sendEmptyMessageDelayed(101, 1200);
            return;
        }
        float volume = clampVolume(requestedVolume);
        if (Math.abs(volume - mAppVolume) < 0.002f) {
            // 已经在当前播放器允许的上限（非 EXO 时为 100%）：再往上滑不该静默无效，
            // 给一次「已到上限」的提示，让用户知道是引擎不支持而不是手势失灵。
            if (!canBoostVolume() && deltaY > 0 && mAppVolume >= 0.999f) {
                int percent = Math.round(mAppVolume * 100);
                for (Map.Entry<IControlComponent, Boolean> next : mControlComponents.entrySet()) {
                    IControlComponent component = next.getKey();
                    if (component instanceof IGestureComponent) {
                        ((IGestureComponent) component).onVolumeChange(percent);
                    }
                }
                Message msg = Message.obtain();
                msg.what = 100;
                msg.obj = "音量" + percent + "%（当前播放器不支持放大）";
                mHandler.sendMessage(msg);
                mHandler.removeMessages(101);
                mHandler.sendEmptyMessageDelayed(101, 1000);
            }
            return;
        }
        mAppVolume = volume;
        // 应用内增益：只作用于当前播放器，不改系统音量（车机往往没有音频权限）
        mControlWrapper.setVolume(mAppVolume);
        Hawk.put(HawkConfig.VOLUME_GAIN, Math.round(mAppVolume * 100));
        int percent = Math.round(mAppVolume * 100);
        LOG.i("volume_gesture start=" + Math.round(mVolumeStart * 100) + "% dy="
                + Math.round(deltaY) + " h=" + height + " -> " + percent + "%"
                + " max=" + Math.round(VolumeBoostMath.MAX_GAIN * 100) + "%"
                + " pcm_capable=" + canBoostVolume());
        com.github.tvbox.osc.diag.DiagLog.event("user_volume=" + percent + "%"
                + " pcm_capable=" + canBoostVolume());
        for (Map.Entry<IControlComponent, Boolean> next : mControlComponents.entrySet()) {
            IControlComponent component = next.getKey();
            if (component instanceof IGestureComponent) {
                ((IGestureComponent) component).onVolumeChange(percent);
            }
        }
        Message msg = Message.obtain();
        msg.what = 100;
        msg.obj = "音量" + percent + "%";
        mHandler.sendMessage(msg);
        mHandler.removeMessages(101);
        mHandler.sendEmptyMessageDelayed(101, 1000);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            // 手势结束：解锁主轴，下一次按下重新判定；进度只在本次锁定为 X 轴时提交。
            boolean commit = action == MotionEvent.ACTION_UP
                    && mGestureAxis == AXIS_X && mSeekPosition >= 0;
            if (commit && mControlWrapper != null) mControlWrapper.seekTo(mSeekPosition);
            mSeekPosition = -1;
            mGestureAxis = AXIS_NONE;
            mChangePosition = mChangeBrightness = mChangeVolume = false;
            stopSlide();
        }
        return mGestureDetector.onTouchEvent(event);
    }

    private void stopSlide() {
        for (Map.Entry<IControlComponent, Boolean> next : mControlComponents.entrySet()) {
            IControlComponent component = next.getKey();
            if (component instanceof IGestureComponent) {
                ((IGestureComponent) component).onStopSlide();
            }
        }
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        return false;
    }

    @Override
    public void onLongPress(MotionEvent e) {

    }

    @Override
    public void onShowPress(MotionEvent e) {

    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent e) {
        return false;
    }


    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }

    public boolean onKeyEvent(KeyEvent event) {
        return false;
    }
}
