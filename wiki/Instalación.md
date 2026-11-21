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

ScudoDNS se mantiene como servicio en primer plano (verás una notificación discreta con el botón **Pausar**) y se vuelve a encender sola al reiniciar. Para pausarla o reanudarla al momento, añade el botón **ScudoDNS** a los ajustes rápidos (desliza desde arriba, pulsa el lápiz de editar y arrástralo al panel). Aun así, algunos fabricantes cierran apps en segundo plano de forma agresiva. Si la protección se apaga sola, haz esto (en la pantalla de Inicio, la tarjeta «Que no se cierre nunca» tiene un acceso directo para cada paso y los marca como hechos):

- **VPN siempre activada**: *Ajustes → Redes → VPN → ScudoDNS → engranaje → VPN siempre activada*. Así arranca sola al encender el móvil.
- **Batería sin restricciones**: acepta el aviso de Android desde la tarjeta. Evita que el sistema cierre la app.
- **Ajustes propios de la marca** (la tarjeta muestra solo los de tu móvil):
  - *Xiaomi / Redmi / POCO*: ahorro de batería de ScudoDNS en «Sin restricciones» y ScudoDNS activado en «Autoinicio en segundo plano».
  - *Huawei / Honor*: Ajustes → Batería → Inicio de aplicaciones → ScudoDNS → desactiva «Gestionar automáticamente» y activa las tres opciones.
  - *OPPO / realme / OnePlus*: ficha de ScudoDNS → Uso de la batería → «Permitir actividad en segundo plano» e «Inicio automático».
  - *vivo / iQOO*: Ajustes → Batería → «Alto consumo en segundo plano» → ScudoDNS, e «Inicio automático».
  - *Samsung*: Ajustes → Batería → Límites de uso en segundo plano → «Apps nunca en suspensión» → añade ScudoDNS.
  - Más detalles por marca en [dontkillmyapp.com](https://dontkillmyapp.com).

## Actualizar

ScudoDNS avisa cuando sale una versión nueva: aparece una tarjeta en Inicio (y una notificación discreta) con las novedades. Pulsa **Descargar**: el navegador descarga el APK y, al terminar, tócalo para instalarlo encima.

La comprobación se puede desactivar, o hacer a mano, en **Acerca de** (icono ⓘ). También puedes descargar el APK de [Releases](https://github.com/rehxio/ScudoDNS/releases) e instalarlo encima. En los dos casos se conservan listas y reglas.
