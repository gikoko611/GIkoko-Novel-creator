package com.gikoko.novelcreator.util

import android.content.Context
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.gikoko.novelcreator.novel.NovelStore
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExporter {

    fun export(ctx: Context, novelId: Long): File {
        val dir = NovelStore.dir(ctx, novelId)
        if (!dir.exists()) throw IOException("ဝတ္ထု မရှိပါ")
        val meta = NovelStore.meta(dir)
        val title = meta.optString("title", "Novel")
        val genre = meta.optString("genre", "")
        val count = meta.optInt("chapterCount", 0)
        if (count < 1) throw IOException("အခန်း မရှိသေးပါ")

        val pageW = 595
        val pageH = 842
        val margin = 56f
        val doc = PdfDocument()
        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
        var canvas = page.canvas
        var y = margin + 60f

        val bodyPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)
        bodyPaint.textSize = 11f
        val headPaint = TextPaint(bodyPaint)
        headPaint.textSize = 15f
        headPaint.typeface = Typeface.DEFAULT_BOLD
        val titlePaint = TextPaint(headPaint)
        titlePaint.textSize = 24f

        fun newPage() {
            doc.finishPage(page)
            pageNum++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
            canvas = page.canvas
            y = margin
        }

        fun drawBlock(text: String, paint: TextPaint, spacingAfter: Float) {
            val width = (pageW - 2 * margin).toInt()
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(4f, 1f)
                .build()
            for (i in 0 until layout.lineCount) {
                val lineH = (layout.getLineBottom(i) - layout.getLineTop(i)).toFloat()
                if (y + lineH > pageH - margin) newPage()
                canvas.drawText(text, layout.getLineStart(i), layout.getLineEnd(i), margin, y + (layout.getLineBaseline(i) - layout.getLineTop(i)), paint)
                y += lineH
            }
            y += spacingAfter
        }

        drawBlock(title, titlePaint, 12f)
        drawBlock("G.I KO KO NOVEL CREATOR • " + genre + " • " +
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()), bodyPaint, 30f)

        for (n in 1..count) {
            if (y > margin) newPage()
            drawBlock("အခန်း " + n, headPaint, 8f)
            drawBlock(NovelStore.readChapter(dir, n), bodyPaint, 20f)
        }

        doc.finishPage(page)
        val safe = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").replace(" ", "_")
        val f = File(ctx.cacheDir, safe + ".pdf")
        f.outputStream().use { doc.writeTo(it) }
        doc.close()
        return f
    }
}
