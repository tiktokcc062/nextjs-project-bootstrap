package com.aman.agent.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SecurityManager - Handles all security operations for AMAN agent
 * Includes master key management, anti-tampering, and secure storage
 */
class SecurityManager(private val context: Context) {

    companion object {
        private const val TAG = "SecurityManager"
        private const val PREFS_NAME = "aman_secure_prefs"
        private const val KEY_MASTER_NUMBER = "master_number"
        private const val KEY_AUTHORIZED_NUMBERS = "authorized_numbers"
        private const val KEY_AGENT_PAUSED = "agent_paused"
        private const val KEY_FIRST_RUN = "first_run"
        private const val KEY_APP_SIGNATURE = "app_signature"
        private const val KEY_ENHANCED_SECURITY = "enhanced_security"
    }

    private lateinit var securePrefs: SharedPreferences
    private lateinit var masterKey: MasterKey
    private val enhancedSecurityMode = AtomicBoolean(false)

    /**
     * Initialize secure storage using Android Keystore
     */
    fun initializeSecureStorage() {
        try {
            masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            securePrefs = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            // Store app signature for integrity verification
            storeAppSignature()

            Log.i(TAG, "Secure storage initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize secure storage", e)
            throw SecurityException("Secure storage initialization failed")
        }
    }

    /**
     * Check if this is the first run
     */
    fun isFirstRun(): Boolean {
        return securePrefs.getBoolean(KEY_FIRST_RUN, true)
    }

    /**
     * Mark first run as completed
     */
    fun markFirstRunCompleted() {
        securePrefs.edit().putBoolean(KEY_FIRST_RUN, false).apply()
    }

    /**
     * Set master number (only allowed on first run or with current master authorization)
     */
    fun setMasterNumber(phoneNumber: String): Boolean {
        return try {
            val hashedNumber = hashPhoneNumber(phoneNumber)
            securePrefs.edit().putString(KEY_MASTER_NUMBER, hashedNumber).apply()
            markFirstRunCompleted()
            Log.i(TAG, "Master number set successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set master number", e)
            false
        }
    }

    /**
     * Verify if phone number is the master number
     */
    fun isMasterNumber(phoneNumber: String): Boolean {
        val storedHash = securePrefs.getString(KEY_MASTER_NUMBER, null) ?: return false
        val inputHash = hashPhoneNumber(phoneNumber)
        return storedHash == inputHash
    }

    /**
     * Check if master number is set
     */
    fun isMasterNumberSet(): Boolean {
        return securePrefs.getString(KEY_MASTER_NUMBER, null) != null
    }

    /**
     * Add authorized number (only master can do this)
     */
    fun addAuthorizedNumber(phoneNumber: String): Boolean {
        return try {
            val currentNumbers = getAuthorizedNumbers().toMutableSet()
            currentNumbers.add(hashPhoneNumber(phoneNumber))
            saveAuthorizedNumbers(currentNumbers)
            Log.i(TAG, "Authorized number added")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add authorized number", e)
            false
        }
    }

    /**
     * Remove authorized number (only master can do this)
     */
    fun removeAuthorizedNumber(phoneNumber: String): Boolean {
        return try {
            val currentNumbers = getAuthorizedNumbers().toMutableSet()
            currentNumbers.remove(hashPhoneNumber(phoneNumber))
            saveAuthorizedNumbers(currentNumbers)
            Log.i(TAG, "Authorized number removed")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove authorized number", e)
            false
        }
    }

    /**
     * Check if phone number is authorized (master or authorized list)
     */
    fun isAuthorizedNumber(phoneNumber: String): Boolean {
        if (isMasterNumber(phoneNumber)) return true
        
        val authorizedNumbers = getAuthorizedNumbers()
        val hashedNumber = hashPhoneNumber(phoneNumber)
        return authorizedNumbers.contains(hashedNumber)
    }

    /**
     * Pause/unpause agent
     */
    fun setAgentPaused(paused: Boolean) {
        securePrefs.edit().putBoolean(KEY_AGENT_PAUSED, paused).apply()
        Log.i(TAG, "Agent ${if (paused) "paused" else "resumed"}")
    }

