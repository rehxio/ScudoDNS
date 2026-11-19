# ScudoDNS - compila el APK en Windows sin instalar Android Studio.
# Descarga en %LOCALAPPDATA%\ScudoBuild: JDK 17 (Microsoft), Android SDK (Google) y Gradle.
# No necesita permisos de administrador. El registro queda en build-log.txt.
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$tool = Join-Path $env:LOCALAPPDATA 'ScudoBuild'
$oldTool = Join-Path $env:LOCALAPPDATA 'EscudoBuild'
if ((Test-Path $oldTool) -and -not (Test-Path $tool)) { Rename-Item $oldTool 'ScudoBuild' }  # reutiliza lo ya descargado
New-Item -ItemType Directory -Force $tool | Out-Null
Start-Transcript -Path (Join-Path $root 'build-log.txt') -Force | Out-Null

function Step($t) { Write-Host "`n==== $t ====" -ForegroundColor Cyan }
function Verify($file, $algo, $expected) {
    $actual = (Get-FileHash -Path $file -Algorithm $algo).Hash.ToLower()
    if ($actual -ne $expected.Trim().ToLower()) {
        Remove-Item $file -Force
        throw "La huella $algo de $(Split-Path $file -Leaf) no coincide. Descarga descartada por seguridad."
    }
    Write-Host "Huella $algo verificada: $(Split-Path $file -Leaf)"
}

function Download($url, $out) {
    Write-Host "Descargando $url"
    & curl.exe -L --fail --retry 3 -o $out $url
    if ($LASTEXITCODE -ne 0) { throw "Fallo al descargar $url" }
}

try {
    # Restos de sincronizaciones anteriores: se borran solos antes de compilar.
    $tmpSync = Join-Path $root '_to_delete'
    if (Test-Path $tmpSync) { Remove-Item $tmpSync -Recurse -Force }

    # 1. JDK 17
    $jdk = Join-Path $tool 'jdk'
    if (-not (Test-Path "$jdk\bin\java.exe")) {
        Step 'Descargando JDK 17 (Microsoft OpenJDK)'
        $zip = Join-Path $tool 'jdk.zip'
        $jdkUrl = 'https://aka.ms/download-jdk/microsoft-jdk-17-windows-x64.zip'
        Download $jdkUrl $zip
        # Microsoft publica la huella SHA-256 junto al ZIP.
        $sumFile = Join-Path $tool 'jdk.sha256.txt'
        & curl.exe -L --fail --silent -o $sumFile "$jdkUrl.sha256sum.txt"
        if ($LASTEXITCODE -eq 0) {
            Verify $zip 'SHA256' ((Get-Content $sumFile -Raw).Trim().Split(' ')[0])
            Remove-Item $sumFile -Force
        } else {
            Write-Host 'AVISO: no se pudo obtener la huella del JDK; se confia en la conexion HTTPS.' -ForegroundColor Yellow
        }
        $tmp = Join-Path $tool 'jdk-tmp'
        if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
        Expand-Archive $zip $tmp
        Move-Item (Get-ChildItem $tmp -Directory | Select-Object -First 1).FullName $jdk
        Remove-Item $tmp, $zip -Recurse -Force
    }
    $env:JAVA_HOME = $jdk
    $env:Path = "$jdk\bin;$env:Path"
    & java -version

    # 2. Android SDK command-line tools
    $sdk = Join-Path $tool 'sdk'
    $sdkm = Join-Path $sdk 'cmdline-tools\latest\bin\sdkmanager.bat'
    if (-not (Test-Path $sdkm)) {
        Step 'Descargando Android SDK command-line tools'
        $url = $null
        $sha1 = $null
        try {
            $xmlFile = Join-Path $tool 'repo.xml'
            Download 'https://dl.google.com/android/repository/repository2-3.xml' $xmlFile
            [xml]$repo = Get-Content $xmlFile
            $pkg = $repo.SelectNodes('//remotePackage') | Where-Object { $_.path -eq 'cmdline-tools;latest' } | Select-Object -First 1
            $complete = ($pkg.archives.archive | Where-Object { $_.'host-os' -eq 'windows' } | Select-Object -First 1).complete
            if ($complete.url) {
                $url = "https://dl.google.com/android/repository/$($complete.url)"
                $sha1 = $complete.checksum.'#text'
                if (-not $sha1) { $sha1 = [string]$complete.checksum }
            }
        } catch { Write-Host "No se pudo leer el catalogo, uso la version conocida" }
        if (-not $url) { $url = 'https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip' }
        $zip = Join-Path $tool 'cmdline.zip'
        Download $url $zip
        if ($sha1) { Verify $zip 'SHA1' $sha1 }
        else { Write-Host 'AVISO: sin huella del catalogo de Google; se confia en la conexion HTTPS.' -ForegroundColor Yellow }
        $tmp = Join-Path $tool 'cmdline-tmp'
        if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
        Expand-Archive $zip $tmp
        New-Item -ItemType Directory -Force (Join-Path $sdk 'cmdline-tools') | Out-Null
        Move-Item (Join-Path $tmp 'cmdline-tools') (Join-Path $sdk 'cmdline-tools\latest')
        Remove-Item $tmp, $zip -Recurse -Force
    }
    $env:ANDROID_HOME = $sdk
    $env:ANDROID_SDK_ROOT = $sdk

    Step 'Aceptando licencias del Android SDK'
    $yes = ("y`n" * 40)
    $yes | & $sdkm --sdk_root=$sdk --licenses | Out-Host

    Step 'Instalando plataforma Android 35 y build-tools'
    & $sdkm --sdk_root=$sdk 'platform-tools' 'platforms;android-35' 'build-tools;35.0.0' | Out-Host
    if ($LASTEXITCODE -ne 0) { throw 'sdkmanager fallo' }

    # 3. Compilar
    $sdkEsc = $sdk.Replace('\', '\\').Replace(':', '\:')
    Set-Content -Path (Join-Path $root 'local.properties') -Value "sdk.dir=$sdkEsc" -Encoding ASCII
    Step 'Compilando (la primera vez tarda varios minutos)'
    Push-Location $root
    & .\gradlew.bat --no-daemon testDebugUnitTest assembleRelease
    $code = $LASTEXITCODE
    Pop-Location
    if ($code -ne 0) { throw "Gradle termino con error $code" }

    $apk = Get-ChildItem (Join-Path $root 'app\build\outputs\apk\release') -Filter *.apk | Select-Object -First 1
    Copy-Item $apk.FullName (Join-Path $root 'ScudoDNS.apk') -Force
    Step "LISTO: $(Join-Path $root 'ScudoDNS.apk')"
    Set-Content -Path (Join-Path $root 'build-status.txt') -Value 'OK'
} catch {
    Write-Host "`nERROR: $_" -ForegroundColor Red
    Set-Content -Path (Join-Path $root 'build-status.txt') -Value "ERROR: $_"
} finally {
    Stop-Transcript | Out-Null
}
