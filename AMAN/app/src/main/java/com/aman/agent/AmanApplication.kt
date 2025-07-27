package com.aman.agent

import android.app.Application
import android.content.Intent
import android.util.Log
import com.aman.agent.security.SecurityManager
import com.aman.agent.services.AmanForegroundService
import com.aman.agent.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * AMAN Application class - Entry point for the secure background agent
 * Initializes core security, database, and starts background services
 */
class AmanApplication : Application() {

    companion object {
        private const val TAG = "AmanApplication"
        lateinit var instance: AmanApplication
            private set
    }

    lateinit var database: AppDatabase
        private set

    lateinit var securityManager: SecurityManager
        private set

    private val applicationScope = CoroutineScope(Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize security first
        initializeSecurity()

        // Initialize database
        initializeDatabase()

        // Start core services
        startCoreServices()

        // Perform security checks
        performSecurityChecks()

        Log.i(TAG, "AMAN Agent initialized successfully")
    }

    private fun initializeSecurity() {
        securityManager = SecurityManager(this)
        
        // Initialize secure storage
        securityManager.initializeSecureStorage()
        
        // Check if this is first run
        if (securityManager.isFirstRun()) {
            Log.i(TAG, "First run detected - awaiting master number setup")
        }
    }

    private fun initializeDatabase() {
        database = AppDatabase.getDatabase(this)
    }

    private fun startCoreServices() {
        applicationScope.launch {
            try {
                // Start the main foreground service
                val serviceIntent = Intent(this@AmanApplication, AmanForegroundService::class.java)
                startForegroundService(serviceIntent)
                
                Log.i(TAG, "Core services started")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start core services", e)
            }
        }
    }

    private fun performSecurityChecks() {
        applicationScope.launch {
            try {
                // Check for debugging/tampering
                if (securityManager.isDebuggingDetected()) {
                    Log.w(TAG, "Debugging detected - enhanced security mode")
                    securityManager.enableEnhancedSecurity()
                }

                // Check for root access
                if (securityManager.isRootDetected()) {
                    Log.w(TAG, "Root access detected - monitoring mode")
                }

                // Verify app integrity
                if (!securityManager.verifyAppIntegrity()) {
                    Log.e(TAG, "App integrity check failed")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Security check failed", e)
            }
        }
    }

    /**
     * Get application instance
     */
    fun getInstance(): AmanApplication = instance

    /**
     * Check if agent is active and ready
     */
    fun isAgentActive(): Boolean {
        return securityManager.isMasterNumberSet() && !securityManager.isAgentPaused()
    }

    /**
     * Emergency shutdown - stops all services
     */
    fun emergencyShutdown() {
        try {
            val serviceIntent = Intent(this, AmanForegroundService::class.java)
            stopService(serviceIntent)
            Log.i(TAG, "Emergency shutdown completed")
        } catch (e: Exception) {
            Log.e(TAG, "Emergency shutdown failed", e)
        }
    }
}
