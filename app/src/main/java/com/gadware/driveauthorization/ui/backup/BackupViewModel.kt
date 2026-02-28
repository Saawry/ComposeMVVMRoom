package com.gadware.driveauthorization.ui.backup

import android.app.Activity
import android.app.PendingIntent
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gadware.driveauthorization.auth.AuthResolutionRequiredException
import com.gadware.driveauthorization.auth.GoogleAuthManager
import com.gadware.driveauthorization.data.BackupRepository
import kotlinx.coroutines.launch

enum class PendingAction { BACKUP, RESTORE }

data class BackupUiState(
    val isBackingUp: Boolean = false,
    val statusText: String = "Idle",
    val error: String? = null,
    val success: Boolean = false,
    val isRestoring: Boolean = false,
    val restoreStatusText: String = "Idle",
    val restoreError: String? = null,
    val restoreSuccess: Boolean = false,
    val authResolutionIntent: PendingIntent? = null,
    val pendingAction: PendingAction? = null
)

class BackupViewModel(
    private val repository: BackupRepository,
    private val authManager: GoogleAuthManager
) : ViewModel() {

    var uiState by mutableStateOf(BackupUiState())
        private set

    fun onBackupClicked(activity: Activity) {
        viewModelScope.launch {
            uiState = BackupUiState(isBackingUp = true, statusText = "Checking authentication...", pendingAction = PendingAction.BACKUP)
            
            // 1. Sign In / Get Email
            val signInResult = authManager.signIn(activity)
            if (signInResult.isFailure) {
                uiState = BackupUiState(error = "Sign-in failed: ${signInResult.exceptionOrNull()?.message}")
                return@launch
            }
            val email = signInResult.getOrThrow()

            // 2. Request Drive Access
            uiState = uiState.copy(statusText = "Checking permissions...")
            val authResult = authManager.requestDriveAccess(activity, email)
            
            if (authResult.isFailure) {
                 val exception = authResult.exceptionOrNull()
                 if (exception is AuthResolutionRequiredException) {
                     // Need user approval
                     uiState = uiState.copy(
                         isBackingUp = false, 
                         statusText = "Permission required",
                         authResolutionIntent = exception.pendingIntent,
                         pendingAction = PendingAction.BACKUP
                     )
                 } else {
                     uiState = BackupUiState(error = "Permission check failed: ${exception?.message}", pendingAction = PendingAction.BACKUP)
                 }
                 return@launch
            }

            // 3. Perform Backup
            val accessToken = authResult.getOrThrow()
            performBackupInternal(email, accessToken)
        }
    }

    fun onAuthResolutionResult(resultOk: Boolean, activity: Activity) {
        val action = uiState.pendingAction
        // Clear the intent and action
        uiState = uiState.copy(authResolutionIntent = null, pendingAction = null)
        
        if (resultOk) {
            // Retry the appropriate flow
            if (action == PendingAction.RESTORE) {
                onRestoreClicked(activity)
            } else {
                onBackupClicked(activity)
            }
        } else {
            if (action == PendingAction.RESTORE) {
                uiState = BackupUiState(restoreError = "Permission denied by user")
            } else {
                uiState = BackupUiState(error = "Permission denied by user")
            }
        }
    }
    
    // Using a separate internal function to avoid duplicating logic if needed
    private suspend fun performBackupInternal(email: String, accessToken: String) {
        uiState = uiState.copy(isBackingUp = true, statusText = "Backing up database...", authResolutionIntent = null)
        
        val result = repository.performBackup(email, accessToken)
        
        if (result.isSuccess) {
            uiState = BackupUiState(success = true, statusText = "Backup completed successfully")
        } else {
            Log.d("BackupOperation", "backupViewmodel: failed --${result.exceptionOrNull()?.message}")
            uiState = BackupUiState(error = "Backup failed: ${result.exceptionOrNull()?.message}")
        }
    }

    fun onRestoreClicked(activity: Activity) {
        viewModelScope.launch {
            uiState = BackupUiState(isRestoring = true, restoreStatusText = "Checking authentication...", pendingAction = PendingAction.RESTORE)
            
            // 1. Sign In / Get Email
            val signInResult = authManager.signIn(activity)
            if (signInResult.isFailure) {
                uiState = BackupUiState(restoreError = "Sign-in failed: ${signInResult.exceptionOrNull()?.message}")
                return@launch
            }
            val email = signInResult.getOrThrow()

            // 2. Request Drive Access
            uiState = uiState.copy(restoreStatusText = "Checking permissions...")
            val authResult = authManager.requestDriveAccess(activity, email)
            
            if (authResult.isFailure) {
                 val exception = authResult.exceptionOrNull()
                 if (exception is AuthResolutionRequiredException) {
                     // Need user approval
                     uiState = uiState.copy(
                         isRestoring = false, 
                         restoreStatusText = "Permission required",
                         authResolutionIntent = exception.pendingIntent,
                         pendingAction = PendingAction.RESTORE
                     )
                 } else {
                     uiState = BackupUiState(restoreError = "Permission check failed: ${exception?.message}", pendingAction = PendingAction.RESTORE)
                 }
                 return@launch
            }

            // 3. Perform Restore
            val accessToken = authResult.getOrThrow()
            performRestoreInternal(email, accessToken)
        }
    }

    private suspend fun performRestoreInternal(email: String, accessToken: String) {
        uiState = uiState.copy(isRestoring = true, restoreStatusText = "Restoring database...", authResolutionIntent = null)
        
        val result = repository.performRestore(email, accessToken)
        
        if (result.isSuccess && result.getOrNull() == true) {
            uiState = BackupUiState(restoreSuccess = true, restoreStatusText = "Restore completed successfully")
        } else if (result.isSuccess && result.getOrNull() == false) {
            uiState = BackupUiState(restoreError = "No backup found in Google Drive")
        } else {
            Log.d("BackupOperation", "backupViewmodel restore: failed --${result.exceptionOrNull()?.message}")
            uiState = BackupUiState(restoreError = "Restore failed: ${result.exceptionOrNull()?.message}")
        }
    }
}

class BackupViewModelFactory(
    private val repository: BackupRepository,
    private val authManager: GoogleAuthManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BackupViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BackupViewModel(repository, authManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
