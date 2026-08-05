package com.lecheeel.memento.crypto

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedPayload(
    val ivBase64: String,
    val ciphertextBase64: String,
    val hmacBase64: String,
)

class CryptoManager(secret: String) {
    private val aesKey: SecretKey
    private val hmacKey: SecretKey
    private val secureRandom = SecureRandom()

    init {
        val material = MessageDigest.getInstance("SHA-256").digest(secret.toByteArray(Charsets.UTF_8))
        aesKey = SecretKeySpec(material, "AES")
        hmacKey = SecretKeySpec(material, "HmacSHA256")
    }

    fun encrypt(plainText: String, associatedData: String): EncryptedPayload {
        val iv = ByteArray(12).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
        cipher.updateAAD(associatedData.toByteArray(Charsets.UTF_8))
        val ciphertext = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val ciphertextBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        val hmac = sign("$ivBase64.$ciphertextBase64.$associatedData")
        return EncryptedPayload(ivBase64, ciphertextBase64, hmac)
    }

    fun sign(data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(hmacKey)
        return Base64.encodeToString(mac.doFinal(data.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
}

