package com.github.tvbox.osc.glide;

import android.content.Context;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.module.AppGlideModule;

import java.io.InputStream;

@GlideModule
public class OkHttpAppGlideModule extends AppGlideModule {
    @Override
    public void registerComponents(@NonNull Context context, @NonNull Glide glide, @NonNull Registry registry) {
        // 图片专用的混搭客户端：系统 DNS（车机 App 栈 DNS 会全挂）+ App 栈代理规则。
        registry.replace(GlideUrl.class, InputStream.class, new OkHttpUrlLoader.Factory(com.github.tvbox.osc.util.PlainHttp.systemDnsWithAppStack()));
    }

    @Override
    public boolean isManifestParsingEnabled() {
        return false;
    }
}
