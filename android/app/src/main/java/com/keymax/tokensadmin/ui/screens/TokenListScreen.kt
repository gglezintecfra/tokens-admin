package com.keymax.tokensadmin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.keymax.tokensadmin.R
import com.keymax.tokensadmin.data.TipoAutenticacion
import com.keymax.tokensadmin.data.Token

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenListScreen(
    tokens: List<Token>,
    query: String,
    lockAvailable: Boolean,
    onQueryChange: (String) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Token) -> Unit,
    onCopy: (Token) -> Unit,
    onImportCsv: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onSyncGoogleIn: () -> Unit,
    onSyncGoogleOut: () -> Unit,
    onSyncGoogle1Click: () -> Unit,
    onOpenWebView: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.lista_subtitulo, tokens.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        var menuExpandido by remember { mutableStateOf(false) }
                        IconButton(onClick = { menuExpandido = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.menu_opciones)
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpandido,
                            onDismissRequest = { menuExpandido = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_sync_google_1click)) },
                                onClick = {
                                    menuExpandido = false
                                    onSyncGoogle1Click()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_sync_google_in)) },
                                onClick = {
                                    menuExpandido = false
                                    onSyncGoogleIn()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_sync_google_out)) },
                                onClick = {
                                    menuExpandido = false
                                    onSyncGoogleOut()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_abrir_webview_google)) },
                                onClick = {
                                    menuExpandido = false
                                    onOpenWebView()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_importar_google)) },
                                onClick = {
                                    menuExpandido = false
                                    onImportCsv()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_respaldar)) },
                                onClick = {
                                    menuExpandido = false
                                    onExportBackup()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_restaurar)) },
                                onClick = {
                                    menuExpandido = false
                                    onImportBackup()
                                }
                            )
                        }
                    }
                )

                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text(stringResource(R.string.lista_buscar)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.accion_limpiar)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.accion_nuevo)) }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!lockAvailable) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.aviso_sin_bloqueo),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            if (tokens.isEmpty()) {
                EstadoVacio(query = query, modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(tokens, key = { it.id }) { token ->
                        TokenCard(
                            token = token,
                            onClick = { onEdit(token) },
                            onCopy = { onCopy(token) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TokenCard(
    token: Token,
    onClick: () -> Unit,
    onCopy: () -> Unit
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = token.aplicacion,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = token.tipo.etiqueta,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            when (token.tipo) {
                TipoAutenticacion.TOKEN -> {
                    Text(
                        text = token.resumenToken.ifBlank { "—" },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TipoAutenticacion.TARJETA -> {
                    Text(
                        text = token.resumenToken,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (token.usuario.isNotBlank()) {
                        Text(
                            text = "Titular: ${token.usuario}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                TipoAutenticacion.LASTPASS -> {
                    Text(
                        text = "LastPass: ${token.usuario.ifBlank { "—" }}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TipoAutenticacion.USUARIO_CONTRASENA -> {
                    Text(
                        text = stringResource(R.string.lista_usuario, token.usuario.ifBlank { "—" }),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(
                            R.string.lista_contrasena,
                            "•".repeat(token.contrasena.length.coerceIn(0, 12)).ifEmpty { "—" }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (token.fechaModificacion.isNotBlank()) {
                    Text(
                        text = stringResource(
                            R.string.lista_modificado,
                            token.fechaModificacion
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                TextButton(
                    onClick = onCopy,
                    enabled = token.secreto.isNotEmpty()
                ) {
                    Text(stringResource(R.string.accion_copiar))
                }
            }
        }
    }
}

@Composable
private fun EstadoVacio(query: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(12.dp))

        if (query.isBlank()) {
            Text(
                text = stringResource(R.string.lista_vacia_titulo),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.lista_vacia_descripcion),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = stringResource(R.string.lista_sin_resultados, query),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}
