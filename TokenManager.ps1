Add-Type -AssemblyName PresentationFramework, PresentationCore, WindowsBase

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

function Escape-SqliteLike {
    param([string]$Text)
    if ($null -eq $Text) { return "" }
    $e = $Text -replace '\', '\\'
    $e = $e -replace '%', '\%'
    $e = $e -replace '_', '\_'
    return (Escape-SqliteString $e)
}

function Invoke-SqliteNonQuery {
    param([string]$Sql)
    $exe = Find-SqliteExe
    if (-not $exe) { throw "No se encontró sqlite3.exe. Descárgalo de https://sqlite.org/download.html y colócalo junto al script o en el PATH." }
    $out = & $exe $dbPath $Sql 2>&1
    if ($LASTEXITCODE -ne 0) { throw (($out | Out-String).Trim()) }
}

function Invoke-SqliteJson {
    param([string]$Sql)
    $exe = Find-SqliteExe
    if (-not $exe) { throw "No se encontró sqlite3.exe. Descárgalo de https://sqlite.org/download.html y colócalo junto al script o en el PATH." }
    $raw = & $exe -json $dbPath $Sql 2>&1
    if ($LASTEXITCODE -ne 0) { throw (($raw | Out-String).Trim()) }
    $json = ($raw | Out-String).Trim()
    if ([string]::IsNullOrWhiteSpace($json)) { return @() }
    $obj = $json | ConvertFrom-Json
    if ($null -eq $obj) { return @() }
    if ($obj -is [System.Array]) { return $obj }
    return @($obj)
}

