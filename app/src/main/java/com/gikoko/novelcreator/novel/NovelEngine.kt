package com.gikoko.novelcreator.novel

object NovelEngine {

    val GENRES = arrayOf("Romance", "Action", "Horror", "Sci-Fi", "Fantasy", "Mystery", "Historical", "Drama", "Comedy", "Adventure")
    val GENRES_MY = arrayOf("အချစ်ဇာတ်လမ်း", "အက်ရှင်", "ထွတ်ထိုးစိတ်ဝင်စား", "သိပ္ပံစိတ်ကူးယဉ်", "ဒဏ္ဍာရီ", "စုံစမ်းဆန်းစစ်", "သမိုင်း", "ဇာတ်ကြောင်းဆန်", "ဟာသ", "စွန့်စားခန်း")

    val CHAPTER_OPTIONS = arrayOf(3, 5, 8, 10)
    val LENGTH_LABELS = arrayOf("တို", "အလယ်", "ရှည်")
    val LENGTH_PARAS = arrayOf("စာပိုဒ် ၄ မှ ၆ ပိုဒ်", "စာပိုဒ် ၈ မှ ၁၂ ပိုဒ်", "စာပိုဒ် ၁၄ မှ ၂၀ ပိုဒ်")

    fun genreMy(genre: String): String {
        val i = GENRES.indexOf(genre)
        return if (i >= 0) GENRES_MY[i] else genre
    }

    private fun templateLine(template: String): String {
        return if (template.isBlank()) "" else "စာရေးရမှုဟန် မှာကြားချက်: " + template + " "
    }

    fun outlinePrompt(idea: String, genre: String, chapters: Int, lenDesc: String, template: String = ""): String {
        val g = genreMy(genre)
        return "သင်သည် မြန်မာစာပေါင်းများစွာ ဖတ်ရှုခဲ့သော ကျွမ်းကျင်သည့် မြန်မာဝတ္ထုရေးဆရာကြီးတစ်ဦး ဖြစ်သည်။ " +
            templateLine(template) +
            "အောက်ပါ ဇာတ်လမ်းအိုင်ဒီယာကို အခြေခံပြီး " + g + " အမျိုးအစား မြန်မာဝတ္ထုတစ်ပုဒ်အတွက် ဇာတ်လမ်းအကြဉ်း စီစဉ်ပေးပါ။ " +
            "အိုင်ဒီယာ: " + idea + " " +
            "ဇာတ်လမ်းကို အခန်း " + chapters + " ခန်းဖြင့် ရေးမည်ဖြစ်ပြီး တစ်ခန်းလျှင် " + lenDesc + " ရှိမည်။ " +
            "ဇာတ်လမ်းက စိတ်လှုပ်စရာကောင်းပြီး ဇာတ်လမ်းအီးဗင်တွေ ချိတ်ဆက်မှုရှိစေပါ။ " +
            "Return STRICT JSON only (no markdown, no extra text) in this exact shape: " +
            "{\"title\":\"ဝတ္ထုခေါင်းစဉ်\",\"setting\":\"ဇာတ်လမ်းဖြစ်တဲ့နေရာ နဲ့ အခြေအနေ\",\"characters\":[{\"name\":\"နာမည်\",\"role\":\"ဇာတ်ကောင်သရုပ်\"}],\"plot\":\"ဇာတ်လမ်းအဓိကအကြောင်းအရာ အစမှအဆုံး\",\"chapters\":[{\"n\":1,\"title\":\"အခန်းခေါင်းစဉ်\",\"brief\":\"အခန်းအကြဉ်း\"}]}"
    }

    fun titlePrompt(idea: String, genre: String): String {
        val g = genreMy(genre)
        return "ဒီ မြန်မာဇာတ်လမ်းအိုင်ဒီယာအတွက် ဝတ္ထုခေါင်းစဉ်တစ်ခု သင့်လျော်စွာ ရွေးပေးပါ: " + idea + " (အမျိုးအစား: " + g + ") " +
            "Return STRICT JSON only: {\"title\":\"ခေါင်းစဉ်\"}"
    }

