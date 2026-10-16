package com.fastlink.accounts.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fastlink.accounts.R
import com.fastlink.accounts.data.PartnerEntity
import com.fastlink.accounts.data.PartnerReportRow
import com.fastlink.accounts.data.PartnerTransactionEntity
import com.fastlink.accounts.databinding.FragmentPartnersBinding
import com.fastlink.accounts.util.ExcelExporter
import com.fastlink.accounts.util.FormatUtils
import com.fastlink.accounts.util.PdfReportGenerator
import com.fastlink.accounts.viewmodel.FastLinkViewModel
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.abs

class PartnersFragment : Fragment() {

    private var _binding: FragmentPartnersBinding? = null
    private val binding get() = _binding!!
    private val viewModel: FastLinkViewModel by activityViewModels()

    private var partnersList: List<PartnerEntity> = emptyList()
    private var partnerTxs: List<PartnerTransactionEntity> = emptyList()
    private var computedRows: List<PartnerReportRow> = emptyList()
    private var fromDate: Long = 0L
    private var toDate: Long = Long.MAX_VALUE
    private lateinit var adapter: PartnersAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPartnersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = PartnersAdapter(
            onAddWithdrawal = { showPartnerTransactionDialog(it.partner, "withdrawal") },
            onAddCarryForward = { showPartnerTransactionDialog(it.partner, "carry_forward") },
            onDeletePartner = {
                lifecycleScope.launch {
                    viewModel.repository.deletePartner(it.partner)
                    viewModel.emitMessage("تم حذف الشريك")
                }
            }
        )
        binding.rvPartners.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPartners.adapter = adapter

        viewModel.partners.observe(viewLifecycleOwner) {
            partnersList = it
            recalculatePartnerReports()
        }
        viewModel.partnerTransactions.observe(viewLifecycleOwner) {
            partnerTxs = it
            recalculatePartnerReports()
        }
        viewModel.allPayments.observe(viewLifecycleOwner) { recalculatePartnerReports() }
        viewModel.expenses.observe(viewLifecycleOwner) { recalculatePartnerReports() }
        viewModel.allSales.observe(viewLifecycleOwner) { recalculatePartnerReports() }

        binding.btnAddPartner.setOnClickListener { showAddPartnerDialog() }
        binding.btnPartnerDateRange.setOnClickListener { pickDateRange() }

        binding.btnExportPartnersPdf.setOnClickListener {
            val cur = viewModel.currencySymbol
            val headers = listOf("الشريك", "النسبة %", "الرصيد المرحّل", "السحوبات", "حصة الأرباح", "الصافي المستحق")
            val rows = computedRows.map { r ->
                listOf(
                    r.partner.name,
                    "${r.partner.percentage}%",
                    FormatUtils.formatNumber(r.carriedOverBalance),
                    FormatUtils.formatNumber(r.withdrawals),
                    FormatUtils.formatNumber(r.profitShare),
                    FormatUtils.formatNumber(r.netPayableProfit)
                )
            }
            val pdf = PdfReportGenerator.generateTabularReportPdf(
                requireContext(),
                "Partners_Report_${FormatUtils.formatFileTimestamp()}.pdf",
                "تقرير حسابات وأرباح الشركاء",
                binding.tvPartnersOverallSummary.text.toString().replace("
", " | "),
                emptyList(),
                headers,
                rows
            )
            PdfReportGenerator.showPdfPreviewDialog(requireContext(), pdf, "تقرير الشركاء")
        }

        binding.btnExportPartnersExcel.setOnClickListener {
            val headers = listOf("الشريك", "الهاتف", "النسبة %", "الرصيد المرحّل", "السحوبات", "حصة الأرباح", "الصافي المستحق")
            val rows = computedRows.map { r ->
                listOf(
                    r.partner.name,
                    r.partner.phone,
                    "${r.partner.percentage}%",
                    FormatUtils.formatNumber(r.carriedOverBalance),
                    FormatUtils.formatNumber(r.withdrawals),
                    FormatUtils.formatNumber(r.profitShare),
                    FormatUtils.formatNumber(r.netPayableProfit)
                )
            }
            val excel = ExcelExporter.exportToExcel(requireContext(), "Partners_Report", "تقارير الشركاء", headers, rows)
            ExcelExporter.showExcelActionsDialog(requireContext(), excel)
        }
    }

    private fun recalculatePartnerReports() {
        val cur = viewModel.currencySymbol
        val sumPct = partnersList.sumOf { it.percentage }
        binding.tvPartnerPercentageWarning.visibility =
            if (partnersList.isNotEmpty() && abs(sumPct - 100.0) > 0.01) View.VISIBLE else View.GONE
        binding.tvPartnerPercentageWarning.text = "⚠️ تنبيه: مجموع نسب الشركاء حالياً (${FormatUtils.formatNumber(sumPct)}%) لا يساوي 100%"

        val periodPayments = viewModel.allPayments.value.orEmpty().filter { it.date in fromDate..toDate }
        val periodExpenses = viewModel.expenses.value.orEmpty().filter { it.date in fromDate..toDate }
        val periodSales = viewModel.allSales.value.orEmpty().filter { it.date in fromDate..toDate }

        val totalPay = periodPayments.sumOf { it.amount }
        val totalExp = periodExpenses.sumOf { it.amount }
        val totalSales = periodSales.sumOf { it.total }
        val netProfit = totalSales - totalExp

        binding.tvPartnersOverallSummary.text = buildString {
            appendLine("إجمالي المبيعات للفترة: ${FormatUtils.formatAmount(totalSales, cur)} | التسديدات (${periodPayments.size}): ${FormatUtils.formatAmount(totalPay, cur)}")
            append("إجمالي المصروفات (${periodExpenses.size}): ${FormatUtils.formatAmount(totalExp, cur)} | صافي الأرباح: ${FormatUtils.formatAmount(netProfit, cur)}")
        }

        computedRows = partnersList.map { p ->
            val pTxs = partnerTxs.filter { it.partnerId == p.id && it.date in fromDate..toDate }
            val carried = pTxs.filter { it.type == "carry_forward" }.sumOf { it.amount }
            val withdrawals = pTxs.filter { it.type == "withdrawal" }.sumOf { it.amount }
            val share = netProfit * (p.percentage / 100.0)
            val netPayable = carried + share - withdrawals
            PartnerReportRow(
                partner = p,
                carriedOverBalance = carried,
                withdrawals = withdrawals,
                profitShare = share,
                netPayableProfit = netPayable
            )
        }
        adapter.submitList(computedRows, cur)
    }

