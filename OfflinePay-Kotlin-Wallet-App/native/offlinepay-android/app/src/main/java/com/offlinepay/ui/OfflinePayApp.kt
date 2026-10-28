package com.offlinepay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.offlinepay.data.AppUiState
import com.offlinepay.data.OfflinePayViewModel
import com.offlinepay.data.WalletTransaction
import com.offlinepay.ui.screens.HistoryScreen
import com.offlinepay.ui.screens.HomeScreen
import com.offlinepay.ui.screens.MobilePayScreen
import com.offlinepay.ui.screens.OfflinePaymentScreen
import com.offlinepay.ui.screens.OnboardingScreen
import com.offlinepay.ui.screens.OtpEntryScreen
import com.offlinepay.ui.screens.PaymentDetailsScreen
import com.offlinepay.ui.screens.PaymentSavedScreen
import com.offlinepay.ui.screens.PhoneEntryScreen
import com.offlinepay.ui.screens.ProfileSetupScreen
import com.offlinepay.ui.screens.QrScannerScreen
import com.offlinepay.ui.screens.SignInChoiceScreen
import com.offlinepay.ui.screens.UpiChoiceScreen
import com.offlinepay.ui.screens.WalletScreen
import com.offlinepay.ui.theme.WealthBeige

private data class BottomDestination(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val bottomDestinations = listOf(
    BottomDestination("home", "Home", Icons.Rounded.Home),
    BottomDestination("wallet", "Wallet", Icons.Rounded.AccountBalanceWallet),
    BottomDestination("history", "History", Icons.Rounded.History)
)

@Composable
fun OfflinePayApp(state: AppUiState, viewModel: OfflinePayViewModel) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    var otp by rememberSaveable { mutableStateOf("") }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isUserFullyOnboarded = state.authenticated && state.userName.isNotBlank()
    val showBottomBar = isUserFullyOnboarded && currentRoute in setOf("home", "wallet", "history")

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = WealthBeige) {
                    bottomDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                androidx.compose.material3.Icon(
                                    destination.icon,
                                    contentDescription = destination.label
                                )
                            },
                            label = { Text(destination.label, fontSize = 11.sp, fontWeight = FontWeight.Medium) }
                        )
                    }
                }
            }
        },
        containerColor = WealthBeige
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (isUserFullyOnboarded) "home" else "onboarding",
            modifier = Modifier.padding(innerPadding).background(WealthBeige)
        ) {
            composable("onboarding") {
                OnboardingScreen(onStart = { navController.navigate("sign-in") })
            }
            composable("sign-in") {
                SignInChoiceScreen(
                    onBack = { navController.popBackStack() },
                    onContinue = { navController.navigate("phone") }
                )
            }
            composable("phone") {
                PhoneEntryScreen(
                    phoneNumber = state.phoneNumber,
                    name = state.userName,
                    onPhoneChange = viewModel::setPhoneNumber,
                    onNameChange = viewModel::setUserName,
                    onBack = { navController.popBackStack() },
                    onContinue = { navController.navigate("otp") }
                )
            }
            composable("otp") {
                OtpEntryScreen(
                    phoneNumber = state.phoneNumber,
                    code = otp,
                    onCodeChange = { otp = it.filter(Char::isDigit).take(6) },
                    onBack = { navController.popBackStack() },
                    onVerify = {
                        viewModel.authenticateDemo(otp) {
                            otp = ""
                            if (state.userName.isBlank()) {
                                navController.navigate("profile") {
                                    popUpTo("onboarding") { inclusive = false }
                                }
                            } else {
                                navController.navigate("home") {
                                    popUpTo("onboarding") { inclusive = true }
                                }
                            }
                        }
                    }
                )
            }
            composable("profile") {
                ProfileSetupScreen(
                    name = state.userName,
                    email = state.userEmail,
                    onNameChange = viewModel::setUserName,
                    onEmailChange = viewModel::setUserEmail,
                    onBack = { navController.popBackStack() },
                    onComplete = {
                        viewModel.saveUserProfile(state.userName, state.userEmail)
                        navController.navigate("home") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                )
            }
            composable("home") {
                HomeScreen(
                    state = state,
                    onPay = {
                        navController.navigate(if (state.internetAvailable) "upi" else "offline")
                    },
                    onWallet = { navController.navigate("wallet") },
                    onHistory = { navController.navigate("history") },
                    onOfflinePay = { navController.navigate("offline") },
                    onSync = viewModel::syncNow,
                    onToggleInternet = { viewModel.setInternetAvailable(!state.internetAvailable) },
                    onToggleBluetoothReceive = {
                        if (state.bluetoothListening) viewModel.stopBluetoothReceiver()
                        else viewModel.startBluetoothReceiver()
                    },
                    onMerchant = { transaction -> openTransaction(navController, viewModel, transaction) }
                )
            }
            composable("upi") {
                UpiChoiceScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onScan = { navController.navigate("scanner") },
                    onMobile = { navController.navigate("mobile") },
                    onMerchant = { transaction -> openTransaction(navController, viewModel, transaction) },
                    onHistory = { navController.navigate("history") }
                )
            }
            composable("scanner") {
                QrScannerScreen(
                    onBack = { navController.popBackStack() },
                    onDetected = { raw ->
                        if (raw.contains("offlineToken") && raw.contains("deviceTransactionId")) {
                            if (viewModel.processScannedQr(raw)) {
                                navController.navigate("history")
                            }
                        } else {
                            val merchantId = raw.take(32)
                            viewModel.setMerchant("QR merchant", merchantId)
                            navController.navigate("details")
                        }
                    },
                    onMobile = { navController.navigate("mobile") }
                )
            }
            composable("mobile") {
                MobilePayScreen(
                    onBack = { navController.popBackStack() },
                    onContinue = { number ->
                        viewModel.setMerchant("Payee • $number", "UPI ID verified")
                        navController.navigate("details")
                    }
                )
            }
            composable("details") {
                PaymentDetailsScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onAmountChange = viewModel::setPaymentAmount,
                    onNoteChange = viewModel::setPaymentNote,
                    onMethodChange = viewModel::setPaymentMethod,
                    onConfirm = {
                        viewModel.confirmOnlinePayment()
                        navController.navigate("history")
                    },
                    onOffline = { navController.navigate("offline") }
                )
            }
            composable("offline") {
                OfflinePaymentScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onAmountChange = viewModel::setPaymentAmount,
                    onNoteChange = viewModel::setPaymentNote,
                    pairedDevices = viewModel.bluetoothManager.getPairedDevices(),
                    onPayQr = {
                        if (viewModel.saveOfflinePayment()) navController.navigate("success")
                    },
                    onPayBluetooth = { dev ->
                        viewModel.sendBluetoothPayment(dev)
                        navController.navigate("success")
                    }
                )
            }
            composable("success") {
                val transaction = state.lastTransaction
                if (transaction != null) {
                    PaymentSavedScreen(
                        state = state,
                        transaction = transaction,
                        onSendBluetooth = viewModel::sendBluetoothPayment,
                        onSync = viewModel::syncNow,
                        pairedDevices = viewModel.bluetoothManager.getPairedDevices(),
                        onDone = {
                            navController.navigate("home") {
                                popUpTo("home") { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    )
                }
            }
            composable("wallet") {
                WalletScreen(
                    state = state,
                    onReserve = viewModel::reserveOffline,
                    onSync = viewModel::syncNow,
                    onSetServerUrl = viewModel::setServerBaseUrl,
                    onTransfer = viewModel::refreshBalancesFromBackend,
                    onHistory = { navController.navigate("history") },
                    onClearHistory = viewModel::clearTransactions,
                    onMerchant = { transaction -> openTransaction(navController, viewModel, transaction) }
                )
            }
            composable("history") {
                HistoryScreen(
                    state = state,
                    onClearHistory = viewModel::clearTransactions,
                    onMerchant = { transaction -> openTransaction(navController, viewModel, transaction) }
                )
            }
        }
    }
}

private fun openTransaction(
    navController: androidx.navigation.NavHostController,
    viewModel: OfflinePayViewModel,
    transaction: WalletTransaction
) {
    viewModel.setMerchant(transaction.merchant, transaction.id)
    navController.navigate("details")
}
