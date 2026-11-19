# ScudoDNS

Bloqueador de anuncios y rastreadores para Android, al estilo Pi-hole, que funciona dentro del móvil (también con datos móviles y fuera de casa).

**Software libre (GPL-3.0), gratis y sin rastreo.**

[Web](https://rehxio.github.io/ScudoDNS/) · [Descargar](https://github.com/rehxio/ScudoDNS/releases/latest) · [Guía](https://github.com/rehxio/ScudoDNS/wiki) · [Reportar un fallo](https://github.com/rehxio/ScudoDNS/issues/new?template=fallo.yml) · [Proponer una mejora](https://github.com/rehxio/ScudoDNS/issues/new?template=mejora.yml)

## Instalar

Descarga el APK de la [última versión](https://github.com/rehxio/ScudoDNS/releases/latest) y ábrelo en el móvil (Android 8 o superior). Android pedirá permiso para instalar apps de origen desconocido. Las versiones nuevas se instalan encima y conservan tus listas y reglas.

## Cómo funciona

- Crea una **VPN local** que solo captura el DNS. El resto del tráfico no pasa por el túnel, así que no gasta batería ni pierde velocidad, y nada sale del móvil hacia servidores de terceros.
- Cada consulta se compara con tus listas. Si el dominio (o un dominio padre) está en ellas, responde `0.0.0.0` / `::` al instante, igual que el modo NULL de Pi-hole. Si no, la reenvía a tus DNS (por defecto Quad9 `9.9.9.9` y Cloudflare `1.1.1.1`).
- Es muy ligera: un millón de dominios bloqueados ocupan unos 8 MB de memoria y cada consulta se decide en microsegundos.

## Pestañas

- **Inicio**: estado de la protección, estadísticas y listas de bloqueo.
- **Registro**: las últimas 10.000 consultas, como el *Query Log* de Pi-hole. Puedes buscar por dominio o app, filtrar por bloqueadas o permitidas, pausar el modo en directo y elegir 25, 50, 100 o 200 por página. Al tocar una consulta puedes bloquearla o permitirla, y elegir el nivel (`a.ads.ejemplo.com` o `ejemplo.com`).
- **Reglas**: tus dominios bloqueados y permitidos a mano. Tienen prioridad sobre las listas, y la regla más específica gana.

En Android 10 o superior, el registro muestra también qué app hizo cada consulta.

## Listas

Puedes añadir cualquier lista por URL. Se admiten estos formatos:

| Formato | Ejemplo |
|---|---|
| hosts | `0.0.0.0 ads.example.com` |
| Un dominio por línea | `ads.example.com` / `*.ads.example.com` |
| AdBlock / AdGuard DNS | `\|\|ads.example.com^` |
| Excepciones (lista blanca) | `@@\|\|permitido.com^` |

Vienen de serie HaGeZi Multi PRO y StevenBlack (activadas), y el filtro DNS de AdGuard (desactivado). Se actualizan solas cada 24 h, y también puedes forzar la actualización con el botón ↻. Si una lista no ha cambiado desde la última vez, el servidor responde «sin cambios» (ETag) y no se descarga de nuevo.

Algunas listas recomendadas:
- HaGeZi Pro++ (más agresiva): `https://raw.githubusercontent.com/hagezi/dns-blocklists/main/wildcard/pro.plus-onlydomains.txt`
- HaGeZi TIF (amenazas y malware): `https://raw.githubusercontent.com/hagezi/dns-blocklists/main/wildcard/tif-onlydomains.txt`

## Ajustes del móvil para que no se escape nada

1. **DNS privado de Android → Desactivado** (la ruta cambia según la marca; está explicada en la [guía de instalación](https://github.com/rehxio/ScudoDNS/wiki/Instalación)). Si tiene un servidor puesto (p. ej. `dns.adguard.com`), Android manda el DNS cifrado por la VPN y **se queda sin Internet**. ScudoDNS avisa en Inicio si está activo.
2. **Chrome → Configuración → Privacidad → Usar DNS seguro → Desactivado.** Lo mismo en Firefox (DoH) y Brave.
3. **VPN siempre activada** para ScudoDNS (en los ajustes de VPN de Android), para que arranque sola al encender el móvil.
4. En Xiaomi, Samsung o Huawei: quita la optimización de batería para ScudoDNS.

## Limitaciones (comunes a cualquier bloqueador DNS)

- Los anuncios que vienen del **mismo dominio que el contenido** no se pueden bloquear por DNS: YouTube, Instagram, Facebook, Twitch o los patrocinios de Spotify.
- Una app que use su propio DNS cifrado (DoH a una IP fija) se salta el filtro.
- Solo puede haber una VPN activa a la vez en Android, así que no es compatible con WireGuard u otra VPN encendida al mismo tiempo.

## Compilar

Para quien quiera revisar el código o compilar su propia copia:

- **Windows:** doble clic en `compilar.bat` (descarga lo necesario la primera vez y deja `ScudoDNS.apk` en la carpeta).
- **Android Studio:** abre la carpeta y usa *Build → Build APK(s)*.
- **Línea de comandos:** `./gradlew testDebugUnitTest assembleRelease`.

Las copias compiladas por tu cuenta se firman con otra clave, así que no se instalan encima de la versión oficial: desinstala una antes de instalar la otra. Más detalles y la estructura del código en la [guía de compilación](https://github.com/rehxio/ScudoDNS/wiki/Compilar-desde-el-código).

## Contribuir

Los fallos y las ideas se recogen en [Issues](https://github.com/rehxio/ScudoDNS/issues), con plantillas que piden lo necesario para reproducirlos. Las propuestas de código llegan como pull request: cada una se revisa antes de incorporarse, y GitHub Actions comprueba que compila y pasa los tests.

## Apoyar el proyecto

ScudoDNS es y será gratis, sin anuncios ni versión de pago. Si te resulta útil y quieres invitarme a un café: [ko-fi.com/rehxio](https://ko-fi.com/rehxio).

## Seguridad

Para reportar una vulnerabilidad, consulta [SECURITY.md](SECURITY.md). No abras una issue pública.

## Licencia

ScudoDNS es software libre: puedes usarlo, estudiarlo, modificarlo y redistribuirlo bajo los términos de la [GNU General Public License v3.0](LICENSE). Las versiones modificadas que se distribuyan deben seguir siendo libres y con el código disponible.
