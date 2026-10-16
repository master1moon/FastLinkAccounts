package com.fastlink.accounts.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.fastlink.accounts.R
import com.fastlink.accounts.backup.BackupManager
import com.fastlink.accounts.databinding.ActivityMainBinding
import com.fastlink.accounts.viewmodel.FastLinkViewModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    val viewModel: FastLinkViewModel by viewModels()

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        BackupManager.ensureBackupDirectoryExists(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // فرض اتجاه RTL من اليمين إلى اليسار
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        val toggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.app_name,
            R.string.app_name
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        requestNecessaryRuntimePermissions()

        binding.navigationView.setNavigationItemSelectedListener { item ->
            navigateToSection(item.itemId)
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            navigateToSection(item.itemId, syncBottomNav = false)
            true
        }

        lifecycleScope.launch {
            viewModel.uiMessage.collect { msg ->
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
            }
        }

        lifecycleScope.launch {
            viewModel.settingsMap.collect { map ->
                when (map["dark_mode"]) {
                    "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                }
            }
        }

        if (savedInstanceState == null) {
            navigateToSection(R.id.nav_dashboard)
        }
    }

    fun navigateToSection(itemId: Int, syncBottomNav: Boolean = true) {
        val (fragment, title) = when (itemId) {
            R.id.nav_dashboard -> DashboardFragment() to getString(R.string.nav_dashboard)
            R.id.nav_packages -> PackagesFragment() to getString(R.string.nav_packages)
            R.id.nav_inventory -> InventoryFragment() to getString(R.string.nav_inventory)
            R.id.nav_stores -> StoresFragment() to getString(R.string.nav_stores)
            R.id.nav_expenses -> ExpensesFragment() to getString(R.string.nav_expenses)
            R.id.nav_reports -> ReportsFragment() to getString(R.string.nav_reports)
            R.id.nav_partners -> PartnersFragment() to getString(R.string.nav_partners)
            R.id.nav_backup -> BackupFragment() to getString(R.string.nav_backup)
            R.id.nav_settings -> SettingsFragment() to getString(R.string.nav_settings)
            else -> DashboardFragment() to getString(R.string.nav_dashboard)
        }

        binding.toolbar.title = title
        binding.navigationView.setCheckedItem(itemId)
        if (syncBottomNav && binding.bottomNavigation.menu.findItem(itemId) != null) {
            binding.bottomNavigation.selectedItemId = itemId
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    fun openStoreDetails(storeId: Long) {
        val fragment = StoreDetailsFragment.newInstance(storeId)
        binding.toolbar.title = "تفاصيل المحل والحساب"
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack("store_details")
            .commit()
    }

    private fun requestNecessaryRuntimePermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        if (perms.isNotEmpty()) {
            permissionsLauncher.launch(perms.toTypedArray())
        }
        // طلب صلاحية MANAGE_EXTERNAL_STORAGE لأندرويد 11+ لضمان الكتابة في /storage/emulated/0/NGS BACKUP/
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            runCatching {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        val backupOnExit = viewModel.settingsMap.value["backup_on_exit"] == "true"
        if (backupOnExit) {
            val maxKeep = viewModel.settingsMap.value["max_backup_count"]?.toIntOrNull() ?: 10
            lifecycleScope.launch {
                BackupManager.performLocalBackup(applicationContext, maxKeep, notifyUser = false)
            }
        }
    }
}
