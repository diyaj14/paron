package com.offlinepay.network

import android.content.Context
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
    val token: String,
    val maxAmount: Double,
    val expiresAt: String,
    val status: String
)

data class SyncResult(
    val acceptedCount: Int,
    val acceptedDeviceTransactionIds: List<String>,
    val message: String
)
data class DemoSession(val accessToken: String, val userId: String, val expiresIn: Long)

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

        const val DEFAULT_BASE_URL = "http://10.0.2.2:8080"
        const val PHYSICAL_DEVICE_URL = "http://192.168.1.6:8080"
    }

    var baseUrl: String
        get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(value) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_BASE_URL, value.trim().removeSuffix("/"))
                .apply()
        }

    /**
     * Resolves the target microservice URL:
     * - Token Service on port 8081
     * - Ledger Service on port 8082
     * - Sync Service on port 8083
     * Extracts host from baseUrl (whether 10.0.2.2, 192.168.x.x, or localhost).
     */
    private fun getServiceUrl(port: Int, path: String): String {
        return baseUrl.trim().removeSuffix("/") + path
    }

    suspend fun createDemoSession(email: String, password: String): Result<DemoSession> = withContext(Dispatchers.IO) {
        runCatching {
            val response = httpPostRaw(
                getServiceUrl(8080, "/api/v1/auth/demo/session"),
                JSONObject().put("username", email).put("password", password).toString(), false
            )
            val json = JSONObject(response)
            DemoSession(json.getString("accessToken"), json.getString("userId"), json.getLong("expiresIn"))
                .also { accessToken = it.accessToken }
        }
    }

    suspend fun createTestAccount(userId: String, initialBalance: Double = 1000.0): Result<AccountBalance> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = getServiceUrl(8082, "/api/v1/ledger/accounts/create-test-account")
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
                val url = getServiceUrl(8082, "/api/v1/ledger/balance/$userId")
                val responseJson = httpGet(url, userId)
                val obj = JSONObject(responseJson)
                AccountBalance(
                    availableBalance = obj.optDouble("availableBalance", 0.0),
                    reservedAmount = obj.optDouble("reservedAmount", 0.0),
                    totalBalance = obj.optDouble("totalBalance", 0.0)
                )
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
                createTestAccount(userId, 1000.0)

                val url = getServiceUrl(8081, "/api/v1/tokens/gettoken")
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
                    expiresAt = obj.optString("expiresAt"),
                    status = obj.optString("status", "ACTIVE")
                )
            }
        }

    suspend fun syncOfflineTransactions(
        userId: String,
        transactions: List<SyncTransactionPayload>
    ): Result<SyncResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = getServiceUrl(8083, "/api/v1/sync/$userId")
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

    private fun httpGet(urlStr: String, userId: String): String {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 7000
        conn.readTimeout = 7000
        val token = accessToken ?: throw IllegalStateException("Sign in before calling the Paron API")
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Accept", "application/json")

        val code = conn.responseCode
        if (code in 200..299) {
            return conn.inputStream.bufferedReader().use(BufferedReader::readText)
        } else {
            val error = conn.errorStream?.bufferedReader()?.use(BufferedReader::readText) ?: "HTTP $code"
            throw Exception("API GET error ($code): $error")
        }
    }

    private fun httpPost(urlStr: String, body: String, userId: String): String = httpPostRaw(urlStr, body, true)

    private fun httpPostRaw(urlStr: String, body: String, authenticated: Boolean): String {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 7000
        conn.readTimeout = 7000
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
            throw Exception("API POST error ($code): $error")
        }
    }
}
