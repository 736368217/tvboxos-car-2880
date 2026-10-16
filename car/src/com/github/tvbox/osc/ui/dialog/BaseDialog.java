package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;

import xyz.doikki.videoplayer.util.CutoutUtil;

public class BaseDialog extends Dialog {
    private int selectionCardId;
    private boolean outsideDown, outsideMoved;
    private float outsideX;
    private float outsideY;
    private final Rect cardBounds = new Rect();
    private final int[] cardLocation = new int[2];

    public BaseDialog(@NonNull Context context) {
        super(context, R.style.CustomDialogStyle);
    }

    public BaseDialog(Context context, int customDialogStyle) {
        super(context, customDialogStyle);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        CutoutUtil.adaptCutoutAboveAndroidP(this, true);
        super.onCreate(savedInstanceState);
    }

    @Override
    public void show() {
        if (isContextInvalid()) return;
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        super.show();
        hideSysBar();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        setCanceledOnTouchOutside(true);
    }

    protected final void enableSelectionDismissal(int cardViewId) {
        selectionCardId = cardViewId;
        setCancelable(true);
        setCanceledOnTouchOutside(true);
    }

    @Override
    public boolean dispatchTouchEvent(@NonNull MotionEvent event) {
        int action = event.getActionMasked();
        View card = findDialogCard();
        if (card == null || !card.isShown()) return super.dispatchTouchEvent(event);
        card.getLocationOnScreen(cardLocation);
        cardBounds.set(cardLocation[0], cardLocation[1], cardLocation[0] + card.getWidth(),
                cardLocation[1] + card.getHeight());
        boolean inside = cardBounds.contains(Math.round(event.getRawX()), Math.round(event.getRawY()));
        if (action == MotionEvent.ACTION_DOWN) {
            outsideDown = !inside;
            outsideMoved = false;
            outsideX = event.getRawX();
            outsideY = event.getRawY();
        }
        if (outsideDown) {
            float distance = (float) Math.hypot(event.getRawX() - outsideX, event.getRawY() - outsideY);
            if (distance > ViewConfiguration.get(getContext()).getScaledTouchSlop()
                    || action == MotionEvent.ACTION_POINTER_DOWN) outsideMoved = true;
            if (action == MotionEvent.ACTION_UP) {
                outsideDown = false;
                boolean cancel = !inside && !outsideMoved;
                outsideMoved = false;
                if (cancel) cancel();
            } else if (action == MotionEvent.ACTION_CANCEL) {
                outsideDown = outsideMoved = false;
            }
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    private View findDialogCard() {
        if (getWindow() == null) return null;
        ViewGroup content = getWindow().findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) return null;
        if (selectionCardId != 0) {
            View explicit = content.findViewById(selectionCardId);
            if (explicit != null) return explicit;
        }
        View layout = content.getChildAt(0);
        // Full-window FrameLayouts are transparent scrims around a single real panel.
        if (layout instanceof FrameLayout && ((FrameLayout) layout).getChildCount() == 1) {
            return ((FrameLayout) layout).getChildAt(0);
        }
        return layout;
    }

    private boolean isContextInvalid() {
        Context context = getContext();
        if (!(context instanceof Activity)) return false;
        Activity activity = (Activity) context;
        return activity.isFinishing()
                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed());
    }

    private void hideSysBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            int uiOptions = getWindow().getDecorView().getSystemUiVisibility();
            uiOptions |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
            uiOptions |= View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            uiOptions |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            uiOptions |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
            uiOptions |= View.SYSTEM_UI_FLAG_FULLSCREEN;
            uiOptions |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            getWindow().getDecorView().setSystemUiVisibility(uiOptions);
        }
    }
}
