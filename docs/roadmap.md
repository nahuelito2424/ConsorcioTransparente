# Roadmap — Consorcio Transparente

> Estado de avance y próximos pasos. El detalle funcional de cada historia está en
> [`especificacion-funcional.md`](./especificacion-funcional.md); el cómo técnico en
> [`arquitectura.md`](./arquitectura.md). Actualizar esta tabla cada vez que una
> historia cambia de estado.

## Estado de las Historias de Usuario

| HU | Nombre | Estado | Pantalla |
|---|---|---|---|
| HU-01 | Auditoría Documental Obligatoria | ✅ Implementada | Facturas |
| HU-02 | Votaciones Vinculantes | ✅ Implementada | Votar |
| HU-03 | Pago de Alquiler y/o Expensas | ✅ Implementada (transferencia + Mercado Pago) | Más → Pagar / Vecinos / Panel de cobros |
| HU-04 | Integración con pasarela de pago (Mercado Pago) | ✅ Implementada (HU-03 y HU-10 Escenario 3) | Más → Pagar / Tickets |
| HU-05 | Muro con moderación previa | ✅ Implementada | Muro |
| HU-06 | Reserva de Espacios Comunes | ✅ Implementada | Más → Reservas |
| HU-07 | Protocolos de emergencia | ✅ Implementada (guía propia, no PDFs oficiales) | Más → Protocolos de emergencia |
| HU-08 | Directorio de Proveedores | ✅ Implementada | Más → Directorio de proveedores |
| HU-09 | Configuración de Cobros y Vencimientos | ✅ Implementada | Más → Configuración del edificio |
| HU-10 | Gestión de Incidentes de Mantenimiento | ✅ Implementada (pago manual + Mercado Pago) | Más → Tickets |

## Segunda plataforma: web para Administradores (`web/`)

No es una HU numerada del TP original (esa numeración es sólo de la app Android) —
es la segunda plataforma que el planteo original mencionaba junto con Android, y que
había quedado explícitamente fuera de alcance hasta terminar las 10 HU.

| Alcance | Estado | Pantalla |
|---|---|---|
| v1 — Liquidación de expensas por alícuota | ✅ Implementada | `/`, `/unidades`, `/gastos`, `/liquidacion` |

