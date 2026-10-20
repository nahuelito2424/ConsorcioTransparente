# Consorcio Transparente — App Android

MVP nativo (Kotlin + Firebase) de la app descripta en el TP de Planificación de
Seminario Final. Cubre, en esta entrega, el flujo de autenticación y seis
historias de punta a punta:

- **HU-01 (Auditoría Documental Obligatoria):** un gasto sin foto de comprobante
  no puede guardarse como verificado, ni en la app ni en las reglas de Firestore.
- **HU-02 (Votaciones Vinculantes):** el Administrador crea una votación con 2+
  opciones y fecha de cierre; cada vecino vota una única vez (garantizado por
  reglas de seguridad, no sólo por la UI); los resultados se ven apenas cierra
  o apenas votaste. El cierre automático + notificación push corren en una
  Cloud Function (`functions/index.js`), no en el cliente.
- **HU-05 (Muro con moderación previa):** cualquier vecino puede proponer una
  publicación (con imagen opcional), pero nace en estado Pendiente y no aparece
  en el muro hasta que el Administrador la aprueba desde la cola de moderación
  — reforzado también en `firestore.rules`, no sólo escondiendo el botón en la UI.
- **HU-06 (Reserva de Espacios Comunes):** franjas horarias por día para cada
  amenity del edificio (SUM, parrilla, pileta), con edición y cancelación de la
  propia reserva respetando el margen de 24hs. El doble booking no se previene
  sólo en el cliente: el id del documento es `{espacio}_{fecha}_{franja}`, así
  que un segundo intento de tomar la misma franja choca contra la regla de
  seguridad (ver comentario en `ReservationRepository.kt`).
- **HU-09 (Configuración de Cobros y Vencimientos):** el Administrador define si
  el edificio cobra alquiler, expensas o ambos, sus días de vencimiento y la
  anticipación del recordatorio. Esta misma pantalla resuelve además el alta de
  edificio: el primer usuario que crea uno se convierte automáticamente en su
  Administrador (regla de "bootstrap" en `firestore.rules`). El envío diario del
  recordatorio corre en una Cloud Function (`sendDueDateReminders`).
- **HU-10 (Gestión de Incidentes de Mantenimiento):** ciclo completo del ticket
  — Reportado (inquilino, con foto opcional) → En Presupuesto (Encargado/Admin
  cargan el monto, o lo dejan "a cotizar") → A Cobrar (se sube el comprobante de
  compra) → Resuelto (se adjunta el comprobante de pago y queda archivado). El
  Escenario 3 de la historia pide cobrar por Mercado Pago; esa parte es HU-04 y
  todavía no está — hoy "A Cobrar" se salda subiendo un comprobante de pago
  manual, dejado como un punto de reemplazo único y documentado en
  `TicketRepository.kt` para cuando se integre la pasarela de verdad.

## Documentación

Este README es la guía de compilación y de uso. La documentación funcional y técnica
completa vive en [`docs/`](./docs/):

- [`docs/especificacion-funcional.md`](./docs/especificacion-funcional.md) — catálogo
  completo de las 10 Historias de Usuario, roles, reglas de negocio, marco legal de
  CABA y sugerencias para versiones futuras.
- [`docs/arquitectura.md`](./docs/arquitectura.md) — stack, capas, modelo de datos de
  Firestore, seguridad y convenciones.
- [`docs/roadmap.md`](./docs/roadmap.md) — estado de avance y próximos pasos.
- [`CLAUDE.md`](./CLAUDE.md) — contexto resumido para retomar el proyecto con Claude Code.

## Qué está implementado

| Pantalla | Historia de usuario | Estado |
|---|---|---|
| Login / Registro | — | Funcional (Firebase Auth) |
| Inicio (Dashboard) | — | Funcional (lee edificio + contadores desde Firestore) |
| Facturas | HU-01 Auditoría Documental Obligatoria | Funcional (listar, cargar gasto con foto obligatoria) |
| Votar | HU-02 Votaciones Vinculantes | Funcional (votar, ver resultados, crear votación si sos Administrador) |
| Muro | HU-05 Muro con moderación previa | Funcional (publicar, moderar, ver el muro aprobado) |
| Más → Configuración del edificio | HU-09 Cobros y Vencimientos | Funcional (crear edificio, o editarlo si sos Administrador) |
| Más → Reservas | HU-06 Reserva de Espacios Comunes | Funcional (ver franjas, reservar, editar, cancelar) |
| Más → Tickets de mantenimiento | HU-10 Gestión de Incidentes | Funcional (reportar, presupuestar, comprobante de compra, comprobante de pago manual) |
| Más → Proveedores, Protocolos | HU-08, HU-07 | Placeholder + botón de cerrar sesión funcional |

El resto de las historias (HU-03, HU-04) tienen sus modelos de datos en
`data/model/` pero todavía no tienen pantalla — son el siguiente paso. HU-04
(Mercado Pago) es además la pieza que falta para que el pago del ticket en
HU-10 sea automático en vez de un comprobante manual.

## Probar la app sin crear un proyecto Firebase (flavor mock)

Para instalar la app y navegarla sin pasar por el paso de Firebase de abajo, compilá el
flavor **mock** en vez de **prod**:

```bash
./gradlew installMockDebug
```

Se loguea con una de estas 3 credenciales fijas (cualquier contraseña, no se valida),
una por rol:

| Email | Rol |
|---|---|
| `admin@mock.com` | Administrador |
| `encargado@mock.com` | Encargado |
| `inquilino@mock.com` | Inquilino |

