# ScudoDNS - crea la clave de firma propia (una sola vez).
# Guarda la clave y su configuracion en %USERPROFILE%\.scudodns, fuera del proyecto,
# para que nunca se suba a GitHub. HAZ COPIA DE SEGURIDAD de esa carpeta y de la contrasena:
# si se pierden, las actualizaciones no podran instalarse encima de la app ya instalada.
$ErrorActionPreference = 'Stop'
$dir = Join-Path $env:USERPROFILE '.scudodns'
$ks = Join-Path $dir 'scudodns-release.p12'
$props = Join-Path $dir 'keystore.properties'

if (Test-Path $ks) {
    Write-Host "Ya existe una clave en $ks. No se sobrescribe." -ForegroundColor Yellow
    exit 0
}

$keytool = Join-Path $env:LOCALAPPDATA 'ScudoBuild\jdk\bin\keytool.exe'
if (-not (Test-Path $keytool)) {
    $keytool = (Get-Command keytool -ErrorAction SilentlyContinue).Source
    if (-not $keytool) { throw 'No encuentro keytool. Ejecuta antes compilar.bat una vez para que descargue el JDK.' }
}

function Read-Plain($prompt) {
    $s = Read-Host -Prompt $prompt -AsSecureString
    $b = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($s)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($b) } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($b) }
}

Write-Host 'Elige una contrasena para la clave (minimo 12 caracteres). Guardala en tu gestor de contrasenas.'
$p1 = Read-Plain 'Contrasena'
if ($p1.Length -lt 12) { throw 'La contrasena debe tener al menos 12 caracteres.' }
$p2 = Read-Plain 'Repite la contrasena'
if ($p1 -ne $p2) { throw 'Las contrasenas no coinciden.' }

New-Item -ItemType Directory -Force $dir | Out-Null
# Solo tu usuario puede leer esta carpeta.
& icacls $dir /inheritance:r /grant:r "$($env:USERNAME):(OI)(CI)F" | Out-Null

# La contrasena se pasa por variable de entorno, no en la linea de comandos.
$env:SCUDO_KS_PASS = $p1
try {
    & $keytool -genkeypair -keystore $ks -storetype PKCS12 -alias scudodns `
        -keyalg RSA -keysize 4096 -validity 10000 `
        -dname 'CN=ScudoDNS, O=rehxio, C=ES' `
        -storepass:env SCUDO_KS_PASS -keypass:env SCUDO_KS_PASS
    if ($LASTEXITCODE -ne 0) { throw 'keytool fallo' }
} finally {
    Remove-Item Env:SCUDO_KS_PASS -ErrorAction SilentlyContinue
}

$ksPath = $ks.Replace('\', '/')
# En .properties la barra invertida es un caracter de escape: la duplicamos.
$pEsc = $p1.Replace('\', '\\')
$lines = @(
    "storeFile=$ksPath",
    "storePassword=$pEsc",
    "keyAlias=scudodns",
    "keyPassword=$pEsc"
)
Set-Content -Path $props -Value $lines -Encoding ASCII

Write-Host ''
Write-Host "Clave creada en $dir" -ForegroundColor Green
Write-Host 'A partir de ahora compilar.bat firmara con ella.'
Write-Host 'IMPORTANTE: copia la carpeta .scudodns a un lugar seguro (USB, nube cifrada) y guarda la contrasena.' -ForegroundColor Yellow
