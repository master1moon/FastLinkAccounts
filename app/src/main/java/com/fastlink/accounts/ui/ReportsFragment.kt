package com.fastlink.accounts.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fastlink.accounts.R
import com.fastlink.accounts.databinding.FragmentReportsBinding
import com.fastlink.accounts.util.ExcelExporter
import com.fastlink.accounts.util.FormatUtils
import com.fastlink.accounts.util.PdfPrintAdapter
import com.fastlink.accounts.util.PdfReportGenerator
import com.fastlink.accounts.viewmodel.FastLinkViewModel
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

class ReportsFragment : Fragment() {

    private var _binding: FragmentReportsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: FastLinkViewModel by activityViewModels()

    private var currentTabIndex = 0
    private var currentHeaders: List<String> = emptyList()
    private var currentRows: List<List<String>> = emptyList()
    private var currentReportTitle: String = "التقرير المالي الشامل"
    private lateinit var rowsAdapter: SimpleReportAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReportsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rowsAdapter = SimpleReportAdapter()
        binding.rvReportRows.layoutManager = LinearLayoutManager(requireContext())
        binding.rvReportRows.adapter = rowsAdapter

        binding.tabLayoutReports.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTabIndex = tab?.position ?: 0
                refreshActiveTab()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        viewModel.dashboardStats.observe(viewLifecycleOwner) { refreshActiveTab() }
        viewModel.storesWithSummary.observe(viewLifecycleOwner) { refreshActiveTab() }
        viewModel.expenses.observe(viewLifecycleOwner) { refreshActiveTab() }

        binding.btnReportPdf.setOnClickListener {
            val pdfFile = buildCurrentTabPdf()
            PdfReportGenerator.showPdfPreviewDialog(requireContext(), pdfFile, currentReportTitle)
        }

        binding.btnReportExcel.setOnClickListener {
            val excelFile = ExcelExporter.exportToExcel(
                requireContext(),
                "Report",
                currentReportTitle,
                currentHeaders,
                currentRows
            )
            ExcelExporter.showExcelActionsDialog(requireContext(), excelFile)
        }

        binding.btnReportPrint.setOnClickListener {
            val pdfFile = buildCurrentTabPdf()
            PdfPrintAdapter.printPdfFile(requireContext(), pdfFile, currentReportTitle)
        }

        binding.btnReportShare.setOnClickListener {
            val shareText = buildString {
                appendLine("📊 *$currentReportTitle*")
                appendLine(binding.tvReportSummaryBox.text)
                appendLine("━━━━━━━━━━━━━━")
                currentRows.take(30).forEach { r ->
                    appendLine(r.joinToString(" | "))
                }
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            startActivity(Intent.createChooser(intent, "مشاركة التقرير"))
        }
    }

