#coding=utf-8
#!/usr/bin/python
import os
import requests
from importlib.machinery import SourceFileLoader
from urllib import parse
import json
import sys
import crypto_protocol_dh
sys.dont_write_bytecode = True

from tvbox_http import install_fallback
install_fallback()


PLUGIN_DOWNLOAD_TIMEOUT = 20
PLUGIN_MAX_REDIRECTS = 5


def createFile(file_path):
    if not os.path.exists(file_path):
        os.makedirs(file_path)


def redirectResponse(tUrl):
    current = tUrl
    for attempt in range(PLUGIN_MAX_REDIRECTS + 1):
        rsp = requests.get(current, allow_redirects=False, verify=False,
                           timeout=PLUGIN_DOWNLOAD_TIMEOUT)
        if rsp.status_code not in (301, 302, 303, 307, 308):
            return rsp
        location = rsp.headers.get("Location")
        rsp.close()
        if not location or attempt == PLUGIN_MAX_REDIRECTS:
            raise requests.TooManyRedirects("plugin redirect limit exceeded")
        next_url = parse.urljoin(current, location)
        if parse.urlsplit(next_url).scheme not in ("https", "http"):
            raise requests.RequestException("unsupported plugin redirect")
        current = next_url
    raise requests.TooManyRedirects("plugin redirect limit exceeded")


def downloadFile(name, url):
    temp_name = name + ".tmp"
    rsp = None
    try:
        rsp = redirectResponse(url)
        rsp.raise_for_status()
        if not 200 <= rsp.status_code < 300:
            raise requests.RequestException("invalid plugin status")
        content = rsp.content
        if not content:
            raise requests.RequestException("empty plugin response")
        compile(content, name, "exec")
        with open(temp_name, "wb") as stream:
            stream.write(content)
        os.replace(temp_name, name)
        print("plugin downloaded " + os.path.basename(name))
        return True
    except Exception as error:
        if os.path.exists(temp_name):
            os.remove(temp_name)
        raise RuntimeError("plugin download failed (%s)" % type(error).__name__) from None
    finally:
        if rsp is not None:
            rsp.close()


def downloadPlugin(basePath, url):
    createFile(basePath)
    parsed = parse.urlparse(url)
    file_name = os.path.basename(parsed.path or url)
    name = parse.unquote(file_name.rsplit(".", 1)[0] if "." in file_name else file_name)
    if not name or os.path.basename(name) != name or "\\" in name:
        raise RuntimeError("invalid plugin name")
    if url.startswith("file://"):
        pyName = parse.unquote(parsed.path)
    else:
        pyName = os.path.join(basePath, name + ".py")
        downloadFile(pyName, url)
    if not os.path.isfile(pyName):
        raise RuntimeError("plugin file missing")
    sPath = gParam['SpiderPath']
    sPath[name] = pyName
    sParam = gParam['SpiderParam']
    paramList = parse.parse_qs(parsed.query).get('extend')
    if paramList is None:
        paramList = ['']
    sParam[name] = paramList[0]
    return pyName

def registerPluginAlias(alias,fileName):
    if alias == None or alias == '':
        return
    name = fileName.split('/')[-1].split('.')[0]
    sPath = gParam['SpiderPath']
    sPath[alias] = fileName
    sParam = gParam['SpiderParam']
    sParam[alias] = sParam[name] if name in sParam.keys() else ''

def loadFromDisk(fileName):
    name = fileName.split('/')[-1].split('.')[0]
    spList = gParam['SpiderList']
    sp = SourceFileLoader(name, fileName).load_module().Spider()
    spList[name] = sp
    return spList[name]

def str2json(content):
    return json.loads(content)

def getDependenceList(ru):
    get_dependence = getattr(ru, 'getDependence', None)
    if callable(get_dependence):
        result = get_dependence()
        return result if result is not None else []
    return []

def setExtendInfo(ru, extend):
    setter = getattr(ru, 'setExtendInfo', None)
    if callable(setter):
        setter(extend)
    else:
        setattr(ru, 'extend', extend)

gParam = {
    "SpiderList":{},
    "SpiderPath":{},
    "SpiderParam":{}
}

def getDependence(ru):
    return getDependenceList(ru)

def getName(ru):
    result = ru.getName()
    return result

def init(ru,extend):
    spoList = []
    spList = gParam['SpiderList']
    sPath = gParam['SpiderPath']
    sParam = gParam['SpiderParam']
    for key in getDependenceList(ru):
        sp = None
        if key in spList.keys():
            sp = spList[key]
        elif key in sPath.keys():
            sp = loadFromDisk(sPath[key])
        if sp != None:
            setExtendInfo(sp, sParam[key])
            spoList.append(sp)
    setExtendInfo(ru, extend)
    ru.init(spoList if len(spoList) > 0 else extend)

def homeContent(ru,filter):
    result = ru.homeContent(filter)
    formatJo = json.dumps(result,ensure_ascii=False)
    return formatJo

def homeVideoContent(ru):
    result = ru.homeVideoContent()
    formatJo = json.dumps(result,ensure_ascii=False)
    return formatJo

def categoryContent(ru,tid, pg, filter, extend):
    result = ru.categoryContent(tid, pg, filter, str2json(extend))
    formatJo = json.dumps(result,ensure_ascii=False)
    return formatJo

def detailContent(ru,array):
    result = ru.detailContent(str2json(array))
    formatJo = json.dumps(result,ensure_ascii=False)
    return formatJo

def playerContent(ru,flag,id,vipFlags):
    result = ru.playerContent(flag,id,str2json(vipFlags))
    formatJo = json.dumps(result,ensure_ascii=False)
    return formatJo

def liveContent(ru,url):
    result = ru.liveContent(url)
    return result

def searchContent(ru,key,quick):
    result = ru.searchContent(key,quick)
    formatJo = json.dumps(result,ensure_ascii=False)
    return formatJo

def localProxy(ru,param):
    result = ru.localProxy(str2json(param))
    return result

def destroy(ru):
    ru.destroy()

def run():
    pass

if __name__ == '__main__':
    run()
