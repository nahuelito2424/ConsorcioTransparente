# Arquitectura técnica — Consorcio Transparente

> Documentación técnica de cómo está construido el MVP. Para qué hace cada pantalla y
> por qué, ver [`especificacion-funcional.md`](./especificacion-funcional.md). Para qué
> falta, ver [`roadmap.md`](./roadmap.md).

## 1. Stack

- **Cliente**: Android nativo, Kotlin, Views + `ViewBinding` (sin Compose ni data
  binding), `Navigation Component` para el bottom nav, `LiveData` + `ViewModel`
  (`androidx.lifecycle`), corrutinas de Kotlin (`kotlinx-coroutines-play-services` para
  adaptar las `Task` de Firebase con `.await()`).
- **Backend**: Firebase — Authentication (email/contraseña), Firestore (base de datos
  principal), Storage (fotos de comprobantes/tickets/publicaciones), Cloud Messaging
  (push), Cloud Functions Gen 2 (Node.js, `functions/index.js`) para lógica programada
  que no puede depender de que un cliente tenga la app abierta.
- **Imágenes**: Glide.
- **`applicationId` / `namespace`**: `com.consorciotransparente.app`.
- **SDK**: `minSdk 24`, `compileSdk`/`targetSdk 36`, AGP 8.11, Kotlin 2.0.21.
- **Product flavors**: `prod` (Firebase real) y `mock` (datos en memoria, sin Firebase;
  ver sección 2 y el README para cómo instalarlo). Mismo `applicationId` en los dos.

## 2. Patrón de capas

```
ui/<feature>/                Activity o Fragment (View) + ViewModel + (a veces) Adapter
data/repository/             una interfaz por feature (contrato) + su Firebase*Repository
data/repository/mock/        Mock*Repository: misma interfaz, datos en memoria (flavor "mock")
data/model/                  data classes que mapean 1:1 con documentos de Firestore
util/Resource.kt             sealed class Loading/Success/Error para exponer estado async a la UI
```

No hay capa de dominio ni inyección de dependencias (Hilt/Koin). Cada `XRepository` es
una interfaz chica con dos implementaciones: `FirebaseXRepository` (habla con Firebase,
la de siempre) y `MockXRepository` (datos en memoria, para poder probar la app sin un
proyecto Firebase real). `RepositoryProvider` (`data/repository/RepositoryProvider.kt`)
es un objeto con un val perezoso por repo que elige cuál instanciar según
`BuildConfig.USE_MOCK_DATA` — un `product flavor` (`mock`/`prod`, ver
`app/build.gradle.kts`), no un contenedor de DI. Cada `ViewModel` sigue tomando el
repo como default value en su constructor, ahora resuelto vía `RepositoryProvider`
en vez de instanciado directo:

```kotlin
class FooViewModel(private val repo: FooRepository = RepositoryProvider.foo) : ViewModel()
```

Es una decisión consciente de alcance de MVP/tesis, no un olvido — mantenerla así
salvo que el usuario pida explícitamente introducir DI (Hilt/Koin) o una capa de
dominio de verdad.

Cada pantalla sigue el mismo esqueleto: el `ViewModel` expone `LiveData<Resource<T>>`,
la `Activity`/`Fragment` observa y resuelve `Loading` (spinner) / `Success` (pinta datos)
/ `Error` (mensaje). Ver `util/Resource.kt`.

### 2.1 Modo simple (accesibilidad)

Todas las `Activity` heredan de `ui/BaseActivity` (nunca de `AppCompatActivity`
directo). `BaseActivity` lee `util/SimpleModePrefs` (un booleano en
`SharedPreferences`, por dispositivo, prendido/apagado desde un switch en Más) y, si
está prendido:

- Aplica `fontScale = 1.3` vía `Configuration` en `attachBaseContext` — el mecanismo
  nativo de Android para escalar texto, no hay que tocar cada layout.
