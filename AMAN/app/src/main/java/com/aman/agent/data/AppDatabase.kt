package com.aman.agent.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.aman.agent.data.dao.CommandLogDao
import com.aman.agent.data.dao.NotificationLogDao
import com.aman.agent.data.dao.ModuleLogDao
import com.aman.agent.data.entities.CommandLog
import com.aman.agent.data.entities.NotificationLog
import com.aman.agent.data.entities.ModuleLog
import com.aman.agent.data.converters.Converters

/**
 * AppDatabase - Room database for AMAN agent
 * Stores command logs, notification logs, and module information
 */
@Database(
    entities = [
        CommandLog::class,
        NotificationLog::class,
        ModuleLog::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun commandLogDao(): CommandLogDao
    abstract fun notificationLogDao(): NotificationLogDao
    abstract fun moduleLogDao(): ModuleLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        private const val DATABASE_NAME = "aman_database"

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Close database instance
         */
        fun closeDatabase() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
