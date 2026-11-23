package com.forgeprom.studio.core.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SettingsStoreTest {

    private fun store(): SettingsStore {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return SettingsStore(context)
    }

    @Test
    fun defaultsAreSafeAndLocal() = runBlocking {
        val settings = store()
        settings.setThemeMode(ThemeMode.SYSTEM)
        settings.setPromptLanguage(PromptLanguage.AR)
        settings.setCloudConsent(false)
        settings.setEngineForTask("general", "local.rules")
        settings.setEngineForTask("code", "local.rules")
        settings.setEngineForTask("analysis", "local.rules")

        val s = settings.settings.first()
        assertEquals(ThemeMode.SYSTEM, s.themeMode)
        assertEquals(PromptLanguage.AR, s.promptLanguage)
        assertFalse(s.cloudConsent)
        assertEquals("local.rules", s.engineGeneral)
        assertEquals("local.rules", s.engineCode)
        assertEquals("local.rules", s.engineAnalysis)
    }

    @Test
    fun updatesPersistedValues() = runBlocking {
        val settings = store()
        settings.setThemeMode(ThemeMode.DARK)
        settings.setPromptLanguage(PromptLanguage.EN)
        settings.setCloudConsent(true)
        settings.setEngineForTask("code", "cloud.gemini")

        val s = settings.settings.first()
        assertEquals(ThemeMode.DARK, s.themeMode)
        assertEquals(PromptLanguage.EN, s.promptLanguage)
        assertEquals(true, s.cloudConsent)
        assertEquals("cloud.gemini", s.engineCode)
    }
}
