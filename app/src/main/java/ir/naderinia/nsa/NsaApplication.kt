package ir.naderinia.nsa

import android.app.Application
import ir.naderinia.nsa.data.AppDatabase
import ir.naderinia.nsa.notification.NotificationHelper

class NsaApplication : Application() {

    val database by lazy { AppDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
    }
}
