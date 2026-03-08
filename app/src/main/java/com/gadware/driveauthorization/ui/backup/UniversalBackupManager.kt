package com.gadware.driveauthorization.ui.backup
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.database.sqlite.SQLiteDatabase
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import android.net.Uri
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object UniversalBackupManager {

    enum class BackupMode {
        FULL,
        DB_AND_CSV,
        DB_AND_METADATA,
        ONLY_DB,
        ONLY_CSV
    }

    fun createBackup(
        context: Context,
        databaseName: String,
        appName: String,
        mode: BackupMode = BackupMode.FULL
    ) {
        val timeStamp = SimpleDateFormat(
            "yyyy-MM-dd_HH-mm",
            Locale.getDefault()
        ).format(Date())

        val tempDir = File(context.cacheDir, "backup_temp")
        tempDir.deleteRecursively()
        tempDir.mkdirs()

        val dbFile = context.getDatabasePath(databaseName)

        when (mode) {
            BackupMode.FULL -> {
                exportDatabase(dbFile, tempDir)
                exportAllTablesAsCsv(context, databaseName, tempDir)
                exportMetadata(tempDir, appName)
            }
            BackupMode.DB_AND_CSV -> {
                exportDatabase(dbFile, tempDir)
                exportAllTablesAsCsv(context, databaseName, tempDir)
            }
            BackupMode.DB_AND_METADATA -> {
                exportDatabase(dbFile, tempDir)
                exportMetadata(tempDir, appName)
            }
            BackupMode.ONLY_DB -> {
                exportDatabase(dbFile, tempDir)
            }
            BackupMode.ONLY_CSV -> {
                exportAllTablesAsCsv(context, databaseName, tempDir)
            }
        }

        val zipFile = File(
            context.cacheDir,
            "${appName}_backup_$timeStamp.zip"
        )

        zipDirectory(tempDir, zipFile)
        saveToDownloads(context, zipFile)

        tempDir.deleteRecursively()
        zipFile.delete()
    }

    private fun exportDatabase(dbFile: File, tempDir: File) {
        val dbDir = File(tempDir, "database")
        dbDir.mkdirs()
        dbFile.copyTo(File(dbDir, dbFile.name), overwrite = true)
    }

    private fun exportMetadata(tempDir: File, appName: String) {
        val metadata = """
            {
              "app_name": "$appName",
              "exported_at": "${System.currentTimeMillis()}",
              "android_version": "${Build.VERSION.SDK_INT}"
            }
        """.trimIndent()

        File(tempDir, "metadata.json").writeText(metadata)
    }

    private fun exportAllTablesAsCsv(
        context: Context,
        databaseName: String,
        tempDir: File
    ) {
        val csvDir = File(tempDir, "csv")
        csvDir.mkdirs()

        val dbPath = context.getDatabasePath(databaseName).path
        val db = SQLiteDatabase.openDatabase(
            dbPath,
            null,
            SQLiteDatabase.OPEN_READONLY
        )

        val tables = getAllTableNames(db)

        tables.forEach { table ->
            exportTableToCsv(db, table, File(csvDir, "$table.csv"))
        }

        db.close()
    }

    private fun getAllTableNames(db: SQLiteDatabase): List<String> {
        val tables = mutableListOf<String>()
        val cursor = db.rawQuery(
            """
            SELECT name FROM sqlite_master 
            WHERE type='table' 
            AND name NOT LIKE 'sqlite_%'
            """.trimIndent(),
            null
        )

        cursor.use {
            while (it.moveToNext()) {
                tables.add(it.getString(0))
            }
        }
        return tables
    }

    private fun exportTableToCsv(
        db: SQLiteDatabase,
        tableName: String,
        outputFile: File
    ) {
        val cursor = db.rawQuery("SELECT * FROM $tableName", null)

        outputFile.bufferedWriter().use { writer ->
            val columns = cursor.columnNames
            writer.write(columns.joinToString(","))
            writer.newLine()

            while (cursor.moveToNext()) {
                val row = buildList {
                    for (i in columns.indices) {
                        val value = cursor.getString(i)
                            ?.replace("\"", "\"\"")
                            ?: ""
                        add("\"$value\"")
                    }
                }
                writer.write(row.joinToString(","))
                writer.newLine()
            }
        }
        cursor.close()
    }

    private fun zipDirectory(sourceDir: File, zipFile: File) {
        ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
            sourceDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val entryName =
                        file.relativeTo(sourceDir).path
                    val entry = ZipEntry(entryName)
                    zipOut.putNextEntry(entry)
                    file.inputStream().copyTo(zipOut)
                    zipOut.closeEntry()
                }
            }
        }
    }

    private fun saveToDownloads(context: Context, zipFile: File) {
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, zipFile.name)
            put(MediaStore.Downloads.MIME_TYPE, "application/zip")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            contentValues
        )

        uri?.let {
            resolver.openOutputStream(it).use { output ->
                zipFile.inputStream().copyTo(output!!)
            }
            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(it, contentValues, null, null)
        }
    }

    fun restoreLocalBackup(context: Context, zipUri: Uri) {
        val tempDir = File(context.cacheDir, "local_restore_temp")
        tempDir.deleteRecursively()
        tempDir.mkdirs()

        context.contentResolver.openInputStream(zipUri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val newFile = File(tempDir, entry.name)
                    if (entry.isDirectory) {
                        newFile.mkdirs()
                    } else {
                        newFile.parentFile?.mkdirs()
                        FileOutputStream(newFile).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }

        val dbName = "name_db"
        val dbFile = context.getDatabasePath(dbName)
        val walFile = context.getDatabasePath("$dbName-wal")
        val shmFile = context.getDatabasePath("$dbName-shm")

        com.gadware.driveauthorization.room.AppDatabase.getDatabase(context).close()

        val extractedDbDir = File(tempDir, "database")
        
        val tempDb = File(extractedDbDir, dbFile.name)
        val tempWal = File(extractedDbDir, walFile.name)
        val tempShm = File(extractedDbDir, shmFile.name)

        if (tempDb.exists()) {
            tempDb.copyTo(dbFile, overwrite = true)
        } else {
             val rootDb = File(tempDir, dbFile.name)
             if (rootDb.exists()) rootDb.copyTo(dbFile, overwrite = true)
        }
        
        if (tempWal.exists()) {
            tempWal.copyTo(walFile, overwrite = true)
        } else {
            val rootWal = File(tempDir, walFile.name)
            if (rootWal.exists()) rootWal.copyTo(walFile, overwrite = true) else if (walFile.exists()) walFile.delete()
        }

        if (tempShm.exists()) {
            tempShm.copyTo(shmFile, overwrite = true)
        } else {
            val rootShm = File(tempDir, shmFile.name)
            if (rootShm.exists()) rootShm.copyTo(shmFile, overwrite = true) else if (shmFile.exists()) shmFile.delete()
        }

        tempDir.deleteRecursively()
    }
}