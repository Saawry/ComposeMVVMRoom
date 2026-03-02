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

class RestoreWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val authManager = GoogleAuthManager(appContext)
    private val database = AppDatabase.getDatabase(appContext)
    private val backupRepository = BackupRepository(appContext, database, authManager)

    override suspend fun doWork(): Result {
        val driveEmail = inputData.getString("DRIVE_EMAIL") ?: return Result.failure()
        val accessToken = inputData.getString("ACCESS_TOKEN") ?: return Result.failure()

        // Set foreground info to show notification
        setForeground(createForegroundInfo("Restoring data from Google Drive..."))

        return try {
            val result = backupRepository.performRestore(driveEmail, accessToken)
            if (result.isSuccess && result.getOrNull() == true) {
                Result.success()
            } else {
                Result.failure() // Can be due to no backup found or failure
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }

    private fun createForegroundInfo(progress: String): ForegroundInfo {
        val channelId = "restore_channel"
        val title = "Data Restore"
        val cancel = "Cancel"
        
        val intent = androidx.work.WorkManager.getInstance(applicationContext)
            .createCancelPendingIntent(id)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Restore Operations",
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
            ForegroundInfo(2, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(2, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(2, notification)
        }
    }
}
