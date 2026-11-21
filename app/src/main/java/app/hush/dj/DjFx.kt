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
import kotlin.math.sin

/**
 * The DJ's mixing desk for one deck: volume, a "bass cut" for the bass swap, a low-pass filter
 * for filter sweeps, and an echo. The engine moves the knobs; this applies them to the audio.
 * Every knob glides to its new value so nothing clicks.
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

    private var channels = 2
    private var rate = 44100

    // Smoothed knob values.
    private var gainS = 1f
    private var bassS = 0f
    private var lpS = 20000f
    private var sendS = 0f

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
        coefHz = -1f
        gainS = gain
        bassS = bassCut
        lpS = lowPassHz
        sendS = echoSend
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
        val delaySamples = (echoDelayMs.coerceIn(80f, 1500f) * rate / 1000f).toInt().coerceIn(1, delay[0].size - 1)

        val glide = 0.0015f
        val lowCoef = (1.0 - exp(-2.0 * PI * 170.0 / rate)).toFloat()
        val len = delay[0].size

        for (f in 0 until frames) {
            gainS += (tGain - gainS) * glide
            bassS += (tBass - bassS) * glide
            lpS += (tLp - lpS) * glide
            sendS += (tSend - sendS) * glide
            if ((f and 63) == 0) {
                if (lpS < 19000f) updateBiquad(lpS)
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

                // Echo: what was sent a moment ago comes back, and feeds itself a little.
                val wet = delay[c][readPos]
                delay[c][delayPos] = x * sendS + wet * 0.5f

                var o = x * gainS + wet * 0.7f
                if (o > 1f) o = 1f else if (o < -1f) o = -1f
                out.putShort((o * 32767f).toInt().toShort())
            }
            delayPos = (delayPos + 1) % len
        }

        // Any leftover bytes (should not happen) are copied as they are.
        while (inputBuffer.hasRemaining()) out.put(inputBuffer.get())
        out.flip()
    }
}
