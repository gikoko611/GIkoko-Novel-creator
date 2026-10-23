package com.gikoko.novelcreator.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

object DownloadsUtil {

    fun saveText(ctx: Context, fileName: String, content: String): String {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues()
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/GIKOKO NOVEL")
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Download folder တွင် မသိမ်းနိုင်ပါ")
            val out = ctx.contentResolver.openOutputStream(uri)
                ?: throw IOException("Download folder တွင် မသိမ်းနိုင်ပါ")
            out.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            return "Downloads/GIKOKO NOVEL/" + fileName
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "GIKOKO NOVEL")
            if (!dir.exists()) dir.mkdirs()
            if (!dir.exists()) throw IOException("Download folder တွင် မသိမ်းနိုင်ပါ - SHARE ဖြင့် ထုတ်ပါ")
            val f = File(dir, fileName)
            f.writeText(content)
            return f.absolutePath
        }
    }

    fun shareFile(ctx: Context, file: File, mime: String) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
        val i = Intent(Intent.ACTION_SEND)
        i.type = mime
        i.putExtra(Intent.EXTRA_STREAM, uri)
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, "Share"))
    }
}
