# Cómo funciona

## La idea

Cada vez que una app quiere conectarse a `ads.ejemplo.com`, primero pregunta al **DNS** qué dirección IP tiene ese nombre. Si la respuesta es "ninguna" (`0.0.0.0`), la app no puede cargar el anuncio. Eso es lo que hacen Pi-hole o AdGuard DNS, y lo que hace ScudoDNS dentro del móvil.

## La VPN local

Android solo deja a una app ver el tráfico de las demás a través de una **VPN**. ScudoDNS crea una VPN que **solo captura el DNS**:

1. Le dice a Android que su servidor DNS es `10.111.222.2`, una dirección que solo existe dentro de la VPN.
2. Lee cada consulta que llega a esa dirección.
3. Si el dominio (o uno de sus dominios padre) está en tus listas, responde al instante `0.0.0.0` (o `::` en IPv6).
4. Si no, la reenvía al DNS real y devuelve la respuesta.

El resto del tráfico (webs, vídeos, descargas) **no pasa por la VPN**, así que no pierdes velocidad ni gastas batería de más.

## A qué DNS reenvía

Por orden:

1. **El DNS de tu red**: el del router en casa (o tu Pi-hole), el de tu operador con datos móviles.
2. **Los de respaldo**: Quad9 (`9.9.9.9`) y Cloudflare (`1.1.1.1`) por defecto. Puedes cambiarlos con el botón **DNS** de la pantalla de inicio.

Si un servidor no contesta en 2 segundos se prueba el siguiente. Si no contesta ninguno, ScudoDNS responde con un error (SERVFAIL) para que la app no se quede colgada.

## Qué se bloquea

- Un dominio de la lista bloquea **también sus subdominios**: si está `doubleclick.net`, también cae `ad.g.doubleclick.net`.
- Las reglas `@@||dominio^` de las listas funcionan como excepciones.
- **Tus reglas mandan sobre todo**, y entre ellas gana la más específica. Por ejemplo, si permites `ejemplo.com` pero bloqueas `ads.ejemplo.com`, se bloquea solo `ads`.

## Rendimiento

- Los dominios se guardan como huellas de 64 bits en un array ordenado: **1 millón de dominios ocupa unos 8 MB** de RAM.
- Cada consulta se resuelve en microsegundos.
- Las listas se actualizan una vez al día, y solo se descargan si han cambiado.

## Privacidad

- Ninguna consulta sale hacia servidores de ScudoDNS: no existen.
- El registro de consultas se guarda **solo en memoria** (las últimas 10.000) y se borra al apagar la protección o con el botón de vaciar.
