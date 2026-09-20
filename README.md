# یادآور من (nsa)

اپلیکیشن اندرویدی برای ثبت و یادآوری هوشمند کارها، قسط‌ها، بدهی‌ها و کارهای دوره‌ای (مثل عوض کردن روغن ماشین).

## وضعیت پروژه

**فاز ۱ (در حال ساخت):** هسته‌ی محکم و قابل‌اعتماد
- [x] افزودن یادآوری (متن)
- [x] تاریخ/ساعت + تکرار (روزانه/هفتگی/ماهانه/سالانه)
- [x] دسته‌بندی دلخواه
- [x] نوتیفیکیشن دقیق با AlarmManager (کار می‌کند حتی وقتی اپ بسته است)
- [x] بازبرنامه‌ریزی خودکار یادآوری‌ها بعد از ریبوت گوشی
- [x] درخواست معافیت از بهینه‌سازی باتری (برای گوشی‌های شیائومی/سامسونگ/هواوی)
- [x] زیرساخت مالی در مدل داده (مبلغ، طرف حساب) — آماده برای فاز ۲
- [ ] ورودی صوتی (Speech-to-Text)
- [ ] snooze و یادآوری چندمرحله‌ای

**فاز ۲ (بعدی):** هوشمندی و مالی — پرداخت جزئی، تشخیص زبان طبیعی تاریخ، پیوست عکس

**فاز ۳ (بعدی):** بکاپ ابری Firebase، ویجت صفحه اصلی

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

### بیلد

```bash
./gradlew assembleDebug
```

فایل APK در مسیر `app/build/outputs/apk/debug/` ساخته می‌شود. برای نصب مستقیم روی گوشی وصل‌شده با USB (با حالت توسعه‌دهنده فعال):

```bash
./gradlew installDebug
```

## ساختار پروژه

```
app/src/main/java/ir/naderinia/nsa/
├── data/            # Entity، DAO، Room Database
├── notification/    # AlarmManager scheduler، BroadcastReceiver ها
├── ui/              # ViewModel، صفحات Compose، تم
└── MainActivity.kt
```

## CI/CD

پایپ‌لاین `.gitlab-ci.yml` روی هر push به `main`/`develop` یا هر Merge Request، پروژه را بیلد و lint می‌کند و APK دیباگ را به‌عنوان artifact نگه می‌دارد.