    private fun buildCurrentTabPdf() = PdfReportGenerator.generateTabularReportPdf(
        context = requireContext(),
        fileName = "Report_${FormatUtils.formatFileTimestamp()}.pdf",
        reportTitle = currentReportTitle,
        subtitle = binding.tvReportSummaryBox.text.toString().replace("
", " | "),
        summaryCards = emptyList(),
        headers = currentHeaders,
        rows = currentRows
    )

    private fun refreshActiveTab() {
        val cur = viewModel.currencySymbol
        val stats = viewModel.dashboardStats.value ?: return
        val stores = viewModel.storesWithSummary.value.orEmpty()
        val expenses = viewModel.expenses.value.orEmpty()

        when (currentTabIndex) {
            0 -> { // أ) تبويب الملخص + رسوم بيانية MPAndroidChart
                currentReportTitle = "ملخص الأداء المالي العام"
                binding.barChartReports.visibility = View.VISIBLE
                setupBarChart(stats.totalSales, stats.totalPayments, stats.totalExpenses, stats.netProfit)
                binding.tvReportSummaryBox.text = buildString {
                    appendLine("إجمالي المبيعات: ${FormatUtils.formatAmount(stats.totalSales, cur)} | التسديدات: ${FormatUtils.formatAmount(stats.totalPayments, cur)}")
                    append("إجمالي المصروفات: ${FormatUtils.formatAmount(stats.totalExpenses, cur)} | صافي الربح: ${FormatUtils.formatAmount(stats.netProfit, cur)}")
                }
                currentHeaders = listOf("البند المالي", "القيمة ($cur)")
                currentRows = listOf(
                    listOf("إجمالي المبيعات", FormatUtils.formatNumber(stats.totalSales)),
                    listOf("إجمالي التسديدات المستلمة", FormatUtils.formatNumber(stats.totalPayments)),
                    listOf("إجمالي الديون المستحقة", FormatUtils.formatNumber(stats.totalDebts)),
                    listOf("إجمالي المصروفات", FormatUtils.formatNumber(stats.totalExpenses)),
                    listOf("صافي الأرباح", FormatUtils.formatNumber(stats.netProfit))
                )
                rowsAdapter.submitRows(currentRows, highlightDebtRows = false)
            }
            1 -> { // ب) تبويب تقرير الديون للمحلات (مع تمييز المحلات المدينة بلون مختلف)
                currentReportTitle = "تقرير ديون المحلات"
                binding.barChartReports.visibility = View.GONE
                val indebtedCount = stores.count { it.currentDebt > 0.01 }
                binding.tvReportSummaryBox.text = "إجمالي المحلات: ${stores.size} | المحلات المدينة: $indebtedCount | إجمالي الديون: ${FormatUtils.formatAmount(stats.totalDebts, cur)}"
                currentHeaders = listOf("اسم المحل", "المبيعات", "المدفوع", "المتبقي", "نوع السعر", "آخر عملية")
                currentRows = stores.map { s ->
                    listOf(
                        s.store.name,
                        FormatUtils.formatNumber(s.totalSales),
                        FormatUtils.formatNumber(s.totalPayments),
                        FormatUtils.formatNumber(s.currentDebt),
                        s.priceTypeArabic,
                        s.lastTransactionDate?.let { FormatUtils.formatDate(it) } ?: "-"
                    )
                }
                rowsAdapter.submitRows(currentRows, highlightDebtRows = true)
            }
            2 -> { // ج) تبويب تقرير الأرباح والخسائر
                currentReportTitle = "تقرير الأرباح والخسائر"
                binding.barChartReports.visibility = View.GONE
                binding.tvReportSummaryBox.text = "صافي الربح الكلي = ${FormatUtils.formatAmount(stats.netProfit, cur)} (المبيعات: ${FormatUtils.formatNumber(stats.totalSales)} - المصروفات: ${FormatUtils.formatNumber(stats.totalExpenses)})"
                currentHeaders = listOf("التاريخ", "نوع المصروف", "الحالة", "المبلغ ($cur)")
                currentRows = expenses.map { e ->
                    listOf(
                        FormatUtils.formatDate(e.date),
                        e.type,
                        e.status,
                        FormatUtils.formatNumber(e.amount)
                    )
                }
                rowsAdapter.submitRows(currentRows, highlightDebtRows = false)
            }
            3 -> { // د) تبويب تقرير المقارنة (الشهر الحالي مع السابق بالقيمة والنسبة)
                currentReportTitle = "تقرير مقارنة الشهر الحالي مع الشهر السابق"
                binding.barChartReports.visibility = View.GONE
                lifecycleScope.launch {
                    val cmp = viewModel.repository.getMonthlyComparisonStats()
                    fun diffAndPct(curr: Double, prev: Double): Pair<String, String> {
                        val diff = curr - prev
                        val pct = if (prev > 0.0) ((diff / prev) * 100.0) else if (curr > 0.0) 100.0 else 0.0
                        return FormatUtils.formatNumber(diff) to String.format("%.1f%%", pct)
                    }
                    val (sDiff, sPct) = diffAndPct(cmp.currentMonthSales, cmp.previousMonthSales)
                    val (pDiff, pPct) = diffAndPct(cmp.currentMonthPayments, cmp.previousMonthPayments)
                    val (eDiff, ePct) = diffAndPct(cmp.currentMonthExpenses, cmp.previousMonthExpenses)
                    val (prDiff, prPct) = diffAndPct(cmp.currentMonthProfit, cmp.previousMonthProfit)

                    binding.tvReportSummaryBox.text = "مقارنة الشهر الحالي بالشهر السابق (الفرق بالقيمة والنسبة المئوية)"
                    currentHeaders = listOf("البند", "الشهر الحالي", "الشهر السابق", "الفرق", "النسبة")
                    currentRows = listOf(
                        listOf("المبيعات", FormatUtils.formatNumber(cmp.currentMonthSales), FormatUtils.formatNumber(cmp.previousMonthSales), sDiff, sPct),
                        listOf("التسديدات", FormatUtils.formatNumber(cmp.currentMonthPayments), FormatUtils.formatNumber(cmp.previousMonthPayments), pDiff, pPct),
                        listOf("المصروفات", FormatUtils.formatNumber(cmp.currentMonthExpenses), FormatUtils.formatNumber(cmp.previousMonthExpenses), eDiff, ePct),
                        listOf("صافي الربح", FormatUtils.formatNumber(cmp.currentMonthProfit), FormatUtils.formatNumber(cmp.previousMonthProfit), prDiff, prPct)
                    )
                    rowsAdapter.submitRows(currentRows, highlightDebtRows = false)
                }
            }
        }
    }

    private fun setupBarChart(sales: Double, payments: Double, expenses: Double, profit: Double) {
        val entries = listOf(
            BarEntry(0f, sales.toFloat()),
            BarEntry(1f, payments.toFloat()),
            BarEntry(2f, expenses.toFloat()),
            BarEntry(3f, profit.toFloat())
        )
        val dataSet = BarDataSet(entries, "الملخص المالي").apply {
            colors = listOf(
                Color.parseColor("#1565C0"),
                Color.parseColor("#2E7D32"),
                Color.parseColor("#EF6C00"),
                Color.parseColor("#00897B")
            )
            valueTextSize = 10f
        }
        binding.barChartReports.apply {
            data = BarData(dataSet)
            description.isEnabled = false
            xAxis.valueFormatter = IndexAxisValueFormatter(listOf("المبيعات", "التسديدات", "المصروفات", "صافي الربح"))
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.granularity = 1f
            axisRight.isEnabled = false
            animateY(400)
            invalidate()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class SimpleReportAdapter : RecyclerView.Adapter<SimpleReportAdapter.VH>() {
        private var rows: List<List<String>> = emptyList()
        private var highlightDebt: Boolean = false

        fun submitRows(newRows: List<List<String>>, highlightDebtRows: Boolean) {
            rows = newRows
            highlightDebt = highlightDebtRows
            notifyDataSetChanged()
        }

        class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val tv = TextView(parent.context).apply {
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 6 }
                setPadding(24, 18, 24, 18)
                textSize = 13f
            }
            return VH(tv)
        }

        override fun getItemCount(): Int = rows.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val r = rows[position]
            holder.tv.text = r.joinToString("  |  ")
            if (highlightDebt) {
                val debtVal = r.getOrNull(3)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
                if (debtVal > 0.01) {
                    holder.tv.setBackgroundColor(Color.parseColor("#FFEBEE"))
                    holder.tv.setTextColor(Color.parseColor("#C62828"))
                } else {
                    holder.tv.setBackgroundColor(Color.WHITE)
                    holder.tv.setTextColor(Color.parseColor("#263238"))
                }
            } else {
                holder.tv.setBackgroundColor(if (position % 2 == 0) Color.WHITE else Color.parseColor("#F8FAFC"))
                holder.tv.setTextColor(Color.parseColor("#263238"))
            }
        }
    }
}
