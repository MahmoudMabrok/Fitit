package tools.mo3ta.fitit.ui.bluesecret

import kotlin.random.Random

enum class BlueSecretMode { COLUMN, ZALGO, SECRET }

/**
 * The tweet effect: a "#" plus stacked Unicode combining marks.
 * X paints hashtags blue, so the marks read as a vertical blue scribble.
 * SECRET mode buries a UTF-8 message in a private mark alphabet between two sentinels.
 */
object BlueSecret {

    private val up = charArrayOf(
        '\u030d', '\u030e', '\u0304', '\u0305', '\u033f', '\u0311', '\u0306', '\u0310',
        '\u0352', '\u0357', '\u0351', '\u0307', '\u0308', '\u030a', '\u0342', '\u0343',
        '\u0344', '\u034a', '\u034b', '\u034c', '\u0303', '\u0302', '\u030c', '\u0350',
        '\u0300', '\u0301', '\u030b', '\u030f', '\u0312', '\u0313', '\u0314', '\u033d',
        '\u0309', '\u0363', '\u0364', '\u0365', '\u0366', '\u0367', '\u0368', '\u0369',
        '\u036a', '\u036b', '\u036c', '\u036d', '\u036e', '\u036f', '\u033e', '\u035b',
        '\u0346', '\u031a'
    )

    private val down = charArrayOf(
        '\u0316', '\u0317', '\u0318', '\u0319', '\u031c', '\u031d', '\u031e', '\u031f',
        '\u0320', '\u0324', '\u0325', '\u0326', '\u0329', '\u032a', '\u032b', '\u032c',
        '\u032d', '\u032e', '\u032f', '\u0330', '\u0331', '\u0332', '\u0333', '\u0339',
        '\u033a', '\u033b', '\u033c', '\u0345', '\u0347', '\u0348', '\u0349', '\u034d',
        '\u034e', '\u0353', '\u0354', '\u0355', '\u0356', '\u0359', '\u035a', '\u0323'
    )

    /** Nibble alphabet for the hidden payload. Never used as visual noise. */
    private val alphabet = charArrayOf(
        '\u0321', '\u0322', '\u0327', '\u0328', '\u0334', '\u0335', '\u0336', '\u0337',
        '\u0338', '\u0340', '\u0341', '\u0358', '\u035c', '\u035d', '\u035e', '\u035f'
    )

    private val noise = up + down + charArrayOf('\u0315', '\u031b', '\u0360', '\u0361', '\u0362', '\u0489')

    private const val MARK_START = '\u20d0'
    private const val MARK_END = '\u20d1'

    fun generate(mode: BlueSecretMode, input: String, intensity: Int, seed: Int): String {
        val random = Random(seed)
        return when (mode) {
            BlueSecretMode.COLUMN -> column(intensity, random)
            BlueSecretMode.ZALGO -> zalgo(input, intensity, random)
            BlueSecretMode.SECRET -> hide(input, intensity, random)
        }
    }

    fun column(intensity: Int, random: Random = Random.Default): String {
        val count = intensity.coerceIn(8, 80)
        return buildString {
            append('#')
            repeat(count) { append(noise[random.nextInt(noise.size)]) }
        }
    }

    fun zalgo(text: String, intensity: Int, random: Random = Random.Default): String {
        val level = intensity.coerceIn(1, 10)
        val mid = charArrayOf(
            '\u0315', '\u031b', '\u0347', '\u0348', '\u0360', '\u0361', '\u0362', '\u0489'
        )
        return buildString {
            text.forEach { ch ->
                append(ch)
                if (!ch.isWhitespace()) {
                    repeat(level) { append(up[random.nextInt(up.size)]) }
                    repeat(maxOf(1, level / 2)) { append(mid[random.nextInt(mid.size)]) }
                    repeat(level) { append(down[random.nextInt(down.size)]) }
                }
            }
        }
    }

    fun hide(message: String, intensity: Int, random: Random = Random.Default): String {
        val bytes = message.encodeToByteArray()
        val payload = buildString {
            append(MARK_START)
            for (byte in bytes) {
                val value = byte.toInt() and 0xFF
                append(alphabet[value shr 4])
                append(alphabet[value and 0x0F])
            }
            append(MARK_END)
        }
        val noiseCount = intensity.coerceIn(0, 60)
        return buildString {
            append('#')
            repeat(noiseCount) { append(noise[random.nextInt(noise.size)]) }
            append(payload)
        }
    }

    fun reveal(text: String): String? {
        val chars = text.toList()
        val start = chars.indexOf(MARK_START)
        if (start < 0) return null
        val end = chars.indexOf(MARK_END, start + 1)
        if (end < 0 || (end - start - 1) % 2 != 0) return null
        val body = chars.subList(start + 1, end)
        val bytes = ByteArray(body.size / 2)
        for (i in bytes.indices) {
            val hi = alphabet.indexOf(body[i * 2])
            val lo = alphabet.indexOf(body[i * 2 + 1])
            if (hi < 0 || lo < 0) return null
            bytes[i] = ((hi shl 4) or lo).toByte()
        }
        return try {
            val decoded = bytes.decodeToString()
            if (decoded.contains('\uFFFD')) null else decoded
        } catch (_: Exception) {
            null
        }
    }
}
