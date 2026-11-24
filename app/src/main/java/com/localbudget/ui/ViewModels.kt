package com.localbudget.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localbudget.AppContainer
import com.localbudget.container

/** Creates a ViewModel scoped to the current navigation destination, wired to the app container. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(noinline create: (AppContainer) -> VM): VM {
    val container = LocalContext.current.container
    return viewModel { create(container) }
}

/** Convenient access to the app container from composables. */
@Composable
fun appContainer(): AppContainer = LocalContext.current.container
