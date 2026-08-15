package com.khomenote

import android.app.Application
import com.khomenote.notification.createReminderChannel
import com.khomenote.notification.scheduleDailyReminderCheck

class KHomeNoteApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createReminderChannel(this)
        scheduleDailyReminderCheck(this)
    }
}
