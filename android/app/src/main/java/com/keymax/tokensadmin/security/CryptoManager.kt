package com.keymax.tokensadmin.security

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
 * Cifra los campos sensibles (Token y Contrasena) con AES-256-GCM usando una
 * clave no exportable generada y custodiada por el Android Keystore.
 *
 * Formato almacenado en SQLite: `enc:v1:<iv_base64>:<cifrado_base64>`.
 * Si un valor no empieza por `enc:v1:` se devuelve tal cual, lo que permite
 * seguir leyendo una `tokens.db` importada del escritorio (datos en claro).
 */
object CryptoManager {

    private const val TAG = "CryptoManager"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "tokens_admin_aes"
    private const val PREFIJO = "enc:v1:"
    private const val SEPARADOR = ":"
    private const val TRANSFORMACION = "AES/GCM/NoPadding"
    private const val TAMANO_TAG_BITS = 128

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(KEYSTORE).apply { load(null) }
    }

    fun cifrarOrNull(valor: String?): String? {
        if (valor.isNullOrEmpty()) return null
        return runCatching { cifrar(valor) }.getOrElse {
            Log.e(TAG, "No se pudo cifrar el valor", it)
            null
        }
    }

    fun descifrar(almacenado: String?): String {
        if (almacenado.isNullOrEmpty()) return ""
        // Dato heredado sin cifrar (importado del escritorio).
        if (!almacenado.startsWith(PREFIJO)) return almacenado
        return runCatching { descifrarCifrado(almacenado) }.getOrElse {
            Log.e(TAG, "No se pudo descifrar el valor", it)
            ""
        }
    }

    private fun cifrar(valor: String): String {
        val cipher = Cipher.getInstance(TRANSFORMACION)
        cipher.init(Cipher.ENCRYPT_MODE, obtenerClave())
        val cifrado = cipher.doFinal(valor.toByteArray(Charsets.UTF_8))
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val datos = Base64.encodeToString(cifrado, Base64.NO_WRAP)
        return PREFIJO + iv + SEPARADOR + datos
    }

    private fun descifrarCifrado(almacenado: String): String {
        val partes = almacenado.removePrefix(PREFIJO).split(SEPARADOR, limit = 2)
        require(partes.size == 2) { "Formato de dato cifrado inválido" }

        val iv = Base64.decode(partes[0], Base64.NO_WRAP)
        val datos = Base64.decode(partes[1], Base64.NO_WRAP)

        val cipher = Cipher.getInstance(TRANSFORMACION)
        cipher.init(Cipher.DECRYPT_MODE, obtenerClave(), GCMParameterSpec(TAMANO_TAG_BITS, iv))
        return String(cipher.doFinal(datos), Charsets.UTF_8)
    }

    @Synchronized
    private fun obtenerClave(): SecretKey {
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generador.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generador.generateKey()
    }
}
