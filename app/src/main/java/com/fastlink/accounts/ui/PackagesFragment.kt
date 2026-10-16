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
import com.fastlink.accounts.data.PackageEntity
import com.fastlink.accounts.data.PackageLinkedToSalesException
import com.fastlink.accounts.data.PackageWithInventory
import com.fastlink.accounts.databinding.FragmentPackagesBinding
import com.fastlink.accounts.util.FormatUtils
import com.fastlink.accounts.viewmodel.FastLinkViewModel
import kotlinx.coroutines.launch
import java.util.Calendar

class PackagesFragment : Fragment() {

    private var _binding: FragmentPackagesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: FastLinkViewModel by activityViewModels()

    private var fullList: List<PackageWithInventory> = emptyList()
    private var sortMode: String = "name"
    private lateinit var adapter: PackagesAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPackagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = PackagesAdapter(
            onEdit = { showAddOrEditPackageDialog(it.pkg) },
            onDelete = { confirmAndDeletePackage(it.pkg, force = false) }
        )
        binding.rvPackages.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPackages.adapter = adapter

        viewModel.packagesWithInventory.observe(viewLifecycleOwner) { list ->
            fullList = list
            applyFilterAndSort()
        }

        binding.etSearchPackage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilterAndSort()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnSortPkgName.setOnClickListener { sortMode = "name"; applyFilterAndSort() }
        binding.btnSortPkgPrice.setOnClickListener { sortMode = "price"; applyFilterAndSort() }
        binding.btnSortPkgDate.setOnClickListener { sortMode = "date"; applyFilterAndSort() }
        binding.btnAddPackage.setOnClickListener { showAddOrEditPackageDialog(null) }
    }

    private fun applyFilterAndSort() {
        val q = binding.etSearchPackage.text?.toString()?.trim().orEmpty()
        var filtered = if (q.isEmpty()) fullList else fullList.filter { it.pkg.name.contains(q, ignoreCase = true) }
        filtered = when (sortMode) {
            "price" -> filtered.sortedBy { it.pkg.retailPrice }
            "date" -> filtered.sortedByDescending { it.pkg.createdAt }
            else -> filtered.sortedBy { it.pkg.name }
        }
        adapter.submitList(filtered, viewModel.currencySymbol)
    }

    private fun showAddOrEditPackageDialog(existing: PackageEntity?) {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 16)
        }
        val etName = EditText(ctx).apply {
            hint = "اسم الباقة (إجباري)"
            setText(existing?.name ?: "")
        }
        val etRetail = EditText(ctx).apply {
            hint = "سعر التجزئة"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(existing?.retailPrice?.toString() ?: "")
        }
        val etWholesale = EditText(ctx).apply {
            hint = "سعر الجملة"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(existing?.wholesalePrice?.toString() ?: "")
        }
        val etDistributor = EditText(ctx).apply {
            hint = "سعر الموزعين"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(existing?.distributorPrice?.toString() ?: "")
        }
        val etInitialQty = EditText(ctx).apply {
            hint = "الكمية الافتتاحية بالمخزون"
            inputType = InputType.TYPE_CLASS_NUMBER
            visibility = if (existing == null) View.VISIBLE else View.GONE
        }

        var selectedDate = existing?.createdAt ?: System.currentTimeMillis()
        val btnDate = Button(ctx).apply {
            text = "تاريخ الإضافة: ${FormatUtils.formatDate(selectedDate)}"
            setOnClickListener {
                val cal = Calendar.getInstance().apply { timeInMillis = selectedDate }
                DatePickerDialog(ctx, { _, y, m, d ->
                    val c = Calendar.getInstance().apply { set(y, m, d) }
                    selectedDate = c.timeInMillis
                    text = "تاريخ الإضافة: ${FormatUtils.formatDate(selectedDate)}"
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }
        }

        layout.addView(etName)
        layout.addView(etRetail)
        layout.addView(etWholesale)
        layout.addView(etDistributor)
        layout.addView(etInitialQty)
        layout.addView(btnDate)

        AlertDialog.Builder(ctx)
            .setTitle(if (existing == null) "إضافة باقة جديدة" else "تعديل الباقة")
            .setView(layout)
            .setPositiveButton("حفظ") { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isEmpty()) {
                    viewModel.emitMessage("اسم الباقة إجباري")
                    return@setPositiveButton
                }
                val retail = etRetail.text.toString().toDoubleOrNull() ?: 0.0
                val wholesale = etWholesale.text.toString().toDoubleOrNull() ?: retail
                val distributor = etDistributor.text.toString().toDoubleOrNull() ?: wholesale
                val initQty = etInitialQty.text.toString().toIntOrNull() ?: 0

                lifecycleScope.launch {
                    viewModel.repository.savePackage(
                        PackageEntity(
                            id = existing?.id ?: 0L,
                            name = name,
                            retailPrice = retail,
                            wholesalePrice = wholesale,
                            distributorPrice = distributor,
                            createdAt = selectedDate
                        ),
                        initialQuantity = initQty
                    )
                    viewModel.emitMessage("تم حفظ الباقة بنجاح")
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun confirmAndDeletePackage(pkg: PackageEntity, force: Boolean) {
        lifecycleScope.launch {
            try {
                if (!force) {
                    AlertDialog.Builder(requireContext())
                        .setTitle("تأكيد حذف الباقة")
                        .setMessage("هل تريد حذف باقة (${pkg.name})؟")
                        .setPositiveButton("حذف") { _, _ ->
                            lifecycleScope.launch {
                                try {
                                    viewModel.repository.deletePackage(pkg, forceConfirm = false)
                                    viewModel.emitMessage("تم حذف الباقة")
                                } catch (e: PackageLinkedToSalesException) {
                                    AlertDialog.Builder(requireContext())
                                        .setTitle("تنبيه ارتباط بمبيعات")
                                        .setMessage(e.message)
                                        .setPositiveButton("تأكيد الحذف النهائي") { _, _ ->
                                            lifecycleScope.launch {
                                                viewModel.repository.deletePackage(pkg, forceConfirm = true)
                                                viewModel.emitMessage("تم حذف الباقة وسجلاتها")
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
            } catch (e: Exception) {
                viewModel.emitMessage(e.localizedMessage ?: "تعذر حذف الباقة")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class PackagesAdapter(
        val onEdit: (PackageWithInventory) -> Unit,
        val onDelete: (PackageWithInventory) -> Unit
    ) : RecyclerView.Adapter<PackagesAdapter.VH>() {

        private var items: List<PackageWithInventory> = emptyList()
        private var currency: String = "ريال"

        fun submitList(newItems: List<PackageWithInventory>, curr: String) {
            items = newItems
            currency = curr
            notifyDataSetChanged()
        }

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvPkgName)
            val tvDate: TextView = view.findViewById(R.id.tvPkgDate)
            val tvPrices: TextView = view.findViewById(R.id.tvPkgPrices)
            val btnEdit: Button = view.findViewById(R.id.btnEditPackage)
            val btnDelete: Button = view.findViewById(R.id.btnDeletePackage)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_package, parent, false)
            return VH(v)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            val p = item.pkg
            holder.tvName.text = p.name
            holder.tvDate.text = FormatUtils.formatDate(p.createdAt)
            holder.tvPrices.text = "تجزئة: ${FormatUtils.formatAmount(p.retailPrice, currency)} | جملة: ${FormatUtils.formatAmount(p.wholesalePrice, currency)} | موزعين: ${FormatUtils.formatAmount(p.distributorPrice, currency)}"
            holder.btnEdit.setOnClickListener { onEdit(item) }
            holder.btnDelete.setOnClickListener { onDelete(item) }
        }
    }
}
