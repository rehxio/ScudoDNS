package es.rehxio.scudo

import android.app.Application
import es.rehxio.scudo.lists.UpdateWorker

class ScudoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        UpdateWorker.schedule(this)
    }
}
