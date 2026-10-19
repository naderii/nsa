# نسخه 0.25.2 — کم کردن حجم (DB v13 — بدون تغییر ساختار)

R8 و shrinkResources از قبل روی release روشن بودن؛ این نسخه سراغ چیزهایی رفت که R8 نمی‌تونه کم کنه:

- **فقط CPUهای واقعی گوشی**: `abiFilters = arm64-v8a + armeabi-v7a`. کتابخونه‌ی native ML Kit برای x86/x86_64 (شبیه‌ساز) دیگه توی APK نیست.
- **فقط زبان fa و en** (`androidResources.localeFilters`): رشته‌های ترجمه‌شده‌ی بقیه‌ی زبان‌ها توی کتابخونه‌ها حذف شد.
- **حذف Coil** (و OkHttp، Okio، AppCompat-resources که با خودش می‌آورد): جاش `util/LocalImage.kt` (حدود ۶۰ خط) برای نمایش عکس پیوست و پیش‌نمایش رسید، با کم‌کردن ابعاد و چرخش EXIF.
- **فونت Vazirmatn سبک‌شده**: فقط محدوده‌ی عربی/فارسی، لاتین پایه و علائم نگارشی؛ ~۱۲۳KB → ~۷۳KB برای هر وزن. شکل‌دهی متن با HarfBuzz روی ۳۶ جمله‌ی آزمایشی قبل و بعد دقیقاً یکسان بود.
- **کتابخونه‌های native فشرده داخل APK** (`useLegacyPackaging = true`): فایل APK کوچک‌تر، حجم نصب‌شده کمی بیشتر.
- فایل‌های متادیتای بی‌مصرف از APK بیرون موند.
- حذف کد بی‌استفاده: `ReminderDao.observeUpcoming`، `PermissionStatus.allEssentialGranted`، `Changelog.latest`، و پوشه‌ی خالی اشتباهی `res/{values,values-fa,...}`.

- **پین کردن `exifinterface:1.3.7`**: ML Kit نسخه‌ی قدیمی 1.0.0 رو می‌خواد و تا قبل از این، Coil ناخواسته به 1.3.7 ارتقاش می‌داد.

## عمداً دست نخورد
- `security-crypto` (Tink): رمز قفل با اون ذخیره می‌شه؛ تعویضش رمز کاربرها رو بی‌اعتبار می‌کنه.
- قانون `-keep` روی پکیج `data`: نام enumها و فیلدها توی پشتیبان/بازیابی استفاده می‌شن.
- ML Kit (اسکن رسید): بزرگ‌ترین بخش حجمه؛ حذفش حذف یه قابلیته و تصمیمش با خودته.
- `PregnancySuggestions` / `BabyCareSuggestions` (SuggestionData): استفاده نمی‌شن ولی محتواست نه کد؛ R8 خودش حذفشون می‌کنه.

versionCode 34 / versionName 0.25.2
