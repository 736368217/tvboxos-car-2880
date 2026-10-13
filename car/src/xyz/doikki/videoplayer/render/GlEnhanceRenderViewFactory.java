package xyz.doikki.videoplayer.render;

import android.content.Context;

/**
 * {@link GlEnhanceRenderView} 的工厂，用法参考 {@link TextureRenderViewFactory}。
 */
public class GlEnhanceRenderViewFactory extends RenderViewFactory {

    public static GlEnhanceRenderViewFactory create() {
        return new GlEnhanceRenderViewFactory();
    }

    @Override
    public IRenderView createRenderView(Context context) {
        return new GlEnhanceRenderView(context);
    }
}
