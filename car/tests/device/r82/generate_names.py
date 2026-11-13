"""Generate reflection names from the exact R8 mapping belonging to the APK under test."""
import json
import re
from collections import defaultdict

def generate(mapping, output):
    classes = {}
    members = defaultdict(set)
    wanted = {'get', 'getSourceBeanList', 'getHomeSourceBean', 'getCSP', 'getKey', 'homeContent', 'homeVideoContent', 'isPlaying', 'getCurrentPosition', 'enterFullPreview', 'playFragment', 'mVodInfo', 'mVideoView', 'fullWindows', 'setSourceLevelMatchEnabled', 'getSourceLevelStatus', 'getMediaPlayer', 'getPcmGainQueueCount'}
    owner = ''
    for line in mapping.read_text(encoding='utf-8').splitlines():
        if not line or line.lstrip().startswith('#'): continue
        if not line.startswith(' ') and line.endswith(':') and ' -> ' in line:
            original, owner = line[:-1].split(' -> ')
            classes[original] = owner
        elif owner and ' -> ' in line:
            old, new = line.strip().split(' -> ')
            old = re.sub(r'^\d+:\d+:', '', old)
            name = old.split('(')[0].rsplit(' ', 1)[-1]
            if name in wanted:
                members[owner + '#' + name].add(new)
    lines = ['package com.tvbox.car.r82test;', 'import java.util.*;', 'final class Names {', 'static final Map<String,String> C=new HashMap<>();', 'static final Map<String,Set<String>> M=new HashMap<>();', 'static {']
    for old, new in classes.items():
        if old.startswith(('com.github.tvbox.osc.', 'com.github.catvod.', 'com.orhanobut.hawk.', 'xyz.doikki.videoplayer.')):
            # Only queried classes need a forward mapping; member keys already use the runtime class name.
            if old in ('com.github.tvbox.osc.api.ApiConfig', 'com.github.tvbox.osc.ui.dialog.AdvancedSettingsDialog', 'com.orhanobut.hawk.Hawk'):
                lines.append('C.put(%s,%s);' % (json.dumps(old), json.dumps(new)))
    for key, values in members.items():
        values.add(key.split('#', 1)[1])
        lines.append('M.put(%s,new HashSet<>(Arrays.asList(%s)));' % (json.dumps(key), ','.join(json.dumps(v) for v in sorted(values))))
    lines += ['}', 'static String klass(String n){return C.containsKey(n)?C.get(n):n;}', 'static Set<String> member(String c,String n){Set<String> s=M.get(c+"#"+n);return s==null?Collections.singleton(n):s;}', '}']
    output.write_text('\n'.join(lines)+'\n', encoding='utf-8')