Todos comparten un mismo edificio de ejemplo con datos precargados (gastos, votación,
publicaciones, ticket, reserva). Los tres roles no hablan con Firebase para nada — los
datos viven en memoria mientras la app esté abierta (se reinician al matar el proceso).
Ver `RepositoryProvider` y `data/repository/mock/` para el detalle.

El flavor **prod** (el de siempre) sí necesita el proyecto Firebase real de la sección
siguiente.

## Antes de compilar: crear el proyecto de Firebase

El archivo `app/google-services.json` que está en el repo es una **plantilla con
valores falsos** (a propósito, para no romper el build antes de tiempo). Sin
reemplazarlo, la app compila pero ningún llamado a Firebase va a funcionar.

1. Andá a [console.firebase.google.com](https://console.firebase.google.com) y creá un proyecto nuevo (ej. "consorcio-transparente").
2. Agregá una app Android con el paquete **`com.consorciotransparente.app`**.
3. Descargá el `google-services.json` real que te da el asistente y reemplazá el de `app/google-services.json`.
4. En el menú de la consola, habilitá:
   - **Authentication** → método "Correo electrónico/contraseña".
   - **Firestore Database** → crear base de datos (modo producción).
   - **Storage** → crear bucket (modo producción).
   - **Cloud Functions** → requiere pasar el proyecto al plan Blaze (pago por uso; el nivel gratuito de invocaciones alcanza de sobra para un TP). Si preferís no activarlo todavía, la app funciona igual — sólo no vas a tener el cierre automático de votaciones ni los recordatorios de vencimiento hasta que despliegues las functions.
5. Subí las reglas y (opcionalmente) las functions con el Firebase CLI:
   ```bash
   npm install -g firebase-tools
   firebase login
   firebase use --add   # elegí el proyecto que creaste en el paso 1
   firebase deploy --only firestore:rules,firestore:indexes,storage
   firebase deploy --only functions   # sólo si activaste el plan Blaze
   ```

## Primer uso (ya no hace falta tocar la consola de Firestore a mano)

1. Registrate en la app (arrancás con rol INQUILINO y sin edificio asignado).
2. Andá a **Más → Configuración del edificio** — como todavía no tenés uno, la
   pantalla te deja crearlo (nombre, dirección, qué cobra, vencimientos,
   espacios comunes). Al guardar, pasás a ser automáticamente el Administrador
   de ese edificio.
3. Con eso ya podés usar Facturas, crear votaciones, moderar el Muro y reservar
   espacios comunes. Para probar como si fueras un vecino común (no
   Administrador), registrá una segunda cuenta y actualizale a mano el campo
   `buildingId` en Firestore (todavía no hay una pantalla de "invitar vecino").

## Compilar

```bash
./gradlew assembleProdDebug   # contra Firebase real
./gradlew assembleMockDebug   # sin Firebase, ver sección de arriba
```

o abrí la carpeta directamente en Android Studio (File → Open), elegí la variante
(`prodDebug`/`mockDebug`) en el panel **Build Variants** y corré la app con el botón ▶
sobre un emulador o un dispositivo conectado.

`gradle.properties` apunta `org.gradle.java.home` al JBR de Android Studio
instalado en esta máquina (AGP 8.11 necesita Java 17+). Si compilás desde otra
PC y falla por versión de Java, borrá o ajustá esa línea.

## Estructura del proyecto

```
app/src/main/java/com/consorciotransparente/app/
  data/model/        modelos Firestore de las 10 historias de usuario
  data/repository/   acceso a Firebase Auth / Firestore / Storage
  ui/auth/           login y registro
  ui/dashboard/      pantalla Inicio
  ui/facturas/       HU-01: listado + alta de gasto con foto obligatoria
  ui/votaciones/     HU-02: listar, votar, ver resultados, crear votación (admin)
  ui/muro/           HU-05: publicar, muro aprobado, cola de moderación (admin)
  ui/reservas/       HU-06: franjas horarias, reservar, editar, cancelar (24hs)
  ui/building/       HU-09: alta de edificio y configuración de cobros/vencimientos
  ui/tickets/        HU-10: reportar, presupuestar, comprobante de compra y de pago
  ui/mas/            menú "Más" + cerrar sesión
firestore.rules       reglas de seguridad (auditoría, un voto por usuario, moderación previa, bootstrap de edificio)
firestore.indexes.json índice compuesto que necesita la Cloud Function de cierre de votaciones
storage.rules          reglas de Storage para fotos de facturas, tickets y publicaciones
functions/index.js     Cloud Functions programadas: cierre de votaciones y recordatorio de vencimientos
```

## Próximos pasos sugeridos

1. Suscribir a cada usuario al tópico FCM `building_{buildingId}` al iniciar sesión, para que las notificaciones que ya mandan las Cloud Functions (cierre de votación, vencimientos) le lleguen a alguien. Lo mismo serviría para avisar cuando un ticket cambia de estado.
2. Pantalla para que el Administrador invite/dé de alta vecinos y unidades (hoy sólo él se auto-asigna al crear el edificio).
3. HU-04: integración real con el SDK de Mercado Pago, tanto para alquiler/expensas como para reemplazar el comprobante de pago manual de HU-10 por un cobro automático.
4. Notificar al Encargado/Administrador cuando se reporta un ticket nuevo, y al inquilino cuando cambia de estado (hoy hay que entrar a la app para enterarse).
5. HU-08: directorio de proveedores con ficha, categorías y calificación (el modelo `Provider` ya está listo, y HU-10 ya deja el `expenseId`/ticket como referencia para vincular la reseña a un trabajo concreto).
