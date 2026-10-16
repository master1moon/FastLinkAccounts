package com.fastlink.accounts.ui

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.fastlink.accounts.backup.BackupManager
import com.fastlink.accounts.backup.GoogleDriveBackupHelper
import com.fastlink.accounts.databinding.FragmentBackupBinding
import com.fastlink.accounts.util.FormatUtils
import com.fastlink.accounts.viewmodel.FastLinkViewModel
import com.fastlink.accounts.worker.BackupWorker
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.launch
import java.io.File

class BackupFragment : Fragment() {

    private var _binding: FragmentBackupBinding? = null
    private val binding get() = _binding!!
    private val viewModel: FastLinkViewModel by activityViewModels()

    private val intervalLabels = listOf("6 ساعات", "12 ساعة", "24 ساعة (افتراضي)", "48 ساعة", "أسبوعي (168 ساعة)")
    private val intervalHoursValues = listOf(6L, 12L, 24L, 48L, 168L)

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        if (task.isSuccessful) {
            refreshDriveAccountUI()
            viewModel.emitMessage("تم ربط حساب Google Drive بنجاح")
        } else {
            viewModel.emitMessage("تعذر تسجيل الدخول بـ Google")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBackupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        refreshDriveAccountUI()

        binding.spBackupInterval.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            intervalLabels
        )
        binding.spBackupInterval.setSelection(2) // 24 hours default

        binding.spBackupInterval.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val hours = intervalHoursValues[position]
                viewModel.saveSetting("local_backup_interval_hours", hours.toString())
                BackupWorker.schedulePeriodicBackup(requireContext(), hours)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // ج) النسخ الاحتياطي اليدوي الفوري
        binding.btnBackupNowPhone.setOnClickListener {
            lifecycleScope.launch {
                val maxKeep = viewModel.settingsMap.value["max_backup_count"]?.toIntOrNull() ?: 10
                val res = BackupManager.performLocalBackup(requireContext(), maxKeep, notifyUser = true)
                res.onSuccess { file ->
                    viewModel.emitMessage("تم حفظ النسخة في: ${file.absolutePath}")
                }.onFailure { e ->
                    viewModel.emitMessage("فشل النسخ: ${e.localizedMessage}")
                }
            }
        }

        binding.btnBackupNowDrive.setOnClickListener {
            lifecycleScope.launch {
                val maxKeep = viewModel.settingsMap.value["max_backup_count"]?.toIntOrNull() ?: 10
                val localRes = BackupManager.performLocalBackup(requireContext(), maxKeep, notifyUser = false)
                val file = localRes.getOrNull() ?: return@launch
                viewModel.emitMessage("جاري الرفع إلى مجلد NGS BACKUP في Google Drive...")
                val driveRes = GoogleDriveBackupHelper.uploadBackupToDrive(requireContext(), file, maxKeep, notifyUser = true)
                driveRes.onSuccess {
                    viewModel.emitMessage("تم رفع النسخة الاحتياطية إلى Google Drive بنجاح")
                }.onFailure { e ->
                    viewModel.emitMessage(e.localizedMessage ?: "فشل الرفع إلى Google Drive")
                }
            }
        }

        binding.btnBackupNowBoth.setOnClickListener {
            lifecycleScope.launch {
                val maxKeep = viewModel.settingsMap.value["max_backup_count"]?.toIntOrNull() ?: 10
                val localRes = BackupManager.performLocalBackup(requireContext(), maxKeep, notifyUser = true)
                val file = localRes.getOrNull()
                if (file != null) {
                    GoogleDriveBackupHelper.uploadBackupToDrive(requireContext(), file, maxKeep, notifyUser = true)
                    viewModel.emitMessage("تم حفظ النسخة محلياً ورفعها إلى Google Drive")
                }
            }
        }

        // ب) تسجيل الدخول بـ Google وفتح مجلد Drive
        binding.btnGoogleSignIn.setOnClickListener {
            val client = GoogleDriveBackupHelper.getSignInClient(requireContext())
            googleSignInLauncher.launch(client.signInIntent)
        }

