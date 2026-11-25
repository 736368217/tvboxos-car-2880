// Shared JS helpers for spider modules.
// - `req` / `http` are the classic catvod-style helpers (`req` is synchronous).
// - `net` mirrors the newer TVBox spider API (`net.get/post/json/text`).
//   It is intentionally synchronous: the QuickJS bridge only drains promise
//   jobs while a Java->JS call is on the stack, so an async `net` would hang
//   `await net.json(...)`. Returned values work with both `await` and direct use.

function http(url, options = {}) {
    if (options?.async === false) return _http(url, options)
    return new Promise(resolve => _http(url, Object.assign({
        complete: res => resolve(res)
    }, options))).catch(err => {
        console.error(err.name, err.message, err.stack)
        return {
            ok: false,
            status: 500,
            url
        }
    })
};

function req(url, options) {
    return _http(url, Object.assign({
        async: false
    }, options || {}));
}

function netOptions(method, options, body) {
    const opts = Object.assign({}, options || {});
    opts.method = method;
    if (body !== undefined && body !== null) {
        opts.body = typeof body === 'string' ? body : JSON.stringify(body);
    }
    return opts;
}

function looksLikeOptions(value) {
    if (!value || typeof value !== 'object' || Array.isArray(value)) return false;
    return ('headers' in value) || ('timeout' in value) || ('charset' in value) || ('method' in value) || ('body' in value);
}

const net = {
    get(url, options) {
        return req(url, netOptions('get', options));
    },
    post(url, body, options) {
        // Support both net.post(url, options) and net.post(url, body, options).
        if (options === undefined && looksLikeOptions(body)) {
            return req(url, netOptions('post', body));
        }
        return req(url, netOptions('post', options, body));
    },
    json(url, options) {
        const res = req(url, options);
        const parsed = JSON.parse(res.content);
        return parsed;
    },
    text(url, options) {
        return req(url, options).content;
    },
    html(url, options) {
        return req(url, options).content;
    }
};

globalThis.req = req;
globalThis.net = net;
