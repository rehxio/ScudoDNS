# Registro y reglas

## Registro

La pestaña **Registro** muestra las últimas 10.000 consultas DNS, como el *Query Log* de Pi-hole.

- 🟢 **Verde**: permitida.
- 🔴 **Rojo**: bloqueada.
- 🟠 **Naranja**: permitida, pero ningún DNS respondió. Si ves muchas, revisa [[Problemas frecuentes|Problemas-frecuentes]].
- 👤 **Icono de persona**: la decisión la ha tomado una regla tuya.

En cada línea aparece la hora, el tipo de consulta (A, AAAA, HTTPS…) y, en Android 10 o superior, **la app que la hizo**.

Herramientas:

- **Buscar** por dominio o por nombre de app.
- **Filtrar**: Todas, Bloqueadas o Permitidas.
- **En directo / En pausa**: congela la lista para leerla con calma.
- **Por página**: 25, 50, 100 o 200 consultas, con flechas para pasar de página.
- **Vaciar** (icono de la papelera, arriba): borra el registro sin tocar listas ni reglas.

## Bloquear o permitir desde el registro

Toca una consulta y se abre un panel:

1. En **Aplicar a**, elige el nivel: el subdominio exacto (`cdn.ads.ejemplo.com`) o uno superior (`ejemplo.com`). La regla afecta también a todos los subdominios.
2. Pulsa **Bloquear** o **Permitir**.
3. Si ya tenías una regla para ese dominio, aparece **Quitar mi regla**.

Algunas apps guardan la respuesta DNS un minuto, así que el cambio puede tardar ese tiempo en notarse.

## Pestaña Reglas

Aquí ves y gestionas tus reglas a mano:

- **Añadir regla**: escribe un dominio y pulsa Bloquear o Permitir. También admite que pegues una URL; se queda con el dominio.
- **Bloqueados por ti** y **Permitidos por ti**: pulsa ✕ para quitar una regla.

Orden de prioridad:

1. Tus reglas. Entre ellas gana la más específica.
2. Excepciones de las listas (`@@||`).
3. Listas de bloqueo.

## Cuando una app deja de funcionar

1. Abre la app que falla.
2. Ve a **Registro**, filtra por **Bloqueadas** y busca el nombre de la app.
3. Permite el dominio que salga en rojo justo cuando falló.
4. Vuelve a probar. Si no era ese, quita la regla y prueba con el siguiente.
