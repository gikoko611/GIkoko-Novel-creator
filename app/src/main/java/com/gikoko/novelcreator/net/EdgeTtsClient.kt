package com.gikoko.novelcreator.net

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object EdgeTtsClient {

    private const val TRUSTED_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
    private const val BASE_WS_PRIMARY = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1"
    private const val BASE_WS_FALLBACK = "wss://api.msedgeservices.com/tts/cognitiveservices/websocket/v1"
    private const val SEC_MS_GEC_VERSION = "2-143.0.3650.75"
    const val VOICE_MY_FEMALE = "my-MM-NilarNeural"
    const val VOICE_MY_MALE = "my-MM-ThihaNeural"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    fun synthesizeMp3(text: String, voice: String, speed: Double): ByteArray {
        val t = text.trim()
        if (t.isEmpty()) throw IOException("Edge TTS: စာသား ဗလာဖြစ်နေပါတယ်")
        if (t.length <= 1800) return synthesizeOnce(t, voice, speed)
        val parts = splitText(t, 1800)
        val all = ByteArrayOutputStream()
        for (p in parts) {
            all.write(synthesizeOnce(p, voice, speed))
        }
        return all.toByteArray()
    }

    private fun splitText(text: String, max: Int): List<String> {
        val out = ArrayList<String>()
        var start = 0
        var lastBreak = -1
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '။' || c == '.' || c == '!' || c == '?' || c == '\n') lastBreak = i + 1
            if (i - start >= max) {
                val cut = if (lastBreak > start) lastBreak else start + max
                val piece = text.substring(start, cut).trim()
                if (piece.isNotEmpty()) out.add(piece)
                start = cut
                lastBreak = -1
            }
            i++
        }
        if (start < text.length) {
            val piece = text.substring(start).trim()
            if (piece.isNotEmpty()) out.add(piece)
        }
        return out
    }

    private fun buildUrl(base: String, connectionId: String, gec: String): String {
        val auth = if (base == BASE_WS_PRIMARY) "TrustedClientToken=" else "Ocp-Apim-Subscription-Key="
        return base + "?" + auth + TRUSTED_TOKEN +
            "&ConnectionId=" + connectionId +
            "&Sec-MS-GEC=" + gec +
            "&Sec-MS-GEC-Version=" + SEC_MS_GEC_VERSION
    }

    private fun synthesizeOnce(text: String, voice: String, speed: Double): ByteArray {
        val endpoints = arrayOf(BASE_WS_PRIMARY, BASE_WS_FALLBACK)
        var lastError: Exception? = null
        for (attempt in 0 until 4) {
            if (attempt > 0) {
                try { Thread.sleep(3000L) } catch (e: InterruptedException) { }
            }
            val id = hexId()
            val gec = DrmV2.secMsGec(TRUSTED_TOKEN)
            val url = buildUrl(endpoints[attempt % 2], id, gec)
            try {
                return synthesizeAttempt(url, id, text, voice, speed)
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IOException("Edge TTS: အသံမရရှိပါ")
    }

    private fun synthesizeAttempt(url: String, connectionId: String, text: String, voice: String, speed: Double): ByteArray {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Pragma", "no-cache")
            .header("Cache-Control", "no-cache")
            .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
            .header("Cookie", "muid=" + hexId().uppercase(Locale.US) + ";")
            .build()

        val audio = ByteArrayOutputStream()
        val done = CountDownLatch(1)
        val failureHolder = arrayOfNulls<Exception>(1)

        val ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val date = dateHeader()
                val config = "X-Timestamp:" + date + "\r\nContent-Type:application/json; charset=utf-8\r\nPath:speech.config\r\n\r\n" +
                    "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"true\"},\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}"
                webSocket.send(config)
                val ratePct = Math.round((speed - 1.0) * 100.0).toInt()
                val rateStr = (if (ratePct >= 0) "+" else "") + ratePct + "%"
                val ssml = "X-RequestId:" + connectionId + "\r\nContent-Type:application/ssml+xml\r\nX-Timestamp:" + date + "Z\r\nPath:ssml\r\n\r\n" +
                    "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'><voice name='" + voice + "'><prosody pitch='+0Hz' rate='" + rateStr + "' volume='+0%'>" + escapeXml(text) + "</prosody></voice></speak>"
                webSocket.send(ssml)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val arr = bytes.toByteArray()
                if (arr.size < 2) return
                val headerLen = ((arr[0].toInt() and 0xff) shl 8) or (arr[1].toInt() and 0xff)
                if (headerLen <= 0 || arr.size < headerLen + 2) return
                val header = String(arr, 2, headerLen, Charsets.UTF_8)
                if (header.contains("Path:audio")) {
                    audio.write(arr, headerLen + 2, arr.size - headerLen - 2)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("Path:turn.end")) done.countDown()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val code = if (response != null) " HTTP " + response.code else ""
                failureHolder[0] = IOException("Edge TTS" + code + ": " + (t.message ?: "connection failed"))
                done.countDown()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                done.countDown()
            }
        })

        try {
            if (!done.await(90, TimeUnit.SECONDS)) {
                throw IOException("Edge TTS: timeout (90s)")
            }
        } finally {
            runCatching { ws.cancel() }
        }
        val failure = failureHolder[0]
        if (failure != null) throw failure
        val mp3 = audio.toByteArray()
        if (mp3.size < 800) throw IOException("Edge TTS: audio မရရှိပါ")
        return mp3
    }

    private fun dateHeader(): String {
        val fmt = SimpleDateFormat("EEE MMM d HH:mm:ss yyyy", Locale.US)
        return fmt.format(Date()) + " GMT+0000 (Coordinated Universal Time)"
    }

    private fun hexId(): String {
        val c = "0123456789abcdef"
        val sb = StringBuilder()
        for (i in 0 until 32) sb.append(c[(Math.random() * 16).toInt()])
        return sb.toString()
    }

    private fun escapeXml(s: String): String {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&apos;")
    }
}

