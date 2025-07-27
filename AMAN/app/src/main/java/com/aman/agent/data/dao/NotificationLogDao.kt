package com.aman.agent.data.dao

import androidx.room.*
import com.aman.agent.data.entities.NotificationLog
import kotlinx.coroutines.flow.Flow

/**
 * NotificationLogDao - Data Access Object for notification logs
 */
@Dao
interface NotificationLogDao {

    @Query("SELECT * FROM notification_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<NotificationLog>>

    @Query("SELECT * FROM notification_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentLogs(limit: Int = 50): List<NotificationLog>

    @Query("SELECT * FROM notification_logs WHERE packageName = :packageName ORDER BY timestamp DESC")
    suspend fun getLogsByPackage(packageName: String): List<NotificationLog>

    @Query("SELECT * FROM notification_logs WHERE sender = :sender ORDER BY timestamp DESC")
    suspend fun getLogsBySender(sender: String): List<NotificationLog>

    @Query("SELECT * FROM notification_logs WHERE isGroupMessage = :isGroup ORDER BY timestamp DESC")
    suspend fun getLogsByGroupStatus(isGroup: Boolean): List<NotificationLog>

    @Query("SELECT * FROM notification_logs WHERE containsCommand = 1 ORDER BY timestamp DESC")
    suspend fun getLogsWithCommands(): List<NotificationLog>

    @Query("SELECT * FROM notification_logs WHERE isProcessed = :processed ORDER BY timestamp DESC")
    suspend fun getLogsByProcessedStatus(processed: Boolean): List<NotificationLog>

    @Query("SELECT * FROM notification_logs WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    suspend fun getLogsByTimeRange(startTime: Long, endTime: Long): List<NotificationLog>

    @Query("SELECT * FROM notification_logs WHERE text LIKE '%' || :searchText || '%' ORDER BY timestamp DESC")
    suspend fun searchLogs(searchText: String): List<NotificationLog>

    @Query("SELECT COUNT(*) FROM notification_logs")
    suspend fun getLogCount(): Int

    @Query("SELECT COUNT(*) FROM notification_logs WHERE containsCommand = 1")
    suspend fun getCommandNotificationCount(): Int

    @Query("SELECT COUNT(*) FROM notification_logs WHERE isGroupMessage = 1")
    suspend fun getGroupMessageCount(): Int

    @Query("SELECT COUNT(*) FROM notification_logs WHERE packageName = :packageName")
    suspend fun getCountByPackage(packageName: String): Int

    @Query("SELECT sender, COUNT(*) as count FROM notification_logs GROUP BY sender ORDER BY count DESC")
    suspend fun getSenderStatistics(): List<SenderStatistic>

    @Query("SELECT packageName, COUNT(*) as count FROM notification_logs GROUP BY packageName ORDER BY count DESC")
    suspend fun getPackageStatistics(): List<PackageStatistic>

    @Query("SELECT DATE(timestamp/1000, 'unixepoch') as date, COUNT(*) as count FROM notification_logs GROUP BY date ORDER BY date DESC LIMIT 30")
    suspend fun getDailyStatistics(): List<DailyStatistic>

    @Insert
    suspend fun insert(notificationLog: NotificationLog): Long

    @Insert
    suspend fun insertAll(notificationLogs: List<NotificationLog>): List<Long>

    @Update
    suspend fun update(notificationLog: NotificationLog)

    @Delete
    suspend fun delete(notificationLog: NotificationLog)

    @Query("DELETE FROM notification_logs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM notification_logs WHERE timestamp < :cutoffTime")
    suspend fun deleteOlderThan(cutoffTime: Long): Int

    @Query("DELETE FROM notification_logs WHERE packageName = :packageName")
    suspend fun deleteByPackage(packageName: String): Int

    @Query("DELETE FROM notification_logs")
    suspend fun deleteAll()

    @Query("DELETE FROM notification_logs WHERE id IN (SELECT id FROM notification_logs ORDER BY timestamp ASC LIMIT :count)")
    suspend fun deleteOldest(count: Int)

    @Query("UPDATE notification_logs SET isProcessed = 1 WHERE id = :id")
    suspend fun markAsProcessed(id: Long)

    @Query("UPDATE notification_logs SET containsCommand = 1, commandExtracted = :command WHERE id = :id")
    suspend fun markAsContainingCommand(id: Long, command: String)

    // Data classes for statistics
    data class SenderStatistic(
        val sender: String,
        val count: Int
    )

    data class PackageStatistic(
        val packageName: String,
        val count: Int
    )

    data class DailyStatistic(
        val date: String,
        val count: Int
    )
}
