package ir.naderinia.nsa.util

import ir.naderinia.nsa.data.FinancialType
import ir.naderinia.nsa.data.Reminder
import ir.naderinia.nsa.data.RepeatInterval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Tests for the pure-Kotlin parts of the assistant: [QuickParser], [IranHolidays], [AskEngine].
 * "Now" is fixed to Saturday 25 Mehr 1405 (2026-10-17) 10:00 local time so results never drift.
 */
class AssistantEnginesTest {

    private val now: Long = Calendar.getInstance().apply { clear(); set(2026, 9, 17, 10, 0, 0) }.timeInMillis

    private fun j(m: Int, d: Int, h: Int = 9, min: Int = 0, y: Int = 1405) = JalaliCalendar.jalaliToMillis(y, m, d, h, min)

    private fun parse(s: String) = QuickParser.parse(s, now)!!

    // ───────────── QuickParser ─────────────

    @Test fun meetingTomorrowAfternoon() {
        val p = parse("فردا ساعت ۵ عصر جلسه با آقای احمدی")
        assertEquals("جلسات", p.category)
        assertEquals(j(7, 26, 17), p.triggerAtMillis)
        assertEquals("جلسه با آقای احمدی", p.title)
        assertFalse(p.timeInferred)
    }

    @Test fun chequeWithAmountDateAndPayee() {
        val p = parse("چک ۴۰ میلیون ۲۰ آذر به نام رضایی")
        assertEquals(FinancialType.CHECK, p.financialType)
        assertEquals(40_000_000L, p.amount)
        assertEquals("رضایی", p.counterparty)
        assertEquals(j(9, 20, 9), p.triggerAtMillis)
        assertEquals("مالی", p.category)
    }

    @Test fun installmentPlan() {
        val p = parse("قسط ۱۲ میلیونی بانک ملی ۱۲ قسط از ۲۰ آذر")
        assertEquals(FinancialType.INSTALLMENT, p.financialType)
        assertEquals(12, p.installmentCount)
        assertEquals(12_000_000L, p.amount)
        assertEquals("ملی", p.bankName)
        assertEquals(RepeatInterval.NONE, p.repeatInterval)
    }

    @Test fun weeklyOnTwoDays() {
        val p = parse("هر شنبه و دوشنبه ساعت ۶ عصر کلاس")
        assertEquals(RepeatInterval.WEEKLY, p.repeatInterval)
        assertEquals("2,7", p.repeatDaysOfWeek)
        // today is Saturday 10:00, so the first one is today 18:00
        assertEquals(j(7, 25, 18), p.triggerAtMillis)
    }

    @Test fun debtWithNumberWords() {
        val p = parse("دو میلیون و پانصد هزار تومن باید به علی بدم تا آخر ماه")
        assertEquals(FinancialType.DEBT, p.financialType)
        assertEquals(2_500_000L, p.amount)
        assertEquals("علی", p.counterparty)
        assertEquals(j(7, 30, 9), p.triggerAtMillis)
    }

    @Test fun creditFromSomeone() {
        val p = parse("از رضا ۳ میلیون طلب دارم ۱۰ آذر")
        assertEquals(FinancialType.CREDIT, p.financialType)
        assertEquals(3_000_000L, p.amount)
        assertEquals("رضا", p.counterparty)
    }

    @Test fun vehicleMileage() {
        val p = parse("تعویض روغن ماشین ۵۰ هزار کیلومتر")
        assertEquals("وسیله نقلیه", p.category)
        assertEquals(50_000L, p.mileageTargetKm)
        assertNull(p.amount)
    }

    @Test fun persianDigitsAndTonight() {
        val p = parse("امشب ساعت ۹ دعای کمیل")
        assertEquals(j(7, 25, 21), p.triggerAtMillis)
        assertEquals("مذهبی", p.category)
    }

    @Test fun inferredAfternoonHourCanBeFlagged() {
        val p = parse("سه شنبه آینده وقت دندانپزشک ساعت ۴")
        assertEquals(j(7, 28, 16), p.triggerAtMillis)
        assertTrue(p.timeInferred)
    }

    @Test fun birthdayWithBirthYear() {
        val p = parse("تولد علی ۱۳۷۰/۰۵/۱۲")
        assertEquals(1370, p.birthYear)
        assertEquals(RepeatInterval.YEARLY, p.repeatInterval)
        assertEquals(j(5, 12, 9, 0, 1406), p.triggerAtMillis)
    }

    @Test fun criticalWordSetsFlag() {
        assertTrue(parse("قبض برق ۳۵۰ هزار تومن ۲۵ مهر حتما").critical)
    }

