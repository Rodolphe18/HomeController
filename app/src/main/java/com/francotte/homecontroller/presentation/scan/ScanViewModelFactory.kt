package com.francotte.homecontroller.presentation.scan

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.francotte.homecontroller.HomeControllerApplication

/** Fabrique le [ScanViewModel] en récupérant le scanner depuis l'AppContainer. */
val ScanViewModelFactory = viewModelFactory {
    initializer {
        val app = this[APPLICATION_KEY] as HomeControllerApplication
        ScanViewModel(app.container.bleScanner)
    }
}
