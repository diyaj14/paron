package com.offlinepay.data

enum class PaymentMethod {
    QR,
    MOBILE,
    BLUETOOTH
}

enum class TransactionStatus {
    COMPLETED,
    PENDING_VERIFICATION,
    SYNCED,
    FAILED
}

data class WalletTransaction(
    val id: String,
    val merchant: String,
    val amountRupees: Double,
    val method: String,
    val dateLabel: String,
    val status: TransactionStatus,
    val offlineToken: String? = null,
    val signature: String? = null,
    val publicKey: String? = null,
    val transactedAt: String? = null,
    val deviceId: String? = null,
    val merchantId: String? = null
)

fun formatRupees(amount: Double): String =
    java.math.BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString()

/** An auditable step emitted by the device or the sync response. */
data class SyncEvent(
    val label: String,
    val detail: String,
    val occurredAt: String,
    val severity: RiskSeverity = RiskSeverity.INFO
)

enum class RiskSeverity { INFO, REVIEW, HIGH }

data class AppUiState(
    val authenticated: Boolean = false,
    val internetAvailable: Boolean = true,
    val phoneNumber: String = "9876543210",
    val userName: String = "",
    val userEmail: String = "",
    val onlineBalance: Int = 1000,
    val offlineBalance: Int = 0,
    val remoteTokenRemaining: Int? = null,
    val activeOfflineToken: String? = null,
    val activeTokenId: String? = null,
    val tokenExpiresAt: String? = null,
    val selectedMerchant: String = "Campus Canteen",
    val merchantId: String = "merchant_canteen",
    val paymentAmount: String = "100",
    val paymentNote: String = "",
    val paymentMethod: PaymentMethod = PaymentMethod.QR,
    val showOfflineFallback: Boolean = false,
    val lastTransaction: WalletTransaction? = null,
    val message: String? = null,
    val isSyncing: Boolean = false,
    val isReserving: Boolean = false,
    val isFunding: Boolean = false,
    val bluetoothListening: Boolean = false,
    val lastQrPayload: String? = null,
    val syncEvents: List<SyncEvent> = emptyList(),
    val serverBaseUrl: String = "http://10.0.2.2:8080",
    val transactions: List<WalletTransaction> = emptyList()
)
