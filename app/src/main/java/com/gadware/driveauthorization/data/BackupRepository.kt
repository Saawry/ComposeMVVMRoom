package com.gadware.driveauthorization.data

import android.content.Context
import android.util.Log
import androidx.sqlite.db.SimpleSQLiteQuery
import com.gadware.driveauthorization.auth.GoogleAuthManager
import com.gadware.driveauthorization.drive.DriveServiceHelper
import com.gadware.driveauthorization.room.AppDatabase
import com.gadware.driveauthorization.utils.ZipUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.log

class BackupRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val authManager: GoogleAuthManager
) {

    suspend fun performBackup(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. Initialize Drive Service
            val driveHelper = DriveServiceHelper(context, email)

            // 2. Checkpoint Database (Ensure all data is in the main .db file or synced)
            // Using a raw query to checkpoint
            database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()

            // 3. Identify DB files
            val dbName = "name_db"
            val dbFile = context.getDatabasePath(dbName)
            val walFile = context.getDatabasePath("$dbName-wal")
            val shmFile = context.getDatabasePath("$dbName-shm")

            val filesToZip = mutableListOf<File>()
            if (dbFile.exists()) filesToZip.add(dbFile)
            if (walFile.exists()) filesToZip.add(walFile)
            if (shmFile.exists()) filesToZip.add(shmFile)

            if (filesToZip.isEmpty()) {
                return@withContext Result.failure(Exception("No database files found"))
            }else{
                Log.d("BackupOperation", "backupRepository --files to zip: not empty.")
            }

            // 4. Copy to Cache and Zip
            try {
                val cacheDir = context.cacheDir
                val backupDir = File(cacheDir, "backup_temp")
                if (backupDir.exists()) {
                    backupDir.deleteRecursively()
                } else {
                    Log.d("BackupOperation", "backupRepository-- backup directory not exists")
                }
                backupDir.mkdirs()

            // We copy files to a temp dir first to avoid file locking issues during zip if possible,
            // though with checkpoint they should be safe to read.
            val filesToPack = filesToZip.map { file ->
                val dest = File(backupDir, file.name)
                file.copyTo(dest, overwrite = true)
                dest
            }

            val zipFile = File(cacheDir, "backup.zip")
            ZipUtils.zipFiles(filesToPack, zipFile)

            // 5. Upload to Drive
            val existingId = driveHelper.findBackupFile()
            Log.d("BackupOperation", "backupRepositort: existing id --${existingId}")
            driveHelper.uploadBackup(zipFile, existingId)

            // Cleanup
            backupDir.deleteRecursively()
            zipFile.delete()
            }catch (e: Exception){
                Log.d("BackupOperation", "backupRepository: copy cache--exception --${e}")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            //Log.e("BackupRepository", "Backup failed", e)
            Log.d("BackupOperation", "Backup Repository: exception --${e}")
            Result.failure(e)
        }
    }

    suspend fun performRestore(email: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val driveHelper = DriveServiceHelper(context, email)
            
            val existingId = driveHelper.findBackupFile()
            Log.d("PerformRestore", "BackupRepository: existing id --${existingId}")
            if (existingId == null) {
                return@withContext Result.success(false)
            }

            val cacheDir = context.cacheDir
            val zipFile = File(cacheDir, "downloaded_backup.zip")
            driveHelper.downloadBackup(existingId, zipFile)

            val tempDir = File(cacheDir, "restore_temp")
            if (tempDir.exists()) tempDir.deleteRecursively()
            tempDir.mkdirs()

            ZipUtils.unzipFiles(zipFile, tempDir)

            database.close()

            val dbName = "name_db"
            val dbFile = context.getDatabasePath(dbName)
            val walFile = context.getDatabasePath("$dbName-wal")
            val shmFile = context.getDatabasePath("$dbName-shm")

            val tempDb = File(tempDir, dbFile.name)
            if (tempDb.exists()) tempDb.copyTo(dbFile, overwrite = true)
            
            val tempWal = File(tempDir, walFile.name)
            if (tempWal.exists()) {
                tempWal.copyTo(walFile, overwrite = true)
            } else if (walFile.exists()) {
                walFile.delete()
            }

            val tempShm = File(tempDir, shmFile.name)
            if (tempShm.exists()) {
                tempShm.copyTo(shmFile, overwrite = true)
            } else if (shmFile.exists()) {
                shmFile.delete()
            }

            try {
                zipFile.delete()
                tempDir.deleteRecursively()
            } catch (e: Exception) {
                Log.d("PerformRestore", "cleanup exception: ${e.message}")
            }

            Result.success(true)
        } catch (e: Exception) {
            Log.e("BackupRepository", "Restore failed", e)
            Result.failure(e)
        }
    }
}
