# ScudoDNS

**ScudoDNS** es un bloqueador de anuncios y rastreadores para Android que funciona como un Pi-hole dentro del móvil. Filtra el DNS de todas las apps, en Wi-Fi y con datos móviles, sin root y sin mandar tu tráfico a ningún servidor externo.

## Qué hace

- Bloquea dominios de publicidad, rastreo y malware usando **listas por URL**, como Pi-hole o AdGuard Home.
- Muestra un **registro** de cada consulta: qué dominio, qué app la hizo y si se bloqueó.
- Te deja **bloquear o permitir** cualquier dominio con un toque.
- Gasta muy poca batería: solo pasa por la app el tráfico DNS, no el resto.

## Empieza por aquí

1. [[Instalación]]
2. [[Cómo funciona|Cómo-funciona]]
3. [[Listas de bloqueo|Listas-de-bloqueo]]
4. [[Registro y reglas|Registro-y-reglas]]
5. [[Problemas frecuentes|Problemas-frecuentes]]

## Lo que no puede hacer

Ningún bloqueador por DNS llega al 100 %. Los anuncios que se sirven **desde el mismo dominio que el contenido** (YouTube, Instagram, Facebook, Twitch, los patrocinios de Spotify) no se pueden bloquear sin romper esas apps. Esto es igual en Pi-hole, AdGuard DNS o NextDNS.

## Reportar un fallo o proponer algo

- Fallos: [Abrir una issue de fallo](https://github.com/rehxio/ScudoDNS/issues/new?template=fallo.yml)
- Ideas: [Proponer una mejora](https://github.com/rehxio/ScudoDNS/issues/new?template=mejora.yml)

## Software libre

ScudoDNS es gratis, sin anuncios y de código abierto bajo licencia **GPL-3.0**. Puedes revisar el código, compilarlo tú mismo y proponer mejoras. Si quieres apoyar su desarrollo, en la app tienes **Acerca de → Invitar a un café**, siempre opcional.
