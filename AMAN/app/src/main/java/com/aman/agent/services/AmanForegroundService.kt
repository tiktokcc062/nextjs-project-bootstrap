package com.aman.agent.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.aman.agent.AmanApplication
import com.aman.agent.R
import com.aman.agent.commands.CommandProcessor
import com.aman.agent.modules.ModuleManager
import kotlinx.coroutines.*

/**
 * AmanForegroundService - Main background service for AMAN agent
 * Handles command processing, module management, and system monitoring
 */
class AmanForegroundService : Service() {

    companion object {
        private const val TAG = "AmanForegroundService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "aman_service_channel"
        private const val CHANNEL_NAME = "AMAN Background Service"
    }

    private lateinit var commandProcessor: CommandProcessor
    private lateinit var moduleManager: ModuleManager
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isServiceRunning = false

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "AMAN Foreground Service created")
        
        // Initialize components
        initializeComponents()
        
        // Create notification channel
        createNotificationChannel()
        
        // Start foreground service
        startForeground(NOTIFICATION_ID, createNotification())
        
        isServiceRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Service start command received")
        
        if (!isServiceRunning) {
            startBackgroundOperations()
        }
        
        // Return START_STICKY to restart service if killed
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // This is not a bound service
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "AMAN Foreground Service destroyed")
        
        isServiceRunning = false
        serviceScope.cancel()
        
        // Clean up resources
        cleanup()
    }

    private fun initializeComponents() {
        try {
            val app = application as AmanApplication
            
            // Initialize command processor
            commandProcessor = CommandProcessor(this, app.securityManager)
            
            // Initialize module manager
            moduleManager = ModuleManager(this, app.securityManager)
            
            Log.i(TAG, "Components initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize components", e)
        }
    }

    private fun startBackgroundOperations() {
        serviceScope.launch {
            try {
                // Start command processing loop
                startCommandProcessing()
                
                // Start system monitoring
                startSystemMonitoring()
                
                // Start module management
                startModuleManagement()
                
                Log.i(TAG, "Background operations started")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start background operations", e)
            }
        }
    }

    private suspend fun startCommandProcessing() {
        withContext(Dispatchers.Default) {
            while (isServiceRunning) {
                try {
                    // Process pending commands
                    commandProcessor.processPendingCommands()
                    
                    // Check for scheduled tasks
                    commandProcessor.processScheduledTasks()
                    
                    // Wait before next cycle
                    delay(5000) // 5 seconds
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error in command processing loop", e)
                    delay(10000) // Wait longer on error
                }
            }
        }
    }

    private suspend fun startSystemMonitoring() {
        withContext(Dispatchers.Default) {
            while (isServiceRunning) {
                try {
                    val app = application as AmanApplication
                    
                    // Check security status
                    if (app.securityManager.isDebuggingDetected()) {
                        Log.w(TAG, "Security threat detected")
                        app.securityManager.enableEnhancedSecurity()
                    }
                    
                    // Monitor system health
                    monitorSystemHealth()
                    
                    // Update notification if needed
                    updateNotification()
                    
                    delay(30000) // 30 seconds
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error in system monitoring", e)
                    delay(60000) // Wait longer on error
                }
            }
        }
    }

    private suspend fun startModuleManagement() {
        withContext(Dispatchers.Default) {
            while (isServiceRunning) {
                try {
                    // Check for module updates
                    moduleManager.checkForModuleUpdates()
                    
                    // Clean up unused modules
                    moduleManager.cleanupUnusedModules()
                    
                    delay(300000) // 5 minutes
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error in module management", e)
                    delay(600000) // Wait longer on error
                }
            }
        }
    }

    private fun monitorSystemHealth() {
        try {
            // Check memory usage
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            val maxMemory = runtime.maxMemory()
            val memoryUsage = (usedMemory.toDouble() / maxMemory.toDouble()) * 100
            
            if (memoryUsage > 80) {
                Log.w(TAG, "High memory usage detected: ${memoryUsage.toInt()}%")
                // Trigger garbage collection
                System.gc()
            }
            
            // Check if agent is still active
            val app = application as AmanApplication
            if (!app.isAgentActive()) {
                Log.i(TAG, "Agent is paused or not configured")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "System health monitoring failed", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background service for device automation"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val app = application as AmanApplication
        val status = if (app.isAgentActive()) "Active" else "Standby"
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AMAN Agent")
            .setContentText("Status: $status")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification() {
        try {
            val notification = createNotification()
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update notification", e)
        }
    }

    private fun cleanup() {
        try {
            // Cancel all coroutines
            serviceScope.cancel()
            
            // Clean up command processor
            if (::commandProcessor.isInitialized) {
                commandProcessor.cleanup()
            }
            
            // Clean up module manager
            if (::moduleManager.isInitialized) {
                moduleManager.cleanup()
            }
            
            Log.i(TAG, "Service cleanup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }

    /**
     * Process immediate command (called from SMS receiver)
     */
    fun processImmediateCommand(command: String, senderNumber: String) {
        serviceScope.launch {
            try {
                commandProcessor.processCommand(command, senderNumber)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process immediate command", e)
            }
        }
    }

    /**
     * Get service status
     */
    fun getServiceStatus(): Map<String, Any> {
        val app = application as AmanApplication
        return mapOf(
            "isRunning" to isServiceRunning,
            "isAgentActive" to app.isAgentActive(),
            "isSecurityEnhanced" to app.securityManager.isEnhancedSecurityEnabled(),
            "moduleCount" to if (::moduleManager.isInitialized) moduleManager.getLoadedModuleCount() else 0
        )
    }
}
