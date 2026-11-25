package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.enhance.TvEnhance;

/**
 * 播放页「增强」弹窗：滤镜 / 超分 / 补帧 / 音效 / 音效对比。
 *
 * <p>TV 操作习惯要点：</p>
 * <ul>
 *   <li><b>弹窗自己接管方向键</b>，不依赖 View 焦点 —— 实测部分电视盒子在 Dialog 里
 *       {@code focused} 恒为 false，只靠 requestFocus 会出现「行看得见却按不动」。</li>
 *   <li>上下换行、左右改值、确认键等价于右移一位（循环），返回键关闭。</li>
 *   <li>画面三项要重建 GL 渲染器，所以标注「下次起播生效」；音效挂在当前音频会话上，
 *       改完立刻能听出区别。</li>
 *   <li>所有改动即时写入偏好，不需要再点保存。</li>
 * </ul>
 */
public class VideoEnhanceDialog extends BaseDialog {

    private static final int ROW_FILTER = 0;
    private static final int ROW_SR = 1;
    private static final int ROW_MEMC = 2;
    private static final int ROW_AUDIO = 3;
    private static final int ROW_COMPARE = 4;
    private static final int ROW_COUNT = 5;

    /** 会话 id 提供者：播放页传控制层实现，设置页可传 null。 */
    public interface SessionProvider {
        int sessionId();
    }

    private final SessionProvider sessionProvider;

    private TextView tvFilter, tvSr, tvMemc, tvAudio, tvCompare, tvFooter;
    private View[] rows;
    private int selectedRow = ROW_FILTER;

    public VideoEnhanceDialog(@NonNull Context context) {
        this(context, null);
    }

    public VideoEnhanceDialog(@NonNull Context context, SessionProvider sessionProvider) {
        super(context);
        this.sessionProvider = sessionProvider;
        setContentView(R.layout.dialog_tv_enhance);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tvFilter = findViewById(R.id.tvFilter);
        tvSr = findViewById(R.id.tvSr);
        tvMemc = findViewById(R.id.tvMemc);
        tvAudio = findViewById(R.id.tvAudio);
        tvCompare = findViewById(R.id.tvCompare);
        tvFooter = findViewById(R.id.tvEnhanceFooter);
        rows = new View[]{findViewById(R.id.rowFilter), findViewById(R.id.rowSr),
                findViewById(R.id.rowMemc), findViewById(R.id.rowAudio),
                findViewById(R.id.rowCompare)};
        for (int i = 0; i < rows.length; i++) {
            final int row = i;
            if (rows[i] != null) {
                rows[i].setOnClickListener(v -> {
                    selectedRow = row;
                    refresh();
                });
            }
        }
        refresh();
        setOnShowListener(dialog -> select(ROW_FILTER));
    }

    private void select(int row) {
        selectedRow = Math.max(0, Math.min(ROW_COUNT - 1, row));
        refresh();
    }

    /** 高亮当前行：选中行保留默认背景，其余行设为透明，肉眼可辨。 */
    private void refresh() {
        for (int i = 0; i < rows.length; i++) {
            if (rows[i] == null) continue;
            boolean on = i == selectedRow;
            rows[i].setBackgroundResource(on
                    ? R.drawable.shape_setting_model_focus : android.R.color.transparent);
            rows[i].setAlpha(on ? 1f : 0.72f);
        }
        if (tvFilter != null) tvFilter.setText(TvEnhance.filterName());
        if (tvSr != null) tvSr.setText(TvEnhance.srName());
        if (tvMemc != null) tvMemc.setText(TvEnhance.memcName());
        if (tvAudio != null) tvAudio.setText(TvEnhance.audioName());
        if (tvCompare != null) tvCompare.setText(TvEnhance.audioCompareLabel());
        if (tvFooter != null) {
            boolean audioOn = TvEnhance.audioEnhancementEnabled()
                    && TvEnhance.audioMode() > 0
                    && sessionId() > 0;
            tvFooter.setText("画面四项下次起播生效 · 音效"
                    + (audioOn ? "已在当前播放生效" : "将在下次起播生效"));
        }
    }

    private int sessionId() {
        try {
            return sessionProvider == null ? 0 : sessionProvider.sessionId();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /** 音效改动立即挂到当前会话，让用户当场听出差别。 */
    private void applyAudioNow() {
        TvEnhance.applyAudio(sessionId());
    }

    private void step(int direction) {
        switch (selectedRow) {
            case ROW_FILTER: {
                int count = Math.max(1, TvEnhance.filterCount());
                int next = (TvEnhance.filterMode() + direction + count) % count;
                TvEnhance.setFilterMode(next);
                break;
            }
            case ROW_SR: {
                int count = TvEnhance.srMaxTier() + 1;
                int next = (TvEnhance.srTier() + direction + count) % count;
                TvEnhance.setSrTier(next);
                break;
            }
            case ROW_MEMC: {
                int count = TvEnhance.memcMaxTier() + 1;
                int next = (TvEnhance.memcTier() + direction + count) % count;
                TvEnhance.setMemcTier(next);
                break;
            }
            case ROW_AUDIO: {
                int count = Math.max(1, TvEnhance.audioModeCount());
                int next = (TvEnhance.audioMode() + direction + count) % count;
                TvEnhance.setAudioMode(next);
                if (!TvEnhance.audioEnhancementEnabled()) {
                    TvEnhance.setAudioEnhancementEnabled(true);
                }
                applyAudioNow();
                break;
            }
            case ROW_COMPARE: {
                TvEnhance.toggleAudioEnhancement();
                applyAudioNow();
                break;
            }
            default:
                break;
        }
        refresh();
    }

    @Override
    public boolean onKeyDown(int keyCode, @NonNull KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                select(selectedRow - 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                select(selectedRow + 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                step(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                step(1);
                return true;
            case KeyEvent.KEYCODE_BACK:
                dismiss();
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }
}
