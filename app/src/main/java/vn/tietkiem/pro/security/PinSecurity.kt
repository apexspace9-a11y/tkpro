package vn.tietkiem.pro.security

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PinSecurity {
    private const val ITERATIONS = 150_000
    private const val KEY_LENGTH = 256

    fun create(pin: String): Pair<String, String> {
        require(pin.length in 4..12 && pin.all(Char::isDigit))
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = derive(pin, salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP) to Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    fun verify(pin: String, saltBase64: String, hashBase64: String): Boolean {
        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val expected = Base64.decode(hashBase64, Base64.NO_WRAP)
        val actual = derive(pin, salt)
        if (expected.size != actual.size) return false
        var diff = 0
        for (i in expected.indices) diff = diff or (expected[i].toInt() xor actual[i].toInt())
        return diff == 0
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }
}
