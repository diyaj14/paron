package org.paron.merchant;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * BLE peripheral for the Paron two-phone demonstration.
 * It receives 0x50 + total-frame-count + frame-index + UTF-8 JSON frames,
 * then notifies the customer with PAYMENT_ACCEPTED or PAYMENT_REJECTED.
 */
public class MainActivity extends Activity {
    private static final UUID SERVICE_UUID = UUID.fromString("dabb1d77-39bd-4b1e-a6f2-1abc02d4de01");
    private static final UUID OFFER_UUID = UUID.fromString("dabb1d77-39bd-4b1e-a6f2-1abc02d4de02");
    private static final UUID RECEIPT_UUID = UUID.fromString("dabb1d77-39bd-4b1e-a6f2-1abc02d4de03");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final int PERMISSION_REQUEST = 41;

    private BluetoothAdapter adapter;
    private BluetoothGattServer gattServer;
    private BluetoothLeAdvertiser advertiser;
    private BluetoothGattCharacteristic receiptCharacteristic;
    private final Map<String, FrameBuffer> buffers = new HashMap<>();
    private TextView status;
    private TextView receiptLog;
    private TextView balanceLabel;
    private TextView disputeLabel;
    private EditText merchantIdInput;
    private EditText apiBaseInput;
    private EditText disputeTxnA;
    private EditText disputeTxnB;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        adapter = ((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 50, 40, 40);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView title = new TextView(this); title.setText("Paron Merchant Receiver"); title.setTextSize(24); root.addView(title);
        status = new TextView(this); status.setText("Receiver stopped"); status.setPadding(0, 24, 0, 16); root.addView(status);
        merchantIdInput = new EditText(this); merchantIdInput.setHint("Merchant ID"); merchantIdInput.setText("merchant-demo-001"); root.addView(merchantIdInput, new LinearLayout.LayoutParams(-1, -2));
        apiBaseInput = new EditText(this); apiBaseInput.setHint("API base URL (e.g. http://192.168.1.10:8080)"); root.addView(apiBaseInput, new LinearLayout.LayoutParams(-1, -2));
        Button refresh = new Button(this); refresh.setText("Register & check balance (online)"); refresh.setOnClickListener(v -> refreshMerchantBalance()); root.addView(refresh);
        balanceLabel = new TextView(this); balanceLabel.setText("Merchant balance unavailable while offline."); balanceLabel.setPadding(0, 8, 0, 16); root.addView(balanceLabel);
        disputeTxnA = new EditText(this); disputeTxnA.setHint("Disputed device txn A"); root.addView(disputeTxnA, new LinearLayout.LayoutParams(-1, -2));
        disputeTxnB = new EditText(this); disputeTxnB.setHint("Disputed device txn B"); root.addView(disputeTxnB, new LinearLayout.LayoutParams(-1, -2));
        Button adjudicate = new Button(this); adjudicate.setText("Resolve dispute (AI Judge)"); adjudicate.setOnClickListener(v -> resolveDispute()); root.addView(adjudicate);
        disputeLabel = new TextView(this); disputeLabel.setText("No dispute resolved yet."); disputeLabel.setPadding(0, 8, 0, 16); root.addView(disputeLabel);
        Button start = new Button(this); start.setText("Start Bluetooth receiver"); start.setOnClickListener(v -> requestPermissionsThenStart()); root.addView(start);
        Button stop = new Button(this); stop.setText("Stop receiver"); stop.setOnClickListener(v -> stopReceiver()); root.addView(stop);
        receiptLog = new TextView(this); receiptLog.setText("No payment received yet."); receiptLog.setPadding(0, 30, 0, 0); root.addView(receiptLog);
        setContentView(root);
    }

    private boolean hasPermissions() {
        return android.os.Build.VERSION.SDK_INT < 31 || (checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED);
    }
    private void requestPermissionsThenStart() {
        if (android.os.Build.VERSION.SDK_INT >= 31 && !hasPermissions()) { requestPermissions(new String[]{Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT}, PERMISSION_REQUEST); return; }
        startReceiver();
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) { super.onRequestPermissionsResult(requestCode, permissions, results); if (requestCode == PERMISSION_REQUEST && hasPermissions()) startReceiver(); else if (requestCode == PERMISSION_REQUEST) setStatus("Nearby devices permission is required."); }

