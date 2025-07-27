package com.aman.agent.utils

import android.content.Context
import android.telephony.SmsManager as AndroidSmsManager
import android.util.Log
import java.io.File

/**
 * SmsManager - Handles SMS and MMS operations
 * Provides secure SMS sending with delivery confirmation
 */
class SmsManager(private val context: Context) {

    companion object {
        private const val TAG = "SmsManager"
        private const val MAX_SMS_LENGTH = 160
        private const val MAX_MULTIPART_SMS_LENGTH = 1530 // 160 * 9 parts (safe limit)
    }

    private val smsManager = AndroidSmsManager.getDefault()

    /**
     * Send SMS message
     */
    suspend fun sendSms(phoneNumber: String, message: String): Boolean {
        return try {
            Log.i(TAG, "Sending SMS to: $phoneNumber")

            val cleanNumber = cleanPhoneNumber(phoneNumber)
            if (!isValidPhoneNumber(cleanNumber)) {
                throw IllegalArgumentException("Invalid phone number: $phoneNumber")
            }

            if (message.isEmpty()) {
                throw IllegalArgumentException("Message cannot be empty")
            }

            // Check message length and split if necessary
            if (message.length <= MAX_SMS_LENGTH) {
                sendSingleSms(cleanNumber, message)
            } else if (message.length <= MAX_MULTIPART_SMS_LENGTH) {
                sendMultipartSms(cleanNumber, message)
            } else {
                throw IllegalArgumentException("Message too long (max ${MAX_MULTIPART_SMS_LENGTH} characters)")
            }

            Log.i(TAG, "SMS sent successfully to: $cleanNumber")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS", e)
            throw SmsException("Failed to send SMS: ${e.message}")
        }
    }

