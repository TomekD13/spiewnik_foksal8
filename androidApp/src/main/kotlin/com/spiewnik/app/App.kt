package com.spiewnik.app

import android.app.Application

/** Application entry point — installs the crash-to-file logger as early as possible. */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
    }
}
