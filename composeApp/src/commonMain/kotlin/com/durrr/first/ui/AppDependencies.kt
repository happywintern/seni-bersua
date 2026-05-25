package com.durrr.first.ui

import com.durrr.first.data.repo.CashFlowRepository
import com.durrr.first.data.repo.MenuRepository
import com.durrr.first.data.repo.OrderCacheRepository
import com.durrr.first.data.repo.OrderSyncRepository
import com.durrr.first.data.repo.MenuSyncRepository
import com.durrr.first.data.repo.RecapRepository
import com.durrr.first.data.repo.ReceiptRepository
import com.durrr.first.data.repo.RecapSyncRepository
import com.durrr.first.data.repo.ServerAuthRepository
import com.durrr.first.data.repo.SettingsRepository
import com.durrr.first.data.repo.StockRepository
import com.durrr.first.data.repo.CashSessionRepository
import com.durrr.first.data.repo.TransaksiSyncRepository
import com.durrr.first.data.repo.TransaksiRepository

class AppDependencies(
    val menuRepository: MenuRepository,
    val transaksiRepository: TransaksiRepository,
    val recapRepository: RecapRepository,
    val cashFlowRepository: CashFlowRepository,
    val stockRepository: StockRepository,
    val cashSessionRepository: CashSessionRepository,
    val receiptRepository: ReceiptRepository,
    val recapSyncRepository: RecapSyncRepository,
    val serverAuthRepository: ServerAuthRepository,
    val settingsRepository: SettingsRepository,
    val orderCacheRepository: OrderCacheRepository,
    val orderSyncRepository: OrderSyncRepository,
    val menuSyncRepository: MenuSyncRepository,
    val transaksiSyncRepository: TransaksiSyncRepository,
    val nowIso: () -> String,
    val todayDate: () -> String,
    val launchScanner: () -> Unit,
    val pickImage: ((String?) -> Unit) -> Unit,
    val uploadMenuImage: suspend (baseUrl: String, localUri: String, outletId: String) -> String?,
    val pickDate: (initialIso: String?, onPicked: (String) -> Unit) -> Unit,
    val shareText: (subject: String, payload: String) -> Unit,
    val printHtml: (jobName: String, htmlPayload: String) -> Unit,
)
