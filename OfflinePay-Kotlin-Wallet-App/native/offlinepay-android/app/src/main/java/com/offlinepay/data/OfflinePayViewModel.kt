package com.offlinepay.data

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.offlinepay.BuildConfig
import com.offlinepay.bluetooth.BluetoothPaymentManager
import com.offlinepay.bluetooth.PaymentOfferEnvelope
import com.offlinepay.crypto.CryptoManager
import com.offlinepay.network.ParonApiClient
import com.offlinepay.network.SyncTransactionPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class OfflinePayViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val demoStore = application.getSharedPreferences(DEMO_PREFS, Application.MODE_PRIVATE)

    val apiClient = ParonApiClient(application)
    val bluetoothManager = BluetoothPaymentManager(application)

    private val _uiState = MutableStateFlow(loadInitialState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        // Automatically try loading real balance on startup
        refreshBalancesFromBackend()
    }

    private fun currentFormattedTime(): String {
        return try {
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM, hh:mm a"))
        } catch (_: Exception) {
            "Just now"
        }
    }

    fun getDeviceId(): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "device_${_uiState.value.phoneNumber}"
    }

    fun setServerBaseUrl(url: String) {
        apiClient.baseUrl = url
        _uiState.update { it.copy(serverBaseUrl = url, message = "Backend URL set to $url") }
        refreshBalancesFromBackend()
    }

    fun setInternetAvailable(available: Boolean) {
        val wasOffline = !_uiState.value.internetAvailable
        _uiState.update { it.copy(internetAvailable = available) }
        // Auto-sync when reconnecting to internet!
        if (available && wasOffline) {
            syncNow()
        }
    }

    fun setPhoneNumber(value: String) {
        val num = value.filter(Char::isDigit).take(10)
        _uiState.update { it.copy(phoneNumber = num, message = null) }
    }

    fun setUserName(value: String) {
        _uiState.update { it.copy(userName = value) }
    }

    fun setUserEmail(value: String) {
        _uiState.update { it.copy(userEmail = value) }
    }

    fun saveUserProfile(name: String, email: String) {
        val updated = _uiState.value.copy(
            userName = name.trim(),
            userEmail = email.trim(),
            authenticated = true,
            message = "Welcome, ${name.trim()}!"
        )
        _uiState.value = updated
        persistState(updated)
        refreshBalancesFromBackend()
    }

    fun verifyOtp(code: String): Boolean {
        if (code == DEMO_OTP || BuildConfig.DEMO_MODE) {
            val currentState = _uiState.value
            val isProfileComplete = currentState.userName.isNotBlank() && currentState.userEmail.isNotBlank()
            _uiState.update { it.copy(authenticated = isProfileComplete, message = null) }
            refreshBalancesFromBackend()
            return true
        }
        _uiState.update { it.copy(message = "Use development code $DEMO_OTP.") }
        return false
    }

    fun setMerchant(name: String, merchantId: String = "merchant_canteen") {
        _uiState.update {
            it.copy(
                selectedMerchant = name.ifBlank { "Campus Canteen" },
                merchantId = merchantId.ifBlank { "merchant_canteen" },
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

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun clearTransactions() {
        val updated = _uiState.value.copy(transactions = emptyList(), message = "Transaction history cleared.")
        _uiState.value = updated
        persistState(updated)
    }

    /**
     * 1. Query live balance from Ledger Service.
     */
    fun refreshBalancesFromBackend() {
        val currentUserId = _uiState.value.phoneNumber.ifBlank { "9876543210" }
        viewModelScope.launch {
            apiClient.createTestAccount(currentUserId, 1000.0)
            val balanceRes = apiClient.fetchBalance(currentUserId)
            balanceRes.onSuccess { bal ->
                _uiState.update { current ->
                    current.copy(
                        onlineBalance = bal.availableBalance.toInt(),
                        offlineBalance = if (current.activeOfflineToken != null) current.offlineBalance else bal.reservedAmount.toInt()
                    )
                }
            }
        }
    }

    /**
     * 2. Reserve Offline Token from Backend.
     * Calls token-service /api/v1/tokens/gettoken, which locks funds in ledger-service
     * and returns the signed JWT offline token.
     */
    fun reserveOffline(amountText: String) {
        val amount = amountText.toDoubleOrNull()
        val state = _uiState.value

        if (amount == null || amount <= 0) {
            _uiState.update { it.copy(message = "Please enter a valid amount.") }
            return
        }

        if (amount > state.onlineBalance) {
            _uiState.update { it.copy(message = "Amount exceeds available balance (₹${state.onlineBalance}).") }
            return
        }

        if (!state.internetAvailable) {
            _uiState.update { it.copy(message = "Internet connection required to request a token from the backend.") }
            return
        }

        _uiState.update { it.copy(isReserving = true, message = "Requesting offline token from backend...") }

        viewModelScope.launch {
            val userId = state.phoneNumber.ifBlank { "9876543210" }
            val deviceId = getDeviceId()

            val result = apiClient.requestOfflineToken(
                userId = userId,
                deviceId = deviceId,
                amount = amount,
                expiryHours = 6
            )

            result.onSuccess { tokenRes ->
                val newOffline = (state.offlineBalance + amount).toInt()
                val reserveTxn = WalletTransaction(
                    id = "res-" + UUID.randomUUID().toString().take(8),
                    merchant = "Bank Ledger Reserve",
                    amountRupees = amount.toInt(),
                    method = "Token Allocation",
                    dateLabel = currentFormattedTime(),
                    status = TransactionStatus.COMPLETED
                )

                val updated = state.copy(
                    isReserving = false,
                    activeOfflineToken = tokenRes.token,
                    activeTokenId = tokenRes.tokenId,
                    tokenExpiresAt = tokenRes.expiresAt,
                    offlineBalance = newOffline,
                    transactions = listOf(reserveTxn) + state.transactions,
                    message = "₹${amount.toInt()} reserved! Token issued from backend for offline spending."
                )
                _uiState.value = updated
                persistState(updated)
                refreshBalancesFromBackend()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isReserving = false,
                        message = "Token reservation failed: ${err.message}"
                    )
                }
            }
        }
    }

    /**
     * Confirm Online UPI payment.
     */
    fun confirmOnlinePayment() {
        val current = _uiState.value
        val amount = current.paymentAmount.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            _uiState.update { it.copy(message = "Please enter a valid amount.") }
            return
        }
        if (amount > current.onlineBalance) {
            _uiState.update { it.copy(message = "Insufficient online balance.") }
            return
        }

        val txn = WalletTransaction(
            id = "upi-" + UUID.randomUUID().toString().take(8),
            merchant = current.selectedMerchant,
            amountRupees = amount.toInt(),
            method = "UPI Online",
            dateLabel = currentFormattedTime(),
            status = TransactionStatus.COMPLETED
        )

        val updated = current.copy(
            onlineBalance = (current.onlineBalance - amount).toInt(),
            lastTransaction = txn,
            transactions = listOf(txn) + current.transactions,
            message = "Paid ₹${amount.toInt()} to ${current.selectedMerchant} successfully!"
        )
        _uiState.value = updated
        persistState(updated)
    }

    /**
     * 3. Make Offline Payment (Signs cryptographically using device EC key).
     */
    fun saveOfflinePayment(): Boolean {
        val current = _uiState.value
        val amount = current.paymentAmount.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            _uiState.update { it.copy(message = "Enter a valid payment amount.") }
            return false
        }

        if (amount > current.offlineBalance) {
            _uiState.update { it.copy(message = "Offline balance is too low. Please reserve an offline token first.") }
            return false
        }

        val token = current.activeOfflineToken
        if (token.isNullOrBlank()) {
            _uiState.update { it.copy(message = "No active offline token. Reserve a token in the Wallet first.") }
            return false
        }

        val txnId = "txn-" + UUID.randomUUID().toString().take(8)
        val transactedAt = LocalDateTime.now().toString()
        val deviceId = getDeviceId()

        // Generate cryptographic ECDSA P-256 signature
        val canonical = CryptoManager.canonicalString(
            deviceTransactionId = txnId,
            offlineToken = token,
            merchantId = current.merchantId,
            amount = amount.toString(),
            transactedAt = transactedAt,
            deviceId = deviceId
        )
        val signature = CryptoManager.sign(context, canonical)
        val publicKey = CryptoManager.getPublicKeyJwk(context)

        // QR / Bluetooth payload
        val payloadObj = JSONObject().apply {
            put("deviceTransactionId", txnId)
            put("offlineToken", token)
            put("amount", amount)
            put("merchantId", current.merchantId)
            put("transactedAt", transactedAt)
            put("deviceId", deviceId)
            put("signature", signature)
            put("publicKey", publicKey)
        }

        val transaction = WalletTransaction(
            id = txnId,
            merchant = current.selectedMerchant,
            amountRupees = amount.toInt(),
            method = "Offline Token",
            dateLabel = currentFormattedTime(),
            status = TransactionStatus.PENDING_VERIFICATION,
            offlineToken = token,
            signature = signature,
            publicKey = publicKey,
            transactedAt = transactedAt,
            deviceId = deviceId
        )

        val updated = current.copy(
            offlineBalance = (current.offlineBalance - amount).toInt(),
            lastTransaction = transaction,
            lastQrPayload = payloadObj.toString(),
            transactions = listOf(transaction) + current.transactions,
            message = "Offline payment saved! Ready to transmit via Bluetooth or QR."
        )

        _uiState.value = updated
        persistState(updated)

        // If online, immediately sync
        if (current.internetAvailable) {
            syncNow()
        }

        return true
    }

    /**
     * 4. Sync Service Activation: Sends all pending offline payments to sync-service
     * on port 8083 / gateway, which verifies signature and settles in Kafka and Ledger.
     */
    fun syncNow() {
        val current = _uiState.value
        val pending = current.transactions.filter {
            it.status == TransactionStatus.PENDING_VERIFICATION && it.offlineToken != null
        }

        if (pending.isEmpty()) {
            _uiState.update { it.copy(message = "All offline payments are up to date.") }
            refreshBalancesFromBackend()
            return
        }

        _uiState.update { it.copy(isSyncing = true, message = "Syncing ${pending.size} offline payment(s) to backend...") }

        viewModelScope.launch {
            val payloads = pending.map { txn ->
                SyncTransactionPayload(
                    deviceTransactionId = txn.id,
                    offlineToken = txn.offlineToken!!,
                    amount = txn.amountRupees.toDouble(),
                    merchantId = current.merchantId,
                    transactedAt = txn.transactedAt ?: LocalDateTime.now().toString(),
                    deviceId = txn.deviceId ?: getDeviceId(),
                    signature = txn.signature ?: "",
                    publicKey = txn.publicKey ?: CryptoManager.getPublicKeyJwk(context)
                )
            }

            val userId = current.phoneNumber.ifBlank { "9876543210" }
            val syncRes = apiClient.syncOfflineTransactions(userId, payloads)

            syncRes.onSuccess { res ->
                val accepted = res.acceptedDeviceTransactionIds.toSet()
                val updatedList = _uiState.value.transactions.map { txn ->
                    if (accepted.contains(txn.id) || res.acceptedCount == pending.size) {
                        txn.copy(status = TransactionStatus.COMPLETED, dateLabel = "Settled on Bank Ledger")
                    } else {
                        txn
                    }
                }

                val finalState = _uiState.value.copy(
                    isSyncing = false,
                    transactions = updatedList,
                    message = "${res.acceptedCount} payment(s) verified & settled via Sync Service!"
                )
                _uiState.value = finalState
                persistState(finalState)
                refreshBalancesFromBackend()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isSyncing = false, message = "Sync failed: ${err.message}. Will retry when online.")
                }
            }
        }
    }

    /**
     * 5. Process incoming QR scan (e.g. merchant phone receives payment from customer phone).
     */
    fun processScannedQr(raw: String): Boolean {
        return try {
            val json = JSONObject(raw)
            val txnId = json.getString("deviceTransactionId")
            val token = json.getString("offlineToken")
            val amount = json.getDouble("amount")
            val merchantId = json.optString("merchantId", "merchant_canteen")
            val transactedAt = json.getString("transactedAt")
            val devId = json.optString("deviceId", "customer_device")
            val sig = json.getString("signature")
            val pubKey = json.getString("publicKey")

            val canonical = CryptoManager.canonicalString(
                deviceTransactionId = txnId,
                offlineToken = token,
                merchantId = merchantId,
                amount = amount.toString(),
                transactedAt = transactedAt,
                deviceId = devId
            )

            val isValid = CryptoManager.verify(canonical, sig, pubKey)
            if (!isValid) {
                _uiState.update { it.copy(message = "Invalid payment signature! Payment rejected.") }
                return false
            }

            val receivedTxn = WalletTransaction(
                id = txnId,
                merchant = "Received from $devId",
                amountRupees = amount.toInt(),
                method = "Offline QR",
                dateLabel = currentFormattedTime(),
                status = TransactionStatus.PENDING_VERIFICATION,
                offlineToken = token,
                signature = sig,
                publicKey = pubKey,
                transactedAt = transactedAt,
                deviceId = devId
            )

            val current = _uiState.value
            val updated = current.copy(
                transactions = listOf(receivedTxn) + current.transactions,
                message = "Received ₹${amount.toInt()} payment from customer! Ready to sync."
            )
            _uiState.value = updated
            persistState(updated)

            if (current.internetAvailable) syncNow()
            true
        } catch (_: Exception) {
            _uiState.update { it.copy(message = "Scanned QR is not a valid Paron payment.") }
            false
        }
    }

    /**
     * 6. Bluetooth P2P: Start listening on Phone B (Merchant Receiver).
     */
    fun startBluetoothReceiver() {
        if (!bluetoothManager.isBluetoothEnabled()) {
            _uiState.update { it.copy(message = "Please turn on Bluetooth to receive payments.") }
            return
        }

        _uiState.update { it.copy(bluetoothListening = true, message = "Listening for offline Bluetooth payments...") }

        viewModelScope.launch {
            bluetoothManager.startReceiver(
                onListening = {
                    _uiState.update { it.copy(bluetoothListening = true, message = "Ready! Waiting for payer phone...") }
                },
                onPaymentReceived = { offer ->
                    val receivedTxn = WalletTransaction(
                        id = offer.deviceTransactionId,
                        merchant = "Bluetooth • ${offer.merchantId}",
                        amountRupees = offer.amount.toInt(),
                        method = "Bluetooth P2P",
                        dateLabel = currentFormattedTime(),
                        status = TransactionStatus.PENDING_VERIFICATION,
                        offlineToken = offer.offlineToken,
                        signature = offer.signature,
                        publicKey = offer.publicKey,
                        transactedAt = offer.transactedAt,
                        deviceId = offer.deviceId
                    )
                    val current = _uiState.value
                    val updated = current.copy(
                        bluetoothListening = false,
                        transactions = listOf(receivedTxn) + current.transactions,
                        message = "Received ₹${offer.amount.toInt()} via Bluetooth!"
                    )
                    _uiState.value = updated
                    persistState(updated)
                    if (current.internetAvailable) syncNow()
                },
                onError = { err ->
                    _uiState.update { it.copy(bluetoothListening = false, message = "Bluetooth: $err") }
                }
            )
        }
    }

    fun stopBluetoothReceiver() {
        bluetoothManager.stopReceiver()
        _uiState.update { it.copy(bluetoothListening = false) }
    }

    /**
     * 7. Bluetooth P2P: Send payment to selected Phone B.
     */
    fun sendBluetoothPayment(device: BluetoothDevice) {
        val current = _uiState.value
        val amount = current.paymentAmount.toDoubleOrNull() ?: 100.0
        val token = current.activeOfflineToken

        if (token == null) {
            _uiState.update { it.copy(message = "Please reserve an offline token before paying.") }
            return
        }

        val txnId = "txn-" + UUID.randomUUID().toString().take(8)
        val transactedAt = LocalDateTime.now().toString()
        val deviceId = getDeviceId()

        val canonical = CryptoManager.canonicalString(
            deviceTransactionId = txnId,
            offlineToken = token,
            merchantId = current.merchantId,
            amount = amount.toString(),
            transactedAt = transactedAt,
            deviceId = deviceId
        )
        val signature = CryptoManager.sign(context, canonical)
        val publicKey = CryptoManager.getPublicKeyJwk(context)

        val offer = PaymentOfferEnvelope(
            deviceTransactionId = txnId,
            offlineToken = token,
            amount = amount,
            merchantId = current.merchantId,
            transactedAt = transactedAt,
            deviceId = deviceId,
            signature = signature,
            publicKey = publicKey
        )

        _uiState.update { it.copy(message = "Connecting to ${device.name ?: "merchant phone"} via Bluetooth...") }

        viewModelScope.launch {
            val res = bluetoothManager.sendPayment(device, offer)
            res.onSuccess { receipt ->
                val txn = WalletTransaction(
                    id = txnId,
                    merchant = device.name ?: "Bluetooth Merchant",
                    amountRupees = amount.toInt(),
                    method = "Bluetooth P2P",
                    dateLabel = currentFormattedTime(),
                    status = TransactionStatus.PENDING_VERIFICATION,
                    offlineToken = token,
                    signature = signature,
                    publicKey = publicKey,
                    transactedAt = transactedAt,
                    deviceId = deviceId
                )

                val updated = _uiState.value.copy(
                    offlineBalance = (_uiState.value.offlineBalance - amount).toInt(),
                    lastTransaction = txn,
                    transactions = listOf(txn) + _uiState.value.transactions,
                    message = "Bluetooth payment accepted by merchant (${receipt.merchantReceiptId})!"
                )
                _uiState.value = updated
                persistState(updated)

                if (_uiState.value.internetAvailable) syncNow()
            }.onFailure { err ->
                _uiState.update { it.copy(message = "Bluetooth payment failed: ${err.message}") }
            }
        }
    }

    private fun loadInitialState(): AppUiState {
        val defaultState = AppUiState()
        val savedName = demoStore.getString(KEY_USER_NAME, "") ?: ""
        val savedEmail = demoStore.getString(KEY_USER_EMAIL, "") ?: ""
        val savedPhone = demoStore.getString(KEY_PHONE_NUMBER, defaultState.phoneNumber) ?: defaultState.phoneNumber
        val storedTransactions = demoStore.getString(KEY_TRANSACTIONS, null)
            ?.let(::decodeTransactions)
            ?: emptyList()

        return defaultState.copy(
            userName = savedName,
            userEmail = savedEmail,
            phoneNumber = savedPhone,
            authenticated = savedName.isNotBlank() && savedEmail.isNotBlank(),
            onlineBalance = demoStore.getInt(KEY_ONLINE_BALANCE, defaultState.onlineBalance),
            offlineBalance = demoStore.getInt(KEY_OFFLINE_BALANCE, defaultState.offlineBalance),
            activeOfflineToken = demoStore.getString(KEY_OFFLINE_TOKEN, null),
            activeTokenId = demoStore.getString(KEY_TOKEN_ID, null),
            tokenExpiresAt = demoStore.getString(KEY_TOKEN_EXPIRES, null),
            serverBaseUrl = apiClient.baseUrl,
            transactions = storedTransactions
        )
    }

    private fun persistState(state: AppUiState) {
        val transactions = JSONArray()
        state.transactions.forEach { transaction ->
            transactions.put(
                JSONObject().apply {
                    put("id", transaction.id)
                    put("merchant", transaction.merchant)
                    put("amount", transaction.amountRupees)
                    put("method", transaction.method)
                    put("date", transaction.dateLabel)
                    put("status", transaction.status.name)
                    put("offlineToken", transaction.offlineToken ?: "")
                    put("signature", transaction.signature ?: "")
                    put("publicKey", transaction.publicKey ?: "")
                    put("transactedAt", transaction.transactedAt ?: "")
                    put("deviceId", transaction.deviceId ?: "")
                }
            )
        }
        demoStore.edit()
            .putString(KEY_USER_NAME, state.userName)
            .putString(KEY_USER_EMAIL, state.userEmail)
            .putString(KEY_PHONE_NUMBER, state.phoneNumber)
            .putInt(KEY_ONLINE_BALANCE, state.onlineBalance)
            .putInt(KEY_OFFLINE_BALANCE, state.offlineBalance)
            .putString(KEY_OFFLINE_TOKEN, state.activeOfflineToken)
            .putString(KEY_TOKEN_ID, state.activeTokenId)
            .putString(KEY_TOKEN_EXPIRES, state.tokenExpiresAt)
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
                    }.getOrDefault(TransactionStatus.COMPLETED),
                    offlineToken = item.optString("offlineToken").ifBlank { null },
                    signature = item.optString("signature").ifBlank { null },
                    publicKey = item.optString("publicKey").ifBlank { null },
                    transactedAt = item.optString("transactedAt").ifBlank { null },
                    deviceId = item.optString("deviceId").ifBlank { null }
                )
            }
        }.getOrDefault(emptyList())

    companion object {
        const val DEMO_OTP = "123456"
        private const val DEMO_PREFS = "offlinepay_data"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_PHONE_NUMBER = "user_phone"
        private const val KEY_ONLINE_BALANCE = "online_balance"
        private const val KEY_OFFLINE_BALANCE = "offline_balance"
        private const val KEY_OFFLINE_TOKEN = "active_offline_token"
        private const val KEY_TOKEN_ID = "active_token_id"
        private const val KEY_TOKEN_EXPIRES = "token_expires_at"
        private const val KEY_TRANSACTIONS = "transactions"
    }
}