    private void startReceiver() {
        if (adapter == null || !adapter.isEnabled()) { setStatus("Turn Bluetooth on, then start the receiver."); return; }
        if (!adapter.isMultipleAdvertisementSupported()) { setStatus("This phone cannot advertise as a BLE merchant."); return; }
        stopReceiver();
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        gattServer = manager.openGattServer(this, gattCallback);
        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        BluetoothGattCharacteristic offer = new BluetoothGattCharacteristic(OFFER_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE, BluetoothGattCharacteristic.PERMISSION_WRITE);
        receiptCharacteristic = new BluetoothGattCharacteristic(RECEIPT_UUID, BluetoothGattCharacteristic.PROPERTY_NOTIFY | BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ);
        receiptCharacteristic.addDescriptor(new BluetoothGattDescriptor(CCCD_UUID, BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE));
        service.addCharacteristic(offer); service.addCharacteristic(receiptCharacteristic); gattServer.addService(service);
        advertiser = adapter.getBluetoothLeAdvertiser();
        AdvertiseSettings settings = new AdvertiseSettings.Builder().setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY).setConnectable(true).setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM).build();
        AdvertiseData data = new AdvertiseData.Builder().addServiceUuid(new ParcelUuid(SERVICE_UUID)).setIncludeDeviceName(false).build();
        advertiser.startAdvertising(settings, data, advertiseCallback);
        setStatus("Advertising as merchant. You may now enable airplane mode and turn Bluetooth back on.");
    }
    private void stopReceiver() { if (advertiser != null) advertiser.stopAdvertising(advertiseCallback); advertiser = null; if (gattServer != null) gattServer.close(); gattServer = null; buffers.clear(); setStatus("Receiver stopped"); }
    @Override protected void onDestroy() { stopReceiver(); super.onDestroy(); }
    private void setStatus(String value) { status.setText(value); }
    private void setBalance(String value) { balanceLabel.setText(value); }

    /*
     * Registers this merchant (idempotent) and loads its collected balance
     * from the backend, so the merchant phone reflects real settled money.
     * Runs on a background thread — network on the UI thread is forbidden.
     */
    private void refreshMerchantBalance() {
        final String base = apiBaseInput.getText().toString().trim().replaceAll("/+$", "");
        final String merchantId = merchantIdInput.getText().toString().trim();
        if (base.isEmpty() || merchantId.isEmpty()) { setBalance("Enter API base URL and merchant ID first."); return; }
        new Thread(() -> {
            try {
                java.net.HttpURLConnection reg = (java.net.HttpURLConnection) new java.net.URL(base + "/api/v1/ledger/merchants/register").openConnection();
                reg.setRequestMethod("POST");
                reg.setRequestProperty("Content-Type", "application/json");
                reg.setDoOutput(true);
                reg.getOutputStream().write(("{\"merchantId\":\"" + merchantId + "\"}").getBytes(StandardCharsets.UTF_8));
                int registerStatus = reg.getResponseCode();
                reg.disconnect();

                java.net.HttpURLConnection bal = (java.net.HttpURLConnection) new java.net.URL(base + "/api/v1/ledger/merchants/" + merchantId).openConnection();
                bal.setRequestMethod("GET");
                int balanceStatus = bal.getResponseCode();
                if (balanceStatus != 200) { bal.disconnect(); runOnUiThread(() -> setBalance("Balance lookup failed (HTTP " + balanceStatus + ")")); return; }
                java.io.InputStream stream = bal.getInputStream();
                java.util.Scanner scanner = new java.util.Scanner(stream, "UTF-8").useDelimiter("\\A");
                String body = scanner.hasNext() ? scanner.next() : "";
                scanner.close();
                bal.disconnect();
                JSONObject json = new JSONObject(body);
                final double collected = json.optDouble("collectedBalance", 0);
                final String name = json.optString("merchantName", merchantId);
                final int regCode = registerStatus;
                runOnUiThread(() -> setBalance(name + (regCode == 200 ? " (registered)" : " (registered now)") + " · collected ₹" + String.format("%.2f", collected)));
            } catch (Exception e) {
                runOnUiThread(() -> setBalance("Backend unreachable: " + e.getMessage()));
            }
        }).start();
    }

    /*
     * Asks the backend AI Judge to arbitrate a dispute between two offline
     * receipts (POST /api/v1/sync/adjudicate). The verdict shows who won and
     * why — all on a background thread, no network on the UI thread.
     */
    private void resolveDispute() {
        final String base = apiBaseInput.getText().toString().trim().replaceAll("/+$", "");
        final String txnA = disputeTxnA.getText().toString().trim();
        final String txnB = disputeTxnB.getText().toString().trim();
        if (base.isEmpty() || txnA.isEmpty() || txnB.isEmpty()) { disputeLabel.setText("Enter API base URL and both device txn ids."); return; }
        new Thread(() -> {
            try {
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(base + "/api/v1/sync/adjudicate").openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                String body = "{\"deviceTransactionIds\":[\"" + txnA + "\",\"" + txnB + "\"]}";
                conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
                int status = conn.getResponseCode();
                if (status != 200) { conn.disconnect(); final int code = status; runOnUiThread(() -> disputeLabel.setText("Adjudication failed (HTTP " + code + ")")); return; }
                java.io.InputStream stream = conn.getInputStream();
                java.util.Scanner scanner = new java.util.Scanner(stream, "UTF-8").useDelimiter("\\A");
                String response = scanner.hasNext() ? scanner.next() : "";
                scanner.close();
                conn.disconnect();
                JSONObject verdict = new JSONObject(response);
                final String ruling = verdict.optString("ruling", "UNKNOWN");
                final String winner = verdict.optString("winnerDeviceTransactionId", "");
                final String summary = verdict.optString("summary", "");
                final double confidence = verdict.optDouble("confidence", 0);
                runOnUiThread(() -> disputeLabel.setText("AI Judge: " + ruling + " (conf " + String.format("%.2f", confidence) + ")\nWinner: " + (winner.isEmpty() ? "none" : winner) + "\n" + summary));
            } catch (Exception e) {
                runOnUiThread(() -> disputeLabel.setText("Backend unreachable: " + e.getMessage()));
            }
        }).start();
    }

    private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() { @Override public void onStartFailure(int errorCode) { setStatus("Could not advertise (error " + errorCode + ")."); } };

    private final BluetoothGattServerCallback gattCallback = new BluetoothGattServerCallback() {
        @Override public void onConnectionStateChange(BluetoothDevice device, int statusCode, int newState) { runOnUiThread(() -> setStatus(newState == BluetoothGatt.STATE_CONNECTED ? "Customer connected: " + device.getName() : "Waiting for customer…")); }
        @Override public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) { if (responseNeeded && gattServer != null) gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null); }
        @Override public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            if (!OFFER_UUID.equals(characteristic.getUuid())) return;
            try { receiveFrame(device, value); if (responseNeeded && gattServer != null) gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null); }
            catch (Exception exception) { if (responseNeeded && gattServer != null) gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null); sendReceipt(device, rejected("", "", "Invalid payment message.")); }
        }
    };

    private void receiveFrame(BluetoothDevice device, byte[] frame) throws Exception {
        if (frame == null || frame.length < 4 || frame[0] != 0x50) throw new IllegalArgumentException("Invalid frame");
        int total = Byte.toUnsignedInt(frame[1]); int index = Byte.toUnsignedInt(frame[2]);
        if (total == 0 || index >= total) throw new IllegalArgumentException("Invalid sequence");
        FrameBuffer buffer = buffers.computeIfAbsent(device.getAddress(), key -> new FrameBuffer(total));
        if (buffer.total != total) throw new IllegalArgumentException("Sequence changed");
        buffer.frames[index] = new String(frame, 3, frame.length - 3, StandardCharsets.UTF_8);
        if (!buffer.complete()) return;
        buffers.remove(device.getAddress());
        handleOffer(device, new JSONObject(buffer.join()));
    }
    private void handleOffer(BluetoothDevice device, JSONObject offer) throws Exception {
        JSONObject transaction = offer.getJSONObject("transaction");
        String id = transaction.optString("deviceTransactionId"); String hash = offer.optString("payloadHash");
        if (!"paron-ble-v1".equals(offer.optString("protocolVersion")) || !"PAYMENT_OFFER".equals(offer.optString("type")) || id.isEmpty() || hash.isEmpty()) { sendReceipt(device, rejected(id, hash, "Unsupported payment offer.")); return; }
        String configuredMerchant = merchantIdInput.getText().toString().trim();
        if (!configuredMerchant.equals(transaction.optString("merchantId"))) { sendReceipt(device, rejected(id, hash, "Merchant ID does not match this receiver.")); return; }
        double amount = transaction.optDouble("amount", 0); if (amount <= 0 || transaction.optString("offlineToken").isEmpty()) { sendReceipt(device, rejected(id, hash, "Payment details are incomplete.")); return; }
        JSONObject receipt = new JSONObject(); receipt.put("type", "PAYMENT_ACCEPTED"); receipt.put("deviceTransactionId", id); receipt.put("payloadHash", hash); receipt.put("merchantReceiptId", "mrc-" + UUID.randomUUID()); receipt.put("receivedAt", Instant.now().toString());
        sendReceipt(device, receipt);
        runOnUiThread(() -> receiptLog.setText("Accepted offline payment\n₹" + String.format("%.2f", amount) + "\nTransaction " + id.substring(0, Math.min(8, id.length())) + "\nPending server settlement"));
    }
    private JSONObject rejected(String id, String hash, String reason) { JSONObject receipt = new JSONObject(); try { receipt.put("type", "PAYMENT_REJECTED"); receipt.put("deviceTransactionId", id); receipt.put("payloadHash", hash); receipt.put("reason", reason); } catch (Exception ignored) {} return receipt; }
    private void sendReceipt(BluetoothDevice device, JSONObject receipt) { if (gattServer == null || receiptCharacteristic == null) return; receiptCharacteristic.setValue(receipt.toString().getBytes(StandardCharsets.UTF_8)); gattServer.notifyCharacteristicChanged(device, receiptCharacteristic, false); }
    private static class FrameBuffer { final int total; final String[] frames; FrameBuffer(int total) { this.total = total; this.frames = new String[total]; } boolean complete() { for (String frame : frames) if (frame == null) return false; return true; } String join() { StringBuilder joined = new StringBuilder(); for (String frame : frames) joined.append(frame); return joined.toString(); } }
}