    /**
     * Check if agent is paused
     */
    fun isAgentPaused(): Boolean {
        return securePrefs.getBoolean(KEY_AGENT_PAUSED, false)
    }

    /**
     * Enable enhanced security mode
     */
    fun enableEnhancedSecurity() {
        enhancedSecurityMode.set(true)
        securePrefs.edit().putBoolean(KEY_ENHANCED_SECURITY, true).apply()
        Log.w(TAG, "Enhanced security mode enabled")
    }

    /**
     * Check if enhanced security is enabled
     */
    fun isEnhancedSecurityEnabled(): Boolean {
        return enhancedSecurityMode.get() || securePrefs.getBoolean(KEY_ENHANCED_SECURITY, false)
    }

    /**
     * Detect debugging/tampering attempts
     */
    fun isDebuggingDetected(): Boolean {
        return try {
            // Check for debugger
            android.os.Debug.isDebuggerConnected() ||
            // Check for emulator
            isEmulator() ||
            // Check for Xposed framework
            isXposedActive() ||
            // Check build flags
            (Build.TAGS != null && Build.TAGS.contains("test-keys"))
        } catch (e: Exception) {
            Log.w(TAG, "Debug detection check failed", e)
            true // Assume compromised if check fails
        }
    }

    /**
     * Detect root access
     */
    fun isRootDetected(): Boolean {
        return try {
            // Check for common root binaries
            val rootPaths = arrayOf(
                "/system/app/Superuser.apk",
                "/sbin/su",
                "/system/bin/su",
                "/system/xbin/su",
                "/data/local/xbin/su",
                "/data/local/bin/su",
                "/system/sd/xbin/su",
                "/system/bin/failsafe/su",
                "/data/local/su"
            )

            rootPaths.any { File(it).exists() } ||
            // Check for root management apps
            isRootAppInstalled() ||
            // Check build tags
            Build.TAGS?.contains("test-keys") == true
        } catch (e: Exception) {
            Log.w(TAG, "Root detection check failed", e)
            false
        }
    }

    /**
     * Verify app integrity
     */
    fun verifyAppIntegrity(): Boolean {
        return try {
            val currentSignature = getCurrentAppSignature()
            val storedSignature = securePrefs.getString(KEY_APP_SIGNATURE, null)
            
            if (storedSignature == null) {
                storeAppSignature()
                return true
            }
            
            currentSignature == storedSignature
        } catch (e: Exception) {
            Log.e(TAG, "App integrity verification failed", e)
            false
        }
    }

    // Private helper methods

    private fun hashPhoneNumber(phoneNumber: String): String {
        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(cleanNumber.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private fun getAuthorizedNumbers(): Set<String> {
        val numbersString = securePrefs.getString(KEY_AUTHORIZED_NUMBERS, "") ?: ""
        return if (numbersString.isEmpty()) {
            emptySet()
        } else {
            numbersString.split(",").toSet()
        }
    }

    private fun saveAuthorizedNumbers(numbers: Set<String>) {
        val numbersString = numbers.joinToString(",")
        securePrefs.edit().putString(KEY_AUTHORIZED_NUMBERS, numbersString).apply()
    }

    private fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for x86") ||
                Build.MANUFACTURER.contains("Genymotion") ||
                Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
    }

    private fun isXposedActive(): Boolean {
        return try {
            Class.forName("de.robv.android.xposed.XposedHelpers")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    private fun isRootAppInstalled(): Boolean {
        val rootApps = arrayOf(
            "com.noshufou.android.su",
            "com.noshufou.android.su.elite",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.thirdparty.superuser",
            "com.yellowes.su"
        )

        return rootApps.any { packageName ->
            try {
                context.packageManager.getPackageInfo(packageName, 0)
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun getCurrentAppSignature(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_SIGNATURES
            )
            val signature = packageInfo.signatures[0]
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(signature.toByteArray())
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get app signature", e)
            ""
        }
    }

    private fun storeAppSignature() {
        val signature = getCurrentAppSignature()
        if (signature.isNotEmpty()) {
            securePrefs.edit().putString(KEY_APP_SIGNATURE, signature).apply()
        }
    }
}
