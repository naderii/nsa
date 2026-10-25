package ir.naderinia.nsa.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Formats are copied from real SMS of each bank; every number (accounts, amounts, balances) and the
 * person's name were changed on purpose.
 * "Now" is fixed to Saturday 25 Mehr 1405 (2026-10-17) 10:00 so results never drift.
 */
class BankSmsParserTest {

    private val now: Long = Calendar.getInstance().apply { clear(); set(2026, 9, 17, 10, 0, 0) }.timeInMillis

    private fun j(m: Int, d: Int, h: Int, min: Int, y: Int = 1405) = JalaliCalendar.jalaliToMillis(y, m, d, h, min)

    private fun parse(s: String) = BankSmsParser.parse(s)!!

    // ───────────── Mehr ─────────────

    @Test fun mehrWithdrawal_withInvisibleDirectionMarks() {
        // The real message is wrapped in U+202A … U+202C and has a trailing space.
        val p = parse("\u202A243370010100000001\u202C \n550,000-\n1405/7/26-7:30\nمانده:30,225,745")
        assertEquals(BankKey.MEHR, p.bank)
        assertEquals(SmsTxKind.WITHDRAWAL, p.kind)
        assertEquals(550_000L, p.amountRial)
        assertEquals(30_225_745L, p.balanceRial)
        assertEquals("243370010100000001", p.accountId)
        assertEquals(SmsDateTime(1405, 7, 26, 7, 30), p.dateTime)
        assertFalse(p.needsReview)
    }

    @Test fun mehrDeposit() {
        val p = parse("243370010100000001\n50,000,000+\n1405/7/23-11:48\nمانده:51,435,745")
        assertEquals(SmsTxKind.DEPOSIT, p.kind)
        assertEquals(50_000_000L, p.amountRial)
        assertEquals(51_435_745L, p.balanceRial)
        assertEquals(SmsDateTime(1405, 7, 23, 11, 48), p.dateTime)
    }

    // ───────────── Melli ─────────────

    @Test fun melliTransferOut_arabicLettersAndNoYear() {
        // Real messages use Arabic ك / ي.
        val p = parse("بانك ملي ايران\nحواله پل:50,000,000-\nحساب:12345\nمانده:3,058,389,593\n0723-11:48")
        assertEquals(BankKey.MELLI, p.bank)
        assertEquals(SmsTxKind.WITHDRAWAL, p.kind)
        assertEquals(50_000_000L, p.amountRial)
        assertEquals("حواله پل", p.label)
        assertEquals("12345", p.accountId)
        assertEquals(3_058_389_593L, p.balanceRial)
        assertEquals(SmsDateTime(null, 7, 23, 11, 48), p.dateTime)
    }

    @Test fun melliSalaryDeposit() {
        val p = parse("بانك ملي ايران\nحقوق:497,436,836+\nحساب:12345\nمانده:4,129,893,345\n0618-13:30")
        assertEquals(SmsTxKind.DEPOSIT, p.kind)
        assertEquals("حقوق", p.label)
        assertEquals(497_436_836L, p.amountRial)
        assertEquals(SmsDateTime(null, 6, 18, 13, 30), p.dateTime)
    }

    @Test fun melliSecondAccountOfSameBank() {
        val p = parse("بانك ملي ايران\nانتقالي:50,000,000+\nحساب:67890\nمانده:60,115,490\n0712-13:13")
        assertEquals("67890", p.accountId)
        assertEquals("انتقالی", p.label)
    }

    // ───────────── Blu ─────────────

    @Test fun bluDeposit_persianDigits_nameNotKept() {
        val p = parse("بلو\nدریافت پل\n علی عزیز، 15,000,000 ریال به حساب شما نشست.\n موجودی: 39,514,210 ریال\n۱۰:۴۹\n۱۴۰۵.۰۷.۲۰")
        assertEquals(BankKey.BLU, p.bank)
        assertEquals(SmsTxKind.DEPOSIT, p.kind)
        assertEquals(15_000_000L, p.amountRial)
        assertEquals(39_514_210L, p.balanceRial)
        assertEquals("دریافت پل", p.label)
        assertNull(p.accountId)
        assertEquals(SmsDateTime(1405, 7, 20, 10, 49), p.dateTime)
        assertFalse(p.label!!.contains("علی"))
    }

