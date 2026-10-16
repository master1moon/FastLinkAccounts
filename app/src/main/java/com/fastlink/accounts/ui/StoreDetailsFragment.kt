package com.fastlink.accounts.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fastlink.accounts.R
import com.fastlink.accounts.data.*
import com.fastlink.accounts.databinding.FragmentStoreDetailsBinding
import com.fastlink.accounts.util.FormatUtils
import com.fastlink.accounts.util.PdfReportGenerator
import com.fastlink.accounts.util.ShareStatementHelper
import com.fastlink.accounts.viewmodel.FastLinkViewModel
import kotlinx.coroutines.launch
import java.util.Calendar

class StoreDetailsFragment : Fragment() {

    private var _binding: FragmentStoreDetailsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: FastLinkViewModel by activityViewModels()

    private var storeId: Long = 0L
    private var currentStoreSummary: StoreWithSummary? = null
    private var availablePackages: List<PackageWithInventory> = emptyList()
    private var currentCycles: List<FinancialCycleRow> = emptyList()
    private var storeSalesWithPkg: List<SaleWithPackageName> = emptyList()
    private var storePayments: List<PaymentEntity> = emptyList()

    private var selectedCycleType: String = "weekly"
    private var customFromDate: Long? = null
    private var customToDate: Long? = null
    private var txFilterMode: String = "all"

    private lateinit var cyclesAdapter: CyclesAdapter
    private lateinit var timelineAdapter: TimelineAdapter

