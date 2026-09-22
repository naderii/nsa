package ir.naderinia.nsa.util

data class ChangelogEntry(
    val versionCode: Int,
    val versionName: String,
    val changes: List<String>
)

object Changelog {
    // Ordered oldest → newest; the What's New screen reverses this for display.
    val entries = listOf(
        ChangelogEntry(1, "0.1.0", listOf(
            "ثبت یادآوری با تاریخ، ساعت و دسته‌بندی",
            "نوتیفیکیشن دقیق حتی وقتی اپ بسته است"
        )),
        ChangelogEntry(2, "0.2.0", listOf(
            "انتخاب طرف حساب از مخاطبین گوشی",
            "پیشنهاد خودکار دسته‌بندی‌های قبلی",
            "ثبت پرداخت جزئی برای یادآوری مالی"
        )),
        ChangelogEntry(3, "0.3.0", listOf("صفحه‌ی راه‌اندازی اولیه برای دسترسی‌های لازم")),
        ChangelogEntry(4, "0.4.0", listOf("مدیریت مالی: بدهی، طلب، چک، قسط، قبض")),
        ChangelogEntry(5, "0.5.0", listOf("داشبورد روزانه و مقایسه‌ی هزینه‌ی ماهانه")),
        ChangelogEntry(6, "0.6.0", listOf("طراحی جدید با فونت فارسی و پالت رنگی اختصاصی")),
        ChangelogEntry(7, "0.7.0", listOf(
            "قفل اپ با رمز و اثر انگشت",
            "ویجت صفحه اصلی",
            "اسکن رسید و قبض با دوربین"
        )),
        ChangelogEntry(8, "0.8.0", listOf("ناوبری با نوار پایین صفحه", "پیوست عکس یا سند به یادآوری")),
        ChangelogEntry(9, "0.9.0", listOf(
            "ویرایش یادآوری‌های ثبت‌شده",
            "رنگ‌بندی فوریت (قرمز/زرد/سبز)",
            "سرویس خودرو بر اساس کیلومتر"
        )),
        ChangelogEntry(10, "0.10.0", listOf("فرم هوشمند بر اساس نوع: سرویس خودرو، جلسه، ساختمان، مالی")),
        ChangelogEntry(11, "0.11.0", listOf("تقویم شمسی در همه‌جای اپ", "قالب تولد/سالگرد و دارو")),
        ChangelogEntry(12, "0.12.0", listOf(
            "قالب بارداری و نوزاد/کودک با پیشنهادهای ماهانه",
            "صفحه‌ی اصلی خلاصه‌شده با منوی دسته‌ها"
        )),
        ChangelogEntry(14, "0.13.0", listOf(
            "انتخاب تاریخ و ساعت شمسی در یک مرحله",
            "فیلد بانک برای چک و قسط، انواع قبض",
            "انتخاب آهنگ هشدار دلخواه"
        )),
        ChangelogEntry(15, "0.14.0", listOf(
            "رفع باگ ویجت که لود نمی‌شد",
            "تکرار هفتگی روی چند روز مشخص",
            "قالب مذهبی برای دعا و اعمال روزانه"
        )),
        ChangelogEntry(16, "0.15.0", listOf(
            "همین صفحه‌ای که الان می‌بینی — از این به بعد بعد از هر آپدیت میاد",
            "شماره‌ی نسخه از منوی «بیشتر» قابل دیدنه"
        )),
        ChangelogEntry(17, "0.15.1", listOf(
            "حذف مجوزهای غیرضروری دوربین و مخاطبین (طبق بررسی کافه‌بازار)"
        )),
        ChangelogEntry(18, "0.15.2", listOf(
            "از این نسخه به بعد، آپدیت اپ دیگه اطلاعاتت رو پاک نمی‌کنه"
        ))
    )

    fun since(lastSeenVersionCode: Int): List<ChangelogEntry> =
        entries.filter { it.versionCode > lastSeenVersionCode }.sortedByDescending { it.versionCode }

    val latest: ChangelogEntry get() = entries.maxByOrNull { it.versionCode }!!
}
