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

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.processLocalRestore(context, uri)
        }
    }

    if (uiState.showExportDialog) {
        var checkCsv by remember { mutableStateOf(false) }
        var checkDb by remember { mutableStateOf(false) }
        var checkMetadata by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { viewModel.onExportDialogDismissed() },
            title = { Text("Export Database") },
            text = {
                Column {
                    Text("Select what to include in the backup:")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = checkDb, onCheckedChange = { 
                            checkDb = it
                            if (!it) checkMetadata = false 
                        })
                        Text("DB File")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = checkCsv, onCheckedChange = { checkCsv = it })
                        Text("CSV (All Tables)")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = checkMetadata, 
                            onCheckedChange = { checkMetadata = it },
                            enabled = checkDb
                        )
                        Text("Metadata JSON")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val mode = when {
                            checkDb && checkCsv && checkMetadata -> com.gadware.driveauthorization.ui.backup.UniversalBackupManager.BackupMode.FULL
                            checkDb && checkCsv -> com.gadware.driveauthorization.ui.backup.UniversalBackupManager.BackupMode.DB_AND_CSV
                            checkDb && checkMetadata -> com.gadware.driveauthorization.ui.backup.UniversalBackupManager.BackupMode.DB_AND_METADATA
                            checkDb -> com.gadware.driveauthorization.ui.backup.UniversalBackupManager.BackupMode.ONLY_DB
                            checkCsv -> com.gadware.driveauthorization.ui.backup.UniversalBackupManager.BackupMode.ONLY_CSV
                            else -> null
                        }
                        if (mode != null) {
                            viewModel.proceedWithExport(context, mode)
                        } else {
                            viewModel.onExportDialogDismissed()
                        }
                    },
                    enabled = checkDb || checkCsv
                ) {
                    Text("Export")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onExportDialogDismissed() }) {
                    Text("Cancel")
                }
            }
        )
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = uiState.selectedRoutineConfig,
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
                        
                        Button(
                            onClick = { viewModel.saveRoutineConfig() },
                            enabled = uiState.routineConfig != uiState.selectedRoutineConfig
                        ) {
                            Text("Save")
                        }
                    }
                }
            }

            // Local Operations Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Local Backup & Restore", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { viewModel.onExportClicked() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Export Database")
                        }
                        Button(
                            onClick = { restoreLauncher.launch("application/zip") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Manual Restore")
                        }
                    }
                }
            }

            // Manual Operations Section (Drive)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Drive Backup & Restore", style = MaterialTheme.typography.titleMedium)
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
                                Text("Backup to Drive")
                            }
                            Button(
                                onClick = { activity?.let { viewModel.onManualRestoreClicked(it) } },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Restore from Drive")
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
