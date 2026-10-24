package com.keymax.tokensadmin.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.keymax.tokensadmin.security.CryptoManager
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Acceso a la tabla `Tokens`. Reproduce la lógica de TokenManager.ps1:
 * búsqueda por Aplicación (LIKE con escape), orden alfabético, y limpieza de
 * los campos que no corresponden al tipo de credencial.
 */
class TokenRepository(private val context: Context) {

    private val helper = TokensDbHelper(context)

    fun search(filtro: String = ""): List<Token> {
        val sql = StringBuilder(
            "SELECT ${TokensContract.COLUMNAS.joinToString()} FROM ${TokensContract.TABLE}"
        )
        val argumentos = mutableListOf<String>()
        val texto = filtro.trim()

        if (texto.isNotEmpty()) {
            sql.append(" WHERE ${TokensContract.COL_APLICACION} LIKE ? ESCAPE '\\'")
            argumentos += "%${escaparLike(texto)}%"
        }
        sql.append(
            " ORDER BY ${TokensContract.COL_APLICACION} COLLATE NOCASE ASC, ${TokensContract.COL_ID} ASC"
        )

        helper.readableDatabase.rawQuery(sql.toString(), argumentos.toTypedArray()).use { cursor ->
            val resultado = ArrayList<Token>(cursor.count)
            while (cursor.moveToNext()) {
                resultado += mapear(cursor)
            }
            return resultado
        }
    }

    fun getById(id: Long): Token? {
        helper.readableDatabase.query(
            TokensContract.TABLE,
            TokensContract.COLUMNAS,
            "${TokensContract.COL_ID} = ?",
            arrayOf(id.toString()),
            null,
            null,
            null
        ).use { cursor ->
            return if (cursor.moveToFirst()) mapear(cursor) else null
        }
    }

    /** Inserta o actualiza según `token.esNuevo`. Devuelve el Id afectado. */
    fun save(token: Token): Long {
        val db = helper.writableDatabase
        val ahora = marcaDeTiempo()

        val values = ContentValues().apply {
            put(TokensContract.COL_APLICACION, token.aplicacion.trim())
            put(TokensContract.COL_TIPO, token.tipo.valor)

            if (token.tipo == TipoAutenticacion.TOKEN) {
                put(TokensContract.COL_TOKEN, CryptoManager.cifrarOrNull(token.token))
                putNull(TokensContract.COL_USUARIO)
                putNull(TokensContract.COL_CONTRASENA)
            } else if (token.tipo == TipoAutenticacion.TARJETA) {
                put(TokensContract.COL_TOKEN, CryptoManager.cifrarOrNull(token.token))
                put(TokensContract.COL_USUARIO, token.usuario.trim().ifEmpty { null })
                put(TokensContract.COL_CONTRASENA, CryptoManager.cifrarOrNull(token.contrasena))
            } else if (token.tipo == TipoAutenticacion.LASTPASS) {
                put(TokensContract.COL_TOKEN, CryptoManager.cifrarOrNull(token.token))
                put(TokensContract.COL_USUARIO, token.usuario.trim().ifEmpty { null })
                put(TokensContract.COL_CONTRASENA, CryptoManager.cifrarOrNull(token.contrasena))
            } else {
                putNull(TokensContract.COL_TOKEN)
                put(TokensContract.COL_USUARIO, token.usuario.trim().ifEmpty { null })
                put(TokensContract.COL_CONTRASENA, CryptoManager.cifrarOrNull(token.contrasena))
            }

            put(TokensContract.COL_MODIFICADO, ahora)
            if (token.esNuevo) {
                put(TokensContract.COL_CREADO, ahora)
            }
        }

        return if (token.esNuevo) {
            db.insert(TokensContract.TABLE, null, values)
        } else {
            db.update(
                TokensContract.TABLE,
                values,
                "${TokensContract.COL_ID} = ?",
                arrayOf(token.id.toString())
            )
            token.id
        }
    }

    fun delete(id: Long): Int =
        helper.writableDatabase.delete(
            TokensContract.TABLE,
            "${TokensContract.COL_ID} = ?",
            arrayOf(id.toString())
        )

