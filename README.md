<div align="center">

<img src="screenshots/banner/banner.gif" alt="APK Qo‘riqchi AI" width="100%">

# APK Qo‘riqchi AI

**Avval tekshiring, keyin o‘rnating.**

APK fayllar, havolalar va telefonga o‘rnatilgan ilovalarni VirusTotal, Google Safe Browsing va qurilmadagi tahlil orqali tekshiradigan Android ilova. Natijani Gemini AI oddiy tilda tushuntiradi.

[![Yuklab olish](https://img.shields.io/github/v/release/Feruzbek0318/apk-qoriqchi-ai?label=APK%20yuklab%20olish&color=1e6bff)](https://github.com/Feruzbek0318/apk-qoriqchi-ai/releases/latest)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3ddc84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7f52ff?logo=kotlin&logoColor=white)
![FastAPI](https://img.shields.io/badge/Backend-FastAPI-009688?logo=fastapi&logoColor=white)
![Tillar](https://img.shields.io/badge/til-UZ%20%C2%B7%20RU%20%C2%B7%20EN-1e6bff)
[![Litsenziya: MIT](https://img.shields.io/badge/litsenziya-MIT-lightgrey)](LICENSE)

</div>

> Ilova Google Play Protect o‘rnini bosmaydi. U qaror qabul qilishga yordam beradi: dalillarni ko‘rsatadi va xavf darajasini tushuntiradi.

## Nima qiladi

| | |
|---|---|
| 📦 **APK tekshirish** | Faylni tanlaysiz — ilova imzosi (v1–v4), ruxsatlar, paket ma’lumotlari va SHA-256 tekshiriladi. Xesh VirusTotal bazasida qidiriladi; topilmasa, faqat roziligingiz bilan yuklanadi. |
| 🔗 **Havola tekshirish** | Havola ochilmaydi. U Google Safe Browsing va VirusTotal’da tekshiriladi, soxta domenlar (masalan, `paypa1-login.xyz`) qurilmaning o‘zida aniqlanadi. |
| 📱 **O‘rnatilgan ilovalar** | Telefondagi ilovalarning xavfli ruxsatlari, maxsus ruxsatlari va qayerdan o‘rnatilgani ko‘rib chiqiladi. |
| ✨ **AI izohi** | Gemini natijani o‘zbek, rus yoki ingliz tilida tushuntiradi. Xavf darajasini esa AI emas, testlangan qoidalar belgilaydi — AI uni o‘zgartira olmaydi. |
| 🔑 **O‘z API kalitingiz** | Ilovada tayyor kalitlar yo‘q. Har bir foydalanuvchi o‘z bepul kalitlarini kiritadi; ular telefonda shifrlangan holda saqlanadi va faqat o‘z xizmatiga yuboriladi. |
| 🕘 **Tarix va mavzular** | Barcha tekshiruvlar tarixi, yorug‘ / qorong‘i / tizim mavzusi, bildirishnomalar. |

Kalitsiz ham ilova ishlaydi: qurilmadagi tekshiruvlar bajariladi, lekin natija **«Noma’lum»** yoki «faqat mahalliy tekshiruv» deb belgilanadi — hech qachon «toza» deb emas.

## Ekran rasmlari

| Bosh sahifa | Fishing havola | AI izohi | APK hisoboti |
|:-:|:-:|:-:|:-:|
| <img src="screenshots/13_home.png" width="200"> | <img src="screenshots/06_url_phishing.png" width="200"> | <img src="screenshots/08_ai_explanation.png" width="200"> | <img src="screenshots/10_apk_report.png" width="200"> |

| Aniqlangan belgilar | Ilovalar auditi | API kalitlari | Qorong‘i mavzu |
|:-:|:-:|:-:|:-:|
| <img src="screenshots/11_apk_findings.png" width="200"> | <img src="screenshots/12_installed_apps.png" width="200"> | <img src="screenshots/04_settings_api_keys.png" width="200"> | <img src="screenshots/15_home_dark.png" width="200"> |

Barcha 16 ta rasm: [screenshots/README.md](screenshots/README.md)

## Yuklab olish va o‘rnatish

1. [Releases](https://github.com/Feruzbek0318/apk-qoriqchi-ai/releases/latest) sahifasidan `APK-Qoriqchi-AI-1.0.0.apk` faylini yuklab oling.
2. Telefonda faylni oching va «Noma’lum manbalardan o‘rnatish»ga ruxsat bering.
3. Ilovada **Sozlamalar → API kalitlari** bo‘limiga o‘z kalitlaringizni kiriting (pastga qarang).

Talab: Android 8.0 (API 26) yoki yangiroq.

> Bu sinov (debug) kaliti bilan imzolangan versiya. Google Play’ga chiqarish uchun alohida kalit bilan imzolash kerak — [ApkQoriqchi/README.md](ApkQoriqchi/README.md#release-build-and-signing).

## API kalitlarini olish

Hammasi bepul. Kalitlar repozitoriyda ham, ilovada ham yo‘q — har kim o‘zinikini kiritadi.

| Xizmat | Qayerdan olinadi | Nima uchun |
|---|---|---|
| **VirusTotal** | [virustotal.com](https://www.virustotal.com) → profil → *API key* | APK va havolalarni 70+ antivirusda tekshirish (bepul: 4 so‘rov/daqiqa, 500/kun) |
| **Google Safe Browsing** | [Google Cloud Console](https://console.cloud.google.com) → *Safe Browsing API* → kalit yaratish | Fishing va zararli saytlarni aniqlash |
| **Google Gemini** *(ixtiyoriy)* | [aistudio.google.com](https://aistudio.google.com/apikey) → *Get API key* | Natijani oddiy tilda tushuntirish |

Bepul tariflar faqat notijorat foydalanish uchun. Gemini’ning bepul tarifida Google yuborilgan ma’lumotlardan o‘z xizmatlarini yaxshilash uchun foydalanishi mumkin — bu ilovaning maxfiylik matnida ham yozilgan.

## Loyiha tuzilishi

```
ApkQoriqchi/    Android ilova — Kotlin, Jetpack Compose, Material 3, Hilt, Room
backend/        Ixtiyoriy server — FastAPI, PostgreSQL/SQLite, Alembic (kalitlar serverda qoladi)
screenshots/    Ekran rasmlari, banner (PNG, GIF, interaktiv HTML) va logotip
dizayn/         UI maketlari va logotipning asl fayli
```

### Android ilovani yig‘ish

Android Studio (JBR 21+) va Android SDK 37 kerak.

```bash
cd ApkQoriqchi
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:testDebugUnitTest     # 91 ta test
./gradlew :app:assembleDebug         # app/build/outputs/apk/debug/app-debug.apk
```

Batafsil (arxitektura, release imzosi, Google Play talablari): [ApkQoriqchi/README.md](ApkQoriqchi/README.md)

### Backend (ixtiyoriy)

Kalitlarni foydalanuvchiga emas, serverga qo‘ymoqchi bo‘lsangiz:

```bash
cd backend
python3.10 -m venv .venv && .venv/bin/pip install -e ".[dev]"
cp .env.example .env               # kalitlarni shu yerga yozing
.venv/bin/python -m pytest -q      # 104 ta test
.venv/bin/uvicorn app.main:app --reload --port 8000   # http://localhost:8000/docs
```

Kalitsiz sinash uchun `.env` da `PROVIDER_FIXTURE_MODE=true` qo‘ying. Batafsil: [backend/README.md](backend/README.md)

## Xavfsizlik va maxfiylik

- Xavf darajasini faqat sof Kotlin’dagi, testlangan qoidalar (`VerdictPolicy`, `RiskEngine`, `UrlVerdict`, `AppRiskScorer`) belgilaydi. AI faqat tushuntiradi.
- Havolalar hech qachon ochilmaydi; APK fayl VirusTotal’ga faqat foydalanuvchi roziligi bilan yuklanadi.
- API kalitlari Android Keystore bilan shifrlanadi.
- Hujjatlar: [holat va test natijalari](ApkQoriqchi/docs/STATUS.md) · [tahdid modeli](ApkQoriqchi/docs/THREAT_MODEL.md) · [maxfiylik siyosati (shablon)](ApkQoriqchi/docs/PRIVACY_POLICY.md) · [backend API](ApkQoriqchi/docs/openapi.json)

## Muallif

Feruzbek Namozboyev — [Telegram](https://t.me/Namozboyev_Feruzbek) · [YouTube](https://www.youtube.com/@feruzbek_namozboyev) · [GitHub](https://github.com/Feruzbek0318)

## Litsenziya

[MIT](LICENSE). VirusTotal, Google Safe Browsing va Gemini — o‘z egalarining xizmatlari; ulardan foydalanish ularning shartlariga bo‘ysunadi.
