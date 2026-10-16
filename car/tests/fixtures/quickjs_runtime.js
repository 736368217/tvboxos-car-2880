// Android QuickJS only. All HTTP traffic stays on the loopback server supplied by init({base}).
let base = '';
let lateResolve = null;
let lateTimers = 0;
const wait = (value, ms = 20) => new Promise(resolve => setTimeout(() => resolve(value), ms));
const list = marker => JSON.stringify({list: [{vod_id: marker, vod_name: marker}]});

function request(path, timeout = 1500) {
    return new Promise((resolve, reject) => {
        if (!/^http:\/\/127\.0\.0\.1:\d+$/.test(base)) {
            reject(new Error('FIXTURE_REQUIRES_LOOPBACK_BASE'));
            return;
        }
        req(base + path, {
            async: true,
            timeout,
            complete: response => {
                if (response.error) reject(new Error('FIXTURE_REQ: ' + response.error));
                else resolve(response.content);
            }
        });
    });
}

function run(mode) {
    switch (mode) {
        case 'sync': return 'SYNC_OK';
        case 'object': return {value: 'OBJECT_OK'};
        case 'promise': return Promise.resolve('PROMISE_OK');
        case 'timer': return wait('first').then(() => wait('TIMER_OK'));
        case 'req': return request('/ok').then(value => wait('REQ_' + value));
        case 'req-timeout': return request('/slow', 80);
        case 'reject': return Promise.reject(new Error('FIXTURE_REJECT'));
        case 'throw': throw new Error('FIXTURE_THROW');
        case 'timeout':
            return new Promise(resolve => {
                lateResolve = resolve;
                setTimeout(() => { lateTimers++; resolve('TOO_LATE'); }, 31000);
            });
        case 'recover':
            // Resolve the timed-out Promise after Java has disconnected its handler bridge.
            // A missing native callback registry entry must never be called here.
            if (lateResolve) { lateResolve('LATE_RESOLUTION'); lateResolve = null; }
            return wait('RECOVER_OK');
        case 'pending-network': return request('/slow', 5000).then(value => 'REQ_' + value);
        case 'pending': return Promise.all([wait('timer', 1000), request('/slow', 5000)]).then(() => 'PENDING_OK');
        case 'events': return JSON.stringify({lateTimers});
        default: throw new Error('FIXTURE_UNKNOWN_MODE: ' + mode);
    }
}

export default {
    init(ext) { base = typeof ext === 'string' ? JSON.parse(ext).base : ext.base; },
    home() { return JSON.stringify({class: [{type_id: 'local', type_name: 'QuickJS local runtime'}]}); },
    homeVod() { return list('SYNC_OK'); },
    category(tid, pg, filter, extend) { return wait(list('CATEGORY_' + (extend.marker || 'OK'))); },
    detail(id) { return request('/ok').then(value => list('DETAIL_' + value)); },
    search(keyword) { return run(keyword); },
    play(flag, id, vip) { return wait(JSON.stringify({parse: 0, url: id, vipCount: vip.length})); },
    action(mode) { return run(mode); },
    isVideo(url) { return url.endsWith('.mp4'); },
    sniffer() { return false; }
};
