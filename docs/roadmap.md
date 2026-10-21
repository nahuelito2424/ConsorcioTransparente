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
| HU-03 | Pago de Alquiler y/o Expensas | 🟡 Parcial (transferencia + comprobante; falta Mercado Pago) | Más → Pagar / Vecinos / Panel de cobros |
| HU-04 | Integración con pasarela de pago (Mercado Pago) | 🕗 Pendiente | — |
| HU-05 | Muro con moderación previa | ✅ Implementada | Muro |
| HU-06 | Reserva de Espacios Comunes | ✅ Implementada | Más → Reservas |
| HU-07 | Protocolos de emergencia (PDF) | 🕗 Pendiente — "Próximamente" | Más → Protocolos (stub) |
| HU-08 | Directorio de Proveedores | 🟡 Modelo listo, falta UI | Más → Proveedores (stub) |
| HU-09 | Configuración de Cobros y Vencimientos | ✅ Implementada | Más → Configuración del edificio |
| HU-10 | Gestión de Incidentes de Mantenimiento | ✅ Implementada (pago manual, ver HU-04) | Más → Tickets |

## Próximos pasos (orden sugerido)

1. **Suscripción a tópicos FCM** (`building_{buildingId}`) al iniciar sesión. Las Cloud
   Functions (`closeExpiredVotes`, `sendDueDateReminders`) ya envían notificaciones a
   ese tópico, pero como ningún cliente Android se suscribe todavía, hoy no le llegan a
   nadie. Es la pieza más barata de resolver con más impacto (desbloquea dos historias
   ya "implementadas" del lado función).
2. **Pantalla de alta/invitación de vecinos y unidades.** Hoy solo el creador del
   edificio se auto-asigna como Administrador; para agregar un segundo usuario a un
   edificio hay que editarle el campo `buildingId` a mano en Firestore. (La pantalla
   "Vecinos" nueva de HU-03 solo le asigna un monto a quien ya tiene `buildingId` — no
   reemplaza esta invitación.)
3. **HU-04 — SDK/Checkout de Mercado Pago**: completa HU-03 (botón de pago dinámico vía
   Cloud Function + webhook, ver `docs/superpowers/specs/2026-10-20-pago-expensas-design.md`)
   y, después, reemplaza el comprobante de pago manual de HU-10 (`markPaid` en
   `TicketRepository.kt`) por la confirmación real del pago.
4. **Notificar al Encargado/Administrador cuando se reporta un ticket nuevo**, y al
   inquilino cuando cambia de estado — hoy hay que entrar a la app para enterarse.
5. **HU-08 — UI del directorio de proveedores**: listado, alta/edición/borrado
   (Administrador), ficha con reseñas y estrellas. El modelo (`Provider`,
   `ProviderReview`) y las reglas de Firestore ya están listos.
6. **HU-07 — protocolos en PDF**, apoyándose en la caché offline de Firestore que ya
   está habilitada (`ConsorcioApp.kt`) para que funcionen sin conexión.

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
