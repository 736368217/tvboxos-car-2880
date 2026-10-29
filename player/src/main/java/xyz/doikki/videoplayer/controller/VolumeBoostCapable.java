package xyz.doikki.videoplayer.controller;

/**
 * 播放器的可选能力标记：能不能把应用内音量放大到 100% 以上。
 *
 * <p>放在 player 模块里是为了让 {@link ControlWrapper} 能判定「当前引擎支不支持」，
 * 而不必反向依赖 app 模块的具体实现类。</p>
 *
 * <p>为什么需要：只有对 PCM 采样做增益的引擎（本项目的 ExoPlayer）能保证
 * &gt;100% 真的变响；IJK / 系统播放器只能靠 LoudnessEnhancer，很多车机上并不生效。
 * 这类播放器下音量上限应压回 100%，而不是让用户拖到 200% 却听不出变化。</p>
 */
public interface VolumeBoostCapable {

    /** true = 支持 &gt;100% 应用内增益。 */
    boolean supportsVolumeBoost();
}