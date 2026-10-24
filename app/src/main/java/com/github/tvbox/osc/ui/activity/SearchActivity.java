package com.github.tvbox.osc.ui.activity;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.lifecycle.ViewModelProvider;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.event.ServerEvent;
import com.github.tvbox.osc.ui.adapter.PinyinAdapter;
import com.github.tvbox.osc.ui.adapter.SearchAdapter;
import com.github.tvbox.osc.ui.adapter.ux_search_ResultAdapter;
import com.github.tvbox.osc.ui.adapter.ux_search_WordAdapter;
import com.github.tvbox.osc.ui.dialog.RemoteDialog;
import com.github.tvbox.osc.ui.dialog.SearchCheckboxDialog;
import com.github.tvbox.osc.ui.tv.widget.SearchKeyboard;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.HistoryHelper;
import com.github.tvbox.osc.util.SearchHelper;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author pj567
 * @date :2020/12/23
 * @description:
 */
public class SearchActivity extends BaseActivity {
    private static final String HOT_SEARCH_URL = "https://movie.douban.com/j/search_subjects?type=tv&tag=%E7%83%AD%E9%97%A8&sort=recommend&page_limit=20&page_start=0";
    private static final int SEARCH_THREAD_COUNT = 6;
    private static final int SEARCH_PUMP_SECONDS = 2;
    private static final int SEARCH_NEXT_BATCH_SECONDS = 3;
    private static final int SEARCH_SITE_TIMEOUT_SECONDS = 15;
    private static final String[] DEFAULT_HOT_WORDS = {
            "\u5bb6\u4e1a",
            "\u4e3b\u89d2",
            "\u4f4e\u667a\u5546\u72af\u7f6a",
            "\u82cf\u8d85",
            "\u4e66\u5377\u4e00\u68a6",
            "\u7f8e\u4eba\u4f59",
            "\u85cf\u6d77\u4f20",
            "\u957f\u5b89\u7684\u8354\u679d",
            "\u5e86\u4f59\u5e74",
            "\u51e1\u4eba\u4fee\u4ed9\u4f20"
    };
    private LinearLayout llLayout;
    private LinearLayout llHistoryWord;
    private TvRecyclerView mGridView;
    private TvRecyclerView mGridViewWord;
    private GridLayout historyWordGrid;
    private View searchEmpty;
    private View searchIdle;
    private View searchResultTitle;
    SourceViewModel sourceViewModel;
    private RemoteDialog remoteDialog;
    private EditText etSearch;
    private TextView tvSearch;
    private TextView tvClear;
    private ImageView tvHistoryClear;
    private SearchKeyboard keyboard;
    private SearchAdapter searchAdapter;
    private PinyinAdapter wordAdapter;
    private PinyinAdapter hotWordAdapter;
    private String searchTitle = "";
    private final List<Movie.Video> highMatchVods = new ArrayList<>();
    private boolean showHighMatchResults = false;
    private TextView tvSearchCheckboxBtn;

    private static HashMap<String, String> mCheckSources = null;
    private SearchCheckboxDialog mSearchCheckboxDialog = null;

