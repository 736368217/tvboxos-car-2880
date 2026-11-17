package xyz.doikki.videoplayer.render;

/** One immutable settings snapshot, published between the UI and GL threads. */
public final class VideoEnhanceConfig {
    public final VideoEnhancePreset preset;
    /** 兼容旧调用点：等价于 srTier > 0。 */
    public final boolean superResolution;
    /** 兼容旧调用点：等价于 memcTier > 0。 */
    public final boolean interpolation;
    public final int srTier;
    public final int memcTier;

    public VideoEnhanceConfig(int mode, boolean superResolution, boolean interpolation) {
        this(mode, VideoEnhanceTiers.fromLegacy(superResolution), VideoEnhanceTiers.fromLegacy(interpolation));
    }

    /** 档位构造：srTier/memcTier 会先夹到 0..3。 */
    public VideoEnhanceConfig(int mode, int srTier, int memcTier) {
        this.preset = VideoEnhancePreset.forMode(mode);
        this.srTier = VideoEnhanceTiers.clampSr(srTier);
        this.memcTier = VideoEnhanceTiers.clampMemc(memcTier);
        this.superResolution = this.srTier > VideoEnhanceTiers.SR_OFF;
        this.interpolation = this.memcTier > VideoEnhanceTiers.MEMC_OFF;
    }

    public VideoEnhanceConfig forHdr(boolean hdr) {
        return hdr && preset.id != VideoEnhancePreset.ORIGINAL
                ? new VideoEnhanceConfig(VideoEnhancePreset.ORIGINAL, srTier, memcTier) : this;
    }

    public VideoEnhanceConfig withMode(int mode) {
        return new VideoEnhanceConfig(mode, srTier, memcTier);
    }

    public VideoEnhanceConfig withSuperResolution(boolean enabled) {
        return new VideoEnhanceConfig(preset.id, VideoEnhanceTiers.fromLegacy(enabled), memcTier);
    }

    public VideoEnhanceConfig withInterpolation(boolean enabled) {
        return new VideoEnhanceConfig(preset.id, srTier, VideoEnhanceTiers.fromLegacy(enabled));
    }

    public VideoEnhanceConfig withSrTier(int tier) {
        return new VideoEnhanceConfig(preset.id, tier, memcTier);
    }

    public VideoEnhanceConfig withMemcTier(int tier) {
        return new VideoEnhanceConfig(preset.id, srTier, tier);
    }

    public boolean usesGl() {
        return preset.id != VideoEnhancePreset.ORIGINAL || superResolution || interpolation;
    }

    public boolean sameAs(VideoEnhanceConfig other) {
        return other != null && preset.id == other.preset.id
                && srTier == other.srTier && memcTier == other.memcTier;
    }

    @Override public String toString() {
        return "mode=" + preset.id + " srTier=" + srTier + " memcTier=" + memcTier;
    }
}
