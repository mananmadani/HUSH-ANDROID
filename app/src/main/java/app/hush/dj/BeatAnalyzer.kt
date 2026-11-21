package app.hush.dj

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Listens to a downloaded song (on the phone, nothing is sent anywhere) and works out its tempo,
 * where the beats fall, where the music really starts and ends, and how energetic it is.
 */
object BeatAnalyzer {

    /** What was found out about one song. Times are in milliseconds of the song itself. */
    class Result(
        /** Beats per minute, or 0 when no steady beat was found. */
        val bpm: Float,
        val firstBeatMs: Double,
        /** Length of one beat in ms, or 0 when there is no steady beat. */
        val periodMs: Double,
        /** Where the music really starts (after any silence). */
        val startMs: Long,
        /** Where the music really ends (before any fade to silence). */
        val endMs: Long,
        /** 0 (calm) to 1 (intense). */
        val energy: Float,
        val durationMs: Long
    ) {
        val hasBeat: Boolean get() = bpm > 0f && periodMs > 0.0

        fun beatAtOrAfter(t: Double): Double {
            if (!hasBeat) return t
            return firstBeatMs + ceil((t - firstBeatMs) / periodMs) * periodMs
        }

        fun beatAtOrBefore(t: Double): Double {
            if (!hasBeat) return t
            return firstBeatMs + floor((t - firstBeatMs) / periodMs) * periodMs
        }

        /** Where inside the current beat the song is: 0 = right on the beat, up to 1. */
        fun phaseAt(t: Double): Float {
            if (!hasBeat) return 0f
            val x = (t - firstBeatMs) / periodMs
            return (x - floor(x)).toFloat()
        }

        fun beatNumberAt(t: Double): Int {
            if (!hasBeat) return 0
            return floor((t - firstBeatMs) / periodMs).toInt()
        }
    }

    private class Envelope(val hopMs: Double, val full: FloatArray, val low: FloatArray, val durationMs: Long)

    /** Returns null if the file cannot be read or is too short to say anything useful. */
    fun analyze(path: String): Result? {
        val env = try {
            decode(path)
        } catch (e: Exception) {
            null
        } ?: return null
        return try {
            work(env)
        } catch (e: Exception) {
            null
        }
    }

    // --- Step 1: decode to a small loudness curve --------------------------------------------

    private class Growing {
        var data = FloatArray(4096)
        var size = 0
        fun add(v: Float) {
            if (size == data.size) data = data.copyOf(size * 2)
            data[size++] = v
        }
        fun toArray(): FloatArray = data.copyOf(size)
    }

