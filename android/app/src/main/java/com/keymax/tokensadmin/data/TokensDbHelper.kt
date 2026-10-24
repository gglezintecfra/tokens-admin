package com.keymax.tokensadmin.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Esquema idéntico al de la base `tokens.db` usada por los scripts de PowerShell,
 * de modo que un archivo existente se puede seguir usando/trasladar.
 */
object TokensContract {
    const val DB_NAME = "tokens.db"
    const val DB_VERSION = 1

    const val TABLE = "Tokens"
    const val COL_ID = "Id"
    const val COL_APLICACION = "Aplicacion"
    const val COL_TIPO = "TipoAutenticacion"
    const val COL_TOKEN = "Token"
    const val COL_USUARIO = "Usuario"
    const val COL_CONTRASENA = "Contrasena"
    const val COL_CREADO = "FechaCreacion"
    const val COL_MODIFICADO = "FechaModificacion"

    val COLUMNAS = arrayOf(
        COL_ID,
        COL_APLICACION,
        COL_TIPO,
        COL_TOKEN,
        COL_USUARIO,
        COL_CONTRASENA,
        COL_CREADO,
        COL_MODIFICADO
    )

    const val SQL_CREATE = """
        CREATE TABLE IF NOT EXISTS $TABLE (
            $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
            $COL_APLICACION TEXT NOT NULL,
            $COL_TIPO TEXT NOT NULL,
            $COL_TOKEN TEXT NULL,
            $COL_USUARIO TEXT NULL,
            $COL_CONTRASENA TEXT NULL,
            $COL_CREADO TEXT DEFAULT (datetime('now','localtime')),
            $COL_MODIFICADO TEXT DEFAULT (datetime('now','localtime'))
        )
    """

    const val SQL_INDICE_APLICACION =
        "CREATE INDEX IF NOT EXISTS IX_Tokens_Aplicacion ON $TABLE($COL_APLICACION)"
}

class TokensDbHelper(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    TokensContract.DB_NAME,
    null,
    TokensContract.DB_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(TokensContract.SQL_CREATE)
        db.execSQL(TokensContract.SQL_INDICE_APLICACION)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Esquema v1: sin migraciones todavía.
    }
}
