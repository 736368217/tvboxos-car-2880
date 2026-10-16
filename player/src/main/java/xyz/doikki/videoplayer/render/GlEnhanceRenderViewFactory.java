package xyz.doikki.videoplayer.render;

import android.content.Context;

/** Immutable initial configuration; the active view also accepts runtime changes. */
public class GlEnhanceRenderViewFactory extends RenderViewFactory {
    private final VideoEnhanceConfig configuration;

    private GlEnhanceRenderViewFactory(int mode, boolean superResolution, boolean interpolation) {
        configuration = new VideoEnhanceConfig(mode, superResolution, interpolation);
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

    public VideoEnhanceConfig getConfiguration() {
        return configuration;
    }

    @Override
    public IRenderView createRenderView(Context context) {
        return new GlEnhanceRenderView(context, configuration.preset.id,
                configuration.superResolution, configuration.interpolation);
    }
}
