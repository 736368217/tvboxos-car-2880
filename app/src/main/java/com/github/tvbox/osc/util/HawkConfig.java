package com.github.tvbox.osc.util;

/**
 * @author pj567
 * @date :2020/12/23
 * @description:
 */
public class HawkConfig {
    public static final String API_URL = "api_url";
    public static final String EPG_URL = "epg_url";
    public static final String SHOW_PREVIEW = "show_preview";
    public static final String API_HISTORY = "api_history";
    public static final String API_LINE_LIST = "api_line_list";
    public static final String API_LINE_SOURCE = "api_line_source";
    public static final String LIVE_API_HISTORY = "live_api_history";
    public static final String EPG_HISTORY = "epg_history";
    public static final String HOME_API = "home_api";
    public static final String DEFAULT_PARSE = "parse_default";
    public static final String DEBUG_OPEN = "debug_open";
    public static final String PARSE_WEBVIEW = "parse_webview"; // true 系统 false xwalk
    public static final String IJK_CODEC = "ijk_codec";
    public static final String PLAY_TYPE = "play_type";//1 ijk 2 exo 10 MXPlayer
    public static final String LIVE_PLAY_TYPE = "live_play_type";//1 ijk 2 exo 10 MXPlayer
    public static final String PLAY_RENDER = "play_render"; //0 texture 2
    public static final String PLAY_SCALE = "play_scale"; //0 texture 2
    public static final String LIVE_PLAY_SCALE = "live_play_scale";
    public static final String PLAY_TIME_STEP = "play_time_step"; //0 texture 2
    public static final String DOH_URL = "doh_url";
    public static final int DEFAULT_HOME_REC = 1;
    public static final String HOME_REC = "home_rec"; // 0 豆瓣热播 1 数据源推荐 2 历史
    public static final String HISTORY_NUM = "history_num";
    public static final String HISTORY_MERGE = "history_merge";
    public static final String SEARCH_VIEW = "search_view"; // 0 列表 1 缩略图
    public static final String LIVE_CHANNEL = "last_live_channel_name";
    public static final String LIVE_CHANNEL_REVERSE = "live_channel_reverse";
    public static final String LIVE_CROSS_GROUP = "live_cross_group";
    public static final String LIVE_CONNECT_TIMEOUT = "live_connect_timeout";
    public static final String LIVE_SHOW_NET_SPEED = "live_show_net_speed";
    public static final String LIVE_SHOW_RESOLUTION = "live_show_resolution";
    public static final String LIVE_SHOW_TIME = "live_show_time";
    public static final String FAST_SEARCH_MODE = "fast_search_mode";
    public static final String SUBTITLE_TEXT_SIZE = "subtitle_text_size";
    public static final String SUBTITLE_TIME_DELAY = "subtitle_time_delay";
    public static final String SUBTITLE_EXO_SCALE = "subtitle_exo_scale";
    public static final String SUBTITLE_EXO_POSITION = "subtitle_exo_position";
    public static final String SOURCES_FOR_SEARCH = "checked_sources_for_search";
    public static final String HOME_REC_STYLE = "home_rec_style";
    public static final String NOW_DATE = "now_date"; //当前日期
    public static final String REMOTE_TVBOX = "remote_tvbox_host";
    public static final String IJK_CACHE_PLAY = "ijk_cache_play";
    public static final String PLAYER_IS_LIVE = "player_is_live";
    public static final String DOH_JSON = "doh_json";
    public static final String LIVE_GROUP_INDEX = "live_group_index";
    public static final String LIVE_GROUP_LIST = "live_group_list";
    public static final String LIVE_API_URL = "live_api_url";
    public static final String M3U8_PURIFY = "m3u8_purify";
    public static final String AUTO_SWITCH_LINE = "auto_switch_line";
    public static final String SCREEN_DISPLAY = "screen_display";
    public static final String LIVE_WEB_HEADER = "live_web_header";
    public static final String DEFAULT_LOAD_LIVE = "DEFAULT_LOAD_LIVE";
    public static final String SEARCH_HISTORY = "search_history";
    public static final String DANMU_OPEN = "danmu_open";
    public static final String DANMU_MAX_LINE = "danmu_max_line";
    public static final String DANMU_SPEED = "danmu_speed";
    public static final String DANMU_ALPHA = "danmu_alpha";
    public static final String DANMU_SIZE_SCALE = "danmu_size_scale";
    public static final String DANMU_RANDOM_COLOR = "danmu_random_color";
    public static final String DANMU_API = "danmu_api";
    public static final String IN_APP_BACK = "inapp_back"; // default true
    // 应用内返回组件（双模：悬浮球 / 边缘拉出）配置键
    public static final String BACK_MODE = "inapp_back_mode"; // 默认 edge（边缘拉出）
    public static final String BACK_MODE_EXPLICIT = "inapp_back_mode_explicit";
    public static final String BACK_POSITION_X = "inapp_back_x";
    public static final String BACK_POSITION_Y = "inapp_back_y";
    public static final String BACK_EDGE_POSITION_Y = "inapp_back_edge_y";
    public static final String BACK_SIDE = "inapp_back_side";
    public static final String BACK_MODE_FLOATING = "floating";
    public static final String BACK_MODE_EDGE = "edge";
    public static final String BACK_MODE_BOTH = "both";
    /** 第三种返回方式：贴边横滑返回（复刻 Swipe Navigation Gesture 的拉伸手柄手感）。 */
    public static final String BACK_MODE_SWIPE = "swipe";
    public static final String BACK_SWIPE_TRIGGER_DP = "inapp_back_swipe_trigger"; // 40-140 dp
    public static final String BACK_SIDE_LEFT = "left";
    public static final String BACK_SIDE_RIGHT = "right";
    public static final String BACK_SIDE_BOTH = "both";
    public static final String BACK_LARGE = "inapp_back_large";
    public static final String BACK_HAPTIC = "inapp_back_haptic"; // 默认 true
    public static final String BACK_HIDE_ON_ROOT = "inapp_back_hide_on_root"; // 默认 false
    public static final String BACK_EDGE_SKIN = "inapp_back_edge_skin";
    public static final String BACK_EDGE_SKIN_EXPLICIT = "inapp_back_skin_explicit";
    public static final String BACK_SKIN_TEARDROP = "teardrop";
    public static final String BACK_SKIN_CAPSULE = "capsule";
    public static final String BACK_HIDE_SECONDS = "hide_seconds"; // 4/6/8，0 = 常驻
    // 返回杠外观（用户可配置）
    public static final String BACK_BAR_WIDTH_DP = "inapp_back_bar_width";   // 可视宽度 dp
    public static final String BACK_BAR_HEIGHT_DP = "inapp_back_bar_height"; // 可视高度 dp
    public static final String BACK_BAR_COLOR = "inapp_back_bar_color";      // ARGB
    public static final String BACK_BAR_ALPHA = "inapp_back_bar_alpha";      // 0-100
    public static final String HIFI_AUDIO = "hifi_audio"; // default 0
    public static final String VIDEO_ENHANCE = "video_enhance"; // 0 original, 1 vivid, 8..10 anime
    public static final String VIDEO_SR = "video_sr"; // default 1: SGSR1
    public static final String VIDEO_INTERP = "video_interp"; // default 1: temporal smoothing
    public static final String VOLUME_GAIN = "app_volume_gain"; // 0-200, default 100 (app-level gain, not system volume)
    public static final String BRIGHTNESS_GESTURE = "brightness_gesture"; // default false: brightness follows system
    // 首页中间入口（历史/直播/搜索/推送/收藏/设置）是否显示
    public static final String HOME_ENTRY_HISTORY = "home_entry_history";
    public static final String HOME_ENTRY_LIVE = "home_entry_live";
    public static final String HOME_ENTRY_SEARCH = "home_entry_search";
    public static final String HOME_ENTRY_PUSH = "home_entry_push";
    public static final String HOME_ENTRY_FAVORITE = "home_entry_favorite";
    public static final String HOME_ENTRY_SETTING = "home_entry_setting";
    public static boolean hotVodDelete;
}
