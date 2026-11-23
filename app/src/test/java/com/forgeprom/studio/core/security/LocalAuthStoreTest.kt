package com.forgeprom.studio.core.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class LocalAuthStoreTest {

    private fun store(): LocalAuthStore {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteSharedPreferences("forgeprom_local_account")
        return LocalAuthStore(context)
    }

    @Test
    fun hasAccountIsFalseUntilAccountIsCreated() {
        val auth = store()
        assertFalse(auth.hasAccount())
        auth.createAccount("user@example.com", "password123".toCharArray()).getOrThrow()
        assertTrue(auth.hasAccount())
    }

    @Test
    fun createAccountStoresCredentialsAndAuthenticateVerifiesThem() {
        val auth = store()
        auth.createAccount("User@Example.com", "password123".toCharArray()).getOrThrow()

        assertTrue(auth.authenticate("user@example.com", "password123".toCharArray()).getOrThrow())
        assertFalse(auth.authenticate("user@example.com", "wrong-pass".toCharArray()).getOrThrow())
        assertFalse(auth.authenticate("other@example.com", "password123".toCharArray()).getOrThrow())
    }

    @Test
    fun createAccountRejectsDuplicateLocalAccount() {
        val auth = store()
        auth.createAccount("user@example.com", "password123".toCharArray()).getOrThrow()

        val result = auth.createAccount("another@example.com", "password123".toCharArray())
        assertTrue(result.isFailure)
    }

    @Test
    fun passwordCharArrayIsClearedAfterOperations() {
        val auth = store()
        val createPassword = "password123".toCharArray()
        auth.createAccount("user@example.com", createPassword).getOrThrow()
        assertArrayEquals(CharArray(createPassword.size), createPassword)

        val authPassword = "password123".toCharArray()
        auth.authenticate("user@example.com", authPassword).getOrThrow()
        assertArrayEquals(CharArray(authPassword.size), authPassword)
    }
}
