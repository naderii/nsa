package ir.naderinia.nsa.data

/** dayOffset is relative to a base date the person provides (pregnancy start / baby's birth date). */
data class SuggestedMilestone(val title: String, val note: String, val dayOffset: Int)

object PregnancySuggestions {
    const val DISCLAIMER = "این‌ها فقط نقاط عطف کلی و عمومی‌ان، نه برنامه‌ی پزشکی دقیق. حتماً زمان‌بندی واقعی رو با پزشک یا ماما خودت هماهنگ کن."

    val items = listOf(
        SuggestedMilestone("اولین ویزیت بارداری", "شروع مراقبت‌های دوران بارداری با پزشک یا ماما", 14),
        SuggestedMilestone("آزمایش‌های پایه بارداری", "آزمایش خون و بررسی‌های اولیه", 56),
        SuggestedMilestone("غربالگری سه‌ماهه اول", "هماهنگ با پزشک برای زمان دقیق سونوگرافی و آزمایش", 77),
        SuggestedMilestone("غربالگری سه‌ماهه دوم", "", 112),
        SuggestedMilestone("سونوگرافی آنومالی (بررسی رشد و ارگان‌ها)", "", 140),
        SuggestedMilestone("تست قند خون بارداری", "", 182),
        SuggestedMilestone("شروع ویزیت‌های مکررتر", "طبق نظر پزشک", 196),
        SuggestedMilestone("آماده کردن ساک بیمارستان", "", 252),
        SuggestedMilestone("تاریخ تخمینی زایمان", "فقط یه تخمینه — تاریخ دقیق رو پزشکت مشخص می‌کنه", 280)
    )
}

object BabyCareSuggestions {
    const val DISCLAIMER = "این‌ها یادآوری‌های عمومی‌ان، نه برنامه‌ی رسمی واکسیناسیون یا دستور پزشکی. حتماً برنامه‌ی دقیق چکاپ و واکسن رو با پزشک کودکت هماهنگ کن."

    val items = listOf(
        SuggestedMilestone("اولین معاینه نوزاد", "", 3),
        SuggestedMilestone("چکاپ دوماهگی", "شامل بررسی رشد و نوبت‌های واکسیناسیون طبق برنامه‌ی پزشک", 60),
        SuggestedMilestone("چکاپ چهارماهگی", "", 120),
        SuggestedMilestone("چکاپ شش‌ماهگی", "", 180),
        SuggestedMilestone("شروع تغذیه کمکی", "با نظر پزشک یا متخصص تغذیه کودک", 180),
        SuggestedMilestone("چکاپ نه‌ماهگی", "", 270),
        SuggestedMilestone("چکاپ یک‌سالگی", "", 365)
    )
}
