package com.gikoko.novelcreator.net

import android.content.Context
import com.gikoko.novelcreator.Prefs
import com.gikoko.novelcreator.novel.NovelEngine
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object AiClient {

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    private const val GEMINI_BASE = "https://generativelanguage.googleapis.com"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .build()

    fun normalizeBase(url: String): String {
        var u = url.trim()
        while (u.endsWith("/")) {
            u = u.substring(0, u.length - 1)
        }
        return u
    }

    fun defaultBase(provider: String): String {
        return if (provider == "gemini") GEMINI_BASE
        else if (provider == "openrouter") "https://openrouter.ai/api/v1"
        else ""
    }

    fun testKeyGemini(apiKey: String): String {
        val req = Request.Builder().url(GEMINI_BASE + "/v1beta/models?key=" + apiKey).get().build()
        client.newCall(req).execute().use { resp ->
            val txt = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw IOException("HTTP " + resp.code + ": " + txt.take(300))
            val arr = JSONObject(txt).optJSONArray("models") ?: JSONArray()
            return "OK - models: " + arr.length()
        }
    }

    fun testKeyCustom(baseUrl: String, apiKey: String): String {
        val req = Request.Builder()
            .url(normalizeBase(baseUrl) + "/models")
            .header("Authorization", "Bearer " + apiKey)
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            val txt = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw IOException("HTTP " + resp.code + ": " + txt.take(300))
            val arr = JSONObject(txt).optJSONArray("data") ?: JSONArray()
            return "OK - total models: " + arr.length()
        }
    }

    fun listGeminiModels(apiKey: String): ArrayList<String> {
        val req = Request.Builder().url(GEMINI_BASE + "/v1beta/models?key=" + apiKey).get().build()
        client.newCall(req).execute().use { resp ->
            val txt = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw IOException("HTTP " + resp.code + ": " + txt.take(600))
            val arr = JSONObject(txt).optJSONArray("models") ?: JSONArray()
            val out = ArrayList<String>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val name = o.optString("name", "")
                val id = if (name.startsWith("models/")) name.substring("models/".length) else name
                if (id.isEmpty()) continue
                val methods = o.optJSONArray("supportedGenerationMethods")
                var supported = false
                if (methods != null) {
                    for (j in 0 until methods.length()) {
                        if (methods.optString(j) == "generateContent") supported = true
                    }
                }
                if (supported) out.add(id)
            }
            if (out.isEmpty()) throw IOException("Gemini model တစ်ခုမှ မတွေ့ပါ")
            out.sort()
            return out
        }
    }

    fun listCustomModels(baseUrl: String, apiKey: String): ArrayList<String> {
        val builder = Request.Builder().url(normalizeBase(baseUrl) + "/models").get()
        if (apiKey.isNotEmpty()) builder.header("Authorization", "Bearer " + apiKey)
        val req = builder.build()
        client.newCall(req).execute().use { resp ->
            val txt = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw IOException("HTTP " + resp.code + ": " + txt.take(600))
            val arr = JSONObject(txt).optJSONArray("data") ?: JSONArray()
            val free = ArrayList<String>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.optString("id", "")
                if (id.isEmpty()) continue
                val pricing = o.optJSONObject("pricing")
                val promptFree = pricing != null && pricing.optString("prompt", "1") == "0"
                val compFree = pricing != null && pricing.optString("completion", "1") == "0"
                if (id.endsWith(":free") || (promptFree && compFree)) free.add(id)
            }
            free.sort()
            if (free.isEmpty()) throw IOException("Free model တစ်ခုမှ မတွေ့ပါ")
            return free
        }
    }

    private fun postJson(url: String, apiKey: String, body: String, geminiStyle: Boolean): JSONObject {
        var attempt = 0
        while (true) {
            val builder = Request.Builder().url(url).post(body.toRequestBody(JSON_MEDIA))
            if (!geminiStyle && apiKey.isNotEmpty()) builder.header("Authorization", "Bearer " + apiKey)
            val req = builder.build()
            client.newCall(req).execute().use { resp ->
                val txt = resp.body?.string() ?: ""
                if (resp.isSuccessful) return JSONObject(txt)
                val retriable = resp.code >= 500 || resp.code == 429
                if (!retriable || attempt >= 2) throw IOException("HTTP " + resp.code + ": " + txt.take(600))
                attempt++
            }
            Thread.sleep(3000L * (attempt + 1))
        }
    }

    fun geminiGenerate(apiKey: String, model: String, prompt: String, jsonMode: Boolean, temperature: Double): String {
        val genCfg = JSONObject().put("temperature", temperature)
        if (jsonMode) genCfg.put("response_mime_type", "application/json")
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put("generationConfig", genCfg)
        val resp = postJson(GEMINI_BASE + "/v1beta/models/" + model + ":generateContent?key=" + apiKey, apiKey, body.toString(), true)
        val cands = resp.optJSONArray("candidates") ?: throw IOException("Gemini response မှာ candidates မရှိပါ")
        val content = cands.getJSONObject(0).optJSONObject("content")
            ?: throw IOException("Gemini response မှာ content မရှိပါ")
        val parts = content.optJSONArray("parts") ?: throw IOException("Gemini response မှာ parts မရှိပါ")
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            sb.append(parts.getJSONObject(i).optString("text", ""))
        }
        if (sb.isBlank()) throw IOException("Gemini က စာသားဗလာ ပြန်တယ်")
        return sb.toString().trim()
    }

    fun customGenerate(baseUrl: String, apiKey: String, model: String, prompt: String, temperature: Double): String {
        val msg = JSONObject().put("role", "user").put("content", prompt)
        val body = JSONObject()
            .put("model", model)
            .put("messages", JSONArray().put(msg))
            .put("temperature", temperature)
        val resp = postJson(normalizeBase(baseUrl) + "/chat/completions", apiKey, body.toString(), false)
        val choices = resp.optJSONArray("choices") ?: throw IOException("Response မှာ choices မရှိပါ")
        val content = choices.getJSONObject(0).optJSONObject("message")
            ?: throw IOException("Response မှာ message မရှိပါ")
        val out = content.optString("content", "")
        if (out.isBlank()) throw IOException("AI က စာသားဗလာ ပြန်တယ်")
        return out.trim()
    }

    fun generate(ctx: Context, prompt: String, jsonMode: Boolean): String {
        val provider = Prefs.provider(ctx)
        val key = Prefs.apiKey(ctx)
        if (key.isEmpty()) throw IOException("API key မထည့်ရသေးပါ - PROVIDER စာမျက်ခုံတွင် ထည့်ပါ")
        val model = Prefs.model(ctx)
        if (model.isEmpty()) throw IOException("AI model ရွေးထားတာ မရှိပါ - PROVIDER စာမျက်ခုံတွင် ရွေးပါ")
        return if (provider == "gemini") geminiGenerate(key, model, prompt, jsonMode, 0.9)
        else customGenerate(Prefs.baseUrl(ctx), key, model, prompt, 0.9)
    }

    fun testKey(ctx: Context): String {
        val key = Prefs.apiKey(ctx)
        if (key.isEmpty()) throw IOException("API key မထည့်ရသေးပါ")
        return if (Prefs.provider(ctx) == "gemini") testKeyGemini(key)
        else testKeyCustom(Prefs.baseUrl(ctx), key)
    }

    fun generateCover(ctx: Context, title: String, genre: String): ByteArray {
        val provider = Prefs.provider(ctx)
        if (provider != "gemini") throw IOException("Cover ဆွဲခြင်းက Gemini provider နဲ့ပဲ အလုပ်လုပ်ပါသည် - PROVIDER မှာ Gemini ရွေးပါ")
        val key = Prefs.apiKey(ctx)
        if (key.isEmpty()) throw IOException("API key မထည့်ရသေးပါ")
        val g = NovelEngine.genreMy(genre)
        val prompt = "မြန်မာဝတ္ထုတစ်ပုဒ်အတွက် novel cover art တစ်ပုံ ဆွဲပေးပါ။ " +
            "ခေါင်းစဉ်: " + title + "။ အမျိုးအစား: " + g + "။ " +
            "Cyberpunk 3D style၊ dramatic lighting၊ ရွှေရောင်နဲ့ အနက်ရောင်အသုံးများစေပါ။ ပုံထဲမှာ စာလုံး လုံးဝမရေးပါနဲ့။"
        val models = arrayOf("gemini-3.1-flash-image", "gemini-2.5-flash-image", "gemini-3-pro-image")
        var lastErr: IOException? = null
        for (m in models) {
            try {
                return generateImageGemini(key, m, prompt)
            } catch (e: IOException) {
                lastErr = e
            }
        }
        throw lastErr ?: IOException("Cover မဆွဲနိုင်ပါ")
    }

    private fun generateImageGemini(apiKey: String, model: String, prompt: String): ByteArray {
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put("generationConfig", JSONObject().put("responseModalities", JSONArray().put("TEXT").put("IMAGE")))
        val req = Request.Builder()
            .url(GEMINI_BASE + "/v1beta/models/" + model + ":generateContent?key=" + apiKey)
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
        client.newCall(req).execute().use { resp ->
            val txt = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw IOException("HTTP " + resp.code + ": " + txt.take(300))
            val cands = JSONObject(txt).optJSONArray("candidates")
            if (cands == null || cands.length() == 0) throw IOException("AI ကပုံ မပြန်ပါ")
            val content = cands.getJSONObject(0).optJSONObject("content") ?: throw IOException("content မရှိပါ")
            val parts = content.optJSONArray("parts") ?: throw IOException("parts မရှိပါ")
            for (i in 0 until parts.length()) {
                val o = parts.getJSONObject(i)
                val inline = o.optJSONObject("inlineData") ?: continue
                val b64 = inline.optString("data", "")
                if (b64.isNotEmpty()) {
                    return android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                }
            }
            throw IOException("AI က IMAGE mode နဲ့ မပြန်ပါ")
        }
    }

    fun extractJson(raw: String): JSONObject {
        var s = raw.trim()
        val fence = s.indexOf("```")
        if (fence >= 0) {
            val firstNl = s.indexOf("\n")
            if (firstNl > 0) s = s.substring(firstNl + 1)
            val endFence = s.lastIndexOf("```")
            if (endFence >= 0) s = s.substring(0, endFence)
        }
        val start = s.indexOf("{")
        val end = s.lastIndexOf("}")
        if (start < 0 || end <= start) throw IOException("AI ပြန်စာက JSON မဟုတ်ပါ: " + raw.take(200))
        return JSONObject(s.substring(start, end + 1))
    }
}
