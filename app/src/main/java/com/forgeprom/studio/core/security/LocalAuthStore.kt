package com.forgeprom.studio.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * حساب محلي للجهاز فقط؛ لا يُرسل البريد أو كلمة المرور إلى أي خدمة.
 * يُحفظ البريد ومشتق كلمة المرور داخل EncryptedSharedPreferences، ولا تحفظ كلمة المرور نفسها.
 */
class LocalAuthStore private constructor(
    private val preferencesFactory: () -> SharedPreferences
) {
    /** Production constructor: credentials are always backed by encrypted preferences. */
    constructor(context: Context) : this({
        val appContext = context.applicationContext
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    })

    /** Test-only seam: Robolectric does not provide AndroidKeyStore. */
    internal constructor(testPreferences: SharedPreferences) : this({ testPreferences })

    private val prefs: SharedPreferences by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        preferencesFactory()
    }

    fun hasAccount(): Boolean = prefs.contains(KEY_EMAIL)

    @Synchronized
    fun createAccount(email: String, password: CharArray): Result<Unit> = try {
        runCatching {
            check(!prefs.contains(KEY_EMAIL)) { "يوجد حساب محلي بالفعل على هذا الجهاز." }
            val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
            val hash = derive(password, salt)
            check(prefs.edit()
                .putString(KEY_EMAIL, email.trim().lowercase(Locale.ROOT))
                .putString(KEY_SALT, salt.toHex())
                .putString(KEY_HASH, hash.toHex())
                .commit()) { "تعذر حفظ الحساب بأمان." }
        }
    } finally {
        password.fill('\u0000')
    }

    fun authenticate(email: String, password: CharArray): Result<Boolean> = try {
        runCatching {
            val savedEmail = prefs.getString(KEY_EMAIL, null) ?: return@runCatching false
            val salt = prefs.getString(KEY_SALT, null)?.hexToBytes() ?: return@runCatching false
            val expected = prefs.getString(KEY_HASH, null)?.hexToBytes() ?: return@runCatching false
            if (!savedEmail.equals(email.trim(), ignoreCase = true)) return@runCatching false
            MessageDigest.isEqual(expected, derive(password, salt))
        }
    } finally {
        password.fill('\u0000')
    }

    private fun derive(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, HASH_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0) { "بيانات الحساب غير صالحة." }
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    companion object {
        private const val PREFS_NAME = "forgeprom_local_account"
        private const val KEY_EMAIL = "email"
        private const val KEY_SALT = "password_salt"
        private const val KEY_HASH = "password_hash"
        private const val SALT_BYTES = 16
        private const val HASH_BITS = 256
        private const val PBKDF2_ITERATIONS = 210_000
    }
}
