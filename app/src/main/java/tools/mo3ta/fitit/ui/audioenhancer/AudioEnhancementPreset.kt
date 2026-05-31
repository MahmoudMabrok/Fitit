package tools.mo3ta.fitit.ui.audioenhancer

/**
 * Tunable presets for the Audio Enhancer pipeline. Each level bundles the
 * parameters for the enhancement stages:
 *   1. high-pass filter (removes low-frequency rumble / handling noise),
 *   2. spectral-gating noise reduction (removes hiss / hum),
 *   3. low-pass filter (tames harsh high-frequency hiss; 0 = disabled),
 *   4. presence EQ (gentle peaking boost for speech clarity),
 *   5. single-band compression (brings quiet passages forward),
 *   6. loudness normalization with a true-peak ceiling (consistent volume).
 *
 * [targetDb] is retained for the legacy peak-normalize path; the pipeline now
 * uses [targetRmsDb] / [ceilingDb] for loudness normalization.
 *
 * Pure data so it can be unit-tested on the JVM without an Android device.
 */
enum class AudioEnhancementLevel(
    val highPassHz: Float,
    val lowPassHz: Float,
    val noiseStrength: Float,
    val noiseFloorDb: Float,
    val targetDb: Float,
    val presenceHz: Float,
    val presenceGainDb: Float,
    val presenceQ: Float,
    val compThresholdDb: Float,
    val compRatio: Float,
    val compMakeupDb: Float,
    val targetRmsDb: Float,
    val ceilingDb: Float,
) {
    /** Gentle clean-up that preserves the most detail. */
    LIGHT(
        highPassHz = 60f, lowPassHz = 0f, noiseStrength = 0.8f, noiseFloorDb = -14f, targetDb = -1.5f,
        presenceHz = 3000f, presenceGainDb = 2f, presenceQ = 0.9f,
        compThresholdDb = -18f, compRatio = 2f, compMakeupDb = 1f,
        targetRmsDb = -18f, ceilingDb = -1f,
    ),

    /** Balanced voice clean-up — the default. */
    STANDARD(
        highPassHz = 90f, lowPassHz = 0f, noiseStrength = 1.3f, noiseFloorDb = -18f, targetDb = -1f,
        presenceHz = 3500f, presenceGainDb = 3.5f, presenceQ = 0.9f,
        compThresholdDb = -20f, compRatio = 2.5f, compMakeupDb = 2f,
        targetRmsDb = -16f, ceilingDb = -1f,
    ),

    /** Aggressive noise removal for very noisy recordings. */
    STRONG(
        highPassHz = 110f, lowPassHz = 9000f, noiseStrength = 1.8f, noiseFloorDb = -24f, targetDb = -1f,
        presenceHz = 4000f, presenceGainDb = 5f, presenceQ = 1f,
        compThresholdDb = -22f, compRatio = 3f, compMakeupDb = 3f,
        targetRmsDb = -14f, ceilingDb = -1f,
    );
}

/**
 * Run the full enhancement pipeline on already-decoded audio.
 *
 * @param channels one float buffer per channel, samples in [-1, 1]. Mutated and
 *        also returned for convenience.
 * @param sampleRate sample rate in Hz.
 * @param onStageProgress optional callback in [0, 1] reporting progress across
 *        the per-channel stages.
 */
fun enhanceChannels(
    channels: Array<FloatArray>,
    sampleRate: Int,
    level: AudioEnhancementLevel,
    onStageProgress: ((Float) -> Unit)? = null,
): Array<FloatArray> {
    val total = channels.size.coerceAtLeast(1)
    for ((index, ch) in channels.withIndex()) {
        var s = ch
        s = AudioDsp.highPass(s, sampleRate, level.highPassHz)
        s = AudioDsp.reduceNoise(s, strength = level.noiseStrength, floorDb = level.noiseFloorDb)
        if (level.lowPassHz > 0f) {
            s = AudioDsp.lowPass(s, sampleRate, level.lowPassHz)
        }
        // Presence lift for speech clarity, then gentle compression.
        s = AudioDsp.peaking(s, sampleRate, level.presenceHz, level.presenceGainDb, level.presenceQ)
        s = AudioDsp.compress(
            s, sampleRate,
            thresholdDb = level.compThresholdDb,
            ratio = level.compRatio,
            makeupDb = level.compMakeupDb,
        )
        channels[index] = s
        onStageProgress?.invoke((index + 1).toFloat() / total)
    }
    // Loudness-normalize across all channels with a true-peak ceiling.
    AudioDsp.loudnessNormalize(channels, level.targetRmsDb, level.ceilingDb)
    return channels
}
