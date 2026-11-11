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
import kotlinx.coroutines.delay
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
        if (available && wasOffline && _uiState.value.authenticated) {
            syncNow()
        }
    }

    fun dismissOfflineFallback() {
        _uiState.update { it.copy(showOfflineFallback = false) }
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

    fun saveUserProfile(name: String, email: String, onSaved: () -> Unit) {
        val current = _uiState.value
        viewModelScope.launch {
            apiClient.saveUserProfile(current.phoneNumber, name.trim(), email.trim()).onSuccess { profile ->
                val updated = _uiState.value.copy(
                    userName = profile.name,
                    userEmail = profile.email,
                    authenticated = true,
                    message = "Welcome, ${profile.name}!"
                )
                _uiState.value = updated
                persistState(updated)
                refreshBalancesFromBackend()
                onSaved()
            }.onFailure { error ->
                _uiState.update { it.copy(message = "Could not save your profile: ${error.message}") }
            }
        }
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

    /** The demo OTP is verified by auth-service and yields the gateway JWT. */
    fun authenticateDemo(code: String, onAuthenticated: () -> Unit) {
        val phoneNumber = _uiState.value.phoneNumber
        if (phoneNumber.length != 10) {
            _uiState.update { it.copy(message = "Enter your phone number before signing in.") }
            return
        }
        viewModelScope.launch {
            apiClient.createDemoSession(phoneNumber, code).onSuccess { session ->
                _uiState.update {
                    it.copy(
                        authenticated = true,
                        message = "Signed in as ${session.userId}."
                    )
                }
                refreshBalancesFromBackend()
                onAuthenticated()
                if (_uiState.value.internetAvailable && _uiState.value.transactions.any {
                        it.status == TransactionStatus.PENDING_VERIFICATION && it.offlineToken != null
                    }) {
                    syncNow()
                }
            }.onFailure { error ->
                _uiState.update { it.copy(message = "Sign-in failed: ${error.message}") }
            }
        }
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
        _uiState.update { it.copy(paymentAmount = value.filter(Char::isDigit).take(8)) }
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
        // The auth-service JWT subject is the verified phone number. Keep every
        // ledger call tied to that subject; a display name is not an account ID.
        val currentUserId = _uiState.value.phoneNumber.ifBlank { return }
        viewModelScope.launch {
            apiClient.createTestAccount(currentUserId, 1000.0)
            val balanceRes = apiClient.fetchBalance(currentUserId)
            balanceRes.onSuccess { bal ->
                val activeTokenResult = apiClient.fetchActiveOfflineToken(currentUserId, getDeviceId())
                val restoredToken = activeTokenResult.getOrNull()
                val updated = _uiState.value.let { current ->
                    val isSameToken = restoredToken != null &&
                        restoredToken.tokenId == current.activeTokenId && current.activeOfflineToken != null
                    current.copy(
                        onlineBalance = bal.availableBalance.toInt(),
                        remoteTokenRemaining = restoredToken?.let {
                            (it.maxAmount - it.spentAmount).toInt().coerceAtLeast(0)
                        },
                        offlineBalance = when {
                            restoredToken != null && restoredToken.token == null -> 0
                            restoredToken != null && restoredToken.token != null && !isSameToken -> (restoredToken.maxAmount - restoredToken.spentAmount).toInt().coerceAtLeast(0)
                            restoredToken != null -> current.offlineBalance
                            // Ledger reservations without a token returned for this device are locked,
                            // but cannot be spent from this screen.
                            activeTokenResult.isSuccess -> 0
                            current.activeOfflineToken != null -> current.offlineBalance
                            else -> 0
                        },
                        activeOfflineToken = restoredToken?.token
                            ?: if (activeTokenResult.isSuccess) null else current.activeOfflineToken,
                        activeTokenId = restoredToken?.tokenId
                            ?: if (activeTokenResult.isSuccess) null else current.activeTokenId,
                        tokenExpiresAt = restoredToken?.expiresAt
                            ?: if (activeTokenResult.isSuccess) null else current.tokenExpiresAt
                    )
                }
                _uiState.value = updated
                persistState(updated)
            }
        }
    }

    fun startPaypalFunding(amount: String, openApproval: (String) -> Unit) {
        val userId = _uiState.value.phoneNumber
        val amountUsd = amount.toDoubleOrNull()
        if (userId.isBlank() || amountUsd == null || amountUsd <= 0.0) {
            _uiState.update { it.copy(message = "Enter a valid PayPal Sandbox amount and sign in.") }
            return
        }
        _uiState.update { it.copy(isFunding = true, message = "Creating PayPal Sandbox order…") }
        viewModelScope.launch {
            apiClient.createPaypalFundingOrder(userId, amountUsd).onSuccess { order ->
                _uiState.update { it.copy(isFunding = false, message = "Approve the payment in PayPal Sandbox.") }
                openApproval(order.approvalUrl)
            }.onFailure { error ->
                _uiState.update { it.copy(isFunding = false, message = "PayPal funding failed: ${error.message}") }
            }
        }
    }

    fun handlePaypalReturn(orderId: String?, cancelled: Boolean) {
        if (cancelled) {
            _uiState.update { it.copy(isFunding = false, message = "PayPal payment cancelled.") }
            return
        }
        if (orderId.isNullOrBlank()) {
            _uiState.update { it.copy(isFunding = false, message = "PayPal did not return an order ID.") }
            return
        }
        val userId = _uiState.value.phoneNumber
        _uiState.update { it.copy(isFunding = true, message = "Verifying PayPal payment…") }
        viewModelScope.launch {
            apiClient.capturePaypalFundingOrder(userId, orderId).onSuccess { balance ->
                _uiState.update { it.copy(isFunding = false, onlineBalance = balance.availableBalance.toInt(), message = "PayPal Sandbox payment captured and wallet updated.") }
            }.onFailure { error ->
                _uiState.update { it.copy(isFunding = false, message = "PayPal capture failed: ${error.message}") }
            }
        }
    }

    /**
     * 2. Reserve Offline Token from Backend.
     * Calls token-service /api/v1/tokens/gettoken, which locks funds in ledger-service
     * and returns the signed JWT offline token.
     */
    fun reserveOffline(amountText: String) {
        reserveOfflineInternal(amountText, afterReserved = null)
    }

    fun reserveOfflineForPayment(amountText: String, afterReserved: () -> Unit) {
        reserveOfflineInternal(amountText, afterReserved)
    }

    fun recoverActiveTokenOnThisDevice() {
        val state = _uiState.value
        if (!state.internetAvailable) {
            _uiState.update { it.copy(message = "Connect to the internet to recover your active token.") }
            return
        }
        if (state.phoneNumber.isBlank()) {
            _uiState.update { it.copy(message = "Sign in before recovering your active token.") }
            return
        }
        _uiState.update { it.copy(isReserving = true, message = "Recovering active offline token...") }
        viewModelScope.launch {
            apiClient.recoverActiveOfflineToken(state.phoneNumber, getDeviceId())
                .onSuccess { token ->
                    val remaining = (token.maxAmount - token.spentAmount).toInt().coerceAtLeast(0)
                    val updated = _uiState.value.copy(
                        isReserving = false,
                        activeOfflineToken = token.token,
                        activeTokenId = token.tokenId,
                        tokenExpiresAt = token.expiresAt,
                        offlineBalance = remaining,
                        remoteTokenRemaining = remaining,
                        message = "₹$remaining token recovered. The previous device's token is now invalid. Sync its pending payments before using this device."
                    )
                    _uiState.value = updated
                    persistState(updated)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isReserving = false, message = "Could not recover active token: ${error.message}")
                    }
                }
        }
    }

    private fun reserveOfflineInternal(amountText: String, afterReserved: (() -> Unit)?) {
        val amount = amountText.toDoubleOrNull()
        val state = _uiState.value

        if (amount == null || amount <= 0) {
            _uiState.update { it.copy(message = "Please enter a valid amount.") }
            return
        }

        if (!state.internetAvailable) {
            _uiState.update { it.copy(message = "Internet connection required to request a token from the backend.") }
            return
        }

        val userId = state.phoneNumber
        if (userId.isBlank()) {
            _uiState.update { it.copy(message = "Sign in before reserving an offline token.") }
            return
        }

        _uiState.update { it.copy(isReserving = true, message = "Requesting offline token from backend...") }

        viewModelScope.launch {
            val deviceId = getDeviceId()

            // Token-service is authoritative; local cached state may be stale.
            val activeResult = apiClient.fetchActiveOfflineToken(userId, deviceId)
            if (activeResult.isFailure) {
                _uiState.update {
                    it.copy(
                        isReserving = false,
                        message = "Could not check for an active token: ${activeResult.exceptionOrNull()?.message}"
                    )
                }
                return@launch
            }
            val existingToken = activeResult.getOrNull()
            if (existingToken != null) {
                if (existingToken.token.isNullOrBlank()) {
                    val blocked = _uiState.value.copy(
                        isReserving = false,
                        activeOfflineToken = null,
                        activeTokenId = existingToken.tokenId,
                        tokenExpiresAt = existingToken.expiresAt,
                        offlineBalance = 0,
                        message = "An active token is assigned to another device. Review the recovery option in Wallet before paying here."
                    )
                    _uiState.value = blocked
                    persistState(blocked)
                    return@launch
                }
                val remaining = (existingToken.maxAmount - existingToken.spentAmount).coerceAtLeast(0.0)
                val restored = _uiState.value.copy(
                    isReserving = false,
                    activeOfflineToken = existingToken.token,
                    activeTokenId = existingToken.tokenId,
                    tokenExpiresAt = existingToken.expiresAt,
                    offlineBalance = remaining.toInt(),
                    message = if (afterReserved == null) {
                        "Your active offline token is ready. ₹${remaining.toInt()} remains."
                    } else null
                )
                _uiState.value = restored
                persistState(restored)
                if (afterReserved != null) afterReserved()
                return@launch
            }

            if (amount > _uiState.value.onlineBalance) {
                _uiState.update {
                    it.copy(isReserving = false, message = "Amount exceeds available balance (₹${it.onlineBalance}).")
                }
                return@launch
            }

            val result = apiClient.requestOfflineToken(
                userId = userId,
                deviceId = deviceId,
                amount = amount,
                expiryHours = 6
            )

            result.onSuccess { tokenRes ->
                val newOffline = (tokenRes.maxAmount - tokenRes.spentAmount).toInt().coerceAtLeast(0)
                val reserveTxn = WalletTransaction(
                    id = "res-" + UUID.randomUUID().toString().take(8),
                    merchant = if (tokenRes.reservationRecovered) "Existing Offline Reserve" else "Bank Ledger Reserve",
                    amountRupees = if (tokenRes.reservationRecovered) tokenRes.maxAmount else amount,
                    method = if (tokenRes.reservationRecovered) "Token Recovery" else "Token Allocation",
                    dateLabel = currentFormattedTime(),
                    status = TransactionStatus.COMPLETED
                )

                val current = _uiState.value
                val updated = current.copy(
                    isReserving = false,
                    activeOfflineToken = tokenRes.token,
                    activeTokenId = tokenRes.tokenId,
                    tokenExpiresAt = tokenRes.expiresAt,
                    offlineBalance = newOffline,
                    onlineBalance = if (tokenRes.reservationRecovered) current.onlineBalance
                        else (current.onlineBalance - amount).toInt().coerceAtLeast(0),
                    transactions = listOf(reserveTxn) + current.transactions,
                    message = if (afterReserved == null) {
                        if (tokenRes.reservationRecovered) {
                            "Recovered your existing ₹${newOffline} offline reservation. No extra funds were locked."
                        } else {
                            "₹${amount.toInt()} reserved! Token issued from backend for offline spending."
                        }
                    } else null
                )
                _uiState.value = updated
                persistState(updated)
                if (afterReserved != null) {
                    afterReserved()
                    if (tokenRes.reservationRecovered) refreshBalancesFromBackend()
                } else {
                    refreshBalancesFromBackend()
                }
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
     * An online payment is never silently converted into an offline one.  The
     * user must explicitly accept the fallback after the online attempt fails.
     */
    fun attemptOnlinePayment(): Boolean {
        val current = _uiState.value
        val amount = current.paymentAmount.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            _uiState.update { it.copy(message = "Please enter a valid amount.") }
            return false
        }
        if (!current.internetAvailable) {
            val failedAttempt = WalletTransaction(
                id = "online-unavailable-" + UUID.randomUUID().toString().take(8),
                merchant = current.selectedMerchant,
                amountRupees = amount,
                method = "Online payment",
                dateLabel = currentFormattedTime(),
                status = TransactionStatus.FAILED
            )
            val updated = current.copy(
                showOfflineFallback = true,
                transactions = listOf(failedAttempt) + current.transactions,
                syncEvents = listOf(
                    SyncEvent(
                        "Online payment unavailable",
                        "No network was available. No online debit was made.",
                        currentFormattedTime(),
                        RiskSeverity.INFO
                    )
                ) + current.syncEvents,
                message = "Online payment needs internet. You can choose your reserved offline balance instead."
            )
            _uiState.value = updated
            persistState(updated)
            return false
        }
        if (amount > current.onlineBalance) {
            _uiState.update { it.copy(message = "Insufficient online balance.") }
            return false
        }

        // No UPI/acquirer integration exists in this project. Never claim an
        // online transfer succeeded or debit the displayed balance locally.
        val failedAttempt = WalletTransaction(
            id = "upi-" + UUID.randomUUID().toString().take(8),
            merchant = current.selectedMerchant,
            amountRupees = amount,
            method = "UPI Online",
            dateLabel = currentFormattedTime(),
            status = TransactionStatus.FAILED
        )
        val updated = current.copy(
            showOfflineFallback = true,
            transactions = listOf(failedAttempt) + current.transactions,
            syncEvents = listOf(
                SyncEvent(
                    "Online UPI unavailable",
                    "No payment provider is connected. No online debit was made.",
                    currentFormattedTime(),
                    RiskSeverity.REVIEW
                )
            ) + current.syncEvents,
            message = "Online UPI is not connected yet. No debit was made; you can use your reserved offline balance instead."
        )
        _uiState.value = updated
        persistState(updated)
        return false
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
            amountRupees = amount,
            method = "Offline Token",
            dateLabel = currentFormattedTime(),
            status = TransactionStatus.PENDING_VERIFICATION,
            offlineToken = token,
            signature = signature,
            publicKey = publicKey,
            transactedAt = transactedAt,
            deviceId = deviceId,
            merchantId = current.merchantId
        )

        val updated = current.copy(
            offlineBalance = (current.offlineBalance - amount).toInt(),
            lastTransaction = transaction,
            lastQrPayload = payloadObj.toString(),
            transactions = listOf(transaction) + current.transactions,
            showOfflineFallback = false,
            syncEvents = listOf(
                SyncEvent(
                    "Offline receipt signed",
                    "${transaction.id} was signed on this device and queued for reconciliation.",
                    currentFormattedTime(),
                    RiskSeverity.INFO
                )
            ) + current.syncEvents,
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

        _uiState.update {
            it.copy(
                isSyncing = true,
                syncEvents = listOf(
                    SyncEvent(
                        "Sync started",
                        "Uploading ${pending.size} signed offline receipt(s) for server verification.",
                        currentFormattedTime(),
                        RiskSeverity.INFO
                    )
                ) + it.syncEvents,
                message = "Syncing ${pending.size} offline payment(s) to backend..."
            )
        }

        viewModelScope.launch {
            val payloads = pending.map { txn ->
                SyncTransactionPayload(
                    deviceTransactionId = txn.id,
                    offlineToken = txn.offlineToken!!,
                    amount = txn.amountRupees,
                    merchantId = txn.merchantId ?: current.merchantId,
                    transactedAt = txn.transactedAt ?: LocalDateTime.now().toString(),
                    deviceId = txn.deviceId ?: getDeviceId(),
                    signature = txn.signature ?: "",
                    publicKey = txn.publicKey ?: CryptoManager.getPublicKeyJwk(context)
                )
            }

            val userId = current.phoneNumber.ifBlank { return@launch }
            val syncRes = apiClient.syncOfflineTransactions(userId, payloads)

            syncRes.onSuccess { res ->
                val accepted = res.acceptedDeviceTransactionIds.toSet()
                val rejectedIds = pending.map { it.id }.filterNot(accepted::contains).toSet()
                if (accepted.isEmpty()) {
                    val failedState = _uiState.value.copy(
                        isSyncing = false,
                        transactions = _uiState.value.transactions.map { txn ->
                            if (txn.id in rejectedIds) txn.copy(status = TransactionStatus.FAILED, dateLabel = "Rejected by sync service") else txn
                        },
                        message = "The sync service rejected the queued receipts. Check the receipt details before retrying."
                    )
                    _uiState.value = failedState
                    persistState(failedState)
                    return@onSuccess
                }

                val queuedState = _uiState.value.copy(
                    transactions = _uiState.value.transactions.map { txn ->
                        if (txn.id in rejectedIds) txn.copy(status = TransactionStatus.FAILED, dateLabel = "Rejected by sync service") else txn
                    },
                    syncEvents = listOf(
                        SyncEvent(
                            "Receipts queued for settlement",
                            "${res.acceptedCount} receipt(s) passed signature checks and were queued. The ledger has not confirmed settlement yet.",
                            currentFormattedTime(),
                            RiskSeverity.INFO
                        )
                    ) + _uiState.value.syncEvents,
                    message = "${res.acceptedCount} payment(s) queued. Waiting for ledger settlement..."
                )
                _uiState.value = queuedState
                persistState(queuedState)

                // Sync accepts receipts asynchronously. Poll the persisted sync
                // status instead of telling the user a queued receipt is settled.
                var settledStatuses = emptyMap<String, String>()
                var attempts = 0
                while (attempts < 20) {
                    delay(2000)
                    attempts++
                    val statusResult = apiClient.fetchOfflineTransactionStatuses(userId)
                    statusResult.onSuccess { statuses ->
                        settledStatuses = statuses
                            .filter { it.deviceTransactionId in accepted }
                            .associate { it.deviceTransactionId to it.status }
                    }
                    if (accepted.all { settledStatuses[it] in setOf("SETTLED", "REJECTED", "HELD_FOR_REVIEW") }) {
                        break
                    }
                }

                val settledCount = settledStatuses.count { it.value == "SETTLED" }
                val rejectedCount = settledStatuses.count { it.value == "REJECTED" }
                val heldCount = settledStatuses.count { it.value == "HELD_FOR_REVIEW" }
                val pendingCount = accepted.size - settledCount - rejectedCount - heldCount
                val finalState = _uiState.value.copy(
                    isSyncing = false,
                    transactions = _uiState.value.transactions.map { txn ->
                        when (settledStatuses[txn.id]) {
                            "SETTLED" -> txn.copy(status = TransactionStatus.COMPLETED, dateLabel = "Settled on Bank Ledger")
                            "REJECTED" -> txn.copy(status = TransactionStatus.FAILED, dateLabel = "Rejected by sync service")
                            "HELD_FOR_REVIEW" -> txn.copy(dateLabel = "Held for review")
                            else -> txn
                        }
                    },
                    syncEvents = listOf(
                        SyncEvent(
                            "Settlement status checked",
                            "$settledCount settled, $rejectedCount rejected, $heldCount held for review, $pendingCount still processing.",
                            currentFormattedTime(),
                            if (pendingCount == 0 && rejectedCount == 0 && heldCount == 0) RiskSeverity.INFO else RiskSeverity.REVIEW
                        )
                    ) + _uiState.value.syncEvents,
                    message = if (settledCount == accepted.size) {
                        "${accepted.size} payment(s) settled by the ledger."
                    } else {
                        "Sync accepted ${res.acceptedCount}; $settledCount settled, $rejectedCount rejected, $heldCount held for review, $pendingCount still processing."
                    }
                )
                _uiState.value = finalState
                persistState(finalState)
                refreshBalancesFromBackend()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        syncEvents = listOf(
                            SyncEvent("Sync retry queued", "The backend did not confirm settlement: ${err.message}", currentFormattedTime(), RiskSeverity.REVIEW)
                        ) + it.syncEvents,
                        message = "Sync failed: ${err.message}. Will retry when online."
                    )
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
                amountRupees = amount,
                method = "Offline QR",
                dateLabel = currentFormattedTime(),
                status = TransactionStatus.PENDING_VERIFICATION,
                offlineToken = token,
                signature = sig,
                publicKey = pubKey,
                transactedAt = transactedAt,
                deviceId = devId,
                merchantId = merchantId
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
                        amountRupees = offer.amount,
                        method = "Bluetooth P2P",
                        dateLabel = currentFormattedTime(),
                        status = TransactionStatus.PENDING_VERIFICATION,
                        offlineToken = offer.offlineToken,
                        signature = offer.signature,
                        publicKey = offer.publicKey,
                        transactedAt = offer.transactedAt,
                        deviceId = offer.deviceId,
                        merchantId = offer.merchantId
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
                    amountRupees = amount,
                    method = "Bluetooth P2P",
                    dateLabel = currentFormattedTime(),
                    status = TransactionStatus.PENDING_VERIFICATION,
                    offlineToken = token,
                    signature = signature,
                    publicKey = publicKey,
                    transactedAt = transactedAt,
                    deviceId = deviceId,
                    merchantId = current.merchantId
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
            // Session JWTs deliberately remain in memory. A saved display name
            // must never restore an authenticated session after an app restart.
            authenticated = false,
            onlineBalance = demoStore.getInt(KEY_ONLINE_BALANCE, defaultState.onlineBalance),
            offlineBalance = demoStore.getInt(KEY_OFFLINE_BALANCE, defaultState.offlineBalance),
            remoteTokenRemaining = demoStore.getInt(KEY_REMOTE_TOKEN_REMAINING, -1).takeIf { it >= 0 },
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
                    put("merchantId", transaction.merchantId ?: "")
                }
            )
        }
        demoStore.edit()
            .putString(KEY_USER_NAME, state.userName)
            .putString(KEY_USER_EMAIL, state.userEmail)
            .putString(KEY_PHONE_NUMBER, state.phoneNumber)
            .putInt(KEY_ONLINE_BALANCE, state.onlineBalance)
            .putInt(KEY_OFFLINE_BALANCE, state.offlineBalance)
            .putInt(KEY_REMOTE_TOKEN_REMAINING, state.remoteTokenRemaining ?: -1)
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
                    amountRupees = item.optDouble("amount"),
                    method = item.optString("method"),
                    dateLabel = item.optString("date"),
                    status = runCatching {
                        TransactionStatus.valueOf(item.optString("status"))
                    }.getOrDefault(TransactionStatus.COMPLETED),
                    offlineToken = item.optString("offlineToken").ifBlank { null },
                    signature = item.optString("signature").ifBlank { null },
                    publicKey = item.optString("publicKey").ifBlank { null },
                    transactedAt = item.optString("transactedAt").ifBlank { null },
                    deviceId = item.optString("deviceId").ifBlank { null },
                    merchantId = item.optString("merchantId").ifBlank { null }
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
        private const val KEY_REMOTE_TOKEN_REMAINING = "remote_token_remaining"
        private const val KEY_OFFLINE_TOKEN = "active_offline_token"
        private const val KEY_TOKEN_ID = "active_token_id"
        private const val KEY_TOKEN_EXPIRES = "token_expires_at"
        private const val KEY_TRANSACTIONS = "transactions"
    }
}
