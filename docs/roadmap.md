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
| HU-04 | Integración con pasarela de pago (Mercado Pago) | ✅ Implementada (sólo HU-03; HU-10 Escenario 3 sigue con pago manual) | Más → Pagar |
| HU-05 | Muro con moderación previa | ✅ Implementada | Muro |
| HU-06 | Reserva de Espacios Comunes | ✅ Implementada | Más → Reservas |
| HU-07 | Protocolos de emergencia (PDF) | 🕗 Pendiente — "Próximamente" | Más → Protocolos (stub) |
| HU-08 | Directorio de Proveedores | ✅ Implementada | Más → Directorio de proveedores |
| HU-09 | Configuración de Cobros y Vencimientos | ✅ Implementada | Más → Configuración del edificio |
| HU-10 | Gestión de Incidentes de Mantenimiento | ✅ Implementada (cobro de reparaciones con pago manual; todavía no usa Mercado Pago) | Más → Tickets |

## Próximos pasos (orden sugerido)

1. **HU-07 — protocolos en PDF**, apoyándose en la caché offline de Firestore que ya
   está habilitada (`ConsorcioApp.kt`) para que funcionen sin conexión.
2. **Reemplazar el pago manual de HU-10** (`markPaid` en `TicketRepository.kt`) por el
   cobro real de reparaciones vía Mercado Pago, reusando la misma integración de HU-04
   (Cloud Function + webhook) ya hecha para HU-03.

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
