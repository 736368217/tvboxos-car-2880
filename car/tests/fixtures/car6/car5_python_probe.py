# -*- coding: utf-8 -*-
"""Local acceptance fixture: real Python imports -> cards -> detail -> synthetic playback."""
import sys
import json
import requests
import ujson
from lxml import etree
from pyquery import PyQuery
from jsonpath import jsonpath
from cachetools import TTLCache
from bs4 import BeautifulSoup
from Crypto.Hash import SHA256
from base.spider import Spider as BaseSpider

BASE = 'http://127.0.0.1:9978/file/TVBoxCar5/'


class Spider(BaseSpider):
    def getName(self):
        return 'Python兼容验收'

    def init(self, extend=''):
        assert etree.HTML('<p>lxml</p>').xpath('string(//p)') == 'lxml'
        assert PyQuery('<p>pyquery</p>')('p').text() == 'pyquery'
        assert BeautifulSoup('<p>bs4</p>', 'html.parser').p.text == 'bs4'
        assert ujson.loads('{"ok":true}')['ok']
        assert jsonpath({'values': [5]}, '$.values[0]') == [5]
        cache = TTLCache(maxsize=2, ttl=60)
        cache['probe'] = SHA256.new(b'car5-python-runtime').hexdigest()
        assert len(cache['probe']) == 64
        response = requests.get(BASE + 'check.json', timeout=8)
        response.raise_for_status()
        assert response.json()['fixture'] == 'car5'
        self.version = sys.version.split()[0]
        print('CAR5_PY_RUNTIME version=' + self.version + ' dependencies=8 network=OK')

    def _video(self, key='python-video'):
        return {'vod_id': key, 'vod_name': 'Python运行成功 ' + getattr(self, 'version', ''),
                'vod_pic': BASE + 'poster.png', 'vod_remarks': 'PYTHON-EXECUTED-8-DEPS'}

    def homeContent(self, filter):
        return {'class': [{'type_id': 'python', 'type_name': 'Python验收分类'}], 'filters': {}}

    def homeVideoContent(self):
        return {'list': [self._video()]}

    def categoryContent(self, tid, pg, filter, extend):
        return {'page': int(pg), 'pagecount': 1, 'limit': 4, 'total': 4, 'list': [dict(self._video(k), vod_name=n) for k,n in [('lowsat','低饱和静态'),('motion','24fps移动图案'),('1080','1080p测试'),('4k','4K旁路测试')]]}

    def detailContent(self, ids):
        video = self._video(ids[0])
        video.update({'vod_content': '真实运行 Python、lxml、ujson、pyquery、requests、jsonpath、cachetools、bs4、Crypto；播放合成片。',
                      'vod_play_from': 'Python本地测试',
                      'vod_play_url': '测试播放$' + BASE + (ids[0] if ids[0] in ['lowsat','motion','1080','4k'] else 'lowsat') + '.mp4'})
        return {'list': [video]}

    def searchContent(self, key, quick, pg='1'):
        print('CAR5_PY_SEARCH key=' + key)
        return {'list': [self._video('python-search')]}

    def playerContent(self, flag, id, vipFlags):
        print('CAR5_PY_PLAY url=' + id)
        return {'parse': 0, 'playUrl': '', 'url': id, 'header': {}}

    def manualVideoCheck(self):
        return False

    def isVideoFormat(self, url):
        return url.endswith('.mp4')

    def localProxy(self, param):
        return [200, 'application/json', json.dumps({'fixture': 'car5-python'})]
