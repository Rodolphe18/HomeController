package com.francotte.homecontroller.presentation.control

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.francotte.homecontroller.HomeControllerApplication

/** Construit un [DeviceControlViewModel] pour une adresse donnée. */
fun deviceControlViewModelFactory(address: String) = viewModelFactory {
    initializer {
        val app = this[APPLICATION_KEY] as HomeControllerApplication
        DeviceControlViewModel(address, app.container.createEspDeviceClient())
    }
}
