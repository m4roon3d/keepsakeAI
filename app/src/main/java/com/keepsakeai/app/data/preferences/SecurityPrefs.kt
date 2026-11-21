package com.keepsakeai.app.data.preferences

import android.content.Context

class SecurityPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("security", Context.MODE_PRIVATE)

    var appLock: Boolean
        get() = prefs.getBoolean("app_lock", false)
        set(v) = prefs.edit().putBoolean("app_lock", v).apply()

    var lastBackupAt: Long
        get() = prefs.getLong("last_backup", 0L)
        set(v) = prefs.edit().putLong("last_backup", v).apply()
}