    @Test fun bluRoundUp_isSavingsNotExpense() {
        val p = parse(
            "بلو\nبرداشت رُند\nعلی عزیز، 425,500 ریال از حساب شما پرید.\nو 74,500 ریال هم رند شد.\n" +
                "موجودی: 18,010,502 ریال\n۱۰:۱۲\n۱۴۰۵.۰۷.۲۳"
        )
        assertEquals(SmsTxKind.WITHDRAWAL, p.kind)
        assertEquals(425_500L, p.amountRial)
        assertEquals(74_500L, p.roundUpRial)
        assertEquals(18_010_502L, p.balanceRial)
        assertEquals("برداشت رند", p.label)
    }

    // ───────────── Mellat ─────────────

    @Test fun mellatWithdrawal_twoDigitYear() {
        val p = parse("حساب1158000001\nبرداشت900,000,000\nمانده60,153,689\n05/03/12-17:47")
        assertEquals(BankKey.MELLAT, p.bank)
        assertEquals(SmsTxKind.WITHDRAWAL, p.kind)
        assertEquals(900_000_000L, p.amountRial)
        assertEquals(60_153_689L, p.balanceRial)
        assertEquals("1158000001", p.accountId)
        assertEquals(SmsDateTime(1405, 3, 12, 17, 47), p.dateTime)
    }

    @Test fun mellatDeposit() {
        val p = parse("حساب1158000001\nواریز9,000,000\nمانده69,153,689\n05/06/26-09:53")
        assertEquals(SmsTxKind.DEPOSIT, p.kind)
        assertEquals(9_000_000L, p.amountRial)
        assertEquals(SmsDateTime(1405, 6, 26, 9, 53), p.dateTime)
    }

    // ───────────── Post Bank ─────────────
    // The real messages use Arabic ي (U+064A) in «واريز» / «ريال»; the escapes keep that exact.

    @Test fun postBankDeposit_signBeforeAmount_timeOnOwnLine() {
        val p = parse("واريز به حساب: 3000200000001\n+767,500\n1405/05/11\n15:57\nمانده: 767,500 ر\u064Aال")
        assertEquals(BankKey.POST, p.bank)
        assertEquals(SmsTxKind.DEPOSIT, p.kind)
        assertEquals(767_500L, p.amountRial)
        assertEquals(767_500L, p.balanceRial)
        assertEquals("3000200000001", p.accountId)
        assertEquals(SmsDateTime(1405, 5, 11, 15, 57), p.dateTime)
        assertFalse(p.needsReview)
    }

    @Test fun postBankWithdrawal() {
        val p = parse(" برداشت از: 3000200000001\n-375,000\n1405/05/11\n15:57\nمانده: 392,500 ر\u064Aال")
        assertEquals(BankKey.POST, p.bank)
        assertEquals(SmsTxKind.WITHDRAWAL, p.kind)
        assertEquals(375_000L, p.amountRial)
        assertEquals(392_500L, p.balanceRial)
    }

    @Test fun postBank_wordsAndSignMustAgree() {
        assertNull(BankSmsParser.parse("واريز به حساب: 3000200000001\n-375,000\n1405/05/11\n15:57\nمانده: 392,500 ر\u064Aال"))
    }

    @Test fun postBank_balanceChainIsConsistent() {
        // 767,500 deposited, then 375,000 withdrawn: the second balance is the first minus the amount.
        val a = parse("واريز به حساب: 3000200000001\n+767,500\n1405/05/11\n15:57\nمانده: 767,500 ر\u064Aال")
        val b = parse("برداشت از: 3000200000001\n-375,000\n1405/05/11\n15:57\nمانده: 392,500 ر\u064Aال")
        assertEquals(a.balanceRial!! - b.amountRial, b.balanceRial!!)
    }

    // ───────────── Shahr Bank ─────────────
    // Real messages use Arabic ي in «واريز» / «موجودي» / «ريال»; the escapes keep that exact.

