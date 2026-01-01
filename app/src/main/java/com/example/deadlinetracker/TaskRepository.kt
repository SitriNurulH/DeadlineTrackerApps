package com.example.deadlinetracker.repository

import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.deadlinetracker.database.TaskDao
import com.example.deadlinetracker.database.TaskEntity
import com.example.deadlinetracker.model.Quote
import com.example.deadlinetracker.model.TaskFirebase
import com.example.deadlinetracker.network.RetrofitClient
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Repository class - Single source of truth
 * Mengelola data dari Room Database, Firebase, dan REST API
 */
class TaskRepository(private val taskDao: TaskDao) {

    private val TAG = "TaskRepository"

    // Firebase references
    private val firebaseDatabase: DatabaseReference = FirebaseDatabase.getInstance().reference.child("tasks")
    private val firebaseStorage: FirebaseStorage = FirebaseStorage.getInstance()

    // LiveData dari Room
    val allTasks: LiveData<List<TaskEntity>> = taskDao.getAllTasks()
    val incompleteTasks: LiveData<List<TaskEntity>> = taskDao.getIncompleteTasks()
    val completedTasks: LiveData<List<TaskEntity>> = taskDao.getCompletedTasks()

    // ==================== LOCAL DATABASE OPERATIONS ====================

    suspend fun insertTask(task: TaskEntity): Long {
        return withContext(Dispatchers.IO) {
            taskDao.insertTask(task)
        }
    }

    suspend fun updateTask(task: TaskEntity) {
        withContext(Dispatchers.IO) {
            taskDao.updateTask(task)
        }
    }

    suspend fun deleteTask(task: TaskEntity) {
        withContext(Dispatchers.IO) {
            taskDao.deleteTask(task)
        }
    }

    suspend fun getTaskById(taskId: Int): TaskEntity? {
        return withContext(Dispatchers.IO) {
            taskDao.getTaskById(taskId)
        }
    }

    suspend fun updateTaskStatus(taskId: Int, isCompleted: Boolean) {
        withContext(Dispatchers.IO) {
            taskDao.updateTaskStatus(taskId, isCompleted)
        }
    }

    fun searchTasks(query: String): LiveData<List<TaskEntity>> {
        return taskDao.searchTasks(query)
    }

    fun getTasksByPriority(priority: String): LiveData<List<TaskEntity>> {
        return taskDao.getTasksByPriority(priority)
    }

    // ==================== FIREBASE OPERATIONS ====================

    /**
     * Sync task to Firebase Realtime Database
     */
    suspend fun syncTaskToFirebase(task: TaskEntity): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val taskFirebase = TaskFirebase(
                    id = task.firebaseId,
                    title = task.title,
                    description = task.description,
                    deadline = task.deadline,
                    priority = task.priority,
                    category = task.category,
                    isCompleted = task.isCompleted,
                    imageUrl = task.imageUrl,
                    createdAt = task.createdAt,
                    updatedAt = System.currentTimeMillis()
                )

                val firebaseId = if (task.firebaseId.isNullOrEmpty()) {
                    firebaseDatabase.push().key ?: throw Exception("Failed to generate Firebase ID")
                } else {
                    task.firebaseId
                }

                firebaseDatabase.child(firebaseId).setValue(taskFirebase).await()

                // Update local database dengan firebaseId
                if (task.firebaseId.isNullOrEmpty()) {
                    val updatedTask = task.copy(firebaseId = firebaseId)
                    taskDao.updateTask(updatedTask)
                }

                Log.d(TAG, "Task synced to Firebase: $firebaseId")
                Result.success(firebaseId)
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing to Firebase", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Load tasks from Firebase
     */
    suspend fun loadTasksFromFirebase(): Result<List<TaskFirebase>> {
        return withContext(Dispatchers.IO) {
            try {
                val snapshot = firebaseDatabase.get().await()
                val tasks = mutableListOf<TaskFirebase>()

                snapshot.children.forEach { child ->
                    child.getValue(TaskFirebase::class.java)?.let { task ->
                        tasks.add(task.copy(id = child.key))
                    }
                }

                Log.d(TAG, "Loaded ${tasks.size} tasks from Firebase")
                Result.success(tasks)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading from Firebase", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Delete task from Firebase
     */
    suspend fun deleteTaskFromFirebase(firebaseId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                firebaseDatabase.child(firebaseId).removeValue().await()
                Log.d(TAG, "Task deleted from Firebase: $firebaseId")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting from Firebase", e)
                Result.failure(e)
            }
        }
    }

    // ==================== FIREBASE STORAGE OPERATIONS ====================

    /**
     * Upload image to Firebase Storage
     */
    suspend fun uploadImageToStorage(uri: Uri, taskId: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val storageRef = firebaseStorage.reference.child("task_images/$taskId/${System.currentTimeMillis()}.jpg")
                val uploadTask = storageRef.putFile(uri).await()
                val downloadUrl = storageRef.downloadUrl.await()

                Log.d(TAG, "Image uploaded: ${downloadUrl.toString()}")
                Result.success(downloadUrl.toString())
            } catch (e: Exception) {
                Log.e(TAG, "Error uploading image", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Delete image from Firebase Storage
     */
    suspend fun deleteImageFromStorage(imageUrl: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val storageRef = firebaseStorage.getReferenceFromUrl(imageUrl)
                storageRef.delete().await()
                Log.d(TAG, "Image deleted from storage")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting image", e)
                Result.failure(e)
            }
        }
    }

    // ==================== REST API OPERATIONS ====================

    /**
     * Get motivational quote from API
     */
    suspend fun getMotivationalQuote(): Result<Quote> {
        return withContext(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.getRandomQuoteByTags("inspirational,motivational")
                if (response.isSuccessful && response.body() != null) {
                    Log.d(TAG, "Quote fetched: ${response.body()?.content}")
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Failed to fetch quote: ${response.code()}"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching quote", e)
                Result.failure(e)
            }
        }
    }

    // ==================== SYNC OPERATIONS ====================

    /**
     * Full sync: Upload all local tasks to Firebase
     */
    suspend fun syncAllToFirebase(): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                val tasks = taskDao.getAllTasks().value ?: emptyList()
                var syncCount = 0

                tasks.forEach { task ->
                    val result = syncTaskToFirebase(task)
                    if (result.isSuccess) syncCount++
                }

                Log.d(TAG, "Synced $syncCount tasks to Firebase")
                Result.success(syncCount)
            } catch (e: Exception) {
                Log.e(TAG, "Error in full sync", e)
                Result.failure(e)
            }
        }
    }
}