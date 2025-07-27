package com.aman.agent.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * CommandLog - Entity for storing command execution history
 */
@Entity(tableName = "command_logs")
data class CommandLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val action: String,
    val sender: String,
    val source: String, // "SMS" or "WhatsApp"
    val parameters: String? = null, // JSON string of parameters
    val result: String? = null,
    val success: Boolean = true,
    val timestamp: Long,
    val executionTime: Long = 0, // Execution time in milliseconds
    val errorMessage: String? = null
)