    @Test fun shahrWithdrawal_withBoldAsterisks() {
        val p = parse("*بانک شهر*\nانتقال وجه با سپرده\nبرداشت از:100800000001\nمبلغ:5,000,000\u0631\u064A\u0627ل\nموجود\u064A:69,000,000 \u0631\u064A\u0627ل\n1405/07/27 14:55:25")
        assertEquals(BankKey.SHAHR, p.bank)
        assertEquals(SmsTxKind.WITHDRAWAL, p.kind)
        assertEquals(5_000_000L, p.amountRial)
        assertEquals(69_000_000L, p.balanceRial)
        assertEquals("100800000001", p.accountId)
        assertEquals("انتقال وجه با سپرده", p.label)
        assertEquals(SmsDateTime(1405, 7, 27, 14, 55), p.dateTime)
        assertFalse(p.needsReview)
    }

    @Test fun shahrDeposit() {
        val p = parse("بانک شهر\nانتقال وجه با سپرده\nوار\u064Aز به:400800000002\nمبلغ:5,000,000\u0631\u064A\u0627ل\nموجود\u064A:5,900,000\u0631\u064A\u0627ل\n1405/07/27 14:55:25")
        assertEquals(BankKey.SHAHR, p.bank)
        assertEquals(SmsTxKind.DEPOSIT, p.kind)
        assertEquals(5_900_000L, p.balanceRial)
        assertEquals("400800000002", p.accountId)
    }

    @Test fun shahrAndPostBankDoNotGetMixedUp() {
        // Both start with «برداشت از»; only the rest of the message tells them apart.
        assertEquals(BankKey.POST, parse("برداشت از: 3000200000001\n-375,000\n1405/05/11\n15:57\nمانده: 392,500 ریال").bank)
        assertEquals(BankKey.SHAHR, parse("برداشت از:100800000001\nمبلغ:375,000ریال\nموجودی:392,500 ریال\n1405/05/11 15:57:00").bank)
    }

    @Test fun shahrInternalTransfer_bothSidesHaveSameAmountAndTime() {
        // Same moment, same amount, one withdrawal + one deposit: the base for "transfer between my accounts".
        val out = parse("بانک شهر\nانتقال وجه با سپرده\nبرداشت از:100800000001\nمبلغ:5,000,000ریال\nموجودی:69,000,000 ریال\n1405/07/27 14:55:25")
        val inn = parse("بانک شهر\nانتقال وجه با سپرده\nواریز به:400800000002\nمبلغ:5,000,000ریال\nموجودی:5,900,000ریال\n1405/07/27 14:55:25")
        assertEquals(out.amountRial, inn.amountRial)
        assertEquals(out.dateTime, inn.dateTime)
        assertTrue(out.kind != inn.kind)
    }

    // ───────────── generic must never read the balance as the amount ─────────────

    @Test fun generic_balanceIsNotTakenAsAmount() {
        val p = parse("برداشت از حساب 1234 مبلغ 375,000 ریال مانده: 392,500 ریال")
        assertEquals(BankKey.GENERIC, p.bank)
        assertEquals(375_000L, p.amountRial)
        assertEquals(392_500L, p.balanceRial)
    }

    @Test fun generic_onlyBalanceNoAmountIsRejected() {
        assertNull(BankSmsParser.parse("واریز انجام شد. مانده: 392,500 ریال"))
    }

    // ───────────── dates ─────────────

    @Test fun resolveMillis_yearGivenIsUsed() {
        assertEquals(j(7, 26, 7, 30), BankSmsParser.resolveMillis(SmsDateTime(1405, 7, 26, 7, 30), now))
    }

    @Test fun resolveMillis_missingYearTakesReferenceYear() {
        assertEquals(j(7, 23, 11, 48), BankSmsParser.resolveMillis(SmsDateTime(null, 7, 23, 11, 48), now))
    }

    @Test fun resolveMillis_missingYearNeverLandsInTheFuture() {
        // 28 Esfand seen in Mehr must be last year's Esfand, not next March.
        assertEquals(j(12, 28, 9, 0, y = 1404), BankSmsParser.resolveMillis(SmsDateTime(null, 12, 28, 9, 0), now))
    }

