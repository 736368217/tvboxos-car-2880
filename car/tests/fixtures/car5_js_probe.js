// Self-contained QuickJS acceptance fixture. No remote template or network dependency.
const base = 'http://127.0.0.1:9978/file/TVBoxCar5/';
const video = () => ({vod_id: 'js-video', vod_name: 'QuickJS运行成功', vod_pic: base + 'poster.png', vod_remarks: 'QUICKJS-EXECUTED'});
export default {
    init: function () {},
    home: function () { return JSON.stringify({class: [{type_id: 'js', type_name: 'JS验收分类'}], filters: {}}); },
    homeVod: function () { return JSON.stringify({list: [video()]}); },
    category: function () { return JSON.stringify({page: 1, pagecount: 1, limit: 1, total: 1, list: [video()]}); },
    detail: function () {
        const item = video();
        item.vod_content = 'QuickJS executes this module and returns a synthetic video.';
        item.vod_play_from = 'JS本地测试';
        item.vod_play_url = '低饱和测试$' + base + 'lowsat.mp4';
        return JSON.stringify({list: [item]});
    },
    search: function () { return JSON.stringify({list: [video()]}); },
    play: function (flag, id) { return JSON.stringify({parse: 0, playUrl: '', url: id, header: {}}); },
    isVideo: function (url) { return url.endsWith('.mp4'); },
    sniffer: function () { return false; }
};
