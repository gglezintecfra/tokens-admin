package com.keymax.tokensadmin.data

/**
 * Tipo de credencial almacenada. Los valores coinciden con la columna
 * `TipoAutenticacion` de la base original creada por TokenManager.ps1,
 * incluyendo soporte para Tarjetas de Crédito/Débito.
 */
enum class TipoAutenticacion(val valor: String, val etiqueta: String) {
    TOKEN("Token", "Token"),
    USUARIO_CONTRASENA("Usuario/Contraseña", "Usuario y contraseña"),
    TARJETA("Tarjeta", "Tarjeta"),
    LASTPASS("LastPass", "LastPass");

    companion object {
        fun desdeValor(valor: String?): TipoAutenticacion {
            if (valor.isNullOrBlank()) return TOKEN
            return entries.firstOrNull { it.valor.equals(valor, ignoreCase = true) } ?: TOKEN
        }
    }
}

/**
 * Registro de la tabla `Tokens`.
 * `token` y `contrasena` llegan ya descifrados desde el repositorio.
 */
data class Token(
    val id: Long = 0L,
    val aplicacion: String = "",
    val tipo: TipoAutenticacion = TipoAutenticacion.TOKEN,
    val token: String = "",
    val usuario: String = "",
    val contrasena: String = "",
    val fechaCreacion: String = "",
    val fechaModificacion: String = ""
) {
    val esNuevo: Boolean get() = id == 0L

    /** Valor que se copia al portapapeles según el tipo. */
    val secreto: String
        get() = when (tipo) {
            TipoAutenticacion.TOKEN -> token
            TipoAutenticacion.USUARIO_CONTRASENA -> contrasena
            TipoAutenticacion.TARJETA -> token
            TipoAutenticacion.LASTPASS -> contrasena
        }

    /** Texto enmascarado/recortado que se muestra en la lista. */
    val resumenToken: String
        get() = when (tipo) {
            TipoAutenticacion.TARJETA -> if (token.length > 4) "•••• •••• •••• " + token.takeLast(4) else token
            TipoAutenticacion.LASTPASS -> "••••••••"
            else -> if (token.length > 120) token.take(120) + "…" else token
        }
}
