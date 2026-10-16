package com.fastlink.accounts.ui

import android.app.AlertDialog
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
import com.fastlink.accounts.data.StoreEntity
import com.fastlink.accounts.data.StoreHasDebtException
import com.fastlink.accounts.data.StoreWithSummary
import com.fastlink.accounts.databinding.FragmentStoresBinding
import com.fastlink.accounts.util.FormatUtils
import com.fastlink.accounts.viewmodel.FastLinkViewModel
import kotlinx.coroutines.launch

class StoresFragment : Fragment() {

    private var _binding: FragmentStoresBinding? = null
    private val binding get() = _binding!!
    private val viewModel: FastLinkViewModel by activityViewModels()

    private var allStores: List<StoreWithSummary> = emptyList()
    private lateinit var adapter: StoresAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStoresBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = StoresAdapter(
            onOpenDetails = { (activity as? MainActivity)?.openStoreDetails(it.store.id) },
            onEdit = { showAddOrEditStoreDialog(it.store) },
            onDelete = { confirmAndDeleteStore(it) }
        )
        binding.rvStores.layoutManager = LinearLayoutManager(requireContext())
        binding.rvStores.adapter = adapter

        viewModel.storesWithSummary.observe(viewLifecycleOwner) { list ->
            allStores = list
            filterStores()
        }