function Initialize-Database {
    try {
        $exe = Find-SqliteExe
        if (-not $exe) {
            [System.Windows.MessageBox]::Show(
                "No se encontró sqlite3.exe.`n`nDescárgalo de https://sqlite.org/download.html y colócalo junto al script o en el PATH.",
                "Error de Configuración", "OK", "Error")
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
        [System.Windows.MessageBox]::Show(
            "No se pudo inicializar la base SQLite ($dbPath).`n`n$_",
            "Error de Base de Datos", "OK", "Error")
        return $false
    }
}

function Get-Tokens {
    param([string]$filter = "")
    try {
        $sql = "SELECT Id, Aplicacion, TipoAutenticacion, Token, Usuario, Contrasena FROM Tokens"
        if ($filter) {
            $f = Escape-SqliteLike $filter
            $sql += " WHERE Aplicacion LIKE '%$f%' ESCAPE '\'"
        }
        $sql += " ORDER BY Aplicacion;"
        $rows = Invoke-SqliteJson -Sql $sql

        $result = @()
        foreach ($row in $rows) {
            $tokenFull = if ($null -ne $row.Token) { [string]$row.Token } else { "" }
            $tokenDisp = if ($tokenFull.Length -gt 120) { $tokenFull.Substring(0, 120) + "..." } else { $tokenFull }
            $result += [PSCustomObject]@{
                Id = $row.Id
                Aplicacion = if ($null -ne $row.Aplicacion) { [string]$row.Aplicacion } else { "" }
                TipoAutenticacion = if ($null -ne $row.TipoAutenticacion) { [string]$row.TipoAutenticacion } else { "" }
                Token = $tokenDisp
                TokenFull = $tokenFull
                Usuario = if ($null -ne $row.Usuario) { [string]$row.Usuario } else { "" }
                Contrasena = if ($null -ne $row.Contrasena) { [string]$row.Contrasena } else { "" }
            }
        }
        return $result
    }
    catch { throw }
}

function Save-Token {
    param($Id, $Aplicacion, $TipoAutenticacion, $Token, $Usuario, $Contrasena)
    try {
        $a = ConvertTo-SqliteLiteral $Aplicacion
        $t = ConvertTo-SqliteLiteral $TipoAutenticacion
        $tk = ConvertTo-SqliteLiteral $Token
        $u = ConvertTo-SqliteLiteral $Usuario
        $c = ConvertTo-SqliteLiteral $Contrasena

        if ($Id) {
            $idInt = [int]$Id
            $sql = "UPDATE Tokens SET Aplicacion=$a, TipoAutenticacion=$t, Token=$tk, Usuario=$u, Contrasena=$c, FechaModificacion=datetime('now','localtime') WHERE Id=$idInt;"
        } else {
            $sql = "INSERT INTO Tokens (Aplicacion,TipoAutenticacion,Token,Usuario,Contrasena) VALUES ($a,$t,$tk,$u,$c);"
        }

        Invoke-SqliteNonQuery -Sql $sql
        return $true
    }
    catch { throw }
}

function Remove-Token {
    param($Id)
    try {
        $idInt = [int]$Id
        Invoke-SqliteNonQuery -Sql "DELETE FROM Tokens WHERE Id=$idInt;"
        return $true
    }
    catch { throw }
}

[xml]$xaml = @"
<Window xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
        xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"
        Title="Token Manager" Height="720" Width="1000"
        WindowStartupLocation="CenterScreen"
        FontFamily="Segoe UI" FontSize="13">
    <Grid Margin="10">
        <Grid.RowDefinitions>
            <RowDefinition Height="Auto"/>
            <RowDefinition Height="Auto"/>
            <RowDefinition Height="*"/>
            <RowDefinition Height="Auto"/>
            <RowDefinition Height="Auto"/>
        </Grid.RowDefinitions>

        <TextBlock Text="Gesti&#xF3;n de Tokens" FontSize="22" FontWeight="Bold" Foreground="#2c3e50" Margin="0,0,0,8"/>

        <StackPanel Grid.Row="1" Orientation="Horizontal" Margin="0,0,0,8">
            <TextBlock Text="Buscar r&#xE1;pida:" VerticalAlignment="Center" FontWeight="SemiBold" Margin="0,0,6,0"/>
            <TextBox x:Name="txtSearch" Width="350" Padding="4"/>
            <Button x:Name="btnClear" Content="Limpiar" Margin="6,0,0,0" Padding="10,4" IsCancel="True"/>
        </StackPanel>

        <DataGrid Grid.Row="2" x:Name="dgTokens" AutoGenerateColumns="False" IsReadOnly="True"
                  AlternatingRowBackground="#F0F4F8" RowHeaderWidth="0" GridLinesVisibility="Horizontal"
                  Margin="0,0,0,8" BorderBrush="#C0C0C0" BorderThickness="1">
            <DataGrid.Columns>
                <DataGridTextColumn Header="ID" Binding="{Binding Id}" Width="45"/>
                <DataGridTextColumn Header="Aplicaci&#xF3;n" Binding="{Binding Aplicacion}" Width="*" MinWidth="150"/>
                <DataGridTextColumn Header="Tipo" Binding="{Binding TipoAutenticacion}" Width="140"/>
                <DataGridTextColumn Header="Token" Binding="{Binding Token}" Width="300"/>
                <DataGridTextColumn Header="Usuario" Binding="{Binding Usuario}" Width="130"/>
                <DataGridTextColumn Header="Contrase&#xF1;a" Binding="{Binding Contrasena}" Width="130"/>
            </DataGrid.Columns>
        </DataGrid>

        <GroupBox Grid.Row="3" Header="Detalles" Padding="8" Margin="0,0,0,8" BorderBrush="#B0B0B0">
            <Grid>
                <Grid.RowDefinitions>
                    <RowDefinition Height="Auto"/>
                    <RowDefinition Height="Auto"/>
                    <RowDefinition Height="Auto"/>
                </Grid.RowDefinitions>
                <Grid.ColumnDefinitions>
                    <ColumnDefinition Width="80"/>
                    <ColumnDefinition Width="*"/>
                    <ColumnDefinition Width="80"/>
                    <ColumnDefinition Width="*"/>
                </Grid.ColumnDefinitions>

                <TextBlock Text="Aplicaci&#xF3;n:" VerticalAlignment="Center" Margin="0,0,4,6"/>
                <TextBox Grid.Column="1" x:Name="txtAplicacion" Margin="0,0,0,6" Padding="4"/>

                <TextBlock Grid.Column="2" Text="Tipo:" VerticalAlignment="Center" Margin="4,0,4,6"/>
                <ComboBox Grid.Column="3" x:Name="cmbTipo" Margin="0,0,0,6" Padding="2"/>

                <StackPanel Grid.Row="1" Grid.ColumnSpan="4" x:Name="pnlToken" Margin="0,0,0,4">
                    <TextBlock Text="Token:" Margin="0,0,0,2"/>
                    <TextBox x:Name="txtToken" Height="90" AcceptsReturn="True" TextWrapping="Wrap"
                             VerticalScrollBarVisibility="Auto" Padding="4"
                             FontFamily="Consolas" FontSize="12"/>
                </StackPanel>

                <StackPanel Grid.Row="2" Grid.ColumnSpan="4" x:Name="pnlUserPass" Visibility="Collapsed">
                    <Grid>
                        <Grid.ColumnDefinitions>
                            <ColumnDefinition Width="80"/>
                            <ColumnDefinition Width="*"/>
                            <ColumnDefinition Width="80"/>
                            <ColumnDefinition Width="*"/>
                        </Grid.ColumnDefinitions>
                        <TextBlock Text="Usuario:" VerticalAlignment="Center" Margin="0,0,4,4"/>
                        <TextBox Grid.Column="1" x:Name="txtUsuario" Margin="0,0,8,4" Padding="4"/>
                        <TextBlock Grid.Column="2" Text="Contrase&#xF1;a:" VerticalAlignment="Center" Margin="4,0,4,4"/>
                        <PasswordBox Grid.Column="3" x:Name="txtContrasena" Margin="0,0,0,4" Padding="4"/>
                    </Grid>
                </StackPanel>
            </Grid>
        </GroupBox>

        <StackPanel Grid.Row="4" Orientation="Horizontal" HorizontalAlignment="Right">
            <Button x:Name="btnNew" Content="Nuevo" Padding="16,6" Margin="0,0,6,0" Background="#3498db" Foreground="White" BorderThickness="0"/>
            <Button x:Name="btnSave" Content="Guardar" Padding="16,6" Margin="0,0,6,0" IsEnabled="False" Background="#27ae60" Foreground="White" BorderThickness="0"/>
            <Button x:Name="btnDelete" Content="Eliminar" Padding="16,6" IsEnabled="False" Background="#e74c3c" Foreground="White" BorderThickness="0"/>
        </StackPanel>
    </Grid>
</Window>
"@

$reader = New-Object System.Xml.XmlNodeReader $xaml
try {
    $window = [Windows.Markup.XamlReader]::Load($reader)
}
catch {
    Write-Host "Error XAML: $_"
    [System.Windows.MessageBox]::Show("Error al cargar la interfaz: $_", "Error", "OK", "Error")
    exit
}

$txtSearch = $window.FindName("txtSearch")
$btnClear = $window.FindName("btnClear")
$dgTokens = $window.FindName("dgTokens")
$txtAplicacion = $window.FindName("txtAplicacion")
$cmbTipo = $window.FindName("cmbTipo")
$pnlToken = $window.FindName("pnlToken")
$pnlUserPass = $window.FindName("pnlUserPass")
$txtToken = $window.FindName("txtToken")
$txtUsuario = $window.FindName("txtUsuario")
$txtContrasena = $window.FindName("txtContrasena")
$btnNew = $window.FindName("btnNew")
$btnSave = $window.FindName("btnSave")
$btnDelete = $window.FindName("btnDelete")

$cmbTipo.Items.Add("Token") | Out-Null
$cmbTipo.Items.Add("Usuario/Contraseña") | Out-Null
$cmbTipo.SelectedIndex = 0

$global:selectedId = $null

function Clear-Form {
    $txtAplicacion.Text = ""
    $txtToken.Text = ""
    $txtUsuario.Text = ""
    $txtContrasena.Password = ""
    $cmbTipo.SelectedIndex = 0
    $global:selectedId = $null
    $btnSave.IsEnabled = $false
    $btnDelete.IsEnabled = $false
}

function Populate-Form {
    param($item)
    if (-not $item) { return }
    $global:selectedId = $item.Id
    $txtAplicacion.Text = $item.Aplicacion
    $txtToken.Text = $item.TokenFull
    if ($item.TipoAutenticacion -eq "Usuario/Contraseña") {
        $cmbTipo.SelectedIndex = 1
        $txtUsuario.Text = $item.Usuario
        $txtContrasena.Password = $item.Contrasena
    } else {
        $cmbTipo.SelectedIndex = 0
    }
    $btnSave.IsEnabled = $true
    $btnDelete.IsEnabled = $true
}

function Refresh-Data {
    param([string]$filter = "")
    try {
        $data = Get-Tokens -filter $filter
        $dgTokens.ItemsSource = $data
        $dgTokens.Items.Refresh()
    }
    catch {
        [System.Windows.MessageBox]::Show("Error al cargar datos: $_", "Error", "OK", "Error")
    }
}

$loginXaml = @'
<Window xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
        xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"
        Title="Token Manager - Acceso" Height="210" Width="360"
        WindowStartupLocation="CenterScreen" ResizeMode="NoResize"
        WindowStyle="SingleBorderWindow" FontFamily="Segoe UI" FontSize="13">
    <Grid Margin="16">
        <Grid.RowDefinitions>
            <RowDefinition Height="Auto"/>
            <RowDefinition Height="Auto"/>
            <RowDefinition Height="Auto"/>
            <RowDefinition Height="Auto"/>
        </Grid.RowDefinitions>
        <TextBlock Text="Token Manager" FontSize="20" FontWeight="Bold" Foreground="#2c3e50" HorizontalAlignment="Center" Margin="0,0,0,6"/>
        <TextBlock Grid.Row="1" Text="Sesi&#xF3;n de Windows detectada" HorizontalAlignment="Center" Foreground="#666" Margin="0,0,0,14"/>
        <TextBlock Grid.Row="2" x:Name="lblUser" HorizontalAlignment="Center" FontSize="14" FontWeight="SemiBold" Foreground="#2c3e50" Margin="0,0,0,16"/>
        <Button Grid.Row="3" x:Name="btnLogin" Content="Continuar" Padding="10,5" Height="30" Width="120" HorizontalAlignment="Center" Background="#3498db" Foreground="White" BorderThickness="0" IsDefault="True"/>
    </Grid>
</Window>
'@

function Show-LoginDialog {
    $rd = New-Object System.Xml.XmlNodeReader ([xml]$loginXaml)
    $w = [Windows.Markup.XamlReader]::Load($rd)
    $l = $w.FindName("lblUser"); $b = $w.FindName("btnLogin")
    $l.Text = "[$([Environment]::UserDomainName)\$([Environment]::UserName)]"
    $ok = $false

    $b.Add_Click({ $script:ok = $true; $w.Close() })

    $w.ShowDialog() | Out-Null
    return $script:ok
}

$txtSearch.Add_TextChanged({
    Refresh-Data -filter $txtSearch.Text.Trim()
})

$btnClear.Add_Click({
    $txtSearch.Text = ""
    Refresh-Data
})

$dgTokens.Add_SelectionChanged({
    if ($dgTokens.SelectedItem) { Populate-Form -item $dgTokens.SelectedItem }
})

$cmbTipo.Add_SelectionChanged({
    if ($cmbTipo.SelectedIndex -eq 1) {
        $pnlToken.Visibility = "Collapsed"
        $pnlUserPass.Visibility = "Visible"
    } else {
        $pnlToken.Visibility = "Visible"
        $pnlUserPass.Visibility = "Collapsed"
    }
})

$btnNew.Add_Click({ Clear-Form; $txtAplicacion.Focus(); $btnSave.IsEnabled = $true })

$btnSave.Add_Click({
    $aplicacion = $txtAplicacion.Text.Trim()
    if (-not $aplicacion) {
        [System.Windows.MessageBox]::Show("El campo Aplicación es obligatorio.", "Validación", "OK", "Warning")
        $txtAplicacion.Focus()
        return
    }
    try {
        if ($cmbTipo.SelectedIndex -eq 0) {
            Save-Token -Id $global:selectedId -Aplicacion $aplicacion -TipoAutenticacion "Token" -Token $txtToken.Text
        } else {
            Save-Token -Id $global:selectedId -Aplicacion $aplicacion -TipoAutenticacion "Usuario/Contraseña" -Usuario $txtUsuario.Text -Contrasena $txtContrasena.Password
        }
        Clear-Form
        Refresh-Data -filter $txtSearch.Text.Trim()
    }
    catch {
        [System.Windows.MessageBox]::Show("Error al guardar: $_", "Error", "OK", "Error")
    }
})

$btnDelete.Add_Click({
    if (-not $global:selectedId) { return }
    $r = [System.Windows.MessageBox]::Show("¿Está seguro de eliminar este registro?", "Confirmar", "YesNo", "Question")
    if ($r -eq "Yes") {
        try {
            Remove-Token -Id $global:selectedId
            Clear-Form
            Refresh-Data -filter $txtSearch.Text.Trim()
        }
        catch {
            [System.Windows.MessageBox]::Show("Error al eliminar: $_", "Error", "OK", "Error")
        }
    }
})

if (Show-LoginDialog) {
    if (Initialize-Database) { Refresh-Data }
    $window.ShowDialog() | Out-Null
}