    private fun decode(path: String): Envelope? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(path)
            var index = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) {
                    index = i
                    format = f
                    break
                }
            }
            if (index < 0 || format == null) return null
            extractor.selectTrack(index)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null

            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var isFloat = false

            val c = MediaCodec.createDecoderByType(mime)
            codec = c
            c.configure(format, null, null, 0)
            c.start()

            var hop = max(256, (rate * 0.0116).toInt())
            var lowCoef = 1.0 - exp(-2.0 * PI * 150.0 / rate)

            val full = Growing()
            val low = Growing()
            var sumFull = 0.0
            var sumLow = 0.0
            var inHop = 0
            var lowState = 0.0
            var totalFrames = 0L

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            val limitFrames = 13L * 60L * rate

            while (!outputDone && totalFrames < limitFrames) {
                if (!inputDone) {
                    val ii = c.dequeueInputBuffer(5000)
                    if (ii >= 0) {
                        val buf = c.getInputBuffer(ii)
                        val size = if (buf != null) extractor.readSampleData(buf, 0) else -1
                        if (size < 0) {
                            c.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            c.queueInputBuffer(ii, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val oi = c.dequeueOutputBuffer(info, 5000)
                if (oi >= 0) {
                    val out = c.getOutputBuffer(oi)
                    if (out != null && info.size > 0) {
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        out.order(ByteOrder.LITTLE_ENDIAN)
                        val ch = max(1, channels)
                        if (isFloat) {
                            val fb = out.asFloatBuffer()
                            val frames = fb.remaining() / ch
                            for (f in 0 until frames) {
                                var m = 0f
                                for (k in 0 until ch) m += fb.get()
                                val x = (m / ch).toDouble()
                                lowState += lowCoef * (x - lowState)
                                sumFull += x * x
                                sumLow += lowState * lowState
                                inHop++
                                if (inHop >= hop) {
                                    full.add(sqrt(sumFull / inHop).toFloat())
                                    low.add(sqrt(sumLow / inHop).toFloat())
                                    sumFull = 0.0; sumLow = 0.0; inHop = 0
                                }
                            }
                            totalFrames += frames
                        } else {
                            val sb = out.asShortBuffer()
                            val frames = sb.remaining() / ch
                            for (f in 0 until frames) {
                                var m = 0
                                for (k in 0 until ch) m += sb.get().toInt()
                                val x = m / ch / 32768.0
                                lowState += lowCoef * (x - lowState)
                                sumFull += x * x
                                sumLow += lowState * lowState
                                inHop++
                                if (inHop >= hop) {
                                    full.add(sqrt(sumFull / inHop).toFloat())
                                    low.add(sqrt(sumLow / inHop).toFloat())
                                    sumFull = 0.0; sumLow = 0.0; inHop = 0
                                }
                            }
                            totalFrames += frames
                        }
                    }
                    c.releaseOutputBuffer(oi, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                } else if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val of = c.outputFormat
                    rate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    isFloat = of.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        of.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    hop = max(256, (rate * 0.0116).toInt())
                    lowCoef = 1.0 - exp(-2.0 * PI * 150.0 / rate)
                }
            }

            if (full.size < 200 || rate <= 0) return null
            return Envelope(
                hopMs = hop * 1000.0 / rate,
                full = full.toArray(),
                low = low.toArray(),
                durationMs = totalFrames * 1000L / rate
            )
        } finally {
            try {
                codec?.stop()
            } catch (_: Exception) {
            }
            try {
                codec?.release()
            } catch (_: Exception) {
            }
            extractor.release()
        }
    }

    // --- Step 2: tempo, beat positions, start/end, energy ------------------------------------

    private fun work(env: Envelope): Result? {
        val n = env.full.size
        val hop = env.hopMs

        // Start and end of the real music, and overall loudness.
        val meanFull = env.full.average().toFloat()
        if (meanFull < 1e-4f) return null
        var firstLoud = 0
        while (firstLoud < n - 1 && env.full[firstLoud] < 0.3f * meanFull) firstLoud++
        var lastLoud = n - 1
        while (lastLoud > firstLoud && env.full[lastLoud] < 0.15f * meanFull) lastLoud--
        var startMs = (firstLoud * hop).toLong()
        var endMs = ((lastLoud + 1) * hop).toLong()
        if (endMs - startMs < 20_000L) {
            startMs = 0L
            endMs = env.durationMs
        }

        // "Hits": sudden rises in the bass and in the whole sound.
        val raw = FloatArray(n)
        for (i in 1 until n) {
            raw[i] = max(0f, env.low[i] - env.low[i - 1]) + 0.5f * max(0f, env.full[i] - env.full[i - 1])
        }
        // Take away the slowly changing average so only real hits stand out.
        val prefix = DoubleArray(n + 1)
        for (i in 0 until n) prefix[i + 1] = prefix[i] + raw[i]
        val onset = FloatArray(n)
        val half = 21
        for (i in 0 until n) {
            val a = max(0, i - half)
            val b = min(n, i + half + 1)
            val mean = (prefix[b] - prefix[a]) / (b - a)
            onset[i] = max(0f, (raw[i] - mean).toFloat())
        }
        val smooth = FloatArray(n)
        for (i in 0 until n) {
            val l = onset[max(0, i - 1)]
            val r = onset[min(n - 1, i + 1)]
            smooth[i] = (l + 2f * onset[i] + r) / 4f
        }
        val meanOnset = smooth.average()
        if (meanOnset < 1e-7) {
            return Result(0f, 0.0, 0.0, startMs, endMs, energyOf(meanFull, 0f), env.durationMs)
        }

        // Candidate tempos from how well the hits repeat.
        val minLag = max(8, floor(60000.0 / (190.0 * hop)).toInt())
        val maxLag = min(n / 3, ceil(60000.0 / (70.0 * hop)).toInt())
        if (maxLag <= minLag + 2) {
            return Result(0f, 0.0, 0.0, startMs, endMs, energyOf(meanFull, 0f), env.durationMs)
        }
        val ac = DoubleArray(maxLag + 2)
        for (lag in minLag - 1..maxLag + 1) {
            var s = 0.0
            val count = n - lag
            for (i in 0 until count) s += onset[i] * onset[i + lag]
            ac[lag] = s / count
        }
        val weighted = ArrayList<Pair<Int, Double>>()
        for (lag in minLag..maxLag) {
            if (ac[lag] >= ac[lag - 1] && ac[lag] >= ac[lag + 1]) {
                val bpm = 60000.0 / (lag * hop)
                weighted.add(lag to ac[lag] * prior(bpm))
            }
        }
        val candidates = weighted.sortedByDescending { it.second }.take(4)

        var bestScore = -1.0
        var bestPeriod = 0.0
        var bestRaw = 0.0
        for ((lag, _) in candidates) {
            var candScore = -1.0
            var candPeriod = lag.toDouble()
            var candRaw = 0.0
            var p = lag - 0.7
            while (p <= lag + 0.7) {
                val s = combScore(smooth, p, 0.5)
                if (s > candRaw) {
                    candRaw = s
                    candPeriod = p
                }
                p += 0.02
            }
            // Fine search around the best.
            var q = candPeriod - 0.05
            val centre = candPeriod
            while (q <= centre + 0.05) {
                val s = combScore(smooth, q, 0.25)
                if (s > candRaw) {
                    candRaw = s
                    candPeriod = q
                }
                q += 0.004
            }
            candScore = candRaw * prior(60000.0 / (candPeriod * hop))
            if (candScore > bestScore) {
                bestScore = candScore
                bestPeriod = candPeriod
                bestRaw = candRaw
            }
        }
        if (bestPeriod <= 0.0) {
            return Result(0f, 0.0, 0.0, startMs, endMs, energyOf(meanFull, 0f), env.durationMs)
        }

        // Is the beat clear enough to trust? (Hits on the beat versus hits anywhere.)
        val confidence = bestRaw / meanOnset
        if (confidence < 1.6) {
            return Result(0f, 0.0, 0.0, startMs, endMs, energyOf(meanFull, 0f), env.durationMs)
        }

        // Pin the beat grid to the start and to the end of the song, so it does not drift.
        val head = bestPhase(smooth, bestPeriod, 0, (n * 0.4).toInt())
        val tail = bestPhase(smooth, bestPeriod, (n * 0.6).toInt(), n)
        var period = bestPeriod
        var firstHop = head
        if (head >= 0.0 && tail >= 0.0) {
            val beats = ((tail - head) / bestPeriod).roundToInt()
            if (beats > 4) {
                val refined = (tail - head) / beats
                if (abs(refined / bestPeriod - 1.0) < 0.004) {
                    period = refined
                    firstHop = head
                }
            }
        } else if (head < 0.0) {
            firstHop = bestPhase(smooth, bestPeriod, 0, n)
            if (firstHop < 0.0) firstHop = 0.0
        }
        // Move the first beat back to the very start of the song.
        firstHop -= floor(firstHop / period) * period

        val bpm = (60000.0 / (period * hop)).toFloat()
        return Result(
            bpm = bpm,
            firstBeatMs = firstHop * hop,
            periodMs = period * hop,
            startMs = startMs,
            endMs = endMs,
            energy = energyOf(meanFull, bpm),
            durationMs = env.durationMs
        )
    }

    /** Dance music mostly sits around 128 BPM, so near-128 candidates are favoured. */
    private fun prior(bpm: Double): Double {
        val z = ln(bpm / 128.0) / 0.35
        return exp(-0.5 * z * z)
    }

    private fun energyOf(meanLoud: Float, bpm: Float): Float {
        val loud = (meanLoud / 0.25f).coerceIn(0f, 1f)
        val pace = if (bpm > 0f) ((bpm - 90f) / 60f).coerceIn(0f, 1f) else 0.4f
        return (0.55f * loud + 0.45f * pace).coerceIn(0f, 1f)
    }

    private fun sampleAt(a: FloatArray, pos: Double): Float {
        val i = floor(pos).toInt()
        if (i < 0 || i >= a.size - 1) return 0f
        val f = (pos - i).toFloat()
        return a[i] * (1f - f) + a[i + 1] * f
    }

    /** Average strength of the hits that land on a grid with this beat length, at the best offset. */
    private fun combScore(a: FloatArray, period: Double, phaseStep: Double): Double {
        var best = 0.0
        var phase = 0.0
        while (phase < period) {
            var sum = 0.0
            var count = 0
            var t = phase
            while (t < a.size - 1) {
                sum += sampleAt(a, t)
                count++
                t += period
            }
            if (count > 0) {
                val v = sum / count
                if (v > best) best = v
            }
            phase += phaseStep
        }
        return best
    }

    /** Position (in hops) of the first beat inside [from, to) for the best offset, or -1. */
    private fun bestPhase(a: FloatArray, period: Double, from: Int, to: Int): Double {
        var best = -1.0
        var bestSum = -1.0
        var phase = 0.0
        while (phase < period) {
            var t = from + phase
            var sum = 0.0
            var count = 0
            while (t < to && t < a.size - 1) {
                sum += sampleAt(a, t)
                count++
                t += period
            }
            if (count >= 3) {
                val v = sum / count
                if (v > bestSum) {
                    bestSum = v
                    best = from + phase
                }
            }
            phase += 0.25
        }
        return best
    }
}
