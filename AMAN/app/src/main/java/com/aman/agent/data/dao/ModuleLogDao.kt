package com.aman.agent.data.dao

import androidx.room.*
import com.aman.agent.data.entities.ModuleLog
import kotlinx.coroutines.flow.Flow

/**
 * ModuleLogDao - Data Access Object for module logs
 */
@Dao
interface ModuleLogDao {

    @Query("SELECT * FROM module_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<ModuleLog>>

    @Query("SELECT * FROM module_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentLogs(limit: Int = 50): List<ModuleLog>

    @Query("SELECT * FROM module_logs WHERE moduleName = :moduleName ORDER BY timestamp DESC")
    suspend fun getLogsByModule(moduleName: String): List<ModuleLog>

    @Query("SELECT * FROM module_logs WHERE action = :action ORDER BY timestamp DESC")
    suspend fun getLogsByAction(action: String): List<ModuleLog>

    @Query("SELECT * FROM module_logs WHERE success = :success ORDER BY timestamp DESC")
    suspend fun getLogsBySuccess(success: Boolean): List<ModuleLog>

    @Query("SELECT * FROM module_logs WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    suspend fun getLogsByTimeRange(startTime: Long, endTime: Long): List<ModuleLog>

    @Query("SELECT * FROM module_logs WHERE moduleName = :moduleName AND action = 'LOADED' ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestLoadLog(moduleName: String): ModuleLog?

    @Query("SELECT * FROM module_logs WHERE action = 'ERROR' ORDER BY timestamp DESC")
    suspend fun getErrorLogs(): List<ModuleLog>

    @Query("SELECT COUNT(*) FROM module_logs")
    suspend fun getLogCount(): Int

    @Query("SELECT COUNT(*) FROM module_logs WHERE success = 1")
    suspend fun getSuccessCount(): Int

    @Query("SELECT COUNT(*) FROM module_logs WHERE success = 0")
    suspend fun getErrorCount(): Int

    @Query("SELECT COUNT(*) FROM module_logs WHERE action = 'LOADED'")
    suspend fun getLoadedModuleCount(): Int

    @Query("SELECT COUNT(*) FROM module_logs WHERE action = 'COMMAND_EXECUTED'")
    suspend fun getCommandExecutionCount(): Int

    @Query("SELECT AVG(executionTime) FROM module_logs WHERE executionTime > 0")
    suspend fun getAverageExecutionTime(): Double?

    @Query("SELECT AVG(memoryUsage) FROM module_logs WHERE memoryUsage > 0")
    suspend fun getAverageMemoryUsage(): Double?

    @Query("SELECT moduleName, COUNT(*) as count FROM module_logs WHERE action = 'COMMAND_EXECUTED' GROUP BY moduleName ORDER BY count DESC")
    suspend fun getModuleUsageStatistics(): List<ModuleUsageStatistic>

    @Query("SELECT action, COUNT(*) as count FROM module_logs GROUP BY action ORDER BY count DESC")
    suspend fun getActionStatistics(): List<ActionStatistic>

    @Query("SELECT moduleName, moduleVersion, MAX(timestamp) as lastUsed FROM module_logs WHERE action = 'COMMAND_EXECUTED' GROUP BY moduleName ORDER BY lastUsed DESC")
    suspend fun getModuleLastUsed(): List<ModuleLastUsed>

    @Query("SELECT moduleName, SUM(memoryUsage) as totalMemory FROM module_logs WHERE memoryUsage > 0 GROUP BY moduleName ORDER BY totalMemory DESC")
    suspend fun getModuleMemoryUsage(): List<ModuleMemoryUsage>

    @Insert
    suspend fun insert(moduleLog: ModuleLog): Long

    @Insert
    suspend fun insertAll(moduleLogs: List<ModuleLog>): List<Long>

    @Update
    suspend fun update(moduleLog: ModuleLog)

    @Delete
    suspend fun delete(moduleLog: ModuleLog)

    @Query("DELETE FROM module_logs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM module_logs WHERE moduleName = :moduleName")
    suspend fun deleteByModule(moduleName: String): Int

    @Query("DELETE FROM module_logs WHERE timestamp < :cutoffTime")
    suspend fun deleteOlderThan(cutoffTime: Long): Int

    @Query("DELETE FROM module_logs WHERE action = :action")
    suspend fun deleteByAction(action: String): Int

    @Query("DELETE FROM module_logs")
    suspend fun deleteAll()

    @Query("DELETE FROM module_logs WHERE id IN (SELECT id FROM module_logs ORDER BY timestamp ASC LIMIT :count)")
    suspend fun deleteOldest(count: Int)

    // Data classes for statistics
    data class ModuleUsageStatistic(
        val moduleName: String,
        val count: Int
    )

    data class ActionStatistic(
        val action: String,
        val count: Int
    )

    data class ModuleLastUsed(
        val moduleName: String,
        val moduleVersion: String,
        val lastUsed: Long
    )

    data class ModuleMemoryUsage(
        val moduleName: String,
        val totalMemory: Long
    )
}
