package ir.naderinia.nsa.util

import kotlin.random.Random

/** Where people can send a sample of their bank's SMS. Nothing is ever sent by the app itself. */
object SampleContacts {
    const val EMAIL = "nadernaderinia@gmail.com"
    const val BALE_ID = "@nadernaderinia"
    const val BALE_URL = "https://ble.ir/nadernaderinia"
    const val TELEGRAM_ID = "@sananaderi"
    const val TELEGRAM_URL = "https://t.me/sananaderi"
}

/**
 * Hides what is private in a bank SMS while keeping what we need to learn the bank's format:
 * the exact wording, the line structure, the length of every number and the separators.
 *
 * - Numbers of 3+ digits (amounts, balances, account and card numbers, phone numbers) get every
 *   digit replaced by a random one of the same script, so «5,000,000» stays a 7-digit number with
 *   thousands separators but is no longer the real amount.
 * - Dates and times are kept on purpose: «05/03/12» only tells us it is year/month/day when the real
 *   digits are visible.
 * - A name before «عزیز / گرامی» or after «آقای / خانم» is replaced by «نام».
 * - Letters (including Arabic ي/ك) and invisible direction marks are kept as they are, because they
 *   are part of the format.
 *
 * The person always sees the result and can edit it before sending anything.
 */
object SmsSampleMasker {

    private const val D = "[0-9\\u06F0-\\u06F9\\u0660-\\u0669]"

    /** Date/time shapes we must not scramble. Order matters only for readability: all are preserved. */
    private val KEEP = Regex(
        "$D{2,4}[/.]$D{1,2}[/.]$D{1,2}" +          // 1405/07/27  05/03/12  ۱۴۰۵.۰۷.۲۳
            "|$D{1,2}:$D{2}(?::$D{2})?" +             // 14:55  14:55:25  ۱۰:۱۲
            "|$D{4}(?=-$D{1,2}:$D{2})"               // 0723 in «0723-11:48» (month+day)
    )

    /** One number, thousands separators included, so «18,010,502» is treated as a whole. */
    private val NUMBER = Regex("$D(?:[,\\u066C]?$D)*")

    private val GREETING_NAME = Regex("(\\S+)(\\s+)(عزیز|گرامی)")
    /** «جناب آقای <first> <last>»: one or more titles, then the name (up to two words). */
    private val TITLE_NAME = Regex("((?:(?:آقای|خانم|جناب|سرکار)\\s+)+)(\\S+)(?:(\\s+)(\\S+))?")

    /** A second word after a name that is really the next part of the sentence, not a surname. */
    private val NOT_A_SURNAME = setOf(
        "مبلغ", "به", "از", "در", "با", "برای", "حساب", "کارت", "ریال", "تومان",
        "مانده", "موجودی", "واریز", "برداشت", "خرید", "پرداخت"
    )

    fun mask(raw: String, random: Random = Random.Default): String {
        val keep = KEEP.findAll(raw).map { it.range }.toList()
        val sb = StringBuilder(raw.length)
        var last = 0
        for (m in NUMBER.findAll(raw)) {
            if (keep.any { m.range.first >= it.first && m.range.last <= it.last }) continue
            val digits = m.value.count { it in DIGIT_CHARS }
            if (digits < 3) continue
            sb.append(raw, last, m.range.first)
            sb.append(scramble(m.value, random))
            last = m.range.last + 1
        }
        sb.append(raw, last, raw.length)
        return maskNames(sb.toString())
    }

    private val DIGIT_CHARS: Set<Char> =
        ('0'..'9').toSet() + ('\u06F0'..'\u06F9').toSet() + ('\u0660'..'\u0669').toSet()

    private fun scramble(number: String, random: Random): String {
        val out = StringBuilder(number.length)
        var first = true
        for (c in number) {
            val base = when (c) {
                in '0'..'9' -> '0'
                in '\u06F0'..'\u06F9' -> '\u06F0'
                in '\u0660'..'\u0669' -> '\u0660'
                else -> { out.append(c); continue }
            }
            val old = c - base
            // Always a different digit; a non-zero leading digit stays non-zero (numbers keep looking real).
            val v = (0..9).filter { it != old && !(first && old != 0 && it == 0) }.random(random)
            first = false
            out.append(base + v)
        }
        return out.toString()
    }

    private fun maskNames(text: String): String =
        TITLE_NAME.replace(GREETING_NAME.replace(text) { "نام${it.groupValues[2]}${it.groupValues[3]}" }) { m ->
            val second = m.groupValues[4]
            val keepSecond = second.isNotEmpty() && (second in NOT_A_SURNAME || second.any(Char::isDigit))
            "${m.groupValues[1]}نام" + if (keepSecond) "${m.groupValues[3]}$second" else ""
        }
}

/** The message the person sends us: masked sample plus what the app understood of it. */
object SmsSampleReport {

    fun subject(): String = "نمونه پیامک بانکی برای نسا"

    /** What the parser made of the ORIGINAL text, without any amount, balance or number. */
    fun recognitionSummary(parsed: ParsedBankSms?): String {
        if (parsed == null) return "نشناخت"
        val kind = if (parsed.kind == SmsTxKind.DEPOSIT) "واریز" else "برداشت"
        val parts = mutableListOf(parsed.bank.persianName, kind)
        if (parsed.balanceRial != null) parts += "مانده دارد"
        if (parsed.dateTime != null) parts += "تاریخ دارد"
        if (parsed.needsReview) parts += "نیاز به تأیید"
        return parts.joinToString("، ")
    }

    fun build(maskedSms: String, sender: String, parsed: ParsedBankSms?, appVersion: String): String =
        buildFromSummary(maskedSms, sender, recognitionSummary(parsed), appVersion)

    fun buildFromSummary(maskedSms: String, sender: String, summary: String, appVersion: String): String =
        buildString {
            appendLine(subject())
            appendLine("فرستنده پیامک: ${sender.trim().ifEmpty { "نامشخص" }}")
            appendLine("تشخیص برنامه: $summary")
            appendLine("نسخه برنامه: $appVersion")
            appendLine("————————")
            append(maskedSms.trim())
        }
}
