package com.keepsakeai.app.notify

import android.content.Context
import com.keepsakeai.app.MindSpaceApp
import com.keepsakeai.app.data.preferences.MoneyPrefs
import com.keepsakeai.app.data.preferences.ReminderStore
import com.keepsakeai.app.expiry.Expiries
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Heads-ups for warranties, insurance and other things that end: about a month before and again in the last week. */
object ExpiryWatch {
    fun check(context: Context) {
        val app = context.applicationContext as MindSpaceApp
        val prefs = MoneyPrefs(app)
        if (!prefs.renewalReminders) return
        val now = System.currentTimeMillis()
        val fmt = SimpleDateFormat("d MMM yyyy", Locale.ENGLISH)
        ReminderStore(app).all().filter { Expiries.isExpiry(it) }.forEach { r ->
            val d = Expiries.daysLeft(r, now)
            val stage = Expiries.alertStage(d) ?: return@forEach
            if (!prefs.firstTime("expiry:${r.id}:$stage")) return@forEach
            val whenText = when (d) { 0 -> "today"; 1 -> "tomorrow"; else -> "in $d days" }
            Notifier.show(
                app, Notifier.REMINDERS, ("expiry" + r.id + stage).hashCode(),
                r.title,
                if (r.title.endsWith(" matures")) "Matures $whenText (${fmt.format(Date(r.atMillis))}). Decide whether to renew or withdraw."
                else "Ends $whenText (${fmt.format(Date(r.atMillis))}). Claim or renew before then.",
                openTab = "home"
            )
        }
    }
}
