// Deterministic UI/gesture fixture; never shipped as a subscription in the app.
const base = 'http://127.0.0.1:9978/file/TVBoxCar5/';
const item = (i) => ({vod_id: 'car7-' + i, vod_name: '车机界面验收 ' + (i + 1) + ' · 两行完整影片标题与长文字显示测试', vod_pic: base + 'poster.png', vod_remarks: '本地合成素材'});
const cards = () => Array.from({length:32}, (_, i) => item(i));
export default {
    init: function () {},
    home: function () { return JSON.stringify({class:Array.from({length:15}, (_, i) => ({type_id:String(i+1),type_name:'验收分类'+(i+1)})),filters:{}}); },
    homeVod: function () { return JSON.stringify({list:cards()}); },
    category: function () { return JSON.stringify({page:1,pagecount:1,limit:32,total:32,list:cards()}); },
    detail: function (id) {
        const v=item(0); v.vod_id=id; v.type_name='本地验收'; v.vod_year='2026'; v.vod_area='合成素材'; v.vod_actor='UI 与播放验收';
        v.vod_content='用于验证车机长标题、详情页、播放控制和应用内返回；画面来自本地测试素材。';
        v.vod_play_from='本地验收'; v.vod_play_url='200秒动态图$'+base+'motion.mp4#重进播放$'+base+'motion.mp4';
        return JSON.stringify({list:[v]});
    },
    search: function () { return JSON.stringify({list:cards().slice(0,5)}); },
    play: function (flag,id) { return JSON.stringify({parse:0,playUrl:'',url:id,header:{}}); },
    isVideo: function (url) { return url.indexOf('.mp4')>=0; },
    sniffer: function () { return false; }
};