object DrmV2 {

    private const val MASK = 0xFFFFFFFFL
    private const val WIN_EPOCH = 11644473600L

    private val SHA256_INITIAL = longArrayOf(
        0x6A09E667L, 0xBB67AE85L, 0x3C6EF372L, 0xA54FF53AL,
        0x510E527FL, 0x9B05688CL, 0x1F83D9ABL, 0x5BE0CD19L
    )
    private val SHA256_STATE = longArrayOf(
        0xFD9FBEA2L, 0x91243CE7L, 0xBE499094L, 0xCD2E1E0AL,
        0x6C006F49L, 0x04BD35ABL, 0xFEB0A7E4L, 0x03EFFBD2L
    )
    private val SHA256_SCHEDULE = longArrayOf(
        0xEB24BF9EL, 0x1558691FL, 0xA0C09EE2L, 0x0CFA32BAL,
        0xA622EE8CL, 0xD1DD9491L, 0xC0DD25A0L, 0xF9E70AB6L,
        0xF0C31573L, 0x7058CE4EL, 0x7188E606L, 0x00E5AB33L,
        0xD258B060L, 0x72766564L
    )
    private val SHA256_K = longArrayOf(
        0x428A2F98L, 0x71374491L, 0xB5C0FBCFL, 0xE9B5DBA5L,
        0x3956C25BL, 0x59F111F1L, 0x923F82A4L, 0xAB1C5ED5L,
        0xD807AA98L, 0x12835B01L, 0x243185BEL, 0x550C7DC3L,
        0x72BE5D74L, 0x80DEB1FEL, 0x9BDC06A7L, 0xC19BF174L,
        0xE49B69C1L, 0xEFBE4786L, 0x0FC19DC6L, 0x240CA1CCL,
        0x2DE92C6FL, 0x4A7484AAL, 0x5CB0A9DCL, 0x76F988DAL,
        0x983E5152L, 0xA831C66DL, 0xB00327C8L, 0xBF597FC7L,
        0xC6E00BF3L, 0xD5A79147L, 0x06CA6351L, 0x14292967L,
        0x27B70A85L, 0x2E1B2138L, 0x4D2C6DFCL, 0x53380D13L,
        0x650A7354L, 0x766A0ABBL, 0x81C2C92EL, 0x92722C85L,
        0xA2BFE8A1L, 0xA81A664BL, 0xC24B8B70L, 0xC76C51A3L,
        0xD192E819L, 0xD6990624L, 0xF40E3585L, 0x106AA070L,
        0x19A4C116L, 0x1E376C08L, 0x2748774CL, 0x34B0BCB5L,
        0x391C0CB3L, 0x4ED8AA4AL, 0x5B9CCA4FL, 0x682E6FF3L,
        0x748F82EEL, 0x78A5636FL, 0x84C87814L, 0x8CC70208L,
        0x90BEFFFAL, 0xA4506CEBL, 0xBEF9A3F7L, 0xC67178F2L
    )
    private val SHA256_BLOCK_K = SHA256_K.copyOf().also { it[14] = 0xC9DC06A7L }
    private const val SIGMA0_ADJ = 0x059C0B80L
    private const val SIGMA1_ADJ = 0x000B92C0L

