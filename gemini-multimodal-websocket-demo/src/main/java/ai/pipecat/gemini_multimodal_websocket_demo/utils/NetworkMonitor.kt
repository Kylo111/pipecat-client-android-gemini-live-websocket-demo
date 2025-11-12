package ai.pipecat.gemini_multimodal_websocket_demo.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NetworkMonitor(context: Context) {
    
    companion object {
        private const val TAG = "NetworkMonitor"
    }
    
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    private val _isConnected = MutableStateFlow(isNetworkAvailable())
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val _onNetworkReconnected = MutableStateFlow(0L)
    val onNetworkReconnected: StateFlow<Long> = _onNetworkReconnected.asStateFlow()
    
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "Network available")
            val wasDisconnected = !_isConnected.value
            _isConnected.value = true
            
            // Trigger reconnection event if we were previously disconnected
            if (wasDisconnected) {
                _onNetworkReconnected.value = System.currentTimeMillis()
                Log.d(TAG, "Network reconnected, triggering queue processing")
            }
        }
        
        override fun onLost(network: Network) {
            Log.d(TAG, "Network lost")
            _isConnected.value = isNetworkAvailable()
        }
        
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val wasDisconnected = !_isConnected.value
            _isConnected.value = hasInternet
            
            // Trigger reconnection event if we regained internet
            if (hasInternet && wasDisconnected) {
                _onNetworkReconnected.value = System.currentTimeMillis()
                Log.d(TAG, "Internet capability restored, triggering queue processing")
            }
        }
    }
    
    init {
        registerNetworkCallback()
    }
    
    private fun registerNetworkCallback() {
        try {
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
            Log.d(TAG, "Network callback registered")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering network callback", e)
        }
    }
    
    fun unregister() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            Log.d(TAG, "Network callback unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering network callback", e)
        }
    }
    
    private fun isNetworkAvailable(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
