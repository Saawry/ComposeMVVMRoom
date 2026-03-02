package com.gadware.driveauthorization.ui.settings

import android.app.Activity
import android.app.PendingIntent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.gadware.driveauthorization.auth.AuthResolutionRequiredException
import com.gadware.driveauthorization.auth.GoogleAuthManager
import com.gadware.driveauthorization.data.SessionManager
import com.gadware.driveauthorization.data.UserRepository
import com.gadware.driveauthorization.worker.BackupWorker
import com.gadware.driveauthorization.worker.RestoreWorker
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class PendingAction { BACKUP, RESTORE }

data class SettingsUiState(
    val userEmail: String? = null,
    val driveEmail: String? = null,
    val lastBackupDateStr: String = "Never",
    val routineConfig: String = "Never",
    val statusText: String = "",
    val isLoading: Boolean = false,
    val showAuthResolution: Boolean = false,
    val authResolutionIntent: PendingIntent? = null,
    val pendingAction: PendingAction? = null,
    val error: String? = null
)

class SettingsViewModel(
    private val authManager: GoogleAuthManager,
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
    private val workManager: WorkManager
) : ViewModel() {

    var uiState by mutableStateOf(SettingsUiState())
        private set

    init {
        loadSettings()
    }

    private fun loadSettings() {
        val userEmail = sessionManager.getUserEmail()
        val lastTimestamp = sessionManager.getLastBackupDate()
        val routineConfig = sessionManager.getRoutineBackupConfig()

        val dateStr = if (lastTimestamp > 0) {
            SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(lastTimestamp))
        } else {
            "Never"
        }

        uiState = uiState.copy(
            userEmail = userEmail,
            lastBackupDateStr = dateStr,
            routineConfig = routineConfig,
            isLoading = true
        )

        if (userEmail != null) {
            viewModelScope.launch {
                val profile = userRepository.getUserProfile(userEmail)
                uiState = uiState.copy(
                    driveEmail = profile?.driveEmail,
                    isLoading = false
                )
            }
        } else {
            uiState = uiState.copy(isLoading = false)
        }
    }

    fun onRoutineConfigChanged(newConfig: String) {
        sessionManager.saveRoutineBackupConfig(newConfig)
        uiState = uiState.copy(routineConfig = newConfig)
        
        // Schedule Work
        scheduleRoutineBackup(newConfig)
    }

    private fun scheduleRoutineBackup(config: String) {
        val workName = "routine_backup"
        when (config) {
            "Never" -> workManager.cancelUniqueWork(workName)
            "Daily" -> {
                val request = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS).build()
                workManager.enqueueUniquePeriodicWork(workName, ExistingPeriodicWorkPolicy.UPDATE, request)
            }
            "Weekly" -> {
                val request = PeriodicWorkRequestBuilder<BackupWorker>(7, TimeUnit.DAYS).build()
                workManager.enqueueUniquePeriodicWork(workName, ExistingPeriodicWorkPolicy.UPDATE, request)
            }
            "Monthly" -> {
                val request = PeriodicWorkRequestBuilder<BackupWorker>(30, TimeUnit.DAYS).build()
                workManager.enqueueUniquePeriodicWork(workName, ExistingPeriodicWorkPolicy.UPDATE, request)
            }
        }
    }

    fun onManualBackupClicked(activity: Activity) {
        handleDriveAction(activity, PendingAction.BACKUP)
    }

    fun onManualRestoreClicked(activity: Activity) {
        handleDriveAction(activity, PendingAction.RESTORE)
    }

    private fun handleDriveAction(activity: Activity, action: PendingAction) {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, statusText = "Checking credentials...", pendingAction = action)

            val userEmail = sessionManager.getUserEmail()
            if (userEmail == null) {
                uiState = uiState.copy(isLoading = false, error = "No user logged in")
                return@launch
            }

            var driveEmail = uiState.driveEmail

            if (driveEmail.isNullOrBlank()) {
                val signInResult = authManager.signIn(activity)
                if (signInResult.isFailure) {
                    uiState = uiState.copy(isLoading = false, error = "Sign-in failed")
                    return@launch
                }
                driveEmail = signInResult.getOrThrow()
                userRepository.updateDriveEmail(userEmail, driveEmail)
                uiState = uiState.copy(driveEmail = driveEmail)
            }

            uiState = uiState.copy(statusText = "Requesting permissions...")
            val authResult = authManager.requestDriveAccess(activity, driveEmail!!)
            
            if (authResult.isFailure) {
                val exception = authResult.exceptionOrNull()
                if (exception is AuthResolutionRequiredException) {
                    uiState = uiState.copy(
                        isLoading = false,
                        statusText = "Permission required",
                        showAuthResolution = true,
                        authResolutionIntent = exception.pendingIntent
                    )
                } else {
                    uiState = uiState.copy(isLoading = false, error = "Permission failed: ${exception?.message}")
                }
                return@launch
            }

            val accessToken = authResult.getOrThrow()
            enqueueWorker(action, driveEmail, accessToken)
        }
    }

    fun onAuthResolutionResult(resultOk: Boolean, activity: Activity) {
        val action = uiState.pendingAction
        uiState = uiState.copy(showAuthResolution = false, authResolutionIntent = null)
        
        if (resultOk && action != null) {
            handleDriveAction(activity, action)
        } else {
            uiState = uiState.copy(isLoading = false, error = "Permission denied")
        }
    }

    private fun enqueueWorker(action: PendingAction, driveEmail: String, accessToken: String) {
        val inputData = Data.Builder()
            .putString("DRIVE_EMAIL", driveEmail)
            .putString("ACCESS_TOKEN", accessToken)
            .build()
            
        if (action == PendingAction.BACKUP) {
            val request = OneTimeWorkRequestBuilder<BackupWorker>()
                .setInputData(inputData)
                .build()
            workManager.enqueue(request)
            uiState = uiState.copy(isLoading = false, statusText = "Backup enqueued in background")
        } else {
            val request = OneTimeWorkRequestBuilder<RestoreWorker>()
                .setInputData(inputData)
                .build()
            workManager.enqueue(request)
            uiState = uiState.copy(isLoading = false, statusText = "Restore enqueued in background")
        }
    }
}

class SettingsViewModelFactory(
    private val authManager: GoogleAuthManager,
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
    private val workManager: WorkManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(authManager, userRepository, sessionManager, workManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