    private val CS64_COEFF = arrayOf(
        longArrayOf(0xE82E91B1L, 0xB32700D0L),
        longArrayOf(0x44DE24C6L, 0x88DFF51DL),
        longArrayOf(0x3BEB65BCL, 0x29514038L),
        longArrayOf(0xC89C5933L, 0xB328CBDFL),
        longArrayOf(0x8D1EC211L, 0xD405B4D5L),
        longArrayOf(0x314EF5D5L, 0x26E55F3CL),
        longArrayOf(0xE92491B1L, 0x607801C6L),
        longArrayOf(0xF0A5B9D5L, 0x82B2C1FBL),
        longArrayOf(0x06064CF3L, 0xFA51A3DAL),
        longArrayOf(0xDA79FA60L, 0x207CC464L),
        longArrayOf(0xBED20560L, 0x3C3C74F3L),
        longArrayOf(0x89F9C9F8L, 0xD2D14B61L),
        longArrayOf(0x35FA38C6L, 0xBB04A2C1L),
        longArrayOf(0xCB49E3C6L, 0xC5626167L)
    )
    private const val CS64_FACTOR = 0x82893933L
    private const val CS64_ADJ0 = 0xF2F90B1AL
    private const val CS64_ADJ1 = 0x0B1A0000L

    private fun rol16(value: Long): Long {
        val v = value and MASK
        return ((v shl 16) or (v ushr 16)) and MASK
    }

    private fun mul32(a: Long, b: Long): Long {
        val aLo = a and 0xFFFFL
        val aHi = (a ushr 16) and 0xFFFFL
        return (aLo * b + ((aHi * b and 0xFFFFL) shl 16)) and MASK
    }

    private fun sigma0(v: Long): Long =
        (v ushr 7) xor (v shl 25) xor (v ushr 18) xor (v shl 14) xor (v ushr 3)

    private fun sigma1(v: Long): Long =
        (v ushr 17) xor (v shl 15) xor (v ushr 19) xor (v shl 13) xor (v ushr 10)

    private fun rotShift(x: Long, k: Int): Long =
        ((x shl (32 - k)) + (x ushr k))

