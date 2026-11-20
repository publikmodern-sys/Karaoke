package com.example.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

enum class ReverbPreset(val id: String, val label: String, val decay: Float, val damping: Float) {
    ROOM("ROOM", "Ruang Intim", 0.56f, 0.45f),
    STUDIO("STUDIO", "Studio Plate", 0.72f, 0.30f),
    HALL("HALL", "Konser Hall", 0.84f, 0.22f);

    companion object {
        fun fromId(id: String): ReverbPreset = entries.firstOrNull { it.id == id } ?: STUDIO
    }
}

data class MixSettings(
    val vocalGainDb: Float = 2.0f,
    val musicGainDb: Float = -2.5f,
    val reverbWet: Float = 0.22f,
    val reverbPreset: ReverbPreset = ReverbPreset.STUDIO,
    val clarityBoost: Float = 0.60f,
    val warmthGain: Float = 1.5f,
    val noiseGateThresholdDb: Float = -46f,
    val compressorAmount: Float = 0.55f,
    val syncOffsetMs: Int = -60,
    val soloVocal: Boolean = false
)

enum class StudioMixPreset(val title: String, val subtitle: String, val apply: (MixSettings) -> MixSettings) {
    STUDIO_CLEAR(
        title = "Studio Jernih",
        subtitle = "Vokal tajam & seimbang",
        apply = {
            it.copy(
                vocalGainDb = 2.5f,
                musicGainDb = -2.5f,
                reverbWet = 0.20f,
                reverbPreset = ReverbPreset.STUDIO,
                clarityBoost = 0.65f,
                warmthGain = 1.5f,
                noiseGateThresholdDb = -46f,
                compressorAmount = 0.60f
            )
        }
    ),
    CONCERT_HALL(
        title = "Konser Hall",
        subtitle = "Gema megah & bertenaga",
        apply = {
            it.copy(
                vocalGainDb = 3.0f,
                musicGainDb = -2.0f,
                reverbWet = 0.38f,
                reverbPreset = ReverbPreset.HALL,
                clarityBoost = 0.55f,
                warmthGain = 2.2f,
                noiseGateThresholdDb = -48f,
                compressorAmount = 0.72f
            )
        }
    ),
    ACOUSTIC_INTIMATE(
        title = "Akustik Intim",
        subtitle = "Hangat, dekat & jernih",
        apply = {
            it.copy(
                vocalGainDb = 2.0f,
                musicGainDb = -3.0f,
                reverbWet = 0.12f,
                reverbPreset = ReverbPreset.ROOM,
                clarityBoost = 0.72f,
                warmthGain = 2.5f,
                noiseGateThresholdDb = -44f,
                compressorAmount = 0.50f
            )
        }
    ),
    NATURAL_RAW(
        title = "Natural Murni",
        subtitle = "Minim efek, suara asli",
        apply = {
            it.copy(
                vocalGainDb = 0.5f,
                musicGainDb = -1.5f,
                reverbWet = 0.04f,
                reverbPreset = ReverbPreset.ROOM,
                clarityBoost = 0.15f,
                warmthGain = 0.0f,
                noiseGateThresholdDb = -56f,
                compressorAmount = 0.20f
            )
        }
    )
}

/**
 * Pure Kotlin 32-bit floating-point studio vocal DSP chain:
 * DC blocker -> 80 Hz High-Pass -> Smooth Downward Noise Gate ->
 * 3-Band Vocal EQ (Warmth 220Hz + Presence 3.2kHz + Air 9.5kHz) ->
 * Soft-Knee Vocal Compressor -> Stereo Comb+Allpass Studio Reverb ->
 * Master Soft Limiter.
 */
class VocalDspProcessor(private val sampleRate: Int = AudioConstants.SAMPLE_RATE) {

    // DC Blocker state
    private var dcPrevIn = 0f
    private var dcPrevOut = 0f

    // Biquad filters
    private val highPass80 = BiquadFilter()
    private val warmthEq = BiquadFilter()
    private val presenceEq = BiquadFilter()
    private val airShelfEq = BiquadFilter()

    // Noise gate envelope
    private var gateRmsSq = 0f
    private var gateGain = 1f
    private val rmsAlpha = 1f - exp((-1.0 / (0.010 * sampleRate)).toFloat())
    private val gateAttack = 1f - exp((-1.0 / (0.006 * sampleRate)).toFloat())
    private val gateRelease = 1f - exp((-1.0 / (0.120 * sampleRate)).toFloat())

    // Compressor envelope
    private var compEnv = 0f
    private val compAttack = 1f - exp((-1.0 / (0.005 * sampleRate)).toFloat())
    private val compRelease = 1f - exp((-1.0 / (0.085 * sampleRate)).toFloat())

