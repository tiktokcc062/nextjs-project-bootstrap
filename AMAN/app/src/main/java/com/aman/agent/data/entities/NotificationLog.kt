package com.aman.agent.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * NotificationLog - Entity for storing WhatsApp notification history
 */
@Entity(tableName = "notification_logs")
data class NotificationLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val packageName: String,
    val title: String,
    val text: String,
    val subText: String? = null,
    val sender: String,
    val isGroupMessage: Boolean = false,
    val timestamp: Long,
    val isProcessed: Boolean = false, // Whether this notification was processed as a command
    val containsCommand: Boolean = false, // Whether this notification contains an AMAN command
    val commandExtracted: String? = null // The command that was extracted (if any)
)
