package com.example.deadlinetracker.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity class untuk Room Database
 * Merepresentasikan tabel tasks di database
 */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val title: String,
    val description: String,
    val deadline: Long, // timestamp dalam milliseconds
    val priority: String, // "HIGH", "MEDIUM", "LOW"
    val category: String,
    val isCompleted: Boolean = false,
    val imageUrl: String? = null, // URL gambar dari Firebase Storage
    val firebaseId: String? = null, // ID dari Firebase Realtime Database
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)