- Cambia el tema a `Theme.ConsorcioTransparente.Simple` (`setTheme()` antes de
  `super.onCreate()`), que redefine el atributo `?attr/colorTextSecondary` igual a
  `text_primary` (negro pleno) en vez del gris de siempre.

Por eso los layouts usan `android:textColor="?attr/colorTextSecondary"` para texto
secundario, nunca `@color/text_secondary` directo — si un layout nuevo necesita texto
secundario, seguir ese mismo atributo. `DashboardFragment` además switchea entre el
bloque de métricas de siempre (`normalModeContent`) y 3 botones grandes
(`simpleModeContent`) leyendo el mismo flag.

## 3. Modelo de datos (Firestore)

```
users/{uid}                                   perfil (rol, buildingId, unitId)
buildings/{buildingId}                         config del edificio (HU-09)
buildings/{buildingId}/units/{unitId}          unidad funcional (alícuota, owner/tenant uid) — web Admin, sección 8
buildings/{buildingId}/liquidaciones/{period}  liquidación de expensas emitida — web Admin, sección 8
buildings/{buildingId}/expenses/{expenseId}    gastos, HU-01
buildings/{buildingId}/votes/{voteId}          votaciones, HU-02
buildings/{buildingId}/votes/{voteId}/responses/{voterUid}   un voto por usuario (id = uid)
buildings/{buildingId}/posts/{postId}          muro, HU-05
buildings/{buildingId}/reservations/{id}       reservas, HU-06; id = "{espacio}_{fecha}_{franja}"
buildings/{buildingId}/tickets/{ticketId}      mantenimiento, HU-10
providers/{providerId}                         directorio global (no por edificio), HU-08
providers/{providerId}/reviews/{reviewId}      reseñas de proveedores
```

Dos trucos de modelado vale la pena recordar porque son la base de invariantes de
negocio (no son solo "cómo se guardan los datos"):

- **Un voto por usuario** (HU-02): el id del documento en `responses` es el propio
  `uid` del votante, así un segundo `create` sobre el mismo id es rechazado por
  Firestore/las reglas sin necesitar una transacción ni una consulta previa.
- **Sin doble reserva** (HU-06): el id del documento de reserva es la clave compuesta
  `{espacio}_{fecha}_{franja}`. Tomar una franja ya reservada es, literalmente, intentar
  crear un documento que ya existe.

### Bootstrap de Administrador

No hay pantalla de "alta de edificio por un superadmin": el primer usuario que
configura un edificio (pantalla HU-09, `BuildingConfigActivity`) se auto-promueve a
Administrador de ese edificio en el mismo flujo (`BuildingRepository.createBuilding`).
La regla de seguridad en `firestore.rules` (`match /users/{uid}`) permite ese único
caso de auto-cambio de rol: un `INQUILINO` sin `buildingId` puede pasar a
`ADMINISTRADOR` únicamente al mismo tiempo que se le asigna el `buildingId` de un
edificio recién creado por él. Cualquier otro cambio de rol requiere que lo haga un
Administrador existente.

## 4. Seguridad

Regla de oro del proyecto: **ninguna invariante de negocio con consecuencias reales se
apoya solo en la UI**. Todo lo que importa (comprobante obligatorio, un voto por
persona, moderación previa del muro, no doble reserva, quién puede cambiar el estado de
un ticket) está reforzado en `firestore.rules`. Al tocar una pantalla que valida algo
"importante", revisar si `firestore.rules` necesita el mismo cambio — agregar la
validación solo en Kotlin no alcanza.

`storage.rules` protege la subida de imágenes (comprobantes, fotos de tickets/posts) de
forma análoga, scopeada por `buildingId`.

## 5. Cloud Functions (`functions/index.js`)

Firebase Functions Gen 2 (Node.js). Dos programadas (`onSchedule`), dos disparadas por
Firestore (`onDocumentCreated`/`onDocumentUpdated`), dos callable (`onCall`) y una HTTP
pública (`onRequest`):

- `closeExpiredVotes` — corre cada 15 minutos, cierra votaciones vencidas
  (`isClosed = true`), tabula `finalCounts` y notifica por FCM al tópico
  `building_{buildingId}`.
