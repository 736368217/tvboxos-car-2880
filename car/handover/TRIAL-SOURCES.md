# Trial Subscription Sources

This file records sanitized identities and final-SHA runtime outcomes for the two user-requested trial sources. Raw response bodies, cookies, private headers, downloaded spiders and private preferences remain outside the repository.

## Source Identities

| Name | URL | Host probe | Parsed site count | Body SHA-256 |
|---|---|---|---:|---|
| clun box | `https://clun.top/box.json` | HTTP 200, `application/json`, 31,068 bytes | 100 | `EB9E8BE391F49848619ADA3268A9F3BF06150359D6D4B91F8303F498F2DEB68B` |
| xiaosa api | `https://raw.githubusercontent.com/736368217/tvbox/refs/heads/123/xiaosa/api.json` | HTTP 200, `text/plain; charset=utf-8`, 27,962 bytes; parsed as JSON | 107 | `C8EA6253825C5738AD2C5D0B18D26CF2FD6FDFEDB1A33E78A0BD153F9496DDFA` |

## Final-SHA Runtime

Final APK SHA: `2CDD1147D70531E2C7DB001C71F19DD9B1947BE6795A30D4E64856A349D6C7F8`.

- xiaosa was entered through the visible source dialog and loaded real Home data (`豆瓣 • 首页`). Positive search `嫁金钗` returned five results. The `咕噜 • APP` detail loaded metadata, `咕噜4K`, episodes 1/2, preview playback, fullscreen controls and active GL rendering.
- clun was entered through the same visible dialog. The final APK returned `HTTP 404` twice while pulling the configuration. The host probe was HTTP 200, so this is a host/runtime source discrepancy, not proof of app failure and not a playback pass.

## Interpretation

- Configuration HTTP success and runtime content success are separate acceptance stages.
- Empty search, empty Live/Detail, remote HTTP errors and player failures are recorded separately.
- Do not rewrite source bodies or add JARs to make a result pass.