    // Stereo Schroeder/Freeverb-style Reverb
    private val combsL = intArrayOf(1116, 1188, 1277, 1356).map { CombFilter(it) }
    private val combsR = intArrayOf(1139, 1211, 1300, 1379).map { CombFilter(it) }
    private val allpassL = intArrayOf(556, 441).map { AllpassFilter(it) }
    private val allpassR = intArrayOf(579, 464).map { AllpassFilter(it) }

    // Cached parameters
    private var cachedClarity = Float.NaN
    private var cachedWarmth = Float.NaN
    private var cachedPreset: ReverbPreset? = null

    init {
        highPass80.configureHighPass(sampleRate.toFloat(), 80f, 0.707f)
        updateFiltersIfNeeded(MixSettings())
    }

    fun resetState() {
        dcPrevIn = 0f
        dcPrevOut = 0f
        gateRmsSq = 0f
        gateGain = 1f
        compEnv = 0f
        highPass80.reset()
        warmthEq.reset()
        presenceEq.reset()
        airShelfEq.reset()
        combsL.forEach { it.reset() }
        combsR.forEach { it.reset() }
        allpassL.forEach { it.reset() }
        allpassR.forEach { it.reset() }
    }

    private fun updateFiltersIfNeeded(settings: MixSettings) {
        if (cachedWarmth != settings.warmthGain) {
            cachedWarmth = settings.warmthGain
            warmthEq.configurePeaking(sampleRate.toFloat(), 220f, 1.1f, settings.warmthGain)
        }
        if (cachedClarity != settings.clarityBoost) {
            cachedClarity = settings.clarityBoost
            val presenceDb = settings.clarityBoost * 6.5f
            val airDb = settings.clarityBoost * 4.0f
            presenceEq.configurePeaking(sampleRate.toFloat(), 3200f, 1.2f, presenceDb)
            airShelfEq.configureHighShelf(sampleRate.toFloat(), 9500f, 0.707f, airDb)
        }
        if (cachedPreset != settings.reverbPreset) {
            cachedPreset = settings.reverbPreset
            val p = settings.reverbPreset
            combsL.forEach { it.setParams(p.decay, p.damping) }
            combsR.forEach { it.setParams(p.decay, p.damping) }
        }
    }

    /**
     * Processes one stereo backing frame and one mono vocal sample, returning mixed stereo (L, R) in [-1f, 1f].
     */
    fun processFrame(
        backingL: Float,
        backingR: Float,
        vocalMono: Float,
        settings: MixSettings,
        outPair: FloatArray
    ) {
        updateFiltersIfNeeded(settings)

        // 1. DC Blocker
        val dcOut = vocalMono - dcPrevIn + 0.995f * dcPrevOut
        dcPrevIn = vocalMono
        dcPrevOut = dcOut

        // 2. 80Hz High-Pass Filter (removes mic rumble / breath thumps)
        var v = highPass80.process(dcOut)

        // 3. Smooth Downward Noise Gate
        gateRmsSq += rmsAlpha * (v * v - gateRmsSq)
        val rms = sqrt(gateRmsSq.coerceAtLeast(1e-12f))
        val rmsDb = 20f * (ln(rms) / LN10)
        val targetGateGain = if (rmsDb >= settings.noiseGateThresholdDb) {
            1f
        } else {
            val below = settings.noiseGateThresholdDb - rmsDb
            // Smooth 4:1 downward expansion capped at -28 dB floor so breaths aren't chopped awkwardly
            dbToLinear((-below * 0.75f).coerceAtLeast(-28f))
        }
        val gAlpha = if (targetGateGain > gateGain) gateAttack else gateRelease
        gateGain += gAlpha * (targetGateGain - gateGain)
        v *= gateGain

        // 4. 3-Band Vocal EQ
        v = warmthEq.process(v)
        v = presenceEq.process(v)
        v = airShelfEq.process(v)

        // 5. Soft-Knee Vocal Compressor
        val absV = abs(v)
        val cAlpha = if (absV > compEnv) compAttack else compRelease
        compEnv += cAlpha * (absV - compEnv)
        val envDb = 20f * (ln(compEnv.coerceAtLeast(1e-6f)) / LN10)
        val thresholdDb = -18f
        val ratio = 1f + settings.compressorAmount * 3.0f // up to 4:1
        val kneeDb = 6f
        val overDb = envDb - thresholdDb
        val compReductionDb = when {
            overDb <= -kneeDb / 2f -> 0f
            overDb >= kneeDb / 2f -> overDb * (1f - 1f / ratio)
            else -> {
                val x = overDb + kneeDb / 2f
                (1f - 1f / ratio) * (x * x) / (2f * kneeDb)
            }
        }
        val makeupDb = settings.compressorAmount * 3.2f
        v *= dbToLinear(-compReductionDb + makeupDb)

        // 6. Stereo Studio Reverb
        val reverbSend = v * 0.35f
        var wetL = 0f
        var wetR = 0f
        for (i in 0 until 4) {
            wetL += combsL[i].process(reverbSend)
            wetR += combsR[i].process(reverbSend)
        }
        wetL *= 0.25f
        wetR *= 0.25f
        for (i in 0 until 2) {
            wetL = allpassL[i].process(wetL)
            wetR = allpassR[i].process(wetR)
        }

        val wetMix = settings.reverbWet.coerceIn(0f, 0.75f)
        val dryMix = 1f - wetMix * 0.35f
        val vocalGain = dbToLinear(settings.vocalGainDb)
        val processedVocalL = (v * dryMix + wetL * wetMix) * vocalGain
        val processedVocalR = (v * dryMix + wetR * wetMix) * vocalGain

        // 7. Mix & Master Soft Limiter
        val musicGain = if (settings.soloVocal) 0f else dbToLinear(settings.musicGainDb)
        val mixL = backingL * musicGain + processedVocalL
        val mixR = backingR * musicGain + processedVocalR

        outPair[0] = softLimit(mixL)
        outPair[1] = softLimit(mixR)
    }

