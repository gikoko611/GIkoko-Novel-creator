package com.gikoko.novelcreator.novel

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class NovelMeta(
    val id: Long,
    val dir: File,
    val title: String,
    val genre: String,
    val mode: String,
    val voice: String,
    val created: String,
    val chapterCount: Int
)

object NovelStore {

    fun root(ctx: Context): File {
        val f = File(ctx.filesDir, "novel_archive")
        if (!f.exists()) f.mkdirs()
        return f
    }

    fun dir(ctx: Context, id: Long): File = File(root(ctx), "" + id)

    fun create(ctx: Context, title: String, genre: String, mode: String, voice: String): Long {
        val id = System.currentTimeMillis()
        val d = dir(ctx, id)
        File(d, "chapters").mkdirs()
        File(d, "audio").mkdirs()
        val meta = JSONObject()
            .put("title", title)
            .put("genre", genre)
            .put("mode", mode)
            .put("voice", voice)
            .put("created", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()))
            .put("chapterCount", 0)
        File(d, "meta.json").writeText(meta.toString())
        return id
    }

    fun meta(d: File): JSONObject {
        return try {
            JSONObject(File(d, "meta.json").readText())
        } catch (e: Exception) {
            JSONObject()
        }
    }

    fun saveMeta(d: File, patch: JSONObject) {
        val m = meta(d)
        val keys = patch.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            m.put(k, patch.get(k))
        }
        File(d, "meta.json").writeText(m.toString())
    }

    fun chaptersDir(d: File): File = File(d, "chapters")
    fun audioDir(d: File): File = File(d, "audio")

    fun saveChapter(d: File, idx: Int, text: String) {
        chaptersDir(d).mkdirs()
        File(chaptersDir(d), "ch_" + idx + ".txt").writeText(text)
    }

    fun hasChapter(d: File, idx: Int): Boolean = File(chaptersDir(d), "ch_" + idx + ".txt").exists()

    fun readChapter(d: File, idx: Int): String {
        return try {
            File(chaptersDir(d), "ch_" + idx + ".txt").readText()
        } catch (e: Exception) {
            ""
        }
    }

    fun chapterCount(d: File): Int = meta(d).optInt("chapterCount", 0)

    fun saveOutline(d: File, json: JSONObject) {
        File(d, "outline.json").writeText(json.toString())
    }

    fun outline(d: File): JSONObject? {
        return try {
            val f = File(d, "outline.json")
            if (f.exists()) JSONObject(f.readText()) else null
        } catch (e: Exception) {
            null
        }
    }

    fun audioFile(d: File, idx: Int): File = File(audioDir(d), "ch_" + idx + ".mp3")

    fun list(ctx: Context): ArrayList<NovelMeta> {
        val out = ArrayList<NovelMeta>()
        val rootDir = root(ctx)
        val dirs = rootDir.listFiles() ?: return out
        for (d in dirs) {
            if (!d.isDirectory) continue
            val m = meta(d)
            out.add(
                NovelMeta(
                    d.name.toLongOrNull() ?: 0L,
                    d,
                    m.optString("title", "No Title"),
                    m.optString("genre", ""),
                    m.optString("mode", ""),
                    m.optString("voice", "my-MM-NilarNeural"),
                    m.optString("created", ""),
                    m.optInt("chapterCount", 0)
                )
            )
        }
        out.sortByDescending { it.id }
        return out
    }

    fun delete(ctx: Context, id: Long): Boolean = dir(ctx, id).deleteRecursively()

    fun fullText(d: File): String {
        val sb = StringBuilder()
        val m = meta(d)
        sb.append(m.optString("title", "Novel")).append("\n\n")
        val n = m.optInt("chapterCount", 0)
        for (i in 1..n) {
            sb.append("---------- အခန်း ").append(i).append(" ----------\n\n")
            sb.append(readChapter(d, i)).append("\n\n")
        }
        return sb.toString()
    }
}
