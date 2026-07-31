$ErrorActionPreference = 'Stop'

$projectRef = 'clqjdfouybtlshqojkxf'
$dbUser = "postgres.$projectRef"
$dbHost = 'aws-1-sa-east-1.pooler.supabase.com'
$dbPort = 5432
$dbName = 'postgres'

$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$documents = Join-Path $env:USERPROFILE 'Documents'
$backupDirectory = Join-Path $documents "respaldos-pravi\$timestamp"
New-Item -ItemType Directory -Path $backupDirectory -Force | Out-Null

$securePassword = Read-Host 'Contrasena de la base de datos Supabase' -AsSecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
$locationPushed = $false

try {
    $databasePassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    $encodedPassword = [Uri]::EscapeDataString($databasePassword)
    $dbUrl = "postgresql://${dbUser}:${encodedPassword}@${dbHost}:${dbPort}/${dbName}"

    Push-Location $backupDirectory
    $locationPushed = $true

    $rolesFile = 'roles.sql'
    $schemaFile = 'schema.sql'
    $dataFile = 'data.sql'

    Write-Host '1/3 Exportando roles...'
    & npx --yes supabase@latest db dump --db-url $dbUrl --file $rolesFile --role-only
    if ($LASTEXITCODE -ne 0) { throw 'Fallo la exportacion de roles.' }

    Write-Host '2/3 Exportando esquema...'
    & npx --yes supabase@latest db dump --db-url $dbUrl --file $schemaFile
    if ($LASTEXITCODE -ne 0) { throw 'Fallo la exportacion del esquema.' }

    Write-Host '3/3 Exportando datos...'
    & npx --yes supabase@latest db dump --db-url $dbUrl --file $dataFile --data-only --use-copy
    if ($LASTEXITCODE -ne 0) { throw 'Fallo la exportacion de datos.' }

    $files = Get-Item $rolesFile, $schemaFile, $dataFile
    $files | Get-FileHash -Algorithm SHA256 |
        Select-Object Path, Algorithm, Hash |
        Export-Csv (Join-Path $backupDirectory 'checksums-sha256.csv') -NoTypeInformation

    Write-Host ''
    Write-Host 'Respaldo de base completado:' -ForegroundColor Green
    Write-Host $backupDirectory
    $files | Select-Object Name, Length, LastWriteTime | Format-Table -AutoSize
    Write-Host 'Los objetos del bucket documentos se respaldan por separado.' -ForegroundColor Yellow
}
finally {
    if ($locationPushed) {
        Pop-Location
    }
    $databasePassword = $null
    $encodedPassword = $null
    $dbUrl = $null
    if ($passwordPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    }
    $securePassword = $null
}
