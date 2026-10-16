package com.fastlink.accounts.util

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.core.content.FileProvider
import com.fastlink.accounts.R
import com.fastlink.accounts.data.FinancialCycleRow
import com.fastlink.accounts.data.PaymentEntity
import com.fastlink.accounts.data.SaleWithPackageName
import com.fastlink.accounts.data.StoreWithSummary
import java.io.File
import java.util.Calendar

object ShareStatementHelper {

    enum class StatementLength {
        BRIEF,   // مختصر (الملخص فقط)
        MEDIUM,  // متوسط (الملخص + العمليات الرئيسية)
        FULL     // كامل (كل العمليات)
    }

    data class StatementOptions(
        val includeSalesDetails: Boolean = true,
        val includePaymentsDetails: Boolean = true,
        val summaryOnly: Boolean = false,
        val lengthMode: StatementLength = StatementLength.FULL
    )

    // ==================== STORE CONTACT ACTIONS (4.5.أ) ====================
    fun dialPhone(context: Context, phone: String) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.trim()}"))
        context.startActivity(intent)
    }

    fun openSmsApp(context: Context, phone: String, body: String = "") {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${phone.trim()}")).apply {
            if (body.isNotEmpty()) {
                putExtra("sms_body", body)
            }
        }
        context.startActivity(intent)
    }

    fun openWhatsAppDirect(context: Context, phone: String, message: String = "") {
        val normalized = FormatUtils.normalizePhoneForWhatsApp(phone)
        val encoded = Uri.encode(message)
        val url = if (message.isNotEmpty()) {
            "https://wa.me/$normalized?text=$encoded"
        } else {
            "https://wa.me/$normalized"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }

    fun shareStoreAsVCard(context: Context, storeSummary: StoreWithSummary) {
        val st = storeSummary.store
        val vcfContent = buildString {
            appendLine("BEGIN:VCARD")
            appendLine("VERSION:3.0")
            appendLine("FN:${st.name} (${st.ownerName})")
            appendLine("ORG:${st.name}")
            appendLine("TEL;TYPE=CELL:${st.phone}")
            if (st.address.isNotBlank()) {
                appendLine("ADR;TYPE=WORK:;;${st.address};;;;")
            }
            appendLine("NOTE:نوع السعر: ${storeSummary.priceTypeArabic}")
            appendLine("END:VCARD")
        }
        val vcfFile = File(context.cacheDir, "${st.name.replace(" ", "_")}.vcf")
        vcfFile.writeText(vcfContent, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            vcfFile
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/x-vcard"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة جهة اتصال المحل"))
    }

    fun openAddressInMaps(context: Context, address: String) {
        if (address.isBlank()) {
            Toast.makeText(context, "لم يتم تحديد عنوان للمحل", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = Uri.parse("geo:0,0?q=${Uri.encode(address)}")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        context.startActivity(intent)
    }

    // ==================== FORMATTED CYCLE STATEMENT (4.6.أ & 4.6.ج) ====================
    fun buildCycleStatementText(
        storeSummary: StoreWithSummary,
        cycle: FinancialCycleRow,
        currency: String,
        options: StatementOptions
    ): String {
        val sb = StringBuilder()
        sb.appendLine("📋 *كشف حساب — فاست لينك*")
        sb.appendLine("🏪 *المحل:* ${storeSummary.store.name}")
        if (storeSummary.store.ownerName.isNotBlank()) {
            sb.appendLine("👤 *المالك:* ${storeSummary.store.ownerName}")
        }
        sb.appendLine("📅 *الفترة:* من ${FormatUtils.formatDate(cycle.fromDate)} إلى ${FormatUtils.formatDate(cycle.toDate)}")
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━")
        sb.appendLine("📦 *عدد الكروت المباعة:* ${cycle.cardsSold} كرت")
        sb.appendLine("💰 *إجمالي المبيعات:* ${FormatUtils.formatAmount(cycle.totalSales, currency)}")
        sb.appendLine("✅ *إجمالي التسديدات:* ${FormatUtils.formatAmount(cycle.totalPayments, currency)}")
        sb.appendLine("🔄 *الديون المرحّلة السابقة:* ${FormatUtils.formatAmount(cycle.openingBalance, currency)}")
        sb.appendLine("📌 *الرصيد المتبقي المستحق:* ${FormatUtils.formatAmount(cycle.closingBalance, currency)}")

        if (!options.summaryOnly && options.lengthMode != StatementLength.BRIEF) {
            val maxItems = if (options.lengthMode == StatementLength.MEDIUM) 5 else Int.MAX_VALUE

            if (options.includeSalesDetails && cycle.salesInCycle.isNotEmpty()) {
                sb.appendLine("━━━━━━━━━━━━━━━━━━━━")
                sb.appendLine("🛒 *تفاصيل عمليات البيع:*")
                cycle.salesInCycle.take(maxItems).forEachIndexed { idx, item ->
                    sb.appendLine("${idx + 1}. [${FormatUtils.formatShortDate(item.sale.date)}] ${item.packageName} × ${item.sale.quantity} = ${FormatUtils.formatAmount(item.sale.total, currency)}")
                }
                if (cycle.salesInCycle.size > maxItems) {
                    sb.appendLine("... وعدد (${cycle.salesInCycle.size - maxItems}) عمليات بيع أخرى")
                }
            }

            if (options.includePaymentsDetails && cycle.paymentsInCycle.isNotEmpty()) {
                sb.appendLine("━━━━━━━━━━━━━━━━━━━━")
                sb.appendLine("💵 *تفاصيل عمليات التسديد:*")
                cycle.paymentsInCycle.take(maxItems).forEachIndexed { idx, pay ->
                    val noteStr = if (pay.notes.isNotBlank()) " (${pay.notes})" else ""
                    sb.appendLine("${idx + 1}. [${FormatUtils.formatShortDate(pay.date)}] ${FormatUtils.formatAmount(pay.amount, currency)}$noteStr")
                }
                if (cycle.paymentsInCycle.size > maxItems) {
                    sb.appendLine("... وعدد (${cycle.paymentsInCycle.size - maxItems}) دفعات أخرى")
                }
            }
        }
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━")
        sb.appendLine("شكراً لتعاملكم معنا — فاست لينك")
        return sb.toString().trim()
    }

    // ==================== FULL HISTORY SMS STATEMENT (4.6.ب — إلزامي: الكشف الكامل) ====================
    fun buildFullHistorySmsText(
        storeSummary: StoreWithSummary,
        allSales: List<SaleWithPackageName>,
        allPayments: List<PaymentEntity>,
        currency: String
    ): String {
        val firstDate = (allSales.map { it.sale.date } + allPayments.map { it.date }).minOrNull() ?: storeSummary.store.createdAt
        val lastDate = (allSales.map { it.sale.date } + allPayments.map { it.date }).maxOrNull() ?: System.currentTimeMillis()

        val sb = StringBuilder()
        sb.appendLine("كشف حساب شامل - فاست لينك")
        sb.appendLine("المحل: ${storeSummary.store.name}")
        sb.appendLine("من: ${FormatUtils.formatDate(firstDate)} إلى: ${FormatUtils.formatDate(lastDate)}")
        sb.appendLine("إجمالي الكروت: ${storeSummary.totalCardsSold}")
        sb.appendLine("إجمالي المبيعات: ${FormatUtils.formatAmount(storeSummary.totalSales, currency)}")
        sb.appendLine("إجمالي التسديدات: ${FormatUtils.formatAmount(storeSummary.totalPayments, currency)}")
        sb.appendLine("الرصيد المتبقي الكلي: ${FormatUtils.formatAmount(storeSummary.currentDebt, currency)}")
        if (allSales.isNotEmpty()) {
            sb.appendLine("--- المبيعات (${allSales.size}) ---")
            allSales.forEach { s ->
                sb.appendLine("${FormatUtils.formatShortDate(s.sale.date)}: ${s.packageName}×${s.sale.quantity}=${FormatUtils.formatNumber(s.sale.total)}")
            }
        }
        if (allPayments.isNotEmpty()) {
            sb.appendLine("--- التسديدات (${allPayments.size}) ---")
            allPayments.forEach { p ->
                sb.appendLine("${FormatUtils.formatShortDate(p.date)}: ${FormatUtils.formatNumber(p.amount)}")
            }
        }
        return sb.toString().trim()
    }

    // ==================== INTERACTIVE SHARE STATEMENT DIALOG (4.6) ====================
    fun showShareAccountStatementDialog(
        context: Context,
        storeSummary: StoreWithSummary,
        currentCycle: FinancialCycleRow,
        allSales: List<SaleWithPackageName>,
        allPayments: List<PaymentEntity>,
        currency: String,
        onCustomPeriodRequested: (Long, Long, (FinancialCycleRow) -> Unit) -> Unit
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_share_statement, null)
        val rgPeriod = view.findViewById<RadioGroup>(R.id.rgStatementPeriod)
        val btnPickDates = view.findViewById<Button>(R.id.btnPickCustomRange)
        val cbIncludeSales = view.findViewById<CheckBox>(R.id.cbIncludeSales)
        val cbIncludePayments = view.findViewById<CheckBox>(R.id.cbIncludePayments)
        val cbSummaryOnly = view.findViewById<CheckBox>(R.id.cbSummaryOnly)
        val rgLength = view.findViewById<RadioGroup>(R.id.rgStatementLength)
        val etPreview = view.findViewById<EditText>(R.id.etStatementPreview)

        val btnShareWhatsApp = view.findViewById<Button>(R.id.btnShareWhatsApp)
        val btnShareSmsFull = view.findViewById<Button>(R.id.btnShareSmsFull)
        val btnShareOtherText = view.findViewById<Button>(R.id.btnShareOtherText)
        val btnSharePdfFile = view.findViewById<Button>(R.id.btnSharePdfFile)

        var activeCycle = currentCycle

        fun currentOptions(): StatementOptions {
            val length = when (rgLength.checkedRadioButtonId) {
                R.id.rbLengthBrief -> StatementLength.BRIEF
                R.id.rbLengthMedium -> StatementLength.MEDIUM
                else -> StatementLength.FULL
            }
            return StatementOptions(
                includeSalesDetails = cbIncludeSales.isChecked,
                includePaymentsDetails = cbIncludePayments.isChecked,
                summaryOnly = cbSummaryOnly.isChecked,
                lengthMode = length
            )
        }

        fun refreshPreview() {
            etPreview.setText(buildCycleStatementText(storeSummary, activeCycle, currency, currentOptions()))
        }

        refreshPreview()

        val changeListener = CompoundButton.OnCheckedChangeListener { _, _ -> refreshPreview() }
        cbIncludeSales.setOnCheckedChangeListener(changeListener)
        cbIncludePayments.setOnCheckedChangeListener(changeListener)
        cbSummaryOnly.setOnCheckedChangeListener(changeListener)
        rgLength.setOnCheckedChangeListener { _, _ -> refreshPreview() }

        rgPeriod.setOnCheckedChangeListener { _, checkedId ->
            val cal = Calendar.getInstance()
            when (checkedId) {
                R.id.rbPeriodCurrentCycle -> {
                    btnPickDates.visibility = View.GONE
                    activeCycle = currentCycle
                    refreshPreview()
                }
                R.id.rbPeriodToday -> {
                    btnPickDates.visibility = View.GONE
                    val now = System.currentTimeMillis()
                    onCustomPeriodRequested(now, now) { computed ->
                        activeCycle = computed
                        refreshPreview()
                    }
                }
                R.id.rbPeriodThisMonth -> {
                    btnPickDates.visibility = View.GONE
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    val start = cal.timeInMillis
                    val end = System.currentTimeMillis()
                    onCustomPeriodRequested(start, end) { computed ->
                        activeCycle = computed
                        refreshPreview()
                    }
                }
                R.id.rbPeriodLastMonth -> {
                    btnPickDates.visibility = View.GONE
                    cal.add(Calendar.MONTH, -1)
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    val start = cal.timeInMillis
                    cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                    val end = cal.timeInMillis
                    onCustomPeriodRequested(start, end) { computed ->
                        activeCycle = computed
                        refreshPreview()
                    }
                }
                R.id.rbPeriodCustom -> {
                    btnPickDates.visibility = View.VISIBLE
                }
            }
        }

        btnPickDates.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(context, { _, y1, m1, d1 ->
                val c1 = Calendar.getInstance().apply { set(y1, m1, d1, 0, 0, 0) }
                DatePickerDialog(context, { _, y2, m2, d2 ->
                    val c2 = Calendar.getInstance().apply { set(y2, m2, d2, 23, 59, 59) }
                    onCustomPeriodRequested(c1.timeInMillis, c2.timeInMillis) { computed ->
                        activeCycle = computed
                        refreshPreview()
                    }
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).apply {
                    setTitle("إلى تاريخ")
                    show()
                }
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).apply {
                setTitle("من تاريخ")
                show()
            }
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle("مشاركة كشف حساب: ${storeSummary.store.name}")
            .setView(view)
            .setNegativeButton("إغلاق", null)
            .create()

        // أ) مشاركة عبر واتساب (الفترة المعروضة/المختارة فقط مع إمكانية تعديل النص قبل الإرسال)
        btnShareWhatsApp.setOnClickListener {
            val editedText = etPreview.text.toString().trim()
            openWhatsAppDirect(context, storeSummary.store.phone, editedText)
            dialog.dismiss()
        }

        // ب) مشاركة عبر SMS (ترسل الكشف الكامل لتاريخ المحل من أول عملية إلى آخر عملية)
        btnShareSmsFull.setOnClickListener {
            val fullSmsText = buildFullHistorySmsText(storeSummary, allSales, allPayments, currency)
            openSmsApp(context, storeSummary.store.phone, fullSmsText)
            dialog.dismiss()
        }

        // ج) مشاركة عبر تطبيقات أخرى كنص (الفترة المعروضة/المختارة)
        btnShareOtherText.setOnClickListener {
            val editedText = etPreview.text.toString().trim()
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "كشف حساب ${storeSummary.store.name}")
                putExtra(Intent.EXTRA_TEXT, editedText)
            }
            context.startActivity(Intent.createChooser(sendIntent, "مشاركة الكشف عبر تطبيق آخر"))
            dialog.dismiss()
        }

        // د) مشاركة كملف PDF (A4) باسم Statement_<StoreName>_<from>_<to>.pdf
        btnSharePdfFile.setOnClickListener {
            val pdfFile = PdfReportGenerator.generateStoreCyclePdf(context, storeSummary, activeCycle, currency)
            PdfReportGenerator.sharePdfFile(context, pdfFile, "مشاركة كشف الحساب PDF")
            dialog.dismiss()
        }

        dialog.show()
    }
}
