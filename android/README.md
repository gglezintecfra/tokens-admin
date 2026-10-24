# Token Manager Android

Versión Android (Kotlin + Jetpack Compose) del administrador de tokens de
escritorio (`TokenManager.ps1`). Misma lógica y **mismo esquema SQLite**, pero
con bloqueo por credencial del dispositivo y cifrado de los secretos.

- **minSdk 31 (Android 12)** · targetSdk 36 · compileSdk 36
- Kotlin 2.2 · Jetpack Compose (Material 3) · SQLite nativo (`SQLiteOpenHelper`)
- `applicationId`: `com.keymax.tokensadmin`

## Compilar e instalar

Requisitos: JDK 17+ (`JAVA_HOME`) y Android SDK con la plataforma 36.

```powershell
cd android
.\gradlew.bat :app:assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

El APK queda en `app\build\outputs\apk\debug\app-debug.apk`.
También se puede abrir la carpeta `android` directamente con Android Studio.

## Funcionalidad (equivalente al script de escritorio)

| Escritorio (`TokenManager.ps1`)        | App Android                                        |
| -------------------------------------- | -------------------------------------------------- |
| Login de sesión de Windows             | `BiometricPrompt` con huella/rostro **o** PIN/patrón del dispositivo |
| Tabla `Tokens` en `tokens.db`          | Misma tabla y columnas en `tokens.db`              |
| Buscar por `Aplicacion` (`LIKE`)       | Mismo `LIKE` con escape de `%`, `_` y `\`, sin distinguir mayúsculas |
| Orden por `Aplicacion`                 | `ORDER BY Aplicacion COLLATE NOCASE`               |
| Alta / edición / borrado con confirmación | Alta / edición / borrado con diálogo de confirmación |
| Tipo `Token` o `Usuario/Contraseña`    | Chips de tipo; los campos que no aplican se guardan como `NULL` |
| `FechaCreacion` / `FechaModificacion`  | Se escriben con el mismo formato `yyyy-MM-dd HH:mm:ss` |

Extras: copiar al portapapeles (marcado como *sensible* en Android 13+),
contraseñas enmascaradas con botón «Ver», tema dinámico Material You y
re-bloqueo automático tras 30 s en segundo plano.

## Estructura

```
app/src/main/java/com/keymax/tokensadmin/
├── MainActivity.kt              # Arranque + autenticación y re-bloqueo
├── data/
│   ├── Token.kt                 # Modelo + enum TipoAutenticacion
│   ├── TokensDbHelper.kt        # Esquema SQLite (TokensContract)
│   └── TokenRepository.kt       # CRUD, búsqueda LIKE, cifrado de campos
├── security/
│   └── CryptoManager.kt         # AES-256-GCM con Android Keystore
└── ui/
    ├── TokenViewModel.kt        # Estado (StateFlow) + acceso a datos en IO
    ├── TokensAdminApp.kt        # Navegación lista ↔ editor + diálogo borrado
    ├── Clipboard.kt
    ├── screens/TokenListScreen.kt
    ├── screens/TokenEditorScreen.kt
    ├── screens/LockScreen.kt
    └── theme/Theme.kt
```

## Base de datos

Archivo `tokens.db` en el almacenamiento privado de la app
(`/data/data/com.keymax.tokensadmin/databases/tokens.db`), con el esquema
original:

```sql
CREATE TABLE Tokens (
    Id INTEGER PRIMARY KEY AUTOINCREMENT,
    Aplicacion TEXT NOT NULL,
    TipoAutenticacion TEXT NOT NULL,
    Token TEXT NULL,
    Usuario TEXT NULL,
    Contrasena TEXT NULL,
    FechaCreacion TEXT DEFAULT (datetime('now','localtime')),
    FechaModificacion TEXT DEFAULT (datetime('now','localtime'))
);
CREATE INDEX IX_Tokens_Aplicacion ON Tokens(Aplicacion);
```

Inspeccionarla en un equipo con depuración USB:

```powershell
adb shell "run-as com.keymax.tokensadmin cat databases/tokens.db" > tokens.db
sqlite3 tokens.db "SELECT Id, Aplicacion, TipoAutenticacion FROM Tokens;"
```

## Seguridad

- **Bloqueo de entrada**: `BiometricPrompt` con
  `BIOMETRIC_STRONG | DEVICE_CREDENTIAL`. Si el dispositivo no tiene PIN ni
  biometría, la app avisa con un banner y permite entrar (no hay forma de
  autenticar), pero los datos siguen cifrados.
- **Cifrado de secretos**: `Token` y `Contrasena` se guardan como
  `enc:v1:<iv_base64>:<ciphertext_base64>` usando AES-256-GCM con una clave
  AES no exportable del **Android Keystore** (alias `tokens_admin_aes`).
- **Compatibilidad**: los valores sin el prefijo `enc:v1:` se leen tal cual,
  de modo que una `tokens.db` copiada del escritorio sigue funcionando; al
  volver a guardar cada registro, sus secretos quedan cifrados.
- `android:allowBackup="false"` para no incluir la base en copias de seguridad.
- El portapapeles se marca con `ClipDescription.EXTRA_IS_SENSITIVE` en
  Android 13+ para evitar la vista previa del contenido.

> Nota: la clave del Keystore está ligada al dispositivo. Si se reinstala la app
> o se borran sus datos, los valores cifrados previos ya no se pueden descifrar
> (la app los muestra vacíos y se pueden volver a capturar).

## Diferencias respecto de la versión de escritorio

- No incluye el importador de `tokens.txt` (por decisión del proyecto); el CRUD
  y la búsqueda son equivalentes.
- El «login» ya no es un diálogo de sesión de Windows sino la credencial real
  del dispositivo (huella/rostro/PIN/patrón).
