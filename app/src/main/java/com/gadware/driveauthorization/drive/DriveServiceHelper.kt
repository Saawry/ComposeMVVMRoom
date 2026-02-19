package com.gadware.driveauthorization.drive

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections

class DriveServiceHelper(context: Context, email: String) {

    private val credential = GoogleAccountCredential.usingOAuth2(
        context, Collections.singleton(DriveScopes.DRIVE_APPDATA)
    ).apply {
        selectedAccountName = email
    }

    private val driveService: Drive = Drive.Builder(
        NetHttpTransport(),
        GsonFactory.getDefaultInstance(),
        credential
    ).setApplicationName("DriveBackupApp").build()

    suspend fun findBackupFile(): String? = withContext(Dispatchers.IO) {
        val fileList = driveService.files().list()
            .setQ("name = 'backup.zip' and 'appDataFolder' in parents and trashed = false")
            .setSpaces("appDataFolder")
            .setFields("files(id, name)")
            .execute()

        fileList.files.firstOrNull()?.id
    }

    suspend fun uploadBackup(localFile: File, existingFileId: String?): String = withContext(Dispatchers.IO) {
        val fileMetadata = com.google.api.services.drive.model.File()
        fileMetadata.name = "backup.zip"
        
        val mediaContent = FileContent("application/zip", localFile)

        if (existingFileId != null) {
            // Update existing file
            driveService.files().update(existingFileId, null, mediaContent)
                .execute()
            existingFileId
        } else {
            // Create new file
            fileMetadata.parents = listOf("appDataFolder")
            val file = driveService.files().create(fileMetadata, mediaContent)
                .setFields("id")
                .execute()
            file.id
        }
    }
}
