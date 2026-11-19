# Compilar desde el código

## Windows, sin Android Studio

1. Clona el repositorio o descarga el ZIP.
2. Haz doble clic en `compilar.bat`.

La primera vez descarga en `%LOCALAPPDATA%\ScudoBuild` el JDK 17 (Microsoft), el Android SDK y Gradle: en total, cerca de 1 GB. Después compila, pasa los tests y deja `ScudoDNS.apk` en la carpeta del proyecto. El registro de la compilación queda en `build-log.txt`.

## Firma

Los APK de las versiones oficiales se firman con una clave propia. Para firmar los tuyos, ejecuta una vez `crear-clave.bat`: crea la clave en `%USERPROFILE%\.scudodns` (fuera del proyecto, para que nunca se suba a GitHub) y a partir de ahí `compilar.bat` la usa automáticamente. Sin ella, el APK se firma con la clave de pruebas de tu equipo.

## Android Studio

Abre la carpeta del proyecto y usa *Build → Build APK(s)*.

## Línea de comandos

```bash
./gradlew testDebugUnitTest   # tests
./gradlew assembleRelease     # APK en app/build/outputs/apk/release/
```

## GitHub Actions

Cada push a `main` y cada pull request compilan la app y pasan los tests. El APK de prueba queda como artefacto de la ejecución durante 14 días.

## Estructura

| Carpeta | Contenido |
|---|---|
| `vpn/` | VPN local, lectura del túnel, reenvío DNS y diagnóstico |
| `filter/` | Lector de listas y filtro en memoria |
| `lists/` | Alta, descarga y actualización de listas |
| `log/` | Registro de consultas y detección de la app |
| `ui/` | Interfaz en Jetpack Compose |

Todo cuelga de `app/src/main/java/es/rehxio/scudo/`.
