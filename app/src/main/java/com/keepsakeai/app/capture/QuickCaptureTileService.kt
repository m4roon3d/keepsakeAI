package com.keepsakeai.app.capture

import android.content.Intent
import android.service.quicksettings.TileService
import com.keepsakeai.app.MainActivity

class QuickCaptureTileService : TileService() {
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(MainActivity.EXTRA_TRIGGER_SNAP, true)
        }
        startActivityAndCollapse(intent)
    }
}
