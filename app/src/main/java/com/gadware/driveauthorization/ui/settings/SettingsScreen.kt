package com.gadware.driveauthorization.ui.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onNavigateBack: () -> Unit) {
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

    LaunchedEffect(uiState.showAuthResolution) {
        if (uiState.showAuthResolution && uiState.authResolutionIntent != null) {
            val intentSenderRequest = IntentSenderRequest.Builder(uiState.authResolutionIntent.intentSender).build()
            startIntentSenderForResult.launch(intentSenderRequest)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Backup") },
                navigationIcon = {
                    Button(onClick = onNavigateBack, modifier = Modifier.padding(start = 8.dp)) {
                        Text("Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // User Profile Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("User Profile", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Login Email: ${uiState.userEmail ?: "Unknown"}")
                    Text("Drive Email: ${uiState.driveEmail ?: "Not linked"}")
                }
            }

            // Backup Status Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Backup Status", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Last Backup: ${uiState.lastBackupDateStr}")
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text("Routine Backup", style = MaterialTheme.typography.titleSmall)
                    val options = listOf("Never", "Daily", "Weekly", "Monthly")
                    var expanded by remember { mutableStateOf(false) }

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = uiState.routineConfig,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Schedule") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            options.forEach { selectionOption ->
                                DropdownMenuItem(
                                    text = { Text(selectionOption) },
                                    onClick = {
                                        viewModel.onRoutineConfigChanged(selectionOption)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Manual Operations Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Manual Operations", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (uiState.isLoading) {
                        CircularProgressIndicator()
                        Text(uiState.statusText, style = MaterialTheme.typography.bodySmall)
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { activity?.let { viewModel.onManualBackupClicked(it) } },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Backup Now")
                            }
                            Button(
                                onClick = { activity?.let { viewModel.onManualRestoreClicked(it) } },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Restore")
                            }
                        }
                        
                        if (uiState.error != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(uiState.error, color = MaterialTheme.colorScheme.error)
                        }
                        
                        if (uiState.statusText.isNotEmpty() && uiState.error == null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(uiState.statusText, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
