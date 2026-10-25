package ir.naderinia.nsa.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Samples use the real formats with invented numbers and names. */
class SmsSampleReportTest {

    private val mehr = "243370010100000001\n550,000-\n1405/7/26-7:30\nمانده:30,225,745"
    private val melli = "بانك ملي ايران\nحواله پل:50,000,000-\nحساب:12345\nمانده:3,058,389,593\n0723-11:48"
    private val bluRound = "بلو\nبرداشت رُند\nعلی عزیز، 425,500 ریال از حساب شما پرید.\nو 74,500 ریال هم رند شد.\n" +
        "موجودی: 18,010,502 ریال\n۱۰:۱۲\n۱۴۰۵.۰۷.۲۳"
    private val mellat = "حساب1158000001\nبرداشت900,000,000\nمانده60,153,689\n05/03/12-17:47"
    private val post = "واريز به حساب: 3000200000001\n+767,500\n1405/05/11\n15:57\nمانده: 767,500 ريال"
    private val shahr = "*بانک شهر*\nانتقال وجه با سپرده\nبرداشت از:100800000001\nمبلغ:5,000,000ريال\nموجودي:69,000,000 ريال\n1405/07/27 14:55:25"
    private val all = listOf(mehr, melli, bluRound, mellat, post, shahr)

    private fun mask(s: String) = SmsSampleMasker.mask(s, Random(7))

    // ───────────── what must change ─────────────

    @Test fun amountsAndBalancesAreNoLongerTheRealOnes() {
        val m = mask(bluRound)
        assertFalse(m.contains("425,500"))
        assertFalse(m.contains("74,500"))
        assertFalse(m.contains("18,010,502"))
    }

    @Test fun accountNumbersAreScrambledButKeepTheirLength() {
        val m = mask(mehr)
        val id = m.lines().first()
        assertEquals(18, id.length)
        assertTrue(id.all { it.isDigit() })
        assertFalse(id == "243370010100000001")
        assertEquals(5, mask(melli).lines().first { it.startsWith("حساب:") }.removePrefix("حساب:").length)
    }

    @Test fun everyDigitOfAScrambledNumberChanges() {
        val original = "243370010100000001"
        val masked = mask(original)
        assertEquals(original.length, masked.length)
        assertTrue(original.indices.all { original[it] != masked[it] })
    }

    @Test fun greetingNameIsRemoved() {
        val m = mask(bluRound)
        assertFalse(m.contains("علی"))
        assertTrue(m.contains("نام عزیز"))
    }

    @Test fun titleNameIsRemoved() {
        assertTrue(mask("جناب آقای احمدی مبلغ 500,000 ریال").contains("آقای نام"))
        assertFalse(mask("جناب آقای احمدی مبلغ 500,000 ریال").contains("احمدی"))
    }

    // ───────────── what must stay ─────────────

    @Test fun datesAndTimesAreKeptExactly() {
        assertTrue(mask(mehr).contains("1405/7/26-7:30"))
        assertTrue(mask(melli).contains("0723-11:48"))
        assertTrue(mask(bluRound).contains("۱۰:۱۲\n۱۴۰۵.۰۷.۲۳"))
        assertTrue(mask(mellat).contains("05/03/12-17:47"))
        assertTrue(mask(post).contains("1405/05/11\n15:57"))
        assertTrue(mask(shahr).contains("1405/07/27 14:55:25"))
    }

    @Test fun separatorsSignsAndLettersAreKept() {
        val m = mask(mehr)
        assertTrue(Regex("^\\d{3},\\d{3}-$").containsMatchIn(m.lines()[1]))
        assertTrue(m.contains("مانده:"))
        // Arabic letters stay Arabic: they are part of the format.
        assertTrue(mask(melli).startsWith("بانك ملي ايران"))
    }

    @Test fun persianDigitsStayPersian() {
        val m = mask("برداشت ۲۵۰,۰۰۰ ریال")
        val number = m.substringAfter("برداشت ").substringBefore(" ریال")
        assertTrue(number.all { it == ',' || it in '\u06F0'..'\u06F9' })
        assertFalse(number == "۲۵۰,۰۰۰")
    }

    @Test fun shortNumbersAreLeftAlone() {
        assertEquals("قسط 5 از 12 پرداخت شد", mask("قسط 5 از 12 پرداخت شد"))
    }

    // ───────────── the sample is still useful to us ─────────────

    @Test fun maskedSampleStillParsesAsTheSameBankAndDirection() {
        for (sample in all) {
            val before = BankSmsParser.parse(sample)!!
            val after = BankSmsParser.parse(mask(sample))
            assertTrue("masked sample no longer parses: $sample", after != null)
            assertEquals(before.bank, after!!.bank)
            assertEquals(before.kind, after.kind)
        }
    }

    @Test fun maskingIsDeterministicForTheSameSeed() {
        assertEquals(SmsSampleMasker.mask(mehr, Random(3)), SmsSampleMasker.mask(mehr, Random(3)))
    }

    // ───────────── the report ─────────────

    @Test fun reportContainsMaskedTextSenderAndVersion() {
        val masked = mask(bluRound)
        val r = SmsSampleReport.build(masked, " +989999900000 ", BankSmsParser.parse(bluRound), "0.27.0")
        assertTrue(r.contains(masked))
        assertTrue(r.contains("فرستنده پیامک: +989999900000"))
        assertTrue(r.contains("نسخه برنامه: 0.27.0"))
        assertTrue(r.contains("تشخیص برنامه: بلو (سامان)، برداشت، مانده دارد، تاریخ دارد"))
    }

    @Test fun reportNeverCarriesTheRealNumbersOrName() {
        val r = SmsSampleReport.build(mask(bluRound), "", BankSmsParser.parse(bluRound), "0.27.0")
        assertFalse(r.contains("425,500"))
        assertFalse(r.contains("18,010,502"))
        assertFalse(r.contains("علی"))
        assertTrue(r.contains("فرستنده پیامک: نامشخص"))
    }

    @Test fun unrecognisedSampleSaysSo() {
        assertEquals("نشناخت", SmsSampleReport.recognitionSummary(null))
        val r = SmsSampleReport.build("متن دلخواه", "X", null, "0.27.0")
        assertTrue(r.contains("تشخیص برنامه: نشناخت"))
    }

    @Test fun reviewFlagShowsInSummary() {
        val p = BankSmsParser.parse("برداشت از حساب 1234 مبلغ 250,000 ریال")!!
        assertTrue(SmsSampleReport.recognitionSummary(p).contains("نیاز به تأیید"))
    }
}
