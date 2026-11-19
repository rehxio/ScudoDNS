# Problemas frecuentes

## Con la protección activa no carga nada

Casi siempre es el **DNS privado** de Android. Si tiene un servidor puesto (por ejemplo `dns.adguard.com`), Android envía el DNS cifrado a ese servidor **por dentro de la VPN**, y la VPN de ScudoDNS solo deja pasar su propia dirección DNS. Resultado: ningún nombre se resuelve.

**Cómo saberlo:** en Inicio aparece un aviso rojo, **"Desactiva el DNS privado"**. En el Registro solo verás consultas al propio servidor (por ejemplo `dns.adguard.com`).

**Solución:** pon el DNS privado en **Desactivado**. Las rutas para cada marca están en [[Instalación]].

No pierdes nada: ScudoDNS hace el mismo filtrado y además con tus propias listas.

## Funciona pero algunas consultas salen en naranja

Ningún DNS ha respondido a esas consultas. Abre la tarjeta **Diagnóstico** en Inicio:

- **Último error: sin respuesta en 2 s**: tu red bloquea ese servidor DNS. Cambia los de respaldo con el botón **DNS**, o deja que se use el de tu router.
- **DNS en uso: ninguno**: no hay conexión, o no se pudo leer el DNS de tu red. Revisa los DNS de respaldo.

## Una app o web concreta ha dejado de funcionar

Se está bloqueando un dominio que necesita. Sigue los pasos de [[Registro y reglas|Registro-y-reglas]], apartado *Cuando una app deja de funcionar*.

## Sigo viendo anuncios en YouTube, Instagram o Twitch

Es una limitación de cualquier bloqueador por DNS: esos anuncios vienen del mismo dominio que el vídeo o las fotos, y bloquearlos rompería la app. Explicado en [[Cómo funciona|Cómo-funciona]].

## El navegador se salta el bloqueo

El navegador tiene activado su propio **DNS seguro** (DNS sobre HTTPS). Desactívalo como se explica en [[Instalación]].

## No puedo usar otra VPN a la vez

Android solo permite una VPN activa. ScudoDNS no es compatible con WireGuard, OpenVPN, AdGuard u otra VPN encendida al mismo tiempo.

## La protección se apaga sola

El fabricante está cerrando la app para ahorrar batería. Pon la batería de ScudoDNS en **Sin restricciones** y activa **VPN siempre activada** (ver [[Instalación]]).

## No aparece qué app hizo la consulta

Solo funciona en Android 10 o superior. En algunas consultas del sistema aparece *Sistema Android* o *Resolución DNS del sistema*.

## Sigue sin funcionar

Abre una issue con la plantilla de fallo e incluye una captura de la tarjeta **Diagnóstico** desplegada: [Reportar un fallo](https://github.com/rehxio/ScudoDNS/issues/new?template=fallo.yml).