- `sendDueDateReminders` — corre una vez por día (09:00), compara el día del mes actual
  contra `rentDueDay`/`expensesDueDay` de cada edificio y notifica cuando falten
  exactamente `reminderDaysBefore` días. Simplificación consciente: compara solo el
  número de día del mes, sin ajustar meses de 28-31 días (ver comentario en el archivo).
- `onTicketCreated` — notifica al tópico `building_{buildingId}_staff` (sólo
  Encargado/Administrador) cuando se reporta un ticket nuevo (HU-10).
- `onTicketStatusChanged` — notifica al tópico `user_{uid}` (el inquilino que lo
  reportó) cuando cambia el `status` de su ticket.
- `createMercadoPagoPreference` (HU-04, callable) — el cliente Android la invoca para
  pagar alquiler/expensas con Mercado Pago. Calcula el monto server-side (nunca confía
  en un monto mandado por el cliente), crea el `Payment` (`method: MERCADO_PAGO`,
  `status: A_VERIFICAR`) y devuelve la URL de Checkout Pro. `external_reference =
  "{buildingId}_{uid}_{period}"`.
- `createTicketMercadoPagoPreference` (HU-10 Escenario 3, callable) — mismo propósito
  que la anterior pero para que el inquilino que reportó un ticket pague el costo de
  la reparación; sólo él puede invocarla para ese ticket (`reportedByUid ==
  request.auth.uid`), y sólo si está en `A_COBRAR`. A diferencia de
  `createMercadoPagoPreference`, no escribe nada en Firestore al generar la
  preferencia — el `Ticket` ya existe, así que no hay estado especulativo que crear ni
  limpiar si se cierra el checkout sin pagar. `external_reference =
  "ticket_{buildingId}_{ticketId}"` (el prefijo `ticket_` es lo que usa el webhook para
  rutear).
- `mercadoPagoWebhook` (HU-04/HU-10, HTTP pública) — recibe la confirmación de pago de
  Mercado Pago, valida su firma (`WebhookSignatureValidator` del SDK `mercadopago`) y
  rutea por el prefijo de `external_reference` hacia `resolveRentPayment` (marca el
  `Payment` `PAGADO`) o `resolveTicketPayment` (marca el `Ticket` `RESUELTO`). Es la
  primera función HTTP pública del proyecto; requiere los secrets
  `MERCADO_PAGO_ACCESS_TOKEN` y `MERCADO_PAGO_WEBHOOK_SECRET` (ver README).

Los clientes Android se suscriben a los tópicos de notificación en
`MainActivity.onResume` (ver `NotificationRepository`), según buildingId/rol/uid del
usuario, y se desuscriben en el logout (`MasFragment`).

Requieren plan Blaze en el proyecto de Firebase para desplegarse; el resto de la app
funciona sin ellas (sin cierre automático de votación, recordatorio de vencimiento,
notificación de tickets, ni pago por Mercado Pago — queda sólo transferencia).

## 6. Setup de un proyecto de Firebase nuevo