    private fun sha256Rounds(state: LongArray, words: List<Long>, firstRound: Int): LongArray {
        var a = state[0]
        var b = state[1]
        var c = state[2]
        var d = state[3]
        var e = state[4]
        var f = state[5]
        var g = state[6]
        var h = state[7]
        val constants = if (firstRound == 14) SHA256_BLOCK_K else SHA256_K
        val aligned = minOf(64, ((firstRound + 7) / 8) * 8)
        for (index in firstRound until aligned) {
            val t1 = h + (rotShift(e, 6) xor rotShift(e, 11) xor rotShift(e, 25)) +
                (g xor (e and (f xor g))) + constants[index] + words[index]
            val t2 = (rotShift(a, 2) xor rotShift(a, 13) xor rotShift(a, 22)) +
                ((a and b) or (c and (a or b)))
            val newA = (t1 + t2) and MASK
            val newE = (d + t1) and MASK
            h = g
            g = f
            f = e
            e = newE
            d = c
            c = b
            b = a
            a = newA
        }
        var index = aligned
        while (index < 64) {
            var t1 = h + (rotShift(e, 6) xor rotShift(e, 11) xor rotShift(e, 25)) +
                (g xor (e and (f xor g))) + constants[index] + words[index]
            d = (d + t1) and MASK
            h = (t1 + (rotShift(a, 2) xor rotShift(a, 13) xor rotShift(a, 22)) +
                ((h and a) or (b and (h or a)))) and MASK
            t1 = g + (rotShift(d, 6) xor rotShift(d, 11) xor rotShift(d, 25)) +
                (f xor (d and (e xor f))) + constants[index + 1] + words[index + 1]
            c = (c + t1) and MASK
            g = (t1 + (rotShift(h, 2) xor rotShift(h, 13) xor rotShift(h, 22)) +
                ((g and h) or (a and (g or h)))) and MASK
            t1 = f + (rotShift(c, 6) xor rotShift(c, 11) xor rotShift(c, 25)) +
                (e xor (c and (d xor e))) + constants[index + 2] + words[index + 2]
            b = (b + t1) and MASK
            f = (t1 + (rotShift(g, 2) xor rotShift(g, 13) xor rotShift(g, 22)) +
                ((f and g) or (h and (f or g)))) and MASK
            t1 = e + (rotShift(b, 6) xor rotShift(b, 11) xor rotShift(b, 25)) +
                (d xor (b and (c xor d))) + constants[index + 3] + words[index + 3]
            a = (a + t1) and MASK
            e = (t1 + (rotShift(f, 2) xor rotShift(f, 13) xor rotShift(f, 22)) +
                ((e and f) or (g and (e or f)))) and MASK
            t1 = d + (rotShift(a, 6) xor rotShift(a, 11) xor rotShift(a, 25)) +
                (c xor (a and (b xor c))) + constants[index + 4] + words[index + 4]
            h = (h + t1) and MASK
            d = (t1 + (rotShift(e, 2) xor rotShift(e, 13) xor rotShift(e, 22)) +
                ((d and e) or (f and (d or e)))) and MASK
            t1 = c + (rotShift(h, 6) xor rotShift(h, 11) xor rotShift(h, 25)) +
                (b xor (h and (a xor b))) + constants[index + 5] + words[index + 5]
            g = (g + t1) and MASK
            c = (t1 + (rotShift(d, 2) xor rotShift(d, 13) xor rotShift(d, 22)) +
                ((c and d) or (e and (c or d)))) and MASK
            t1 = b + (rotShift(g, 6) xor rotShift(g, 11) xor rotShift(g, 25)) +
                (a xor (g and (h xor a))) + constants[index + 6] + words[index + 6]
            f = (f + t1) and MASK
            b = (t1 + (rotShift(c, 2) xor rotShift(c, 13) xor rotShift(c, 22)) +
                ((b and c) or (d and (b or c)))) and MASK
            t1 = a + (rotShift(f, 6) xor rotShift(f, 11) xor rotShift(f, 25)) +
                (h xor (f and (g xor h))) + constants[index + 7] + words[index + 7]
            e = (e + t1) and MASK
            a = (t1 + (rotShift(b, 2) xor rotShift(b, 13) xor rotShift(b, 22)) +
                ((a and b) or (c and (a or b)))) and MASK
            index += 8
        }
        return longArrayOf(a, b, c, d, e, f, g, h)
    }

    private fun expandSchedule(words: MutableList<Long>, firstWord: Int) {
        for (index in firstWord until 64) {
            val x = words[index - 2]
            val y = words[index - 15]
            val s1 = (x ushr 17) xor (x shl 15) xor (x ushr 19) xor (x shl 13) xor (x ushr 10)
            val s0 = (y ushr 7) xor (y shl 25) xor (y ushr 18) xor (y shl 14) xor (y ushr 3)
            words.add((s1 + words[index - 7] + s0 + words[index - 16]) and MASK)
        }
    }

