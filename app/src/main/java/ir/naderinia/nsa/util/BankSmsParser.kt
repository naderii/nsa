package ir.naderinia.nsa.util

enum class SmsTxKind { DEPOSIT, WITHDRAWAL }

enum class BankKey(val persianName: String) {
    MEHR("بانک مهر ایران"),
    MELLI("بانک ملی ایران"),
    BLU("بلو (سامان)"),
    MELLAT("بانک ملت"),
    POST("پست بانک"),
    SHAHR("بانک شهر"),
    /** Not one of the known formats: found by keywords only, so the person must confirm it. */
    GENERIC("بانک نامشخص")
}

/**
 * Date/time exactly as the SMS wrote it (Jalali). [year] is null when the bank leaves it out
 * (Melli) — see [BankSmsParser.resolveMillis].
 */
data class SmsDateTime(val year: Int?, val month: Int, val day: Int, val hour: Int, val minute: Int)

/**
 * What we keep from one bank SMS. Deliberately NOT stored: the raw text, the person's name
 * (Blu greets by name) and any full account number — callers keep only a short suffix.
 *
 * All amounts are in RIAL, exactly as banks send them; converting to Toman is a per-account
 * setting done by the caller ([BankSmsParser.rialToToman]).
 */
data class ParsedBankSms(
    val bank: BankKey,
    val kind: SmsTxKind,
    val amountRial: Long,
    val balanceRial: Long?,
    /** Account identifier digits exactly as in the SMS (full, partial or null) — match with [BankSmsParser.accountMatches]. */
    val accountId: String?,
    /** Bank's own description: «حواله پل»، «حقوق»، «برداشت رند»... */
    val label: String?,
    val dateTime: SmsDateTime?,
    /** Blu «رُند»: extra rial moved to savings with this withdrawal. Savings, NOT spending. */
    val roundUpRial: Long = 0L,
    /** True when the format was not recognised for certain — never act on it without confirmation. */
    val needsReview: Boolean = false
) {
    /**
     * Same SMS pasted/received twice → same key. The balance after the transaction is the strongest
     * part; callers should also add their account id before hashing. The year is left out on purpose
     * (Melli has none).
     */
    fun dedupeKey(): String {
        val d = dateTime?.let { "${it.month}-${it.day}-${it.hour}:${it.minute}" } ?: "-"
        return "$kind|$amountRial|${balanceRial ?: "-"}|$d"
    }
}

/**
 * Reads Iranian bank SMS (Mehr, Melli, Blu/Saman, Mellat, Post Bank, Shahr) into [ParsedBankSms].
 *
 * Pure Kotlin, no Android classes, no storage and no network — everything it needs is in the text,
 * so it can be unit-tested on the JVM. Each bank has its own strict parser; a loose keyword-based
 * fallback ([BankKey.GENERIC]) catches the rest and always sets needsReview.
 *
 * Returns null for anything that is not clearly a money movement (OTP codes, ads...).
 */
object BankSmsParser {

    // ───────────── public API ─────────────

    fun parse(raw: String): ParsedBankSms? {
        val lines = cleanLines(raw)
        if (lines.isEmpty()) return null
        return parseMelli(lines)
            ?: parseMehr(lines)
            ?: parseMellat(lines)
            ?: parsePostBank(lines)
            ?: parseShahr(lines)
            ?: parseBlu(lines)
            ?: parseGeneric(lines)
    }

    /**
     * Epoch millis of the SMS date. [referenceMillis] is when the SMS arrived (or now, for a pasted one):
     * it supplies the missing year (Melli) and stops a year-less date from landing in the future.
     * Returns null if the date does not exist (e.g. 31 Esfand in a non-leap year).
     */
    fun resolveMillis(dt: SmsDateTime, referenceMillis: Long): Long? {
        val refYear = JalaliCalendar.millisToJalali(referenceMillis).year
        val year = dt.year ?: refYear
        if (dt.day > JalaliCalendar.daysInJalaliMonth(year, dt.month)) return null
        val millis = JalaliCalendar.jalaliToMillis(year, dt.month, dt.day, dt.hour, dt.minute)
        if (dt.year == null && millis > referenceMillis + DAY_MS) {
            val prev = year - 1
            if (dt.day > JalaliCalendar.daysInJalaliMonth(prev, dt.month)) return null
            return JalaliCalendar.jalaliToMillis(prev, dt.month, dt.day, dt.hour, dt.minute)
        }
        return millis
    }

