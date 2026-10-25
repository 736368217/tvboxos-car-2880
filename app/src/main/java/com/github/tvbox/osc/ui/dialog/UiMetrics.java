package com.github.tvbox.osc.ui.dialog;

/**
 * 自绘弹窗的字号规范（以屏高比例给出的 px 值）。
 *
 * <p>以前每个弹窗各自算字号，漏写 setTextSize 的按钮会退回系统默认 14sp，
 * 在 2880 宽的车机屏上小到看不见。新增弹窗一律从这里取字号。</p>
 */
public final class UiMetrics {
    /** 弹窗标题。 */
    public static final float DIALOG_TITLE = 0.034f;
    /** 列表/设置行文字。 */
    public static final float DIALOG_ROW = 0.026f;
    /** 底部按钮（关闭 / 确定 / 完成）。 */
    public static final float DIALOG_ACTION_TEXT = 0.026f;

    private UiMetrics() {
    }
}
