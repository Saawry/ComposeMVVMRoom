package com.gadware.driveauthorization.room


import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class NameViewModelFactory(
    private val repository: NameRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return NameViewModel(repository) as T
    }
}
