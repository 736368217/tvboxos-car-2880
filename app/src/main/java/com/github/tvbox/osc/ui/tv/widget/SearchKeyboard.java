package com.github.tvbox.osc.ui.tv.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseMultiItemQuickAdapter;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.chad.library.adapter.base.entity.MultiItemEntity;
import com.github.tvbox.osc.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SearchKeyboard extends FrameLayout {
    private static final int COLUMN_COUNT = 12;
    private final RecyclerView recyclerView;
    private final List<String> keys = Arrays.asList(
            "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "", "",
            "Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P", "", "",
            "A", "S", "D", "F", "G", "H", "J", "K", "L", "Del", "", "",
            "Z", "X", "C", "V", "B", "N", "M", "，", "。", "空格");
    private final List<Keyboard> keyboardList = new ArrayList<>();
    private OnSearchKeyListener searchKeyListener;
    private final OnFocusChangeListener focusChangeListener = (view, focused) -> {
        if (view != null) view.setSelected(focused);
    };

    public SearchKeyboard(@NonNull Context context) {
        this(context, null);
    }

    public SearchKeyboard(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SearchKeyboard(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        View view = LayoutInflater.from(context).inflate(R.layout.layout_keyborad, this);
        recyclerView = view.findViewById(R.id.mRecyclerView);
        GridLayoutManager manager = new GridLayoutManager(context, COLUMN_COUNT);
        manager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return "空格".equals(keys.get(position)) ? 3 : 1;
            }
        });
        recyclerView.setLayoutManager(manager);
        recyclerView.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(@NonNull View child) {
                if (child.isFocusable() && child.getOnFocusChangeListener() == null) {
                    child.setOnFocusChangeListener(focusChangeListener);
                }
            }

            @Override
            public void onChildViewDetachedFromWindow(@NonNull View child) { }
        });

        for (String key : keys) keyboardList.add(new Keyboard(key.isEmpty() ? 2 : 1, key));
        KeyboardAdapter adapter = new KeyboardAdapter(keyboardList);
        recyclerView.setAdapter(adapter);
        view.findViewById(R.id.ux_search_remote).setOnClickListener(v -> dispatchKey(0, "远程搜索"));
        view.findViewById(R.id.ux_search_left).setOnClickListener(v -> dispatchKey(-1, "左移"));
        view.findViewById(R.id.ux_search_right).setOnClickListener(v -> dispatchKey(-2, "右移"));
        adapter.setOnItemClickListener((source, itemView, position) -> {
            Keyboard key = adapter.getItem(position);
            if (key == null || key.key.isEmpty()) return;
            dispatchKey("Del".equals(key.key) ? 1 : position + 2,
                    "空格".equals(key.key) ? " " : key.key);
        });
    }

    private void dispatchKey(int code, String key) {
        if (searchKeyListener != null) searchKeyListener.onSearchKey(code, key);
    }

    static final class Keyboard implements MultiItemEntity {
        private final int itemType;
        private final String key;

        Keyboard(int itemType, String key) {
            this.itemType = itemType;
            this.key = key;
        }

        @Override
        public int getItemType() {
            return itemType;
        }
    }

    private static final class KeyboardAdapter extends BaseMultiItemQuickAdapter<Keyboard, BaseViewHolder> {
        KeyboardAdapter(List<Keyboard> data) {
            super(data);
            addItemType(1, R.layout.item_keyboard);
            addItemType(2, R.layout.item_keyboard_spacer);
        }

        @Override
        protected void convert(BaseViewHolder holder, Keyboard item) {
            if (holder.getItemViewType() == 1) {
                holder.setText(R.id.keyName, item.key);
            } else {
                holder.itemView.setFocusable(false);
                holder.itemView.setClickable(false);
            }
        }
    }

    public void requestFirstKeyFocus() {
        recyclerView.post(() -> {
            View firstKey = recyclerView.getChildAt(0);
            if (firstKey != null && firstKey.isFocusable()) firstKey.requestFocus();
            else recyclerView.requestFocus();
        });
    }

    public void setOnSearchKeyListener(OnSearchKeyListener listener) {
        searchKeyListener = listener;
    }

    public interface OnSearchKeyListener {
        void onSearchKey(int position, String key);
    }
}
