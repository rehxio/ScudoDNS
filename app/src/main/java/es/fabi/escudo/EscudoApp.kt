package es.fabi.escudo

import android.app.Application
import es.fabi.escudo.lists.UpdateWorker

class EscudoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        UpdateWorker.schedule(this)
    }
}
