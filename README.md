# Siderúrgica Perú móvil — avance sprints 1 y 2

Proyecto Android nativo en Kotlin y Jetpack Compose. Se construyó a partir del backlog entregado y de `SIDERURGICA-ERP-feature-automatizacion.zip` (backend Spring Boot + frontend Angular). El código del ERP de referencia no se modifica aquí.

## Abrir y configurar

1. Abre esta carpeta en Android Studio con JDK 17 y SDK 36. Si Android Studio solicita Gradle, genera el wrapper con Gradle 8.13 (`gradle wrapper --gradle-version 8.13`) y sincroniza el proyecto. El wrapper binario no se incluye.
2. Configura un proyecto Firebase Android con el identificador `pe.edu.unmsm.siderurgica`. Habilita Email/Password en Authentication y Storage. Descarga `google-services.json` y colócalo en `app/`. Publica las reglas incluidas en `firebase.storage.rules` tras revisarlas en tu proyecto Firebase. No subas el JSON a un repositorio público.
3. En `~/.gradle/gradle.properties` agrega `ERP_API_URL=http://10.0.2.2:3000/` para emulador y backend local. En teléfono físico usa una URL HTTPS alcanzable desde el teléfono. La barra final es obligatoria. La app release bloquea HTTP; debug admite HTTP para desarrollo local.
4. Levanta el backend con su PostgreSQL y variables de entorno según `BACKEND/README.md`. Crea o utiliza un usuario ERP con rol `VENTAS` o `ADMIN`. Inicia Firebase con correo/contraseña y vincula por separado el usuario del ERP.

## Implementado

| Historia | Avance en este proyecto |
| --- | --- |
| HU01 | Kotlin, Compose Material 3, capas UI/datos, ViewModel, Retrofit y corrutinas. |
| HU02 | Firebase Email/Password: registro, acceso, recuperación, cierre. ERP `/login` entrega JWT y rol. Persistencia cifrada del token con Android Keystore. Acceso biométrico opcional al reabrir con una sesión ERP guardada. |
| HU03 | Room mantiene cotizaciones, pedidos, ventas e inventario por usuario ERP. WorkManager actualiza lecturas con red. Borradores de cotización/pedido se guardan localmente y se envían tras confirmación. |
| HU04 | Listado, búsqueda y alta de cotizaciones con Retrofit. Previsualización del descuento por cantidad que aplica el backend. Aprobación genera pedido en una transacción del ERP. |
| HU05 | Alta y listado de pedidos; aprobación valida/reserva stock y genera venta mediante el flujo existente del backend. Anulación usa el estado `ANULADO` aceptado por el ERP. |
| HU06 | PDF informativo de venta, compartir con Android y subida opcional a Firebase Storage bajo la cuenta Firebase. |

## Límites del backend de referencia

- El ERP actual autentica con nombre de usuario/JWT. Firebase no emite permisos ERP. Un registro nuevo en Firebase **no crea un usuario del ERP**; el administrador debe darle una cuenta y rol. La app pide ambas identidades para acceder a Storage y operar ventas.
- El backend admite un producto por cotización y pedido. La pantalla respeta ese contrato, sin fingir un carrito de varias líneas.
- Aprobar una cotización ya genera su pedido; aprobar un pedido ya genera la venta y lo pasa a `FACTURADO`. La app no repite esas operaciones.
- El ERP no ofrece idempotencia de escritura. Reenviar automáticamente un POST tras perder la respuesta puede duplicar registros. Por eso WorkManager sincroniza **solo lecturas** y los borradores requieren confirmación manual. La sincronización automática segura de operaciones pendientes sigue pendiente en el backend.
- El backend no emite comprobantes tributarios. El PDF generado es una **constancia informativa**, no una boleta o factura SUNAT. La emisión fiscal requiere datos, numeración y un servicio de facturación válidos.
- No se incluyen credenciales, configuración privada de Firebase ni endpoints de producción.

## Validación

Se cotejaron nombres de campos y rutas contra los controladores, entidades y servicios de la versión del ERP proporcionada. Este entorno no tiene Android SDK ni Gradle, por lo que aún faltan sincronización de dependencias, compilación, emulador y pruebas de extremo a extremo en Android Studio.
