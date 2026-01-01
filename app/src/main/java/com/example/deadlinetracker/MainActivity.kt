package com.example.deadlinetracker.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.deadlinetracker.R
import com.example.deadlinetracker.adapter.TaskAdapter
import com.example.deadlinetracker.database.TaskEntity
import com.example.deadlinetracker.databinding.ActivityMainBinding
import com.example.deadlinetracker.service.ReminderService
import com.example.deadlinetracker.utils.NotificationHelper
import com.example.deadlinetracker.viewmodel.TaskViewModel
import com.google.android.material.snackbar.Snackbar

/**
 * MainActivity - Main screen of the app
 * Displays list of tasks using RecyclerView
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: TaskViewModel
    private lateinit var taskAdapter: TaskAdapter
    private var currentFilter = FilterType.ALL

    // Enum untuk tipe filter
    enum class FilterType {
        ALL, INCOMPLETE, COMPLETED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup Toolbar
        setSupportActionBar(binding.toolbar)

        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[TaskViewModel::class.java]

        // Setup RecyclerView
        setupRecyclerView()

        // Setup Observers
        setupObservers()

        // Setup Click Listeners
        setupClickListeners()

        // Setup Search Functionality
        setupSearchFunctionality()

        // Create Notification Channel
        NotificationHelper.createNotificationChannel(this)

        // Start Reminder Service
        ReminderService.startService(this)

        // Fetch motivational quote
        viewModel.fetchMotivationalQuote()
    }

    private fun setupRecyclerView() {
        taskAdapter = TaskAdapter(
            onTaskClick = { task ->
                // Navigate to detail activity
                val intent = Intent(this, TaskDetailActivity::class.java)
                intent.putExtra("TASK_ID", task.id)
                startActivity(intent)
            },
            onTaskLongClick = { task ->
                // Show delete confirmation
                showDeleteConfirmation(task.id, task.title)
                true
            },
            onCheckboxClick = { task, isChecked ->
                // Update task status
                viewModel.updateTaskStatus(task.id, isChecked)
            }
        )

        binding.recyclerViewTasks.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = taskAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupObservers() {
        // Observe all tasks
        viewModel.allTasks.observe(this) { tasks ->
            updateTaskList(tasks)
        }

        // Observe loading state
        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        // Observe error messages
        viewModel.errorMessage.observe(this) { message ->
            message?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                viewModel.clearMessages()
            }
        }

        // Observe success messages
        viewModel.successMessage.observe(this) { message ->
            message?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show()
                viewModel.clearMessages()
            }
        }

        // Observe motivational quote
        viewModel.currentQuote.observe(this) { quote ->
            quote?.let {
                binding.textQuote.text = "\"${it.content}\""
                binding.textQuoteAuthor.text = "- ${it.author}"
            }
        }
    }

    private fun setupClickListeners() {
        // FAB - Add new task
        binding.fabAddTask.setOnClickListener {
            val intent = Intent(this, AddTaskActivity::class.java)
            startActivity(intent)
        }

        // Filter chips
        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                R.id.chipAll -> {
                    currentFilter = FilterType.ALL
                    applyFilterAndSearch()
                }
                R.id.chipIncomplete -> {
                    currentFilter = FilterType.INCOMPLETE
                    applyFilterAndSearch()
                }
                R.id.chipCompleted -> {
                    currentFilter = FilterType.COMPLETED
                    applyFilterAndSearch()
                }
            }
        }

        // Quote card - refresh quote
        binding.cardQuote.setOnClickListener {
            viewModel.fetchMotivationalQuote()
        }
    }

    private fun setupSearchFunctionality() {
        binding.searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // Not needed
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Perform search when text changes
                applyFilterAndSearch()
            }

            override fun afterTextChanged(s: Editable?) {
                // Not needed
            }
        })
    }

    private fun applyFilterAndSearch() {
        val searchQuery = binding.searchBar.text.toString().trim()

        viewModel.allTasks.value?.let { allTasks ->
            // Filter berdasarkan tipe
            val filteredByType = when (currentFilter) {
                FilterType.ALL -> allTasks
                FilterType.INCOMPLETE -> allTasks.filter { !it.isCompleted }
                FilterType.COMPLETED -> allTasks.filter { it.isCompleted }
            }

            // Filter berdasarkan query pencarian
            val searchResults = if (searchQuery.isEmpty()) {
                filteredByType
            } else {
                filteredByType.filter { task ->
                    task.title.contains(searchQuery, ignoreCase = true) ||
                            task.description?.contains(searchQuery, ignoreCase = true) == true ||
                            task.category?.contains(searchQuery, ignoreCase = true) == true
                }
            }

            updateTaskList(searchResults)
        }
    }

    private fun updateTaskList(tasks: List<TaskEntity>) {
        if (tasks.isEmpty()) {
            binding.recyclerViewTasks.visibility = View.GONE
            binding.layoutEmptyState.visibility = View.VISIBLE

            // Update empty state message based on search query
            val searchQuery = binding.searchBar.text.toString().trim()
            if (searchQuery.isNotEmpty()) {
                // Bisa menambahkan TextView untuk pesan pencarian tidak ditemukan
                // atau menggunakan existing empty state
            }
        } else {
            binding.recyclerViewTasks.visibility = View.VISIBLE
            binding.layoutEmptyState.visibility = View.GONE
            taskAdapter.submitList(tasks)
        }
    }

    private fun showDeleteConfirmation(taskId: Int, taskTitle: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_delete_title))
            .setMessage("Hapus task \"$taskTitle\"?")
            .setPositiveButton(getString(R.string.dialog_yes)) { _, _ ->
                viewModel.allTasks.value?.find { it.id == taskId }?.let { task ->
                    viewModel.deleteTask(task)
                }
            }
            .setNegativeButton(getString(R.string.dialog_no), null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_sync -> {
                // Sync all tasks to Firebase
                Toast.makeText(this, "Syncing to cloud...", Toast.LENGTH_SHORT).show()
                viewModel.allTasks.value?.forEach { task ->
                    viewModel.syncTaskToFirebase(task)
                }
                true
            }
            R.id.action_load_from_cloud -> {
                // Load tasks from Firebase
                viewModel.loadTasksFromFirebase()
                true
            }
            R.id.action_refresh_quote -> {
                // Refresh motivational quote
                viewModel.fetchMotivationalQuote()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh quote when returning to activity
        viewModel.fetchMotivationalQuote()
    }
}