Detalle completo en
[`docs/superpowers/specs/2026-10-23-web-admin-liquidacion-design.md`](./superpowers/specs/2026-10-23-web-admin-liquidacion-design.md)
y la sección 8 de [`arquitectura.md`](./arquitectura.md#8-web-para-administradores-web).
Deliberadamente fuera de esta v1 (ver "No incluye" del diseño): Encargado/Inquilino en
la web, gestión de alquiler, aprobar/rechazar comprobantes desde la web, exportar a
PDF/Excel, cualquier pantalla que no sea expensas (tickets, muro, votaciones, etc.).

## Próximos pasos (orden sugerido)

Las 10 Historias de Usuario del TP ya están implementadas (incluida la mejora que
había quedado pendiente, Mercado Pago para reparaciones), y también la v1 de la
segunda plataforma (web para Administradores, liquidación de expensas). No queda
ningún ítem priorizado — lo que sigue es el backlog de abajo, una eventual v2 de la
web (Encargado/Inquilino, exportar, aprobar cobros), y lo que el usuario deje para el
final (canal WhatsApp/PWA, una integración de IA gratuita).

✅ **HU-04 — Mercado Pago para reparaciones (HU-10 Escenario 3)** resuelta:
`createTicketMercadoPagoPreference` (callable nueva) genera la preferencia para quien
reportó el ticket; `mercadoPagoWebhook` (ya existía para HU-03) ahora rutea por el
prefijo de `external_reference` entre `resolveRentPayment` y la `resolveTicketPayment`
nueva. A diferencia del flujo de alquiler/expensas, acá no hace falta escribir ningún
estado especulativo al generar la preferencia (el ticket ya existe en `A_COBRAR`), así
que cerrar el checkout sin pagar y reintentar es seguro sin la lógica de transacción
que hizo falta para HU-03. El Admin/Encargado sigue con el pago manual de siempre
(`markPaid`) — no se le agregó el botón de Mercado Pago, sólo al inquilino que
reportó el ticket. Probado a mano en el emulador (mock): Encargado confirma la
compra (pasa a A_COBRAR), no ve el botón de Mercado Pago; el inquilino que reportó sí
lo ve, lo usa, y el ticket pasa a "Resuelto" mostrando "Pagado por Mercado Pago." Las
Cloud Functions, como en HU-04 original, sólo se validaron con `node --check` +
`require` local — sin proyecto de Firebase real ni credenciales de Mercado Pago en
este entorno. Ver `docs/superpowers/plans/2026-10-22-mercado-pago-tickets.md`.

✅ **HU-07 — Protocolos de emergencia** resuelta: `ProtocolosActivity` (listado) +
`ProtocolDetailActivity` (pasos), con contenido fijo en `EmergencyProtocols.kt`
(incendio, sismo, corte de luz, inundación) más teléfonos de emergencia nacionales
(Bomberos, SAME, Policía, Defensa Civil). **No son los PDFs oficiales** de Defensa
Civil GCBA/SAME que pedía originalmente la historia — no hay forma de descargarlos y
verificarlos desde este entorno, y el contenido de un protocolo de emergencia no es
algo para inventar. Decisión tomada con el usuario: en su lugar, una guía general
redactada a partir de lineamientos de seguridad reconocidos, con el disclaimer
explícito en la pantalla de que no reemplaza las instrucciones oficiales. Sin
Repository/Firestore porque es contenido 100% estático, bundleado con la app (funciona
sin conexión por definición, no por caché). Probado a mano en el emulador (mock):
listado, detalle de un protocolo, volver.

✅ **Pantalla de alta/invitación de vecinos** resuelta: en Registrarse hay un campo
"Código del edificio" opcional (el id del edificio, sin un código corto propio), y
`BuildingConfigActivity` se lo muestra al Administrador con un botón de copiar para
pasárselo a cada vecino. `AuthRepository.register` valida que el código corresponda a
un edificio existente (y si no, deja al usuario sin edificio asignado, igual que
antes). La pantalla "Vecinos" de HU-03 sigue siendo sólo para asignar montos, no para
el alta en sí.

✅ **Notificaciones de tickets** resuelta: `functions/index.js` suma
`onTicketCreated` (avisa a `building_{buildingId}_staff`, tópico sólo para
Encargado/Administrador) y `onTicketStatusChanged` (avisa a `user_{uid}`, tópico
personal del inquilino que reportó el ticket) cuando cambia `status`. La app se
suscribe a ambos tópicos (además del general `building_{buildingId}`) en
`MainActivity.onResume`, según el rol del usuario, y se desuscribe en el logout de
`MasFragment`. Sin un proyecto de Firebase real no hay forma de desplegar ni probar
estas dos funciones nuevas — sólo se validó que `index.js` tiene sintaxis válida y que
sus imports resuelven (`node --check` + `require` local).

✅ **Suscripción a tópicos FCM** (`building_{buildingId}`) resuelta: `MainActivity`
suscribe al tópico del edificio del usuario en cada `onResume` (así cubre también el
caso de recién crear el edificio), pide el permiso `POST_NOTIFICATIONS` en tiempo de
ejecución (API 33+), y `MasFragment` desuscribe antes del logout. El flavor mock usa un
`MockNotificationRepository` sin operación — no hay forma de probar la llegada real de
la notificación sin un proyecto de Firebase real.

✅ **HU-08 — Directorio de Proveedores** resuelta: pantalla de listado (`ProveedoresActivity`),
ficha con promedio de estrellas y reseñas (`ProviderDetailActivity`) y alta/edición
(`AddEditProviderActivity`, sólo Administrador). El promedio se calcula en el momento a
partir de las reseñas (`ProviderDetailViewModel`), no se denormaliza en
`Provider.averageRating`/`ratingCount` — ver el comentario de `ProviderRepository` por
qué. Probado a mano en el emulador (mock): alta, edición, borrado, y dejar una reseña
como Admin e Inquilino.

✅ **HU-04 — Mercado Pago (sólo HU-03)** resuelta: `createMercadoPagoPreference`
(callable) calcula el monto server-side y genera la preferencia de Checkout Pro;
`mercadoPagoWebhook` (HTTP pública, primera del proyecto) valida la firma con el
validador del propio SDK `mercadopago` y marca el pago `PAGADO` sin intervención del
Administrador. El cliente abre la URL de checkout en una Custom Tab
(`androidx.browser`), sin SDK nativo de pagos. El flavor mock marca el pago `PAGADO`
al instante (sin red) para poder demostrar el flujo sin cuenta real. Sin un proyecto de
Firebase real ni credenciales de Mercado Pago en este entorno, las dos Cloud Functions
nuevas sólo se validaron con `node --check` + `require` local — el webhook en sí
(firma inválida, `external_reference` desconocida) se verificó leyendo el código, no
en runtime. Ver `docs/superpowers/plans/2026-10-21-mercado-pago.md` para el detalle.

## Backlog / ideas a más largo plazo

Ver la sección "Sugerencias para versiones futuras" en
[`especificacion-funcional.md`](./especificacion-funcional.md#6-sugerencias-para-versiones-futuras)
para el detalle de cada una (rendición de cuentas descargable, fondo de reserva, libro
de actas digital, prorrateo por alícuota, estado de cuenta por unidad, etc.).

Más allá de eso, y ya fuera del alcance del MVP Android:

- **Web app** para Administradores (mencionada en el planteo original del TP como
  segunda plataforma, junto con Android).
- **iOS** — explícitamente pospuesto hasta después de validar el MVP Android.
- Tests automatizados (hoy el proyecto no tiene tests de UI ni de `Repository`; el
  `androidTest`/`test` del template de Android Studio sigue vacío).
