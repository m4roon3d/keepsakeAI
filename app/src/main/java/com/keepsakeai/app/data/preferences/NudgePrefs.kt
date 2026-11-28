package com.keepsakeai.app.data.preferences

import android.content.Context

/** Morning brief / evening journal settings and which memories you've already opened. */
class NudgePrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("nudges", Context.MODE_PRIVATE)

    var morningBrief: Boolean
        get() = prefs.getBoolean("brief", true)
        set(v) = prefs.edit().putBoolean("brief", v).apply()

    /** Tag notes, voice memos and links with where you were when you saved them. */
    var tagSaveLocation: Boolean
        get() = prefs.getBoolean("tag_save_location", true)
        set(v) = prefs.edit().putBoolean("tag_save_location", v).apply()

    var eveningJournal: Boolean
        get() = prefs.getBoolean("journal", true)
        set(v) = prefs.edit().putBoolean("journal", v).apply()

    var viewed: Set<Long>
        get() = prefs.getStringSet("viewed", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
        private set(v) = prefs.edit().putStringSet("viewed", v.map { it.toString() }.toSet()).apply()

    /** Memories already decided on in the daily review (kept, turned into a to-do). */
    var reviewed: Set<Long>
        get() = prefs.getStringSet("reviewed", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
        private set(v) = prefs.edit().putStringSet("reviewed", v.map { it.toString() }.toSet()).apply()

    fun markReviewed(id: Long) { if (id !in reviewed) reviewed = (reviewed + id).let { if (it.size > 2000) it.drop(500).toSet() else it } }

    fun markViewed(id: Long) { if (id !in viewed) viewed = (viewed + id).let { if (it.size > 2000) it.drop(500).toSet() else it } }
}
