package com.gadware.driveauthorization.ui.login

import android.app.Activity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gadware.driveauthorization.auth.AuthResolutionRequiredException
import com.gadware.driveauthorization.auth.GoogleAuthManager
import com.gadware.driveauthorization.data.BackupRepository
import com.gadware.driveauthorization.data.UserRepository
import kotlinx.coroutines.launch

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    data class LoadingMessage(val message: String) : LoginState()
    data class RequiresAuthResolution(val intent: android.app.PendingIntent?, val email: String) : LoginState()
    data class SuccessRegistered(val email: String) : LoginState()
    data class SuccessNotRegistered(val email: String) : LoginState()
    data class Error(val message: String) : LoginState()
}

class LoginViewModel(
    private val authManager: GoogleAuthManager,
    private val userRepository: UserRepository,
    private val backupRepository: BackupRepository
) : ViewModel() {

    var loginState by mutableStateOf<LoginState>(LoginState.Idle)
        private set

    fun signIn(activity: Activity) {
        viewModelScope.launch {
            loginState = LoginState.Loading
            val signInResult = authManager.signIn(activity)
            if (signInResult.isFailure) {
                loginState = LoginState.Error("Sign-in failed: ${signInResult.exceptionOrNull()?.message}")
                return@launch
            }
            
            val email = signInResult.getOrThrow()
            
            // Check if registered
            val isRegistered = userRepository.isUserRegistered(email)
            if (isRegistered) {
                loginState = LoginState.LoadingMessage("Checking Drive permissions...")
                val authResult = authManager.requestDriveAccess(activity, email)
                
                if (authResult.isFailure) {
                    val exception = authResult.exceptionOrNull()
                    if (exception is AuthResolutionRequiredException) {
                        loginState = LoginState.RequiresAuthResolution(exception.pendingIntent, email)
                    } else {
                        loginState = LoginState.SuccessRegistered(email)
                    }
                    return@launch
                }
                
                performRestoreAndProceed(email)
            } else {
                loginState = LoginState.SuccessNotRegistered(email)
            }
        }
    }

    fun onAuthResolutionResult(resultOk: Boolean, email: String) {
        viewModelScope.launch {
            if (resultOk) {
                performRestoreAndProceed(email)
            } else {
                loginState = LoginState.SuccessRegistered(email)
            }
        }
    }

    private suspend fun performRestoreAndProceed(email: String) {
        loginState = LoginState.LoadingMessage("Checking and restoring backup...")
        backupRepository.performRestore(email)
        loginState = LoginState.SuccessRegistered(email)
    }
}

class LoginViewModelFactory(
    private val authManager: GoogleAuthManager,
    private val userRepository: UserRepository,
    private val backupRepository: BackupRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LoginViewModel(authManager, userRepository, backupRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
