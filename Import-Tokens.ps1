# --- SQLite (archivo local, sin servidor) ---
$baseDir = if ($PSScriptRoot) { $PSScriptRoot } else { (Get-Location).Path }
$dbPath = Join-Path $baseDir "tokens.db"
$script:SqliteExe = $null

function Find-SqliteExe {
    if ($script:SqliteExe -and (Test-Path $script:SqliteExe)) { return $script:SqliteExe }
    $candidates = @()
    if ($PSScriptRoot) { $candidates += (Join-Path $PSScriptRoot "sqlite3.exe") }
    if ($env:LOCALAPPDATA) { $candidates += (Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\sqlite3.exe") }
    foreach ($c in $candidates) {
        if ($c -and (Test-Path $c)) { $script:SqliteExe = $c; return $c }
    }
    $cmd = Get-Command "sqlite3.exe" -ErrorAction SilentlyContinue
    if ($cmd) { $script:SqliteExe = $cmd.Source; return $script:SqliteExe }
    $cmd = Get-Command "sqlite3" -ErrorAction SilentlyContinue
    if ($cmd) { $script:SqliteExe = $cmd.Source; return $script:SqliteExe }
    return $null
}

function Escape-SqliteString {
    param([string]$Text)
    if ($null -eq $Text) { return "" }
    return ($Text -replace "'", "''")
}

function ConvertTo-SqliteLiteral {
    param($Value)
    if ([string]::IsNullOrEmpty($Value)) { return "NULL" }
    return "'" + (Escape-SqliteString ([string]$Value)) + "'"
}

function Invoke-SqliteNonQuery {
    param([string]$Sql)
    $exe = Find-SqliteExe
    if (-not $exe) { throw "No se encontró sqlite3.exe. Descárgalo de https://sqlite.org/download.html y colócalo junto al script o en el PATH." }
    $out = & $exe $dbPath $Sql 2>&1
    if ($LASTEXITCODE -ne 0) { throw (($out | Out-String).Trim()) }
}

function Initialize-Database {
    try {
        $exe = Find-SqliteExe
        if (-not $exe) {
            Write-Host "No se encontró sqlite3.exe. Descárgalo de https://sqlite.org/download.html y colócalo junto al script o en el PATH." -ForegroundColor Red
            return $false
        }
        $sql = @"
CREATE TABLE IF NOT EXISTS Tokens (
    Id INTEGER PRIMARY KEY AUTOINCREMENT,
    Aplicacion TEXT NOT NULL,
    TipoAutenticacion TEXT NOT NULL,
    Token TEXT NULL,
    Usuario TEXT NULL,
    Contrasena TEXT NULL,
    FechaCreacion TEXT DEFAULT (datetime('now','localtime')),
    FechaModificacion TEXT DEFAULT (datetime('now','localtime'))
);
"@
        Invoke-SqliteNonQuery -Sql $sql
        return $true
    }
    catch {
        Write-Host "ERROR DB: $_" -ForegroundColor Red
        return $false
    }
}

function Test-IsCredential {
    param($Text)
    if ($Text -match '(?i)^sk-(proj|ant|rat|kor)' -and $Text.Length -gt 40) { return $true }
    if ($Text -match '^pk-(proj|ant)' -and $Text.Length -gt 40) { return $true }
    if ($Text -match '^PMAK-' -and $Text.Length -gt 20) { return $true }
    if ($Text -match '^AIza' -and $Text.Length -gt 20) { return $true }
    if ($Text -match '^AQ\.' -and $Text.Length -gt 20) { return $true }
    if ($Text -match '^dckr_pat_' -and $Text.Length -gt 20) { return $true }
    if ($Text -match '^[a-f0-9\-]{30,}$') { return $true }
    if ($Text -match '^[A-Za-z0-9+/=]{40,}$') { return $true }
    return $false
}

function Normalize-AppName {
    param($Name)
    $n = $Name -replace '^(Password|API\s+Key|Api\s+Key)\s+', ''
    $n = $n -replace '\s+(API\s+Key|Api\s+Key|Key):?\s*$', ''
    $n = $n.Trim()
    return $n
}

function Get-Entries {
    param($Path)

    $raw = Get-Content $Path -Raw
    $raw = $raw.Trim() -replace "`r`n", "`n"

    $groups = @()
    $buf = ""
    foreach ($line in $raw -split "`n") {
        if ([string]::IsNullOrWhiteSpace($line)) {
            if ($buf) { $groups += $buf; $buf = "" }
        } else {
            $buf += if ($buf) { "`n" } else { "" }
            $buf += $line.Trim()
        }
    }
    if ($buf) { $groups += $buf }

    $entries = @()
    $prevApp = ""

    for ($i = 0; $i -lt $groups.Count; $i++) {
        $lines = $groups[$i] -split "`n"
        $first = $lines[0]

        # --- Single line groups ---
        if ($lines.Count -eq 1) {
            # "Label: Value"
            if ($first -match '^(.+?):\s+(.+)$') {
                $app = Normalize-AppName $matches[1]
                $val = $matches[2].Trim()
                $entries += [PSCustomObject]@{ App = $app; Type = "Token"; Token = $val; User = ""; Pass = "" }
                $prevApp = $app
                continue
            }
            # "AppName sk-..." or "AppName dckr_pat_..." without colon
            if ($first -match '(?i)^(\w[\w\s]+?)\s+((?:sk-(?:proj|ant|rat|kor)|pk-(?:proj|ant)|PMAK-|AIza|AQ\.|dckr_pat_|ghp_|gho_|ghu_|github_pat_)\S+)$') {
                $app = Normalize-AppName $matches[1]
                $val = $matches[2]
                $entries += [PSCustomObject]@{ App = $app; Type = "Token"; Token = $val; User = ""; Pass = "" }
                $prevApp = $app
                continue
            }
            # Looks like a credential → tie to prev app
            if (Test-IsCredential $first) {
                $num = 2
                while ($entries | Where-Object { $_.App -eq "$prevApp $num" }) { $num++ }
                $entries += [PSCustomObject]@{ App = "$prevApp $num"; Type = "Token"; Token = $first; User = ""; Pass = "" }
                continue
            }
            $prevApp = $first
            continue
        }

        $rest = @($lines[1..($lines.Count - 1)])

        # --- "key: " label on first line with sub-name ---
        if ($first -match '^(.+?):\s*$') {
            $base = Normalize-AppName $matches[1]
            if ($rest.Count -eq 2) {
                $app = "$base $($rest[0])"
                $entries += [PSCustomObject]@{ App = $app; Type = "Token"; Token = $rest[1]; User = ""; Pass = "" }
                $prevApp = $app; continue
            }
            if ($rest.Count -eq 1) {
                $entries += [PSCustomObject]@{ App = $base; Type = "Token"; Token = $rest[0]; User = ""; Pass = "" }
                $prevApp = $base; continue
            }
            $prevApp = $base; continue
        }

        # --- Multi-line: classify by count ---
        $appBase = Normalize-AppName $first

        if ($rest.Count -eq 1) {
            # Single value: check for "Field: Value" (like "API Key: xxx")
            if ($rest[0] -match '^(API\s+Key|Api\s+Key|Key|Token|Password|Apikey):\s+(.+)$') {
                $entries += [PSCustomObject]@{ App = $appBase; Type = "Token"; Token = $matches[2]; User = ""; Pass = "" }
            }
            # "user: pass" format
            elseif ($rest[0] -match '^(.+?):\s+(.+)$') {
                $entries += [PSCustomObject]@{ App = $appBase; Type = "Usuario/Contraseña"; Token = ""; User = $matches[1]; Pass = $matches[2] }
            }
            else {
                $entries += [PSCustomObject]@{ App = $appBase; Type = "Token"; Token = $rest[0]; User = ""; Pass = "" }
            }
            $prevApp = $appBase; continue
        }

        if ($rest.Count -eq 2) {
            $entries += [PSCustomObject]@{ App = $appBase; Type = "Usuario/Contraseña"; Token = ""; User = $rest[0]; Pass = $rest[1] }
            $prevApp = $appBase; continue
        }

        # Three+ values → numbered tokens
        for ($n = 0; $n -lt $rest.Count; $n++) {
            $app = if ($rest.Count -gt 1) { "$appBase $($n+1)" } else { $appBase }
            $entries += [PSCustomObject]@{ App = $app; Type = "Token"; Token = $rest[$n]; User = ""; Pass = "" }
        }
        $prevApp = $appBase
    }

    return $entries
}

function Insert-Entry {
    param($Entry)
    try {
        if ($Entry.Type -eq "Token") {
            $a = ConvertTo-SqliteLiteral $Entry.App
            $t = ConvertTo-SqliteLiteral $Entry.Type
            $v = ConvertTo-SqliteLiteral $Entry.Token
            $sql = "INSERT INTO Tokens (Aplicacion,TipoAutenticacion,Token) VALUES ($a,$t,$v);"
        } else {
            $a = ConvertTo-SqliteLiteral $Entry.App
            $t = ConvertTo-SqliteLiteral $Entry.Type
            $u = ConvertTo-SqliteLiteral $Entry.User
            $p = ConvertTo-SqliteLiteral $Entry.Pass
            $sql = "INSERT INTO Tokens (Aplicacion,TipoAutenticacion,Usuario,Contrasena) VALUES ($a,$t,$u,$p);"
        }
        Invoke-SqliteNonQuery -Sql $sql
        return $true
    }
    catch { Write-Host "  ERROR: $_" -ForegroundColor Red; return $false }
}

# --- MAIN ---
$txtPath = Join-Path $PSScriptRoot "tokens.txt"

Write-Host "Inicializando base de datos..." -ForegroundColor Cyan
if (-not (Initialize-Database)) {
    Write-Host "No se pudo inicializar SQLite. Abortando." -ForegroundColor Red
    exit 1
}

Write-Host "Leyendo $txtPath ..." -ForegroundColor Cyan
$entries = Get-Entries -Path $txtPath
Write-Host "Se encontraron $($entries.Count) entradas`n" -ForegroundColor Cyan

# Preview
foreach ($e in $entries) {
    $tag = if ($e.Type -eq "Token") { "TOKEN" } else { "U/P  " }
    $val = if ($e.Type -eq "Token") {
        $t = $e.Token; ($t.Substring(0, [Math]::Min(50, $t.Length)) + "...")
    } else {
        "$($e.User) / $($e.Pass)"
    }
    Write-Host "  [$tag] $($e.App) -> $val" -ForegroundColor Gray
}

Write-Host "`nImportando..." -ForegroundColor Cyan
$ok = 0; $fail = 0
foreach ($e in $entries) {
    if (Insert-Entry $e) { $ok++ } else { $fail++ }
}

$color = if ($fail -eq 0) { "Green" } else { "Yellow" }
Write-Host "Completado: $ok insertadas, $fail fallidas" -ForegroundColor $color