    private fun showAddPartnerDialog() {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 16)
        }
        val etName = EditText(ctx).apply { hint = "اسم الشريك" }
        val etPct = EditText(ctx).apply {
            hint = "نسبة الشريك % (مثال: 50)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val etPhone = EditText(ctx).apply {
            hint = "رقم الهاتف"
            inputType = InputType.TYPE_CLASS_PHONE
        }
        layout.addView(etName)
        layout.addView(etPct)
        layout.addView(etPhone)

        AlertDialog.Builder(ctx)
            .setTitle("إضافة شريك جديد")
            .setView(layout)
            .setPositiveButton("حفظ") { _, _ ->
                val name = etName.text.toString().trim()
                val pct = etPct.text.toString().toDoubleOrNull() ?: 0.0
                if (name.isEmpty() || pct <= 0.0) {
                    viewModel.emitMessage("يرجى إدخال اسم الشريك ونسبته")
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    viewModel.repository.savePartner(PartnerEntity(name = name, percentage = pct, phone = etPhone.text.toString().trim()))
                    viewModel.emitMessage("تم حفظ الشريك")
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showPartnerTransactionDialog(partner: PartnerEntity, txType: String) {
        val ctx = requireContext()
        val etAmount = EditText(ctx).apply {
            hint = if (txType == "withdrawal") "مبلغ السحب" else "مبلغ الرصيد المرحّل"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        AlertDialog.Builder(ctx)
            .setTitle("${if (txType == "withdrawal") "تسجيل سحب للشريك" else "تسجيل رصيد مرحّل"}: ${partner.name}")
            .setView(etAmount)
            .setPositiveButton("حفظ") { _, _ ->
                val amt = etAmount.text.toString().toDoubleOrNull() ?: return@setPositiveButton
                lifecycleScope.launch {
                    viewModel.repository.addPartnerTransaction(partner.id, txType, amt)
                    viewModel.emitMessage("تم تسجيل العملية للشريك")
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun pickDateRange() {
        val ctx = requireContext()
        val cal = Calendar.getInstance()
        DatePickerDialog(ctx, { _, y1, m1, d1 ->
            val c1 = Calendar.getInstance().apply { set(y1, m1, d1, 0, 0, 0) }
            DatePickerDialog(ctx, { _, y2, m2, d2 ->
                val c2 = Calendar.getInstance().apply { set(y2, m2, d2, 23, 59, 59) }
                fromDate = c1.timeInMillis
                toDate = c2.timeInMillis
                recalculatePartnerReports()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class PartnersAdapter(
        val onAddWithdrawal: (PartnerReportRow) -> Unit,
        val onAddCarryForward: (PartnerReportRow) -> Unit,
        val onDeletePartner: (PartnerReportRow) -> Unit
    ) : RecyclerView.Adapter<PartnersAdapter.VH>() {

        private var items: List<PartnerReportRow> = emptyList()
        private var currency: String = "ريال"

        fun submitList(newItems: List<PartnerReportRow>, curr: String) {
            items = newItems
            currency = curr
            notifyDataSetChanged()
        }

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvPkgName)
            val tvDate: TextView = view.findViewById(R.id.tvPkgDate)
            val tvDetails: TextView = view.findViewById(R.id.tvPkgPrices)
            val btnWithdrawal: Button = view.findViewById(R.id.btnEditPackage)
            val btnDelete: Button = view.findViewById(R.id.btnDeletePackage)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_package, parent, false)
            return VH(v)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.tvName.text = "${item.partner.name} (${item.partner.percentage}%)"
            holder.tvDate.text = item.partner.phone
            holder.tvDetails.text = buildString {
                appendLine("الرصيد المرحّل: ${FormatUtils.formatAmount(item.carriedOverBalance, currency)} | السحوبات: ${FormatUtils.formatAmount(item.withdrawals, currency)}")
                appendLine("حصته من الأرباح: ${FormatUtils.formatAmount(item.profitShare, currency)}")
                append("صافي الأرباح المستحقة: ${FormatUtils.formatAmount(item.netPayableProfit, currency)}")
            }
            holder.btnWithdrawal.text = "+ تسجيل سحب"
            holder.btnWithdrawal.setOnClickListener { onAddWithdrawal(item) }
            holder.btnWithdrawal.setOnLongClickListener {
                onAddCarryForward(item)
                true
            }
            holder.btnDelete.setOnClickListener { onDeletePartner(item) }
        }
    }
}
