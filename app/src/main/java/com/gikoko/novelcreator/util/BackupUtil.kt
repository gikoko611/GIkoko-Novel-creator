package com.gikoko.novelcreator.util

import android.content.Context
import com.gikoko.novelcreator.novel.NovelStore
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupUtil {

    fun backup(ctx: Context): File {
        val root = NovelStore.root(ctx)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val f = File(ctx.cacheDir, "gikoko_backup_" + stamp + ".zip")
        ZipOutputStream(f.outputStream()).use { zip ->
            addDirToZip(zip, root, "")
        }
        return f
    }

    private fun addDirToZip(zip: ZipOutputStream, dir: File, prefix: String) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            val entry = if (prefix.isEmpty()) file.name else prefix + "/" + file.name
            if (file.isDirectory) {
                addDirToZip(zip, file, entry)
            } else {
                zip.putNextEntry(ZipEntry(entry))
                FileInputStream(file).use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    fun restore(ctx: Context, input: InputStream): Int {
        val root = NovelStore.root(ctx)
        val seen = HashSet<String>()
        ZipInputStream(input).use { zin ->
            while (true) {
                val e: ZipEntry = zin.nextEntry ?: break
                val name = e.name
                if (name.contains("..") || name.startsWith("/")) {
                    zin.closeEntry()
                    continue
                }
                val outFile = File(root, name)
                if (e.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    if (name.endsWith("meta.json")) {
                        seen.add(outFile.parentFile?.name ?: "")
                    }
                    outFile.outputStream().use { out ->
                        zin.copyTo(out)
                    }
                }
                zin.closeEntry()
            }
        }
        return seen.size
    }
}
