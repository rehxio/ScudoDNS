# Historial de cambios

## 0.7.0
### Actualizaciones desde la app
- ScudoDNS avisa cuando hay una **versión nueva** (en Inicio y con una notificación discreta) y permite **descargarla e instalarla** con un toque. Android pide confirmación antes de instalar; la primera vez, también permiso para instalar apps desde ScudoDNS.
- Antes de instalar se comprueba que el archivo viene de GitHub por HTTPS, que su huella SHA-256 coincide con la publicada, que es una versión más nueva de ScudoDNS y que está firmado con la misma clave.
- Se comprueba una vez al día y al abrir la app (como mucho cada 6 horas). Se puede desactivar, o comprobar a mano, en *Acerca de*.

## 0.6.0 — 19/11/2026
### Siempre encendida
- La protección funciona como **servicio en primer plano**, con una notificación mínima y silenciosa (con botón «Pausar»), para que Android no la cierre para liberar memoria.
- **Se vuelve a encender sola** al reiniciar el móvil y al actualizar la app, si estaba encendida.
- Si el sistema mata el proceso, la vuelve a arrancar en cuanto puede.
- Aviso en Inicio, con accesos directos, para quitar el ahorro de batería y activar «VPN siempre activada» (y, en Xiaomi, el inicio automático).
- Si otra VPN toma el control, ScudoDNS se aparta y no intenta volver a encenderse.

### Más cómoda
- **Botón en los ajustes rápidos** (desliza desde arriba y añade «ScudoDNS» al panel): pausa o reanuda la protección sin abrir la app.
- Al activar la protección por primera vez, los permisos de notificaciones y de VPN se piden uno detrás de otro, ya no a la vez.

- Apartado de **donativos** opcional en *Acerca de* (Ko-fi), sin avisos ni ventanas emergentes.

### Correcciones
- La VPN ya no hace que Android trate el Wi-Fi como **conexión de pago**: las copias de seguridad y descargas grandes que esperan a una red sin límite vuelven a funcionar con la protección encendida.

### Menos consumo
- El hilo que lee el túnel ya no se despierta cada segundo: duerme hasta que llega una consulta.
- Sin consultas, los hilos de trabajo se liberan solos.
- La pantalla solo se actualiza mientras está visible.

## 0.5.0 — 19/11/2026
### Rendimiento
- **Caché DNS propia**: las consultas repetidas se contestan al instante, respetando el TTL de cada respuesta. También se guardan las respuestas negativas (NXDOMAIN) según su SOA.
- **Respuestas caducadas al instante** (RFC 8767): si una respuesta acaba de caducar se entrega la anterior con TTL corto y se actualiza en segundo plano.
- **Consultas escalonadas**: si el primer servidor DNS no contesta en un tiempo algo mayor de lo habitual (60–400 ms según lo que suela tardar), se pregunta también al siguiente y gana la primera respuesta. Antes había que esperar 2 s.
- Los servidores que fallan varias veces seguidas pasan al final de la lista.
- La app que hace cada consulta se averigua en paralelo cuando hay que salir a la red, sin retrasar la respuesta.
- La caché se vacía al cambiar de red o de servidores DNS. En el Registro, las respuestas servidas desde caché llevan la etiqueta «caché».

## 0.4.0 — 19/11/2026
- La app pasa a llamarse **ScudoDNS** (paquete `es.rehxio.scudo`). Antes se llamaba Escudo DNS.
- Aviso en la pantalla principal, con botón a los ajustes, si el **DNS privado** de Android está activo.
- La tarjeta Diagnóstico aparece plegada por defecto.
- Nueva pantalla **Acerca de** (icono ⓘ arriba o enlace al final de Inicio): versión, licencia, código, guía, reportar fallos y un apartado opcional de donativos.
- Licencia **GPL-3.0**: ScudoDNS pasa a ser software libre.
- Web del proyecto (`docs/`, GitHub Pages), wiki (`wiki/`), plantillas de issues y pull requests, y compilación automática en GitHub Actions.

### Seguridad
- Las respuestas DNS solo se aceptan del servidor al que se preguntó (socket conectado) y si coinciden en ID, tipo de mensaje y pregunta: se impide colar respuestas falsificadas desde la red.
- Cola de consultas acotada: una app que inunde de consultas ya no puede agotar la memoria (las sobrantes se descartan y se cuentan en Diagnóstico).
- Las listas solo se descargan por **HTTPS**, también tras una redirección.
- Límites frente a listas enormes o maliciosas: 150 MB por descarga, 5 millones de dominios por lista, 10 millones en total y líneas de más de 2.048 caracteres ignoradas.
- Preparada la firma con **clave propia**, guardada fuera del repositorio (se usa a partir de la 0.6.0).
- `compilar.bat` verifica la huella SHA-256 del JDK y SHA-1 de las herramientas de Android antes de usarlas.
- GitHub Actions valida `gradle-wrapper.jar`, no guarda credenciales en el checkout y Dependabot vigila dependencias y acciones.
- Política de seguridad (`SECURITY.md`) para reportar vulnerabilidades en privado.

## 0.3.1 — 19/11/2026
- Tarjeta **Diagnóstico** en Inicio: paquetes leídos, consultas respondidas, DNS en uso, último error y aviso si el **DNS privado** de Android está activo.
- Las conexiones TCP al DNS (DNS sobre TLS en el puerto 853 y DNS por TCP) se rechazan al instante con RST, en vez de quedarse esperando.
- Explicado el caso de "sin Internet con la protección activa": ocurre cuando el DNS privado de Android tiene un servidor puesto (p. ej. `dns.adguard.com`). Hay que desactivarlo.

## 0.3.0 — 19/11/2026
- Las consultas se reenvían primero al **DNS de la red** (router, Pi-hole u operador) y después a los de respaldo (Quad9, Cloudflare).
- Cambio de servidor si uno no responde en 2 s; si ninguno responde se contesta SERVFAIL para que las apps no se queden colgadas.
- Se aceptan solo respuestas cuyo ID coincide con la consulta.
- Las consultas sin respuesta aparecen en naranja en el Registro.
- Corregida la URL de HaGeZi Multi PRO (devolvía 404) con migración automática.
- Corregido el contador de dominios cuando el servidor responde "sin cambios" (304).

## 0.2.0 — 19/11/2026
- Pestaña **Registro** al estilo del *Query Log* de Pi-hole: últimas 10.000 consultas, app que la hizo (Android 10+), búsqueda, filtros, modo en directo y paginación de 25/50/100/200.
- Bloquear o permitir un dominio desde el registro, eligiendo el nivel (subdominio o dominio).
- Pestaña **Reglas** con dominios bloqueados y permitidos a mano (prioridad sobre las listas).
- Rediseño visual: paleta propia con modo oscuro, tarjeta de estado animada y navegación inferior.
- Descargas condicionales (ETag / Last-Modified): si una lista no cambia, no se vuelve a descargar.
- Script `compilar.bat` para generar el APK en Windows sin Android Studio.

## 0.1.0 — 19/11/2026
- Primera versión: VPN local que solo captura el DNS y bloquea dominios de listas por URL.
- Formatos hosts, un dominio por línea y AdBlock/AdGuard (`||dominio^`, `@@||` como lista blanca).
- Filtro en memoria con hashes de 64 bits ordenados (~8 MB por millón de dominios).
- Actualización diaria de listas con WorkManager.
