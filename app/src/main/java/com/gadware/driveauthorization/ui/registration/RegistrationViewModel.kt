package com.gadware.driveauthorization.ui.registration

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gadware.driveauthorization.data.UserProfile
import com.gadware.driveauthorization.data.UserRepository
import kotlinx.coroutines.launch
import java.util.Calendar

class RegistrationViewModel(private val userRepository: UserRepository, val email: String) : ViewModel() {

    var name by mutableStateOf("")
    var shopName by mutableStateOf("")
    var phoneNumber by mutableStateOf("")
    var address by mutableStateOf("")

    var isRegistering by mutableStateOf(false)
        private set
        
    var registrationSuccess by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun register() {
        if (name.isBlank() || shopName.isBlank() || phoneNumber.isBlank() || address.isBlank()) {
            errorMessage = "Please fill all fields"
            return
        }

        viewModelScope.launch {
            isRegistering = true
            errorMessage = null
            
            val calendar = Calendar.getInstance()
            val regDate = calendar.timeInMillis
            calendar.add(Calendar.MONTH, 6)
            val nextPayDate = calendar.timeInMillis

            val userProfile = UserProfile(
                email = email,
                name = name,
                shopName = shopName,
                phoneNumber = phoneNumber,
                address = address,
                regDate = regDate,
                userType = "free",
                status = "pending",
                nextPayDate = nextPayDate
            )

            val result = userRepository.registerUser(userProfile)
            if (result.isSuccess) {
                registrationSuccess = true
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "Registration failed"
            }
            isRegistering = false
        }
    }
}

class RegistrationViewModelFactory(private val userRepository: UserRepository, private val email: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RegistrationViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RegistrationViewModel(userRepository, email) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
