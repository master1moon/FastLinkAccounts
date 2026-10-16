package com.fastlink.accounts.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.fastlink.accounts.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class FastLinkViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FastLinkDatabase.getInstance(application)
    val repository = AppRepository(db)

    // LiveData & StateFlow for UI observation
    val dashboardStats: LiveData<DashboardStats> = repository.dashboardStatsFlow.asLiveData()
    val packagesWithInventory: LiveData<List<PackageWithInventory>> = repository.packagesWithInventoryFlow.asLiveData()
    val inventoryMovements: LiveData<List<InventoryMovementEntity>> = repository.inventoryMovementsFlow.asLiveData()
    val storesWithSummary: LiveData<List<StoreWithSummary>> = repository.storesWithSummaryFlow.asLiveData()
    val allSales: LiveData<List<SaleEntity>> = repository.salesFlow.asLiveData()
    val allPayments: LiveData<List<PaymentEntity>> = repository.paymentsFlow.asLiveData()
    val expenses: LiveData<List<ExpenseEntity>> = repository.expensesFlow.asLiveData()
    val expenseTypes: LiveData<List<ExpenseTypeEntity>> = repository.expenseTypesFlow.asLiveData()
    val partners: LiveData<List<PartnerEntity>> = repository.partnersFlow.asLiveData()
    val partnerTransactions: LiveData<List<PartnerTransactionEntity>> = repository.partnerTransactionsFlow.asLiveData()
    val backupLogs: LiveData<List<BackupLogEntity>> = repository.backupLogsFlow.asLiveData()

    val settingsMap: StateFlow<Map<String, String>> = repository.settingsFlow
        .map { list -> list.associate { it.key to it.value } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, FastLinkDatabase.DEFAULT_SETTINGS)

    val currencySymbol: String
        get() = settingsMap.value["currency"] ?: "ريال"

    private val _uiMessage = MutableSharedFlow<String>()
    val uiMessage: SharedFlow<String> = _uiMessage.asSharedFlow()

    fun emitMessage(msg: String) {
        viewModelScope.launch {
            _uiMessage.emit(msg)
        }
    }

    fun saveSetting(key: String, value: String) {
        viewModelScope.launch {
            repository.updateSetting(key, value)
        }
    }
}
