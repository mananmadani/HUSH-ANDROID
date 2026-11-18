package app.hush.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Locks and unlocks backup files with a password.
 *
 * - The password is turned into a 256-bit key with PBKDF2 (HMAC-SHA-256, 210,000 rounds and a random
 *   salt), so guessing passwords is slow.
 * - The data is compressed, then encrypted with AES-256 in GCM mode. GCM also detects a wrong password
 *   and any change to the file, so a damaged or edited backup is refused instead of half-restored.
 * - File layout: "HUSHENC1" (8 bytes) + salt (16) + IV (12) + encrypted data.
 *
 * Nothing is sent anywhere, and Hush cannot recover a forgotten password.
 */
object BackupCrypto {

    private val MAGIC = "HUSHENC1".toByteArray(Charsets.US_ASCII)
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val TAG_BITS = 128
    private const val ROUNDS = 210_000

    const val MIN_PASSWORD = 8

    fun isEncrypted(file: ByteArray): Boolean =
        file.size > MAGIC.size + SALT_LEN + IV_LEN && file.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(MAGIC)
        val sealed = cipher.doFinal(gzip(plain))
        return MAGIC + salt + iv + sealed
    }

    /** Throws [GeneralSecurityException] for a wrong password or a damaged file. */
    fun decrypt(file: ByteArray, password: CharArray): ByteArray {
        if (!isEncrypted(file)) throw GeneralSecurityException("Not an encrypted Hush backup")
        val salt = file.copyOfRange(MAGIC.size, MAGIC.size + SALT_LEN)
        val iv = file.copyOfRange(MAGIC.size + SALT_LEN, MAGIC.size + SALT_LEN + IV_LEN)
        val sealed = file.copyOfRange(MAGIC.size + SALT_LEN + IV_LEN, file.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(MAGIC)
        return gunzip(cipher.doFinal(sealed))
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ROUNDS, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun gzip(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(data) }
        return out.toByteArray()
    }

    private fun gunzip(data: ByteArray): ByteArray =
        GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
}
