package app.hush.dj

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * The DJ's mixing desk for one deck: volume, bass cut, low-pass filter, echo, a loop roll,
 * and two made-up sounds (a rising sweep and a bass impact). The engine moves the knobs;
 * this applies them to the audio. Every knob glides so nothing clicks.
 */
@OptIn(UnstableApi::class)
class DjFx : BaseAudioProcessor() {

    /** Volume of the dry sound, 0 to 1. */
    @Volatile var gain = 1f

    /** 0 = full bass, 1 = bass removed. */
    @Volatile var bassCut = 0f

    /** Low-pass filter cut-off in Hz. 20000 means the filter is off. */
    @Volatile var lowPassHz = 20000f

    /** How much of the sound is sent into the echo, 0 to 1. */
    @Volatile var echoSend = 0f

    /** Echo spacing in milliseconds (of the song itself). */
    @Volatile var echoDelayMs = 450f

    /** Rising "whoosh" before a drop, 0 to 1. */
    @Volatile var riser = 0f

    /** Loop roll: repeat the last chunk of this many milliseconds. 0 = off. */
    @Volatile var rollMs = 0f

    /** Change this number to play one bass impact. */
    @Volatile var impact = 0

    private var channels = 2
    private var rate = 44100

    // Smoothed knob values.
    private var gainS = 1f
    private var bassS = 0f
    private var lpS = 20000f
    private var sendS = 0f
    private var riserS = 0f

    // Bass cut: two gentle low-pass stages whose output is subtracted from the sound.
    private var low1 = FloatArray(2)
    private var low2 = FloatArray(2)

    // Low-pass filter (biquad) state per channel.
    private var z1 = FloatArray(2)
    private var z2 = FloatArray(2)
    private var b0 = 1f
    private var b1 = 0f
    private var b2 = 0f
    private var a1 = 0f
    private var a2 = 0f
    private var coefHz = -1f

    // Echo memory per channel.
    private var delay = Array(2) { FloatArray(1) }
    private var delayPos = 0

    // Loop roll memory per channel.
    private var hist = Array(2) { FloatArray(1) }
    private var histPos = 0
    private var rolling = false
    private var loopStart = 0
    private var loopLen = 1
    private var loopI = 0

