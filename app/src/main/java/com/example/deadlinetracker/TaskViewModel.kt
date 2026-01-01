package com.example.deadlinetracker.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.deadlinetracker.database.TaskDatabase
import com.example.deadlinetracker.database.TaskEntity
import com.example.deadlinetracker.model.Quote
import com.example.deadlinetracker.repository.TaskRepository
import kotlinx.coroutines.launch

/**
 * ViewModel untuk Task operations
 * Mengikuti MVVM architecture pattern
 */
class TaskViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TaskRepository

    // LiveData observables
    val allTasks: LiveData<List<TaskEntity>>
    val incompleteTasks: LiveData<List<TaskEntity>>
    val completedTasks: LiveData<List<TaskEntity>>

    // Status LiveData
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _successMessage = MutableLiveData<String?>()
    val successMessage: LiveData<String?> = _successMessage

    private val _currentQuote = MutableLiveData<Quote?>()
    val currentQuote: LiveData<Quote?> = _currentQuote

    init {
        val taskDao = TaskDatabase.getDatabase(application).taskDao()
        repository = TaskRepository(taskDao)
        allTasks = repository.allTasks
        incompleteTasks = repository.incompleteTasks
        completedTasks = repository.completedTasks
    }

    // ==================== TASK OPERATIONS ====================

    fun insertTask(task: TaskEntity) = viewModelScope.launch {
        try {
            _isLoading.value = true
            repository.insertTask(task)
            _successMessage.value = "Task berhasil ditambahkan"
        } catch (e: Exception) {
            _errorMessage.value = "Gagal menambahkan task: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    fun updateTask(task: TaskEntity) = viewModelScope.launch {
        try {
            _isLoading.value = true
            repository.updateTask(task)
            _successMessage.value = "Task berhasil diupdate"
        } catch (e: Exception) {
            _errorMessage.value = "Gagal update task: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    fun deleteTask(task: TaskEntity) = viewModelScope.launch {
        try {
            _isLoading.value = true
            repository.deleteTask(task)

            // Hapus dari Firebase jika ada
            if (!task.firebaseId.isNullOrEmpty()) {
                repository.deleteTaskFromFirebase(task.firebaseId)
            }

            // Hapus gambar dari storage jika ada
            if (!task.imageUrl.isNullOrEmpty()) {
                repository.deleteImageFromStorage(task.imageUrl)
            }

            _successMessage.value = "Task berhasil dihapus"
        } catch (e: Exception) {
            _errorMessage.value = "Gagal menghapus task: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    fun updateTaskStatus(taskId: Int, isCompleted: Boolean) = viewModelScope.launch {
        try {
            repository.updateTaskStatus(taskId, isCompleted)
            val message = if (isCompleted) "Task selesai! 🎉" else "Task dibuka kembali"
            _successMessage.value = message
        } catch (e: Exception) {
            _errorMessage.value = "Gagal update status: ${e.message}"
        }
    }

    fun searchTasks(query: String): LiveData<List<TaskEntity>> {
        return repository.searchTasks(query)
    }

    fun getTasksByPriority(priority: String): LiveData<List<TaskEntity>> {
        return repository.getTasksByPriority(priority)
    }

    // ==================== FIREBASE OPERATIONS ====================

    fun syncTaskToFirebase(task: TaskEntity) = viewModelScope.launch {
        try {
            _isLoading.value = true
            val result = repository.syncTaskToFirebase(task)

            if (result.isSuccess) {
                _successMessage.value = "Task berhasil disinkronkan ke cloud"
            } else {
                _errorMessage.value = "Gagal sinkronisasi: ${result.exceptionOrNull()?.message}"
            }
        } catch (e: Exception) {
            _errorMessage.value = "Error sinkronisasi: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    fun loadTasksFromFirebase() = viewModelScope.launch {
        try {
            _isLoading.value = true
            val result = repository.loadTasksFromFirebase()

            if (result.isSuccess) {
                val firebaseTasks = result.getOrNull() ?: emptyList()

                // Convert dan insert ke local database
                firebaseTasks.forEach { firebaseTask ->
                    val localTask = TaskEntity(
                        title = firebaseTask.title,
                        description = firebaseTask.description,
                        deadline = firebaseTask.deadline,
                        priority = firebaseTask.priority,
                        category = firebaseTask.category,
                        isCompleted = firebaseTask.isCompleted,
                        imageUrl = firebaseTask.imageUrl,
                        firebaseId = firebaseTask.id,
                        createdAt = firebaseTask.createdAt,
                        updatedAt = firebaseTask.updatedAt
                    )
                    repository.insertTask(localTask)
                }

                _successMessage.value = "Berhasil load ${firebaseTasks.size} tasks dari cloud"
            } else {
                _errorMessage.value = "Gagal load dari cloud: ${result.exceptionOrNull()?.message}"
            }
        } catch (e: Exception) {
            _errorMessage.value = "Error loading: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    fun uploadImage(uri: Uri, taskId: String, onSuccess: (String) -> Unit) = viewModelScope.launch {
        try {
            _isLoading.value = true
            val result = repository.uploadImageToStorage(uri, taskId)

            if (result.isSuccess) {
                val imageUrl = result.getOrNull() ?: ""
                onSuccess(imageUrl)
                _successMessage.value = "Gambar berhasil diupload"
            } else {
                _errorMessage.value = "Gagal upload gambar: ${result.exceptionOrNull()?.message}"
            }
        } catch (e: Exception) {
            _errorMessage.value = "Error upload: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    // ==================== REST API OPERATIONS ====================

    fun fetchMotivationalQuote() = viewModelScope.launch {
        try {
            val result = repository.getMotivationalQuote()

            if (result.isSuccess) {
                _currentQuote.value = result.getOrNull()
            } else {
                _errorMessage.value = "Gagal mengambil quote: ${result.exceptionOrNull()?.message}"
            }
        } catch (e: Exception) {
            _errorMessage.value = "Error fetching quote: ${e.message}"
        }
    }

    // ==================== UTILITY ====================

    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }

    suspend fun getTaskById(taskId: Int): TaskEntity? {
        return repository.getTaskById(taskId)
    }
}