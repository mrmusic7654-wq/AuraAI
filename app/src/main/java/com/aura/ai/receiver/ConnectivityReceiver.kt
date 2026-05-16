package com.aura.ai.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

class ConnectivityReceiver : BroadcastReceiver() {
    
    var connectivityListener: ((Boolean) -> Unit)? = null
    
    override fun onReceive(context: Context, intent: Intent) {
        val isConnected = isNetworkAvailable(context)
        connectivityListener?.invoke(isConnected)
    }
    
    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = 
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
               capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
               capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}
