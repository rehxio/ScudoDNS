# Seguridad

## Cómo reportar una vulnerabilidad

**No abras una issue pública** para un fallo de seguridad: cualquiera podría aprovecharlo antes de que esté corregido.

Repórtalo en privado desde la pestaña **Security → Report a vulnerability** de este repositorio. Incluye:

- Qué has encontrado y qué impacto tiene.
- Pasos para reproducirlo (y, si puedes, una prueba de concepto).
- Versión de ScudoDNS y de Android.

Responderé lo antes posible. Cuando esté corregido, se publicará una versión nueva y el aviso correspondiente, con reconocimiento a quien lo reportó si así lo desea.

## Versiones con soporte

Solo la última versión publicada en [Releases](https://github.com/rehxio/ScudoDNS/releases) recibe correcciones de seguridad.

## Alcance

ScudoDNS funciona solo en el móvil: no tiene servidores, cuentas ni analíticas. Interesan especialmente:

- Fallos al procesar paquetes o respuestas DNS (cierres, lecturas fuera de límites, respuestas falsificadas aceptadas).
- Formas de que otra app del móvil o alguien en la misma red salte el filtro o lo altere.
- Problemas en la descarga o el procesado de listas.
- Fugas del registro de consultas fuera del dispositivo.

No se consideran vulnerabilidades las limitaciones propias de cualquier bloqueador por DNS (anuncios servidos desde el mismo dominio que el contenido, apps que usan su propio DNS cifrado), que están documentadas en la [wiki](https://github.com/rehxio/ScudoDNS/wiki/Problemas-frecuentes).

## Verificar el APK

Las versiones oficiales están firmadas siempre con la misma clave. Android no permite instalar una actualización firmada con otra clave encima de la app ya instalada, así que un APK manipulado no puede sustituir al tuyo sin que lo desinstales antes. Descarga ScudoDNS solo desde las Releases de este repositorio.
