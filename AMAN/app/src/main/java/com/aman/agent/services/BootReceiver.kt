package com.aman.agent.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.aman.agent.AmanApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * BootReceiver - Automatically starts AMAN agent after device boot
 * Ensures the background service is always running when device starts
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
        private const val STARTUP_DELAY = 10000L // 10 seconds delay after boot
    }

    private val receiverScope = CoroutineScope(Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.i(TAG, "Device boot completed - starting AMAN agent")
                startAmanAgent(context)
            }
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                if (intent.dataString?.contains(context.packageName) == true) {
                    Log.i(TAG, "AMAN package updated - restarting agent")
                    startAmanAgent(context)
                }
            }
        }
    }

    private fun startAmanAgent(context: Context) {
        receiverScope.launch {
            try {
                // Wait for system to stabilize after boot
                delay(STARTUP_DELAY)
                
                val app = context.applicationContext as AmanApplication
                
                // Check if agent is configured
                if (!app.securityManager.isMasterNumberSet()) {
                    Log.i(TAG, "Master number not set - agent in standby mode")
                    return@launch
                }
                
                // Check if agent is paused
                if (app.securityManager.isAgentPaused()) {
                    Log.i(TAG, "Agent is paused - not starting service")
                    return@launch
                }
                
                // Start the foreground service
                val serviceIntent = Intent(context, AmanForegroundService::class.java)
                context.startForegroundService(serviceIntent)
                
                Log.i(TAG, "AMAN agent started successfully after boot")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start AMAN agent after boot", e)
            }
        }
    }
}
