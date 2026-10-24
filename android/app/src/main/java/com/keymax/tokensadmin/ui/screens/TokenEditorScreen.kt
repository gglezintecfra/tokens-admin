package com.keymax.tokensadmin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.keymax.tokensadmin.R
import com.keymax.tokensadmin.data.TipoAutenticacion
import com.keymax.tokensadmin.data.Token

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenEditorScreen(
    original: Token?,
    onCancel: () -> Unit,
    onSave: (Token) -> Unit,
    onDelete: (Token) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val clave = original?.id

    var aplicacion by rememberSaveable(clave) { mutableStateOf(original?.aplicacion.orEmpty()) }
    var tipoValor by rememberSaveable(clave) {
        mutableStateOf((original?.tipo ?: TipoAutenticacion.TOKEN).valor)
    }
    var token by rememberSaveable(clave) { mutableStateOf(original?.token.orEmpty()) }
    var usuario by rememberSaveable(clave) { mutableStateOf(original?.usuario.orEmpty()) }
    var contrasena by rememberSaveable(clave) { mutableStateOf(original?.contrasena.orEmpty()) }
    var mostrarContrasena by rememberSaveable { mutableStateOf(false) }
    var intentoGuardar by rememberSaveable { mutableStateOf(false) }

    val tipo = TipoAutenticacion.desdeValor(tipoValor)
    val errorAplicacion = intentoGuardar && aplicacion.isBlank()
    val focusAplicacion = remember { FocusRequester() }

    LaunchedEffect(clave) {
        if (original == null) {
            focusAplicacion.requestFocus()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (original == null) {
                                R.string.editor_titulo_nuevo
                            } else {
                                R.string.editor_titulo_editar
                            }
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.accion_volver)
                        )
                    }
                },
                actions = {
                    if (original != null) {
                        IconButton(onClick = { onDelete(original) }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.accion_eliminar),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = aplicacion,
                onValueChange = { aplicacion = it },
                label = { Text(stringResource(R.string.campo_aplicacion)) },
                singleLine = true,
                isError = errorAplicacion,
                supportingText = {
                    if (errorAplicacion) {
                        Text(stringResource(R.string.campo_aplicacion_error))
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusAplicacion)
            )

            Text(
                text = stringResource(R.string.campo_tipo),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TipoAutenticacion.entries.forEach { opcion ->
                    FilterChip(
                        selected = tipo == opcion,
                        onClick = { tipoValor = opcion.valor },
                        label = { Text(opcion.etiqueta) }
                    )
                }
            }

            when (tipo) {
                TipoAutenticacion.TOKEN -> {
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text(stringResource(R.string.campo_token)) },
                        placeholder = { Text(stringResource(R.string.campo_token_ayuda)) },
                        minLines = 4,
                        maxLines = 12,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                TipoAutenticacion.TARJETA -> {
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text(stringResource(R.string.campo_numero_tarjeta)) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = usuario,
                        onValueChange = { usuario = it },
                        label = { Text(stringResource(R.string.campo_titular)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = contrasena,
                        onValueChange = { contrasena = it },
                        label = { Text(stringResource(R.string.campo_cvv)) },
                        singleLine = true,
                        visualTransformation = if (mostrarContrasena) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        trailingIcon = {
                            TextButton(onClick = { mostrarContrasena = !mostrarContrasena }) {
                                Text(
                                    stringResource(
                                        if (mostrarContrasena) {
                                            R.string.campo_ocultar
                                        } else {
                                            R.string.campo_mostrar
                                        }
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                TipoAutenticacion.LASTPASS -> {
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text("ID de LastPass") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = usuario,
                        onValueChange = { usuario = it },
                        label = { Text(stringResource(R.string.campo_usuario)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = contrasena,
                        onValueChange = { contrasena = it },
                        label = { Text(stringResource(R.string.campo_contrasena)) },
                        singleLine = true,
                        visualTransformation = if (mostrarContrasena) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            TextButton(onClick = { mostrarContrasena = !mostrarContrasena }) {
                                Text(
                                    stringResource(
                                        if (mostrarContrasena) {
                                            R.string.campo_ocultar
                                        } else {
                                            R.string.campo_mostrar
                                        }
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                TipoAutenticacion.USUARIO_CONTRASENA -> {
                    OutlinedTextField(
                        value = usuario,
                        onValueChange = { usuario = it },
                        label = { Text(stringResource(R.string.campo_usuario)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = contrasena,
                        onValueChange = { contrasena = it },
                        label = { Text(stringResource(R.string.campo_contrasena)) },
                        singleLine = true,
                        visualTransformation = if (mostrarContrasena) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            TextButton(onClick = { mostrarContrasena = !mostrarContrasena }) {
                                Text(
                                    stringResource(
                                        if (mostrarContrasena) {
                                            R.string.campo_ocultar
                                        } else {
                                            R.string.campo_mostrar
                                        }
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (original != null &&
                (original.fechaCreacion.isNotBlank() || original.fechaModificacion.isNotBlank())
            ) {
                Text(
                    text = stringResource(
                        R.string.fechas,
                        original.fechaCreacion.ifBlank { "—" },
                        original.fechaModificacion.ifBlank { "—" }
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = {
                    intentoGuardar = true
                    if (aplicacion.isNotBlank()) {
                        onSave(construirToken(original, aplicacion, tipo, token, usuario, contrasena))
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(stringResource(R.string.accion_guardar))
            }
        }
    }
}

private fun construirToken(
    original: Token?,
    aplicacion: String,
    tipo: TipoAutenticacion,
    token: String,
    usuario: String,
    contrasena: String
): Token = (original ?: Token()).copy(
    aplicacion = aplicacion.trim(),
    tipo = tipo,
    token = if (tipo == TipoAutenticacion.TOKEN) token.trim() else "",
    usuario = if (tipo == TipoAutenticacion.USUARIO_CONTRASENA) usuario.trim() else "",
    contrasena = if (tipo == TipoAutenticacion.USUARIO_CONTRASENA) contrasena else ""
)
