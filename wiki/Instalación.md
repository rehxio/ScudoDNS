# Instalación

## Requisitos

- Android **8.0** o superior.
- Para ver qué app hace cada consulta en el Registro: Android **10** o superior.

## Pasos

1. Descarga el APK de la última versión en [Releases](https://github.com/rehxio/ScudoDNS/releases).
2. Ábrelo desde el móvil. Android pedirá permiso para instalar apps desde ese origen (navegador o gestor de archivos): acéptalo.
3. Abre ScudoDNS. La primera vez descarga las listas por defecto (unos segundos).
4. Activa el interruptor de la tarjeta verde y acepta la petición de **conexión VPN**. Es una VPN local: el tráfico no sale del móvil hacia ningún servidor nuestro.

## Ajustes imprescindibles

### Desactiva el DNS privado

Si el DNS privado de Android tiene un servidor puesto (por ejemplo `dns.adguard.com`), **te quedarás sin Internet** con la protección activa. ScudoDNS lo detecta y muestra un aviso rojo en Inicio.

- Xiaomi / HyperOS: *Ajustes → Conexión y uso compartido → DNS privado → Desactivado*
- Pixel y Android puro: *Ajustes → Redes e Internet → DNS privado → Desactivado*
- Samsung: *Ajustes → Conexiones → Más ajustes de conexión → DNS privado → Desactivado*

### Desactiva el DNS seguro del navegador

- Chrome: *Ajustes → Privacidad y seguridad → Usar DNS seguro → Desactivado*
- Firefox y Brave tienen una opción equivalente (DNS sobre HTTPS).

Si no, el navegador resuelve los nombres por su cuenta y se salta el filtro.

## Recomendados

- **VPN siempre activada**: *Ajustes → Redes → VPN → ScudoDNS → engranaje → VPN siempre activada*. Así arranca sola al encender el móvil.
- **Batería sin restricciones** (Xiaomi, Samsung, Huawei, Oppo): *Ajustes → Apps → ScudoDNS → Ahorro de batería → Sin restricciones*. Evita que el sistema cierre la app.

## Actualizar

Instala el APK nuevo encima del anterior. Se conservan listas y reglas.

> Si vienes de **Escudo DNS** (versiones 0.1–0.3), desinstálala antes: ScudoDNS usa otro identificador y Android la trata como una app distinta.
