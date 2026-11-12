package com.offlinepay.network

import android.content.Context
import com.offlinepay.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class AccountBalance(
    val availableBalance: Double,
    val reservedAmount: Double,
    val totalBalance: Double
)

data class OfflineTokenResult(
    val tokenId: String,
    val token: String?,
    val maxAmount: Double,
    val spentAmount: Double = 0.0,
    val expiresAt: String,
    val status: String,
    val reservationRecovered: Boolean = false
)

data class SyncResult(
    val acceptedCount: Int,
    val acceptedDeviceTransactionIds: List<String>,
    val message: String
)
data class DemoSession(val accessToken: String, val userId: String, val expiresIn: Long)
data class UserProfile(val userId: String, val name: String, val email: String)
data class OfflineTransactionStatus(val deviceTransactionId: String, val status: String)
data class PaypalFundingOrder(val fundingId: String, val paypalOrderId: String, val approvalUrl: String)

data class SyncTransactionPayload(
    val deviceTransactionId: String,
    val offlineToken: String,
    val amount: Double,
    val merchantId: String,
    val transactedAt: String,
    val deviceId: String,
    val signature: String,
    val publicKey: String
)

class ParonApiClient(private val context: Context) {
    @Volatile private var accessToken: String? = null

    companion object {
        private const val PREFS_NAME = "offlinepay_network_settings"
        private const val KEY_BASE_URL = "api_base_url"
        private const val DEMO_OTP = "123456"

        // The Render gateway works from both physical devices and emulators.
        // 10.0.2.2 is only valid from the Android emulator, never from a phone.
        const val DEFAULT_BASE_URL = "https://paron-api.onrender.com"
        const val EMULATOR_BASE_URL = "http://10.0.2.2:8080"
        private const val LEGACY_WIFI_BASE_URL = "http://192.168.1.6:8080"
    }

