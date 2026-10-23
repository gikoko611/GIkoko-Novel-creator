package com.gikoko.novelcreator

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugLog {

    fun log(ctx: Context, msg: String) {
        try {
            val f = File(ctx.filesDir, "debug.log")
            val stamp = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date())
            f.appendText("[" + stamp + "] " + msg + "\n")
            if (f.length() > 400000L) f.delete()
        } catch (e: Exception) {
        }
    }
}
