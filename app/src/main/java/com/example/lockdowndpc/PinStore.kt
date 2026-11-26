package com.example.lockdowndpc

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** A salted PBKDF2 hash; production builds should also add retry throttling and a recovery workflow. */
object PinStore {
    private const val PREFS = "local_admin_auth"
    private const val SALT = "salt"
    private const val HASH = "hash"
    private const val ITERATIONS = 150_000
    private const val KEY_BITS = 256

    fun hasPin(context: Context): Boolean {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return !p.getString(HASH, null).isNullOrBlank()
    }

    fun setPin(context: Context, pin: String) {
        require(pin.length >= 6 && pin.all { it.isDigit() }) { "Admin PIN must be at least 6 digits." }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val digest = derive(pin, salt)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(SALT, salt.joinToString("") { "%02x".format(it) })
            .putString(HASH, digest.joinToString("") { "%02x".format(it) })
            .apply()
    }

    fun verify(context: Context, pin: String): Boolean {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saltHex = p.getString(SALT, null) ?: return false
        val expectedHex = p.getString(HASH, null) ?: return false
        val salt = try {
            saltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } catch (_: Exception) {
            return false
        }
        val expected = try {
            expectedHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } catch (_: Exception) {
            return false
        }
        return MessageDigest.isEqual(expected, derive(pin, salt))
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
