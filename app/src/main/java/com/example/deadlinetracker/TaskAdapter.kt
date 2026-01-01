package com.example.deadlinetracker.adapter

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.deadlinetracker.R
import com.example.deadlinetracker.database.TaskEntity
import com.example.deadlinetracker.model.Priority
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter untuk RecyclerView Task List
 * Menggunakan ListAdapter dengan DiffUtil untuk efisiensi
 */
class TaskAdapter(
    private val onTaskClick: (TaskEntity) -> Unit,
    private val onTaskLongClick: (TaskEntity) -> Boolean,
    private val onCheckboxClick: (TaskEntity, Boolean) -> Unit
) : ListAdapter<TaskEntity, TaskAdapter.TaskViewHolder>(TaskDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = getItem(position)
        holder.bind(task)
    }

    inner class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: CardView = itemView.findViewById(R.id.cardViewTask)
        private val checkBox: CheckBox = itemView.findViewById(R.id.checkBoxCompleted)
        private val textTitle: TextView = itemView.findViewById(R.id.textTaskTitle)
        private val textDescription: TextView = itemView.findViewById(R.id.textTaskDescription)
        private val textDeadline: TextView = itemView.findViewById(R.id.textDeadline)
        private val textCategory: TextView = itemView.findViewById(R.id.textCategory)
        private val viewPriorityIndicator: View = itemView.findViewById(R.id.viewPriorityIndicator)
        private val imageAttachment: ImageView = itemView.findViewById(R.id.imageAttachment)
        private val iconCloud: ImageView = itemView.findViewById(R.id.iconCloud)

        fun bind(task: TaskEntity) {
            // Set data
            textTitle.text = task.title
            textDescription.text = task.description
            textCategory.text = task.category
            checkBox.isChecked = task.isCompleted

            // Set deadline
            val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
            textDeadline.text = dateFormat.format(Date(task.deadline))

            // Set priority indicator color
            val priority = Priority.fromString(task.priority)
            viewPriorityIndicator.setBackgroundColor(priority.color)

            // Show cloud icon if synced to Firebase
            iconCloud.visibility = if (task.firebaseId != null) View.VISIBLE else View.GONE

            // Show attachment icon if image exists
            if (!task.imageUrl.isNullOrEmpty()) {
                imageAttachment.visibility = View.VISIBLE
                Glide.with(itemView.context)
                    .load(task.imageUrl)
                    .placeholder(R.drawable.ic_image)
                    .into(imageAttachment)
            } else {
                imageAttachment.visibility = View.GONE
            }

            // Strike through if completed
            if (task.isCompleted) {
                textTitle.paintFlags = textTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                textDescription.paintFlags = textDescription.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                textTitle.alpha = 0.6f
                textDescription.alpha = 0.6f
            } else {
                textTitle.paintFlags = textTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                textDescription.paintFlags = textDescription.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                textTitle.alpha = 1.0f
                textDescription.alpha = 1.0f
            }

            // Check if deadline is near or passed
            val currentTime = System.currentTimeMillis()
            val timeUntilDeadline = task.deadline - currentTime
            val oneDayInMillis = 24 * 60 * 60 * 1000L

            when {
                timeUntilDeadline < 0 && !task.isCompleted -> {
                    // Deadline passed
                    cardView.setCardBackgroundColor(
                        itemView.context.getColor(R.color.deadline_passed)
                    )
                }
                timeUntilDeadline < oneDayInMillis && !task.isCompleted -> {
                    // Deadline soon (within 24 hours)
                    cardView.setCardBackgroundColor(
                        itemView.context.getColor(R.color.deadline_soon)
                    )
                }
                else -> {
                    // Normal
                    cardView.setCardBackgroundColor(
                        itemView.context.getColor(R.color.white)
                    )
                }
            }

            // Click listeners
            cardView.setOnClickListener {
                onTaskClick(task)
            }

            cardView.setOnLongClickListener {
                onTaskLongClick(task)
            }

            checkBox.setOnClickListener {
                onCheckboxClick(task, checkBox.isChecked)
            }
        }
    }

    /**
     * DiffUtil callback untuk efisiensi update RecyclerView
     */
    class TaskDiffCallback : DiffUtil.ItemCallback<TaskEntity>() {
        override fun areItemsTheSame(oldItem: TaskEntity, newItem: TaskEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TaskEntity, newItem: TaskEntity): Boolean {
            return oldItem == newItem
        }
    }
}