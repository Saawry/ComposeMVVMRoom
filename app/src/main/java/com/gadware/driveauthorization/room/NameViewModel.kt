package com.gadware.driveauthorization.room


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class NameViewModel(
    private val repository: NameRepository
) : ViewModel() {

    val names = repository.names

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun saveName(input: String) {
        if (input.length < 3) {
            _error.value = "Name must be at least 3 characters"
            return
        }

        _error.value = null
        viewModelScope.launch {
            repository.addName(input)
        }
    }
}
