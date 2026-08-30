package org.vibetgram.app

import android.app.Application

class VibeTGramApplication : Application() {
    private lateinit var bootstrap: AndroidCoreBootstrapProvider

    override fun onCreate() {
        super.onCreate()
        bootstrap = AndroidCoreBootstrapProvider(this)
        AppCompositionRoot.install(bootstrap)
    }

    override fun onTerminate() {
        bootstrap.close()
        super.onTerminate()
    }
}
