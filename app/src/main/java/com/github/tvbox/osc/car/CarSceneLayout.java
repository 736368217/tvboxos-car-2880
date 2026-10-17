package com.github.tvbox.osc.car;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

/**
 * A native design-pixel viewport. The single child remains a normal Android view tree;
 * Android applies its transform to both drawing and touch dispatch (no bitmap UI).
 * The default 2880x1620 scene fits the available app window without changing device DPI.
 * An optional android:tag="width,height" selects another design viewport.
 */
public final class CarSceneLayout extends ViewGroup {
    private int designWidth = 2880;
    private int designHeight = 1620;

    public CarSceneLayout(Context context) { this(context, null); }
    public CarSceneLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        setClipChildren(true);
        setClipToPadding(true);
    }

    @Override protected void onFinishInflate() {
        super.onFinishInflate();
        if (getChildCount() != 1) throw new IllegalStateException("Car scene needs one native root");
        Object value = getTag();
        if (value instanceof String) {
            String[] size = ((String) value).split(",");
            if (size.length == 2) {
                try {
                    int width = Integer.parseInt(size[0].trim());
                    int height = Integer.parseInt(size[1].trim());
                    if (width > 0 && height > 0) { designWidth = width; designHeight = height; }
                } catch (NumberFormatException ignored) { }
            }
        }
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = resolveSize(designWidth, widthSpec);
        int height = resolveSize(designHeight, heightSpec);
        setMeasuredDimension(width, height);
        if (getChildCount() > 0) getChildAt(0).measure(
                MeasureSpec.makeMeasureSpec(designWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(designHeight, MeasureSpec.EXACTLY));
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (getChildCount() == 0) return;
        View scene = getChildAt(0);
        float scale = Math.min((float) getWidth() / designWidth, (float) getHeight() / designHeight);
        int x = Math.round((getWidth() - designWidth * scale) / 2f);
        int y = Math.round((getHeight() - designHeight * scale) / 2f);
        scene.setPivotX(0f);
        scene.setPivotY(0f);
        scene.setScaleX(scale);
        scene.setScaleY(scale);
        scene.layout(x, y, x + designWidth, y + designHeight);
    }

    public static boolean contains(View view) {
        for (ViewParent parent = view == null ? null : view.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof CarSceneLayout) return true;
        }
        return false;
    }

    @Override protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    }
    @Override public LayoutParams generateLayoutParams(AttributeSet attrs) { return new LayoutParams(getContext(), attrs); }
    @Override protected LayoutParams generateLayoutParams(LayoutParams params) { return new LayoutParams(params); }
}
