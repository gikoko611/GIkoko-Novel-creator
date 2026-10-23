package com.gikoko.novelcreator

import android.content.Context
import com.gikoko.novelcreator.net.AiClient
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Prefs {

    private const val NAME = "novel_prefs"

    private fun sp(ctx: Context) = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun provider(ctx: Context): String = sp(ctx).getString("provider", "openrouter") ?: "openrouter"
    fun setProvider(ctx: Context, v: String) { sp(ctx).edit().putString("provider", v).apply() }

    fun baseUrl(ctx: Context): String {
        val def = AiClient.defaultBase(provider(ctx))
        return sp(ctx).getString("base_url", def) ?: def
    }
    fun setBaseUrl(ctx: Context, v: String) { sp(ctx).edit().putString("base_url", v).apply() }

    fun apiKey(ctx: Context): String = sp(ctx).getString("api_key", "") ?: ""
    fun setApiKey(ctx: Context, v: String) { sp(ctx).edit().putString("api_key", v).apply() }

    fun model(ctx: Context): String {
        val def = if (provider(ctx) == "gemini") "gemini-3.8-flash" else ""
        return sp(ctx).getString("model", def) ?: def
    }
    fun setModel(ctx: Context, v: String) { sp(ctx).edit().putString("model", v).apply() }

    fun voice(ctx: Context): String = sp(ctx).getString("voice", "my-MM-NilarNeural") ?: "my-MM-NilarNeural"
    fun setVoice(ctx: Context, v: String) { sp(ctx).edit().putString("voice", v).apply() }

    fun connStatus(ctx: Context): String = sp(ctx).getString("conn_status", "") ?: ""
    fun setConnStatus(ctx: Context, v: String) { sp(ctx).edit().putString("conn_status", v).apply() }

    fun templates(ctx: Context): ArrayList<String> {
        val out = ArrayList<String>()
        return try {
            val arr = JSONArray(sp(ctx).getString("prompt_templates", "[]"))
            for (i in 0 until arr.length()) out.add(arr.optString(i))
            out
        } catch (e: Exception) {
            out
        }
    }

    fun addTemplate(ctx: Context, t: String) {
        val list = templates(ctx)
        list.add(t.trim())
        sp(ctx).edit().putString("prompt_templates", JSONArray(list).toString()).apply()
    }

    fun removeTemplate(ctx: Context, idx: Int) {
        val list = templates(ctx)
        if (idx in list.indices) {
            list.removeAt(idx)
            sp(ctx).edit().putString("prompt_templates", JSONArray(list).toString()).apply()
        }
    }

    private fun usageJson(ctx: Context): JSONObject {
        return try {
            JSONObject(sp(ctx).getString("usage_hist", "{}"))
        } catch (e: Exception) {
            JSONObject()
        }
    }

    fun addUsage(ctx: Context) {
        try {
            val j = usageJson(ctx)
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            j.put(today, j.optInt(today) + 1)
            sp(ctx).edit().putString("usage_hist", j.toString()).apply()
        } catch (e: Exception) {
        }
    }

    fun usageWeek(ctx: Context): List<Pair<String, Int>> {
        val j = usageJson(ctx)
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dayFmt = SimpleDateFormat("EEE", Locale.US)
        val out = ArrayList<Pair<String, Int>>()
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -6)
        for (i in 0 until 7) {
            val key = fmt.format(cal.time)
            out.add(Pair(dayFmt.format(cal.time), j.optInt(key)))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return out
    }

    fun usageTotal(ctx: Context): Int {
        val j = usageJson(ctx)
        var total = 0
        val keys = j.keys()
        while (keys.hasNext()) {
            total += j.optInt(keys.next())
        }
        return total
    }
}
