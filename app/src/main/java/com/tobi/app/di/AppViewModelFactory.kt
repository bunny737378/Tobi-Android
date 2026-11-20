package com.tobi.app.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tobi.app.feature.connectors.ConnectorManagerViewModel
import com.tobi.app.feature.details.DetailsViewModel
import com.tobi.app.feature.downloads.DownloadsViewModel
import com.tobi.app.feature.home.HomeViewModel
import com.tobi.app.feature.library.LibraryViewModel
import com.tobi.app.feature.search.SearchViewModel
import com.tobi.app.feature.settings.SettingsViewModel

class AppViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> HomeViewModel(container) as T
            modelClass.isAssignableFrom(SearchViewModel::class.java) -> SearchViewModel(container) as T
            modelClass.isAssignableFrom(DetailsViewModel::class.java) -> DetailsViewModel(container) as T
            modelClass.isAssignableFrom(LibraryViewModel::class.java) -> LibraryViewModel(container) as T
            modelClass.isAssignableFrom(DownloadsViewModel::class.java) -> DownloadsViewModel(container) as T
            modelClass.isAssignableFrom(ConnectorManagerViewModel::class.java) -> ConnectorManagerViewModel(container) as T
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(container) as T
            else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
