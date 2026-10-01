package pl.nju.opencode.ui

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Password storage for silent re-login, encrypted with AES-256-GCM.
 *
 * The key lives in the Android Keystore and is generated with
 * [KeyGenParameterSpec.Builder] defaults, which mark it non-exportable: it
 * never exists as bytes the app can hand out, so nothing recoverable is ever
 * written to disk — only the ciphertext and the per-write IV.
 *
 * This matters because the obvious implementation (a fixed key compiled into
 * the APK) would be theatre: anyone who pulls the APK can read the key and
 * decrypt the file in seconds. Hardware-backed key storage is the only thing
 * that makes "encrypted at rest" mean anything here.
 *
 * Stated plainly, because it is the honest limit rather than a defect: this
 * defends against offline extraction of app data and against backups. It does
 * not defend against a rooted device, where code running as this app's UID
 * can simply ask the Keystore to decrypt. Any scheme where the app itself
 * must be able to recover the password has that same ceiling — the only way
 * to remove it is not to store the password at all.
 */
object SecureCredentials {

    private const val TAG = "NjuCreds"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "nju_password_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128

    private const val PREFS = "nju_prefs"
    private const val KEY_PASSWORD = "password_enc"

    private fun prefs(context: Context): android.content.SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }

        val generator =
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    /** Persists [password] as `iv:ciphertext`, both Base64, Keystore-sealed. */
    fun save(context: Context, password: String) {
        if (password.isBlank()) {
            clear(context)
            return
        }
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
            val payload =
                Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
                    Base64.encodeToString(encrypted, Base64.NO_WRAP)
            prefs(context).edit().putString(KEY_PASSWORD, payload).apply()
            Log.i(TAG, "password stored (AES-256-GCM, keystore key)")
        } catch (e: Exception) {
            // Never fail a login over storage — worst case the user re-enters
            // the password next time.
            Log.w(TAG, "store failed: ${e.javaClass.simpleName}")
        }
    }

    /** Decrypts the stored password, or null if absent/unreadable/stale key. */
    fun load(context: Context): String? {
        val payload = prefs(context).getString(KEY_PASSWORD, null) ?: return null
        return try {
            val parts = payload.split(":", limit = 2)
            if (parts.size != 2) return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key(),
                GCMParameterSpec(TAG_BITS, Base64.decode(parts[0], Base64.NO_WRAP))
            )
            String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "decrypt failed: ${e.javaClass.simpleName}")
            null
        }
    }

    fun has(context: Context): Boolean = prefs(context).contains(KEY_PASSWORD)

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_PASSWORD).apply()
    }
}
