package com.gadware.driveauthorization

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gadware.driveauthorization.room.AppDatabase
import com.gadware.driveauthorization.room.NameRepository
import com.gadware.driveauthorization.room.NameViewModel
import com.gadware.driveauthorization.room.NameViewModelFactory
import com.gadware.driveauthorization.ui.theme.DriveAuthorizationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val dao = AppDatabase.getDatabase(this).nameDao()
        val repository = NameRepository(dao)
        val factory = NameViewModelFactory(repository)
        //val viewModel = NameViewModel(NameRepository(AppDatabase.getDatabase(this).nameDao()))
        setContent {
            val viewModel: NameViewModel = viewModel(factory = factory)
            InsertName(viewModel)
        }
    }
}

