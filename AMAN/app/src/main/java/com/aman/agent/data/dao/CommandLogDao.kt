package com.aman.agent.data.dao

import androidx.room.*
import com.aman.agent.data.entities.CommandLog
import kotlinx.coroutines.flow.Flow

/**
 * CommandLogDao - Data Access Object for command logs
 */
@Dao
interface CommandLogDao {

    @Query("SELECT * FROM command_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<CommandLog>>

    @Query("SELECT * FROM command_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentLogs(limit: Int = 50): List<CommandLog>

    @Query("SELECT * FROM command_logs WHERE sender = :sender ORDER BY timestamp DESC")
    suspend fun getLogsBySender(sender: String): List<CommandLog>

    @Query("SELECT * FROM command_logs WHERE action = :action ORDER BY timestamp DESC")
    suspend fun getLogsByAction(action: String): List<CommandLog>

    @Query("SELECT * FROM command_logs WHERE source = :source ORDER BY timestamp DESC")
    suspend fun getLogsBySource(source: String): List<CommandLog>

    @Query("SELECT * FROM command_logs WHERE success = :success ORDER BY timestamp DESC")
    suspend fun getLogsBySuccess(success: Boolean): List<CommandLog>

    @Query("SELECT * FROM command_logs WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    suspend fun getLogsByTimeRange(startTime: Long, endTime: Long): List<CommandLog>

    @Query("SELECT COUNT(*) FROM command_logs")
    suspend fun getLogCount(): Int

    @Query("SELECT COUNT(*) FROM command_logs WHERE success = 1")
    suspend fun getSuccessCount(): Int

    @Query("SELECT COUNT(*) FROM command_logs WHERE success = 0")
    suspend fun getErrorCount(): Int

    @Query("SELECT AVG(executionTime) FROM command_logs WHERE executionTime > 0")
    suspend fun getAverageExecutionTime(): Double?

    @Query("SELECT action, COUNT(*) as count FROM command_logs GROUP BY action ORDER BY count DESC")
    suspend fun getCommandStatistics(): List<CommandStatistic>

    @Query("SELECT sender, COUNT(*) as count FROM command_logs GROUP BY sender ORDER BY count DESC")
    suspend fun getSenderStatistics(): List<SenderStatistic>

    @Insert
    suspend fun insert(commandLog: CommandLog): Long

    @Insert
    suspend fun insertAll(commandLogs: List<CommandLog>): List<Long>

    @Update
    suspend fun update(commandLog: CommandLog)

    @Delete
    suspend fun delete(commandLog: CommandLog)

    @Query("DELETE FROM command_logs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM command_logs WHERE timestamp < :cutoffTime")
    suspend fun deleteOlderThan(cutoffTime: Long): Int

    @Query("DELETE FROM command_logs")
    suspend fun deleteAll()

    @Query("DELETE FROM command_logs WHERE id IN (SELECT id FROM command_logs ORDER BY timestamp ASC LIMIT :count)")
    suspend fun deleteOldest(count: Int)

    // Data classes for statistics
    data class CommandStatistic(
        val action: String,
        val count: Int
    )

    data class SenderStatistic(
        val sender: String,
        val count: Int
    )
}