Ver los pasos detallados en el [`README.md`](../README.md#antes-de-compilar-crear-el-proyecto-de-firebase)
de la raíz — no se duplican acá para no tener dos fuentes desactualizables por
separado. Resumen: crear proyecto en la consola, agregar app Android con el paquete
`com.consorciotransparente.app`, reemplazar el `google-services.json` de plantilla,
habilitar Auth/Firestore/Storage (y opcionalmente Functions con plan Blaze), desplegar
reglas e índices con el Firebase CLI.

## 7. Convenciones

- **Idioma**: comentarios de código, mensajes de UI y strings en **español**
  (rioplatense/Argentina); nombres de clases, variables y funciones en inglés siguiendo
  convención Kotlin estándar.
- **Comentarios**: cada modelo/repositorio lleva un comentario corto que dice **qué
  Historia de Usuario implementa y por qué está modelado así** (no qué hace la
  operación, eso ya lo dice el nombre). Mantener ese estilo al agregar código nuevo.
- **Un Repository por feature**, sin lógica de negocio compartida entre repositorios;
  cada uno recibe sus dependencias de Firebase con valor por defecto en el constructor.
- **`Resource<T>`** es el único envoltorio de estado async — no introducir uno nuevo
  (ni Flow/StateFlow) sin que lo pida el usuario explícitamente.

## 8. Web para Administradores (`web/`)

Segunda plataforma del TP (mencionada en el planteo original junto con Android), sobre
el mismo proyecto de Firebase — comparte Auth/Firestore/Storage con la app, sin backend
propio. Diseño completo:
[`docs/superpowers/specs/2026-10-23-web-admin-liquidacion-design.md`](./superpowers/specs/2026-10-23-web-admin-liquidacion-design.md).

- **Stack**: React 19 + TypeScript + Vite, Tailwind CSS v4 (plugin oficial de Vite,
  `@theme` en CSS — no `tailwind.config.js`, esa es la API de v3), `react-router-dom`,
  Firebase JS SDK. Sin librería de componentes (Material/Bootstrap/etc.) a propósito:
  son las que hacen que este tipo de producto se vea siempre igual.
- **Sin capa Repository/Mock**: a diferencia de Android (3 roles, pensado para
  convivir con un flavor sin red), acá hay un solo rol y un solo flujo de datos — cada
  pantalla usa un hook (`useUnits`, `useExpenses`, etc. en `web/src/hooks/`) que habla
  directo al SDK de Firebase. Portar el patrón de Android sería ingeniería de más.
- **Sólo rol `ADMINISTRADOR`**: `useAuth()` (`web/src/auth/`) desloguea de inmediato a
  cualquier otro perfil. Encargado/Inquilino quedan para una etapa futura.
- **Alcance v1**: liquidación de expensas por alícuota. Activa `buildings/{id}/units`
  (el modelo existía desde HU-03 pero no lo usaba nadie) y agrega
  `buildings/{id}/liquidaciones/{period}` (historial, snapshot inmutable por período —
  re-emitir el mismo período sobrescribe, sin versiones). El cálculo en sí
  (`web/src/lib/liquidacion.ts`) es una función pura: `amount = total × alícuota / 100`,
  redondeado a 2 decimales.
- **Coordinación con Android, sin tocarlo**: al "Emitir", la web escribe
  `users/{uid}.expensesAmount` — el mismo campo que ya lee
  `createMercadoPagoPreference`/`uploadReceipt` del lado Android (ver sección 5). Cero
  cambios en el modelo de pagos de Android. Único cambio Android de esta v1:
  `VecinosActivity` muestra un aviso de sólo lectura cuando un monto viene de una
  liquidación (`BuildingRepository.getLatestLiquidacionTenants`), sin bloquear la
  edición manual — las dos vías siguen escribiendo el mismo campo, sin coordinarse
  entre sí más que con ese aviso.
- **`firestore.rules`**: `units` y `liquidaciones` siguen el mismo patrón que el resto
  (`allow read: if belongsToBuilding(buildingId)`, `allow write: if isAdmin() &&
  myBuildingId() == buildingId`) — nada nuevo conceptualmente, reusa las funciones ya
  definidas al tope del archivo.
- **Desarrollo sin proyecto de Firebase real**: Firebase Local Emulator Suite
  (Auth+Firestore+Storage) con un script de semilla
  (`web/scripts/seed-emulator.ts`, `firebase-admin`) — ver `web/README.md`. Requiere
  Java en el PATH para los emuladores (en esta máquina, ninguno de los `java` del PATH
  por defecto funciona; usar el JBR de Android Studio,
  `C:\Program Files\Android\Android Studio\jbr\bin`).
- **Tests**: a diferencia de Android, acá sí hay — `web/src/lib/liquidacion.test.ts`
  (Vitest, función pura) y `web/tests/firestore-rules.test.ts`
  (`@firebase/rules-unit-testing` contra el emulador real, no sólo lectura del archivo
  de reglas).
