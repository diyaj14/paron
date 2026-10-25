package com.offlinepay.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offlinepay.data.AppUiState
import com.offlinepay.data.TransactionStatus
import com.offlinepay.data.WalletTransaction
import com.offlinepay.ui.components.DemoBanner
import com.offlinepay.ui.components.DarkHero
import com.offlinepay.ui.components.MerchantIcon
import com.offlinepay.ui.components.MerchantRow
import com.offlinepay.ui.components.OutlineAction
import com.offlinepay.ui.components.PrimaryAction
import com.offlinepay.ui.components.SectionCard
import com.offlinepay.ui.components.StatusBadge
import com.offlinepay.ui.components.WaveArt
import com.offlinepay.ui.theme.GlacierBlue
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
    onMerchant: (WalletTransaction) -> Unit
) {
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
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Good morning,", color = Color(0xFFBACBDD), fontSize = 14.sp)
                        Text("Diya Joshy", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF294565)), contentAlignment = Alignment.Center) {
                        Text("D", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
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
                        Text("Current Balance", color = Color(0xFFDBEFFF), fontSize = 14.sp)
                        Text("₹${state.onlineBalance + state.offlineBalance}", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    }
                    Icon(Icons.Rounded.Visibility, contentDescription = "Balance visible", tint = Color.White)
                }
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
                        .background(WealthBeige)
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BalanceColumn("Online Balance", "₹${state.onlineBalance}", Icons.Rounded.AccountBalance, Modifier.weight(1f))
                    Box(Modifier.width(1.dp).height(48.dp).background(Color(0xFFD4DDE6)))
                    BalanceColumn("Offline Balance", "₹${state.offlineBalance}", Icons.Rounded.WifiOff, Modifier.weight(1f))
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    HomeAction("Pay with UPI", Icons.Rounded.QrCodeScanner, onPay, Modifier.weight(1f))
                    HomeAction("Wallet", Icons.Rounded.AccountBalanceWallet, onWallet, Modifier.weight(1f))
                    HomeAction("Transaction\nHistory", Icons.Rounded.History, onHistory, Modifier.weight(1f))
                }
                Spacer(Modifier.height(13.dp))
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
                        Text("No Internet?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Use your offline balance to pay.", color = Color(0xFFCCD9E8), fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
                    }
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Color.White)
                }
            }
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            DemoBanner()
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recent Transactions", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TrustIndigo)
                Text("View All  ›", color = YieldBlue, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onHistory))
            }
            SectionCard {
                state.transactions.take(3).forEachIndexed { index, transaction ->
                    TransactionLine(transaction, onClick = { onMerchant(transaction) })
                    if (index != state.transactions.take(3).lastIndex) {
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun BalanceColumn(
    title: String,
    balance: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Row(modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = YieldBlue, modifier = Modifier.size(25.dp))
        Column(Modifier.padding(start = 10.dp)) {
            Text(title, color = TrustIndigo, fontSize = 12.sp)
            Text(balance, color = TrustIndigo, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
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
                Text("Recent Merchants", color = TrustIndigo, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("View All  ›", color = YieldBlue, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onHistory))
            }
            SectionCard {
                state.transactions.take(3).forEachIndexed { index, transaction ->
                    TransactionLine(transaction, onClick = { onMerchant(transaction) })
                    if (index != state.transactions.take(3).lastIndex) Spacer(Modifier.height(6.dp))
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
    onTransfer: () -> Unit,
    onHistory: () -> Unit,
    onMerchant: (WalletTransaction) -> Unit
) {
    var reserveAmount by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(WealthBeige).verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Wallet", color = TrustIndigo, fontWeight = FontWeight.Bold, fontSize = 26.sp, modifier = Modifier.padding(top = 12.dp))
        Text("Manage your available balances.", color = MutedNavy, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
        DemoBanner()
        Box(
            Modifier.fillMaxWidth().padding(top = 17.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFF0E86EE), Color(0xFF0757B5))))
                .padding(20.dp)
        ) {
            Column {
                Text("Current Balance", color = Color(0xFFDCEFFF), fontSize = 14.sp)
                Text("₹${state.onlineBalance + state.offlineBalance}", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 13.dp)) {
            WalletBalanceCard("Online Balance", "₹${state.onlineBalance}", Icons.Rounded.AccountBalance, Modifier.weight(1f))
            WalletBalanceCard("Offline Balance", "₹${state.offlineBalance}", Icons.Rounded.WifiOff, Modifier.weight(1f))
        }
        Text("Reserve Offline", color = TrustIndigo, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(top = 23.dp, bottom = 8.dp))
        SectionCard {
            Text("Move a portion of your online balance to your offline allowance.", color = MutedNavy, fontSize = 13.sp, lineHeight = 19.sp)
            OutlinedTextField(
                value = reserveAmount,
                onValueChange = { reserveAmount = it.filter(Char::isDigit).take(7) },
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
                "Reserve Offline",
                onClick = { onReserve(reserveAmount) },
                modifier = Modifier.padding(top = 12.dp),
                enabled = reserveAmount.isNotBlank()
            )
        }
        OutlineAction("Transfer from bank", onTransfer, Modifier.fillMaxWidth().padding(top = 12.dp))
        Row(
            Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Recent transactions", color = TrustIndigo, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("View all  ›", color = YieldBlue, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onHistory))
        }
        SectionCard {
            state.transactions.take(4).forEachIndexed { index, transaction ->
                TransactionLine(transaction, onClick = { onMerchant(transaction) })
                if (index != state.transactions.take(4).lastIndex) Spacer(Modifier.height(5.dp))
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
fun HistoryScreen(state: AppUiState, onMerchant: (WalletTransaction) -> Unit) {
    Column(Modifier.fillMaxSize().background(WealthBeige).verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Transaction History", color = TrustIndigo, fontWeight = FontWeight.Bold, fontSize = 25.sp, modifier = Modifier.padding(top = 12.dp))
        Text("Your recent payments and their current status.", color = MutedNavy, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
        DemoBanner()
        SectionCard(Modifier.padding(top = 16.dp)) {
            state.transactions.forEachIndexed { index, transaction ->
                TransactionLine(transaction, onClick = { onMerchant(transaction) }, showStatus = true)
                if (index != state.transactions.lastIndex) Spacer(Modifier.height(8.dp))
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
            Text(transaction.dateLabel, color = MutedNavy, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
            if (showStatus) {
                Spacer(Modifier.height(5.dp))
                StatusBadge(
                    if (transaction.status == TransactionStatus.PENDING_VERIFICATION) "Pending verification" else "Recorded demo data",
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