    companion object {
        private const val ARG_STORE_ID = "arg_store_id"

        fun newInstance(storeId: Long): StoreDetailsFragment {
            return StoreDetailsFragment().apply {
                arguments = Bundle().apply { putLong(ARG_STORE_ID, storeId) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storeId = arguments?.getLong(ARG_STORE_ID) ?: 0L
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStoreDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cyclesAdapter = CyclesAdapter(
            onShowDetails = { showCycleDetailsDialog(it) },
            onShareCycle = { openShareStatementDialog(it) },
            onSavePdf = {
                val summary = currentStoreSummary ?: return@CyclesAdapter
                val pdf = PdfReportGenerator.generateStoreCyclePdf(requireContext(), summary, it, viewModel.currencySymbol)
                PdfReportGenerator.showPdfPreviewDialog(requireContext(), pdf, "كشف دورة ${summary.store.name}")
            },
            onCloseCycle = { cycle ->
                lifecycleScope.launch {
                    viewModel.repository.closeCycle(cycle)
                    viewModel.emitMessage("تم إغلاق الدورة المالية وترحيل الرصيد للدورة التالية")
                    refreshCyclesAndTimeline()
                }
            }
        )
        binding.rvFinancialCycles.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFinancialCycles.adapter = cyclesAdapter

        timelineAdapter = TimelineAdapter(
            onEdit = { item ->
                when (item) {
                    is TimelineItem.SaleItem -> showEditSaleDialog(item.saleWithPkg)
                    is TimelineItem.PaymentItem -> showEditPaymentDialog(item.payment)
                }
            },
            onDelete = { item ->
                confirmDeleteTimelineItem(item)
            }
        )
        binding.rvStoreTimeline.layoutManager = LinearLayoutManager(requireContext())
        binding.rvStoreTimeline.adapter = timelineAdapter

        viewModel.packagesWithInventory.observe(viewLifecycleOwner) { pkgs ->
            availablePackages = pkgs
        }

        viewModel.storesWithSummary.observe(viewLifecycleOwner) { list ->
            val found = list.find { it.store.id == storeId } ?: return@observe
            currentStoreSummary = found
            bindStoreHeaderAndInfo(found)
            refreshCyclesAndTimeline()
        }

        viewModel.allSales.observe(viewLifecycleOwner) { refreshCyclesAndTimeline() }
        viewModel.allPayments.observe(viewLifecycleOwner) { refreshCyclesAndTimeline() }

        // اختيار نوع الدورة المالية (اليوم / الأسبوع / الشهر / مخصص)
        binding.rgCycleType.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbCycleDaily -> {
                    selectedCycleType = "daily"
                    refreshCyclesAndTimeline()
                }
                R.id.rbCycleWeekly -> {
                    selectedCycleType = "weekly"
                    refreshCyclesAndTimeline()
                }
                R.id.rbCycleMonthly -> {
                    selectedCycleType = "monthly"
                    refreshCyclesAndTimeline()
                }
                R.id.rbCycleCustom -> {
                    pickCustomCycleDates()
                }
            }
        }

        // تصفية سجل العمليات
        binding.btnFilterTxAll.setOnClickListener { txFilterMode = "all"; updateTimelineList() }
        binding.btnFilterTxSales.setOnClickListener { txFilterMode = "sales"; updateTimelineList() }
        binding.btnFilterTxPayments.setOnClickListener { txFilterMode = "payments"; updateTimelineList() }

        // الإجراءات السريعة
        binding.btnStoreSellCards.setOnClickListener { showSellToStoreDialog() }
        binding.btnStoreRecordPayment.setOnClickListener { showRecordPaymentDialog() }
        binding.btnStoreShareStatement.setOnClickListener {
            val active = currentCycles.firstOrNull()
            if (active != null) {
                openShareStatementDialog(active)
            }
        }
        binding.btnStoreSavePdfA4.setOnClickListener {
            val summary = currentStoreSummary ?: return@setOnClickListener
            val active = currentCycles.firstOrNull() ?: return@setOnClickListener
            val pdf = PdfReportGenerator.generateStoreCyclePdf(requireContext(), summary, active, viewModel.currencySymbol)
            PdfReportGenerator.showPdfPreviewDialog(requireContext(), pdf, "كشف حساب ${summary.store.name}")
        }
    }

    private fun bindStoreHeaderAndInfo(summary: StoreWithSummary) {
        val st = summary.store
        val cur = viewModel.currencySymbol
        binding.tvDetailHeaderStoreName.text = "🏪 ${st.name}"
        binding.tvDetailOverallTotals.text = "إجمالي المبيعات الكلي: ${FormatUtils.formatAmount(summary.totalSales, cur)} | إجمالي التسديدات الكلي: ${FormatUtils.formatAmount(summary.totalPayments, cur)}"
        binding.tvDetailCurrentDebt.text = "الرصيد المتبقي الحالي (الدين): ${FormatUtils.formatAmount(summary.currentDebt, cur)}"

        binding.tvStoreBasicInfo.text = buildString {
            appendLine("صاحب المحل: ${st.ownerName.ifBlank { "-" }}")
            appendLine("رقم الهاتف: ${st.phone}")
            appendLine("العنوان: ${st.address.ifBlank { "غير محدد" }}")
            appendLine("نوع السعر المطبق: ${summary.priceTypeArabic}")
            append("تاريخ الإضافة: ${FormatUtils.formatDate(st.createdAt)}")
        }

        // أيقونات الاتصال والرسائل وواتساب والمشاركة والخرائط (4.5.أ)
        binding.btnIconCall.setOnClickListener { ShareStatementHelper.dialPhone(requireContext(), st.phone) }
        binding.btnIconSms.setOnClickListener { ShareStatementHelper.openSmsApp(requireContext(), st.phone) }
        binding.btnIconWhatsApp.setOnClickListener { ShareStatementHelper.openWhatsAppDirect(requireContext(), st.phone) }
        binding.btnIconShareContact.setOnClickListener { ShareStatementHelper.shareStoreAsVCard(requireContext(), summary) }
        binding.btnIconOpenMaps.setOnClickListener { ShareStatementHelper.openAddressInMaps(requireContext(), st.address) }
    }

    private fun pickCustomCycleDates() {
        val ctx = requireContext()
        val cal = Calendar.getInstance()
        DatePickerDialog(ctx, { _, y1, m1, d1 ->
            val c1 = Calendar.getInstance().apply { set(y1, m1, d1, 0, 0, 0) }
            DatePickerDialog(ctx, { _, y2, m2, d2 ->
                val c2 = Calendar.getInstance().apply { set(y2, m2, d2, 23, 59, 59) }
                selectedCycleType = "custom"
                customFromDate = c1.timeInMillis
                customToDate = c2.timeInMillis
                refreshCyclesAndTimeline()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun refreshCyclesAndTimeline() {
        if (storeId == 0L) return
        lifecycleScope.launch {
            val cycles = viewModel.repository.computeStoreFinancialCycles(
                storeId = storeId,
                cycleType = selectedCycleType,
                customFrom = customFromDate,
                customTo = customToDate
            )
            currentCycles = cycles
            cyclesAdapter.submitList(cycles, viewModel.currencySymbol)

            val pkgMap = availablePackages.associate { it.pkg.id to it.pkg.name }
            val rawSales = viewModel.allSales.value.orEmpty().filter { it.storeId == storeId }
            storeSalesWithPkg = rawSales.map { SaleWithPackageName(it, pkgMap[it.packageId] ?: "باقة #${it.packageId}") }
            storePayments = viewModel.allPayments.value.orEmpty().filter { it.storeId == storeId }
            updateTimelineList()
        }
    }

    private fun updateTimelineList() {
        val combined = mutableListOf<TimelineItem>()
        if (txFilterMode == "all" || txFilterMode == "sales") {
            storeSalesWithPkg.forEach { combined.add(TimelineItem.SaleItem(it)) }
        }
        if (txFilterMode == "all" || txFilterMode == "payments") {
            storePayments.forEach { combined.add(TimelineItem.PaymentItem(it)) }
        }
        combined.sortByDescending { it.date }
        timelineAdapter.submitList(combined, viewModel.currencySymbol)
    }

    private fun showSellToStoreDialog() {
        val summary = currentStoreSummary ?: return
        if (availablePackages.isEmpty()) {
            viewModel.emitMessage("يرجى إضافة باقات وتحديد كمياتها في المخزون أولاً")
            return
        }
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 16)
        }
        val pkgNames = availablePackages.map { "${it.pkg.name} (المتوفر: ${it.quantity})" }
        val spPackage = Spinner(ctx).apply {
            adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, pkgNames)
        }
        val etQty = EditText(ctx).apply {
            hint = "الكمية المطلوبة"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("1")
        }
        val etUnitPrice = EditText(ctx).apply {
            hint = "سعر الوحدة (${summary.priceTypeArabic})"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        fun updateAutoPrice(pos: Int) {
            val pkg = availablePackages[pos].pkg
            val autoPrice = when (summary.store.priceType) {
                "wholesale" -> pkg.wholesalePrice
                "distributor" -> pkg.distributorPrice
                else -> pkg.retailPrice
            }
            etUnitPrice.setText(autoPrice.toString())
        }
        updateAutoPrice(0)

        spPackage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateAutoPrice(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        layout.addView(spPackage)
        layout.addView(etQty)
        layout.addView(etUnitPrice)

        AlertDialog.Builder(ctx)
            .setTitle("بيع كروت للمحل: ${summary.store.name}")
            .setView(layout)
            .setPositiveButton("تأكيد البيع") { _, _ ->
                val selectedPkg = availablePackages[spPackage.selectedItemPosition]
                val qty = etQty.text.toString().toIntOrNull() ?: 0
                val unitPrice = etUnitPrice.text.toString().toDoubleOrNull()
                if (qty <= 0) {
                    viewModel.emitMessage("يرجى إدخال كمية صحيحة")
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    try {
                        viewModel.repository.recordSale(
                            storeId = storeId,
                            packageId = selectedPkg.pkg.id,
                            quantity = qty,
                            customUnitPrice = unitPrice
                        )
                        viewModel.emitMessage("تم تسجيل عملية البيع وخصم المخزون تلقائياً")
                    } catch (e: InsufficientStockException) {
                        AlertDialog.Builder(ctx)
                            .setTitle("⚠️ نقص في المخزون")
                            .setMessage(e.message)
                            .setPositiveButton("حسناً", null)
                            .show()
                    }
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showRecordPaymentDialog() {
        val summary = currentStoreSummary ?: return
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 16)
        }
        val etAmount = EditText(ctx).apply {
            hint = "المبلغ المسدد"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val etNotes = EditText(ctx).apply {
            hint = "ملاحظات الدفعة (اختياري)"
        }
        layout.addView(etAmount)
        layout.addView(etNotes)

        AlertDialog.Builder(ctx)
            .setTitle("تسجيل دفعة من: ${summary.store.name}")
            .setView(layout)
            .setPositiveButton("حفظ الدفعة") { _, _ ->
                val amount = etAmount.text.toString().toDoubleOrNull() ?: 0.0
                if (amount <= 0.0) {
                    viewModel.emitMessage("يرجى إدخال مبلغ صحيح")
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    viewModel.repository.recordPayment(storeId, amount, etNotes.text.toString().trim())
                    viewModel.emitMessage("تم تسجيل الدفعة بنجاح")
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun openShareStatementDialog(cycle: FinancialCycleRow) {
        val summary = currentStoreSummary ?: return
        ShareStatementHelper.showShareAccountStatementDialog(
            context = requireContext(),
            storeSummary = summary,
            currentCycle = cycle,
            allSales = storeSalesWithPkg,
            allPayments = storePayments,
            currency = viewModel.currencySymbol,
            onCustomPeriodRequested = { from, to, callback ->
                lifecycleScope.launch {
                    val customCycles = viewModel.repository.computeStoreFinancialCycles(storeId, "custom", from, to)
                    customCycles.firstOrNull()?.let(callback)
                }
            }
        )
    }

    private fun showCycleDetailsDialog(cycle: FinancialCycleRow) {
        val cur = viewModel.currencySymbol
        val msg = buildString {
            appendLine("الفترة: من ${FormatUtils.formatDate(cycle.fromDate)} إلى ${FormatUtils.formatDate(cycle.toDate)}")
            appendLine("عدد الكروت المباعة: ${cycle.cardsSold}")
            appendLine("إجمالي المبيعات: ${FormatUtils.formatAmount(cycle.totalSales, cur)}")
            appendLine("إجمالي التسديدات: ${FormatUtils.formatAmount(cycle.totalPayments, cur)}")
            appendLine("الديون المرحّلة قبل الدورة: ${FormatUtils.formatAmount(cycle.openingBalance, cur)}")
            appendLine("الديون المستحقة بعد الدورة: ${FormatUtils.formatAmount(cycle.closingBalance, cur)}")
            appendLine("━━━━━━━━━━━━━━")
            appendLine("عمليات البيع (${cycle.salesInCycle.size}):")
            cycle.salesInCycle.forEach { s ->
                appendLine("• [${FormatUtils.formatShortDate(s.sale.date)}] ${s.packageName} × ${s.sale.quantity} = ${FormatUtils.formatAmount(s.sale.total, cur)}")
            }
            appendLine("━━━━━━━━━━━━━━")
            appendLine("عمليات التسديد (${cycle.paymentsInCycle.size}):")
            cycle.paymentsInCycle.forEach { p ->
                appendLine("• [${FormatUtils.formatShortDate(p.date)}] ${FormatUtils.formatAmount(p.amount, cur)} ${p.notes}")
            }
        }
        AlertDialog.Builder(requireContext())
            .setTitle("تفاصيل الدورة المالية")
            .setMessage(msg)
            .setPositiveButton("مشاركة الكشف") { _, _ -> openShareStatementDialog(cycle) }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    private fun showEditSaleDialog(saleWithPkg: SaleWithPackageName) {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 16)
        }
        val etQty = EditText(ctx).apply {
            hint = "الكمية"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(saleWithPkg.sale.quantity.toString())
        }
        val etPrice = EditText(ctx).apply {
            hint = "سعر الوحدة"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(saleWithPkg.sale.unitPrice.toString())
        }
        layout.addView(etQty)
        layout.addView(etPrice)

        AlertDialog.Builder(ctx)
            .setTitle("تعديل عملية بيع: ${saleWithPkg.packageName}")
            .setView(layout)
            .setPositiveButton("حفظ التعديل") { _, _ ->
                val newQty = etQty.text.toString().toIntOrNull() ?: return@setPositiveButton
                val newPrice = etPrice.text.toString().toDoubleOrNull() ?: return@setPositiveButton
                lifecycleScope.launch {
                    try {
                        viewModel.repository.updateSaleWithStockAdjustment(
                            saleWithPkg.sale,
                            newQty,
                            newPrice,
                            saleWithPkg.sale.date
                        )
                        viewModel.emitMessage("تم تعديل عملية البيع وتحديث المخزون")
                    } catch (e: Exception) {
                        viewModel.emitMessage(e.localizedMessage ?: "تعذر التعديل")
                    }
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showEditPaymentDialog(payment: PaymentEntity) {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 16)
        }
        val etAmount = EditText(ctx).apply {
            hint = "المبلغ"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(payment.amount.toString())
        }
        val etNotes = EditText(ctx).apply {
            hint = "الملاحظات"
            setText(payment.notes)
        }
        layout.addView(etAmount)
        layout.addView(etNotes)

        AlertDialog.Builder(ctx)
            .setTitle("تعديل عملية تسديد")
            .setView(layout)
            .setPositiveButton("حفظ") { _, _ ->
                val newAmt = etAmount.text.toString().toDoubleOrNull() ?: return@setPositiveButton
                lifecycleScope.launch {
                    viewModel.repository.updatePayment(payment.copy(amount = newAmt, notes = etNotes.text.toString().trim()))
                    viewModel.emitMessage("تم تعديل الدفعة")
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun confirmDeleteTimelineItem(item: TimelineItem) {
        AlertDialog.Builder(requireContext())
            .setTitle("تأكيد الحذف")
            .setMessage("هل أنت متأكد من حذف هذه العملية؟")
            .setPositiveButton("حذف") { _, _ ->
                lifecycleScope.launch {
                    when (item) {
                        is TimelineItem.SaleItem -> {
                            viewModel.repository.deleteSaleAndRestoreStock(item.saleWithPkg.sale)
                            viewModel.emitMessage("تم حذف البيع وإرجاع الكمية للمخزون")
                        }
                        is TimelineItem.PaymentItem -> {
                            viewModel.repository.deletePayment(item.payment)
                            viewModel.emitMessage("تم حذف الدفعة")
                        }
                    }
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class CyclesAdapter(
        val onShowDetails: (FinancialCycleRow) -> Unit,
        val onShareCycle: (FinancialCycleRow) -> Unit,
        val onSavePdf: (FinancialCycleRow) -> Unit,
        val onCloseCycle: (FinancialCycleRow) -> Unit
    ) : RecyclerView.Adapter<CyclesAdapter.VH>() {

        private var items: List<FinancialCycleRow> = emptyList()
        private var currency: String = "ريال"

        fun submitList(newItems: List<FinancialCycleRow>, curr: String) {
            items = newItems
            currency = curr
            notifyDataSetChanged()
        }

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvPeriod: TextView = view.findViewById(R.id.tvCyclePeriod)
            val tvStatus: TextView = view.findViewById(R.id.tvCycleStatus)
            val tvMetrics: TextView = view.findViewById(R.id.tvCycleMetrics)
            val btnDetails: Button = view.findViewById(R.id.btnCycleDetails)
            val btnShare: Button = view.findViewById(R.id.btnCycleShare)
            val btnPdf: Button = view.findViewById(R.id.btnCyclePdf)
            val btnClose: Button = view.findViewById(R.id.btnCycleClose)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_cycle_row, parent, false)
            return VH(v)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val row = items[position]
            holder.tvPeriod.text = "من ${FormatUtils.formatDate(row.fromDate)} إلى ${FormatUtils.formatDate(row.toDate)}"
            val isClosed = row.status == "closed"
            holder.tvStatus.text = if (isClosed) "مغلقة" else "مفتوحة"
            holder.tvStatus.setTextColor(if (isClosed) Color.parseColor("#C62828") else Color.parseColor("#2E7D32"))
            holder.tvMetrics.text = buildString {
                appendLine("الكروت المباعة: ${row.cardsSold} | مبيعات الدورة: ${FormatUtils.formatAmount(row.totalSales, currency)}")
                appendLine("تسديدات الدورة: ${FormatUtils.formatAmount(row.totalPayments, currency)}")
                append("الديون المرحّلة: ${FormatUtils.formatAmount(row.openingBalance, currency)} ⬅ المستحقة بعد الدورة: ${FormatUtils.formatAmount(row.closingBalance, currency)}")
            }
            holder.btnClose.isEnabled = !isClosed
            holder.btnDetails.setOnClickListener { onShowDetails(row) }
            holder.btnShare.setOnClickListener { onShareCycle(row) }
            holder.btnPdf.setOnClickListener { onSavePdf(row) }
            holder.btnClose.setOnClickListener { onCloseCycle(row) }
        }
    }

    private class TimelineAdapter(
        val onEdit: (TimelineItem) -> Unit,
        val onDelete: (TimelineItem) -> Unit
    ) : RecyclerView.Adapter<TimelineAdapter.VH>() {

        private var items: List<TimelineItem> = emptyList()
        private var currency: String = "ريال"

        fun submitList(newItems: List<TimelineItem>, curr: String) {
            items = newItems
            currency = curr
            notifyDataSetChanged()
        }

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tvTxTitle)
            val tvSub: TextView = view.findViewById(R.id.tvTxSubtitle)
            val btnEdit: Button = view.findViewById(R.id.btnEditTx)
            val btnDelete: Button = view.findViewById(R.id.btnDeleteTx)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_timeline_tx, parent, false)
            return VH(v)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            when (val item = items[position]) {
                is TimelineItem.SaleItem -> {
                    val s = item.saleWithPkg
                    holder.tvTitle.text = "🛒 بيع: ${s.packageName} × ${s.sale.quantity} = ${FormatUtils.formatAmount(s.sale.total, currency)}"
                    holder.tvTitle.setTextColor(Color.parseColor("#0D47A1"))
                    holder.tvSub.text = "التاريخ: ${FormatUtils.formatDateTime(s.sale.date)} | سعر الوحدة: ${FormatUtils.formatNumber(s.sale.unitPrice)}"
                    holder.btnEdit.setOnClickListener { onEdit(item) }
                    holder.btnDelete.setOnClickListener { onDelete(item) }
                }
                is TimelineItem.PaymentItem -> {
                    val p = item.payment
                    holder.tvTitle.text = "💵 تسديد دفعة: ${FormatUtils.formatAmount(p.amount, currency)}"
                    holder.tvTitle.setTextColor(Color.parseColor("#2E7D32"))
                    holder.tvSub.text = "التاريخ: ${FormatUtils.formatDateTime(p.date)} | ${p.notes.ifBlank { "دفعة نقدية" }}"
                    holder.btnEdit.setOnClickListener { onEdit(item) }
                    holder.btnDelete.setOnClickListener { onDelete(item) }
                }
            }
        }
    }
}