    private fun mapear(cursor: Cursor): Token {
        val tipo = TipoAutenticacion.desdeValor(cursor.texto(TokensContract.COL_TIPO))
        return Token(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(TokensContract.COL_ID)),
            aplicacion = cursor.texto(TokensContract.COL_APLICACION).orEmpty(),
            tipo = tipo,
            token = if (tipo == TipoAutenticacion.TOKEN || tipo == TipoAutenticacion.TARJETA || tipo == TipoAutenticacion.LASTPASS) {
                CryptoManager.descifrar(cursor.texto(TokensContract.COL_TOKEN))
            } else {
                ""
            },
            usuario = cursor.texto(TokensContract.COL_USUARIO).orEmpty(),
            contrasena = if (tipo == TipoAutenticacion.USUARIO_CONTRASENA || tipo == TipoAutenticacion.TARJETA || tipo == TipoAutenticacion.LASTPASS) {
                CryptoManager.descifrar(cursor.texto(TokensContract.COL_CONTRASENA))
            } else {
                ""
            },
            fechaCreacion = cursor.texto(TokensContract.COL_CREADO).orEmpty(),
            fechaModificacion = cursor.texto(TokensContract.COL_MODIFICADO).orEmpty()
        )
    }

    private fun Cursor.texto(columna: String): String? {
        val indice = getColumnIndex(columna)
        return if (indice < 0 || isNull(indice)) null else getString(indice)
    }

    private fun escaparLike(texto: String): String = texto
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")

    private fun marcaDeTiempo(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

    /** Importa un CSV exportado de Google Passwords. Devuelve el número de registros importados. */
    fun importarCsvGoogle(inputStream: InputStream): Int {
        val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
        val lineas = reader.readLines()
        if (lineas.isEmpty()) return 0

        val cabecera = parsearCsvLinea(lineas[0]).map { it.lowercase(Locale.US).trim() }
        val idxName = cabecera.indexOfFirst { it.contains("name") || it.contains("title") }
        val idxUser = cabecera.indexOfFirst { it.contains("user") || it.contains("username") || it.contains("login") }
        val idxPass = cabecera.indexOfFirst { it.contains("pass") || it.contains("password") }
        val idxUrl = cabecera.indexOfFirst { it.contains("url") || it.contains("website") }

        if (idxUser < 0 || idxPass < 0) return 0

        var count = 0
        for (i in 1 until lineas.size) {
            val campos = parsearCsvLinea(lineas[i])
            if (campos.size <= maxOf(idxUser, idxPass)) continue

            val name = if (idxName >= 0 && idxName < campos.size) campos[idxName].trim() else ""
            val url = if (idxUrl >= 0 && idxUrl < campos.size) campos[idxUrl].trim() else ""
            val user = campos[idxUser].trim()
            val pass = campos[idxPass].trim()

            val aplicacion = when {
                name.isNotEmpty() -> name
                url.isNotEmpty() -> extraerDominio(url)
                else -> "Google Import"
            }

            if (aplicacion.isNotEmpty() && (user.isNotEmpty() || pass.isNotEmpty())) {
                val token = Token(
                    id = 0L,
                    aplicacion = aplicacion,
                    tipo = TipoAutenticacion.USUARIO_CONTRASENA,
                    token = "",
                    usuario = user,
                    contrasena = pass
                )
                save(token)
                count++
            }
        }
        return count
    }

    private fun parsearCsvLinea(linea: String): List<String> {
        val resultado = mutableListOf<String>()
        val actual = StringBuilder()
        var entreComillas = false
        var i = 0
        while (i < linea.length) {
            val c = linea[i]
            when {
                c == '"' -> {
                    if (entreComillas && i + 1 < linea.length && linea[i + 1] == '"') {
                        actual.append('"')
                        i++
                    } else {
                        entreComillas = !entreComillas
                    }
                }
                c == ',' && !entreComillas -> {
                    resultado.add(actual.toString())
                    actual.clear()
                }
                else -> {
                    actual.append(c)
                }
            }
            i++
        }
        resultado.add(actual.toString())
        return resultado
    }

    private fun extraerDominio(url: String): String {
        return try {
            val limpia = url.removePrefix("https://").removePrefix("http://").removePrefix("www.")
            val fin = limpia.indexOf('/')
            if (fin >= 0) limpia.substring(0, fin) else limpia
        } catch (e: Exception) {
            url
        }
    }

    fun exportarBaseDatos(outputStream: OutputStream) {
        helper.writableDatabase.close()
        val dbFile = context.getDatabasePath(TokensContract.DB_NAME)
        if (dbFile.exists()) {
            dbFile.inputStream().use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    fun importarBaseDatos(inputStream: InputStream) {
        helper.writableDatabase.close()
        val dbFile = context.getDatabasePath(TokensContract.DB_NAME)
        dbFile.parentFile?.mkdirs()
        inputStream.use { input ->
            dbFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
}