    private TextView wordsSwitch;
    private boolean aggregateSearchMode;
    private boolean aggregateSearchModeInited = false;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_search;
    }


    private static Boolean hasKeyBoard;
    private static Boolean isSearchBack;
    @Override
    protected void init() {
        initView();
        initViewModel();
        initData();
        hasKeyBoard = true;
        isSearchBack = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (searchPaused) {
            resumePausedSearches();
        }
        requestSearchFocusWhenReady();
        applySearchWordMode();
        refreshSearchHistoryWords();
        if (hots != null && !hots.isEmpty()) setHotWordsData(hots);
    }

    private void requestSearchFocusWhenReady() {
        final View focusView = hasKeyBoard || isSearchBack ? tvSearch : etSearch;
        if (focusView == null) return;
        focusView.post(new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) return;
                focusView.requestFocus();
                focusView.requestFocusFromTouch();
            }
        });
    }

    private void initView() {
        EventBus.getDefault().register(this);
        llLayout = findViewById(R.id.llLayout);
        llHistoryWord = findViewById(R.id.llHistoryWord);
        etSearch = findViewById(R.id.etSearch);
        tvSearch = findViewById(R.id.tvSearch);
        tvSearchCheckboxBtn = findViewById(R.id.tvSearchCheckboxBtn);
        tvClear = findViewById(R.id.tvClear);
        mGridView = findViewById(R.id.mGridView);
        keyboard = findViewById(R.id.keyBoardRoot);
        mGridViewWord = findViewById(R.id.mGridViewWord);
        historyWordGrid = findViewById(R.id.historyWordGrid);
        tvHistoryClear = findViewById(R.id.tvHistoryClear);
        searchEmpty = findViewById(R.id.uxSearchEmpty);
        searchIdle = findViewById(R.id.uxSearchIdle);
        searchResultTitle = findViewById(R.id.ux_search_result_title);
        findViewById(R.id.uxSearchEmptyClear).setOnClickListener(v -> tvClear.performClick());
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        findViewById(R.id.ux_search_back).setOnClickListener(v -> onBackPressed());
        findViewById(R.id.ux_search_ime).setOnClickListener(v -> showSystemKeyboard());
        findViewById(R.id.ux_search_aggregate).setOnClickListener(v -> {
            String word = etSearch.getText().toString().trim();
            if (TextUtils.isEmpty(word)) {
                Toast.makeText(this, "请先输入片名，再打开全屏聚合搜索", Toast.LENGTH_SHORT).show();
                return;
            }
            Bundle bundle = new Bundle();
            bundle.putString("title", word);
            jumpActivity(FastSearchActivity.class, bundle);
        });
        mGridViewWord.setHasFixedSize(true);
        wordAdapter = new ux_search_WordAdapter();
        hotWordAdapter = new ux_search_WordAdapter();
        wordsSwitch = findViewById(R.id.wordSwitch);
        applySearchWordMode();
        wordAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                startSearch(wordAdapter.getItem(position));
            }
        });
        hotWordAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                startSearch(hotWordAdapter.getItem(position));
            }
        });
        mGridView.setHasFixedSize(true);
        mGridView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
        searchAdapter = new ux_search_ResultAdapter();
        mGridView.setAdapter(searchAdapter);
        searchAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                Movie.Video video = searchAdapter.getData().get(position);
                if (video != null) {
                    openSearchVideo(video);
                }
            }
        });
        wordsSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Both modes keep their history rail available; this toggles hot/history chips.
                FastClickCheckUtil.check(v);
                String wd = wordsSwitch.getText().toString().trim();
                if(wd.contains("热词")){
                    ArrayList<String> hisWord= Hawk.get(HawkConfig.SEARCH_HISTORY, new ArrayList<String>());
                    if (hisWord.isEmpty()){
                        Toast.makeText(mContext, "暂无历史搜索", Toast.LENGTH_SHORT).show();
                    }else {
                        wordsSwitch.setText("历史 搜索");
                        (aggregateSearchMode ? hotWordAdapter : wordAdapter).setNewData(hisWord);
                    }
                }
                if(!wd.contains("热词")){
                    wordsSwitch.setText("热词 搜索");
                    if(hots!=null && !hots.isEmpty()){
                        setHotWordsData(hots);
                    }
                }
            }
        });
        tvHistoryClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                HistoryHelper.clearSearchHistory();
                refreshSearchHistoryWords();
                if ("历史 搜索".contentEquals(wordsSwitch.getText())) {
                    (aggregateSearchMode ? hotWordAdapter : wordAdapter).setNewData(new ArrayList<>());
                }
                Toast.makeText(mContext, "已清空搜索历史", Toast.LENGTH_SHORT).show();
            }
        });
        tvSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                hasKeyBoard = true;
                String wd = etSearch.getText().toString().trim();
                if (!TextUtils.isEmpty(wd)) {
                    startSearch(wd);
                } else {
                    Toast.makeText(mContext, "输入内容不能为空", Toast.LENGTH_SHORT).show();
                }
            }
        });
        tvClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                cancel();
                searchTitle = "";
                folderHistory.clear();
                folderLoading = false;
                searchAdapter.setNewData(new ArrayList<>());
                etSearch.setText("");
                showSuccess();
                showSearchIdle();
                setHotWordsData(hots == null ? new ArrayList<>() : hots);
            }
        });

        //软键盘

        etSearch.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEARCH || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    String wd = etSearch.getText().toString().trim();
                    if (!TextUtils.isEmpty(wd)) {
                        startSearch(wd);
                    } else {
                        Toast.makeText(mContext, "输入内容不能为空", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }
                return false;
            }
        });

        // 监听遥控器
        etSearch.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
                    String wd = etSearch.getText().toString().trim();
                    if (!TextUtils.isEmpty(wd)) {
                        startSearch(wd);
                    } else {
                        Toast.makeText(mContext, "输入内容不能为空", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }
                return false;
            }
        });
        keyboard.setOnSearchKeyListener(new SearchKeyboard.OnSearchKeyListener() {
            @Override
            public void onSearchKey(int pos, String key) {
                if (pos == 0) {
                    remoteDialog = new RemoteDialog(mContext);
                    remoteDialog.show();
                    return;
                }
                int start = Math.max(0, etSearch.getSelectionStart());
                int end = Math.max(start, etSearch.getSelectionEnd());
                if (pos == -1 || pos == -2) {
                    String text = etSearch.getText().toString();
                    int cursor = pos == -1 ? (start > 0 ? text.offsetByCodePoints(start, -1) : 0)
                            : (end < text.length() ? text.offsetByCodePoints(end, 1) : text.length());
                    etSearch.setSelection(cursor);
                    return;
                }
                if (pos == 1) {
                    if (start != end) etSearch.getText().delete(start, end);
                    else if (start > 0) etSearch.getText().delete(
                            etSearch.getText().toString().offsetByCodePoints(start, -1), start);
                } else {
                    etSearch.getText().replace(start, end, key);
                }
                String text = etSearch.getText().toString();
                if (!TextUtils.isEmpty(text)) loadRec(text);
            }
        });
        setLoadSir(llLayout);
        tvSearchCheckboxBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                List<SourceBean> searchAbleSource = ApiConfig.get().getSearchSourceBeanList();
                if (mSearchCheckboxDialog == null) {
                    mSearchCheckboxDialog = new SearchCheckboxDialog(SearchActivity.this, searchAbleSource, mCheckSources);
                }else {
                    if(searchAbleSource.size()!=mSearchCheckboxDialog.mSourceList.size()){
                        mSearchCheckboxDialog.setMSourceList(searchAbleSource);
                    }
                }
                mSearchCheckboxDialog.show();
            }
        });
    }

    private void startSearch(String wd) {
        if (TextUtils.isEmpty(wd)) {
            return;
        }
        etSearch.setText(wd);
        etSearch.setSelection(wd.length());
        search(wd);
        refreshSearchHistoryWords();
    }

    private boolean isAggregateSearchMode() {
        return Hawk.get(HawkConfig.FAST_SEARCH_MODE, true);
    }

    private void setAggregateHotTitle() {
        wordsSwitch.setText("热词 | 历史");
        wordsSwitch.setTextSize(TypedValue.COMPLEX_UNIT_PX, 28);
        wordsSwitch.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            wordsSwitch.setLetterSpacing(0.08f);
        }
    }

    private void setNormalWordTitle() {
        wordsSwitch.setText("热词 | 历史");
        wordsSwitch.setTextSize(TypedValue.COMPLEX_UNIT_PX, 28);
        wordsSwitch.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            wordsSwitch.setLetterSpacing(0f);
        }
    }

    private void applySearchWordMode() {
        boolean aggregateMode = isAggregateSearchMode();
        if (aggregateSearchModeInited && aggregateSearchMode == aggregateMode) {
            return;
        }
        aggregateSearchModeInited = true;
        aggregateSearchMode = aggregateMode;
        llHistoryWord.setVisibility(View.VISIBLE);
        llLayout.setVisibility(View.VISIBLE);
        setNormalWordTitle();
        wordsSwitch.setFocusable(true);
        wordsSwitch.setBackgroundResource(R.drawable.ux_setting_choice);
        mGridViewWord.setLayoutManager(new V7GridLayoutManager(this.mContext, 5));
        mGridViewWord.setAdapter(aggregateSearchMode ? hotWordAdapter : wordAdapter);
        refreshSearchHistoryWords();
    }

    private void setHotWordsData(ArrayList<String> data) {
        ArrayList<String> visible = data == null ? new ArrayList<>() : data;
        if (visible.size() > 15) visible = new ArrayList<>(visible.subList(0, 15));
        if (aggregateSearchMode) {
            hotWordAdapter.setNewData(visible);
        } else {
            wordAdapter.setNewData(visible);
        }
    }

    private void refreshSearchHistoryWords() {
        historyWordGrid.post(new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) return;
                ArrayList<String> history = Hawk.get(HawkConfig.SEARCH_HISTORY, new ArrayList<String>());
                historyWordGrid.removeAllViews();
                if (history.isEmpty()) {
                    // One empty state per panel: the centred idle block already explains the next step.
                    if (llHistoryWord != null) llHistoryWord.setVisibility(View.GONE);
                    if (tvHistoryClear != null) tvHistoryClear.setVisibility(View.GONE);
                    return;
                }
                if (llHistoryWord != null) llHistoryWord.setVisibility(View.VISIBLE);
                if (tvHistoryClear != null) tvHistoryClear.setVisibility(View.VISIBLE);
                int itemHeight = 90;
                int itemMargin = 2;
                int paddingH = 24;
                int minWidth = 190;
                int availableWidth = historyWordGrid.getWidth();
                if (availableWidth <= 0) availableWidth = llHistoryWord.getWidth();
                float textSize = 28;
                int textColor = getResources().getColor(R.color.car_text);
                LinearLayout row = null;
                int rowWidth = 0;
                for (int i = 0; i < history.size(); i++) {
                    final String word = history.get(i);
                    TextView item = new TextView(SearchActivity.this);
                    item.setText(word);
                    item.setSingleLine(true);
                    item.setGravity(Gravity.CENTER);
                    item.setIncludeFontPadding(false);
                    item.setFocusable(true);
                    item.setTextColor(textColor);
                    item.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
                    item.setMinWidth(minWidth);
                    item.setPadding(paddingH, 0, paddingH, 0);
                    item.setBackgroundResource(R.drawable.ux_setting_choice);
                    item.measure(
                            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                            View.MeasureSpec.makeMeasureSpec(itemHeight, View.MeasureSpec.EXACTLY));
                    int itemWidth = Math.min(Math.max(minWidth, item.getMeasuredWidth()), Math.max(minWidth, availableWidth - 32));
                    int rowItemWidth = itemWidth + itemMargin * 2;
                    if (row == null || (rowWidth > 0 && rowWidth + rowItemWidth > availableWidth)) {
                        row = new LinearLayout(SearchActivity.this);
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        GridLayout.LayoutParams rowParams = new GridLayout.LayoutParams(
                                GridLayout.spec(GridLayout.UNDEFINED),
                                GridLayout.spec(GridLayout.UNDEFINED));
                        rowParams.width = GridLayout.LayoutParams.MATCH_PARENT;
                        rowParams.height = GridLayout.LayoutParams.WRAP_CONTENT;
                        historyWordGrid.addView(row, rowParams);
                        rowWidth = 0;
                    }
                    item.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            startSearch(word);
                        }
                    });
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(itemWidth, itemHeight);
                    params.setMargins(itemMargin, itemMargin, itemMargin, itemMargin);
                    row.addView(item, params);
                    rowWidth += rowItemWidth;
                }
            }
        });
    }

    private void initViewModel() {
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        sourceViewModel.listResult.observe(this, new androidx.lifecycle.Observer<AbsXml>() {
            @Override
            public void onChanged(AbsXml data) {
                if (!folderLoading) return;
                folderLoading = false;
                if (data == null || data.movie == null || data.movie.videoList == null) {
                    showEmpty();
                    return;
                }
                showSuccess();
                mGridView.setVisibility(View.VISIBLE);
                searchAdapter.setNewData(data.movie.videoList);
            }
        });
    }

    private void openSearchVideo(Movie.Video video) {
        pauseSearchTasks();
        hasKeyBoard = false;
        if (TextUtils.equals("folder", video.tag)) {
            folderHistory.add(new ArrayList<>(searchAdapter.getData()));
            folderLoading = true;
            showLoading();
            sourceViewModel.getList(video.sourceKey, video.id);
            return;
        }
        isSearchBack = true;
        Bundle bundle = new Bundle();
        bundle.putString("id", video.id);
        bundle.putString("sourceKey", video.sourceKey);
        bundle.putString("title", video.name);
        bundle.putString("picture", video.pic);
        putDetailFallbackCandidates(bundle, video);
        jumpActivity(DetailActivity.class, bundle);
    }

    @Override
    public void onBackPressed() {
        if (!folderHistory.isEmpty()) {
            folderLoading = false;
            List<Movie.Video> previous = folderHistory.remove(folderHistory.size() - 1);
            showSuccess();
            mGridView.setVisibility(View.VISIBLE);
            searchAdapter.setNewData(previous);
            return;
        }
        super.onBackPressed();
    }

    /**
     * 拼音联想
     */
    private ArrayList<String> localPinyinCandidates(String query) {
        ArrayList<String> matches = new ArrayList<>();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return matches;
        String prefix = query.replaceAll("[^A-Za-z0-9]", "").toLowerCase(java.util.Locale.ROOT);
        if (prefix.isEmpty()) return matches;
        ArrayList<String> candidates = new ArrayList<>();
        if (hots != null) candidates.addAll(hots);
        ArrayList<String> history = Hawk.get(HawkConfig.SEARCH_HISTORY, new ArrayList<String>());
        if (history != null) candidates.addAll(history);
        for (String word : DEFAULT_HOT_WORDS) candidates.add(word);
        HashSet<String> seen = new HashSet<>();
        android.icu.text.Transliterator transliterator;
        try {
            transliterator = android.icu.text.Transliterator.getInstance("Han-Latin; Latin-ASCII");
        } catch (RuntimeException ignored) {
            return matches;
        }
        for (String candidate : candidates) {
            if (TextUtils.isEmpty(candidate) || seen.contains(candidate)) continue;
            seen.add(candidate);
            String latin = transliterator.transliterate(candidate).toLowerCase(java.util.Locale.ROOT);
            StringBuilder initials = new StringBuilder();
            boolean tokenStart = true;
            for (int i = 0; i < latin.length(); i++) {
                char value = latin.charAt(i);
                if (Character.isLetterOrDigit(value)) {
                    if (tokenStart) initials.append(value);
                    tokenStart = false;
                } else {
                    tokenStart = true;
                }
            }
            if (initials.toString().startsWith(prefix)) {
                matches.add(candidate);
                if (matches.size() >= 21) break;
            }
        }
        return matches;
    }

    private void loadRec(String key) {
        final String requestedKey = key == null ? "" : key.trim();
        if (requestedKey.isEmpty()) return;
        ArrayList<String> localSuggestions = localPinyinCandidates(requestedKey);
        if (!localSuggestions.isEmpty()) {
            wordsSwitch.setText("猜你想搜");
            setHotWordsData(localSuggestions);
        }
        OkGo.get("https://tv.aiseet.atianqi.com/i-tvbin/qtv_video/search/get_search_smart_box")
                .params("format", "json")
                .params("page_num", 0)
                .params("page_size", 20)
                .params("key", requestedKey)
                .execute(new AbsCallback() {
                    @Override
                    public void onSuccess(Response response) {
                        try {
                            if (!TextUtils.equals(requestedKey, etSearch.getText().toString().trim())) return;
                            ArrayList<String> remoteSuggestions = new ArrayList<>();
                            String result = (String) response.body();
                            Gson gson = new Gson();
                            JsonElement json = gson.fromJson(result, JsonElement.class);
                            JsonArray groupDataArr = json.getAsJsonObject()
                                    .get("data").getAsJsonObject()
                                    .get("search_data").getAsJsonObject()
                                    .get("vecGroupData").getAsJsonArray()
                                    .get(0).getAsJsonObject()
                                    .get("group_data").getAsJsonArray();
                            for (JsonElement groupDataElement : groupDataArr) {
                                JsonObject groupData = groupDataElement.getAsJsonObject();
                                String keywordTxt = groupData.getAsJsonObject("dtReportInfo")
                                        .getAsJsonObject("reportData")
                                        .get("keyword_txt").getAsString();
                                remoteSuggestions.add(keywordTxt.trim());
                            }
                            if (remoteSuggestions.isEmpty()) return;
                            wordsSwitch.setText("猜你想搜");
                            setHotWordsData(remoteSuggestions);
                            mGridViewWord.smoothScrollToPosition(0);
                        } catch (Throwable th) {
                            th.printStackTrace();
                        }
                    }

                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        return response.body().string();
                    }
                });
    }

    private static ArrayList<String> hots;
    private static boolean hotWordsRequested;

    private void useDefaultHotWords() {
        ArrayList<String> data = new ArrayList<>();
        for (String word : DEFAULT_HOT_WORDS) {
            data.add(word);
        }
        cacheHotWords(data);
    }

    private void cacheHotWords(ArrayList<String> data) {
        hots = data;
        setHotWordsData(hots);
    }

    private String cleanHotWord(String title) {
        if (TextUtils.isEmpty(title)) return "";
        return title.trim().replaceAll("<|>|《|》|-", "").split(" ")[0];
    }

    private void addHotWord(ArrayList<String> data, String title) {
        String word = cleanHotWord(title);
        if (!TextUtils.isEmpty(word) && !data.contains(word)) {
            data.add(word);
        }
    }

    private void initData() {
        initCheckedSourcesForSearch();
        applySearchWordMode();
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("title")) {
            String title = intent.getStringExtra("title");
            showLoading();
            intent.removeExtra("title");
            startSearch(title);
        }
        if (aggregateSearchMode) {
            setAggregateHotTitle();
            refreshSearchHistoryWords();
        } else {
            setNormalWordTitle();
        }
        if(hots!=null && !hots.isEmpty()){
            setHotWordsData(hots);
            return;
        }
        if (hotWordsRequested) {
            return;
        }
        hotWordsRequested = true;
        // 加载热词
        OkGo.<String>get(HOT_SEARCH_URL)
