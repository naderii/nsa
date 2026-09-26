package ir.naderinia.nsa.util

/**
 * General, commonly-known infant care/vaccination milestones by age in
 * months — a helpful starting checklist, not medical advice. Vaccination
 * schedules vary by country and can change, so the UI always tells the
 * person to confirm with their pediatrician.
 */
object BabyCareGuide {
    val milestonesByMonth: Map<Int, List<String>> = mapOf(
        0 to listOf("واکسن BCG و هپاتیت B (بدو تولد)", "تست غربالگری نوزادان (PKU و تیروئید)", "ویزیت اولیه پزشک اطفال"),
        2 to listOf("واکسن پنتاوالان و فلج اطفال (نوبت اول)"),
        4 to listOf("واکسن پنتاوالان و فلج اطفال (نوبت دوم)"),
        6 to listOf("واکسن پنتاوالان و فلج اطفال (نوبت سوم)", "شروع تغذیه کمکی"),
        9 to listOf("چکاپ رشد و تکامل"),
        12 to listOf("واکسن MMR — سرخک، سرخجه، اوریون (نوبت اول)"),
        15 to listOf("واکسن یادآور"),
        18 to listOf("واکسن یادآور پنتاوالان و فلج اطفال"),
        24 to listOf("چکاپ دندان‌پزشکی اولیه", "ارزیابی رشد زبان و گفتار")
    )

    const val disclaimer = "این‌ها یادآوری‌های عمومی‌اند، برنامه‌ی دقیق واکسیناسیون رو حتماً با پزشک اطفال هماهنگ کن."
}
