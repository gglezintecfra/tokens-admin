package com.keymax.tokensadmin.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.keymax.tokensadmin.data.TipoAutenticacion
import com.keymax.tokensadmin.data.Token
import com.keymax.tokensadmin.data.TokenRepository
import com.keymax.tokensadmin.security.GooglePasswordSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

class TokenViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TokenRepository(application)
    private val googleSyncManager = GooglePasswordSyncManager(application)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _tokens = MutableStateFlow<List<Token>>(emptyList())
    val tokens: StateFlow<List<Token>> = _tokens.asStateFlow()

    private var trabajoBusqueda: Job? = null

    init {
        refresh()
    }

    fun onQueryChange(valor: String) {
        _query.value = valor
        refresh()
    }

    fun refresh() {
        trabajoBusqueda?.cancel()
        trabajoBusqueda = viewModelScope.launch {
            _tokens.value = buscarEnHiloSecundario()
        }
    }

    suspend fun save(token: Token) {
        withContext(Dispatchers.IO) { repository.save(token) }
        _tokens.value = buscarEnHiloSecundario()
    }

    suspend fun delete(id: Long) {
        withContext(Dispatchers.IO) { repository.delete(id) }
        _tokens.value = buscarEnHiloSecundario()
    }

    suspend fun importarCsvGoogle(inputStream: InputStream): Int =
        withContext(Dispatchers.IO) {
            val count = repository.importarCsvGoogle(inputStream)
            _tokens.value = buscarEnHiloSecundario()
            count
        }

    suspend fun exportarRespaldo(outputStream: OutputStream) =
        withContext(Dispatchers.IO) {
            repository.exportarBaseDatos(outputStream)
        }

    suspend fun importarRespaldo(inputStream: InputStream) =
        withContext(Dispatchers.IO) {
            repository.importarBaseDatos(inputStream)
            _tokens.value = buscarEnHiloSecundario()
        }

    suspend fun sincronizarGoogle1Click(): Boolean =
        withContext(Dispatchers.IO) {
            val par = googleSyncManager.sincronizarDesdeGoogle()
            if (par != null) {
                val (usuario, contrasena) = par
                val token = Token(
                    id = 0L,
                    aplicacion = "Google Password Manager (1-Click)",
                    tipo = TipoAutenticacion.USUARIO_CONTRASENA,
                    token = "",
                    usuario = usuario,
                    contrasena = contrasena
                )
                repository.save(token)
                _tokens.value = buscarEnHiloSecundario()
                true
            } else {
                false
            }
        }

    suspend fun sincronizarDesdeGooglePasswordManager(): Boolean = sincronizarGoogle1Click()

    suspend fun sincronizarHaciaGooglePasswordManager(token: Token): Boolean =
        withContext(Dispatchers.IO) {
            if (token.tipo == TipoAutenticacion.USUARIO_CONTRASENA && token.usuario.isNotBlank() && token.contrasena.isNotBlank()) {
                googleSyncManager.sincronizarHaciaGoogle(token.usuario, token.contrasena)
            } else {
                false
            }
        }

    private suspend fun buscarEnHiloSecundario(): List<Token> =
        withContext(Dispatchers.IO) { repository.search(_query.value) }
}
