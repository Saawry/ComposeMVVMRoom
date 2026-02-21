package com.gadware.driveauthorization.ui.login

import android.app.Activity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gadware.driveauthorization.auth.GoogleAuthManager
import com.gadware.driveauthorization.data.UserRepository
import kotlinx.coroutines.launch

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    data class SuccessRegistered(val email: String) : LoginState()
    data class SuccessNotRegistered(val email: String) : LoginState()
    data class Error(val message: String) : LoginState()
}

class LoginViewModel(
    private val authManager: GoogleAuthManager,
    private val userRepository: UserRepository
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
                loginState = LoginState.SuccessRegistered(email)
            } else {
                loginState = LoginState.SuccessNotRegistered(email)
            }
        }
    }
}

class LoginViewModelFactory(
    private val authManager: GoogleAuthManager,
    private val userRepository: UserRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LoginViewModel(authManager, userRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
