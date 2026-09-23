package com.khomenote

import android.app.Application
import com.khomenote.notification.ReminderScheduler
import com.khomenote.notification.createReminderChannel

class KHomeNoteApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createReminderChannel(this)
        ReminderScheduler.ensureScheduled(this)
    }
}
