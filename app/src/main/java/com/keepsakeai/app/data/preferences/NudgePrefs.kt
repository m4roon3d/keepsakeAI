package com.keepsakeai.app.data.preferences

import android.content.Context

/** Morning brief / evening journal settings and which memories you've already opened. */
class NudgePrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("nudges", Context.MODE_PRIVATE)

    var morningBrief: Boolean
        get() = prefs.getBoolean("brief", true)
        set(v) = prefs.edit().putBoolean("brief", v).apply()

    var eveningJournal: Boolean
        get() = prefs.getBoolean("journal", true)
        set(v) = prefs.edit().putBoolean("journal", v).apply()

    var viewed: Set<Long>
        get() = prefs.getStringSet("viewed", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
        private set(v) = prefs.edit().putStringSet("viewed", v.map { it.toString() }.toSet()).apply()

    fun markViewed(id: Long) { if (id !in viewed) viewed = (viewed + id).let { if (it.size > 2000) it.drop(500).toSet() else it } }
}
