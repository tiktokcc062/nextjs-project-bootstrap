package com.aman.agent.ui

import android.app.Activity
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.aman.agent.AmanApplication
import com.aman.agent.R
import kotlinx.coroutines.*

/**
 * SecretActivity - Hidden activity accessible only via secret command
 * Provides basic status information and emergency controls
 */
class SecretActivity : Activity() {

    companion object {
        private const val TAG = "SecretActivity"
        private const val AUTO_FINISH_DELAY = 10000L // 10 seconds
    }

    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.i(TAG, "Secret activity accessed")
        
        try {
            // Set transparent theme
            setTheme(R.style.Theme_AMAN_Transparent)
            
            // Show status information
            showStatusInformation()
            
            // Auto-finish after delay
            scheduleAutoFinish()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in secret activity", e)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
        
        // Disable the activity again after use
        disableActivity()
    }

    private fun showStatusInformation() {
        try {
            val app = application as AmanApplication
            
            val statusInfo = buildString {
                appendLine("AMAN Agent Status")
                appendLine("================")
                appendLine("Active: ${app.isAgentActive()}")
                appendLine("Master Set: ${app.securityManager.isMasterNumberSet()}")
                appendLine("Paused: ${app.securityManager.isAgentPaused()}")
                appendLine("Enhanced Security: ${app.securityManager.isEnhancedSecurityEnabled()}")
                appendLine("Debug Detected: ${app.securityManager.isDebuggingDetected()}")
                appendLine("Root Detected: ${app.securityManager.isRootDetected()}")
                appendLine("App Integrity: ${app.securityManager.verifyAppIntegrity()}")
            }
            
            // Show status as toast (since we have no UI)
            Toast.makeText(this, statusInfo, Toast.LENGTH_LONG).show()
            
            Log.i(TAG, "Status information displayed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing status information", e)
            Toast.makeText(this, "Error retrieving status", Toast.LENGTH_SHORT).show()
        }
    }

    private fun scheduleAutoFinish() {
        activityScope.launch {
            delay(AUTO_FINISH_DELAY)
            if (!isFinishing) {
                Log.i(TAG, "Auto-finishing secret activity")
                finish()
            }
        }
    }

    private fun disableActivity() {
        try {
            val componentName = ComponentName(this, SecretActivity::class.java)
            packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            Log.i(TAG, "Secret activity disabled")
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling secret activity", e)
        }
    }

    /**
     * Enable the secret activity (called via command)
     */
    companion object {
        fun enableActivity(app: AmanApplication) {
            try {
                val componentName = ComponentName(app, SecretActivity::class.java)
                app.packageManager.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                Log.i(TAG, "Secret activity enabled")
            } catch (e: Exception) {
                Log.e(TAG, "Error enabling secret activity", e)
            }
        }
    }
}
