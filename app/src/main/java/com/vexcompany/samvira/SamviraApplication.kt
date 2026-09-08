package com.vexcompany.samvira

import android.app.Application
import com.vexcompany.samvira.core.di.AppContainer

/**
 * Application entry point. Owns the manual dependency container for the app.
 *
 * Manual constructor injection (via [AppContainer]) is used instead of a
 * compile-time DI framework: the foundation is small and the wiring stays
 * explicit and auditable. A DI framework can be introduced later if it
 * materially improves maintainability.
 */
class SamviraApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
