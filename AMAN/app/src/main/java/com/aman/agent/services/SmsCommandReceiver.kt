package com.aman.agent.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import android.util.Log
import com.aman.agent.AmanApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * SmsCommandReceiver - Handles incoming SMS messages for command processing
 * Filters messages from authorized numbers and forwards commands to the service
 */
class SmsCommandReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsCommandReceiver"
        private const val SMS_RECEIVED_ACTION = "android.provider.Telephony.SMS_RECEIVED"
        private const val COMMAND_PREFIX = "AMAN:"
        private const val SETUP_PREFIX = "AMAN_SETUP:"
    }

    private val receiverScope = CoroutineScope(Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != SMS_RECEIVED_ACTION) return

        receiverScope.launch {
            try {
                processSmsIntent(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing SMS intent", e)
            }
        }
    }

    private suspend fun processSmsIntent(context: Context, intent: Intent) {
        val bundle = intent.extras ?: return
        val pdus = bundle.get("pdus") as? Array<*> ?: return
        val format = bundle.getString("format")

        for (pdu in pdus) {
            try {
                val smsMessage = if (format != null) {
                    SmsMessage.createFromPdu(pdu as ByteArray, format)
                } else {
                    SmsMessage.createFromPdu(pdu as ByteArray)
                }

                val senderNumber = smsMessage.originatingAddress ?: continue
                val messageBody = smsMessage.messageBody ?: continue

                Log.d(TAG, "SMS received from: $senderNumber")

                // Process the message
                processMessage(context, senderNumber, messageBody)

            } catch (e: Exception) {
                Log.e(TAG, "Error parsing SMS message", e)
            }
        }
    }

    private suspend fun processMessage(context: Context, senderNumber: String, messageBody: String) {
        val app = context.applicationContext as AmanApplication

        // Check if this is a setup command (for first-time master number setup)
        if (messageBody.startsWith(SETUP_PREFIX) && app.securityManager.isFirstRun()) {
            handleSetupCommand(app, senderNumber, messageBody)
            return
        }

        // Check if this is a regular AMAN command
        if (!messageBody.startsWith(COMMAND_PREFIX)) {
            return // Not an AMAN command
        }

        // Verify sender authorization
        if (!app.securityManager.isAuthorizedNumber(senderNumber)) {
            Log.w(TAG, "Unauthorized command attempt from: $senderNumber")
            handleUnauthorizedAccess(context, senderNumber)
            return
        }

        // Check if agent is paused
        if (app.securityManager.isAgentPaused()) {
            // Only allow unpause command when paused
            val command = messageBody.substring(COMMAND_PREFIX.length).trim()
            if (!command.startsWith("unpause", ignoreCase = true)) {
                Log.i(TAG, "Agent is paused, ignoring command: $command")
                return
            }
        }

        // Extract command
        val command = messageBody.substring(COMMAND_PREFIX.length).trim()
        
        if (command.isEmpty()) {
            Log.w(TAG, "Empty command received")
            return
        }

        Log.i(TAG, "Processing command: $command from $senderNumber")

        // Forward to foreground service for processing
        forwardCommandToService(context, command, senderNumber)
    }

    private suspend fun handleSetupCommand(app: AmanApplication, senderNumber: String, messageBody: String) {
        try {
            // Extract setup command
            val setupCommand = messageBody.substring(SETUP_PREFIX.length).trim()
            
            // Expected format: "AMAN_SETUP:SET_MASTER"
            if (setupCommand.equals("SET_MASTER", ignoreCase = true)) {
                if (app.securityManager.setMasterNumber(senderNumber)) {
                    Log.i(TAG, "Master number set successfully")
                    
                    // Send confirmation SMS
                    sendSmsResponse(app, senderNumber, "AMAN Agent activated. Master number set.")
                    
                    // Start the foreground service if not already running
                    val serviceIntent = Intent(app, AmanForegroundService::class.java)
                    app.startForegroundService(serviceIntent)
                    
                } else {
                    Log.e(TAG, "Failed to set master number")
                    sendSmsResponse(app, senderNumber, "Failed to set master number.")
                }
            } else {
                Log.w(TAG, "Invalid setup command: $setupCommand")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling setup command", e)
        }
    }

    private suspend fun forwardCommandToService(context: Context, command: String, senderNumber: String) {
        try {
            // Try to get the running service instance
            val serviceIntent = Intent(context, AmanForegroundService::class.java)
            
            // Start service if not running
            context.startForegroundService(serviceIntent)
            
            // Create a custom intent to pass the command
            val commandIntent = Intent(context, AmanForegroundService::class.java).apply {
                putExtra("command", command)
                putExtra("sender", senderNumber)
                putExtra("timestamp", System.currentTimeMillis())
            }
            
            context.startForegroundService(commandIntent)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error forwarding command to service", e)
        }
    }

    private suspend fun handleUnauthorizedAccess(context: Context, senderNumber: String) {
        try {
            val app = context.applicationContext as AmanApplication
            
            // Log unauthorized access attempt
            Log.w(TAG, "Unauthorized access attempt from: $senderNumber")
            
            // If enhanced security is enabled, take additional measures
            if (app.securityManager.isEnhancedSecurityEnabled()) {
                // Could implement additional security measures here
                // For example: temporary blocking, alert to master, etc.
                
                // Alert master number about unauthorized access
                if (app.securityManager.isMasterNumberSet()) {
                    // This would be implemented in the SMS utility
                    // sendAlertToMaster(app, "Unauthorized access attempt from: $senderNumber")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling unauthorized access", e)
        }
    }

    private suspend fun sendSmsResponse(app: AmanApplication, phoneNumber: String, message: String) {
        try {
            // This would use the SMS utility to send response
            // Implementation would be in SmsManager utility class
            Log.i(TAG, "Sending SMS response to: $phoneNumber")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sending SMS response", e)
        }
    }

    /**
     * Check if message is an AMAN command
     */
    private fun isAmanCommand(messageBody: String): Boolean {
        return messageBody.startsWith(COMMAND_PREFIX) || messageBody.startsWith(SETUP_PREFIX)
    }

    /**
     * Extract command from message body
     */
    private fun extractCommand(messageBody: String): String? {
        return when {
            messageBody.startsWith(COMMAND_PREFIX) -> {
                messageBody.substring(COMMAND_PREFIX.length).trim()
            }
            messageBody.startsWith(SETUP_PREFIX) -> {
                messageBody.substring(SETUP_PREFIX.length).trim()
            }
            else -> null
        }
    }

    /**
     * Validate command format
     */
    private fun isValidCommand(command: String): Boolean {
        if (command.isEmpty()) return false
        
        // Basic validation - command should not be too long
        if (command.length > 500) return false
        
        // Add more validation rules as needed
        return true
    }
}
