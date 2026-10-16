package com.fastlink.accounts.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
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
import com.fastlink.accounts.data.ExpenseEntity
import com.fastlink.accounts.data.ExpenseTypeEntity
import com.fastlink.accounts.databinding.FragmentExpensesBinding
import com.fastlink.accounts.util.FormatUtils
import com.fastlink.accounts.viewmodel.FastLinkViewModel
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.ceil
import kotlin.math.max

class ExpensesFragment : Fragment() {

    private var _binding: FragmentExpensesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: FastLinkViewModel by activityViewModels()

    private var allExpenses: List<ExpenseEntity> = emptyList()
    private var allExpenseTypes: List<ExpenseTypeEntity> = emptyList()
    private var selectedTypeFilter: String = "الكل"
    private var selectedStatusFilter: String = "الكل"
    private var dateFromFilter: Long? = null
    private var dateToFilter: Long? = null

    private var currentPage = 0
    private val pageSize = 20 // ترقيم صفحات عند تجاوز 20 صفاً
    private lateinit var adapter: ExpensesAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentExpensesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ExpensesAdapter(
            onEdit = { showAddOrEditExpenseDialog(it) },
            onDelete = { confirmDeleteExpense(it) }
        )
        binding.rvExpenses.layoutManager = LinearLayoutManager(requireContext())
        binding.rvExpenses.adapter = adapter

