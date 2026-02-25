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

data class BackupUiState(
    val isBackingUp: Boolean = false,
    val statusText: String = "Idle",
    val error: String? = null,
    val success: Boolean = false,
    val authResolutionIntent: PendingIntent? = null
)

class BackupViewModel(
    private val repository: BackupRepository,
    private val authManager: GoogleAuthManager
) : ViewModel() {

    var uiState by mutableStateOf(BackupUiState())
        private set

    fun onBackupClicked(activity: Activity) {
        viewModelScope.launch {
            uiState = BackupUiState(isBackingUp = true, statusText = "Checking authentication...")
            
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
                         authResolutionIntent = exception.pendingIntent
                     )
                 } else {
                     uiState = BackupUiState(error = "Permission check failed: ${exception?.message}")
                 }
                 return@launch
            }

            // 3. Perform Backup
            performBackupInternal(email)
        }
    }

    fun onAuthResolutionResult(resultOk: Boolean, activity: Activity) {
        // Clear the intent
        uiState = uiState.copy(authResolutionIntent = null)
        
        if (resultOk) {
            // Retry the backup flow (we assume we have the email, but safer to start over or store email)
            // Ideally we re-fetch email or store it in state. 
            // Only restarting if we have the email.
            // For simplicity, let's just ask user to click backup again OR restart flow?
            // Better UX: Restart flow automatically.
            // But we need the activity ref again to get email (CredMan). 
            // Actually, if resultOk, we are authorized. We still need the email for the Repository.
            // I'll call onBackupClicked again using the passed activity.
             onBackupClicked(activity)
        } else {
            uiState = BackupUiState(error = "Permission denied by user")
        }
    }
    
    // Using a separate internal function to avoid duplicating logic if needed
    private suspend fun performBackupInternal(email: String) {
        uiState = uiState.copy(isBackingUp = true, statusText = "Backing up database...", authResolutionIntent = null)
        
        val result = repository.performBackup(email)
        
        if (result.isSuccess) {
            uiState = BackupUiState(success = true, statusText = "Backup completed successfully")
        } else {
            Log.d("BackupOperation", "backupViewmodel: failed --${result.exceptionOrNull()?.message}")
            uiState = BackupUiState(error = "Backup failed: ${result.exceptionOrNull()?.message}")
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
