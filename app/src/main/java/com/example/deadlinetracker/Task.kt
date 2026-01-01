package com.example.deadlinetracker.model

/**
 * Model class untuk Task
 * Digunakan untuk transfer data antar layer
 */
data class Task(
    val id: Int = 0,
    val title: String,
    val description: String,
    val deadline: Long,
    val priority: Priority,
    val category: String,
    val isCompleted: Boolean = false,
    val imageUrl: String? = null,
    val firebaseId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Enum untuk Priority level
 */
enum class Priority(val value: String, val color: Int) {
    HIGH("HIGH", 0xFFFF1744.toInt()),      // Red
    MEDIUM("MEDIUM", 0xFFFF6D00.toInt()),  // Orange
    LOW("LOW", 0xFF00C853.toInt());        // Green

    companion object {
        fun fromString(value: String): Priority {
            return values().find { it.value == value } ?: MEDIUM
        }
    }
}

/**
 * Model untuk Firebase Realtime Database
 */
data class TaskFirebase(
    val id: String? = null,
    val title: String = "",
    val description: String = "",
    val deadline: Long = 0,
    val priority: String = "MEDIUM",
    val category: String = "",
    val isCompleted: Boolean = false,
    val imageUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val userId: String = "" // untuk multi-user
)