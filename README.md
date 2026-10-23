# G.I KO KO NOVEL CREATOR

**AI နဲ့ ဝတ္ထုဇာတ်လမ်းရေးပြီး Audiobook အဖြစ် ထုတ်ပေးတဲ့ Android App**

POWERED BY I KO KO

## Features (v0.0.1)

- NOVEL STUDIO - ဇာတ်အမျိုးအစား ရွေးချယ် (Romance / Action / Horror / Sci-Fi / Fantasy / Mystery / Historical / Drama / Comedy / Adventure)
- OUTLINE FIRST / DIRECT - ဇာတ်ကောင် + plot အရင်ဆွဲပြီး အခန်းတွေဆက်ရေးတဲ့ mode နဲ့ တိုက်ရိုက်ရေးတဲ့ mode (သုံးသူ ရွေးချယ်)
- NOVEL LIBRARY - ရေးပြီးသား ဝတ္ထုတွေ သိမ်း / ဖွင့် / ဖျက်
- CHAPTER TOOLS - ဆက်ရေး (CONTINUE) / ပြန်ရေး (REWRITE) / အရှည်တိုး (EXPAND)
- CUSTOM PROVIDER - Gemini / OpenRouter / Custom OpenAI-compatible URL + API key + Model + Free model စာရင်း auto ရယူ + API key connection test
- AUDIOBOOK - Edge TTS (မြန်မာအသံ NilarNeural / ThihaNeural) နဲ့ အခန်းတိုင်း MP3 ထုတ်၊ Built-in player (အရှိန်ချိန် + sleep timer)
- EXPORT - TXT export + share
- USAGE TRACKER - 7 ရက် AI သုံးစွဲမှု chart
- CYBER 3D THEME - Cyber grid animation + ရွှေပန်းကနုတ် border + Gold 3D logo

## Build

- Kotlin, programmatic UI, minSdk 24, targetSdk 34, okhttp 4.12
- CI: dev branch တိုင်း "Dev Build Check" run (assembleRelease)
- main branch တင်ရင် release CI - signed APK + GitHub Release (v0.0.1)

## Icon / Logo အစားထိုးနည်း (Termux)

    git clone https://github.com/gikoko611/GIkoko-Novel-creator.git
    cd GIkoko-Novel-creator

App Icon အစားထိုး

1. app/src/main/res/drawable/ic_launcher_background.xml နဲ့ ic_launcher_foreground.xml ကို ကိုယ်ပိုင် vector နဲ့ အစားထိုး
2. PNG နဲ့ထည့်ချင်ရင် - ic_launcher.png (192px) ကို mipmap-xxxhdpi စသည့် folder များတွင်ထည့်ပြီး mipmap-anydpi-v26/ic_launcher.xml ကို ဖျက်ပါ

Splash / Home Logo

- app/src/main/res/drawable-nodpi/img_logo.png အနေနဲ့ ကိုယ်ပိုင် logo ထည့်လျှင် app က အလိုအလျောက် အသုံးပြုသွားမည် (code-drawn 3D logo အစား)

    git add .
    git commit -m "custom logo"
    git push origin dev

## POWERED BY I KO KO
