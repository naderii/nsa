package ir.naderinia.nsa.util

/**
 * Small, dependency-free helpers for understanding typed / dictated Persian.
 * Used by [QuickParser] (create a reminder from a sentence) and [AskEngine] (answer a question).
 *
 * Everything works on a *normalized* string: one kind of ی/ک, ASCII digits, half-spaces turned into
 * plain spaces, no diacritics — so patterns only have to be written once.
 */
object PersianText {

    private val DIACRITICS = Regex("[\\u064B-\\u065F\\u0670\\u0640]")
    private val MULTI_SPACE = Regex("\\s+")
    private val THOUSANDS_COMMA = Regex("(?<=\\d),(?=\\d{3})")

    /** Persian / Arabic-Indic digits → ASCII, so one regex like \d works for every keyboard. */
    fun toAsciiDigits(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            sb.append(
                when (c) {
                    in '۰'..'۹' -> '0' + (c - '۰')
                    in '٠'..'٩' -> '0' + (c - '٠')
                    else -> c
                }
            )
        }
        return sb.toString()
    }

    fun normalize(raw: String): String {
        var t = raw
            .replace('ي', 'ی').replace('ى', 'ی').replace('ك', 'ک')
            .replace('ۀ', 'ه').replace('ة', 'ه')
            .replace('أ', 'ا').replace('إ', 'ا')
            .replace("\u200c", " ").replace("\u200f", " ").replace("\u200e", " ")
            .replace('\u00a0', ' ')
            .replace('٫', '.').replace('٬', ',')
            .replace('،', ' ').replace('؛', ' ').replace('؟', '?')
        t = DIACRITICS.replace(t, "")
        t = toAsciiDigits(t)
        t = THOUSANDS_COMMA.replace(t, "")
        t = t.lowercase()
        return MULTI_SPACE.replace(t, " ").trim()
    }

    // ───────────── number words → digits ─────────────

    private val SMALL = mapOf(
        "صفر" to 0, "یک" to 1, "یه" to 1, "دو" to 2, "سه" to 3, "چهار" to 4, "پنج" to 5,
        "شش" to 6, "شیش" to 6, "هفت" to 7, "هشت" to 8, "نه" to 9, "ده" to 10, "یازده" to 11,
        "دوازده" to 12, "سیزده" to 13, "چهارده" to 14, "پانزده" to 15, "پونزده" to 15,
        "شانزده" to 16, "شونزده" to 16, "هفده" to 17, "هجده" to 18, "نوزده" to 19
    )
    private val TENS = mapOf(
        "بیست" to 20, "سی" to 30, "چهل" to 40, "پنجاه" to 50,
        "شصت" to 60, "هفتاد" to 70, "هشتاد" to 80, "نود" to 90
    )
    private val HUNDREDS = mapOf(
        "صد" to 100, "یکصد" to 100, "دویست" to 200, "سیصد" to 300, "چهارصد" to 400,
        "پانصد" to 500, "پانسد" to 500, "ششصد" to 600, "هفتصد" to 700, "هشتصد" to 800, "نهصد" to 900
    )
    private val ALL = HashMap<String, Int>().apply { putAll(SMALL); putAll(TENS); putAll(HUNDREDS) }

    /** Words that are also everyday Persian ("نه" = no, "یه" = a, "ده" = village): only a number before a unit. */
    private val AMBIGUOUS = setOf("نه", "ده", "یه", "یک", "سی", "صد", "دو", "سه")
    private val UNIT_AFTER = setOf(
        "ساعت", "روز", "هفته", "ماه", "سال", "دقیقه", "ثانیه", "میلیون", "میلیارد", "هزار",
        "قسط", "بار", "تومان", "تومن", "ریال", "کیلومتر", "کیلو", "صبح", "عصر", "شب", "ظهر",
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    private fun isNumberToken(tok: String, prev: String?, next: String?): Boolean {
        if (tok !in ALL) return false
        if (tok in AMBIGUOUS) {
            val afterUnit = next != null && next in UNIT_AFTER
            val afterSaat = prev == "ساعت"
            return afterUnit || afterSaat
        }
        return true
    }

    /**
     * «دو میلیون و پانصد هزار» → «2 میلیون و 500 هزار», «ساعت پنج و نیم» → «ساعت 5 و نیم».
     * Multipliers (هزار / میلیون / میلیارد) stay words — the amount reader combines them.
     * Call after [normalize] and after the weekday names («سه شنبه») were glued.
     */
    fun numberWordsToDigits(text: String): String {
        val tokens = text.split(' ').filter { it.isNotEmpty() }
        val out = ArrayList<String>()
        var i = 0
        while (i < tokens.size) {
            val tok = tokens[i]
            val prev = out.lastOrNull()
            if (isNumberToken(tok, prev, tokens.getOrNull(i + 1))) {
                var total = ALL.getValue(tok)
                var j = i + 1
                // «بیست و پنج», «صد و بیست و سه»
                while (j + 1 < tokens.size && tokens[j] == "و" && tokens[j + 1] in ALL &&
                    isNumberToken(tokens[j + 1], "و", tokens.getOrNull(j + 2)) &&
                    ALL.getValue(tokens[j + 1]) < lastPart(total)
                ) {
                    total += ALL.getValue(tokens[j + 1])
                    j += 2
                }
                out += total.toString()
                i = j
            } else {
                out += tok
                i++
            }
        }
        return out.joinToString(" ")
    }

    /** The "size" of the last added part, so «بیست و پنج» chains but «پنج و بیست» does not. */
    private fun lastPart(total: Int): Int = when {
        total % 10 != 0 -> total % 10 // ends in a ones word → nothing smaller may follow
        total % 100 != 0 -> 10        // ends in a tens word → only ones may follow
        else -> 100                   // ends in hundreds → tens / ones may follow
    }

    /** Ordinal words used for days of the month: «پنجم» → 5, «بیست و یکم» → 21. */
    private val ORD_SMALL = listOf(
        "", "اول", "دوم", "سوم", "چهارم", "پنجم", "ششم", "هفتم", "هشتم", "نهم", "دهم",
        "یازدهم", "دوازدهم", "سیزدهم", "چهاردهم", "پانزدهم", "شانزدهم", "هفدهم", "هجدهم", "نوزدهم"
    )

    fun ordinalWordsToDigits(text: String): String {
        var t = text
        // 31, 30, 21–29 first so «بیست و یکم» is not eaten by «یکم»/«بیستم»
        t = t.replace(Regex("(?<![\\p{L}\\d])سی و یکم(?![\\p{L}\\d])"), "31ام")
        t = t.replace(Regex("(?<![\\p{L}\\d])سی ?ام(?![\\p{L}\\d])"), "30ام")
        for (n in 21..29) {
            t = t.replace(Regex("(?<![\\p{L}\\d])بیست و ${ORD_SMALL[n - 20]}(?![\\p{L}\\d])"), "${n}ام")
        }
        t = t.replace(Regex("(?<![\\p{L}\\d])بیستم(?![\\p{L}\\d])"), "20ام")
        // «اول» only counts as a day when a month follows («اول مهر», «اول ماه»); elsewhere it is a plain word.
        t = t.replace(Regex("(?<![\\p{L}\\d])اول(?= (?:ماه|فروردین|اردیبهشت|خرداد|تیر|مرداد|شهریور|مهر|آبان|آذر|دی|بهمن|اسفند)(?![\\p{L}]))"), "1ام")
        for (n in 2..19) {
            t = t.replace(Regex("(?<![\\p{L}\\d])${ORD_SMALL[n]}(?![\\p{L}\\d])"), "${n}ام")
        }
        return t
    }

    /** Glue weekday names that contain a number word («سه شنبه») so they are never read as a number. */
    fun glueWeekdays(text: String): String =
        text.replace(Regex("(?<![\\p{L}\\d])(یک|دو|سه|چهار|پنج) ?شنبه"), "$1شنبه")


    /** Full pipeline used by every reader: normalize → glue weekdays → numbers → ordinals. */
    fun prepare(raw: String): String =
        ordinalWordsToDigits(numberWordsToDigits(glueWeekdays(normalize(raw))))
}