    /**
     * Does the account id found in an SMS belong to the stored account suffix? Lengths differ per bank
     * (18, 10, 5 digits...), so compare from the end in both directions.
     */
    fun accountMatches(smsAccountId: String, storedSuffix: String): Boolean {
        val a = PersianText.toAsciiDigits(smsAccountId).trim()
        val b = PersianText.toAsciiDigits(storedSuffix).trim()
        if (a.isEmpty() || b.isEmpty()) return false
        return a.endsWith(b) || b.endsWith(a)
    }

    /** Banks send rial; the app shows toman. */
    fun rialToToman(rial: Long): Long = rial / 10

    // ───────────── cleaning ─────────────

    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Invisible direction marks (the Mehr SMS is wrapped in them) and byte-order marks. */
    private val INVISIBLE = Regex("[\\u200B\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069\\uFEFF]")

    /**
     * One entry per non-empty line, each normalised (ي/ك → ی/ک, Persian digits → ASCII, no diacritics,
     * no thousands commas). Line breaks matter in these SMS, which is why [PersianText.normalize]
     * (it flattens whitespace) runs per line.
     */
    private fun cleanLines(raw: String): List<String> =
        raw.replace("\r", "\n").split('\n')
            .map { line ->
                val noMarks = INVISIBLE.replace(line, "")
                    .replace('\u2212', '-').replace('\u2013', '-')
                    .replace('\u2011', '-').replace('\u2010', '-')
                PersianText.normalize(noMarks.replace("*", ""))
            }
            .filter { it.isNotEmpty() }

    private fun validTime(h: Int, m: Int) = h in 0..23 && m in 0..59

    private fun dateTime(year: Int?, month: Int, day: Int, hour: Int, minute: Int): SmsDateTime? {
        if (month !in 1..12 || day !in 1..31 || !validTime(hour, minute)) return null
        if (year != null && year !in 1300..1499) return null
        return SmsDateTime(year, month, day, hour, minute)
    }

    /** «05» → 1405. Two-digit years of the 1300s (≥ 51) are treated as 13xx. */
    private fun expandYear(yy: Int): Int = if (yy <= 50) 1400 + yy else 1300 + yy

    private val BALANCE_LINE = Regex("^(?:مانده|موجودی)\\s*:?\\s*(\\d+)(?:\\s*ریال)?$")

    private fun findBalance(lines: List<String>): Long? =
        lines.firstNotNullOfOrNull { BALANCE_LINE.matchEntire(it)?.groupValues?.get(1)?.toLongOrNull() }

    // ───────────── Mehr ─────────────
    // 243370010100000001
    // 550000-
    // 1405/7/26-7:30
    // مانده:30225745

    private val MEHR_ACCOUNT = Regex("^\\d{12,20}$")
    private val MEHR_AMOUNT = Regex("^(\\d+)([+-])$")
    private val MEHR_DATE = Regex("^(\\d{4})/(\\d{1,2})/(\\d{1,2})-(\\d{1,2}):(\\d{2})$")