    private fun softLimit(x: Float): Float {
        val ax = abs(x)
        return if (ax <= 0.82f) {
            x
        } else {
            val sign = if (x >= 0f) 1f else -1f
            sign * (0.82f + 0.14f * tanh((ax - 0.82f) / 0.14f))
        }
    }

    companion object {
        private const val LN10 = 2.3025851f
        fun dbToLinear(db: Float): Float = 10f.pow(db / 20f)
    }
}

private class BiquadFilter {
    private var b0 = 1f
    private var b1 = 0f
    private var b2 = 0f
    private var a1 = 0f
    private var a2 = 0f
    private var z1 = 0f
    private var z2 = 0f

    fun reset() {
        z1 = 0f
        z2 = 0f
    }

    fun process(x: Float): Float {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }

    fun configureHighPass(fs: Float, fc: Float, q: Float) {
        val w0 = (2.0 * PI * fc / fs)
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / (2.0 * q)
        val a0 = 1.0 + alpha
        b0 = ((1.0 + cosW0) / 2.0 / a0).toFloat()
        b1 = (-(1.0 + cosW0) / a0).toFloat()
        b2 = ((1.0 + cosW0) / 2.0 / a0).toFloat()
        a1 = (-2.0 * cosW0 / a0).toFloat()
        a2 = ((1.0 - alpha) / a0).toFloat()
    }

    fun configurePeaking(fs: Float, fc: Float, q: Float, gainDb: Float) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * fc / fs
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / (2.0 * q)
        val a0 = 1.0 + alpha / a
        b0 = ((1.0 + alpha * a) / a0).toFloat()
        b1 = ((-2.0 * cosW0) / a0).toFloat()
        b2 = ((1.0 - alpha * a) / a0).toFloat()
        a1 = ((-2.0 * cosW0) / a0).toFloat()
        a2 = ((1.0 - alpha / a) / a0).toFloat()
    }

    fun configureHighShelf(fs: Float, fc: Float, slope: Float, gainDb: Float) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * fc / fs
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / 2.0 * sqrt((a + 1.0 / a) * (1.0 / slope - 1.0) + 2.0)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha
        val a0 = (a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha
        b0 = (a * ((a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha) / a0).toFloat()
        b1 = (-2.0 * a * ((a - 1.0) + (a + 1.0) * cosW0) / a0).toFloat()
        b2 = (a * ((a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha) / a0).toFloat()
        a1 = (2.0 * ((a - 1.0) - (a + 1.0) * cosW0) / a0).toFloat()
        a2 = (((a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha) / a0).toFloat()
    }
}

private class CombFilter(size: Int) {
    private val buffer = FloatArray(size)
    private var idx = 0
    private var filterStore = 0f
    private var feedback = 0.72f
    private var damp1 = 0.3f
    private var damp2 = 0.7f

    fun setParams(decay: Float, damping: Float) {
        feedback = decay.coerceIn(0.1f, 0.92f)
        damp1 = damping.coerceIn(0.05f, 0.85f)
        damp2 = 1f - damp1
    }

    fun reset() {
        buffer.fill(0f)
        idx = 0
        filterStore = 0f
    }

    fun process(input: Float): Float {
        val output = buffer[idx]
        filterStore = output * damp2 + filterStore * damp1
        buffer[idx] = input + filterStore * feedback
        idx++
        if (idx >= buffer.size) idx = 0
        return output
    }
}

private class AllpassFilter(size: Int) {
    private val buffer = FloatArray(size)
    private var idx = 0

    fun reset() {
        buffer.fill(0f)
        idx = 0
    }

    fun process(input: Float): Float {
        val bufOut = buffer[idx]
        val output = -input + bufOut
        buffer[idx] = input + bufOut * 0.5f
        idx++
        if (idx >= buffer.size) idx = 0
        return output
    }
}