    @Test fun unsupportedRepeatIsReportedNotSilentlyDropped() {
        val p = parse("هر ۱۰ روز بنزین بزنم")
        assertEquals(RepeatInterval.NONE, p.repeatInterval)
        assertTrue(p.warnings.any { it.contains("پشتیبانی نمی‌شه") })
    }

    @Test fun blankInputGivesNothing() {
        assertNull(QuickParser.parse("   ", now))
    }

    @Test fun questionDetection() {
        assertTrue(QuickParser.looksLikeQuestion("این ماه چقدر قسط دارم؟"))
        assertTrue(QuickParser.looksLikeQuestion("چک های این هفته"))
        assertTrue(QuickParser.looksLikeQuestion("فردا تعطیله؟"))
        assertFalse(QuickParser.looksLikeQuestion("فردا ساعت ۵ جلسه"))
    }

    // ───────────── IranHolidays ─────────────

    @Test fun officialHolidays1405() {
        assertEquals(IranHolidays.Kind.HOLIDAY, IranHolidays.status(j(1, 1, 12)).kind)
        assertEquals(IranHolidays.Kind.HOLIDAY, IranHolidays.status(j(10, 2, 12)).kind)
        assertEquals(IranHolidays.Kind.HOLIDAY, IranHolidays.status(j(12, 19, 12)).kind)
        assertFalse(IranHolidays.status(j(10, 2, 12)).approximate)
    }

    @Test fun fridayAndThursday() {
        assertEquals(IranHolidays.Kind.FRIDAY, IranHolidays.status(j(9, 20, 12)).kind) // 20 آذر 1405 = Friday
        assertEquals(IranHolidays.Kind.THURSDAY, IranHolidays.status(j(7, 30, 12)).kind)
        assertFalse(IranHolidays.isBankDay(j(7, 30, 12)))
        assertTrue(IranHolidays.isBankDay(j(7, 26, 12)))
    }

    @Test fun movingBackToBankDay() {
        val before = IranHolidays.previousBankDay(j(9, 20, 9))
        assertEquals(JalaliCalendar.millisToJalali(j(9, 18, 9)), JalaliCalendar.millisToJalali(before))
        assertNull(IranHolidays.dueWarning(j(7, 26, 9)))
        assertNotNull(IranHolidays.dueWarning(j(10, 2, 9)))
    }

    @Test fun solarHolidaysAreExactInAnyYear() {
        assertEquals(IranHolidays.Kind.HOLIDAY, IranHolidays.status(JalaliCalendar.jalaliToMillis(1410, 11, 22, 12, 0)).kind)
        assertFalse(IranHolidays.status(JalaliCalendar.jalaliToMillis(1410, 11, 22, 12, 0)).approximate)
    }

    @Test fun religiousHolidaysOfUnlistedYearsAreFlaggedApproximate() {
        val ups = IranHolidays.upcoming(JalaliCalendar.jalaliToMillis(1406, 2, 1, 12, 0), 1)
        assertEquals(1, ups.size)
        assertTrue(ups[0].second.approximate)
    }

    // ───────────── AskEngine ─────────────

    private val reminders = listOf(
        Reminder(id = 1, title = "قسط ماشین", triggerAtMillis = j(7, 28), financialType = FinancialType.INSTALLMENT, amount = 12_000_000),
        Reminder(id = 2, title = "قسط وام مسکن", triggerAtMillis = j(7, 30), financialType = FinancialType.INSTALLMENT, amount = 8_000_000),
        Reminder(id = 3, title = "چک رضایی", triggerAtMillis = j(8, 1), financialType = FinancialType.CHECK, amount = 40_000_000),
        Reminder(id = 4, title = "قبض برق", triggerAtMillis = j(7, 20), financialType = FinancialType.BILL, amount = 350_000),
        Reminder(id = 5, title = "جلسه با احمدی", triggerAtMillis = j(7, 25, 15), category = "جلسات")
    )

    @Test fun monthlyInstallmentTotal() {
        val a = AskEngine.answer("این ماه چقدر قسط دارم؟", reminders, now)
        assertTrue(a.headline, a.headline.contains("۲۰ میلیون"))
        assertEquals(listOf(1L, 2L), a.reminderIds)
    }

    @Test fun overdueBills() {
        val a = AskEngine.answer("قبض های عقب افتاده", reminders, now)
        assertEquals(listOf(4L), a.reminderIds)
    }

    @Test fun tomorrowIsNotAHoliday() {
        assertTrue(AskEngine.answer("فردا تعطیله؟", reminders, now).headline.startsWith("نه"))
    }

    @Test fun todayAgenda() {
        val a = AskEngine.answer("امروز چی دارم؟", reminders, now)
        assertEquals(listOf(5L), a.reminderIds)
    }

    @Test fun unknownQuestionIsNotPretended() {
        assertFalse(AskEngine.answer("سلام خوبی", reminders, now).understood)
    }
}
