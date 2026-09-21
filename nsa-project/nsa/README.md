# یادآور من (nsa)

اپلیکیشن اندرویدی برای ثبت و یادآوری هوشمند کارها، قسط‌ها، بدهی‌ها، سرویس خودرو، بارداری، دارو و کارهای دوره‌ای — با تقویم شمسی، بدون نیاز به اینترنت یا سرویس ابری.

## ویژگی‌ها

- یادآوری با تاریخ/ساعت شمسی، تکرار (روزانه/هفتگی چندروزه/ماهانه/سالانه)
- فرم هوشمند بر اساس نوع: عمومی، سرویس خودرو (با کیلومتر)، جلسه، ساختمان، مالی (بدهی/طلب/چک/قسط/قبض با بانک و طرف‌حساب)، تولد و سالگرد، دارو، بارداری، نوزاد و کودک، مذهبی
- مدیریت مالی و داشبورد روزانه با مقایسه‌ی هزینه‌ی ماهانه
- پیوست عکس/سند، اسکن رسید با OCR آفلاین (ML Kit)
- قفل اپ با رمز/اثر انگشت، ویجت صفحه اصلی، انتخاب آهنگ هشدار
- «تازه‌های اپ» خودکار بعد از هر آپدیت
- کاملاً آفلاین — هیچ داده‌ای از گوشی خارج نمی‌شه

## نقشه راه باقی‌مانده

- [ ] ورودی صوتی (Speech-to-Text)
- [ ] Migration واقعی دیتابیس به‌جای `fallbackToDestructiveMigration` (باید قبل از انتشار عمومی انجام بشه — نگاه کن به `RELEASE.md`)
- [ ] یادآوری چندمرحله‌ای (Snooze)

## فونت

متن‌های فارسی اپ با فونت **[Vazirmatn](https://github.com/rastikerdar/vazirmatn)** رندر می‌شن (لایسنس SIL Open Font License 1.1 — رایگان و آزاد برای استفاده تجاری، فایل کامل لایسنس در `VAZIRMATN_LICENSE.txt`).

## معماری فنی

- **زبان:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **معماری:** MVVM (`ReminderViewModel` + `StateFlow`)
- **ذخیره‌سازی محلی:** Room Database
- **زمان‌بندی نوتیفیکیشن:** `AlarmManager.setExactAndAllowWhileIdle` (نه WorkManager — چون برای یادآوری‌های دقیق زمانی مناسب‌تر است)
- **حداقل نسخه اندروید:** API 26 (Android 8.0)

## راه‌اندازی محیط توسعه

پیش‌نیازها: JDK 17، Android SDK Command Line Tools (نصب کامل Android Studio ضروری نیست).

```bash
git clone <آدرس ریپوی شما روی GitLab>
cd nsa
```

### نکته مهم درباره JDK

فایل `gradle.properties` مسیر JDK 17 را به‌صورت مستقیم (`/usr/lib/jvm/java-17-openjdk-amd64`) پین کرده تا این پروژه مستقل از نسخه‌ی پیش‌فرض جاوای سیستم (که ممکن است جدیدتر باشد) کار کند. اگر مسیر جاوای ۱۷ روی سیستم شما فرق دارد، این مقدار را در `gradle.properties` اصلاح کنید. برای پیدا کردن مسیر:

```bash
update-alternatives --list java
```

### تولید Gradle Wrapper

به دلایل فنی (باینری jar)، خود wrapper (`gradlew`, `gradlew.bat`, `gradle-wrapper.jar`) در این ریپو ساخته نشده. یک‌بار این دستور را بزنید (اگر gradle به‌صورت مستقل نصب دارید، وگرنه `sudo apt install gradle` یا از طریق sdkman):

```bash
gradle wrapper --gradle-version 8.9 --distribution-type bin
```

این دستور فایل‌های لازم را می‌سازد و از همان `gradle-wrapper.properties` موجود در ریپو استفاده می‌کند.

### بیلد Debug

```bash
./gradlew assembleDebug
```

فایل APK در مسیر `app/build/outputs/apk/debug/` ساخته می‌شود. برای نصب مستقیم روی گوشی وصل‌شده با USB (با حالت توسعه‌دهنده فعال):

```bash
./gradlew installDebug
```

### بیلد Release (برای انتشار)

راهنمای کامل ساخت keystore، AAB برای Google Play، و APK امضاشده در `RELEASE.md` هست. فهرست کتابخونه‌های متن‌باز و لایسنس‌هاشون هم در `THIRD_PARTY_NOTICES.md`.

## ساختار پروژه

```
app/src/main/java/ir/naderinia/nsa/
├── data/            # Entity، DAO، Room Database
├── notification/    # AlarmManager scheduler، BroadcastReceiver ها، آهنگ هشدار
├── widget/          # ویجت صفحه اصلی
├── ui/              # ViewModel، صفحات Compose، تم، کامپوننت‌های مشترک
├── util/            # تقویم شمسی، امنیت، تنظیمات ماشین، راهنمای بارداری/نوزاد، Changelog
└── MainActivity.kt
```

## CI/CD

پایپ‌لاین `.gitlab-ci.yml` روی هر push به `main`/`develop` یا هر Merge Request، پروژه را بیلد و lint می‌کند و APK دیباگ را به‌عنوان artifact نگه می‌دارد.
