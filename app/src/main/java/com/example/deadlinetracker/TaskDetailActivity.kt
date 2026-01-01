package com.example.deadlinetracker.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.deadlinetracker.R
import com.example.deadlinetracker.database.TaskEntity
import com.example.deadlinetracker.model.Priority
import com.example.deadlinetracker.viewmodel.TaskViewModel
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.example.deadlinetracker.databinding.ActivityTaskDetailBinding

/**
 * TaskDetailActivity - Detail view of a task
 */
class TaskDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTaskDetailBinding
    private lateinit var viewModel: TaskViewModel
    private var taskId: Int = 0
    private var currentTask: TaskEntity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTaskDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup Toolbar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Detail Task"

        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[TaskViewModel::class.java]

        // Get task ID from intent
        taskId = intent.getIntExtra("TASK_ID", 0)
        if (taskId == 0) {
            Toast.makeText(this, "Error: Task not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Load task data
        loadTaskData()

        // Setup observers
        setupObservers()
    }

    private fun loadTaskData() {
        lifecycleScope.launch {
            val task = viewModel.getTaskById(taskId)
            task?.let {
                currentTask = it
                displayTaskData(it)
            } ?: run {
                Toast.makeText(this@TaskDetailActivity, "Task tidak ditemukan", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun displayTaskData(task: TaskEntity) {
        // Set title and description
        binding.textTaskTitle.text = task.title
        binding.textTaskDescription.text = task.description

        // Set deadline
        val dateFormat = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale("id", "ID"))
        binding.textDeadline.text = dateFormat.format(Date(task.deadline))

        // Set category
        binding.chipCategory.text = task.category

        // Set priority
        val priority = Priority.fromString(task.priority)
        binding.chipPriority.text = priority.value
        binding.chipPriority.chipBackgroundColor = android.content.res.ColorStateList.valueOf(priority.color)

        // Set status
        if (task.isCompleted) {
            binding.chipStatus.text = "Selesai ✓"
            binding.chipStatus.chipBackgroundColor = getColorStateList(R.color.success)
        } else {
            binding.chipStatus.text = "Belum Selesai"
            binding.chipStatus.chipBackgroundColor = getColorStateList(R.color.warning)
        }

        // Set cloud sync indicator
        if (task.firebaseId != null) {
            binding.iconCloud.visibility = View.VISIBLE
        }

        // Display image if exists
        if (!task.imageUrl.isNullOrEmpty()) {
            binding.imageTask.visibility = View.VISIBLE
            Glide.with(this)
                .load(task.imageUrl)
                .placeholder(R.drawable.ic_image)
                .into(binding.imageTask)
        } else {
            binding.imageTask.visibility = View.GONE
        }

        // Check deadline status
        checkDeadlineStatus(task)
    }

    private fun checkDeadlineStatus(task: TaskEntity) {
        val currentTime = System.currentTimeMillis()
        val timeUntilDeadline = task.deadline - currentTime
        val oneDayInMillis = 24 * 60 * 60 * 1000L

        when {
            task.isCompleted -> {
                binding.cardDeadlineWarning.visibility = View.GONE
            }
            timeUntilDeadline < 0 -> {
                binding.cardDeadlineWarning.visibility = View.VISIBLE
                binding.cardDeadlineWarning.setCardBackgroundColor(getColor(R.color.error))
                binding.textDeadlineWarning.text = "⚠️ Deadline sudah terlewat!"
            }
            timeUntilDeadline < oneDayInMillis -> {
                binding.cardDeadlineWarning.visibility = View.VISIBLE
                binding.cardDeadlineWarning.setCardBackgroundColor(getColor(R.color.warning))
                val hoursLeft = (timeUntilDeadline / (60 * 60 * 1000)).toInt()
                binding.textDeadlineWarning.text = "🔔 Deadline dalam $hoursLeft jam!"
            }
            else -> {
                binding.cardDeadlineWarning.visibility = View.GONE
            }
        }
    }

    private fun setupObservers() {
        viewModel.successMessage.observe(this) { message ->
            message?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                viewModel.clearMessages()
            }
        }

        viewModel.errorMessage.observe(this) { message ->
            message?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                viewModel.clearMessages()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_task_detail, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_edit -> {
                // Edit task
                val intent = Intent(this, AddTaskActivity::class.java)
                intent.putExtra("TASK_ID", taskId)
                startActivity(intent)
                true
            }
            R.id.action_delete -> {
                // Delete task
                showDeleteConfirmation()
                true
            }
            R.id.action_toggle_status -> {
                // Toggle completion status
                currentTask?.let { task ->
                    viewModel.updateTaskStatus(taskId, !task.isCompleted)
                    loadTaskData()
                }
                true
            }
            R.id.action_sync_to_cloud -> {
                // Sync to Firebase
                currentTask?.let { task ->
                    viewModel.syncTaskToFirebase(task)
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showDeleteConfirmation() {
        currentTask?.let { task ->
            AlertDialog.Builder(this)
                .setTitle("Hapus Task?")
                .setMessage("Apakah Anda yakin ingin menghapus \"${task.title}\"?")
                .setPositiveButton("Ya") { _, _ ->
                    viewModel.deleteTask(task)
                    finish()
                }
                .setNegativeButton("Tidak", null)
                .show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onResume() {
        super.onResume()
        // Reload task data when returning
        loadTaskData()
    }
}