    private fun sha256Init(data: ByteArray): LongArray {
        val words = ArrayList<Long>()
        for (i in 0 until 14) words.add(0L)
        words.add(((data[0].toLong() and 0xFF) shl 16) or ((data[1].toLong() and 0xFF) shl 8) or (data[2].toLong() and 0xFF))
        words.add(((data[3].toLong() and 0xFF) shl 24) or ((data[4].toLong() and 0xFF) shl 16) or ((data[5].toLong() and 0xFF) shl 8) or (data[6].toLong() and 0xFF))
        for (index in 16 until 30) {
            var value = SHA256_SCHEDULE[index - 16]
            if (index - 2 >= 14) {
                value += if (index == 16) (sigma1(words[index - 2]) xor SIGMA1_ADJ) else sigma1(words[index - 2])
            }
            if (index - 7 >= 14) value += words[index - 7]
            if (index - 15 >= 14) {
                value += if (index == 29) (sigma0(words[index - 15]) xor SIGMA0_ADJ) else sigma0(words[index - 15])
            }
            words.add(value and MASK)
        }
        words.add((sigma1(words[28]) + words[23] + sigma0(words[15]) + words[14] + 0xC9DC06A7L - SHA256_K[14]) and MASK)
        expandSchedule(words, 31)
        val working = sha256Rounds(SHA256_STATE, words, 14)
        val out = LongArray(8)
        for (i in 0 until 8) out[i] = (SHA256_INITIAL[i] + working[i]) and MASK
        return out
    }

