package com.keymax.tokensadmin.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val AzulOscuro = Color(0xFF1F4E79)
private val Azul = Color(0xFF2C6E9B)
private val AzulClaro = Color(0xFF9CCBF5)
private val Verde = Color(0xFF2E7D5B)

private val EsquemaClaro = lightColorScheme(
    primary = AzulOscuro,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4F7),
    onPrimaryContainer = Color(0xFF0A2540),
    secondary = Verde,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6EFE3),
    onSecondaryContainer = Color(0xFF06281B),
    background = Color(0xFFFBFDFF),
    surface = Color(0xFFFBFDFF)
)

private val EsquemaOscuro = darkColorScheme(
    primary = AzulClaro,
    onPrimary = Color(0xFF06233B),
    primaryContainer = Color(0xFF163A57),
    onPrimaryContainer = Color(0xFFD3E4F7),
    secondary = Color(0xFF8FD8B8),
    onSecondary = Color(0xFF06281B),
    secondaryContainer = Color(0xFF1D4634),
    onSecondaryContainer = Color(0xFFD6EFE3),
    background = Color(0xFF101418),
    surface = Color(0xFF101418)
)

@Composable
fun TokensAdminTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> EsquemaOscuro
        else -> EsquemaClaro
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
