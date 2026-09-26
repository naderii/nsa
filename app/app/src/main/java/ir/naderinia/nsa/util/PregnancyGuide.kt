package ir.naderinia.nsa.util

/**
 * General, commonly-known pregnancy milestone reminders by month, meant as
 * a helpful starting checklist — not medical advice. The in-app text always
 * tells the person to confirm the exact schedule with their own doctor/ماما,
 * since it varies by person and clinic.
 */
object PregnancyGuide {
    val milestonesByMonth: Map<Int, List<String>> = mapOf(
        1 to listOf("شروع مصرف اسید فولیک", "اولین ویزیت پزشک یا ماما"),
        2 to listOf("آزمایش خون اولیه بارداری", "سونوگرافی تعیین سن بارداری"),
        3 to listOf("غربالگری سه‌ماهه اول (NT)", "مشاوره ژنتیک در صورت نیاز"),
        4 to listOf("غربالگری سه‌ماهه دوم (کوآد مارکر)"),
        5 to listOf("سونوگرافی آنومالی (بررسی سلامت و جنسیت جنین)"),
        6 to listOf("تست دیابت بارداری (OGTT)", "واکسن آنفولانزا (در فصل مناسب)"),
        7 to listOf("شروع کلاس آمادگی زایمان", "ویزیت‌های منظم‌تر پزشک"),
        8 to listOf("واکسن Tdap (کزاز و سیاه‌سرفه)", "آماده کردن ساک بیمارستان"),
        9 to listOf("ویزیت‌های هفتگی پزشک", "آماده‌سازی نهایی برای زایمان")
    )

    const val disclaimer = "این‌ها یادآوری‌های عمومی‌اند، برنامه‌ی دقیق رو حتماً با پزشک یا ماما هماهنگ کن."
}
