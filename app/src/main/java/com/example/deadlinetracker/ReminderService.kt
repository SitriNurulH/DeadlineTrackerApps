package com.example.deadlinetracker.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.deadlinetracker.R
import com.example.deadlinetracker.database.TaskDatabase
import com.example.deadlinetracker.ui.MainActivity
import kotlinx.coroutines.*
import java.util.*

/**
 * Service untuk menjalankan reminder di background
 * Mengecek deadline tasks dan mengirim notifikasi
 */
class ReminderService : Service() {

    private val TAG = "ReminderService"
    private val CHANNEL_ID = "deadline_reminder_channel"
    private val NOTIFICATION_ID = 1001

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var reminderJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")

        // Start sebagai foreground service
        startForeground(NOTIFICATION_ID, createForegroundNotification())

        // Start reminder checker
        startReminderChecker()

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        reminderJob?.cancel()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    /**
     * Membuat notification channel untuk Android O+
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Deadline Reminders"
            val descriptionText = "Notifikasi reminder untuk deadline tugas"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Membuat foreground notification
     */
    private fun createForegroundNotification(): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Deadline Tracker")
            .setContentText("Monitoring deadline tugas Anda...")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * Memulai periodic checker untuk reminder
     */
    private fun startReminderChecker() {
        reminderJob = serviceScope.launch {
            while (isActive) {
                try {
                    checkDeadlines()
                    delay(60 * 60 * 1000L) // Check setiap 1 jam
                } catch (e: Exception) {
                    Log.e(TAG, "Error in reminder checker", e)
                }
            }
        }
    }

    /**
     * Mengecek deadlines dan mengirim notifikasi
     */
    private suspend fun checkDeadlines() {
        withContext(Dispatchers.IO) {
            try {
                val taskDao = TaskDatabase.getDatabase(applicationContext).taskDao()
                val tasks = taskDao.getAllTasks().value ?: emptyList()

                val currentTime = System.currentTimeMillis()
                val oneDayInMillis = 24 * 60 * 60 * 1000L

                tasks.filter { !it.isCompleted }.forEach { task ->
                    val timeUntilDeadline = task.deadline - currentTime

                    when {
                        // Deadline sudah lewat
                        timeUntilDeadline < 0 -> {
                            sendNotification(
                                "⚠️ Deadline Terlewat!",
                                "${task.title} - sudah melewati deadline!",
                                task.id
                            )
                        }
                        // Deadline dalam 1 hari
                        timeUntilDeadline < oneDayInMillis -> {
                            val hoursLeft = (timeUntilDeadline / (60 * 60 * 1000)).toInt()
                            sendNotification(
                                "🔔 Deadline Mendekat!",
                                "${task.title} - ${hoursLeft} jam lagi!",
                                task.id
                            )
                        }
                        // Deadline dalam 3 hari
                        timeUntilDeadline < (3 * oneDayInMillis) -> {
                            val daysLeft = (timeUntilDeadline / oneDayInMillis).toInt()
                            sendNotification(
                                "📅 Reminder",
                                "${task.title} - ${daysLeft} hari lagi",
                                task.id
                            )
                        }
                    }
                }

                Log.d(TAG, "Checked ${tasks.size} tasks for deadlines")
            } catch (e: Exception) {
                Log.e(TAG, "Error checking deadlines", e)
            }
        }
    }

    /**
     * Mengirim notification
     */
    private fun sendNotification(title: String, message: String, taskId: Int) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("TASK_ID", taskId)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            taskId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(taskId + 1000, notification)

        Log.d(TAG, "Notification sent: $title - $message")
    }

    companion object {
        /**
         * Start service
         */
        fun startService(context: Context) {
            val intent = Intent(context, ReminderService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Stop service
         */
        fun stopService(context: Context) {
            val intent = Intent(context, ReminderService::class.java)
            context.stopService(intent)
        }
    }
}