    @Test fun resolveMillis_impossibleDateIsNull() {
        // 1405 is not a leap year: 30 Esfand does not exist.
        assertNull(BankSmsParser.resolveMillis(SmsDateTime(1405, 12, 30, 9, 0), now))
    }

    // ───────────── safety ─────────────

    @Test fun otpAndAdsAreIgnored() {
        assertNull(BankSmsParser.parse("کد تایید شما ۱۲۳۴۵۶ است. آن را به کسی ندهید."))
        assertNull(BankSmsParser.parse("جشنواره تخفیف ویژه بانک! همین امروز مراجعه کنید"))
        assertNull(BankSmsParser.parse(""))
        assertNull(BankSmsParser.parse("   \n  "))
    }

    @Test fun dateOnlyLinesAreNotMistakenForAmounts() {
        // "0723-11:48" / "1405/7/26-7:30" look like "digits then minus" but are dates.
        assertNull(BankSmsParser.parse("تاریخ 0723-11:48"))
        assertNull(BankSmsParser.parse("1405/7/26-7:30"))
    }

    @Test fun contradictoryBluSentenceIsRejected() {
        assertNull(BankSmsParser.parse("بلو\n100,000 ریال از حساب شما پرید و 100,000 ریال به حساب شما نشست"))
    }

    // ───────────── generic fallback ─────────────

    @Test fun genericWithdrawal_alwaysNeedsReview() {
        val p = parse("برداشت از حساب 1234 مبلغ 250,000 ریال مانده 5,000,000")
        assertEquals(BankKey.GENERIC, p.bank)
        assertEquals(SmsTxKind.WITHDRAWAL, p.kind)
        assertEquals(250_000L, p.amountRial)
        assertEquals(5_000_000L, p.balanceRial)
        assertTrue(p.needsReview)
    }

    @Test fun genericTomanMessageIsConvertedToRial() {
        val p = parse("واریز 2,000 تومان به حساب شما")
        assertEquals(SmsTxKind.DEPOSIT, p.kind)
        assertEquals(20_000L, p.amountRial)
        assertTrue(p.needsReview)
    }

    @Test fun genericAmbiguousDirectionIsRejected() {
        assertNull(BankSmsParser.parse("واریز و برداشت 250,000 ریال"))
    }

    // ───────────── accounts, duplicates, units ─────────────

    @Test fun accountMatching_differentLengthsPerBank() {
        assertTrue(BankSmsParser.accountMatches("243370010100000001", "0001"))
        assertTrue(BankSmsParser.accountMatches("12345", "2345"))
        assertTrue(BankSmsParser.accountMatches("2345", "12345"))
        assertTrue(BankSmsParser.accountMatches("۱۲۳۴۵", "2345"))
        assertFalse(BankSmsParser.accountMatches("12345", "9999"))
        assertFalse(BankSmsParser.accountMatches("12345", ""))
        assertFalse(BankSmsParser.accountMatches("", "2345"))
    }

    @Test fun samePasteTwice_sameDedupeKey_differentBalance_differentKey() {
        val a = parse("حساب1158000001\nواریز9,000,000\nمانده69,153,689\n05/06/26-09:53")
        val b = parse("حساب1158000001\nواریز9,000,000\nمانده69,153,689\n05/06/26-09:53")
        val c = parse("حساب1158000001\nواریز9,000,000\nمانده78,153,689\n05/06/26-09:53")
        assertEquals(a.dedupeKey(), b.dedupeKey())
        assertTrue(a.dedupeKey() != c.dedupeKey())
    }

    @Test fun rialToToman() {
        assertEquals(42_550L, BankSmsParser.rialToToman(425_500L))
        assertEquals(0L, BankSmsParser.rialToToman(9L))
    }

    @Test fun parsedResultCarriesNoRawText() {
        // Guard for the privacy promise: nothing in the result can hold the message or a name.
        val p = parse("بلو\nبرداشت رُند\nعلی عزیز، 425,500 ریال از حساب شما پرید.\nو 74,500 ریال هم رند شد.\nموجودی: 18,010,502 ریال\n۱۰:۱۲\n۱۴۰۵.۰۷.۲۳")
        assertNotNull(p)
        assertFalse(p.toString().contains("علی"))
    }
}
