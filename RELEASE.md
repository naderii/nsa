# راهنمای ساخت نسخه‌ی انتشار (Release)

## ۱. ساخت Keystore (فقط یک‌بار، برای همیشه نگهش دار)

⚠️ **این فایل و رمزهاش رو گم نکن.** اگه گمش کنی، دیگه هیچ‌وقت نمی‌تونی همین اپ رو آپدیت کنی روی گوشی‌های کاربرا یا Google Play — باید اپ کاملاً جدیدی منتشر کنی.

```bash
keytool -genkeypair -v \
  -keystore nsa-release.jks \
  -alias nsa \
  -keyalg RSA -keysize 2048 -validity 10000
```

چندتا سوال می‌پرسه (اسم، سازمان، کشور و...) — هرچی خواستی بزن، مهم نیست دقیق باشه. توی این پروسه ازت دو تا رمز عبور می‌خواد (رمز خود keystore، و رمز کلید) — این‌ها رو یه جای امن (پسورد منیجر) ذخیره کن.

فایل `nsa-release.jks` رو یه‌جای امن نگه دار (نه توی گیت‌هاب، نه هیچ‌جای عمومی).

## ۲. ساخت `keystore.properties`

توی ریشه‌ی پروژه (کنار `settings.gradle.kts`) یه فایل به اسم `keystore.properties` بساز:

```properties
storeFile=/مسیر/کامل/به/nsa-release.jks
storePassword=رمز_keystore
keyAlias=nsa
keyPassword=رمز_کلید
```

این فایل توی `.gitignore` هست و هیچ‌وقت commit نمی‌شه — دقیقاً همون‌جوری که باید باشه.

## ۳. ساخت فایل AAB (برای Google Play)

```bash
./gradlew bundleRelease
```

خروجی: `app/build/outputs/bundle/release/app-release.aab` — همینو آپلود می‌کنی توی Play Console.

## ۴. ساخت APK امضاشده (برای بازارهای دیگه مثل کافه‌بازار یا نصب مستقیم)

```bash
./gradlew assembleRelease
```

خروجی: `app/build/outputs/apk/release/app-release.apk`

## ۵. کاهش حجم برنامه

از قبل فعال کردیم:
- **R8** (`isMinifyEnabled = true`) — کد استفاده‌نشده رو حذف و کد رو فشرده می‌کنه
- **Resource shrinking** (`isShrinkResources = true`) — فایل‌های منبع استفاده‌نشده رو حذف می‌کنه

بزرگ‌ترین بخش حجم اپ احتمالاً مدل آفلاین ML Kit (برای اسکن رسید) هست. اگه یه روز حجم اپ مهم‌تر از سرعت/آفلاین‌بودن OCR شد، می‌شه به نسخه‌ی Google Play Services مدل (که هنگام نیاز دانلود می‌شه، نه از اول توی APK) سوییچ کرد — بگو اگه خواستی این کارو انجام بدیم.

## ۶. لایسنس‌ها (برای انتشار رایگان)

- کد خود پروژه: MIT License (فایل `LICENSE`) — کاملاً رایگان و آزاده.
- فونت Vazirmatn: SIL Open Font License (فایل `VAZIRMATN_LICENSE.txt`) — رایگان، حتی برای استفاده‌ی تجاری.
- کتابخونه‌های بیرونی: همه‌شون Apache 2.0 یا مشابه‌ش هستن، هیچ‌کدوم هزینه ندارن. فهرست کامل توی `THIRD_PARTY_NOTICES.md`.

هیچ‌کدوم از این‌ها مانع انتشار رایگان اپ نمی‌شن.

## چک‌لیست قبل از انتشار

- [ ] `applicationId` نهاییه: `ir.naderinia.nsa` ✅ (از قبل همینه)
- [ ] `versionCode` رو هر بار که آپدیت می‌دی، ببر بالا (از قبل داریم مدیریتش می‌کنیم)
- [ ] `keystore.properties` رو ساختی و `bundleRelease`/`assembleRelease` رو تست کردی
- [ ] یه‌بار اپ release رو نصب کن و کامل تست کن (R8 بعضی‌وقتا رفتار متفاوتی از debug داره)
- [ ] از خودت مطمئن شو که دیتابیس migration داره (نه `fallbackToDestructiveMigration`) قبل از انتشار عمومی — این خیلی مهمه، پایین‌تر توضیح دادم
