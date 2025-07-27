package com.aman.agent.services

import android.app.Notification
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.aman.agent.AmanApplication
import com.aman.agent.data.entities.NotificationLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * WhatsAppNotificationListener - Monitors WhatsApp notifications
 * Reads and logs WhatsApp messages for command processing and monitoring
 */
class WhatsAppNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "WhatsAppNotificationListener"
        private const val WHATSAPP_PACKAGE = "com.whatsapp"
        private const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"
    }

    private val listenerScope = CoroutineScope(Dispatchers.Default)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        
        // Only process WhatsApp notifications
        if (!isWhatsAppNotification(sbn)) return
        
        listenerScope.launch {
            try {
                processWhatsAppNotification(sbn)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing WhatsApp notification", e)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        // Handle notification removal if needed
    }

    private suspend fun processWhatsAppNotification(sbn: StatusBarNotification) {
        val app = applicationContext as AmanApplication
        
        // Check if agent is active
        if (!app.isAgentActive()) {
            return
        }

        val notification = sbn.notification
        val extras = notification.extras

        // Extract notification details
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: text
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""

        Log.d(TAG, "WhatsApp notification - Title: $title, Text: $text")

        // Create notification log entry
        val notificationLog = NotificationLog(
            packageName = sbn.packageName,
            title = title,
            text = bigText,
            subText = subText,
            timestamp = System.currentTimeMillis(),
            sender = extractSenderFromTitle(title),
            isGroupMessage = isGroupMessage(title, text)
        )

        // Save to database
        saveNotificationLog(app, notificationLog)

        // Check if this is a command from authorized number
        checkForCommands(app, notificationLog)
    }

    private fun isWhatsAppNotification(sbn: StatusBarNotification): Boolean {
        return sbn.packageName == WHATSAPP_PACKAGE || 
               sbn.packageName == WHATSAPP_BUSINESS_PACKAGE
    }

    private fun extractSenderFromTitle(title: String): String {
        // WhatsApp notification titles usually contain sender name
        // Format examples: "John Doe", "Group Name", etc.
        return title.trim()
    }

    private fun isGroupMessage(title: String, text: String): Boolean {
        // Group messages often have format "Sender: Message" in text
        // or specific indicators in title
        return text.contains(":") && !title.contains("WhatsApp")
    }

    private suspend fun saveNotificationLog(app: AmanApplication, log: NotificationLog) {
        try {
            app.database.notificationLogDao().insert(log)
            Log.d(TAG, "Notification logged: ${log.sender}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save notification log", e)
        }
    }

    private suspend fun checkForCommands(app: AmanApplication, log: NotificationLog) {
        try {
            // Check if sender is authorized (by name or phone number)
            // This would require mapping WhatsApp contacts to phone numbers
            // For now, we'll implement basic command detection
            
            val messageText = log.text.lowercase()
            
            // Look for AMAN command patterns in WhatsApp messages
            if (messageText.contains("aman:") || messageText.contains("aman_")) {
                Log.i(TAG, "Potential AMAN command detected in WhatsApp from: ${log.sender}")
                
                // Extract and validate command
                val command = extractWhatsAppCommand(log.text)
                if (command != null && isValidWhatsAppCommand(command)) {
                    // Forward to command processor
                    forwardWhatsAppCommand(app, command, log.sender)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for commands", e)
        }
    }

    private fun extractWhatsAppCommand(text: String): String? {
        return try {
            val lowerText = text.lowercase()
            when {
                lowerText.contains("aman:") -> {
                    val startIndex = lowerText.indexOf("aman:") + 5
                    text.substring(startIndex).trim()
                }
                lowerText.contains("aman_") -> {
                    val startIndex = lowerText.indexOf("aman_") + 5
                    text.substring(startIndex).trim()
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting WhatsApp command", e)
            null
        }
    }

    private fun isValidWhatsAppCommand(command: String): Boolean {
        // Basic validation for WhatsApp commands
        return command.isNotEmpty() && command.length <= 200
    }

    private suspend fun forwardWhatsAppCommand(app: AmanApplication, command: String, sender: String) {
        try {
            // Create intent to forward command to foreground service
            val commandIntent = Intent(this, AmanForegroundService::class.java).apply {
                putExtra("whatsapp_command", command)
                putExtra("whatsapp_sender", sender)
                putExtra("timestamp", System.currentTimeMillis())
            }
            
            startForegroundService(commandIntent)
            
            Log.i(TAG, "WhatsApp command forwarded: $command from $sender")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error forwarding WhatsApp command", e)
        }
    }

    /**
     * Get recent WhatsApp notifications
     */
    fun getRecentNotifications(limit: Int = 50): List<NotificationLog> {
        return try {
            val app = applicationContext as AmanApplication
            // This would be implemented with database query
            // app.database.notificationLogDao().getRecent(limit)
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting recent notifications", e)
            emptyList()
        }
    }

    /**
     * Clear old notification logs
     */
    fun clearOldLogs(olderThanDays: Int = 7) {
        listenerScope.launch {
            try {
                val app = applicationContext as AmanApplication
                val cutoffTime = System.currentTimeMillis() - (olderThanDays * 24 * 60 * 60 * 1000L)
                // app.database.notificationLogDao().deleteOlderThan(cutoffTime)
                Log.i(TAG, "Old notification logs cleared")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing old logs", e)
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "WhatsApp Notification Listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "WhatsApp Notification Listener disconnected")
    }
}
