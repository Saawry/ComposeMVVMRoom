package com.gadware.driveauthorization.ui.backup

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun BackupScreen(viewModel: BackupViewModel) {
    val uiState = viewModel.uiState
    val context = LocalContext.current
    val activity = context as? Activity

    val startIntentSenderForResult = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (activity != null) {
            viewModel.onAuthResolutionResult(result.resultCode == Activity.RESULT_OK, activity)
        }
    }

    LaunchedEffect(uiState.authResolutionIntent) {
        uiState.authResolutionIntent?.let { pendingIntent ->
            val intentSenderRequest = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
            startIntentSenderForResult.launch(intentSenderRequest)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (uiState.isBackingUp) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = uiState.statusText, style = MaterialTheme.typography.bodyLarge)
        } else {
            if (uiState.success) {
                Text(
                    text = "Backup Successful!",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            if (uiState.error != null) {
                Text(
                    text = uiState.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Button(
                onClick = {
                    if (activity != null) {
                        viewModel.onBackupClicked(activity)
                    }
                },
                enabled = !uiState.isBackingUp
            ) {
                Text("Backup to Google Drive")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = uiState.statusText,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
