package com.aman.agent.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * ModuleLog - Entity for storing module loading and execution history
 */
@Entity(tableName = "module_logs")
data class ModuleLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val moduleName: String,
    val moduleVersion: String,
    val action: String, // "LOADED", "UNLOADED", "COMMAND_EXECUTED", "ERROR"
    val details: String? = null,
    val timestamp: Long,
    val success: Boolean = true,
    val errorMessage: String? = null,
    val executionTime: Long = 0, // For command executions
    val memoryUsage: Long = 0, // Memory usage in bytes
    val checksum: String? = null // Module file checksum for integrity verification
)
