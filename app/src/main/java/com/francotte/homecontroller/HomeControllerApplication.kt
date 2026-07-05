package com.francotte.homecontroller

import android.app.Application
import com.francotte.homecontroller.di.AppContainer

class HomeControllerApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