        binding.etSearchStore.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterStores()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnAddStore.setOnClickListener { showAddOrEditStoreDialog(null) }
    }

    private fun filterStores() {
        val q = binding.etSearchStore.text?.toString()?.trim().orEmpty()
        val filtered = if (q.isEmpty()) {
            allStores
        } else {
            allStores.filter {
                it.store.name.contains(q, ignoreCase = true) ||
                    it.store.phone.contains(q, ignoreCase = true) ||
                    it.store.ownerName.contains(q, ignoreCase = true)
            }
        }
        adapter.submitList(filtered, viewModel.currencySymbol)
    }

    private fun showAddOrEditStoreDialog(existing: StoreEntity?) {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 16)
        }
        val etName = EditText(ctx).apply {
            hint = "اسم المحل (إجباري)"
            setText(existing?.name ?: "")
        }
        val etOwner = EditText(ctx).apply {
            hint = "اسم صاحب المحل"
            setText(existing?.ownerName ?: "")
        }
        val etPhone = EditText(ctx).apply {
            hint = "رقم الهاتف (إجباري)"
            inputType = InputType.TYPE_CLASS_PHONE
            setText(existing?.phone ?: "")
        }
        val etAddress = EditText(ctx).apply {
            hint = "العنوان"
            setText(existing?.address ?: "")
        }
        val priceTypesLabels = arrayOf("سعر التجزئة", "سعر الجملة", "سعر الموزعين")
        val priceTypesValues = arrayOf("retail", "wholesale", "distributor")
        val spPriceType = Spinner(ctx).apply {
            adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, priceTypesLabels)
            val idx = priceTypesValues.indexOf(existing?.priceType ?: "wholesale").coerceAtLeast(0)
            setSelection(idx)
        }
        val etNotes = EditText(ctx).apply {
            hint = "ملاحظات"
            setText(existing?.notes ?: "")
        }

        layout.addView(etName)
        layout.addView(etOwner)
        layout.addView(etPhone)
        layout.addView(etAddress)
        layout.addView(spPriceType)
        layout.addView(etNotes)

        AlertDialog.Builder(ctx)
            .setTitle(if (existing == null) "إضافة محل جديد" else "تعديل بيانات المحل")
            .setView(layout)
            .setPositiveButton("حفظ") { _, _ ->
                val name = etName.text.toString().trim()
                val phone = etPhone.text.toString().trim()
                if (name.isEmpty()) {
                    viewModel.emitMessage("اسم المحل إجباري")
                    return@setPositiveButton
                }
                if (!viewModel.repository.isValidPhoneNumber(phone)) {
                    viewModel.emitMessage("يرجى إدخال رقم هاتف صحيح (7 إلى 15 رقماً)")
                    return@setPositiveButton
                }
                val selectedPriceType = priceTypesValues[spPriceType.selectedItemPosition]
                lifecycleScope.launch {
                    viewModel.repository.saveStore(
                        StoreEntity(
                            id = existing?.id ?: 0L,
                            name = name,
                            ownerName = etOwner.text.toString().trim(),
                            phone = phone,
                            address = etAddress.text.toString().trim(),
                            priceType = selectedPriceType,
                            cycleType = existing?.cycleType ?: "weekly",
                            notes = etNotes.text.toString().trim(),
                            createdAt = existing?.createdAt ?: System.currentTimeMillis()
                        )
                    )
                    viewModel.emitMessage("تم حفظ بيانات المحل بنجاح")
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun confirmAndDeleteStore(item: StoreWithSummary) {
        AlertDialog.Builder(requireContext())
            .setTitle("حذف المحل")
            .setMessage("هل تريد حذف محل (${item.store.name}) وكافة سجلاته؟")
            .setPositiveButton("حذف") { _, _ ->
                lifecycleScope.launch {
                    try {
                        viewModel.repository.deleteStore(item, forceConfirm = false)
                        viewModel.emitMessage("تم حذف المحل")
                    } catch (e: StoreHasDebtException) {
                        AlertDialog.Builder(requireContext())
                            .setTitle("⚠️ تنبيه مديونية مستحقة")
                            .setMessage(e.message)
                            .setPositiveButton("تأكيد الحذف مع المديونية") { _, _ ->
                                lifecycleScope.launch {
                                    viewModel.repository.deleteStore(item, forceConfirm = true)
                                    viewModel.emitMessage("تم حذف المحل وكافة سجلاته")
                                }
                            }
                            .setNegativeButton("إلغاء", null)
                            .show()
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

    private class StoresAdapter(
        val onOpenDetails: (StoreWithSummary) -> Unit,
        val onEdit: (StoreWithSummary) -> Unit,
        val onDelete: (StoreWithSummary) -> Unit
    ) : RecyclerView.Adapter<StoresAdapter.VH>() {

        private var items: List<StoreWithSummary> = emptyList()
        private var currency: String = "ريال"

        fun submitList(newItems: List<StoreWithSummary>, curr: String) {
            items = newItems
            currency = curr
            notifyDataSetChanged()
        }

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val card: View = view.findViewById(R.id.cardStoreItem)
            val tvName: TextView = view.findViewById(R.id.tvStoreName)
            val tvPriceType: TextView = view.findViewById(R.id.tvStorePriceType)
            val tvOwnerPhone: TextView = view.findViewById(R.id.tvStoreOwnerAndPhone)
            val tvBalance: TextView = view.findViewById(R.id.tvStoreBalanceSummary)
            val btnDetails: Button = view.findViewById(R.id.btnOpenStoreDetails)
            val btnEdit: Button = view.findViewById(R.id.btnEditStore)
            val btnDelete: Button = view.findViewById(R.id.btnDeleteStore)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_store, parent, false)
            return VH(v)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.tvName.text = item.store.name
            holder.tvPriceType.text = "سعر ${item.priceTypeArabic}"
            holder.tvOwnerPhone.text = "المالك: ${item.store.ownerName.ifBlank { "-" }} | هاتف: ${item.store.phone}"
            holder.tvBalance.text = "المبيعات: ${FormatUtils.formatAmount(item.totalSales, currency)} | المدفوع: ${FormatUtils.formatAmount(item.totalPayments, currency)} | المتبقي: ${FormatUtils.formatAmount(item.currentDebt, currency)}"
            holder.card.setOnClickListener { onOpenDetails(item) }
            holder.btnDetails.setOnClickListener { onOpenDetails(item) }
            holder.btnEdit.setOnClickListener { onEdit(item) }
            holder.btnDelete.setOnClickListener { onDelete(item) }
        }
    }
}