        val statuses = listOf("الكل", "مدفوع", "معلق")
        binding.spFilterExpenseStatus.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, statuses)
        binding.spFilterExpenseStatus.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedStatusFilter = statuses[position]
                currentPage = 0
                applyFiltersAndPagination()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        viewModel.expenseTypes.observe(viewLifecycleOwner) { types ->
            allExpenseTypes = types
            val typeNames = listOf("الكل") + types.map { it.name }
            binding.spFilterExpenseType.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, typeNames)
        }

        binding.spFilterExpenseType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedTypeFilter = parent?.getItemAtPosition(position)?.toString() ?: "الكل"
                currentPage = 0
                applyFiltersAndPagination()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        viewModel.expenses.observe(viewLifecycleOwner) { list ->
            allExpenses = list
            applyFiltersAndPagination()
        }

        binding.etSearchExpenseNotes.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentPage = 0
                applyFiltersAndPagination()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnFilterExpenseDate.setOnClickListener { pickExpenseDateFilter() }
        binding.btnAddExpense.setOnClickListener { showAddOrEditExpenseDialog(null) }

        binding.btnPrevPage.setOnClickListener {
            if (currentPage > 0) {
                currentPage--
                applyFiltersAndPagination()
            }
        }
        binding.btnNextPage.setOnClickListener {
            currentPage++
            applyFiltersAndPagination()
        }
    }

    private fun applyFiltersAndPagination() {
        val q = binding.etSearchExpenseNotes.text?.toString()?.trim().orEmpty()
        var filtered = allExpenses
        if (selectedTypeFilter != "الكل") {
            filtered = filtered.filter { it.type == selectedTypeFilter }
        }
        if (selectedStatusFilter != "الكل") {
            filtered = filtered.filter { it.status == selectedStatusFilter }
        }
        if (dateFromFilter != null && dateToFilter != null) {
            filtered = filtered.filter { it.date in dateFromFilter!!..dateToFilter!! }
        }
        if (q.isNotEmpty()) {
            filtered = filtered.filter {
                it.notes.contains(q, ignoreCase = true) || it.type.contains(q, ignoreCase = true)
            }
        }

        val totalSum = filtered.sumOf { it.amount }
        binding.tvExpensesPeriodTotal.text = "إجمالي المصروفات للفترة المعروضة (${filtered.size} عملية): ${FormatUtils.formatAmount(totalSum, viewModel.currencySymbol)}"

        val totalPages = max(1, ceil(filtered.size / pageSize.toDouble()).toInt())
        if (currentPage >= totalPages) currentPage = totalPages - 1

        val startIdx = currentPage * pageSize
        val pageItems = if (filtered.isEmpty()) emptyList() else filtered.subList(startIdx, (startIdx + pageSize).coerceAtMost(filtered.size))
        adapter.submitList(pageItems, viewModel.currencySymbol)

        binding.tvPageIndicator.text = "صفحة ${currentPage + 1} من $totalPages"
        binding.btnPrevPage.isEnabled = currentPage > 0
        binding.btnNextPage.isEnabled = currentPage < totalPages - 1
    }

    private fun pickExpenseDateFilter() {
        val ctx = requireContext()
        val cal = Calendar.getInstance()
        DatePickerDialog(ctx, { _, y1, m1, d1 ->
            val c1 = Calendar.getInstance().apply { set(y1, m1, d1, 0, 0, 0) }
            DatePickerDialog(ctx, { _, y2, m2, d2 ->
                val c2 = Calendar.getInstance().apply { set(y2, m2, d2, 23, 59, 59) }
                dateFromFilter = c1.timeInMillis
                dateToFilter = c2.timeInMillis
                currentPage = 0
                applyFiltersAndPagination()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showAddOrEditExpenseDialog(existing: ExpenseEntity?) {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 16)
        }

        val addNewTypeOption = "+ إضافة نوع جديد..."
        val typeOptions = allExpenseTypes.map { it.name }.toMutableList().apply { add(addNewTypeOption) }
        val spType = Spinner(ctx).apply {
            adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, typeOptions)
            if (existing != null) {
                val idx = typeOptions.indexOf(existing.type)
                if (idx >= 0) setSelection(idx)
            }
        }
        val etCustomType = EditText(ctx).apply {
            hint = "اكتب اسم نوع المصروف الجديد (يحفظ تلقائياً)"
            visibility = View.GONE
        }

        spType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                etCustomType.visibility = if (typeOptions[position] == addNewTypeOption) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val etAmount = EditText(ctx).apply {
            hint = "المبلغ"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(existing?.amount?.toString() ?: "")
        }
        val etNotes = EditText(ctx).apply {
            hint = "ملاحظات"
            setText(existing?.notes ?: "")
        }
        val statusOptions = arrayOf("مدفوع", "معلق")
        val spStatus = Spinner(ctx).apply {
            adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, statusOptions)
            if (existing?.status == "معلق") setSelection(1)
        }

        var selectedDate = existing?.date ?: System.currentTimeMillis()
        val btnDate = Button(ctx).apply {
            text = "التاريخ: ${FormatUtils.formatDate(selectedDate)}"
            setOnClickListener {
                val cal = Calendar.getInstance().apply { timeInMillis = selectedDate }
                DatePickerDialog(ctx, { _, y, m, d ->
                    val c = Calendar.getInstance().apply { set(y, m, d) }
                    selectedDate = c.timeInMillis
                    text = "التاريخ: ${FormatUtils.formatDate(selectedDate)}"
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }
        }

        layout.addView(spType)
        layout.addView(etCustomType)
        layout.addView(etAmount)
        layout.addView(spStatus)
        layout.addView(etNotes)
        layout.addView(btnDate)

        AlertDialog.Builder(ctx)
            .setTitle(if (existing == null) "إضافة مصروف جديد" else "تعديل المصروف")
            .setView(layout)
            .setPositiveButton("حفظ") { _, _ ->
                val amount = etAmount.text.toString().toDoubleOrNull() ?: 0.0
                if (amount <= 0.0) {
                    viewModel.emitMessage("يرجى إدخال مبلغ صحيح")
                    return@setPositiveButton
                }
                val chosenOption = typeOptions[spType.selectedItemPosition]
                val finalType = if (chosenOption == addNewTypeOption) {
                    etCustomType.text.toString().trim()
                } else {
                    chosenOption
                }
                if (finalType.isEmpty()) {
                    viewModel.emitMessage("يرجى اختيار أو كتابة نوع المصروف")
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    if (chosenOption == addNewTypeOption) {
                        viewModel.repository.addCustomExpenseType(finalType)
                    }
                    viewModel.repository.saveExpense(
                        ExpenseEntity(
                            id = existing?.id ?: 0L,
                            date = selectedDate,
                            type = finalType,
                            amount = amount,
                            notes = etNotes.text.toString().trim(),
                            status = statusOptions[spStatus.selectedItemPosition]
                        )
                    )
                    viewModel.emitMessage("تم حفظ المصروف")
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun confirmDeleteExpense(expense: ExpenseEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle("حذف المصروف")
            .setMessage("هل تريد حذف مصروف (${expense.type}) بقيمة ${FormatUtils.formatNumber(expense.amount)}؟")
            .setPositiveButton("حذف") { _, _ ->
                lifecycleScope.launch {
                    viewModel.repository.deleteExpense(expense)
                    viewModel.emitMessage("تم حذف المصروف")
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class ExpensesAdapter(
        val onEdit: (ExpenseEntity) -> Unit,
        val onDelete: (ExpenseEntity) -> Unit
    ) : RecyclerView.Adapter<ExpensesAdapter.VH>() {

        private var items: List<ExpenseEntity> = emptyList()
        private var currency: String = "ريال"

        fun submitList(newItems: List<ExpenseEntity>, curr: String) {
            items = newItems
            currency = curr
            notifyDataSetChanged()
        }

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvType: TextView = view.findViewById(R.id.tvExpenseType)
            val tvAmount: TextView = view.findViewById(R.id.tvExpenseAmount)
            val tvMeta: TextView = view.findViewById(R.id.tvExpenseMeta)
            val btnEdit: Button = view.findViewById(R.id.btnEditExpense)
            val btnDelete: Button = view.findViewById(R.id.btnDeleteExpense)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_expense, parent, false)
            return VH(v)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.tvType.text = "${item.type} (${item.status})"
            holder.tvAmount.text = FormatUtils.formatAmount(item.amount, currency)
            holder.tvMeta.text = "التاريخ: ${FormatUtils.formatDate(item.date)} | ملاحظات: ${item.notes.ifBlank { "-" }}"
            holder.btnEdit.setOnClickListener { onEdit(item) }
            holder.btnDelete.setOnClickListener { onDelete(item) }
        }
    }
}
