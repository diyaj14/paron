package com.offlinepay.ui.screens

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.CameraController
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.offlinepay.data.AppUiState
import com.offlinepay.data.PaymentMethod
import com.offlinepay.data.WalletTransaction
import com.offlinepay.ui.components.BackAction
import com.offlinepay.ui.components.InfoRow
import com.offlinepay.ui.components.MerchantRow
import com.offlinepay.ui.components.OfflineNotice
import com.offlinepay.ui.components.PrimaryAction
import com.offlinepay.ui.components.ScreenTitle
import com.offlinepay.ui.components.SectionCard
import com.offlinepay.ui.components.StatusBadge
import com.offlinepay.ui.components.WaveArt
import com.offlinepay.ui.theme.GlacierBlue
import com.offlinepay.ui.theme.LineBlue
import com.offlinepay.ui.theme.MutedNavy
import com.offlinepay.ui.theme.SoftBlue
import com.offlinepay.ui.theme.TrustIndigo
import com.offlinepay.ui.theme.WealthBeige
import com.offlinepay.ui.theme.YieldBlue
import java.util.concurrent.Executors

@Composable
fun MobilePayScreen(onBack: () -> Unit, onContinue: (String) -> Unit) {
    var number by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().background(WealthBeige).padding(20.dp)
    ) {
        BackAction(onBack)
        Spacer(Modifier.height(24.dp))
        ScreenTitle("Enter Mobile Number", "Enter a UPI ID or mobile number to continue.")
        SectionCard(Modifier.padding(top = 24.dp)) {
            OutlinedTextField(
                value = number,
                onValueChange = { number = it.filter(Char::isDigit).take(10) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Smartphone, null, tint = YieldBlue) },
                placeholder = { Text("10-digit mobile number") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = YieldBlue,
                    unfocusedBorderColor = LineBlue
                )
            )
            Text(
                "Enter payee's mobile number or UPI ID for direct wallet transfer.",
                color = MutedNavy,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
        Spacer(Modifier.weight(1f))
        PrimaryAction(
            "Continue",
            onClick = { onContinue(number) },
            enabled = number.length == 10,
            trailingArrow = true
        )
    }
}

