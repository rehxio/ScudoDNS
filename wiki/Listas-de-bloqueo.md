# Listas de bloqueo

## Listas de serie

| Lista | Qué bloquea | Estado inicial |
|---|---|---|
| HaGeZi Multi PRO | Publicidad, rastreo, telemetría y malware. Muy equilibrada. | Activada |
| StevenBlack Unified hosts | Publicidad y malware, la clásica de Pi-hole. | Activada |
| AdGuard DNS filter | La lista del servicio AdGuard DNS. | Desactivada |

Muchas listas comparten dominios. La cifra de **dominios bloqueados** de la pantalla de inicio cuenta cada dominio una sola vez.

## Añadir una lista

En Inicio, pulsa **Añadir lista** y pega la URL. Se descarga y se activa al momento.

Formatos admitidos:

| Formato | Ejemplo |
|---|---|
| hosts | `0.0.0.0 ads.ejemplo.com` |
| Un dominio por línea | `ads.ejemplo.com` o `*.ads.ejemplo.com` |
| AdBlock / AdGuard DNS | `\|\|ads.ejemplo.com^` |
| Excepciones | `@@\|\|permitido.com^` |

Se ignoran los comentarios, las reglas cosméticas (`##`), las expresiones regulares y las reglas con rutas (`||ejemplo.com/ads`), porque no se pueden aplicar por DNS.

## Listas recomendadas

| Lista | URL | Para qué |
|---|---|---|
| HaGeZi Pro++ | `https://raw.githubusercontent.com/hagezi/dns-blocklists/main/wildcard/pro.plus-onlydomains.txt` | Más agresiva que PRO. Puede romper alguna web. |
| HaGeZi Threat Intelligence | `https://raw.githubusercontent.com/hagezi/dns-blocklists/main/wildcard/tif-onlydomains.txt` | Malware, phishing y estafas. |

No hace falta acumular muchas listas: dos o tres buenas bloquean casi todo, y cuantas más pones, más fácil es que algo legítimo se rompa.

## Actualizaciones

- Automáticas cada 24 horas, cuando hay conexión.
- Manuales con el botón ↻ de la pantalla de inicio.
- Si la lista no ha cambiado, el servidor lo indica y no se vuelve a descargar.
- Si una descarga falla, la lista muestra el error en rojo y se sigue usando la última versión buena.
