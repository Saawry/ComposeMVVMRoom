package com.gadware.driveauthorization.ui.login

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onNavigateToRegistration: (String) -> Unit,
    onNavigateToHome: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    
    val state = viewModel.loginState

    val startIntentSenderForResult = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (state is LoginState.RequiresAuthResolution) {
            viewModel.onAuthResolutionResult(result.resultCode == Activity.RESULT_OK, state.email)
        }
    }

    LaunchedEffect(state) {
        when (state) {
            is LoginState.SuccessRegistered -> {
                onNavigateToHome()
            }
            is LoginState.SuccessNotRegistered -> {
                onNavigateToRegistration(state.email)
            }
            is LoginState.RequiresAuthResolution -> {
                state.intent?.let { pendingIntent ->
                    val intentSenderRequest = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                    startIntentSenderForResult.launch(intentSenderRequest)
                }
            }
            else -> {}
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Welcome",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        if (state is LoginState.Error) {
            Text(
                text = state.message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        Button(
            onClick = {
                activity?.let { viewModel.signIn(it) }
            },
            enabled = state !is LoginState.Loading && state !is LoginState.LoadingMessage
        ) {
            if (state is LoginState.Loading || state is LoginState.LoadingMessage) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                if (state is LoginState.LoadingMessage) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(state.message)
                }
            } else {
                Text("Sign in with Google")
            }
        }
    }
}
