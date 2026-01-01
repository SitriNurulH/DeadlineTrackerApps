package com.example.deadlinetracker.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.example.deadlinetracker.R
import com.example.deadlinetracker.database.TaskEntity
import com.example.deadlinetracker.databinding.ActivityAddTaskBinding
import com.example.deadlinetracker.model.Priority
import com.example.deadlinetracker.viewmodel.TaskViewModel
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.*

/**
 * AddTaskActivity - Screen untuk menambah atau edit task
 */
class AddTaskActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddTaskBinding
    private lateinit var viewModel: TaskViewModel

    private var selectedDeadline: Long = 0
    private var selectedImageUri: Uri? = null
    private var uploadedImageUrl: String? = null
    private var isEditMode = false
    private var editTaskId = 0

    private val calendar = Calendar.getInstance()

    // Image picker launcher
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                displayImage(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddTaskBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup Toolbar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Tambah Task Baru"

        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[TaskViewModel::class.java]

        // Check if edit mode
        intent.getIntExtra("TASK_ID", 0).let { taskId ->
            if (taskId != 0) {
                isEditMode = true
                editTaskId = taskId
                supportActionBar?.title = "Edit Task"
                loadTaskData(taskId)
            }
        }

        // Setup Click Listeners
        setupClickListeners()

        // Setup Observers
        setupObservers()
    }

    private fun setupClickListeners() {
        // Date Picker
        binding.buttonSelectDate.setOnClickListener {
            showDatePicker()
        }

        // Time Picker
        binding.buttonSelectTime.setOnClickListener {
            showTimePicker()
        }

        // Image Upload
        binding.buttonUploadImage.setOnClickListener {
            openImagePicker()
        }

        // Save Button
        binding.buttonSave.setOnClickListener {
            saveTask()
        }

        // Cancel Button
        binding.buttonCancel.setOnClickListener {
            finish()
        }
    }

    private fun setupObservers() {
        // Observe loading state
        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.buttonSave.isEnabled = !isLoading
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
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                viewModel.clearMessages()
                finish() // Close activity after success
            }
        }
    }

    private fun showDatePicker() {
        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                updateDeadlineDisplay()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.datePicker.minDate = System.currentTimeMillis()
        datePickerDialog.show()
    }

    private fun showTimePicker() {
        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                calendar.set(Calendar.MINUTE, minute)
                selectedDeadline = calendar.timeInMillis
                updateDeadlineDisplay()
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun updateDeadlineDisplay() {
        if (selectedDeadline == 0L) {
            selectedDeadline = calendar.timeInMillis
        }
        val dateFormat = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale("id", "ID"))
        binding.textSelectedDeadline.text = dateFormat.format(Date(selectedDeadline))
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        imagePickerLauncher.launch(intent)
    }

    private fun displayImage(uri: Uri) {
        binding.imagePreview.visibility = View.VISIBLE
        Glide.with(this)
            .load(uri)
            .into(binding.imagePreview)
    }

    private fun saveTask() {
        // Validate input
        val title = binding.editTextTitle.text.toString().trim()
        val description = binding.editTextDescription.text.toString().trim()
        val category = binding.editTextCategory.text.toString().trim()

        if (title.isEmpty()) {
            binding.tilTitle.error = "Judul tidak boleh kosong"
            return
        }

        if (description.isEmpty()) {
            binding.tilDescription.error = "Deskripsi tidak boleh kosong"
            return
        }

        if (category.isEmpty()) {
            binding.tilCategory.error = "Kategori tidak boleh kosong"
            return
        }

        if (selectedDeadline == 0L) {
            Toast.makeText(this, "Pilih deadline terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }

        // Get priority
        val priority = when (binding.radioGroupPriority.checkedRadioButtonId) {
            R.id.radioPriorityHigh -> Priority.HIGH.value
            R.id.radioPriorityLow -> Priority.LOW.value
            else -> Priority.MEDIUM.value
        }

        // Upload image if selected
        if (selectedImageUri != null && uploadedImageUrl == null) {
            val tempTaskId = if (isEditMode) editTaskId.toString() else UUID.randomUUID().toString()
            viewModel.uploadImage(selectedImageUri!!, tempTaskId) { imageUrl ->
                uploadedImageUrl = imageUrl
                createAndSaveTask(title, description, category, priority, imageUrl)
            }
        } else {
            createAndSaveTask(title, description, category, priority, uploadedImageUrl)
        }
    }

    private fun createAndSaveTask(
        title: String,
        description: String,
        category: String,
        priority: String,
        imageUrl: String?
    ) {
        val task = TaskEntity(
            id = if (isEditMode) editTaskId else 0,
            title = title,
            description = description,
            deadline = selectedDeadline,
            priority = priority,
            category = category,
            imageUrl = imageUrl,
            updatedAt = System.currentTimeMillis()
        )

        if (isEditMode) {
            viewModel.updateTask(task)
        } else {
            viewModel.insertTask(task)
        }

        // Sync to Firebase
        viewModel.syncTaskToFirebase(task)
    }

    private fun loadTaskData(taskId: Int) {
        // TODO: Load task from ViewModel
        // This would require adding a method to ViewModel to get single task
        // For now, this is a placeholder
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}