package com.gadware.driveauthorization.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.gadware.driveauthorization.R
import com.gadware.driveauthorization.auth.GoogleAuthManager
import com.gadware.driveauthorization.data.BackupRepository
import com.gadware.driveauthorization.data.SessionManager
import com.gadware.driveauthorization.room.AppDatabase

class BackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val authManager = GoogleAuthManager(appContext)
    private val database = AppDatabase.getDatabase(appContext)
    private val backupRepository = BackupRepository(appContext, database, authManager)
    private val sessionManager = SessionManager(appContext)

    override suspend fun doWork(): Result {
        val driveEmail = inputData.getString("DRIVE_EMAIL") ?: return Result.failure()
        val accessToken = inputData.getString("ACCESS_TOKEN") ?: return Result.failure()

        val dbFile = applicationContext.getDatabasePath("name_db")
        val isLargeDb = dbFile.exists() && dbFile.length() > 5 * 1024 * 1024 // 5MB

        // Set foreground info to show notification if DB is large
        if (isLargeDb) {
            setForeground(createForegroundInfo("Backing up large data to Google Drive..."))
        }

        return try {
            val result = backupRepository.performBackup(driveEmail, accessToken)
            if (result.isSuccess) {
                // Save last backup date
                sessionManager.saveLastBackupDate(System.currentTimeMillis())
                Result.success()
            } else {
                Result.failure()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }

    private fun createForegroundInfo(progress: String): ForegroundInfo {
        val channelId = "backup_channel"
        val title = "Data Backup"
        val cancel = "Cancel"
        
        val intent = androidx.work.WorkManager.getInstance(applicationContext)
            .createCancelPendingIntent(id)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Backup Operations",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setTicker(title)
            .setContentText(progress)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, cancel, intent)
            .build()
            
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(1, notification)
        }
    }
}
