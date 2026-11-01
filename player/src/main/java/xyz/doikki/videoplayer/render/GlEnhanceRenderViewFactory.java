package xyz.doikki.videoplayer.render;

import android.content.Context;

/** Immutable initial configuration; the active view also accepts runtime changes. */
public class GlEnhanceRenderViewFactory extends RenderViewFactory {
    private final VideoEnhanceConfig configuration;

    private GlEnhanceRenderViewFactory(int mode, boolean superResolution, boolean interpolation) {
        configuration = new VideoEnhanceConfig(mode, superResolution, interpolation);
    }

    private GlEnhanceRenderViewFactory(int mode, int srTier, int memcTier, boolean tiers) {
        configuration = new VideoEnhanceConfig(mode, srTier, memcTier);
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

    /** 档位版工厂：srTier / memcTier 为 0..3。 */
    public static GlEnhanceRenderViewFactory createTiers(int mode, int srTier, int memcTier) {
        return new GlEnhanceRenderViewFactory(mode, srTier, memcTier, true);
    }

    public VideoEnhanceConfig getConfiguration() {
        return configuration;
    }

    @Override
    public IRenderView createRenderView(Context context) {
        return new GlEnhanceRenderView(context, configuration.preset.id,
                configuration.srTier, configuration.memcTier);
    }
}
