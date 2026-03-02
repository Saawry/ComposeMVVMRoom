package com.gadware.driveauthorization.data

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    fun saveUserEmail(email: String) {
        prefs.edit().putString("user_email", email).apply()
    }

    fun getUserEmail(): String? {
        return prefs.getString("user_email", null)
    }

    fun saveLastBackupDate(timestamp: Long) {
        prefs.edit().putLong("last_backup_date", timestamp).apply()
    }

    fun getLastBackupDate(): Long {
        return prefs.getLong("last_backup_date", 0L)
    }

    fun saveRoutineBackupConfig(config: String) {
        prefs.edit().putString("routine_backup_config", config).apply()
    }

    fun getRoutineBackupConfig(): String {
        return prefs.getString("routine_backup_config", "Never") ?: "Never"
    }

    fun clearSession() {
        prefs.edit().remove("user_email").apply()
    }
}
