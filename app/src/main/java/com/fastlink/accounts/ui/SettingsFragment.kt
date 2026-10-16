package com.fastlink.accounts.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.fastlink.accounts.data.ExpenseTypeEntity
import com.fastlink.accounts.databinding.FragmentSettingsBinding
import com.fastlink.accounts.viewmodel.FastLinkViewModel
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: FastLinkViewModel by activityViewModels()
    private var currentExpenseTypes: List<ExpenseTypeEntity> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val settings = viewModel.settingsMap.value
        binding.swDarkMode.isChecked = settings["dark_mode"] == "dark"
        binding.etCurrencySetting.setText(settings["currency"] ?: "ريال")
        binding.swNotifyDailyDebts.isChecked = (settings["notify_daily_debts"] ?: "true") == "true"
        binding.swNotifyWeeklyExpenses.isChecked = (settings["notify_weekly_expenses"] ?: "true") == "true"
        binding.swNotifyBackupStatus.isChecked = (settings["notify_backup_status"] ?: "true") == "true"

        val fontScales = listOf("صغير", "متوسط (افتراضي)", "كبير")
        binding.spFontScale.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, fontScales)
        binding.spFontScale.setSelection(1)

        viewModel.expenseTypes.observe(viewLifecycleOwner) { list ->
            currentExpenseTypes = list
        }

        binding.swDarkMode.setOnCheckedChangeListener { _, isChecked ->
            viewModel.saveSetting("dark_mode", if (isChecked) "dark" else "light")
        }

        binding.btnSaveCurrency.setOnClickListener {
            val c = binding.etCurrencySetting.text.toString().trim().ifEmpty { "ريال" }
            viewModel.saveSetting("currency", c)
            viewModel.emitMessage("تم تحديث العملة إلى: $c")
        }

        binding.swNotifyDailyDebts.setOnCheckedChangeListener { _, isChecked ->
            viewModel.saveSetting("notify_daily_debts", isChecked.toString())
        }
        binding.swNotifyWeeklyExpenses.setOnCheckedChangeListener { _, isChecked ->
            viewModel.saveSetting("notify_weekly_expenses", isChecked.toString())
        }
        binding.swNotifyBackupStatus.setOnCheckedChangeListener { _, isChecked ->
            viewModel.saveSetting("notify_backup_status", isChecked.toString())
        }

        binding.btnManageExpenseTypes.setOnClickListener {
            showManageExpenseTypesDialog()
        }

        // مسح جميع البيانات (مع تأكيد مزدوج إلزامي)
        binding.btnClearAllData.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("⚠️ التأكيد الأول: مسح جميع البيانات")
                .setMessage("هل أنت متأكد من رغبتك في مسح كافة المحلات والمبيعات والمصروفات؟ (ننصح بأخذ نسخة احتياطية أولاً)")
                .setPositiveButton("متابعة للتأكيد النهائي") { _, _ ->
                    AlertDialog.Builder(requireContext())
                        .setTitle("🚨 التأكيد النهائي المزدوج")
                        .setMessage("لا يمكن التراجع عن هذه العملية إلا باستعادة نسخة احتياطية. هل تريد المسح الكامل الآن؟")
                        .setPositiveButton("نعم، امسح جميع البيانات") { _, _ ->
                            lifecycleScope.launch {
                                viewModel.repository.clearAllData()
                                viewModel.emitMessage("تم مسح جميع البيانات بنجاح")
                            }
                        }
                        .setNegativeButton("إلغاء", null)
                        .show()
                }
                .setNegativeButton("إلغاء", null)
                .show()
        }
    }

    private fun showManageExpenseTypesDialog() {
        val ctx = requireContext()
        val labels = currentExpenseTypes.map { t ->
            if (t.isDefault) "🔒 ${t.name} (افتراضي)" else "🗑️ ${t.name} (مخصص - اضغط للحذف)"
        }.toTypedArray()

        AlertDialog.Builder(ctx)
            .setTitle("إدارة أنواع المصروفات (الـ 14 الافتراضية لا تُحذف)")
            .setItems(labels) { _, which ->
                val selected = currentExpenseTypes[which]
                if (selected.isDefault) {
                    viewModel.emitMessage("الأنواع الافتراضية (14 نوعاً) مضمّنة في النظام ولا يمكن حذفها")
                } else {
                    lifecycleScope.launch {
                        viewModel.repository.deleteCustomExpenseType(selected.id)
                        viewModel.emitMessage("تم حذف النوع المخصص: ${selected.name}")
                    }
                }
            }
            .setPositiveButton("+ إضافة نوع مخصص") { _, _ ->
                val et = EditText(ctx).apply { hint = "اسم نوع المصروف الجديد" }
                AlertDialog.Builder(ctx)
                    .setTitle("إضافة نوع مصروف مخصص")
                    .setView(et)
                    .setPositiveButton("حفظ") { _, _ ->
                        val name = et.text.toString().trim()
                        if (name.isNotEmpty()) {
                            lifecycleScope.launch {
                                viewModel.repository.addCustomExpenseType(name)
                                viewModel.emitMessage("تمت إضافة النوع المخصص")
                            }
                        }
                    }
                    .setNegativeButton("إلغاء", null)
                    .show()
            }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