        binding.btnOpenDriveFolder.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://drive.google.com/drive/my-drive"))
            startActivity(intent)
        }

        // د) استعادة نسخة احتياطية مع معاينة البيانات قبل الاستبدال
        binding.btnRestoreFromPhone.setOnClickListener {
            val backups = BackupManager.listLocalBackups(requireContext())
            if (backups.isEmpty()) {
                viewModel.emitMessage("لا توجد نسخ احتياطية في مجلد NGS BACKUP")
                return@setOnClickListener
            }
            val names = backups.map { "${it.name} (${it.length() / 1024} KB)" }.toTypedArray()
            AlertDialog.Builder(requireContext())
                .setTitle("اختر نسخة احتياطية من الهاتف (NGS BACKUP)")
                .setItems(names) { _, which ->
                    previewAndConfirmRestore(backups[which])
                }
                .setNegativeButton("إلغاء", null)
                .show()
        }

        binding.btnRestoreFromDrive.setOnClickListener {
            lifecycleScope.launch {
                val res = GoogleDriveBackupHelper.listDriveBackups(requireContext())
                res.onSuccess { list ->
                    if (list.isEmpty()) {
                        viewModel.emitMessage("لا توجد نسخ احتياطية في مجلد NGS BACKUP على Drive")
                        return@onSuccess
                    }
                    val labels = list.map { "${it.name} (${it.sizeBytes / 1024} KB)" }.toTypedArray()
                    AlertDialog.Builder(requireContext())
                        .setTitle("اختر نسخة من Google Drive")
                        .setItems(labels) { _, idx ->
                            lifecycleScope.launch {
                                val dl = GoogleDriveBackupHelper.downloadDriveBackupToCache(requireContext(), list[idx])
                                dl.onSuccess { tempFile ->
                                    previewAndConfirmRestore(tempFile)
                                }.onFailure { err ->
                                    viewModel.emitMessage(err.localizedMessage ?: "فشل تنزيل النسخة")
                                }
                            }
                        }
                        .setNegativeButton("إلغاء", null)
                        .show()
                }.onFailure { err ->
                    viewModel.emitMessage(err.localizedMessage ?: "يرجى تسجيل الدخول بـ Google أولاً")
                }
            }
        }

        // هـ) إعدادات النسخ التلقائي
        binding.swAutoLocalBackup.setOnCheckedChangeListener { _, isChecked ->
            viewModel.saveSetting("auto_local_backup", isChecked.toString())
        }
        binding.swAutoDriveBackup.setOnCheckedChangeListener { _, isChecked ->
            viewModel.saveSetting("auto_drive_backup", isChecked.toString())
        }
        binding.swBackupOnExit.setOnCheckedChangeListener { _, isChecked ->
            viewModel.saveSetting("backup_on_exit", isChecked.toString())
        }

        viewModel.backupLogs.observe(viewLifecycleOwner) { logs ->
            val lastSuccess = logs.firstOrNull { it.status == "success" }
            binding.tvBackupLogsSummary.text = if (lastSuccess != null) {
                "آخر نسخة احتياطية ناجحة: ${FormatUtils.formatDateTime(lastSuccess.timestamp)} (${lastSuccess.type})
المسار: ${lastSuccess.filePath}"
            } else {
                "المجلد المحلي المعتمد: /storage/emulated/0/NGS BACKUP/"
            }
        }
    }

    private fun previewAndConfirmRestore(file: File) {
        val info = BackupManager.inspectBackupFile(file)
        val previewMsg = buildString {
            appendLine("اسم الملف: ${info.fileName}")
            appendLine("تاريخ النسخة: ${FormatUtils.formatDateTime(info.lastModified)}")
            appendLine("الحجم: ${info.fileSizeBytes / 1024} كيلوبايت")
            appendLine("عدد المحلات داخل النسخة: ${info.storesCount}")
            appendLine("عدد عمليات البيع: ${info.salesCount}")
            appendLine("عدد المصروفات: ${info.expensesCount}")
            appendLine("━━━━━━━━━━━━━━")
            append("هل تريد استبدال البيانات الحالية بهذه النسخة الاحتياطية؟")
        }

        AlertDialog.Builder(requireContext())
            .setTitle("معاينة بيانات النسخة قبل الاستعادة")
            .setMessage(previewMsg)
            .setPositiveButton("تأكيد الاستعادة") { _, _ ->
                val res = BackupManager.restoreFromBackupFile(requireContext(), file)
                res.onSuccess {
                    viewModel.emitMessage("تمت استعادة النسخة الاحتياطية بنجاح! جاري تحديث التطبيق...")
                    requireActivity().recreate()
                }.onFailure { err ->
                    viewModel.emitMessage("فشل الاستعادة: ${err.localizedMessage}")
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun refreshDriveAccountUI() {
        val acct = GoogleDriveBackupHelper.getSignedInAccount(requireContext())
        if (acct != null) {
            binding.tvDriveAccountStatus.text = "متصل بحساب: ${acct.email}"
            binding.btnGoogleSignIn.text = "تغيير حساب Google"
        } else {
            binding.tvDriveAccountStatus.text = "الحالة: غير متصل بحساب Google Drive"
            binding.btnGoogleSignIn.text = "تسجيل الدخول بـ Google"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
