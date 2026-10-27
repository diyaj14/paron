package com.offlinepay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offlinepay.data.AppUiState
import com.offlinepay.data.TransactionStatus
import com.offlinepay.data.WalletTransaction
import com.offlinepay.ui.components.DarkHero
import com.offlinepay.ui.components.MerchantIcon
import com.offlinepay.ui.components.MerchantRow
import com.offlinepay.ui.components.OutlineAction
import com.offlinepay.ui.components.PrimaryAction
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

@Composable
fun HomeScreen(
    state: AppUiState,
    onPay: () -> Unit,
    onWallet: () -> Unit,
    onHistory: () -> Unit,
    onOfflinePay: () -> Unit,
    onSync: () -> Unit,
    onToggleInternet: () -> Unit,
    onToggleBluetoothReceive: () -> Unit,
    onMerchant: (WalletTransaction) -> Unit
) {
    val pendingCount = state.transactions.count { it.status == TransactionStatus.PENDING_VERIFICATION }

    Column(
        Modifier
            .fillMaxSize()
            .background(WealthBeige)
            .verticalScroll(rememberScrollState())
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xFF07182D), Color(0xFF092A50))))
        ) {
            WaveArt(Modifier.matchParentSize(), dark = true)
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp)) {
                // Top Status Bar: Internet toggle & user profile greeting
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Welcome to OfflinePay,", color = Color(0xFFBACBDD), fontSize = 13.sp)
                        val displayName = state.userName.ifBlank { "User" }
                        Text(displayName, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (state.userEmail.isNotBlank()) "${state.userEmail} • +91 ${state.phoneNumber}"
                            else "+91 ${state.phoneNumber}",
                            color = Color(0xFFBACBDD),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    // Network toggle button (Simulate Offline / Online)
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (state.internetAvailable) Color(0xFF1B4E38) else Color(0xFF5A2A2A))
                            .clickable(onClick = onToggleInternet)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (state.internetAvailable) Icons.Rounded.Wifi else Icons.Rounded.WifiOff,
                            null,
                            tint = if (state.internetAvailable) Color(0xFF68D391) else Color(0xFFFEB2B2),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (state.internetAvailable) "Online" else "Offline",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Balance Card
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF0E86EE), Color(0xFF0757B5))))
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.AccountBalanceWallet, null, tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text("Available Online Balance", color = Color(0xFFDBEFFF), fontSize = 13.sp)
                        Text("₹${state.onlineBalance}", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
                        .background(WealthBeige)
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BalanceColumn("Locked in Offline Token", "₹${state.offlineBalance}", Icons.Rounded.WifiOff, Modifier.weight(1f))
                    Box(Modifier.width(1.dp).height(42.dp).background(Color(0xFFD4DDE6)))
                    BalanceColumn("Total Balance", "₹${state.onlineBalance + state.offlineBalance}", Icons.Rounded.AccountBalance, Modifier.weight(1f))
                }

                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    HomeAction("Pay with UPI", Icons.Rounded.QrCodeScanner, onPay, Modifier.weight(1f))
                    HomeAction("Manage\nWallet", Icons.Rounded.AccountBalanceWallet, onWallet, Modifier.weight(1f))
                    HomeAction("Transaction\nHistory", Icons.Rounded.History, onHistory, Modifier.weight(1f))
                }

                Spacer(Modifier.height(12.dp))

                // Pay Offline Action
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(TrustIndigo)
                        .clickable(onClick = onOfflinePay)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(45.dp).clip(CircleShape).background(Color(0xFF254262)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.WifiOff, null, tint = Color.White)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text("Pay Offline", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Spend offline allowance via Bluetooth or QR", color = Color(0xFFCCD9E8), fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Color.White)
                }

                Spacer(Modifier.height(10.dp))

                // Receive Offline Payment Action (Bluetooth Merchant mode)
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (state.bluetoothListening) Color(0xFF1E5B3A) else Color(0xFF1B314B))
                        .clickable(onClick = onToggleBluetoothReceive)
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(45.dp).clip(CircleShape).background(Color.White.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Bluetooth, null, tint = Color.White)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(
                            if (state.bluetoothListening) "Listening for Bluetooth Payment..." else "Receive Offline Payment (Bluetooth)",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            if (state.bluetoothListening) "Payer phone can now connect and pay!" else "Act as receiver / merchant phone",
                            color = Color(0xFFCCD9E8),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    if (state.bluetoothListening) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }

        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            // Sync Banner if there are pending offline payments
            if (pendingCount > 0) {
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFEAF5FF))
                        .border(1.dp, YieldBlue, RoundedCornerShape(16.dp))
                        .clickable(onClick = onSync)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(SoftBlue), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Sync, null, tint = YieldBlue)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("$pendingCount Offline Payment(s) Queued", color = TrustIndigo, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Tap to sync with backend & Kafka settlement", color = MutedNavy, fontSize = 12.sp)
                    }
                    if (state.isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = YieldBlue, strokeWidth = 2.dp)
                    } else {
                        Text("Sync  ›", color = YieldBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recent Transactions", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TrustIndigo)
                Text("View All  ›", color = YieldBlue, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onHistory))
            }

            SectionCard {
                if (state.transactions.isEmpty()) {
                    Text("No transactions yet. Complete a payment or reserve offline tokens to start tracking.", color = MutedNavy, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                } else {
                    state.transactions.take(4).forEachIndexed { index, transaction ->
                        TransactionLine(transaction, onClick = { onMerchant(transaction) }, showStatus = true)
                        if (index != state.transactions.take(4).lastIndex) {
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun BalanceColumn(label: String, amount: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Column(modifier.padding(horizontal = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = YieldBlue, modifier = Modifier.size(17.dp))
            Text(label, color = MutedNavy, fontSize = 12.sp, modifier = Modifier.padding(start = 5.dp))
        }
        Text(amount, color = TrustIndigo, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun HomeAction(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .height(106.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(WealthBeige)
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = YieldBlue, modifier = Modifier.size(30.dp))
        Text(title, textAlign = TextAlign.Center, color = TrustIndigo, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun UpiChoiceScreen(
    state: AppUiState,
    onBack: () -> Unit,
    onScan: () -> Unit,
    onMobile: () -> Unit,
    onMerchant: (WalletTransaction) -> Unit,
    onHistory: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(WealthBeige).verticalScroll(rememberScrollState())) {
        DarkHero("Pay with UPI", "Choose how you want to pay", onBack, illustration = true)
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp)) {
            ChoiceCard("Scan QR Code", "Scan the merchant's QR code to pay.", Icons.Rounded.QrCodeScanner, onScan)
            Spacer(Modifier.height(10.dp))
            ChoiceCard("Enter Mobile Number", "Enter UPI ID or mobile number to pay.", Icons.Rounded.PhoneAndroid, onMobile)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(Modifier.weight(1f).height(1.dp).background(Color(0xFFCCD8E2)))
                Text("  OR  ", color = MutedNavy, fontSize = 12.sp)
                Box(Modifier.weight(1f).height(1.dp).background(Color(0xFFCCD8E2)))
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Recent Activity", color = TrustIndigo, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("View All  ›", color = YieldBlue, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onHistory))
            }
            SectionCard {
                if (state.transactions.isEmpty()) {
                    Text("No transactions yet.", color = MutedNavy, fontSize = 13.sp, modifier = Modifier.padding(10.dp))
                } else {
                    state.transactions.take(3).forEachIndexed { index, transaction ->
                        TransactionLine(transaction, onClick = { onMerchant(transaction) })
                        if (index != state.transactions.take(3).lastIndex) Spacer(Modifier.height(6.dp))
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ChoiceCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFFFFFEFA))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(66.dp).clip(RoundedCornerShape(15.dp)).background(if (title.startsWith("Scan")) TrustIndigo else YieldBlue), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(34.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, color = TrustIndigo, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(subtitle, color = MutedNavy, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 5.dp))
        }
        Text("›", fontSize = 28.sp, color = TrustIndigo)
    }
}

@Composable
fun WalletScreen(
    state: AppUiState,
    onReserve: (String) -> Unit,
    onSync: () -> Unit,
    onSetServerUrl: (String) -> Unit,
    onTransfer: () -> Unit,
    onHistory: () -> Unit,
    onClearHistory: () -> Unit,
    onMerchant: (WalletTransaction) -> Unit
) {
    var reserveAmount by remember { mutableStateOf("300") }
    var customUrl by remember { mutableStateOf(state.serverBaseUrl) }

    Column(Modifier.fillMaxSize().background(WealthBeige).verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Wallet & Offline Tokens", color = TrustIndigo, fontWeight = FontWeight.Bold, fontSize = 26.sp, modifier = Modifier.padding(top = 12.dp))
        Text("Manage balances, reserve offline tokens, and view profile.", color = MutedNavy, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))

        // Total Balance
        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFF0E86EE), Color(0xFF0757B5))))
                .padding(20.dp)
        ) {
            Column {
                Text("Total Account Balance", color = Color(0xFFDCEFFF), fontSize = 13.sp)
                Text("₹${state.onlineBalance + state.offlineBalance}", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 13.dp)) {
            WalletBalanceCard("Available Online", "₹${state.onlineBalance}", Icons.Rounded.AccountBalance, Modifier.weight(1f))
            WalletBalanceCard("Offline Token", "₹${state.offlineBalance}", Icons.Rounded.WifiOff, Modifier.weight(1f))
        }

        // User Profile Card
        SectionCard(Modifier.padding(top = 16.dp)) {
            Text("Registered Account Profile", color = TrustIndigo, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Person, null, tint = YieldBlue, modifier = Modifier.size(18.dp))
                Text(state.userName.ifBlank { "User" }, color = TrustIndigo, fontWeight = FontWeight.Medium, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Email, null, tint = YieldBlue, modifier = Modifier.size(18.dp))
                Text(state.userEmail.ifBlank { "Not provided" }, color = MutedNavy, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.PhoneAndroid, null, tint = YieldBlue, modifier = Modifier.size(18.dp))
                Text("+91 ${state.phoneNumber}", color = MutedNavy, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
            }
        }

        // Active Offline Token Info
        SectionCard(Modifier.padding(top = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (state.activeOfflineToken != null) Icons.Rounded.CheckCircle else Icons.Rounded.WifiOff,
                    null,
                    tint = if (state.activeOfflineToken != null) Color(0xFF38A169) else Color(0xFFE53E3E),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (state.activeOfflineToken != null) "Active Backend Offline Token Loaded" else "No Active Offline Token",
                    color = TrustIndigo,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
            if (state.activeTokenId != null) {
                Text("Token ID: ${state.activeTokenId}", color = MutedNavy, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
            if (state.tokenExpiresAt != null) {
                Text("Expires: ${state.tokenExpiresAt}", color = MutedNavy, fontSize = 11.sp)
            }
        }

        // Reserve Token from Backend Section
        Text("Reserve Offline Funds", color = TrustIndigo, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        SectionCard {
            Text("Locks funds on Ledger Service (Port 8082) and issues a signed JWT Token (Port 8081) for offline phone-to-phone spending.", color = MutedNavy, fontSize = 13.sp, lineHeight = 18.sp)
            OutlinedTextField(
                value = reserveAmount,
                onValueChange = { reserveAmount = it.filter(Char::isDigit).take(6) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                singleLine = true,
                prefix = { Text("₹") },
                placeholder = { Text("Amount to reserve") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = YieldBlue,
                    unfocusedBorderColor = Color(0xFFD7E5EF)
                )
            )

            PrimaryAction(
                if (state.isReserving) "Reserving from Backend..." else "Reserve from Backend",
                onClick = { onReserve(reserveAmount) },
                modifier = Modifier.padding(top = 12.dp),
                enabled = reserveAmount.isNotBlank() && !state.isReserving
            )
        }

        // Backend Server Setting
        Text("Backend Server Address", color = TrustIndigo, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        SectionCard {
            Text("Select target host based on where you are running the app:", color = MutedNavy, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryAction(
                    "Emulator (10.0.2.2)",
                    onClick = {
                        customUrl = "http://10.0.2.2:8080"
                        onSetServerUrl(customUrl)
                    },
                    modifier = Modifier.weight(1f)
                )
                PrimaryAction(
                    "Wi-Fi (192.168.1.6)",
                    onClick = {
                        customUrl = "http://192.168.1.6:8080"
                        onSetServerUrl(customUrl)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = customUrl,
                onValueChange = { customUrl = it },
                label = { Text("Current Server URL") },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                singleLine = true
            )
            OutlineAction(
                "Apply Server URL",
                onClick = { onSetServerUrl(customUrl) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }

        Spacer(Modifier.height(14.dp))
        PrimaryAction("Sync Queued Payments Now", onClick = onSync, modifier = Modifier.fillMaxWidth())

        Row(
            Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Recent Transactions", color = TrustIndigo, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("View All  ›", color = YieldBlue, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onHistory))
        }
        SectionCard {
            if (state.transactions.isEmpty()) {
                Text("No transactions yet.", color = MutedNavy, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
            } else {
                state.transactions.take(4).forEachIndexed { index, transaction ->
                    TransactionLine(transaction, onClick = { onMerchant(transaction) }, showStatus = true)
                    if (index != state.transactions.take(4).lastIndex) Spacer(Modifier.height(5.dp))
                }
            }
        }
    }
}

@Composable
private fun WalletBalanceCard(title: String, balance: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xFFFFFEFA)).padding(14.dp)) {
        Icon(icon, null, tint = YieldBlue)
        Text(title, color = MutedNavy, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        Text(balance, color = TrustIndigo, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
fun HistoryScreen(
    state: AppUiState,
    onClearHistory: () -> Unit,
    onMerchant: (WalletTransaction) -> Unit
) {
    Column(Modifier.fillMaxSize().background(WealthBeige).verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Transaction History", color = TrustIndigo, fontWeight = FontWeight.Bold, fontSize = 25.sp)
            if (state.transactions.isNotEmpty()) {
                Row(
                    Modifier.clickable(onClick = onClearHistory).padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.DeleteOutline, null, tint = Color(0xFFE53E3E), modifier = Modifier.size(16.dp))
                    Text("Clear", color = Color(0xFFE53E3E), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 2.dp))
                }
            }
        }
        Text("Real-time tracking of offline payments, Bluetooth transfers, and ledger settlements.", color = MutedNavy, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))

        SectionCard(Modifier.padding(top = 8.dp)) {
            if (state.transactions.isEmpty()) {
                Text(
                    "No transactions recorded yet.\nAny offline payment created or received will be logged here.",
                    color = MutedNavy,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(14.dp)
                )
            } else {
                state.transactions.forEachIndexed { index, transaction ->
                    TransactionLine(transaction, onClick = { onMerchant(transaction) }, showStatus = true)
                    if (index != state.transactions.lastIndex) Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun TransactionLine(transaction: WalletTransaction, onClick: () -> Unit, showStatus: Boolean = false) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MerchantIcon(Modifier.size(42.dp))
        Column(Modifier.weight(1f).padding(start = 11.dp)) {
            Text(transaction.merchant, color = TrustIndigo, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Text("${transaction.method} • ${transaction.dateLabel}", color = MutedNavy, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
            if (showStatus) {
                Spacer(Modifier.height(4.dp))
                StatusBadge(
                    if (transaction.status == TransactionStatus.PENDING_VERIFICATION) "Pending Sync (Offline)" else "Settled on Bank Ledger",
                    pending = transaction.status == TransactionStatus.PENDING_VERIFICATION
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("₹${transaction.amountRupees}", color = TrustIndigo, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            if (!showStatus) {
                Text("›", color = TrustIndigo, fontSize = 20.sp)
            }
        }
    }
}
