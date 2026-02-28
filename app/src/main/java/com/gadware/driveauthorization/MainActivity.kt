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
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gadware.driveauthorization.data.UserRepository
import com.gadware.driveauthorization.room.AppDatabase
import com.gadware.driveauthorization.room.NameRepository
import com.gadware.driveauthorization.room.NameViewModel
import com.gadware.driveauthorization.room.NameViewModelFactory
import com.gadware.driveauthorization.ui.login.LoginScreen
import com.gadware.driveauthorization.ui.login.LoginViewModel
import com.gadware.driveauthorization.ui.login.LoginViewModelFactory
import com.gadware.driveauthorization.ui.registration.RegistrationScreen
import com.gadware.driveauthorization.ui.registration.RegistrationViewModel
import com.gadware.driveauthorization.ui.registration.RegistrationViewModelFactory
import com.gadware.driveauthorization.ui.theme.DriveAuthorizationTheme

import com.gadware.driveauthorization.ui.home.HomeScreen
import com.gadware.driveauthorization.data.SessionManager
import androidx.compose.runtime.LaunchedEffect

enum class Screen { Login, Registration, Home, InsertData, Operation }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val dao = AppDatabase.getDatabase(this).nameDao()
        val repository = NameRepository(dao)
        val factory = NameViewModelFactory(repository)
        
        // Auth and Repositories
        val authManager = com.gadware.driveauthorization.auth.GoogleAuthManager(applicationContext)
        val userRepository = UserRepository()
        
        // Backup Feature Dependencies
        val backupRepository = com.gadware.driveauthorization.data.BackupRepository(applicationContext, AppDatabase.getDatabase(this), authManager)
        val sessionManager = SessionManager(applicationContext)
        val backupViewModelFactory = com.gadware.driveauthorization.ui.backup.BackupViewModelFactory(backupRepository, authManager, userRepository, sessionManager)

        // Login Feature
        val loginViewModelFactory = LoginViewModelFactory(authManager, userRepository, backupRepository)

        setContent {
            var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
            var userEmail by remember { mutableStateOf(sessionManager.getUserEmail()) }

            LaunchedEffect(Unit) {
                if (sessionManager.getUserEmail() == null) {
                    currentScreen = Screen.Login
                }
            }

            when (currentScreen) {
                Screen.Login -> {
                    val loginViewModel: LoginViewModel = viewModel(factory = loginViewModelFactory)
                    LoginScreen(
                        viewModel = loginViewModel,
                        onNavigateToRegistration = { email ->
                            userEmail = email
                            currentScreen = Screen.Registration
                        },
                        onNavigateToHome = {
                            val currentEmail = sessionManager.getUserEmail() ?: userEmail ?: "unknown"
                            sessionManager.saveUserEmail(currentEmail)
                            currentScreen = Screen.Home
                        }
                    )
                }
                Screen.Registration -> {
                    val email = userEmail ?: ""
                    val registrationViewModelFactory = RegistrationViewModelFactory(userRepository, email)
                    val registrationViewModel: RegistrationViewModel = viewModel(factory = registrationViewModelFactory)
                    RegistrationScreen(
                        viewModel = registrationViewModel,
                        onRegistrationSuccess = {
                            sessionManager.saveUserEmail(userEmail ?: "unknown")
                            currentScreen = Screen.Home
                        }
                    )
                }
                Screen.Home -> {
                    val viewModel: NameViewModel = viewModel(factory = factory)
                    HomeScreen(
                        viewModel = viewModel,
                        onInsertClick = { currentScreen = Screen.InsertData },
                        onOperationClick = { currentScreen = Screen.Operation }
                    )
                }
                Screen.InsertData -> {
                    val viewModel: NameViewModel = viewModel(factory = factory)
                    Column(Modifier.fillMaxSize()) {
                        Button(onClick = { currentScreen = Screen.Home }, modifier = Modifier.padding(16.dp)) {
                            Text("Back to Home")
                        }
                        InsertName(viewModel)
                    }
                }
                Screen.Operation -> {
                    Column(Modifier.fillMaxSize()) {
                        Button(onClick = { currentScreen = Screen.Home }, modifier = Modifier.padding(16.dp)) {
                            Text("Back to Home")
                        }
                        val backupViewModel: com.gadware.driveauthorization.ui.backup.BackupViewModel = viewModel(factory = backupViewModelFactory)
                        com.gadware.driveauthorization.ui.backup.BackupScreen(backupViewModel)
                    }
                }
            }
        }
    }
}

