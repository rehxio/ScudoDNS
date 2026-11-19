# Escudo DNS

Bloqueador de anuncios y rastreadores para Android, al estilo Pi-hole, que funciona dentro del móvil (también con datos móviles y fuera de casa).

## Cómo funciona

- Crea una **VPN local** que solo captura el DNS. El resto del tráfico no pasa por el túnel, así que no gasta batería ni pierde velocidad, y nada sale del móvil hacia servidores de terceros.
- Cada consulta se compara con tus listas. Si el dominio (o un dominio padre) está en ellas, responde `0.0.0.0` / `::` al instante, igual que el modo NULL de Pi-hole. Si no, la reenvía a tus DNS (por defecto Quad9 `9.9.9.9` y Cloudflare `1.1.1.1`).
- Los dominios se guardan como hashes de 64 bits en un array ordenado: 1 millón de dominios ocupan unos 8 MB de RAM y cada búsqueda tarda microsegundos.

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
- HaGeZi Pro++ (más agresiva): `https://raw.githubusercontent.com/hagezi/dns-blocklists/main/domains/pro.plus.txt`
- HaGeZi TIF (amenazas y malware): `https://raw.githubusercontent.com/hagezi/dns-blocklists/main/domains/tif.txt`

## Compilar

**Opción A: GitHub, sin instalar nada.** Sube la carpeta a un repositorio de GitHub. La acción `.github/workflows/build.yml` pasa los tests, compila el APK y lo deja en *Actions → (última ejecución) → Artifacts*.

**Opción B: Windows sin Android Studio.** Doble clic en `compilar.bat`. La primera vez descarga JDK 17 y el Android SDK en `%LOCALAPPDATA%\EscudoBuild` (~1 GB) y deja el APK como `EscudoDNS.apk` en la carpeta del proyecto. El registro queda en `build-log.txt`.

**Opción C: Android Studio.** Abre la carpeta y ejecuta *Run* o *Build → Build APK(s)*.

El APK de release va firmado con la clave de debug para que puedas instalarlo directamente. Si lo compilas en sitios distintos (por ejemplo en GitHub y en tu PC), cada uno usa una clave diferente: tendrás que desinstalar la app antes de instalar la otra versión.

## Ajustes del móvil para que no se escape nada

1. **Ajustes → Conexión y uso compartido → DNS privado → Desactivado.** Si tiene un servidor puesto (p. ej. `dns.adguard.com`), Android manda el DNS cifrado por la VPN y **se queda sin Internet**. La tarjeta Diagnóstico avisa si está activo.
2. **Chrome → Configuración → Privacidad → Usar DNS seguro → Desactivado.** Lo mismo en Firefox (DoH) y Brave.
3. **Ajustes → Red → VPN → Escudo DNS → VPN siempre activada.** Así arranca sola al encender el móvil.
4. En Xiaomi, Samsung o Huawei: quita la optimización de batería para Escudo DNS.

## Limitaciones (comunes a cualquier bloqueador DNS)

- Los anuncios que vienen del **mismo dominio que el contenido** no se pueden bloquear por DNS: YouTube, Instagram, Facebook, Twitch o los patrocinios de Spotify.
- Una app que use su propio DNS cifrado (DoH a una IP fija) se salta el filtro.
- Solo puede haber una VPN activa a la vez en Android, así que no es compatible con WireGuard u otra VPN encendida al mismo tiempo.

## Estructura

```
app/src/main/java/es/fabi/escudo/
├── vpn/EscudoVpnService.kt   VPN local, lectura del túnel y reenvío DNS
├── vpn/Packets.kt            Parseo y construcción de paquetes IPv4/IPv6 + UDP
├── vpn/Dns.kt                Lectura de consultas y respuestas de bloqueo
├── filter/ListParser.kt      Parser de formatos hosts / dominios / AdBlock
├── filter/Blocklist.kt       Filtro en memoria (hashes ordenados) y ficheros
├── lists/ListRepository.kt   Alta, baja, descarga y combinación de listas
├── lists/UpdateWorker.kt     Actualización diaria (WorkManager)
├── log/QueryLog.kt           Registro circular, filtros y paginación
├── log/AppResolver.kt        App que hizo cada consulta (Android 10+)
└── ui/                       Interfaz en Jetpack Compose (Inicio, Registro, Reglas)
```
