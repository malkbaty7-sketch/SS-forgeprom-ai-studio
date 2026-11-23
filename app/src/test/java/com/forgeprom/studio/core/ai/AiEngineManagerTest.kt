package com.forgeprom.studio.core.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.forgeprom.studio.core.security.ApiKeyStore
import com.forgeprom.studio.core.settings.SettingsStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AiEngineManagerTest {

    private fun manager(): AiEngineManager {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteSharedPreferences("forgeprom_secure_keys")
        val settings = SettingsStore(context)
        val apiKeys = ApiKeyStore(context)
        return AiEngineManager(
            settingsStore = settings,
            apiKeyStore = apiKeys,
            neuralEngine = LocalNeuralEngine(context),
            localEngine = LocalRulesEngine(),
            geminiEngine = GeminiEngine(apiKeys),
            openAiEngine = OpenAiCompatEngine(apiKeys)
        )
    }

    @Test
    fun cloudGateBlocksWhenConsentIsNotGranted() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = SettingsStore(context)
        settings.setCloudConsent(false)
        val manager = manager()

        val result = manager.cloudGate()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }

    @Test
    fun cloudGateAllowsWhenConsentIsGranted() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = SettingsStore(context)
        settings.setCloudConsent(true)
        val manager = manager()

        val result = manager.cloudGate()
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().cloudConsent)
    }
}
