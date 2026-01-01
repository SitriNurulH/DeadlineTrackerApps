package com.example.deadlinetracker.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room Database class
 * Singleton pattern untuk memastikan hanya ada satu instance database
 */
@Database(
    entities = [TaskEntity::class],
    version = 1,
    exportSchema = false
)
abstract class TaskDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile
        private var INSTANCE: TaskDatabase? = null

        fun getDatabase(context: Context): TaskDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TaskDatabase::class.java,
                    "deadline_tracker_database"
                )
                    .fallbackToDestructiveMigration() // Hapus data saat schema berubah
                    .build()
                INSTANCE = instance
                instance
            }
        }

        // Fungsi untuk testing - hapus database
        fun destroyInstance() {
            INSTANCE = null
        }
    }
}