    // Made-up sounds.
    private var rnd = 12345
    private var noiseLow = 0f
    private var seenImpact = 0
    private var impactOn = false
    private var impactN = 0
    private var impactPhase = 0.0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount !in 1..2) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        channels = inputAudioFormat.channelCount
        rate = inputAudioFormat.sampleRate
        allocate()
        return inputAudioFormat
    }

    private fun allocate() {
        low1 = FloatArray(channels)
        low2 = FloatArray(channels)
        z1 = FloatArray(channels)
        z2 = FloatArray(channels)
        delay = Array(channels) { FloatArray(rate * 2) }
        delayPos = 0
        hist = Array(channels) { FloatArray(rate * 2) }
        histPos = 0
        rolling = false
        noiseLow = 0f
        impactOn = false
        coefHz = -1f
        gainS = gain
        bassS = bassCut
        lpS = lowPassHz
        sendS = echoSend
        riserS = riser
        seenImpact = impact
    }

    override fun onFlush() {
        if (channels > 0) allocate()
    }

    override fun onReset() {
        channels = 2
        rate = 44100
    }

    /** Biquad low-pass (Q about 0.8) for the current cut-off. */
    private fun updateBiquad(hz: Float) {
        if (kotlin.math.abs(hz - coefHz) < coefHz * 0.01f && coefHz > 0f) return
        coefHz = hz
        val w = 2.0 * PI * hz / rate
        val alpha = sin(w) / (2.0 * 0.8)
        val cw = cos(w)
        val a0 = 1.0 + alpha
        b0 = (((1.0 - cw) / 2.0) / a0).toFloat()
        b1 = ((1.0 - cw) / a0).toFloat()
        b2 = b0
        a1 = ((-2.0 * cw) / a0).toFloat()
        a2 = ((1.0 - alpha) / a0).toFloat()
    }

    override fun queueInput(inputBuffer: java.nio.ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val out = replaceOutputBuffer(size)
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        out.order(ByteOrder.LITTLE_ENDIAN)

        val frameBytes = 2 * channels
        val frames = size / frameBytes

        // Where the knobs should end up this time.
        val tGain = gain
        val tBass = bassCut
        val tLp = lowPassHz.coerceIn(80f, 20000f)
        val tSend = echoSend
        val tRiser = riser
        val len = delay[0].size
        val delaySamples = (echoDelayMs.coerceIn(80f, 1500f) * rate / 1000f).toInt().coerceIn(1, len - 1)

        // Loop roll on or off, or a new loop length.
        val rollLen = if (rollMs > 0f) (rollMs * rate / 1000f).toInt().coerceIn(256, len / 2) else 0
        if (rollLen > 0) {
            if (!rolling) {
                rolling = true
                loopLen = rollLen
                loopStart = (histPos - loopLen + len) % len
                loopI = 0
            } else if (rollLen != loopLen) {
                loopLen = rollLen
                loopI %= loopLen
            }
        } else if (rolling) {
            rolling = false
        }

        val imp = impact
        if (imp != seenImpact) {
            seenImpact = imp
            impactOn = true
            impactN = 0
            impactPhase = 0.0
        }

        // The whoosh is noise with its low end removed; the cut-off climbs as the riser grows.
        val sweepHz = 300.0 + 5700.0 * tRiser * tRiser
        val noiseCoef = (1.0 - exp(-2.0 * PI * sweepHz / rate)).toFloat()

        val glide = 0.0015f
        val lowCoef = (1.0 - exp(-2.0 * PI * 170.0 / rate)).toFloat()

        for (f in 0 until frames) {
            gainS += (tGain - gainS) * glide
            bassS += (tBass - bassS) * glide
            lpS += (tLp - lpS) * glide
            sendS += (tSend - sendS) * glide
            riserS += (tRiser - riserS) * 0.002f
            if ((f and 63) == 0) {
                if (lpS < 19000f) updateBiquad(lpS)
            }

            // Made-up sounds, the same on both channels.
            var synth = 0f
            if (riserS > 0.002f || impactOn) {
                rnd = rnd * 1664525 + 1013904223
                val n = (rnd ushr 8) / 8388608f - 1f
                if (riserS > 0.002f) {
                    noiseLow += noiseCoef * (n - noiseLow)
                    synth += (n - noiseLow) * 0.32f * riserS * riserS
                }
                if (impactOn) {
                    val t = impactN / rate.toDouble()
                    val hz = 42.0 + 120.0 * exp(-t * 14.0)
                    impactPhase += 2.0 * PI * hz / rate
                    synth += (sin(impactPhase) * 0.6 * exp(-t * 4.5)).toFloat()
                    if (t < 0.03) synth += n * 0.22f * exp(-t * 120.0).toFloat()
                    impactN++
                    if (t > 1.0) impactOn = false
                }
            }

            val readPos = (delayPos - delaySamples + len) % len
            for (c in 0 until channels) {
                var x = inputBuffer.getShort() / 32768f

                // Bass cut.
                low1[c] += lowCoef * (x - low1[c])
                low2[c] += lowCoef * (low1[c] - low2[c])
                x -= bassS * low2[c]

                // Filter sweep.
                if (lpS < 19000f) {
                    val y = b0 * x + z1[c]
                    z1[c] = b1 * x - a1 * y + z2[c]
                    z2[c] = b2 * x - a2 * y
                    x = y
                }

                // Loop roll: keep the last moment in memory, or replay it.
                if (rolling) {
                    val edge = min(1f, min(loopI, loopLen - loopI) / 48f)
                    x = hist[c][(loopStart + loopI) % len] * edge
                } else {
                    hist[c][histPos] = x
                }

                // Echo: what was sent a moment ago comes back, and feeds itself a little.
                val wet = delay[c][readPos]
                delay[c][delayPos] = x * sendS + wet * 0.55f

                var o = x * gainS + wet * 0.85f + synth
                if (o > 1f) o = 1f else if (o < -1f) o = -1f
                out.putShort((o * 32767f).toInt().toShort())
            }
            if (rolling) loopI = (loopI + 1) % loopLen else histPos = (histPos + 1) % len
            delayPos = (delayPos + 1) % len
        }

        // Any leftover bytes (should not happen) are copied as they are.
        while (inputBuffer.hasRemaining()) out.put(inputBuffer.get())
        out.flip()
    }
}