//        OkGo.<String>get("https://api.web.360kan.com/v1/rank")
//                .params("cat", "1")
                .headers("User-Agent", "Mozilla/5.0")
                .execute(new AbsCallback<String>() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        try {
                            ArrayList<String> data = new ArrayList<String>();
                            JsonArray itemList = JsonParser.parseString(response.body()).getAsJsonObject().get("subjects").getAsJsonArray();
//                            JsonArray itemList = JsonParser.parseString(response.body()).getAsJsonObject().get("data").getAsJsonArray();
                            for (JsonElement ele : itemList) {
                                JsonObject obj = (JsonObject) ele;
                                if (obj.has("title")) {
                                    addHotWord(data, obj.get("title").getAsString());
                                }
                            }
                            if (data.isEmpty()) {
                                useDefaultHotWords();
                                return;
                            }
                            cacheHotWords(data);
                        } catch (Throwable th) {
                            th.printStackTrace();
                            useDefaultHotWords();
                        }
                    }

                    @Override
                    public void onError(Response<String> response) {
                        super.onError(response);
                        useDefaultHotWords();
                    }

                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        return response.body().string();
                    }
                });

    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void server(ServerEvent event) {
        if (event.type == ServerEvent.SERVER_SEARCH) {
            String title = (String) event.obj;
            showLoading();
            if(Hawk.get(HawkConfig.FAST_SEARCH_MODE, true)){
                Bundle bundle = new Bundle();
                bundle.putString("title", title);
                jumpActivity(FastSearchActivity.class, bundle);
            }else{
                search(title);
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_SEARCH_RESULT) {
            try {
                searchData(event.obj == null ? null : (AbsXml) event.obj);
            } catch (Exception e) {
                searchData(null);
            }
        }
    }

    private void initCheckedSourcesForSearch() {
        mCheckSources = SearchHelper.getSourcesForSearch();
    }

    public static void setCheckedSourcesForSearch(HashMap<String,String> checkedSources) {
        mCheckSources = checkedSources;
    }

    private void search(String title) {
        cancel();
        if (remoteDialog != null) {
            remoteDialog.dismiss();
            remoteDialog = null;
        }
        showLoading();
        etSearch.setText(title);
        etSearch.setSelection(etSearch.length());

        //写入历史记录
        HistoryHelper.setSearchHistory(title);


        this.searchTitle = title;
        mGridView.setVisibility(View.INVISIBLE);
        searchAdapter.setNewData(new ArrayList<>());
        searchResult();
    }

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private AtomicInteger allRunCount = new AtomicInteger(0);
    private final Set<String> pendingSearchKeys = Collections.synchronizedSet(new HashSet<String>());
    private final List<SearchTask> waitingSearchTasks = Collections.synchronizedList(new ArrayList<SearchTask>());
    private final Set<String> startedSearchKeys = Collections.synchronizedSet(new HashSet<String>());
    private final Set<String> releasedSearchKeys = Collections.synchronizedSet(new HashSet<String>());
    private final AtomicInteger totalSearchCount = new AtomicInteger(0);
    private volatile String currentSearchToken = "";
    private boolean searchPaused = false;
    private final List<Movie.Video> detailFallbackSearchResults = new ArrayList<>();
    private final List<List<Movie.Video>> folderHistory = new ArrayList<>();
    private boolean folderLoading;

    private void searchResult() {
        cancel();
        searchAdapter.setNewData(new ArrayList<>());
        allRunCount.set(0);
        pendingSearchKeys.clear();
        waitingSearchTasks.clear();
        startedSearchKeys.clear();
        releasedSearchKeys.clear();
        highMatchVods.clear();
        detailFallbackSearchResults.clear();
        folderHistory.clear();
        showHighMatchResults = false;
        totalSearchCount.set(0);
        currentSearchToken = sourceViewModel.beginSearches();
        searchPaused = false;
        List<SourceBean> searchRequestList = new ArrayList<>();
        searchRequestList.addAll(ApiConfig.get().getSourceBeanList());
        SourceBean home = ApiConfig.get().getHomeSourceBean();
        searchRequestList.remove(home);
        if (home != null) searchRequestList.add(0, home);

        ArrayList<SearchTask> searchTasks = new ArrayList<>();
        for (SourceBean bean : searchRequestList) {
            if (bean == null || !bean.isSearchable()) continue;
            if (mCheckSources != null && !mCheckSources.containsKey(bean.getKey())) continue;
            searchTasks.add(new SearchTask(bean.getKey(), searchTitle, currentSearchToken, isBlockingSearchSource(bean)));
        }
        if (searchTasks.isEmpty()) {
            Toast.makeText(mContext, "没有指定搜索源", Toast.LENGTH_SHORT).show();
            showEmpty();
            cancel();
            return;
        }
        for (SearchTask task : searchTasks) pendingSearchKeys.add(task.sourceKey);
        allRunCount.set(searchTasks.size());
        totalSearchCount.set(searchTasks.size());
        // Keep ordinary HTTP sources independent of slow blocking Spider workers.
        for (SearchTask task : searchTasks) {
            if (!task.blocking && startedSearchKeys.add(task.sourceKey)) submitSearchTask(task);
        }
        waitingSearchTasks.addAll(searchTasks);
        startNextSearchBatch(currentSearchToken);
        startSearchPump(currentSearchToken);
    }

    private boolean matchSearchResult(String name, String searchTitle) {
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(searchTitle)) return false;
        return TextUtils.equals(name.trim(), searchTitle.trim());
    }

    private boolean isHighMatchSearchResult(Movie.Video video) {
        return video != null && !TextUtils.isEmpty(video.name) && !TextUtils.isEmpty(searchTitle)
                && video.name.replaceAll("\\s+", "").startsWith(searchTitle.replaceAll("\\s+", ""));
    }

    private boolean shouldShowHighMatchResults() {
        if (showHighMatchResults || searchAdapter.getData().size() > 0) return false;
        int total = totalSearchCount.get();
        int threshold = Math.min(SEARCH_THREAD_COUNT, total);
        return threshold > 0 && total - allRunCount.get() >= threshold;
    }

    private void addSearchResults(List<Movie.Video> data) {
        if (data == null || data.isEmpty()) return;
        if (searchAdapter.getData().size() > 0) {
            searchAdapter.addData(data);
        } else {
            showSuccess();
            mGridView.setVisibility(View.VISIBLE);
            searchAdapter.setNewData(data);
        }
    }

    private void searchData(AbsXml absXml) {
        if (!isCurrentSearchResult(absXml)) {
            return;
        }
        String sourceKey = absXml == null ? "" : absXml.sourceKey;
        if (!markSearchFinished(sourceKey, absXml.searchToken)) {
            return;
        }
        releaseSearchSlotAndStartNext(sourceKey, absXml.searchToken);
        if (absXml != null && absXml.movie != null && absXml.movie.videoList != null && absXml.movie.videoList.size() > 0) {
            List<Movie.Video> exactData = new ArrayList<>();
            List<Movie.Video> highData = new ArrayList<>();
            for (Movie.Video video : absXml.movie.videoList) {
                if (isHighMatchSearchResult(video)) {
                    highMatchVods.add(video);
                    highData.add(video);
                    detailFallbackSearchResults.add(video);
                }
                if (matchSearchResult(video.name, searchTitle)) {
                    exactData.add(video);
                }
            }

            if (showHighMatchResults) {
                addSearchResults(highData);
            } else if (!exactData.isEmpty()) {
                addSearchResults(exactData);
            }
        }

        if (shouldShowHighMatchResults()) {
            showHighMatchResults = true;
            addSearchResults(new ArrayList<>(highMatchVods));
        }

        finishSearchIfDone();
    }

    private void putDetailFallbackCandidates(Bundle bundle, Movie.Video selectedVideo) {
        if (bundle == null || selectedVideo == null || TextUtils.isEmpty(selectedVideo.name)) {
            return;
        }
        String title = selectedVideo.name.trim();
        ArrayList<Movie.Video> candidates = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (Movie.Video video : detailFallbackSearchResults) {
            if (video == null || TextUtils.isEmpty(video.id)
                    || !TextUtils.equals(title, video.name == null ? "" : video.name.trim())) {
                continue;
            }
            String key = (video.sourceKey == null ? "" : video.sourceKey) + "|" + video.id;
            if (keys.add(key)) {
                candidates.add(video);
                if (candidates.size() >= 20) {
                    break;
                }
            }
        }
        if (!candidates.isEmpty()) {
            bundle.putSerializable(DetailActivity.EXTRA_DETAIL_FALLBACK_CANDIDATES, candidates);
        }
    }

    private void scheduleSearchAdvance(final String sourceKey, final String searchToken) {
        searchHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isSearchPending(sourceKey, searchToken) && releaseSearchSlot(sourceKey, searchToken)) {
                    startNextSearchTask(searchToken);
                }
            }
        }, SEARCH_NEXT_BATCH_SECONDS * 1000L);
    }

    private void startSearchPump(final String searchToken) {
        searchHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isCurrentSearchToken(searchToken) || allRunCount.get() <= 0) return;
                if (!waitingSearchTasks.isEmpty()) startNextSearchBatch(searchToken);
                if (isCurrentSearchToken(searchToken)) searchHandler.postDelayed(this, SEARCH_PUMP_SECONDS * 1000L);
            }
        }, SEARCH_PUMP_SECONDS * 1000L);
    }

    private void scheduleSearchTimeout(final String sourceKey, final String searchToken) {
        searchHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (markSearchFinished(sourceKey, searchToken)) {
                    sourceViewModel.cancelSearchSource(searchToken, sourceKey);
                    LOG.i("search outcome=timeout");
                    releaseSearchSlotAndStartNext(sourceKey, searchToken);
                    finishSearchIfDone();
                }
            }
        }, SEARCH_SITE_TIMEOUT_SECONDS * 1000L);
    }

    private boolean submitSearchTask(SearchTask task) {
        if (!isSearchPending(task.sourceKey, task.searchToken)) return false;
        FutureTask<Void> future = new FutureTask<>(task, null);
        if (!sourceViewModel.submitSearchTask(task.searchToken, task.sourceKey, future, task.blocking)) return false;
        scheduleSearchAdvance(task.sourceKey, task.searchToken);
        scheduleSearchTimeout(task.sourceKey, task.searchToken);
        return true;
    }

    private void startNextSearchBatch(String searchToken) {
        for (int i = 0; i < SEARCH_THREAD_COUNT; i++) {
            if (!startNextSearchTask(searchToken)) {
                return;
            }
        }
    }

    private boolean startNextSearchTask(String searchToken) {
        if (!isCurrentSearchToken(searchToken)) return false;
        SearchTask task = takeNextSearchTask(searchToken);
        if (task == null) {
            return false;
        }
        if (!submitSearchTask(task)) {
            startedSearchKeys.remove(task.sourceKey);
            if (isSearchPending(task.sourceKey, task.searchToken)) {
                synchronized (waitingSearchTasks) {
                    waitingSearchTasks.add(0, task);
                }
            }
            return false;
        }
        return true;
    }

    private SearchTask takeNextSearchTask(String searchToken) {
        synchronized (waitingSearchTasks) {
            while (!waitingSearchTasks.isEmpty()) {
                SearchTask task = waitingSearchTasks.remove(0);
                if (!TextUtils.equals(task.searchToken, searchToken)
                        || !isSearchPending(task.sourceKey, searchToken) || !startedSearchKeys.add(task.sourceKey)) {
                    continue;
                }
                return task;
            }
        }
        return null;
    }

    private void resumePausedSearches() {
        if (!searchPaused) {
            return;
        }
        searchPaused = false;
        List<String> sourceKeys = getPendingSearchKeys();
        if (sourceKeys.isEmpty()) {
            finishSearchIfDone();
            return;
        }
        currentSearchToken = sourceViewModel.beginSearches();
        waitingSearchTasks.clear();
        startedSearchKeys.clear();
        releasedSearchKeys.clear();
        for (String sourceKey : sourceKeys) {
            SourceBean bean = ApiConfig.get().getSource(sourceKey);
            waitingSearchTasks.add(new SearchTask(sourceKey, searchTitle, currentSearchToken, isBlockingSearchSource(bean)));
        }
        startNextSearchBatch(currentSearchToken);
        startSearchPump(currentSearchToken);
    }

    private void pauseSearchTasks() {
        searchPaused = allRunCount.get() > 0;
        cancel();
    }

    private boolean isCurrentSearchResult(AbsXml absXml) {
        return absXml != null && isCurrentSearchToken(absXml.searchToken);
    }

    private boolean isCurrentSearchToken(String searchToken) {
        return !TextUtils.isEmpty(searchToken) && searchToken.equals(currentSearchToken);
    }

    private boolean markSearchFinished(String sourceKey, String searchToken) {
        if (!isCurrentSearchToken(searchToken)) return false;
        synchronized (pendingSearchKeys) {
            if (TextUtils.isEmpty(sourceKey)) {
                return false;
            }
            if (!pendingSearchKeys.remove(sourceKey)) {
                return false;
            }
            allRunCount.set(pendingSearchKeys.size());
            return true;
        }
    }

    private boolean releaseSearchSlot(String sourceKey, String searchToken) {
        if (!isCurrentSearchToken(searchToken) || TextUtils.isEmpty(sourceKey)) return false;
        return releasedSearchKeys.add(sourceKey);
    }

    private void releaseSearchSlotAndStartNext(String sourceKey, String searchToken) {
        if (releaseSearchSlot(sourceKey, searchToken)) {
            startNextSearchTask(searchToken);
        }
    }

    private boolean isSearchPending(String sourceKey, String searchToken) {
        if (!isCurrentSearchToken(searchToken) || TextUtils.isEmpty(sourceKey)) return false;
        synchronized (pendingSearchKeys) {
            return pendingSearchKeys.contains(sourceKey);
        }
    }

    private boolean isBlockingSearchSource(SourceBean bean) {
        return bean == null || bean.getType() == 3;
    }


    private List<String> getPendingSearchKeys() {
        synchronized (pendingSearchKeys) {
            return new ArrayList<>(pendingSearchKeys);
        }
    }

    private void finishSearchIfDone() {
        if (allRunCount.get() > 0) return;
        searchPaused = false;
        if (searchAdapter.getData().size() <= 0) {
            showEmpty();
        }
        cancel();
    }

    private class SearchTask implements Runnable {
        private final String sourceKey;
        private final String title;
        private final String searchToken;
        private final boolean blocking;

        private SearchTask(String sourceKey, String title, String searchToken, boolean blocking) {
            this.sourceKey = sourceKey;
            this.title = title;
            this.searchToken = searchToken;
            this.blocking = blocking;
        }

        @Override
        public void run() {
            if (!isSearchPending(sourceKey, searchToken) || Thread.currentThread().isInterrupted()) return;
            try {
                sourceViewModel.getSearch(sourceKey, title, searchToken);
            } catch (Throwable error) {
                LOG.i("search outcome=worker_error");
                searchHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (markSearchFinished(sourceKey, searchToken)) {
                            sourceViewModel.cancelSearchSource(searchToken, sourceKey);
                            releaseSearchSlotAndStartNext(sourceKey, searchToken);
                            finishSearchIfDone();
                        }
                    }
                });
            }
        }
    }


    private void cancel() {
        String token = currentSearchToken;
        currentSearchToken = ""; // Invalidate delivery before cancelling workers and transports.
        searchHandler.removeCallbacksAndMessages(null);
        sourceViewModel.cancelSearches(token);
        waitingSearchTasks.clear();
    }

    @Override
    protected void onPause() {
        pauseSearchTasks();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        cancel();
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    @Override
    protected void showEmpty() {
        if (searchEmpty == null) {
            super.showEmpty();
            return;
        }
        super.showSuccess();
        mGridView.setVisibility(View.INVISIBLE);
        if (searchIdle != null) searchIdle.setVisibility(View.GONE);
        if (searchResultTitle != null) searchResultTitle.setVisibility(View.GONE);
        searchEmpty.setVisibility(View.VISIBLE);
    }

    @Override
    protected void showLoading() {
        if (searchEmpty != null) searchEmpty.setVisibility(View.GONE);
        if (searchIdle != null) searchIdle.setVisibility(View.GONE);
        if (searchResultTitle != null) searchResultTitle.setVisibility(View.GONE);
        super.showLoading();
    }

    @Override
    protected void showSuccess() {
        if (searchEmpty != null) searchEmpty.setVisibility(View.GONE);
        if (searchIdle != null) searchIdle.setVisibility(View.GONE);
        if (searchResultTitle != null) searchResultTitle.setVisibility(View.VISIBLE);
        super.showSuccess();
    }

    private void showSearchIdle() {
        if (searchEmpty != null) searchEmpty.setVisibility(View.GONE);
        if (searchIdle != null) searchIdle.setVisibility(View.VISIBLE);
        if (searchResultTitle != null) searchResultTitle.setVisibility(View.GONE);
        if (mGridView != null) mGridView.setVisibility(View.INVISIBLE);
    }

    private void showSystemKeyboard() {
        etSearch.requestFocus();
        etSearch.setSelection(etSearch.length());
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null || imm.getEnabledInputMethodList().isEmpty()) {
            Toast.makeText(this, "未检测到系统输入法，请使用车载键盘或手机扫码输入", Toast.LENGTH_LONG).show();
            return;
        }
        etSearch.post(() -> imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT));
        etSearch.postDelayed(() -> {
            if (isFinishing() || !etSearch.hasFocus()) return;
            View decor = getWindow().getDecorView();
            android.graphics.Rect visible = new android.graphics.Rect();
            decor.getWindowVisibleDisplayFrame(visible);
            int hiddenHeight = decor.getRootView().getHeight() - visible.bottom;
            if (hiddenHeight < 160) {
                imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
                etSearch.clearFocus();
                keyboard.requestFirstKeyFocus();
                Toast.makeText(this, "系统输入法不可见，已切回车载键盘", Toast.LENGTH_LONG).show();
            }
        }, 800L);
    }

}
