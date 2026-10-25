package com.offlinepay.data

enum class PaymentMethod {
    QR,
    MOBILE
}

enum class TransactionStatus {
    COMPLETED,
    PENDING_VERIFICATION
}

data class WalletTransaction(
    val id: String,
    val merchant: String,
    val amountRupees: Int,
    val method: String,
    val dateLabel: String,
    val status: TransactionStatus
)

data class AppUiState(
    val authenticated: Boolean = false,
    val internetAvailable: Boolean = false,
    val phoneNumber: String = "",
    val onlineBalance: Int = 500,
    val offlineBalance: Int = 500,
    val selectedMerchant: String = "Campus Canteen",
    val merchantId: String = "TXN-00123",
    val paymentAmount: String = "100",
    val paymentNote: String = "",
    val paymentMethod: PaymentMethod = PaymentMethod.QR,
    val lastTransaction: WalletTransaction? = null,
    val message: String? = null,
    val transactions: List<WalletTransaction> = listOf(
        WalletTransaction(
            id = "DEMO-001",
            merchant = "Campus Canteen",
            amountRupees = 100,
            method = "UPI",
            dateLabel = "Apr 26, 11:20 AM",
            status = TransactionStatus.COMPLETED
        ),
        WalletTransaction(
            id = "DEMO-002",
            merchant = "Campus Store",
            amountRupees = 150,
            method = "UPI",
            dateLabel = "Apr 26, 10:42 AM",
            status = TransactionStatus.COMPLETED
        ),
        WalletTransaction(
            id = "DEMO-003",
            merchant = "Café (Merchant C)",
            amountRupees = 50,
            method = "Offline Balance",
            dateLabel = "Apr 26, 09:15 AM",
            status = TransactionStatus.COMPLETED
        )
    )
)
