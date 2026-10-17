package com.petcare.app.ui.common

import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.petcare.app.PetCareApp
import com.petcare.app.data.AppContainer

/** Pulls the app-wide [AppContainer] out of the ViewModel creation extras. */
val CreationExtras.appContainer: AppContainer
    get() = (this[APPLICATION_KEY] as PetCareApp).container

/**
 * Obtains a ViewModel built from the [AppContainer] plus the navigation
 * arguments in [SavedStateHandle]. Keeps every screen's DI wiring to one line.
 */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    crossinline create: (container: AppContainer, handle: SavedStateHandle) -> VM
): VM = viewModel(
    factory = viewModelFactory {
        initializer { create(appContainer, createSavedStateHandle()) }
    }
)

/**
 * The app-wide [AppContainer] for composables that need a service, not a
 * ViewModel. Resolved the same way ViewModels resolve it — through the host
 * activity's creation extras — so tests that inject a container there see it too.
 */
@Composable
fun rememberAppContainer(): AppContainer {
    val context = LocalContext.current
    return remember(context) { context.findAppContainer() }
}

private fun Context.findAppContainer(): AppContainer {
    var current: Context? = this
    while (current != null) {
        if (current is HasDefaultViewModelProviderFactory) {
            (current.defaultViewModelCreationExtras[APPLICATION_KEY] as? PetCareApp)
                ?.let { return it.container }
        }
        current = (current as? ContextWrapper)?.baseContext
    }
    return (applicationContext as PetCareApp).container
}
