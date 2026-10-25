package com.offlinepay

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.offlinepay.data.OfflinePayViewModel
import com.offlinepay.ui.OfflinePayApp
import com.offlinepay.ui.theme.OfflinePayTheme

class MainActivity : ComponentActivity() {
    private lateinit var appViewModel: OfflinePayViewModel
    private lateinit var connectivityManager: ConnectivityManager
    private var callbackRegistered = false
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateConnectivity()
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            appViewModel.setInternetAvailable(
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            )
        }

        override fun onLost(network: Network) {
            updateConnectivity()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appViewModel = ViewModelProvider(this)[OfflinePayViewModel::class.java]
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        updateConnectivity()
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = android.graphics.Color.rgb(8, 26, 49)
        window.navigationBarColor = android.graphics.Color.rgb(255, 251, 231)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = 0
        setContent {
            OfflinePayTheme {
                val appViewModel: OfflinePayViewModel = viewModel()
                val state by appViewModel.uiState.collectAsState()
                OfflinePayApp(state = state, viewModel = appViewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!callbackRegistered) {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
            callbackRegistered = true
            updateConnectivity()
        }
    }

    override fun onStop() {
        if (callbackRegistered) {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            callbackRegistered = false
        }
        super.onStop()
    }

    private fun updateConnectivity() {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = activeNetwork?.let(connectivityManager::getNetworkCapabilities)
        appViewModel.setInternetAvailable(
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        )
    }
}
