package ir.naderinia.nsa.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/** Writes a monthly finance report as CSV (opens in Excel) or PDF, and shares it. */
object ReportExporter {

    private fun exportFile(context: Context, report: FinanceReport, extension: String): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        return File(dir, "nsa-report-${report.year}-${"%02d".format(report.month)}.$extension")
    }

    private fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    // ------------------------------------------------------------------ CSV

    private fun csv(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""

    private fun row(vararg cells: Any): String = cells.joinToString(",") { csv(it.toString()) }

    fun exportCsv(context: Context, report: FinanceReport): Uri {
        val sb = StringBuilder()
        sb.append('\uFEFF') // BOM: makes Excel read the Persian text as UTF-8

        sb.appendLine(row("گزارش مالی", report.monthLabel))
        sb.appendLine()

        sb.appendLine(row("پرداخت‌های ثبت‌شده"))
        sb.appendLine(row("تاریخ", "عنوان", "نوع", "طرف حساب", "بانک", "مبلغ (تومان)"))
        report.payments.forEach {
            sb.appendLine(row(JalaliCalendar.formatDate(it.at), it.title, it.typeLabel, it.counterparty, it.bank, it.amount))
        }
        sb.appendLine(row("جمع پرداخت‌ها", "", "", "", "", report.totalPaid))
        sb.appendLine()

        sb.appendLine(row("سررسیدهای این ماه"))
        sb.appendLine(row("تاریخ سررسید", "عنوان", "نوع", "مبلغ", "پرداخت‌شده", "باقی‌مانده", "وضعیت"))
        report.dues.forEach {
            sb.appendLine(row(JalaliCalendar.formatDate(it.dueAt), it.title, it.typeLabel, it.amount, it.paid, it.remaining, it.status))
        }
        sb.appendLine(row("جمع باقی‌مانده", "", "", "", "", report.remainingDue, ""))

        val file = exportFile(context, report, "csv")
        file.writeText(sb.toString(), Charsets.UTF_8)
        return uriFor(context, file)
    }

    // ------------------------------------------------------------------ PDF

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f

    // Right edges of 5 columns (RTL: first column is the right-most) and their widths.
    private val COL_RIGHT = floatArrayOf(555f, 490f, 335f, 255f, 150f)
    private val COL_WIDTH = floatArrayOf(60f, 150f, 70f, 90f, 100f)

    private class PdfWriter(val doc: PdfDocument) {
        var pageNumber = 0
        lateinit var page: PdfDocument.Page
        var y = 0f

        val normal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f
            textAlign = Paint.Align.RIGHT
            color = 0xFF1B1B23.toInt()
        }
        val bold = Paint(normal).apply { typeface = Typeface.DEFAULT_BOLD }
        val title = Paint(bold).apply { textSize = 18f }
        val muted = Paint(normal).apply { color = 0xFF47464F.toInt() }

        fun newPage() {
            if (pageNumber > 0) doc.finishPage(page)
            pageNumber++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create())
            y = MARGIN + 10f
        }

        fun ensure(height: Float) {
            if (y + height > PAGE_H - MARGIN) newPage()
        }

        fun line(text: String, paint: Paint, gap: Float = 18f) {
            ensure(gap)
            page.canvas.drawText(text, PAGE_W - MARGIN, y, paint)
            y += gap
        }

        fun fit(text: String, paint: Paint, maxWidth: Float): String {
            if (paint.measureText(text) <= maxWidth) return text
            val count = paint.breakText(text, true, maxWidth - paint.measureText("…"), null)
            return text.take(count) + "…"
        }

        fun cells(values: List<String>, paint: Paint) {
            ensure(16f)
            values.forEachIndexed { i, text ->
                page.canvas.drawText(fit(text, paint, COL_WIDTH[i]), COL_RIGHT[i], y, paint)
            }
            y += 16f
        }

        fun finish() {
            doc.finishPage(page)
        }
    }

    fun exportPdf(context: Context, report: FinanceReport): Uri {
        val doc = PdfDocument()
        val w = PdfWriter(doc)
        w.newPage()

        w.line("گزارش مالی — ${report.monthLabel}", w.title, 28f)
        w.line("جمع پرداخت‌ها: ${report.totalPaid.grouped()} تومان", w.bold)
        w.line("باقی‌مانده‌ی سررسیدهای این ماه: ${report.remainingDue.grouped()} تومان", w.normal, 24f)

        if (report.paidByType.isNotEmpty()) {
            w.line("پرداخت‌ها به تفکیک نوع", w.bold)
            report.paidByType.forEach { (label, amount) -> w.line("$label: ${amount.grouped()} تومان", w.normal, 16f) }
            w.y += 8f
        }
        if (report.paidByBank.isNotEmpty()) {
            w.line("پرداخت‌ها به تفکیک بانک", w.bold)
            report.paidByBank.forEach { (bank, amount) -> w.line("$bank: ${amount.grouped()} تومان", w.normal, 16f) }
            w.y += 8f
        }

        w.line("پرداخت‌های ثبت‌شده", w.bold)
        if (report.payments.isEmpty()) {
            w.line("در این ماه پرداختی ثبت نشده", w.muted, 16f)
        } else {
            w.cells(listOf("تاریخ", "عنوان", "نوع", "بانک", "مبلغ"), w.bold)
            report.payments.forEach {
                w.cells(
                    listOf(JalaliCalendar.formatDate(it.at), it.title, it.typeLabel, it.bank, it.amount.grouped()),
                    w.normal
                )
            }
        }
        w.y += 12f

        w.line("سررسیدهای این ماه", w.bold)
        if (report.dues.isEmpty()) {
            w.line("سررسیدی در این ماه نیست", w.muted, 16f)
        } else {
            w.cells(listOf("تاریخ", "عنوان", "نوع", "وضعیت", "باقی‌مانده"), w.bold)
            report.dues.forEach {
                w.cells(
                    listOf(JalaliCalendar.formatDate(it.dueAt), it.title, it.typeLabel, it.status, it.remaining.grouped()),
                    w.normal
                )
            }
        }

        w.finish()

        val file = exportFile(context, report, "pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return uriFor(context, file)
    }

    // ------------------------------------------------------------------ share

    fun share(context: Context, uri: Uri, mimeType: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, "اشتراک‌گذاری گزارش").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