    private fun parseMehr(lines: List<String>): ParsedBankSms? {
        val accountId = lines.firstOrNull { MEHR_ACCOUNT.matches(it) } ?: return null
        val amountMatch = lines.firstNotNullOfOrNull { MEHR_AMOUNT.matchEntire(it) } ?: return null
        val amount = amountMatch.groupValues[1].toLongOrNull() ?: return null
        val kind = if (amountMatch.groupValues[2] == "+") SmsTxKind.DEPOSIT else SmsTxKind.WITHDRAWAL
        val dt = lines.firstNotNullOfOrNull { MEHR_DATE.matchEntire(it) }?.let {
            val g = it.groupValues
            dateTime(g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4].toInt(), g[5].toInt())
        }
        return ParsedBankSms(
            bank = BankKey.MEHR, kind = kind, amountRial = amount,
            balanceRial = findBalance(lines), accountId = accountId, label = null, dateTime = dt
        )
    }

    // ───────────── Melli ─────────────
    // بانك ملي ايران
    // حواله پل:50000000-
    // حساب:12345
    // مانده:3058389593
    // 0723-11:48          (month+day, no year)

    private val MELLI_AMOUNT = Regex("^(.+?):(\\d+)([+-])$")
    private val MELLI_ACCOUNT = Regex("^حساب:(\\d+)$")
    private val MELLI_DATE = Regex("^(\\d{2})(\\d{2})-(\\d{1,2}):(\\d{2})$")

    private fun parseMelli(lines: List<String>): ParsedBankSms? {
        val amountMatch = lines.firstNotNullOfOrNull { MELLI_AMOUNT.matchEntire(it) } ?: return null
        val dateMatch = lines.firstNotNullOfOrNull { MELLI_DATE.matchEntire(it) } ?: return null
        val amount = amountMatch.groupValues[2].toLongOrNull() ?: return null
        val kind = if (amountMatch.groupValues[3] == "+") SmsTxKind.DEPOSIT else SmsTxKind.WITHDRAWAL
        val g = dateMatch.groupValues
        return ParsedBankSms(
            bank = BankKey.MELLI, kind = kind, amountRial = amount,
            balanceRial = findBalance(lines),
            accountId = lines.firstNotNullOfOrNull { MELLI_ACCOUNT.matchEntire(it)?.groupValues?.get(1) },
            label = amountMatch.groupValues[1].trim(),
            dateTime = dateTime(null, g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4].toInt())
        )
    }

    // ───────────── Mellat ─────────────
    // حساب1234567890
    // برداشت900000
    // مانده60153689
    // 05/03/12-17:47      (yy/mm/dd-HH:MM)

    private val MELLAT_ACCOUNT = Regex("^حساب\\s*:?\\s*(\\d+)$")
    private val MELLAT_TX = Regex("^(واریز|برداشت)\\s*:?\\s*(\\d+)$")
    private val MELLAT_DATE = Regex("^(\\d{2})/(\\d{1,2})/(\\d{1,2})-(\\d{1,2}):(\\d{2})$")

    private fun parseMellat(lines: List<String>): ParsedBankSms? {
        val tx = lines.firstNotNullOfOrNull { MELLAT_TX.matchEntire(it) } ?: return null
        val amount = tx.groupValues[2].toLongOrNull() ?: return null
        val kind = if (tx.groupValues[1] == "واریز") SmsTxKind.DEPOSIT else SmsTxKind.WITHDRAWAL
        val dt = lines.firstNotNullOfOrNull { MELLAT_DATE.matchEntire(it) }?.let {
            val g = it.groupValues
            dateTime(expandYear(g[1].toInt()), g[2].toInt(), g[3].toInt(), g[4].toInt(), g[5].toInt())
        }
        return ParsedBankSms(
            bank = BankKey.MELLAT, kind = kind, amountRial = amount,
            balanceRial = findBalance(lines),
            accountId = lines.firstNotNullOfOrNull { MELLAT_ACCOUNT.matchEntire(it)?.groupValues?.get(1) },
            label = null, dateTime = dt
        )
    }

    // ───────────── Post Bank ─────────────
    // واريز به حساب: 3000200000001        (or «برداشت از: ...»)
    // +767500                              (sign BEFORE the amount)
    // 1405/05/11
    // 15:57                                (time on its own line)
    // مانده: 767500 ريال

    private val POST_ACCOUNT = Regex("^(واریز به حساب|برداشت از)\\s*:?\\s*(\\d+)$")
    private val POST_AMOUNT = Regex("^([+-])(\\d+)$")
    private val DATE_SLASH = Regex("^(\\d{4})/(\\d{1,2})/(\\d{1,2})$")

    private fun parsePostBank(lines: List<String>): ParsedBankSms? {
        val acc = lines.firstNotNullOfOrNull { POST_ACCOUNT.matchEntire(it) } ?: return null
        val amountMatch = lines.firstNotNullOfOrNull { POST_AMOUNT.matchEntire(it) } ?: return null
        val amount = amountMatch.groupValues[2].toLongOrNull() ?: return null
        val kind = if (amountMatch.groupValues[1] == "+") SmsTxKind.DEPOSIT else SmsTxKind.WITHDRAWAL
        // The wording and the sign must agree, otherwise we do not trust the message.
        val wordsSayDeposit = acc.groupValues[1].startsWith("واریز")
        if (wordsSayDeposit != (kind == SmsTxKind.DEPOSIT)) return null

        val date = lines.firstNotNullOfOrNull { DATE_SLASH.matchEntire(it) }
        val time = lines.firstNotNullOfOrNull { TIME_LINE.matchEntire(it) }
        val dt = if (date != null && time != null) {
            dateTime(
                date.groupValues[1].toInt(), date.groupValues[2].toInt(), date.groupValues[3].toInt(),
                time.groupValues[1].toInt(), time.groupValues[2].toInt()
            )
        } else null
        return ParsedBankSms(
            bank = BankKey.POST, kind = kind, amountRial = amount,
            balanceRial = findBalance(lines), accountId = acc.groupValues[2], label = null, dateTime = dt
        )
    }

    // ───────────── Shahr Bank ─────────────
    // *بانک شهر*
    // انتقال وجه با سپرده                  (the bank's own description)
    // برداشت از:1008...                     (or «واريز به:...»; no sign anywhere)
    // مبلغ:5000000ريال
    // موجودي:69594946 ريال
    // 1405/07/27 14:55:25                  (date and time on ONE line, with seconds)

    private val SHAHR_ACCOUNT = Regex("^(واریز به|برداشت از)\\s*:?\\s*(\\d+)$")
    private val SHAHR_AMOUNT = Regex("^مبلغ\\s*:?\\s*(\\d+)\\s*(ریال|تومان)?$")
    private val SHAHR_DATETIME = Regex("^(\\d{4})/(\\d{1,2})/(\\d{1,2})\\s+(\\d{1,2}):(\\d{2})(?::\\d{2})?$")

    private fun parseShahr(lines: List<String>): ParsedBankSms? {
        val acc = lines.firstNotNullOfOrNull { SHAHR_ACCOUNT.matchEntire(it) } ?: return null
        val amountMatch = lines.firstNotNullOfOrNull { SHAHR_AMOUNT.matchEntire(it) } ?: return null
        val factor = if (amountMatch.groupValues[2] == "تومان") 10L else 1L
        val amount = (amountMatch.groupValues[1].toLongOrNull() ?: return null) * factor
        val kind = if (acc.groupValues[1] == "واریز به") SmsTxKind.DEPOSIT else SmsTxKind.WITHDRAWAL
        val dt = lines.firstNotNullOfOrNull { SHAHR_DATETIME.matchEntire(it) }?.let {
            val g = it.groupValues
            dateTime(g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4].toInt(), g[5].toInt())
        }
        // The description is the line with no digits and no colon that is not the bank's own title.
        val label = lines.firstOrNull { l -> l.none(Char::isDigit) && !l.contains(':') && !l.contains("بانک شهر") }
        return ParsedBankSms(
            bank = BankKey.SHAHR, kind = kind, amountRial = amount,
            balanceRial = findBalance(lines)?.let { it * factor },
            accountId = acc.groupValues[2], label = label, dateTime = dt
        )
    }

    // ───────────── Blu (Saman) ─────────────
    // بلو
    // برداشت رُند
    // <name> عزیز، 425500 ریال از حساب شما پرید.
    // و 74500 ریال هم رند شد.
    // موجودی: 18010502 ریال
    // ۱۰:۱۲
    // ۱۴۰۵.۰۷.۲۳
    // «پرید» = withdrawal, «نشست» = deposit. No account number in the message at all.

    private val BLU_SENT = Regex("(\\d+) ریال از حساب شما پرید")
    private val BLU_GOT = Regex("(\\d+) ریال به حساب شما نشست")
    private val BLU_ROUNDUP = Regex("(\\d+) ریال هم رند شد")
    private val TIME_LINE = Regex("^(\\d{1,2}):(\\d{2})$")
    private val DATE_DOTS = Regex("^(\\d{4})[./](\\d{1,2})[./](\\d{1,2})$")

    private fun parseBlu(lines: List<String>): ParsedBankSms? {
        val text = lines.joinToString(" ")
        val sent = BLU_SENT.find(text)
        val got = BLU_GOT.find(text)
        if ((sent == null) == (got == null)) return null // neither, or contradictory
        val amount = (sent ?: got)!!.groupValues[1].toLongOrNull() ?: return null

        val time = lines.firstNotNullOfOrNull { TIME_LINE.matchEntire(it) }
        val date = lines.firstNotNullOfOrNull { DATE_DOTS.matchEntire(it) }
        val dt = if (time != null && date != null) {
            dateTime(
                date.groupValues[1].toInt(), date.groupValues[2].toInt(), date.groupValues[3].toInt(),
                time.groupValues[1].toInt(), time.groupValues[2].toInt()
            )
        } else null

        // The title is the line right after «بلو» (never the sentence with the person's name).
        val bluIdx = lines.indexOf("بلو")
        val label = lines.getOrNull(bluIdx + 1)?.takeIf { bluIdx >= 0 && it.none(Char::isDigit) }

        return ParsedBankSms(
            bank = BankKey.BLU,
            kind = if (sent != null) SmsTxKind.WITHDRAWAL else SmsTxKind.DEPOSIT,
            amountRial = amount,
            balanceRial = findBalance(lines),
            accountId = null,
            label = label,
            dateTime = dt,
            roundUpRial = BLU_ROUNDUP.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        )
    }

    // ───────────── generic fallback ─────────────

    private val GENERIC_SIGNED = Regex("(?<![\\d/:.])(\\d{4,})([+-])(?!\\d)")
    private val GENERIC_KEYWORD_AMOUNT = Regex("(?:واریز|دریافت|برداشت|خرید|پرداخت|کسر|مبلغ)\\s*:?\\s*(\\d{3,})")
    private val GENERIC_UNIT_AMOUNT = Regex("(\\d{3,})\\s*(?:ریال|تومان)")
    private val GENERIC_BALANCE = Regex("(?:مانده|موجودی)\\s*:?\\s*(\\d+)")
    /** The whole «مانده: 123 ریال» phrase, cut out before looking for the amount so the balance is never read as one. */
    private val GENERIC_BALANCE_PHRASE = Regex("(?:مانده|موجودی)\\s*:?\\s*\\d+(?:\\s*(?:ریال|تومان))?")
    private val DEPOSIT_WORDS = listOf("واریز", "دریافت", "نشست", "بستانکار", "افزایش")
    private val WITHDRAWAL_WORDS = listOf("برداشت", "خرید", "پرداخت", "پرید", "بدهکار", "کاهش", "کسر")

    /**
     * Last resort for banks/messages we have no exact format for. Only answers when the direction is
     * unambiguous, and always sets needsReview.
     */
    private fun parseGeneric(lines: List<String>): ParsedBankSms? {
        val text = lines.joinToString(" ")
        val forAmount = GENERIC_BALANCE_PHRASE.replace(text, " ")
        val signed = GENERIC_SIGNED.find(forAmount)

        val kind: SmsTxKind
        val amountRaw: Long
        if (signed != null) {
            kind = if (signed.groupValues[2] == "+") SmsTxKind.DEPOSIT else SmsTxKind.WITHDRAWAL
            amountRaw = signed.groupValues[1].toLongOrNull() ?: return null
        } else {
            val hasDeposit = DEPOSIT_WORDS.any { text.contains(it) }
            val hasWithdrawal = WITHDRAWAL_WORDS.any { text.contains(it) }
            if (hasDeposit == hasWithdrawal) return null
            kind = if (hasDeposit) SmsTxKind.DEPOSIT else SmsTxKind.WITHDRAWAL
            val amountMatch = GENERIC_KEYWORD_AMOUNT.find(forAmount) ?: GENERIC_UNIT_AMOUNT.find(forAmount) ?: return null
            amountRaw = amountMatch.groupValues[1].toLongOrNull() ?: return null
        }

        // A message in Toman only (no rial anywhere) is converted so everything downstream is rial.
        val unitFactor = if (text.contains("تومان") && !text.contains("ریال")) 10L else 1L
        val balance = GENERIC_BALANCE.find(text)?.groupValues?.get(1)?.toLongOrNull()
        return ParsedBankSms(
            bank = BankKey.GENERIC, kind = kind, amountRial = amountRaw * unitFactor,
            balanceRial = balance?.times(unitFactor),
            accountId = null, label = null, dateTime = null, needsReview = true
        )
    }
}
