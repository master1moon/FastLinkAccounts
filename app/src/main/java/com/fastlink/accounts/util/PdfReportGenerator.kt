package com.fastlink.accounts.util

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.fastlink.accounts.R
import com.fastlink.accounts.data.FinancialCycleRow
import com.fastlink.accounts.data.StoreWithSummary
import java.io.File
import java.io.FileOutputStream

object PdfReportGenerator {

    // A4 Dimensions in Points (210 x 297 mm = 595 x 842 pt)
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 36f

    data class SummaryCardItem(val label: String, val value: String, val colorHex: String = "#0D47A1")

    private fun loadTypeface(context: Context, bold: Boolean): Typeface {
        return try {
            val path = if (bold) "fonts/cairo_bold.ttf" else "fonts/cairo_regular.ttf"
            Typeface.createFromAsset(context.assets, path)
        } catch (e: Exception) {
            Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    fun generateTabularReportPdf(
        context: Context,
        fileName: String,
        reportTitle: String,
        subtitle: String,
        summaryCards: List<SummaryCardItem>,
        headers: List<String>, // Ordered Right-to-Left (index 0 is rightmost column)
        rows: List<List<String>>,
        footerNote: String = "تم الإنشاء بواسطة تطبيق فاست لينك - حسابات (الإصدار 01.07)"
    ): File {
        val pdfDocument = PdfDocument()
        val regularFont = loadTypeface(context, false)
        val boldFont = loadTypeface(context, true)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldFont
            textSize = 18f
            color = Color.WHITE
            textAlign = Paint.Align.RIGHT
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularFont
            textSize = 11f
            color = Color.parseColor("#E3F2FD")
            textAlign = Paint.Align.RIGHT
        }
        val cardLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularFont
            textSize = 9.5f
            color = Color.parseColor("#546E7A")
            textAlign = Paint.Align.RIGHT
        }
        val cardValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldFont
            textSize = 12f
            color = Color.parseColor("#0D47A1")
            textAlign = Paint.Align.RIGHT
        }
        val headerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldFont
            textSize = 10f
            color = Color.WHITE
            textAlign = Paint.Align.RIGHT
        }
        val rowTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularFont
            textSize = 9.5f
            color = Color.parseColor("#263238")
            textAlign = Paint.Align.RIGHT
        }
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularFont
            textSize = 8.5f
            color = Color.parseColor("#78909C")
            textAlign = Paint.Align.CENTER
        }
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.7f
            color = Color.parseColor("#CFD8DC")
        }

        val usableWidth = PAGE_WIDTH - (2 * MARGIN)
        val colCount = headers.size.coerceAtLeast(1)
        val colWidth = usableWidth / colCount

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        var currentY = MARGIN

        fun drawHeaderBanner(c: Canvas): Float {
            var y = MARGIN
            val bannerRect = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 72f)
            boxPaint.color = Color.parseColor("#0D47A1")
            c.drawRoundRect(bannerRect, 8f, 8f, boxPaint)

            // Draw App Name & Report Title RTL (Right aligned)
            c.drawText("فاست لينك - حسابات", PAGE_WIDTH - MARGIN - 16f, y + 26f, titlePaint)
            c.drawText(reportTitle, PAGE_WIDTH - MARGIN - 16f, y + 48f, subtitlePaint.apply { textSize = 13f; typeface = boldFont })
            c.drawText(subtitle, PAGE_WIDTH - MARGIN - 16f, y + 64f, subtitlePaint.apply { textSize = 9.5f; typeface = regularFont })

            // Date on top-left
            val leftPaint = Paint(subtitlePaint).apply { textAlign = Paint.Align.LEFT }
            c.drawText("التاريخ: ${FormatUtils.formatDateTime(System.currentTimeMillis())}", MARGIN + 14f, y + 26f, leftPaint)
            y += 84f
            return y
        }

        fun drawTableHeader(c: Canvas, yPos: Float): Float {
            val headerHeight = 26f
            boxPaint.color = Color.parseColor("#1565C0")
            c.drawRect(MARGIN, yPos, PAGE_WIDTH - MARGIN, yPos + headerHeight, boxPaint)
            for (i in headers.indices) {
                val colRight = PAGE_WIDTH - MARGIN - (i * colWidth)
                c.drawText(headers[i], colRight - 6f, yPos + 17f, headerTextPaint)
            }
            return yPos + headerHeight
        }

        fun drawFooter(c: Canvas, pgNum: Int) {
            c.drawText("$footerNote  |  صفحة $pgNum", PAGE_WIDTH / 2f, PAGE_HEIGHT - 16f, footerPaint)
        }

        currentY = drawHeaderBanner(canvas)

        // Draw Summary Cards (up to 4 per row, RTL)
        if (summaryCards.isNotEmpty()) {
            val perRow = summaryCards.size.coerceAtMost(4)
            val gap = 8f
            val cardW = (usableWidth - gap * (perRow - 1)) / perRow
            val cardH = 46f
            summaryCards.chunked(4).forEach { chunk ->
                chunk.forEachIndexed { idx, card ->
                    val rightEdge = PAGE_WIDTH - MARGIN - idx * (cardW + gap)
                    val leftEdge = rightEdge - cardW
                    val rect = RectF(leftEdge, currentY, rightEdge, currentY + cardH)
                    boxPaint.color = Color.parseColor("#F5F7FA")
                    canvas.drawRoundRect(rect, 6f, 6f, boxPaint)
                    canvas.drawRoundRect(rect, 6f, 6f, borderPaint)
                    canvas.drawText(card.label, rightEdge - 8f, currentY + 17f, cardLabelPaint)
                    cardValuePaint.color = runCatching { Color.parseColor(card.colorHex) }.getOrDefault(Color.parseColor("#0D47A1"))
                    canvas.drawText(card.value, rightEdge - 8f, currentY + 36f, cardValuePaint)
                }
                currentY += cardH + 10f
            }
            currentY += 4f
        }

        currentY = drawTableHeader(canvas, currentY)
        val rowHeight = 22f

        rows.forEachIndexed { rowIndex, rowData ->
            if (currentY + rowHeight > PAGE_HEIGHT - 42f) {
                drawFooter(canvas, pageNumber)
                pdfDocument.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                currentY = MARGIN
                currentY = drawTableHeader(canvas, currentY)
            }

            boxPaint.color = if (rowIndex % 2 == 0) Color.WHITE else Color.parseColor("#F8FAFC")
            canvas.drawRect(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + rowHeight, boxPaint)
            canvas.drawRect(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY + rowHeight, borderPaint)

            for (i in headers.indices) {
                val cellText = rowData.getOrNull(i) ?: ""
                val colRight = PAGE_WIDTH - MARGIN - (i * colWidth)
                val maxChars = (colWidth / 5.2f).toInt().coerceAtLeast(8)
                val display = if (cellText.length > maxChars) cellText.take(maxChars - 1) + "…" else cellText
                canvas.drawText(display, colRight - 6f, currentY + 15f, rowTextPaint)
            }
            currentY += rowHeight
        }

        drawFooter(canvas, pageNumber)
        pdfDocument.finishPage(page)

        val outDir = getReportsOutputDir(context)
        val safeFileName = if (fileName.endsWith(".pdf")) fileName else "$fileName.pdf"
        val outFile = File(outDir, safeFileName)
        FileOutputStream(outFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        NotificationHelper.showNotification(
            context,
            NotificationHelper.CHANNEL_EXPORT,
            "تم حفظ ملف PDF بنجاح",
            "المسار: ${outFile.absolutePath}"
        )
        return outFile
    }

    fun generateStoreCyclePdf(
        context: Context,
        storeSummary: StoreWithSummary,
        cycle: FinancialCycleRow,
        currency: String
    ): File {
        val fromStr = FormatUtils.formatDate(cycle.fromDate).replace("/", "-")
        val toStr = FormatUtils.formatDate(cycle.toDate).replace("/", "-")
        val cleanName = storeSummary.store.name.replace(Regex("[^\p{L}\p{N}_-]"), "_")
        val fileName = "Statement_${cleanName}_${fromStr}_${toStr}.pdf"

        val summaryCards = listOf(
            SummaryCardItem("إجمالي مبيعات الدورة", FormatUtils.formatAmount(cycle.totalSales, currency), "#1565C0"),
            SummaryCardItem("إجمالي تسديدات الدورة", FormatUtils.formatAmount(cycle.totalPayments, currency), "#2E7D32"),
            SummaryCardItem("الديون المرحّلة السابقة", FormatUtils.formatAmount(cycle.openingBalance, currency), "#EF6C00"),
            SummaryCardItem("الرصيد المتبقي النهائي", FormatUtils.formatAmount(cycle.closingBalance, currency), "#C62828")
        )

        val headers = listOf("التاريخ", "نوع العملية", "البيان / الباقة", "الكمية", "السعر", "المبلغ ($currency)")
        val combinedRows = mutableListOf<Pair<Long, List<String>>>()

        cycle.salesInCycle.forEach { s ->
            combinedRows.add(
                s.sale.date to listOf(
                    FormatUtils.formatDate(s.sale.date),
                    "بيع كروت",
                    s.packageName,
                    s.sale.quantity.toString(),
                    FormatUtils.formatNumber(s.sale.unitPrice),
                    FormatUtils.formatNumber(s.sale.total)
                )
            )
        }
        cycle.paymentsInCycle.forEach { p ->
            combinedRows.add(
                p.date to listOf(
                    FormatUtils.formatDate(p.date),
                    "تسديد دفعة",
                    p.notes.ifBlank { "دفعة نقدية" },
                    "-",
                    "-",
                    FormatUtils.formatNumber(p.amount)
                )
            )
        }
        val sortedRows = combinedRows.sortedBy { it.first }.map { it.second }

        return generateTabularReportPdf(
            context = context,
            fileName = fileName,
            reportTitle = "كشف حساب محل: ${storeSummary.store.name}",
            subtitle = "الفترة من ${FormatUtils.formatDate(cycle.fromDate)} إلى ${FormatUtils.formatDate(cycle.toDate)} | الهاتف: ${storeSummary.store.phone} | السعر: ${storeSummary.priceTypeArabic}",
            summaryCards = summaryCards,
            headers = headers,
            rows = sortedRows
        )
    }

    fun getReportsOutputDir(context: Context): File {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(downloads, "FastLink_Reports")
        if (!dir.exists() && dir.mkdirs()) {
            return dir
        }
        val fallback = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "FastLink_Reports")
        if (!fallback.exists()) fallback.mkdirs()
        return fallback
    }

    fun sharePdfFile(context: Context, pdfFile: File, title: String = "مشاركة ملف PDF") {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }

    fun showPdfPreviewDialog(context: Context, pdfFile: File, reportTitle: String) {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val scrollView = ScrollView(context)
        val pagesHolder = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        try {
            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val maxPreviewPages = renderer.pageCount.coerceAtMost(5)
            for (i in 0 until maxPreviewPages) {
                val page = renderer.openPage(i)
                val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val iv = ImageView(context).apply {
                    setImageBitmap(bmp)
                    adjustViewBounds = true
                    setPadding(0, 0, 0, 16)
                }
                pagesHolder.addView(iv)
            }
            renderer.close()
            pfd.close()
        } catch (e: Exception) {
            Toast.makeText(context, "تم حفظ الملف: ${pdfFile.name}", Toast.LENGTH_SHORT).show()
        }

        scrollView.addView(pagesHolder)
        container.addView(scrollView)

        AlertDialog.Builder(context)
            .setTitle("معاينة PDF: $reportTitle")
            .setView(container)
            .setPositiveButton("مشاركة") { _, _ ->
                sharePdfFile(context, pdfFile, reportTitle)
            }
            .setNeutralButton("طباعة") { _, _ ->
                PdfPrintAdapter.printPdfFile(context, pdfFile, reportTitle)
            }
            .setNegativeButton("حفظ وإغلاق") { _, _ ->
                Toast.makeText(context, "تم الحفظ في التنزيلات: ${pdfFile.name}", Toast.LENGTH_LONG).show()
            }
            .show()
    }
}
