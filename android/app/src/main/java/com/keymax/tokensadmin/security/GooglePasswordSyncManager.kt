package com.keymax.tokensadmin.security

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CreatePasswordResponse
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException

class GooglePasswordSyncManager(private val context: Context) {

    private val credentialManager = CredentialManager.create(context)

    suspend fun sincronizarDesdeGoogle(): Pair<String, String>? {
        return try {
            val getPasswordOption = GetPasswordOption()
            val request = GetCredentialRequest(listOf(getPasswordOption))
            val result = credentialManager.getCredential(context, request)
            when (val credential = result.credential) {
                is PasswordCredential -> {
                    Pair(credential.id, credential.password)
                }
                else -> null
            }
        } catch (e: GetCredentialException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun sincronizarHaciaGoogle(username: String, password: String): Boolean {
        return try {
            val request = CreatePasswordRequest(id = username, password = password)
            val response = credentialManager.createCredential(context, request)
            response is CreatePasswordResponse
        } catch (e: CreateCredentialException) {
            false
        } catch (e: Exception) {
            false
        }
    }
}
