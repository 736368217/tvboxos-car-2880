package xyz.doikki.videoplayer.render;

import android.content.Context;

/** Creates a colour renderer with its preset set before the GL surface is created. */
public class GlEnhanceRenderViewFactory extends RenderViewFactory {
    private final int mode;

    private GlEnhanceRenderViewFactory(int mode) {
        this.mode = VideoEnhancePreset.forMode(mode).id;
    }

    public static GlEnhanceRenderViewFactory create() {
        return create(VideoEnhancePreset.VIVID_COLOR);
    }

    public static GlEnhanceRenderViewFactory create(int mode) {
        return new GlEnhanceRenderViewFactory(mode);
    }

    @Override
    public IRenderView createRenderView(Context context) {
        return new GlEnhanceRenderView(context, mode);
    }
}
