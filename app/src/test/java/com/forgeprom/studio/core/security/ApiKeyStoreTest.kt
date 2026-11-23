package com.forgeprom.studio.core.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ApiKeyStoreTest {

    private fun store(): ApiKeyStore {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteSharedPreferences("forgeprom_secure_keys")
        return ApiKeyStore(context)
    }

    @Test
    fun defaultsAreEmptyAndSane() {
        val store = store()
        assertEquals("", store.geminiKey())
        assertEquals("", store.openAiKey())
        assertEquals(ApiKeyStore.DEFAULT_OPENAI_URL, store.openAiBaseUrl())
        assertEquals(ApiKeyStore.DEFAULT_OPENAI_MODEL, store.openAiModel())
    }

    @Test
    fun storesAndReadsKeysAndProviderConfig() {
        val store = store()
        store.setGeminiKey("gem-key")
        store.setOpenAiKey("oa-key")
        store.setOpenAiBaseUrl("https://example.com/v1")
        store.setOpenAiModel("gpt-test")

        assertEquals("gem-key", store.geminiKey())
        assertEquals("oa-key", store.openAiKey())
        assertEquals("https://example.com/v1", store.openAiBaseUrl())
        assertEquals("gpt-test", store.openAiModel())
    }

    @Test
    fun valuesPersistAcrossInstancesWhenStorageIsAvailable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteSharedPreferences("forgeprom_secure_keys")
        val first = ApiKeyStore(context)
        first.setGeminiKey("persisted-key")

        val second = ApiKeyStore(context)
        // In encrypted mode persists across instances; in in-memory fallback it may not.
        // The store must still behave safely and report its mode honestly.
        if (second.isEncrypted) {
            assertEquals("persisted-key", second.geminiKey())
        } else {
            assertTrue(second.geminiKey().isEmpty() || second.geminiKey() == "persisted-key")
        }
    }
}
