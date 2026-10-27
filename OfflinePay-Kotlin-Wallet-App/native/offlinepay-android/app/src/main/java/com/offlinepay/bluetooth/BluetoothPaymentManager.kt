package com.offlinepay.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import com.offlinepay.crypto.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID

data class PaymentOfferEnvelope(
    val deviceTransactionId: String,
    val offlineToken: String,
    val amount: Double,
    val merchantId: String,
    val transactedAt: String,
    val deviceId: String,
    val signature: String,
    val publicKey: String
)

data class PaymentReceipt(
    val deviceTransactionId: String,
    val merchantReceiptId: String,
    val receivedAt: String
)

class BluetoothPaymentManager(private val context: Context) {

    companion object {
        // Standard SPP UUID for Bluetooth RFCOMM P2P
        val PARON_PAYMENT_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        const val SERVICE_NAME = "ParonOfflinePay"
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    fun isBluetoothAvailable(): Boolean = bluetoothAdapter != null
    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        return try {
            bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private var currentServerSocket: BluetoothServerSocket? = null
    private var isListening = false

    fun stopReceiver() {
        isListening = false
        try {
            currentServerSocket?.close()
        } catch (_: Exception) {}
        currentServerSocket = null
    }

    /**
     * Receiver (Phone B): Listens for an incoming offline payment from Phone A.
     */
    @SuppressLint("MissingPermission")
    suspend fun startReceiver(
        onListening: () -> Unit,
        onPaymentReceived: (PaymentOfferEnvelope) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            withContext(Dispatchers.Main) { onError("Bluetooth is not enabled.") }
            return@withContext
        }

        try {
            stopReceiver()
            val serverSocket = adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, PARON_PAYMENT_UUID)
            currentServerSocket = serverSocket
            isListening = true
            withContext(Dispatchers.Main) { onListening() }

            val socket: BluetoothSocket = serverSocket.accept() // Blocks until Phone A connects
            val reader = BufferedReader(InputStreamReader(socket.inputStream))
            val line = reader.readLine()

            if (line.isNullOrBlank()) {
                withContext(Dispatchers.Main) { onError("Empty data received.") }
                socket.close()
                return@withContext
            }

            val envelopeObj = JSONObject(line)
            val type = envelopeObj.optString("type")
            if (type != "PAYMENT_OFFER") {
                withContext(Dispatchers.Main) { onError("Unexpected message: $type") }
                socket.close()
                return@withContext
            }

            val txnObj = envelopeObj.getJSONObject("transaction")
            val offer = PaymentOfferEnvelope(
                deviceTransactionId = txnObj.getString("deviceTransactionId"),
                offlineToken = txnObj.getString("offlineToken"),
                amount = txnObj.getDouble("amount"),
                merchantId = txnObj.optString("merchantId", "Merchant"),
                transactedAt = txnObj.getString("transactedAt"),
                deviceId = txnObj.optString("deviceId", "Device"),
                signature = txnObj.getString("signature"),
                publicKey = txnObj.getString("publicKey")
            )

            // Verify signature offline before accepting
            val canonical = CryptoManager.canonicalString(
                deviceTransactionId = offer.deviceTransactionId,
                offlineToken = offer.offlineToken,
                merchantId = offer.merchantId,
                amount = offer.amount.toString(),
                transactedAt = offer.transactedAt,
                deviceId = offer.deviceId
            )
            val isValid = CryptoManager.verify(canonical, offer.signature, offer.publicKey)

            if (!isValid) {
                withContext(Dispatchers.Main) { onError("Signature verification failed! Payment rejected.") }
                socket.close()
                return@withContext
            }

            // Send back acceptance receipt
            val receiptObj = JSONObject().apply {
                put("type", "PAYMENT_ACCEPTED")
                put("deviceTransactionId", offer.deviceTransactionId)
                put("merchantReceiptId", "rcpt-" + UUID.randomUUID().toString().take(8))
                put("receivedAt", java.time.LocalDateTime.now().toString())
            }

            val writer = OutputStreamWriter(socket.outputStream)
            writer.write(receiptObj.toString() + "\n")
            writer.flush()
            socket.close()

            withContext(Dispatchers.Main) { onPaymentReceived(offer) }

        } catch (e: Exception) {
            if (isListening) {
                withContext(Dispatchers.Main) { onError(e.message ?: "Bluetooth error") }
            }
        } finally {
            stopReceiver()
        }
    }

    /**
     * Payer (Phone A): Connects to Phone B and sends the signed payment offer.
     */
    @SuppressLint("MissingPermission")
    suspend fun sendPayment(
        targetDevice: BluetoothDevice,
        offer: PaymentOfferEnvelope
    ): Result<PaymentReceipt> = withContext(Dispatchers.IO) {
        runCatching {
            val socket = targetDevice.createRfcommSocketToServiceRecord(PARON_PAYMENT_UUID)
            socket.connect() // Connects to Phone B

            val envelope = JSONObject().apply {
                put("protocolVersion", "paron-ble-v1")
                put("type", "PAYMENT_OFFER")
                put("transaction", JSONObject().apply {
                    put("deviceTransactionId", offer.deviceTransactionId)
                    put("offlineToken", offer.offlineToken)
                    put("amount", offer.amount)
                    put("merchantId", offer.merchantId)
                    put("transactedAt", offer.transactedAt)
                    put("deviceId", offer.deviceId)
                    put("signature", offer.signature)
                    put("publicKey", offer.publicKey)
                })
            }

            val writer = OutputStreamWriter(socket.outputStream)
            writer.write(envelope.toString() + "\n")
            writer.flush()

            // Wait for receipt
            val reader = BufferedReader(InputStreamReader(socket.inputStream))
            val replyLine = reader.readLine()
            socket.close()

            if (replyLine.isNullOrBlank()) {
                throw Exception("No receipt returned by merchant phone.")
            }

            val receiptObj = JSONObject(replyLine)
            if (receiptObj.optString("type") != "PAYMENT_ACCEPTED") {
                throw Exception("Merchant phone did not accept payment.")
            }

            PaymentReceipt(
                deviceTransactionId = receiptObj.getString("deviceTransactionId"),
                merchantReceiptId = receiptObj.optString("merchantReceiptId", "rcpt-ok"),
                receivedAt = receiptObj.optString("receivedAt", java.time.LocalDateTime.now().toString())
            )
        }
    }
}
