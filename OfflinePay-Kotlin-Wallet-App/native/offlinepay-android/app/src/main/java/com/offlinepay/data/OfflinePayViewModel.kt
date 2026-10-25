package com.offlinepay.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.offlinepay.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

class OfflinePayViewModel(application: Application) : AndroidViewModel(application) {
    private val demoStore = application.getSharedPreferences(DEMO_PREFS, Application.MODE_PRIVATE)
    private val _uiState = MutableStateFlow(loadInitialState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    fun setInternetAvailable(available: Boolean) {
        _uiState.update { it.copy(internetAvailable = available) }
    }

    fun setPhoneNumber(value: String) {
        _uiState.update { it.copy(phoneNumber = value.filter(Char::isDigit).take(10), message = null) }
    }

    fun verifyOtp(code: String): Boolean {
        if (BuildConfig.DEMO_MODE && code == DEMO_OTP) {
            _uiState.update { it.copy(authenticated = true, message = null) }
            return true
        }
        _uiState.update {
            it.copy(message = if (BuildConfig.DEMO_MODE) {
                "Use the development code 123456."
            } else {
                "Phone verification is not configured. No account was signed in."
            })
        }
        return false
    }

    fun setMerchant(name: String, merchantId: String = "TXN-00123") {
        _uiState.update {
            it.copy(
                selectedMerchant = name.ifBlank { "Mobile payment" },
                merchantId = merchantId.ifBlank { "Not provided" },
                message = null
            )
        }
    }

    fun setPaymentAmount(value: String) {
        _uiState.update { it.copy(paymentAmount = value.filter { char -> char.isDigit() || char == '.' }.take(8)) }
    }

    fun setPaymentNote(value: String) {
        _uiState.update { it.copy(paymentNote = value.take(80)) }
    }

    fun setPaymentMethod(method: PaymentMethod) {
        _uiState.update { it.copy(paymentMethod = method) }
    }

    fun confirmOnlinePayment() {
        _uiState.update {
            it.copy(message = "UPI processing is not configured. No payment was made.")
        }
    }

    fun saveOfflineDemoPayment(): Boolean {
        if (!BuildConfig.DEMO_MODE) {
            _uiState.update {
                it.copy(message = "Offline-token authorization is not configured. No payment was made.")
            }
            return false
        }
        val current = _uiState.value
        val amount = current.paymentAmount.toIntOrNull()
        if (amount == null || amount <= 0) {
            _uiState.update { it.copy(message = "Enter a valid payment amount.") }
            return false
        }
        if (amount > current.offlineBalance) {
            _uiState.update { it.copy(message = "The offline balance is too low for this amount.") }
            return false
        }

        val transaction = WalletTransaction(
            id = "OFF-DEMO-${System.currentTimeMillis().toString().takeLast(8)}",
            merchant = current.selectedMerchant,
            amountRupees = amount,
            method = "Offline Balance",
            dateLabel = "Saved just now",
            status = TransactionStatus.PENDING_VERIFICATION
        )
        val updated = current.copy(
            offlineBalance = current.offlineBalance - amount,
            lastTransaction = transaction,
            transactions = listOf(transaction) + current.transactions,
            message = null
        )
        _uiState.value = updated
        persistDemoState(updated)
        return true
    }

    fun reserveOffline(amountText: String): Boolean {
        if (!BuildConfig.DEMO_MODE) {
            _uiState.update { it.copy(message = "Wallet balance management is not configured. No balances were changed.") }
            return false
        }
        val amount = amountText.toIntOrNull()
        val state = _uiState.value
        if (amount == null || amount <= 0 || amount > state.onlineBalance) {
            _uiState.update { it.copy(message = "Enter an amount up to your online balance.") }
            return false
        }
        val updated = state.copy(
            onlineBalance = state.onlineBalance - amount,
            offlineBalance = state.offlineBalance + amount,
            message = "₹$amount moved to your offline balance."
        )
        _uiState.value = updated
        persistDemoState(updated)
        return true
    }

    fun transferFromBank() {
        _uiState.update { it.copy(message = "Bank transfers are not configured. No transfer was made.") }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun loadInitialState(): AppUiState {
        val defaultState = AppUiState()
        if (!BuildConfig.DEMO_MODE) return defaultState

        val storedTransactions = demoStore.getString(KEY_TRANSACTIONS, null)
            ?.let(::decodeTransactions)
            ?: defaultState.transactions
        return defaultState.copy(
            onlineBalance = demoStore.getInt(KEY_ONLINE_BALANCE, defaultState.onlineBalance),
            offlineBalance = demoStore.getInt(KEY_OFFLINE_BALANCE, defaultState.offlineBalance),
            transactions = storedTransactions
        )
    }

    private fun persistDemoState(state: AppUiState) {
        if (!BuildConfig.DEMO_MODE) return
        val transactions = JSONArray()
        state.transactions.forEach { transaction ->
            transactions.put(
                JSONObject()
                    .put("id", transaction.id)
                    .put("merchant", transaction.merchant)
                    .put("amount", transaction.amountRupees)
                    .put("method", transaction.method)
                    .put("date", transaction.dateLabel)
                    .put("status", transaction.status.name)
            )
        }
        demoStore.edit()
            .putInt(KEY_ONLINE_BALANCE, state.onlineBalance)
            .putInt(KEY_OFFLINE_BALANCE, state.offlineBalance)
            .putString(KEY_TRANSACTIONS, transactions.toString())
            .apply()
    }

    private fun decodeTransactions(json: String): List<WalletTransaction> =
        runCatching {
            val array = JSONArray(json)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                WalletTransaction(
                    id = item.optString("id"),
                    merchant = item.optString("merchant"),
                    amountRupees = item.optInt("amount"),
                    method = item.optString("method"),
                    dateLabel = item.optString("date"),
                    status = runCatching {
                        TransactionStatus.valueOf(item.optString("status"))
                    }.getOrDefault(TransactionStatus.COMPLETED)
                )
            }
        }.getOrDefault(AppUiState().transactions)

    companion object {
        const val DEMO_OTP = "123456"
        private const val DEMO_PREFS = "offlinepay_demo_data"
        private const val KEY_ONLINE_BALANCE = "online_balance"
        private const val KEY_OFFLINE_BALANCE = "offline_balance"
        private const val KEY_TRANSACTIONS = "transactions"
    }
}
