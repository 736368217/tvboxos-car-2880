package xyz.doikki.videoplayer.render;

/** One immutable settings snapshot, published between the UI and GL threads. */
public final class VideoEnhanceConfig {
    public final VideoEnhancePreset preset;
    public final boolean superResolution;
    public final boolean interpolation;

    public VideoEnhanceConfig(int mode, boolean superResolution, boolean interpolation) {
        this.preset = VideoEnhancePreset.forMode(mode);
        this.superResolution = superResolution;
        this.interpolation = interpolation;
    }

    public VideoEnhanceConfig forHdr(boolean hdr) {
        return hdr && preset.id != VideoEnhancePreset.ORIGINAL
                ? new VideoEnhanceConfig(VideoEnhancePreset.ORIGINAL, superResolution, interpolation) : this;
    }

    public VideoEnhanceConfig withMode(int mode) {
        return new VideoEnhanceConfig(mode, superResolution, interpolation);
    }

    public VideoEnhanceConfig withSuperResolution(boolean enabled) {
        return new VideoEnhanceConfig(preset.id, enabled, interpolation);
    }

    public VideoEnhanceConfig withInterpolation(boolean enabled) {
        return new VideoEnhanceConfig(preset.id, superResolution, enabled);
    }

    public boolean usesGl() {
        return preset.id != VideoEnhancePreset.ORIGINAL || superResolution || interpolation;
    }

    public boolean sameAs(VideoEnhanceConfig other) {
        return other != null && preset.id == other.preset.id
                && superResolution == other.superResolution && interpolation == other.interpolation;
    }

    @Override public String toString() {
        return "mode=" + preset.id + " sr=" + superResolution + " interp=" + interpolation;
    }
}
