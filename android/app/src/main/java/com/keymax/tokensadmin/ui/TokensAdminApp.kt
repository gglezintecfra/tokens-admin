package com.keymax.tokensadmin.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keymax.tokensadmin.R
import com.keymax.tokensadmin.data.TipoAutenticacion
import com.keymax.tokensadmin.data.Token
import com.keymax.tokensadmin.ui.screens.GoogleWebViewScreen
import com.keymax.tokensadmin.ui.screens.TokenEditorScreen
import com.keymax.tokensadmin.ui.screens.TokenListScreen
import kotlinx.coroutines.launch

/**
 * Contenedor de la app: lista + editor, con confirmación de borrado y avisos.
 */
@Composable
fun TokensAdminApp(
    viewModel: TokenViewModel,
    lockAvailable: Boolean = true
) {
    val tokens by viewModel.tokens.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var creando by remember { mutableStateOf(false) }
    var editando by remember { mutableStateOf<Token?>(null) }
    var porEliminar by remember { mutableStateOf<Token?>(null) }
    var mostrandoWebView by remember { mutableStateOf(false) }

    val enEditor = creando || editando != null

    val msgGuardado = stringResource(R.string.msg_guardado)
    val msgEliminado = stringResource(R.string.msg_eliminado)
    val msgCopiado = stringResource(R.string.msg_copiado)
    val msgNadaQueCopiar = stringResource(R.string.msg_nada_que_copiar)
    val msgErrorGuardar = stringResource(R.string.msg_error_guardar)
    val msgErrorEliminar = stringResource(R.string.msg_error_eliminar)

    val msgImportacionExitosa = stringResource(R.string.msg_importacion_exitosa)
    val msgImportacionError = stringResource(R.string.msg_importacion_error)
    val msgRespaldoExitoso = stringResource(R.string.msg_respaldo_exitoso)
    val msgRespaldoError = stringResource(R.string.msg_respaldo_error)
    val msgRestauracionExitosa = stringResource(R.string.msg_restauracion_exitosa)
    val msgRestauracionError = stringResource(R.string.msg_restauracion_error)

    val launcherImportarCsv = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val resultado = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        viewModel.importarCsvGoogle(input)
                    } ?: 0
                }
                if (resultado.isSuccess) {
                    val count = resultado.getOrDefault(0)
                    snackbarHostState.showSnackbar(msgImportacionExitosa.format(count))
                } else {
                    snackbarHostState.showSnackbar(msgImportacionError)
                }
            }
        }
    }

    val launcherRespaldar = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val resultado = runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        viewModel.exportarRespaldo(output)
                    }
                }
                if (resultado.isSuccess) {
                    snackbarHostState.showSnackbar(msgRespaldoExitoso)
                } else {
                    snackbarHostState.showSnackbar(msgRespaldoError)
                }
            }
        }
    }

    val launcherRestaurar = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val resultado = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        viewModel.importarRespaldo(input)
                    }
                }
                if (resultado.isSuccess) {
                    snackbarHostState.showSnackbar(msgRestauracionExitosa)
                } else {
                    snackbarHostState.showSnackbar(msgRestauracionError)
                }
            }
        }
    }

    BackHandler(enabled = enEditor || mostrandoWebView) {
        if (mostrandoWebView) {
            mostrandoWebView = false
        } else {
            creando = false
            editando = null
        }
    }

    val msgSyncInExito = stringResource(R.string.msg_sync_google_in_exito)
    val msgSyncInVacio = stringResource(R.string.msg_sync_google_in_vacio)
    val msgSyncOutExito = stringResource(R.string.msg_sync_google_out_exito)
    val msgSync1ClickExito = stringResource(R.string.msg_sync_google_1click_exito)

    val onSyncGoogle1Click: () -> Unit = {
        mostrandoWebView = true
        Unit
    }

    val onSyncGoogleIn: () -> Unit = {
        scope.launch {
            val exito = viewModel.sincronizarDesdeGooglePasswordManager()
            if (exito) {
                snackbarHostState.showSnackbar(msgSyncInExito)
            } else {
                snackbarHostState.showSnackbar(msgSyncInVacio)
            }
        }
        Unit
    }

    val onSyncGoogleOut: () -> Unit = {
        scope.launch {
            val tokenUserPass = tokens.firstOrNull { it.tipo == TipoAutenticacion.USUARIO_CONTRASENA }
            if (tokenUserPass != null) {
                val exito = viewModel.sincronizarHaciaGooglePasswordManager(tokenUserPass)
                if (exito) {
                    snackbarHostState.showSnackbar(msgSyncOutExito)
                } else {
                    snackbarHostState.showSnackbar("No se pudo guardar en Google Passwords")
                }
            } else {
                snackbarHostState.showSnackbar("No hay credenciales de usuario/contraseña para exportar")
            }
        }
        Unit
    }

    if (mostrandoWebView) {
        GoogleWebViewScreen(onBack = { mostrandoWebView = false })
    } else if (enEditor) {
        TokenEditorScreen(
            original = editando,
            onCancel = {
                creando = false
                editando = null
            },
            onDelete = { token -> porEliminar = token },
            onSave = { token ->
                scope.launch {
                    val resultado = runCatching { viewModel.save(token) }
                    if (resultado.isSuccess) {
                        creando = false
                        editando = null
                        snackbarHostState.showSnackbar(msgGuardado)
                    } else {
                        snackbarHostState.showSnackbar(msgErrorGuardar)
                    }
                }
            },
            snackbarHostState = snackbarHostState
        )
    } else {
        TokenListScreen(
            tokens = tokens,
            query = query,
            lockAvailable = lockAvailable,
            onQueryChange = viewModel::onQueryChange,
            onAdd = { creando = true },
            onEdit = { token -> editando = token },
            onCopy = { token ->
                if (token.secreto.isEmpty()) {
                    scope.launch { snackbarHostState.showSnackbar(msgNadaQueCopiar) }
                } else {
                    copiarAlPortapapeles(context, token.secreto)
                    scope.launch { snackbarHostState.showSnackbar(msgCopiado) }
                }
            },
            onImportCsv = { launcherImportarCsv.launch(arrayOf("text/*", "application/vnd.ms-excel", "text/csv")) },
            onExportBackup = { launcherRespaldar.launch("tokens_backup.db") },
            onImportBackup = { launcherRestaurar.launch(arrayOf("*/*")) },
            onSyncGoogleIn = onSyncGoogleIn,
            onSyncGoogleOut = onSyncGoogleOut,
            onSyncGoogle1Click = onSyncGoogle1Click,
            onOpenWebView = { mostrandoWebView = true },
            snackbarHostState = snackbarHostState
        )
    }

    val objetivo = porEliminar
    if (objetivo != null) {
        AlertDialog(
            onDismissRequest = { porEliminar = null },
            title = { Text(stringResource(R.string.eliminar_titulo)) },
            text = { Text(stringResource(R.string.eliminar_mensaje, objetivo.aplicacion)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        porEliminar = null
                        scope.launch {
                            val resultado = runCatching { viewModel.delete(objetivo.id) }
                            if (resultado.isSuccess) {
                                if (editando?.id == objetivo.id) {
                                    editando = null
                                }
                                creando = false
                                snackbarHostState.showSnackbar(msgEliminado)
                            } else {
                                snackbarHostState.showSnackbar(msgErrorEliminar)
                            }
                        }
                    }
                ) {
                    Text(
                        text = stringResource(R.string.accion_eliminar),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { porEliminar = null }) {
                    Text(stringResource(R.string.accion_cancelar))
                }
            }
        )
    }
}
