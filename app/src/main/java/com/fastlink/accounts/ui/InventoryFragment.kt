package com.fastlink.accounts.ui

import android.app.AlertDialog
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
import com.fastlink.accounts.data.InventoryMovementEntity
import com.fastlink.accounts.data.PackageWithInventory
import com.fastlink.accounts.databinding.FragmentInventoryBinding
import com.fastlink.accounts.util.FormatUtils
import com.fastlink.accounts.viewmodel.FastLinkViewModel
import kotlinx.coroutines.launch

class InventoryFragment : Fragment() {

    private var _binding: FragmentInventoryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: FastLinkViewModel by activityViewModels()
    private var latestMovements: List<InventoryMovementEntity> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentInventoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = InventoryAdapter { item ->
            showUpdateQuantityDialog(item)
        }
        binding.rvInventory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvInventory.adapter = adapter

        viewModel.packagesWithInventory.observe(viewLifecycleOwner) { list ->
            val cur = viewModel.currencySymbol
            adapter.submitList(list, cur)
            val totalQty = list.sumOf { it.quantity }
            val sumRetail = list.sumOf { it.retailTotalValue }
            val sumWholesale = list.sumOf { it.wholesaleTotalValue }
            val sumDistributor = list.sumOf { it.distributorTotalValue }
            binding.tvInvTotalSummary.text = buildString {
                appendLine("إجمالي عدد الكروت المتوفرة: $totalQty كرت")
                appendLine("إجمالي القيمة بسعر التجزئة: ${FormatUtils.formatAmount(sumRetail, cur)}")
                appendLine("إجمالي القيمة بسعر الجملة: ${FormatUtils.formatAmount(sumWholesale, cur)}")
                append("إجمالي القيمة بسعر الموزعين: ${FormatUtils.formatAmount(sumDistributor, cur)}")
            }
        }

        viewModel.inventoryMovements.observe(viewLifecycleOwner) { movements ->
            latestMovements = movements
        }

        binding.btnShowMovementsLog.setOnClickListener {
            showMovementsDialog()
        }
    }

    private fun showUpdateQuantityDialog(item: PackageWithInventory) {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 16)
        }
        val etQty = EditText(ctx).apply {
            hint = "الكمية الجديدة المتوفرة"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(item.quantity.toString())
        }
        val etReason = EditText(ctx).apply {
            hint = "سبب التحديث (اختياري)"
            setText("تحديث يدوي للمخزون")
        }
        layout.addView(etQty)
        layout.addView(etReason)

        AlertDialog.Builder(ctx)
            .setTitle("تحديث كمية: ${item.pkg.name}")
            .setView(layout)
            .setPositiveButton("تحديث") { _, _ ->
                val newQty = etQty.text.toString().toIntOrNull() ?: return@setPositiveButton
                val reason = etReason.text.toString().trim().ifEmpty { "تحديث يدوي" }
                lifecycleScope.launch {
                    viewModel.repository.updatePackageInventory(item.pkg.id, newQty, reason)
                    viewModel.emitMessage("تم تحديث كمية المخزون")
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showMovementsDialog() {
        val text = if (latestMovements.isEmpty()) {
            "لا توجد حركات مسجلة حتى الآن."
        } else {
            latestMovements.joinToString("
━━━━━━━━━━━━━━
") { m ->
                val sign = if (m.changeAmount >= 0) "+${m.changeAmount}" else m.changeAmount.toString()
                "[${FormatUtils.formatDateTime(m.timestamp)}] ${m.packageName}
التغيير: $sign | الرصيد بعد الحركة: ${m.newQuantity} | السبب: ${m.reason}"
            }
        }
        AlertDialog.Builder(requireContext())
            .setTitle("سجل حركات الكميات بالمخزون")
            .setMessage(text)
            .setPositiveButton("إغلاق", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class InventoryAdapter(
        val onUpdateClick: (PackageWithInventory) -> Unit
    ) : RecyclerView.Adapter<InventoryAdapter.VH>() {

        private var items: List<PackageWithInventory> = emptyList()
        private var currency: String = "ريال"

        fun submitList(newItems: List<PackageWithInventory>, curr: String) {
            items = newItems
            currency = curr
            notifyDataSetChanged()
        }

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvInvPkgName)
            val tvQty: TextView = view.findViewById(R.id.tvInvQty)
            val tvValues: TextView = view.findViewById(R.id.tvInvValues)
            val btnUpdate: Button = view.findViewById(R.id.btnUpdateQty)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_inventory, parent, false)
            return VH(v)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.tvName.text = item.pkg.name
            holder.tvQty.text = "الكمية: ${item.quantity} كرت"
            holder.tvValues.text = buildString {
                appendLine("القيمة بسعر التجزئة: ${FormatUtils.formatAmount(item.retailTotalValue, currency)}")
                appendLine("القيمة بسعر الجملة: ${FormatUtils.formatAmount(item.wholesaleTotalValue, currency)}")
                append("القيمة بسعر الموزعين: ${FormatUtils.formatAmount(item.distributorTotalValue, currency)}")
            }
            holder.btnUpdate.setOnClickListener { onUpdateClick(item) }
        }
    }
}