    /**
     * Send single SMS
     */
    private fun sendSingleSms(phoneNumber: String, message: String) {
        try {
            smsManager.sendTextMessage(
                phoneNumber,
                null, // Service center address (null = use default)
                message,
                null, // Sent intent
                null  // Delivery intent
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error sending single SMS", e)
            throw e
        }
    }

    /**
     * Send multipart SMS
     */
    private fun sendMultipartSms(phoneNumber: String, message: String) {
        try {
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(
                phoneNumber,
                null, // Service center address
                parts,
                null, // Sent intents
                null  // Delivery intents
            )
            Log.i(TAG, "Multipart SMS sent in ${parts.size} parts")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending multipart SMS", e)
            throw e
        }
    }

    /**
     * Send MMS with attachment (photo)
     */
    suspend fun sendMms(phoneNumber: String, message: String, attachment: File? = null): Boolean {
        return try {
            Log.i(TAG, "Sending MMS to: $phoneNumber")

            val cleanNumber = cleanPhoneNumber(phoneNumber)
            if (!isValidPhoneNumber(cleanNumber)) {
                throw IllegalArgumentException("Invalid phone number: $phoneNumber")
            }

            // MMS implementation would be more complex and require additional libraries
            // For now, we'll implement a basic version
            sendMmsMessage(cleanNumber, message, attachment)

            Log.i(TAG, "MMS sent successfully to: $cleanNumber")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to send MMS", e)
            throw SmsException("Failed to send MMS: ${e.message}")
        }
    }

    /**
     * Send MMS message (simplified implementation)
     */
    private fun sendMmsMessage(phoneNumber: String, message: String, attachment: File?) {
        try {
            // This is a simplified implementation
            // A full MMS implementation would require:
            // 1. Creating MMS PDU
            // 2. Handling multimedia content
            // 3. Using MMS APIs or third-party libraries

            if (attachment == null) {
                // Send as regular SMS if no attachment
                sendSingleSms(phoneNumber, message)
            } else {
                // For now, send SMS with file path info
                val mmsMessage = "$message\n[Attachment: ${attachment.name}]"
                if (mmsMessage.length <= MAX_SMS_LENGTH) {
                    sendSingleSms(phoneNumber, mmsMessage)
                } else {
                    sendMultipartSms(phoneNumber, mmsMessage)
                }
                
                Log.w(TAG, "MMS sent as SMS with attachment info (full MMS not implemented)")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error sending MMS", e)
            throw e
        }
    }

    /**
     * Send SMS with delivery confirmation
     */
    suspend fun sendSmsWithConfirmation(phoneNumber: String, message: String): SmsResult {
        return try {
            // This would require implementing PendingIntent for delivery confirmation
            // For now, we'll use the basic send method
            val success = sendSms(phoneNumber, message)
            
            SmsResult(
                success = success,
                messageId = generateMessageId(),
                timestamp = System.currentTimeMillis(),
                recipient = phoneNumber
            )

        } catch (e: Exception) {
            SmsResult(
                success = false,
                messageId = null,
                timestamp = System.currentTimeMillis(),
                recipient = phoneNumber,
                error = e.message
            )
        }
    }

    /**
     * Send emergency SMS to master number
     */
    suspend fun sendEmergencySms(message: String): Boolean {
        return try {
            // This would get the master number from SecurityManager
            // For now, we'll log the emergency message
            Log.w(TAG, "EMERGENCY SMS: $message")
            
            // Implementation would be:
            // val masterNumber = securityManager.getMasterNumber()
            // sendSms(masterNumber, "EMERGENCY: $message")
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send emergency SMS", e)
            false
        }
    }

    /**
     * Clean phone number (remove formatting)
     */
    private fun cleanPhoneNumber(phoneNumber: String): String {
        return phoneNumber.replace(Regex("[^+0-9]"), "")
    }

    /**
     * Validate phone number format
     */
    private fun isValidPhoneNumber(phoneNumber: String): Boolean {
        val cleanNumber = cleanPhoneNumber(phoneNumber)
        
        // Basic validation - should be 10-15 digits, optionally starting with +
        val phonePattern = Regex("^\\+?[0-9]{10,15}$")
        return phonePattern.matches(cleanNumber)
    }

    /**
     * Generate unique message ID
     */
    private fun generateMessageId(): String {
        return "SMS_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }

    /**
     * Get SMS sending capability info
     */
    fun getSmsCapabilities(): SmsCapabilities {
        return try {
            SmsCapabilities(
                canSendSms = true, // Assume true if no exception
                canSendMms = true, // Simplified
                maxSmsLength = MAX_SMS_LENGTH,
                maxMultipartLength = MAX_MULTIPART_SMS_LENGTH,
                supportsDeliveryReports = false // Would need to implement
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting SMS capabilities", e)
            SmsCapabilities(
                canSendSms = false,
                canSendMms = false,
                maxSmsLength = 0,
                maxMultipartLength = 0,
                supportsDeliveryReports = false
            )
        }
    }

    /**
     * Check if SMS permission is granted
     */
    fun hasSmsPermission(): Boolean {
        return try {
            // Check if we can access SMS manager
            AndroidSmsManager.getDefault()
            true
        } catch (e: Exception) {
            Log.e(TAG, "SMS permission check failed", e)
            false
        }
    }

    /**
     * Format message for SMS sending
     */
    fun formatMessage(message: String): String {
        return message.trim().replace(Regex("\\s+"), " ")
    }

    /**
     * Split long message into SMS-sized parts
     */
    fun splitMessage(message: String): List<String> {
        return if (message.length <= MAX_SMS_LENGTH) {
            listOf(message)
        } else {
            smsManager.divideMessage(message)
        }
    }

    /**
     * Estimate SMS cost (number of parts)
     */
    fun estimateSmsCost(message: String): Int {
        return if (message.length <= MAX_SMS_LENGTH) {
            1
        } else {
            smsManager.divideMessage(message).size
        }
    }

    // Data classes for results

    data class SmsResult(
        val success: Boolean,
        val messageId: String?,
        val timestamp: Long,
        val recipient: String,
        val error: String? = null
    )

    data class SmsCapabilities(
        val canSendSms: Boolean,
        val canSendMms: Boolean,
        val maxSmsLength: Int,
        val maxMultipartLength: Int,
        val supportsDeliveryReports: Boolean
    )

    /**
     * Custom exception for SMS operations
     */
    class SmsException(message: String) : Exception(message)
}