    fun chapterPrompt(idea: String, genre: String, brief: String, chapterTitle: String, n: Int, total: Int, lenDesc: String, prevTail: String, template: String = ""): String {
        val g = genreMy(genre)
        return "သင်သည် ကျွမ်းကျင်သည့် မြန်မာဝတ္ထုရေးဆရာကြီး ဖြစ်သည်။ " +
            templateLine(template) +
            "အောက်ပါ ဇာတ်လမ်းအိုင်ဒီယာအတွက် " + g + " အမျိုးအစား မြန်မာဝတ္ထုရဲ့ အခန်း " + n + " (စုစုပေါင်း " + total + " ခန်း) ကို ရေးပေးပါ။ " +
            "အိုင်ဒီယာ: " + idea + ". " +
            "ဒီအခန်းရဲ့ အကြဉ်း: " + brief + ". " +
            "အခန်းခေါင်းစဉ်: " + chapterTitle + ". " +
            "တစ်ခန်းလျှင် " + lenDesc + " ရှိစေပါ။ မြန်မာစာပေါင်း ရှုပ်ထွေးမပြီး ခံစားမှုပြည့်ဝစွာ ရေးပါ။ " +
            "ရေးထားပြီးသော အခန်းတွေကို ထပ်မရေးပါနဲ့ - နောက်ဆုံးရေးခဲ့တဲ့ အပိုင်းအနည်းငယ်: ..." + prevTail + "... " +
            "Markdown၊ ခေါင်းစဉ်နံပါတ်၊ ** စသည်တွေ မထည့်ပါနဲ့။ စာပိုဒ်ခွဲခြားတဲ့ စာသားသက်သက်သာ ပြန်ပေးပါ။"
    }

    fun rewritePrompt(prevTail: String, chapterText: String): String {
        return "သင်သည် မြန်မာဝတ္ထုရေးဆရာကြီး ဖြစ်သည်။ " +
            "အောက်ပါ ဝတ္ထုအခန်းကို ဇာတ်လမ်းအီးဗင်တွေ မထိခိုက်စေဘဲ စာပေါင်းအသုံးအနှုန်း ပိုကောင်းလာအောင် စိတ်လှုပ်စရာကောင်းတဲ့ ပုံစံအသစ်နဲ့ ပြန်ရေးပေးပါ။ " +
            "အရှည်အတိုင်းအတာကို မူရင်းနဲ့ ခန့်ညားစေပါ။ " +
            "ရေးထားပြီးသား ရှေ့အပိုင်းအနည်းငယ်: ..." + prevTail + "... " +
            "ပြန်ရေးမယ့် အခန်း:\n\n" + chapterText + "\n\n" +
            "Markdown မထည့်ပါနဲ့။ စာပိုဒ်စာသားသက်သက်သာ ပြန်ပေးပါ။"
    }

    fun expandPrompt(chapterText: String, lenDesc: String): String {
        return "သင်သည် မြန်မာဝတ္ထုရေးဆရာကြီး ဖြစ်သည်။ " +
            "အောက်ပါ ဝတ္ထုအခန်းကို မူရင်းဇာတ်လမ်းအီးဗင်တွေ အတူတူထားပြီး ပိုအရှည်ချဲ့ပေးပါ - ခံစားချက်၊ ပတ်ဝန်းကျင်၊ အတွေး၊ အပြုအမူတွေကို အသေးစိတ်ထည့်ပြီး ပို၍ " + lenDesc + " ဖြစ်စေပါ။\n\n" +
            chapterText + "\n\n" +
            "Markdown မထည့်ပါနဲ့။ စာပိုဒ်စာသားသက်သက်သာ ပြန်ပေးပါ။"
    }

    fun continuePrompt(idea: String, genre: String, brief: String, chapterTitle: String, n: Int, total: Int, lenDesc: String, prevTail: String): String {
        return chapterPrompt(idea, genre, brief, chapterTitle, n, total, lenDesc, prevTail)
    }

    fun cleanText(raw: String): String {
        var s = raw.trim()
        if (s.startsWith("```")) {
            val firstNl = s.indexOf("\n")
            if (firstNl > 0) s = s.substring(firstNl + 1)
            val endFence = s.lastIndexOf("```")
            if (endFence >= 0) s = s.substring(0, endFence)
        }
        s = s.replace("**", "")
        while (s.contains("\n\n\n")) s = s.replace("\n\n\n", "\n\n")
        return s.trim()
    }
}
