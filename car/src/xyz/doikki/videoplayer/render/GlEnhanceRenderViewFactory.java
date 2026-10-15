package xyz.doikki.videoplayer.render;

import android.content.Context;

/** Snapshot all three independent settings before the render Surface is created. */
public class GlEnhanceRenderViewFactory extends RenderViewFactory {
    private final int mode;
    private final boolean superResolution;
    private final boolean interpolation;

    private GlEnhanceRenderViewFactory(int mode, boolean superResolution, boolean interpolation) {
        this.mode = VideoEnhancePreset.forMode(mode).id;
        this.superResolution = superResolution;
        this.interpolation = interpolation;
    }

    public static GlEnhanceRenderViewFactory create() {
        return create(VideoEnhancePreset.VIVID_COLOR);
    }

    public static GlEnhanceRenderViewFactory create(int mode) {
        return create(mode, false, false);
    }

    public static GlEnhanceRenderViewFactory create(int mode, boolean superResolution, boolean interpolation) {
        return new GlEnhanceRenderViewFactory(mode, superResolution, interpolation);
    }

    @Override
    public IRenderView createRenderView(Context context) {
        return new GlEnhanceRenderView(context, mode, superResolution, interpolation);
    }
}
