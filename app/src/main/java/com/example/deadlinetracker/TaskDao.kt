package com.example.deadlinetracker.database

import androidx.lifecycle.LiveData
import androidx.room.*

/**
 * Data Access Object (DAO) untuk operasi database
 * Berisi query-query untuk CRUD operations
 */
@Dao
interface TaskDao {

    // CREATE
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>)

    // READ
    @Query("SELECT * FROM tasks ORDER BY deadline ASC")
    fun getAllTasks(): LiveData<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :taskId")
    suspend fun getTaskById(taskId: Int): TaskEntity?

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 ORDER BY deadline ASC")
    fun getIncompleteTasks(): LiveData<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isCompleted = 1 ORDER BY deadline DESC")
    fun getCompletedTasks(): LiveData<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE priority = :priority ORDER BY deadline ASC")
    fun getTasksByPriority(priority: String): LiveData<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE category = :category ORDER BY deadline ASC")
    fun getTasksByCategory(category: String): LiveData<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE deadline BETWEEN :startDate AND :endDate ORDER BY deadline ASC")
    fun getTasksByDateRange(startDate: Long, endDate: Long): LiveData<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%'")
    fun searchTasks(query: String): LiveData<List<TaskEntity>>

    // UPDATE
    @Update
    suspend fun updateTask(task: TaskEntity)

    @Query("UPDATE tasks SET isCompleted = :isCompleted WHERE id = :taskId")
    suspend fun updateTaskStatus(taskId: Int, isCompleted: Boolean)

    // DELETE
    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteTaskById(taskId: Int)

    @Query("DELETE FROM tasks")
    suspend fun deleteAllTasks()

    @Query("DELETE FROM tasks WHERE isCompleted = 1")
    suspend fun deleteCompletedTasks()

    // COUNT
    @Query("SELECT COUNT(*) FROM tasks")
    suspend fun getTaskCount(): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE isCompleted = 0")
    suspend fun getIncompleteTaskCount(): Int
}