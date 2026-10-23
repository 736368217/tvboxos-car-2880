package com.github.tvbox.osc.car;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.TextView;

/**
 * TextView whose compound drawable and label are centred as one group.
 *
 * <p>Android centres the text inside the area left over after compound padding, while the
 * drawable is pinned to the padding edge. For a fixed-width action button the icon therefore
 * sits well left of centre. This view measures the icon + gap + label group and applies equal
 * side padding so the whole group lands in the middle. Left-aligned search fields and headings
 * keep their normal behaviour.</p>
 */
public class CarIconTextView extends TextView {
    private int baseLeft = -1, baseRight = -1, baseTop = -1, baseBottom = -1;

    public CarIconTextView(Context context) {
        super(context);
    }

    public CarIconTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CarIconTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        applyGroupCentering(width, height);
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter);
        applyGroupCentering(getWidth(), getHeight());
    }

    private void applyGroupCentering(int width, int height) {
        if (width <= 0 || height <= 0) return;
        captureBasePadding();
        int gravity = getGravity();
        boolean centreHorizontal = (gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.CENTER_HORIZONTAL;
        boolean centreVertical = (gravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.CENTER_VERTICAL;
        if (!centreHorizontal && !centreVertical) return;

        Drawable[] drawables = getCompoundDrawables();
        Drawable[] relative = getCompoundDrawablesRelative();
        Drawable left = drawables[0] != null ? drawables[0] : relative[0];
        Drawable top = drawables[1] != null ? drawables[1] : relative[1];

        if (centreHorizontal && left != null) {
            CharSequence text = getText();
            if (TextUtils.isEmpty(text)) return;
            int iconWidth = left.getIntrinsicWidth();
            int gap = getCompoundDrawablePadding();
            int textWidth = (int) Math.ceil(getPaint().measureText(text.toString()));
            int contentWidth = iconWidth + gap + textWidth;
            int available = width - baseLeft - baseRight;
            int side = Math.max(baseLeft, (available - contentWidth) / 2);
            if (Math.abs(side - getPaddingLeft()) > 1 || Math.abs(side - getPaddingRight()) > 1) {
                setPadding(side, baseTop, side, baseBottom);
            }
        } else if (centreVertical && top != null) {
            CharSequence text = getText();
            if (TextUtils.isEmpty(text)) return;
            int iconHeight = top.getIntrinsicHeight();
            int gap = getCompoundDrawablePadding();
            android.graphics.Paint.FontMetricsInt fm = getPaint().getFontMetricsInt();
            int textHeight = fm.bottom - fm.top;
            int contentHeight = iconHeight + gap + textHeight;
            int available = height - baseTop - baseBottom;
            int side = Math.max(baseTop, (available - contentHeight) / 2);
            if (Math.abs(side - getPaddingTop()) > 1 || Math.abs(side - getPaddingBottom()) > 1) {
                setPadding(baseLeft, side, baseRight, side);
            }
        }
    }

    private void captureBasePadding() {
        if (baseLeft >= 0) return;
        baseLeft = getPaddingLeft();
        baseRight = getPaddingRight();
        baseTop = getPaddingTop();
        baseBottom = getPaddingBottom();
    }
}
