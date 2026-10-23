package com.gikoko.novelcreator

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.gikoko.novelcreator.net.AiClient
import kotlin.concurrent.thread

class ProviderActivity : AppCompatActivity() {

    private var provider = "openrouter"

    private lateinit var providerRow: LinearLayout
    private lateinit var baseUrlInput: EditText
    private lateinit var keyInput: EditText
    private lateinit var modelInput: EditText
    private lateinit var statusTv: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shell)
        val root = findViewById<LinearLayout>(R.id.rootContainer)

        root.addView(Ui.backBtn(this) { finish() })
        root.addView(Ui.title(this, "🔑 AI PROVIDER & KEY"))
        root.addView(Ui.label(this, "API key တွေ ကိုယ်ပိုင်စက်အတွင်းမှာသာ သိမ်းထားပါသည်"))

        val providerCard = Ui.card(this)
        providerCard.addView(Ui.section(this, "PROVIDER ရွေးပါ"))
        providerRow = Ui.row(this)
        providerCard.addView(providerRow)
        root.addView(providerCard)

        val urlCard = Ui.card(this)
        urlCard.addView(Ui.section(this, "BASE URL"))
        baseUrlInput = Ui.input(this, "https://openrouter.ai/api/v1", Prefs.baseUrl(this), false)
        urlCard.addView(baseUrlInput)
        urlCard.addView(Ui.label(this, "Gemini မှာ BASE URL မလိုပါ"))
        root.addView(urlCard)

        val keyCard = Ui.card(this)
        keyCard.addView(Ui.section(this, "API KEY"))
        keyInput = Ui.input(this, "API key ထည့်ပါ", Prefs.apiKey(this), false)
        keyInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        keyCard.addView(keyInput)
        keyCard.addView(Ui.label(this, "Gemini key ကို aistudio.google.com မှာလည်း အခမဲ့ ရနိုင်သည်"))
        root.addView(keyCard)

        val modelCard = Ui.card(this)
        modelCard.addView(Ui.section(this, "MODEL"))
        modelInput = Ui.input(this, "model နာမည်", Prefs.model(this), false)
        modelCard.addView(modelInput)
        val freeBtn = Ui.chip(this, "🎁 FREE MODELS", false) { fetchFreeModels() }
        modelCard.addView(freeBtn)
        modelCard.addView(Ui.label(this, "FREE MODELS က provider ရဲ့ အခမဲ့ model စာရင်းကို အလိုအလျောက် ရယူပေးမည်"))
        root.addView(modelCard)

        val testBtn = Ui.goldBtn(this, "🔌 CONNECTION TEST") { testConnection() }
        root.addView(testBtn)
        val saveBtn = Ui.goldBtn(this, "💾 SAVE") { save() }
        root.addView(saveBtn)

        statusTv = Ui.statusLine(this, Prefs.connStatus(this))
        root.addView(statusTv)

        provider = Prefs.provider(this)
        refreshProviderChips()
    }

    private fun refreshProviderChips() {
        providerRow.removeAllViews()
        val items = listOf("GEMINI", "OPENROUTER", "CUSTOM")
        for (p in items) {
            val selected = provider.equals(p, ignoreCase = true)
            providerRow.addView(Ui.chip(this, p, selected) { pickProvider(p) })
        }
    }

    private fun pickProvider(p: String) {
        provider = p.lowercase()
        Prefs.setProvider(this, provider)
        baseUrlInput.setText(AiClient.defaultBase(provider))
        if (provider == "gemini") {
            modelInput.setText("gemini-3.8-flash")
        } else {
            modelInput.setText("")
        }
        refreshProviderChips()
    }

    private fun currentBaseUrl(): String {
        if (provider == "gemini") return AiClient.defaultBase("gemini")
        return baseUrlInput.text.toString().trim()
    }

    private fun fetchFreeModels() {
        val url = currentBaseUrl()
        val key = keyInput.text.toString().trim()
        if (url.isEmpty()) {
            Toast.makeText(this, "Base URL ထည့်ပါ", Toast.LENGTH_SHORT).show()
            return
        }
        val dialog = AlertDialog.Builder(this)
            .setMessage("Free models ရှာနေပါသည်...")
            .setCancelable(false)
            .show()
        thread(start = true) {
            try {
                val models = if (provider == "gemini") AiClient.listGeminiModels(key) else AiClient.listCustomModels(url, key)
                runOnUiThread {
                    dialog.dismiss()
                    val arr = models.toTypedArray()
                    AlertDialog.Builder(this)
                        .setTitle("🎁 FREE MODELS (" + arr.size + ")")
                        .setSingleChoiceItems(arr, -1) { d, idx ->
                            modelInput.setText(arr[idx])
                            d.dismiss()
                        }
                        .setNegativeButton("ပိတ်", null)
                        .show()
                }
            } catch (e: Exception) {
                DebugLog.log(this, "free models error: " + (e.message ?: ""))
                runOnUiThread {
                    dialog.dismiss()
                    Toast.makeText(this, "Free model ရှာမရပါ: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun testConnection() {
        val dialog = AlertDialog.Builder(this)
            .setMessage("ချိတ်ဆက် စစ်ဆေးနေပါသည်...")
            .setCancelable(false)
            .show()
        val key = keyInput.text.toString().trim()
        val url = currentBaseUrl()
        thread(start = true) {
            try {
                val result: String
                if (provider == "gemini") {
                    result = AiClient.testKeyGemini(key)
                } else {
                    result = AiClient.testKeyCustom(url, key)
                }
                Prefs.setConnStatus(this, "✔ ချိတ်ဆက်မှု အောင်မြင်")
                runOnUiThread {
                    dialog.dismiss()
                    statusTv.text = "✔ ချိတ်ဆက်မှု အောင်မြင် (" + result + ")"
                    AlertDialog.Builder(this)
                        .setTitle("✔ အောင်မြင်ပါသည်")
                        .setMessage(result)
                        .setPositiveButton("OK", null)
                        .show()
                }
            } catch (e: Exception) {
                DebugLog.log(this, "test connection error: " + (e.message ?: ""))
                Prefs.setConnStatus(this, "✖ ချိတ်ဆက်မှု မအောင်မြင်")
                runOnUiThread {
                    dialog.dismiss()
                    statusTv.text = "✖ ချိတ်ဆက်မှု မအောင်မြင်"
                    AlertDialog.Builder(this)
                        .setTitle("✖ မအောင်မြင်ပါ")
                        .setMessage(e.message ?: "")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    private fun save() {
        val key = keyInput.text.toString().trim()
        if (key.isEmpty()) {
            Toast.makeText(this, "API key ထည့်ပါ", Toast.LENGTH_SHORT).show()
            return
        }
        val model = modelInput.text.toString().trim()
        if (model.isEmpty()) {
            Toast.makeText(this, "Model ရွေး/ရိုက်ထည့်ပါ", Toast.LENGTH_SHORT).show()
            return
        }
        if (provider != "gemini" && currentBaseUrl().isEmpty()) {
            Toast.makeText(this, "Base URL ထည့်ပါ", Toast.LENGTH_SHORT).show()
            return
        }
        Prefs.setProvider(this, provider)
        Prefs.setBaseUrl(this, if (provider == "gemini") AiClient.defaultBase("gemini") else currentBaseUrl())
        Prefs.setApiKey(this, key)
        Prefs.setModel(this, model)
        Toast.makeText(this, "သိမ်းပြီးပါပြီ ✓", Toast.LENGTH_SHORT).show()
        finish()
    }
}
