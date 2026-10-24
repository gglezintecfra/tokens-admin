package com.keymax.tokensadmin

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.keymax.tokensadmin.ui.TokenViewModel
import com.keymax.tokensadmin.ui.TokensAdminApp
import com.keymax.tokensadmin.ui.screens.LockScreen
import com.keymax.tokensadmin.ui.theme.TokensAdminTheme

/**
 * Punto de entrada. Antes de mostrar la lista exige la credencial del
 * dispositivo (huella/rostro/PIN/patrón), equivalente al "login" del script
 * original pero usando el bloqueo del sistema.
 */
class MainActivity : FragmentActivity() {

    private val viewModel: TokenViewModel by viewModels()

    private var desbloqueado by mutableStateOf(false)
    private var mensajeBloqueo by mutableStateOf<String?>(null)
    private var autenticando by mutableStateOf(false)
    private var bloqueoDisponible by mutableStateOf(true)
    private var momentoSalida: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        bloqueoDisponible = hayAutenticacionDispositivo()
        desbloqueado = savedInstanceState?.getBoolean(ESTADO_DESBLOQUEADO) ?: !bloqueoDisponible

        setContent {
            TokensAdminTheme {
                if (desbloqueado || !bloqueoDisponible) {
                    TokensAdminApp(
                        viewModel = viewModel,
                        lockAvailable = bloqueoDisponible
                    )
                } else {
                    LockScreen(
                        message = mensajeBloqueo,
                        busy = autenticando,
                        onUnlock = ::solicitarAutenticacion
                    )
                }
            }
        }

        if (savedInstanceState == null && bloqueoDisponible) {
            solicitarAutenticacion()
        }
    }

    override fun onStart() {
        super.onStart()
        if (momentoSalida > 0L) {
            val transcurrido = System.currentTimeMillis() - momentoSalida
            if (bloqueoDisponible && transcurrido > TIEMPO_REBLOQUEO_MS) {
                desbloqueado = false
                mensajeBloqueo = null
            }
            momentoSalida = 0L
        }
    }

    override fun onStop() {
        super.onStop()
        momentoSalida = System.currentTimeMillis()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(ESTADO_DESBLOQUEADO, desbloqueado)
    }

    private fun autenticadores(): Int =
        Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL

    private fun hayAutenticacionDispositivo(): Boolean =
        BiometricManager.from(this).canAuthenticate(autenticadores()) ==
            BiometricManager.BIOMETRIC_SUCCESS

    private fun solicitarAutenticacion() {
        if (!bloqueoDisponible) {
            desbloqueado = true
            return
        }
        if (autenticando) return

        autenticando = true
        mensajeBloqueo = null

        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {

                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    autenticando = false
                    mensajeBloqueo = null
                    desbloqueado = true
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    autenticando = false
                    mensajeBloqueo = when (errorCode) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_CANCELED -> getString(R.string.lock_cancelado)

                        else -> errString.toString()
                    }
                }

                override fun onAuthenticationFailed() {
                    mensajeBloqueo = getString(R.string.lock_fallido)
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.app_name))
            .setSubtitle(getString(R.string.lock_subtitle))
            .setAllowedAuthenticators(autenticadores())
            .setConfirmationRequired(false)
            .build()

        prompt.authenticate(info)
    }

    private companion object {
        const val ESTADO_DESBLOQUEADO = "desbloqueado"
        const val TIEMPO_REBLOQUEO_MS = 30_000L
    }
}