@Composable
fun QrScannerScreen(onBack: () -> Unit, onDetected: (String) -> Unit, onMobile: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> permissionGranted = granted }
    val controller = remember { LifecycleCameraController(context) }
    val barcodeScanner = remember { BarcodeScanning.getClient() }
    val executor = remember { Executors.newSingleThreadExecutor() }
    var scanError by remember { mutableStateOf(false) }

    LaunchedEffect(permissionGranted) {
        if (permissionGranted) {
            try {
                controller.cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                controller.bindToLifecycle(lifecycleOwner)
                controller.setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
                controller.setImageAnalysisAnalyzer(
                    executor,
                    MlKitAnalyzer(
                        listOf(barcodeScanner),
                        ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED,
                        executor
                    ) { result ->
                        val value = result.getValue(barcodeScanner)
                            ?.firstOrNull { it.rawValue != null }
                            ?.rawValue
                        if (!value.isNullOrBlank()) {
                            ContextCompat.getMainExecutor(context).execute { onDetected(value) }
                        }
                    }
                )
            } catch (_: Exception) {
                scanError = true
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            executor.shutdown()
            barcodeScanner.close()
        }
    }

    Column(Modifier.fillMaxSize().background(TrustIndigo)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BackAction(onBack, dark = true)
            Text("Scan QR Code", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
        Text("Point your camera at the merchant or payer QR code.", color = Color(0xFFC9D9ED), fontSize = 14.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
        Box(
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 20.dp, vertical = 14.dp)
                .clip(RoundedCornerShape(22.dp)).background(Color(0xFF102746)),
            contentAlignment = Alignment.Center
        ) {
            if (permissionGranted) {
                AndroidView(
                    factory = { viewContext ->
                        PreviewView(viewContext).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            this.controller = controller
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    Modifier.size(230.dp)
                        .border(3.dp, GlacierBlue, RoundedCornerShape(22.dp))
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.QrCodeScanner, null, tint = GlacierBlue, modifier = Modifier.size(56.dp))
                    Text(
                        if (scanError) "Camera unavailable. Try entering a mobile number." else "Camera permission is needed to scan.",
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                    PrimaryAction("Allow Camera", { permissionLauncher.launch(Manifest.permission.CAMERA) }, Modifier.padding(horizontal = 24.dp))
                }
            }
        }
        PrimaryAction(
            "Enter Mobile Number Instead",
            onClick = onMobile,
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 22.dp)
        )
    }
}

@Composable
fun PaymentDetailsScreen(
    state: AppUiState,
    onBack: () -> Unit,
    onAmountChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onMethodChange: (PaymentMethod) -> Unit,
    onConfirm: () -> Unit,
    onOffline: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(WealthBeige).verticalScroll(rememberScrollState())) {
        Box(
            Modifier.fillMaxWidth().height(168.dp)
                .background(Color(0xFFF9FCFE))
        ) {
            WaveArt(Modifier.fillMaxSize(), dark = false)
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                BackAction(onBack)
                Spacer(Modifier.height(6.dp))
                ScreenTitle("Payment Details", "Review details and choose your payment method")
            }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            SectionCard(Modifier.padding(top = 8.dp)) {
                MerchantRow(state.selectedMerchant, "Merchant ID: ${state.merchantId}")
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(LineBlue))
                Text("Amount to Pay", color = MutedNavy, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
                OutlinedTextField(
                    value = state.paymentAmount,
                    onValueChange = onAmountChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    prefix = { Text("₹", fontWeight = FontWeight.Bold, fontSize = 26.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = YieldBlue,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = TrustIndigo,
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                OutlinedTextField(
                    value = state.paymentNote,
                    onValueChange = onNoteChange,
                    modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                    singleLine = true,
                    placeholder = { Text("Add a note (optional)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = YieldBlue,
                        unfocusedBorderColor = LineBlue
                    )
                )
            }
            Text("Select Payment Method", color = TrustIndigo, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp, bottom = 8.dp))
            MethodRow(
                "Scan QR Code",
                "Scan merchant QR code to pay.",
                PaymentMethod.QR,
                state.paymentMethod,
                onMethodChange
            )
            Spacer(Modifier.height(9.dp))
            MethodRow(
                "Enter Mobile Number",
                "Enter UPI ID or phone number to pay.",
                PaymentMethod.MOBILE,
                state.paymentMethod,
                onMethodChange
            )
            Text(
                if (state.internetAvailable) {
                    "Online balance available: ₹${state.onlineBalance}. Tap confirm to complete transfer."
                } else {
                    "No internet connection detected. Switch to offline balance."
                },
                color = MutedNavy,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 12.dp, bottom = 5.dp)
            )
            PrimaryAction(
                "Confirm Payment",
                onConfirm,
                modifier = Modifier.padding(top = 12.dp),
                enabled = state.internetAvailable
            )
            Text(
                "No internet? Use offline balance",
                color = YieldBlue,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SoftBlue)
                    .clickable(onClick = onOffline)
                    .padding(14.dp)
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun MethodRow(
    title: String,
    subtitle: String,
    method: PaymentMethod,
    selected: PaymentMethod,
    onSelect: (PaymentMethod) -> Unit
) {
    val isSelected = selected == method
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(17.dp))
            .background(if (isSelected) Color(0xFFEAF7FF) else Color(0xFFFFFEFA))
            .border(if (isSelected) 1.5.dp else 1.dp, if (isSelected) YieldBlue else LineBlue, RoundedCornerShape(17.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(51.dp).clip(RoundedCornerShape(13.dp)).background(SoftBlue), contentAlignment = Alignment.Center) {
            Icon(if (method == PaymentMethod.QR) Icons.Rounded.QrCodeScanner else Icons.Rounded.Smartphone, null, tint = YieldBlue)
        }
        Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
            Text(title, color = TrustIndigo, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(subtitle, color = MutedNavy, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 3.dp))
        }
        RadioButton(selected = isSelected, onClick = { onSelect(method) })
    }
}

@Composable
fun OfflinePaymentScreen(
    state: AppUiState,
    onBack: () -> Unit,
    onAmountChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    pairedDevices: List<BluetoothDevice>,
    onPayQr: () -> Unit,
    onPayBluetooth: (BluetoothDevice) -> Unit
) {
    val context = LocalContext.current
    var selectedChannel by remember { mutableStateOf("BLUETOOTH") } // "BLUETOOTH" or "QR"
    var selectedDevice by remember { mutableStateOf<BluetoothDevice?>(pairedDevices.firstOrNull()) }

    LaunchedEffect(pairedDevices) {
        if (selectedDevice == null && pairedDevices.isNotEmpty()) {
            selectedDevice = pairedDevices.first()
        }
    }

    val amountValue = state.paymentAmount.toDoubleOrNull() ?: 0.0
    val isAmountValid = amountValue > 0 && amountValue <= state.offlineBalance

    Column(Modifier.fillMaxSize().background(WealthBeige).verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(210.dp).background(Color(0xFFFFFEFA))) {
            WaveArt(Modifier.fillMaxSize(), dark = false)
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                BackAction(onBack)
                Text("Offline P2P Payment", color = TrustIndigo, fontWeight = FontWeight.Bold, fontSize = 26.sp, modifier = Modifier.padding(top = 6.dp))
                Text(
                    "Pay peer-to-peer with 100% air-gap.\nSelect Bluetooth device pairing or visual QR code.",
                    color = MutedNavy,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Icon(Icons.Rounded.WifiOff, null, tint = YieldBlue.copy(alpha = .45f), modifier = Modifier.align(Alignment.End).padding(end = 20.dp).size(48.dp))
            }
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            // Amount & Note Card
            SectionCard(Modifier.padding(top = 10.dp)) {
                MerchantRow(state.selectedMerchant, "Merchant ID: ${state.merchantId}")
                Box(Modifier.fillMaxWidth().padding(vertical = 10.dp).height(1.dp).background(LineBlue))
                Text("Payment Amount", color = MutedNavy, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = state.paymentAmount,
                    onValueChange = onAmountChange,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    singleLine = true,
                    prefix = { Text("₹", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = TrustIndigo) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = YieldBlue,
                        unfocusedBorderColor = LineBlue
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = TrustIndigo,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                )

                OutlinedTextField(
                    value = state.paymentNote,
                    onValueChange = onNoteChange,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    singleLine = true,
                    placeholder = { Text("Add note (e.g., Canteen Lunch)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = YieldBlue,
                        unfocusedBorderColor = LineBlue
                    )
                )
            }

            // Offline Balance Status
            SectionCard(Modifier.padding(top = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(SoftBlue), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.AccountBalanceWallet, null, tint = YieldBlue)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("Active Offline Token Balance", color = TrustIndigo, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("₹${state.offlineBalance} available to spend", color = MutedNavy, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { (state.offlineBalance / 1000f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(start = 56.dp, top = 10.dp).height(6.dp).clip(CircleShape),
                    color = YieldBlue,
                    trackColor = LineBlue
                )
                if (state.activeOfflineToken == null) {
                    Text(
                        "⚠️ No active token loaded. Go to Wallet to reserve offline balance from backend.",
                        color = Color(0xFFC53030),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            // Transfer Mode Selector
            Text("Transfer Channel", color = TrustIndigo, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChannelChoiceTab(
                    title = "Bluetooth P2P",
                    subtitle = "Wireless RFCOMM",
                    icon = Icons.Rounded.Bluetooth,
                    selected = selectedChannel == "BLUETOOTH",
                    onClick = { selectedChannel = "BLUETOOTH" },
                    modifier = Modifier.weight(1f)
                )
                ChannelChoiceTab(
                    title = "Visual QR Code",
                    subtitle = "Camera Scan",
                    icon = Icons.Rounded.QrCode,
                    selected = selectedChannel == "QR",
                    onClick = { selectedChannel = "QR" },
                    modifier = Modifier.weight(1f)
                )
            }

            // Bluetooth Mode: Device Selection & Pairing
            if (selectedChannel == "BLUETOOTH") {
                SectionCard(Modifier.padding(top = 14.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Select Nearby / Paired Device", color = TrustIndigo, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            "Pair in Settings ⚙️",
                            color = YieldBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                } catch (_: Exception) {}
                            }
                        )
                    }

                    Text(
                        "Select payee phone to transmit ECDSA-signed claim via Bluetooth socket.",
                        color = MutedNavy,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 3.dp, bottom = 10.dp)
                    )

                    if (pairedDevices.isEmpty()) {
                        Box(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF2F6FA))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Rounded.BluetoothSearching, null, tint = MutedNavy, modifier = Modifier.size(34.dp))
                                Text(
                                    "No paired Bluetooth devices detected.",
                                    color = TrustIndigo,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                                Text(
                                    "Tap 'Pair in Settings' above to pair the payee phone.",
                                    color = MutedNavy,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            pairedDevices.forEach { device ->
                                val isChosen = selectedDevice?.address == device.address
                                Row(
                                    Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isChosen) Color(0xFFEAF5FF) else Color(0xFFF8FAFC))
                                        .border(if (isChosen) 1.5.dp else 1.dp, if (isChosen) YieldBlue else LineBlue, RoundedCornerShape(12.dp))
                                        .clickable { selectedDevice = device }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        Modifier.size(38.dp).clip(CircleShape).background(if (isChosen) YieldBlue else SoftBlue),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Rounded.Smartphone,
                                            null,
                                            tint = if (isChosen) Color.White else YieldBlue,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                        Text(
                                            device.name ?: "Nearby Android Device",
                                            color = TrustIndigo,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Text(device.address, color = MutedNavy, fontSize = 11.sp)
                                    }
                                    RadioButton(selected = isChosen, onClick = { selectedDevice = device })
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    PrimaryAction(
                        text = if (selectedDevice != null) "Pay ₹${state.paymentAmount} via Bluetooth to ${selectedDevice?.name ?: "Phone"}" else "Select Device to Pay",
                        onClick = {
                            selectedDevice?.let(onPayBluetooth)
                        },
                        enabled = selectedDevice != null && isAmountValid && state.activeOfflineToken != null,
                        trailingArrow = true
                    )
                }
            } else {
                // QR Code Mode
                SectionCard(Modifier.padding(top = 14.dp)) {
                    Text("Visual Air-Gapped Handover", color = TrustIndigo, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        "Generates an offline QR containing ECDSA-signed claim. Payee phone scans directly with camera.",
                        color = MutedNavy,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 3.dp, bottom = 12.dp)
                    )

                    PrimaryAction(
                        text = "Generate ₹${state.paymentAmount} Offline QR",
                        onClick = onPayQr,
                        enabled = isAmountValid && state.activeOfflineToken != null,
                        trailingArrow = true
                    )
                }
            }

            OfflineNotice(
                "Each transaction is cryptographically signed using this phone's hardware ECDSA key. When internet returns, Sync Service settles funds on the central bank ledger.",
                Modifier.padding(top = 14.dp, bottom = 24.dp)
            )
        }
    }
}

@Composable
private fun ChannelChoiceTab(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Color(0xFFEAF5FF) else Color(0xFFFFFEFA))
            .border(if (selected) 2.dp else 1.dp, if (selected) YieldBlue else LineBlue, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(if (selected) YieldBlue else SoftBlue),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = if (selected) Color.White else YieldBlue, modifier = Modifier.size(22.dp))
        }
        Text(title, color = TrustIndigo, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        Text(subtitle, color = MutedNavy, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
fun PaymentSavedScreen(
    state: AppUiState,
    transaction: WalletTransaction,
    onSendBluetooth: (BluetoothDevice) -> Unit,
    onSync: () -> Unit,
    pairedDevices: List<BluetoothDevice>,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    var showBtDialog by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(WealthBeige).verticalScroll(rememberScrollState()).padding(horizontal = 17.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            WaveArt(Modifier.fillMaxSize(), dark = false)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(70.dp).clip(CircleShape).background(YieldBlue), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(44.dp))
                }
                Text("Offline Payment Signed", color = TrustIndigo, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                Text(
                    "₹${transaction.amountRupees} signed with device ECDSA key.",
                    color = MutedNavy,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        // P2P Handover Options
        SectionCard(Modifier.padding(top = 8.dp)) {
            Text("P2P Transmission", color = TrustIndigo, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("Let the receiver phone scan this QR or transmit via Bluetooth.", color = MutedNavy, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))

            if (state.lastQrPayload != null) {
                val bitmap = remember(state.lastQrPayload) {
                    com.offlinepay.util.QrCodeGenerator.generateQrBitmap(state.lastQrPayload, 400)
                }
                if (bitmap != null) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Offline Payment QR",
                            modifier = Modifier.size(200.dp).clip(RoundedCornerShape(12.dp))
                        )
                    }
                    Text("Show this QR to payee camera to verify offline.", textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MutedNavy, fontSize = 11.sp, modifier = Modifier.fillMaxWidth())
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryAction(
                    "Send via Bluetooth",
                    onClick = { showBtDialog = true },
                    modifier = Modifier.weight(1f)
                )
                if (state.internetAvailable) {
                    PrimaryAction(
                        "Sync to Backend",
                        onClick = onSync,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        SectionCard(Modifier.padding(top = 12.dp)) {
            MerchantRow(transaction.merchant, "Txn: ${transaction.id}")
            Box(Modifier.fillMaxWidth().padding(vertical = 10.dp).height(1.dp).background(LineBlue))
            InfoRow("Amount Paid", "₹${transaction.amountRupees}", emphasize = true)
            InfoRow("Payment Method", transaction.method)
            InfoRow("Date & Time", transaction.dateLabel)
            InfoRow("Transaction ID", transaction.id)
            StatusBadge("Signed Offline • Queued for Settlement", pending = true)
        }

        OfflineNotice(
            "This payment is cryptographically signed. Once either device connects to the internet, Sync Service settles it atomically on the ledger.",
            Modifier.padding(top = 12.dp)
        )
        PrimaryAction("Done", onDone, modifier = Modifier.padding(top = 14.dp, bottom = 22.dp))

        // Bluetooth devices dialog
        if (showBtDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showBtDialog = false },
                title = { Text("Select Payee Phone (Bluetooth)") },
                text = {
                    Column {
                        if (pairedDevices.isEmpty()) {
                            Text("No paired Bluetooth devices found. Pair the phones in Android Settings first.")
                        } else {
                            pairedDevices.forEach { dev ->
                                Text(
                                    text = "📱 ${dev.name ?: "Unknown"} (${dev.address})",
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        showBtDialog = false
                                        onSendBluetooth(dev)
                                    }.padding(vertical = 10.dp),
                                    fontWeight = FontWeight.Medium,
                                    color = TrustIndigo
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    OutlinedButton(onClick = {
                        showBtDialog = false
                        try {
                            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                        } catch (_: Exception) {}
                    }) {
                        Text("Bluetooth Settings")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showBtDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
