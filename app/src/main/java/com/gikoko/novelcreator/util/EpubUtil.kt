package com.gikoko.novelcreator.util

import android.content.Context
import com.gikoko.novelcreator.novel.NovelStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object EpubUtil {

    fun export(ctx: Context, novelId: Long): File {
        val dir = NovelStore.dir(ctx, novelId)
        if (!dir.exists()) throw IOException("ဝတ္ထု မရှိပါ")
        val meta = NovelStore.meta(dir)
        val title = meta.optString("title", "Novel")
        val count = meta.optInt("chapterCount", 0)
        if (count < 1) throw IOException("အခန်း မရှိသေးပါ")
        val bookId = "urn:uuid:gikoko-novel-" + novelId

        val out = ByteArrayOutputStream()
        val zip = ZipOutputStream(out)
        writeStored(zip, "mimetype", "application/epub+zip".toByteArray(Charsets.US_ASCII))
        write(zip, "META-INF/container.xml", containerXml().toByteArray(Charsets.UTF_8))
        write(zip, "OEBPS/style.css", css().toByteArray(Charsets.UTF_8))
        write(zip, "OEBPS/nav.xhtml", navXhtml(title, count).toByteArray(Charsets.UTF_8))
        write(zip, "OEBPS/content.opf", opf(title, bookId, count).toByteArray(Charsets.UTF_8))
        for (n in 1..count) {
            write(zip, "OEBPS/ch" + n + ".xhtml", chapterXhtml(title, n, NovelStore.readChapter(dir, n)).toByteArray(Charsets.UTF_8))
        }
        zip.finish()

        val safe = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").replace(" ", "_")
        val f = File(ctx.cacheDir, safe + ".epub")
        f.writeBytes(out.toByteArray())
        return f
    }

    private fun writeStored(zip: ZipOutputStream, path: String, data: ByteArray) {
        val e = ZipEntry(path)
        e.method = ZipEntry.STORED
        val crc = CRC32()
        crc.update(data)
        e.crc = crc.value
        e.size = data.size.toLong()
        e.compressedSize = data.size.toLong()
        zip.putNextEntry(e)
        zip.write(data)
        zip.closeEntry()
    }

    private fun write(zip: ZipOutputStream, path: String, data: ByteArray) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(data)
        zip.closeEntry()
    }

    private fun esc(s: String): String {
        val sb = StringBuilder()
        for (c in s) {
            when (c) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun containerXml(): String {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">\n" +
            "<rootfiles><rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/></rootfiles>\n" +
            "</container>"
    }

    private fun css(): String {
        return "body{font-family:serif;line-height:1.8;margin:1em} h1,h2{color:#8a6d00} p{text-indent:0;margin:0 0 0.8em 0}"
    }

    private fun opf(title: String, bookId: String, count: Int): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        sb.append("<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"bookid\">\n")
        sb.append("<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n")
        sb.append("<dc:identifier id=\"bookid\">").append(esc(bookId)).append("</dc:identifier>\n")
        sb.append("<dc:title>").append(esc(title)).append("</dc:title>\n")
        sb.append("<dc:creator>G.I KO KO</dc:creator>\n")
        sb.append("<dc:language>my</dc:language>\n")
        sb.append("<meta property=\"dcterms:modified\">")
            .append(SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()))
            .append("</meta>\n")
        sb.append("</metadata>\n<manifest>\n")
        sb.append("<item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>\n")
        sb.append("<item id=\"css\" href=\"style.css\" media-type=\"text/css\"/>\n")
        for (n in 1..count) {
            sb.append("<item id=\"ch").append(n).append("\" href=\"ch").append(n).append(".xhtml\" media-type=\"application/xhtml+xml\"/>\n")
        }
        sb.append("</manifest>\n<spine>\n")
        for (n in 1..count) {
            sb.append("<itemref idref=\"ch").append(n).append("\"/>\n")
        }
        sb.append("</spine>\n</package>")
        return sb.toString()
    }

    private fun navXhtml(title: String, count: Int): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<!DOCTYPE html>\n")
        sb.append("<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\">\n")
        sb.append("<head><title>").append(esc(title)).append("</title>")
            .append("<link rel=\"stylesheet\" type=\"text/css\" href=\"style.css\"/></head>\n")
        sb.append("<body>\n<nav epub:type=\"toc\">\n<h1>").append(esc(title)).append("</h1>\n<ol>\n")
        for (n in 1..count) {
            sb.append("<li><a href=\"ch").append(n).append(".xhtml\">အခန်း ").append(n).append("</a></li>\n")
        }
        sb.append("</ol>\n</nav>\n</body>\n</html>")
        return sb.toString()
    }

    private fun chapterXhtml(title: String, n: Int, text: String): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<!DOCTYPE html>\n")
        sb.append("<html xmlns=\"http://www.w3.org/1999/xhtml\">\n")
        sb.append("<head><title>").append(esc(title)).append(" - အခန်း ").append(n).append("</title>")
            .append("<link rel=\"stylesheet\" type=\"text/css\" href=\"style.css\"/></head>\n")
        sb.append("<body>\n<h2>အခန်း ").append(n).append("</h2>\n")
        val lines = text.split("\n")
        for (line in lines) {
            val t = line.trim()
            if (t.isEmpty()) continue
            sb.append("<p>").append(esc(t)).append("</p>\n")
        }
        sb.append("</body>\n</html>")
        return sb.toString()
    }
}
