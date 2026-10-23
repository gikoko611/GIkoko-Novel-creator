package com.gikoko.novelcreator.util

import com.gikoko.novelcreator.net.EdgeTtsClient
import java.io.ByteArrayOutputStream

object DualVoiceUtil {

    fun altVoiceFor(voice: String): String {
        return if (voice == EdgeTtsClient.VOICE_MY_FEMALE) EdgeTtsClient.VOICE_MY_MALE else EdgeTtsClient.VOICE_MY_FEMALE
    }

    fun synthesize(text: String, mainVoice: String, altVoice: String, speed: Double): ByteArray {
        val segments = splitDialogue(text)
        val all = ByteArrayOutputStream()
        for (s in segments) {
            val t = s.text.trim()
            if (t.isEmpty()) continue
            val v = if (s.dialogue) altVoice else mainVoice
            all.write(EdgeTtsClient.synthesizeMp3(t, v, speed))
        }
        return all.toByteArray()
    }

    private class Seg(val text: String, val dialogue: Boolean)

    private fun splitDialogue(text: String): List<Seg> {
        val out = ArrayList<Seg>()
        val sb = StringBuilder()
        var inQuote = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (!inQuote && (c == '"' || c == '\u201C')) {
                flushNarration(out, sb)
                inQuote = true
                sb.append(c)
            } else if (inQuote && (c == '"' || c == '\u201D')) {
                sb.append(c)
                out.add(Seg(sb.toString(), true))
                sb.setLength(0)
                inQuote = false
            } else {
                sb.append(c)
            }
            i++
        }
        if (inQuote) {
            out.add(Seg(sb.toString(), true))
        } else {
            flushNarration(out, sb)
        }
        return out
    }

    private fun flushNarration(out: MutableList<Seg>, sb: StringBuilder) {
        if (sb.isNotEmpty()) {
            out.add(Seg(sb.toString(), false))
            sb.setLength(0)
        }
    }
}