    private fun sha256Digest(data: ByteArray): ByteArray {
        if (data.size < 7) throw IOException("token data too short")
        val length = 57 + data.size
        val padLen = (((55 - length) % 64) + 64) % 64
        val padded = ByteArrayOutputStream()
        padded.write(data, 7, data.size - 7)
        padded.write(0x80)
        for (i in 0 until padLen) padded.write(0)
        val bitLen = (length * 8).toLong()
        var shift = 56
        while (shift >= 0) {
            padded.write(((bitLen ushr shift) and 0xFF).toInt())
            shift -= 8
        }
        val state = sha256Init(data.copyOf(7))
        val buf = padded.toByteArray()
        var offset = 0
        while (offset < buf.size) {
            val words = ArrayList<Long>()
            for (i in 0 until 16) {
                val b0 = buf[offset + i * 4].toLong() and 0xFF
                val b1 = buf[offset + i * 4 + 1].toLong() and 0xFF
                val b2 = buf[offset + i * 4 + 2].toLong() and 0xFF
                val b3 = buf[offset + i * 4 + 3].toLong() and 0xFF
                words.add((b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3)
            }
            expandSchedule(words, 16)
            val working = sha256Rounds(state, words, 0)
            for (i in 0 until 8) state[i] = (state[i] + working[i]) and MASK
            offset += 64
        }
        val out = ByteArray(32)
        for (i in 0 until 8) {
            out[i * 4] = ((state[i] ushr 24) and 0xFF).toByte()
            out[i * 4 + 1] = ((state[i] ushr 16) and 0xFF).toByte()
            out[i * 4 + 2] = ((state[i] ushr 8) and 0xFF).toByte()
            out[i * 4 + 3] = (state[i] and 0xFF).toByte()
        }
        return out
    }

    private fun wordsLe4Even(data: ByteArray): List<Long> {
        val pad = (4 - (data.size % 4)) % 4
        val padded = ByteArray(data.size + pad)
        for (i in data.indices) padded[i] = data[i]
        val words = ArrayList<Long>()
        var i = 0
        while (i < padded.size) {
            words.add(
                (padded[i].toLong() and 0xFF) or
                    ((padded[i + 1].toLong() and 0xFF) shl 8) or
                    ((padded[i + 2].toLong() and 0xFF) shl 16) or
                    ((padded[i + 3].toLong() and 0xFF) shl 24)
            )
            i += 4
        }
        if (words.size % 2 == 1) words.add(0L)
        return words
    }

    private fun cs64Digest(data: ByteArray, digest: ByteArray): LongArray {
        val keyA = ((digest[0].toLong() and 0xFF) or ((digest[1].toLong() and 0xFF) shl 8) or
            ((digest[2].toLong() and 0xFF) shl 16) or ((digest[3].toLong() and 0xFF) shl 24)) or 1L
        val keyB = ((digest[4].toLong() and 0xFF) or ((digest[5].toLong() and 0xFF) shl 8) or
            ((digest[6].toLong() and 0xFF) shl 16) or ((digest[7].toLong() and 0xFF) shl 24)) or 1L
        val factorA = mul32(keyA, CS64_FACTOR)
        val factorB = mul32(keyB, CS64_FACTOR)
        val terms = ArrayList<Long>()
        val offsets = ArrayList<Long>()
        for (i in CS64_COEFF.indices) {
            terms.add(mul32(CS64_COEFF[i][0], if (i % 2 == 0) factorA else factorB))
            offsets.add(CS64_COEFF[i][1])
        }
        val prefixed = ByteArray(data.size + 1)
        prefixed[0] = 0
        for (i in data.indices) prefixed[i + 1] = data[i]
        val words = wordsLe4Even(prefixed)
        for (index in words.indices) {
            val word = words[index]
            val even = index % 2 == 0
            var term = mul32(word, if (even) keyA else keyB)
            var offset = mul32(rol16(word), if (even) 0xEF0569FBL else 0xC31713DBL)
            if (index == 0) {
                term = (term + mul32(CS64_ADJ0, factorA)) and MASK
                offset = (offset + CS64_ADJ1) and MASK
            }
            terms.add(term and MASK)
            offsets.add(offset and MASK)
        }
        var swapAcc = 0L
        var swapLast = 0L
        var state = 0L
        var acc = 0L
        var index = 0
        while (index < terms.size) {
            val x = terms[index]
            val y = terms[index + 1]
            var t = rol16((offsets[index] + x) and MASK)
            var q = mul32(t, 0x76F50F34L)
            swapAcc += (mul32(rol16(q), 0xC3EFEA97L) + q) and MASK
            t = rol16((offsets[index + 1] + y) and MASK)
            q = mul32(t, 0x1881CE3CL)
            swapLast = (mul32(rol16(q), 0x35BD1EC9L) + q) and MASK
            swapAcc = (swapAcc + swapLast) and MASK
            state = (mul32(state, keyA) + x) and MASK
            state = mul32(rol16(state), 0xCF98B111L)
            state = mul32(rol16(state), 0x87085B9FL)
            state = mul32(rol16(state), 0x12CEB96DL)
            state = mul32(rol16(state), 0x257E1D83L)
            acc += state
            var t2 = (mul32(state, keyB) + y) and MASK
            t2 = mul32(rol16(t2), 0xA27416F5L)
            t2 = mul32(rol16(t2), 0xD38396FFL)
            state = mul32(rol16(mul32(t2, 0x7C932B89L)), 0xBFA49F69L)
            acc = (acc + state) and MASK
            index += 2
        }
        return longArrayOf(swapLast xor state, swapAcc xor acc)
    }

    private fun leHex32(v: Long): String {
        val sb = StringBuilder(8)
        var shift = 0
        while (shift < 32) {
            val b = ((v ushr shift) and 0xFFL).toInt()
            if (b < 16) sb.append('0')
            sb.append(Integer.toHexString(b))
            shift += 8
        }
        return sb.toString().uppercase(Locale.US)
    }

    private fun hexUpper(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            if (v < 16) sb.append('0')
            sb.append(Integer.toHexString(v))
        }
        return sb.toString().uppercase(Locale.US)
    }

    fun secMsGec(trustedToken: String): String {
        var t = System.currentTimeMillis() / 1000L + WIN_EPOCH
        t -= t % 300L
        t *= 10000000L
        val data = (t.toString() + trustedToken).toByteArray(Charsets.US_ASCII)
        val digest = sha256Digest(data)
        val cs = cs64Digest(data, digest)
        return leHex32(cs[0]) + leHex32(cs[1]) + "-" + hexUpper(digest)
    }
}