    var baseUrl: String
        get() {
            val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedUrl = preferences.getString(KEY_BASE_URL, null)?.trim()?.removeSuffix("/")
            // Upgrade existing installs that still have the emulator or stale LAN URL saved.
            if (savedUrl.isNullOrBlank() || savedUrl == EMULATOR_BASE_URL || savedUrl == LEGACY_WIFI_BASE_URL) {
                preferences.edit().putString(KEY_BASE_URL, DEFAULT_BASE_URL).apply()
                return DEFAULT_BASE_URL
            }
            return savedUrl
        }
        set(value) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_BASE_URL, value.trim().removeSuffix("/"))
                .apply()
        }

    /** App requests go through the API gateway; the gateway routes to each service. */
    private fun gatewayUrl(path: String): String = baseUrl.trim().removeSuffix("/") + path

    suspend fun createDemoSession(email: String, password: String): Result<DemoSession> = withContext(Dispatchers.IO) {
        runCatching {
            val response = httpPostRaw(
                gatewayUrl("/api/v1/auth/otp/verify"),
                JSONObject().put("phoneNumber", email).put("code", password).toString(), false
            )
            val json = JSONObject(response)
            DemoSession(json.getString("accessToken"), json.getString("userId"), json.getLong("expiresIn"))
                .also { accessToken = it.accessToken }
        }
    }

    suspend fun saveUserProfile(userId: String, name: String, email: String): Result<UserProfile> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject().put("name", name).put("email", email).toString()
                val response = httpPost(
                    gatewayUrl("/api/v1/auth/profile"),
                    body,
                    userId
                )
                val json = JSONObject(response)
                UserProfile(json.getString("userId"), json.getString("name"), json.getString("email"))
            }
        }

    suspend fun createTestAccount(userId: String, initialBalance: Double = 1000.0): Result<AccountBalance> =
        withContext(Dispatchers.IO) {
            val existingBalance = fetchBalance(userId)
            if (existingBalance.isSuccess) return@withContext existingBalance

            runCatching {
                val url = gatewayUrl("/api/v1/ledger/accounts/create-test-account")
                val body = JSONObject().apply {
                    put("userId", userId)
                    put("initialBalance", initialBalance)
                }.toString()

                val responseJson = httpPost(url, body, userId)
                val obj = JSONObject(responseJson)
                AccountBalance(
                    availableBalance = obj.optDouble("availableBalance", initialBalance),
                    reservedAmount = obj.optDouble("reservedAmount", 0.0),
                    totalBalance = obj.optDouble("totalBalance", initialBalance)
                )
            }.recoverCatching {
                // If account already exists on ledger, fetch current balance
                fetchBalance(userId).getOrThrow()
            }
        }

    suspend fun fetchBalance(userId: String): Result<AccountBalance> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = gatewayUrl("/api/v1/ledger/balance/$userId")
                val responseJson = httpGet(url, userId)
                val obj = JSONObject(responseJson)
                AccountBalance(
                    availableBalance = obj.optDouble("availableBalance", 0.0),
                    reservedAmount = obj.optDouble("reservedAmount", 0.0),
                    totalBalance = obj.optDouble("totalBalance", 0.0)
                )
            }
        }

    suspend fun createPaypalFundingOrder(userId: String, amountUsd: Double): Result<PaypalFundingOrder> = withContext(Dispatchers.IO) {
        runCatching {
            val response = httpPost(gatewayUrl("/api/funding/paypal/orders"), JSONObject().put("amount", amountUsd).toString(), userId)
            val json = JSONObject(response)
            PaypalFundingOrder(json.getString("fundingId"), json.getString("paypalOrderId"), json.getString("approvalUrl"))
        }
    }

    suspend fun capturePaypalFundingOrder(userId: String, paypalOrderId: String): Result<AccountBalance> = withContext(Dispatchers.IO) {
        runCatching {
            httpPost(gatewayUrl("/api/funding/paypal/orders/${java.net.URLEncoder.encode(paypalOrderId, "UTF-8")}/capture"), "{}", userId)
            fetchBalance(userId).getOrThrow()
        }
    }

    suspend fun fetchActiveOfflineToken(userId: String, deviceId: String): Result<OfflineTokenResult?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = httpGetOptional(gatewayUrl("/api/v1/tokens/active?deviceId=$deviceId"), userId)
                    ?: return@runCatching null
                val obj = JSONObject(response)
                OfflineTokenResult(
                    tokenId = obj.optString("tokenId"),
                    token = obj.optString("token").takeUnless { it.isBlank() || it == "null" },
                    maxAmount = obj.optDouble("maxAmount", 0.0),
                    spentAmount = obj.optDouble("spentAmount", 0.0),
                    expiresAt = obj.optString("expiresAt"),
                    status = obj.optString("status", "ACTIVE"),
                    reservationRecovered = obj.optBoolean("reservationRecovered", false)
                )
            }
        }

    suspend fun recoverActiveOfflineToken(userId: String, deviceId: String): Result<OfflineTokenResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val encodedDeviceId = java.net.URLEncoder.encode(deviceId, "UTF-8")
                val response = httpPost(
                    gatewayUrl("/api/v1/tokens/active/recover?deviceId=$encodedDeviceId"),
                    "{}",
                    userId
                )
                val obj = JSONObject(response)
                OfflineTokenResult(
                    tokenId = obj.optString("tokenId"),
                    token = obj.optString("token").takeUnless { it.isBlank() || it == "null" },
                    maxAmount = obj.optDouble("maxAmount", 0.0),
                    spentAmount = obj.optDouble("spentAmount", 0.0),
                    expiresAt = obj.optString("expiresAt"),
                    status = obj.optString("status", "ACTIVE"),
                    reservationRecovered = false
                ).also {
                    require(!it.token.isNullOrBlank()) { "The backend did not return a usable offline token." }
                }
            }
        }

    suspend fun requestOfflineToken(
        userId: String,
        deviceId: String,
        amount: Double,
        expiryHours: Int = 6
    ): Result<OfflineTokenResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                // Ensure account exists on ledger
                createTestAccount(userId, 1000.0).getOrThrow()

                val url = gatewayUrl("/api/v1/tokens/gettoken")
                val body = JSONObject().apply {
                    put("userId", userId)
                    put("deviceId", deviceId)
                    put("amount", amount)
                    put("expiryHours", expiryHours)
                }.toString()

                val responseJson = httpPost(url, body, userId)
                val obj = JSONObject(responseJson)
                OfflineTokenResult(
                    tokenId = obj.optString("tokenId"),
                    token = obj.getString("token"),
                    maxAmount = obj.optDouble("maxAmount", amount),
                    spentAmount = obj.optDouble("spentAmount", 0.0),
                    expiresAt = obj.optString("expiresAt"),
                    status = obj.optString("status", "ACTIVE"),
                    reservationRecovered = obj.optBoolean("reservationRecovered", false)
                )
            }
        }

    suspend fun syncOfflineTransactions(
        userId: String,
        transactions: List<SyncTransactionPayload>
    ): Result<SyncResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = gatewayUrl("/api/v1/sync/$userId")
                val jsonArray = JSONArray()
                transactions.forEach { txn ->
                    jsonArray.put(JSONObject().apply {
                        put("deviceTransactionId", txn.deviceTransactionId)
                        put("offlineToken", txn.offlineToken)
                        put("amount", txn.amount)
                        put("merchantId", txn.merchantId)
                        put("transactedAt", txn.transactedAt)
                        put("deviceId", txn.deviceId)
                        put("signature", txn.signature)
                        put("publicKey", txn.publicKey)
                    })
                }

                val body = JSONObject().apply {
                    put("transactions", jsonArray)
                }.toString()

                val responseJson = httpPost(url, body, userId)
                val obj = JSONObject(responseJson)
                val acceptedIds = mutableListOf<String>()
                val array = obj.optJSONArray("acceptedDeviceTransactionIds")
                if (array != null) {
                    for (i in 0 until array.length()) {
                        acceptedIds.add(array.getString(i))
                    }
                }
                SyncResult(
                    acceptedCount = obj.optInt("acceptedCount", 0),
                    acceptedDeviceTransactionIds = acceptedIds,
                    message = obj.optString("message", "Sync completed")
                )
            }
        }

    suspend fun fetchOfflineTransactionStatuses(userId: String): Result<List<OfflineTransactionStatus>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = httpGet(gatewayUrl("/api/v1/sync/$userId"), userId)
                val array = JSONArray(response)
                List(array.length()) { index ->
                    val item = array.getJSONObject(index)
                    OfflineTransactionStatus(
                        deviceTransactionId = item.getString("deviceTransactionId"),
                        status = item.getString("status")
                    )
                }
            }
        }

    private fun httpGet(urlStr: String, userId: String): String {
        return retryAfterDemoSessionRenewal(userId) { httpGetOnce(urlStr) }
    }

    private fun httpGetOnce(urlStr: String): String {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        // Render free services can take close to a minute to wake. Funding capture
        // also calls PayPal and the ledger, so a short mobile timeout can hide a
        // successful server-side capture/credit.
        conn.connectTimeout = 15000
        conn.readTimeout = 120000
        val token = accessToken ?: throw IllegalStateException("Sign in before calling the Paron API")
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Accept", "application/json")

        val code = conn.responseCode
        if (code in 200..299) {
            return conn.inputStream.bufferedReader().use(BufferedReader::readText)
        } else {
            val error = conn.errorStream?.bufferedReader()?.use(BufferedReader::readText) ?: "HTTP $code"
            throw Exception("API GET error ($code): ${readableError(error)}")
        }
    }

    private fun httpGetOptional(urlStr: String, userId: String): String? {
        return retryAfterDemoSessionRenewal(userId) { httpGetOptionalOnce(urlStr) }
    }

    private fun httpGetOptionalOnce(urlStr: String): String? {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 15000
        conn.readTimeout = 120000
        val token = accessToken ?: throw IllegalStateException("Sign in before calling the Paron API")
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Accept", "application/json")

        val code = conn.responseCode
        if (code == HttpURLConnection.HTTP_NOT_FOUND) return null
        if (code in 200..299) {
            return conn.inputStream.bufferedReader().use(BufferedReader::readText)
        }
        val error = conn.errorStream?.bufferedReader()?.use(BufferedReader::readText) ?: "HTTP $code"
        throw Exception("API GET error ($code): ${readableError(error)}")
    }

    private fun httpPost(urlStr: String, body: String, userId: String): String =
        retryAfterDemoSessionRenewal(userId) { httpPostRaw(urlStr, body, true) }

    /**
     * Demo auth sessions are short lived. Renew only when the gateway explicitly
     * rejects the session JWT, then retry the same request once. Offline receipts
     * remain queued on-device if renewal or the retry fails.
     */
    private fun <T> retryAfterDemoSessionRenewal(userId: String, request: () -> T): T {
        try {
            return request()
        } catch (firstFailure: Exception) {
            val isExpiredGatewaySession = BuildConfig.DEMO_MODE &&
                firstFailure.message?.let {
                    it.contains("API POST error (401): Invalid or expired token") ||
                        it.contains("API GET error (401): Invalid or expired token")
                } == true
            if (!isExpiredGatewaySession) throw firstFailure

            renewDemoSession(userId)
            return request()
        }
    }

    private fun renewDemoSession(userId: String) {
        val body = JSONObject()
            .put("phoneNumber", userId)
            .put("code", DEMO_OTP)
            .toString()
        val response = httpPostRaw(gatewayUrl("/api/v1/auth/otp/verify"), body, false)
        accessToken = JSONObject(response).getString("accessToken")
    }

    private fun httpPostRaw(urlStr: String, body: String, authenticated: Boolean): String {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 15000
        conn.readTimeout = 120000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        if (authenticated) {
            val token = accessToken ?: throw IllegalStateException("Sign in before calling the Paron API")
            conn.setRequestProperty("Authorization", "Bearer $token")
        }
        conn.setRequestProperty("Accept", "application/json")

        OutputStreamWriter(conn.outputStream).use { it.write(body) }

        val code = conn.responseCode
        if (code in 200..299) {
            return conn.inputStream.bufferedReader().use(BufferedReader::readText)
        } else {
            val error = conn.errorStream?.bufferedReader()?.use(BufferedReader::readText) ?: "HTTP $code"
            throw Exception("API POST error ($code): ${readableError(error)}")
        }
    }

    private fun readableError(body: String): String = runCatching {
        val json = JSONObject(body)
        val message = sequenceOf("message", "detail", "error_description")
            .map { json.optString(it) }
            .firstOrNull { it.isNotBlank() }
        val code = sequenceOf("errorCode", "error")
            .map { json.optString(it) }
            .firstOrNull { it.isNotBlank() }
        val status = json.optInt("status", 0).takeIf { it > 0 }
        when {
            message != null && code != null -> "$message ($code)"
            message != null -> message
            code != null && status != null -> "$code (HTTP $status)"
            code != null -> code
            else -> body
        }
    }.getOrNull()?.takeIf(String::isNotBlank) ?: body
}
