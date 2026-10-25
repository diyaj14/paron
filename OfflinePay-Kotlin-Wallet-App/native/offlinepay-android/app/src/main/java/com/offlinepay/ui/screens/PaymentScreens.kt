package com.offlinepay.ui.screens

import android.Manifest
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.offlinepay.BuildConfig
import com.offlinepay.data.AppUiState
import com.offlinepay.data.PaymentMethod
import com.offlinepay.data.WalletTransaction
import com.offlinepay.ui.components.BackAction
import com.offlinepay.ui.components.DemoBanner
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
                "This app is in development mode. No UPI payment will be initiated.",
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
        Text("Point your camera at the merchant's QR code.", color = Color(0xFFC9D9ED), fontSize = 14.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
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
                ScreenTitle("Payment Details", "Review the details and choose your payment method")
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
                "Scan the merchant's QR code to pay.",
                PaymentMethod.QR,
                state.paymentMethod,
                onMethodChange
            )
            Spacer(Modifier.height(9.dp))
            MethodRow(
                "Enter Mobile Number",
                "Enter UPI ID or mobile number to pay.",
                PaymentMethod.MOBILE,
                state.paymentMethod,
                onMethodChange
            )
            Text(
                if (state.internetAvailable) {
                    "UPI processing is not configured in this development build."
                } else {
                    "No internet connection detected. Use your offline balance instead."
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
    onPay: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(WealthBeige).verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(242.dp).background(Color(0xFFFFFEFA))) {
            WaveArt(Modifier.fillMaxSize(), dark = false)
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                BackAction(onBack)
                Text("Offline Payment", color = TrustIndigo, fontWeight = FontWeight.Bold, fontSize = 27.sp, modifier = Modifier.padding(top = 10.dp))
                Text(
                    "No internet connection detected.\nYou can still make payments using your offline balance.",
                    color = MutedNavy,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(top = 7.dp)
                )
                Icon(Icons.Rounded.WifiOff, null, tint = YieldBlue.copy(alpha = .55f), modifier = Modifier.align(Alignment.End).padding(end = 24.dp, top = 12.dp).size(56.dp))
            }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            DemoBanner()
            SectionCard(Modifier.padding(top = 13.dp)) {
                MerchantRow(state.selectedMerchant, "Merchant ID: ${state.merchantId}")
                Box(Modifier.fillMaxWidth().padding(vertical = 12.dp).height(1.dp).background(LineBlue))
                Text("Amount to Pay", color = MutedNavy, fontSize = 13.sp)
                Text("₹${state.paymentAmount}", color = TrustIndigo, fontSize = 31.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
            }
            SectionCard(Modifier.padding(top = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(49.dp).clip(CircleShape).background(SoftBlue), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.AccountBalanceWallet, null, tint = YieldBlue)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("Offline Balance", color = TrustIndigo, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("₹${state.offlineBalance} remaining", color = MutedNavy, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp))
                    }
                    Text("›", color = TrustIndigo, fontSize = 23.sp)
                }
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { (state.offlineBalance / 500f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(start = 61.dp, top = 12.dp).height(8.dp).clip(CircleShape),
                    color = YieldBlue,
                    trackColor = LineBlue
                )
            }
            OfflineNotice(
                "This transaction will be saved locally and verified once you reconnect to the internet.",
                Modifier.padding(top = 12.dp)
            )
            Text(
                "Development-only local record. Token validation and reconciliation are not configured.",
                color = MutedNavy,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 10.dp)
            )
            PrimaryAction(
                "Pay Using Offline Token",
                onClick = onPay,
                modifier = Modifier.padding(top = 14.dp, bottom = 20.dp),
                enabled = BuildConfig.DEMO_MODE,
                trailingArrow = true
            )
        }
    }
}

@Composable
fun PaymentSavedScreen(
    transaction: WalletTransaction,
    onDone: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().background(WealthBeige).verticalScroll(rememberScrollState()).padding(horizontal = 17.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(244.dp), contentAlignment = Alignment.Center) {
            WaveArt(Modifier.fillMaxSize(), dark = false)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(94.dp).clip(CircleShape).background(YieldBlue), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(57.dp))
                }
                Text("Payment Successful", color = TrustIndigo, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp))
                Text(
                    "Payment of ₹${transaction.amountRupees} saved using your offline balance.",
                    color = MutedNavy,
                    fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(top = 5.dp)
                )
            }
        }
        DemoBanner()
        SectionCard(Modifier.padding(top = 14.dp)) {
            MerchantRow(transaction.merchant, "Merchant ID: local demo")
            Box(Modifier.fillMaxWidth().padding(vertical = 10.dp).height(1.dp).background(LineBlue))
            InfoRow("Amount Paid", "₹${transaction.amountRupees}", emphasize = true)
            InfoRow("Payment Method", transaction.method)
            InfoRow("Date & Time", transaction.dateLabel)
            InfoRow("Transaction ID", transaction.id)
            StatusBadge("Pending verification · demo only", pending = true)
        }
        OfflineNotice(
            "This local development record is not a real payment. It will not be settled or reconciled.",
            Modifier.padding(top = 12.dp)
        )
        PrimaryAction("Done", onDone, modifier = Modifier.padding(top = 15.dp, bottom = 22.dp))
    }
}
