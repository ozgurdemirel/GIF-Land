package club.ozgur.gifland.di

import club.ozgur.gifland.domain.repository.MediaRepository
import club.ozgur.gifland.domain.service.RecordingService
import club.ozgur.gifland.platform.PlatformMediaRepository
import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import kotlinx.coroutines.runBlocking
import org.koin.dsl.module
import java.util.prefs.Preferences

/**
 * Platform-specific dependency injection module for JVM/Desktop.
 * Provides implementations that are specific to desktop platforms.
 */
val platformModule = module {

    // Settings implementation using Java Preferences
    single<Settings> {
        PreferencesSettings(
            Preferences.userNodeForPackage(Settings::class.java)
        )
    }

    // Recording service - platform-specific implementation that wraps the JVM Recorder
    single { RecordingService(get(), get()) }

    // Media repository with platform-specific file operations
    single<MediaRepository> {
        val platformRepo = runBlocking {
            PlatformMediaRepository.create()
        }
        // Return the base repository that PlatformMediaRepository delegates to
        MediaRepository()
    }

    // Also provide the platform repository for JVM-specific operations
    single {
        runBlocking {
            PlatformMediaRepository.create()
        }
    }
}