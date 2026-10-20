# Diseño: HU-03 (Pago de Alquiler/Expensas) + HU-04 (Mercado Pago)

> Spec de referencia: [`docs/especificacion-funcional.md`](../../especificacion-funcional.md#hu-03--pago-de-alquiler-y-o-expensas-pendiente).
> Arquitectura general del proyecto: [`docs/arquitectura.md`](../../arquitectura.md).

## Por qué

HU-03 y HU-04 son las dos historias de usuario pendientes más grandes del proyecto
(junto con HU-07/HU-08). Sin ellas, la app no cumple su propósito central de dejar
pagar alquiler/expensas de forma transparente y trazable. El usuario pidió retomar el
trabajo de optimización de la app basado en una investigación de mercado, priorizando
primero el "modo simple" de accesibilidad (ya implementado) y ahora este bloque de
pagos, que también habilita dos ideas de esa investigación: mostrarle al inquilino de
qué se compone lo que paga (desglose simple) y facilitarle pagar (copiar CBU/alias,
botón directo a Mercado Pago).

## Alcance

### Incluye

1. **Lista de vecinos** (Administrador): ver los inquilinos de su edificio y asignarle
   a cada uno un monto fijo de alquiler y/o expensas (según lo que cobre el edificio).
2. **Pantalla "Pagar"** (Inquilino): ver el total a pagar este mes (con desglose
   alquiler/expensas), su estado, y pagar por transferencia o Mercado Pago.
3. **Transferencia bancaria**: la app muestra el CBU/alias del edificio (configurado
   por el Administrador) con botón de copiar, y deja subir una foto del comprobante.
   El pago queda "a verificar" hasta que el Administrador lo aprueba o rechaza.
4. **Mercado Pago real (HU-04)**: botón que genera una preferencia de pago (Checkout
   Pro) con el monto exacto vía una Cloud Function nueva; al confirmarse el pago
   (webhook de Mercado Pago) el sistema lo marca "pagado" sin intervención humana.
5. **Comprobante dentro de la app**: al quedar pagado (por cualquier método), un
   registro con fecha, monto, desglose y método queda disponible como constancia e
   historial de meses anteriores. No es un PDF generado — ver "No incluye".
6. **Notificación de vencimiento próximo**: Cloud Function programada (mismo patrón
   que el cierre automático de votaciones) que avisa por push cuando faltan los días
   configurados en HU-09 (`reminderDaysBefore`) para el `dueDay`.
7. **Panel de cobros** (Administrador): de un vistazo, qué vecinos pagaron este mes y
   cuáles no, con acceso para aprobar/rechazar comprobantes pendientes.
8. `firestore.rules` para todo lo anterior (ver sección dedicada) y la implementación
   `Mock*` correspondiente en el flavor `mock`, siguiendo el patrón ya establecido
   (`RepositoryProvider`, `data/repository/mock/`).

### Períodos independientes (no se acumula deuda)

Cada mes (`period`) es un documento de pago independiente con su propio estado. Si un
inquilino no paga octubre, la pantalla "Pagar" en noviembre solo le muestra y le cobra
noviembre — no se suma la deuda vieja al monto a pagar. Lo de octubre sin pagar queda
visible en el historial del inquilino y en el panel de cobros del Administrador, pero
no bloquea ni infla el período actual. (Decisión tomada con el usuario tras la revisión
del diseño.)

### No incluye (deliberadamente, para no sobre-diseñar)

- **Alícuotas/unidades**: el modelo `UnitInfo` (alícuota, propietario, inquilino)
  existe pero no se usa. El monto lo tipea el Administrador a mano por vecino. Esto
  quedó decidido explícitamente con el usuario para no bloquear HU-03 detrás de una
  pantalla de gestión de unidades que no existe.
- **PDF real del comprobante**: eso es territorio de HU-07 (protocolos en PDF, que
  sigue pendiente). El "comprobante" de esta entrega es un registro dentro de la app,
  no un archivo descargable.
- **Recargos/intereses por mora**: no se pidió, no se agrega.
- **Invitación de vecinos por la app**: sigue sin existir (ya documentado así en el
  README); la lista de vecinos de este diseño solo muestra a los que ya tienen
  `buildingId` asignado (por ahora, a mano vía consola de Firestore).
- **Reconciliación retroactiva**: no se migran pagos de meses anteriores a la puesta
  en marcha de esta funcionalidad; el historial arranca desde que se despliega.

## Modelo de datos (Firestore)

```
users/{uid}
  + rentAmount: Double = 0.0       // lo asigna el Administrador, no el propio usuario
  + expensesAmount: Double = 0.0   // ídem

buildings/{buildingId}
  + cbu: String = ""
  + alias: String = ""
  // las credenciales de Mercado Pago NO viven en Firestore: se configuran como
  // variables de entorno de Cloud Functions (ver sección Cloud Functions)

buildings/{buildingId}/payments/{uid}_{period}   // period = "yyyy-MM", ej. "2026-10"
  uid: String
  period: String
  breakdown: Map<String, Double>   // ej. {"alquiler": 50000, "expensas": 20000}
  amount: Double                   // suma de breakdown, para no recalcular en cada lectura
  method: String                   // "TRANSFERENCIA" | "MERCADO_PAGO"
  status: String                   // "A_VERIFICAR" | "PAGADO" | "RECHAZADO"
  receiptUrl: String = ""          // Storage, solo método TRANSFERENCIA
  mercadoPagoPaymentId: String = ""// solo método MERCADO_PAGO
  createdAt: Timestamp
  verifiedAt: Timestamp? = null
  verifiedByUid: String = ""
```

El id del documento de pago es `{uid}_{period}` (mismo truco que `Reservation` con
`slotId`): hace trivial buscar "mi pago de este mes" con un `get()` directo y evita
duplicados por error.

## Pantallas nuevas

1. **`VecinosActivity`** (Administrador, acceso desde Más): lista los `users` del
   edificio con rol `INQUILINO` (query `where buildingId == X and role == INQUILINO`),
   cada fila con dos campos editables (monto alquiler / monto expensas, mostrando solo
   el o los que el edificio cobra) y botón guardar por fila. Mismo esqueleto visual que
   `TicketsActivity`/`ReservasActivity` (lista + `SwipeRefreshLayout`).

2. **`PagarActivity`** (Inquilino): accesible desde Más ("💳 Pagar alquiler/expensas")
   y, como atajo, tocando la tarjeta "Expensas del mes" del Dashboard en modo normal
   (en modo simple ya existe el botón grande "Ver mis facturas", que **sigue yendo a
   Facturas** — HU-01, gastos del edificio — no a esta pantalla nueva; son conceptos
   distintos: gastos del edificio vs. lo que yo debo pagar).
   - Si no hay pago del período actual o está `RECHAZADO`: muestra el desglose y el
     total, y los dos botones de pago.
   - Si está `A_VERIFICAR`: mensaje de "tu comprobante está en revisión".
   - Si está `PAGADO`: comprobante (fecha, monto, desglose, método) + link a ver
     historial de meses anteriores (lista simple, más antiguo primero).

3. **`CobrosActivity`** (Administrador, acceso desde Más): lista de inquilinos del
   período actual con tag de estado (Pagado en verde / A verificar en amarillo /
   Pendiente en rojo); tocar una fila "a verificar" abre el detalle con la imagen del
   comprobante y botones Aprobar/Rechazar.

## Repositorios

Nuevas interfaces (+ `FirebaseX`/`MockX`, resueltas por `RepositoryProvider` como el
resto del proyecto):

- **`ResidentRepository`**: `listResidents(buildingId): List<User>`,
  `updateAmounts(buildingId, uid, rentAmount, expensesAmount)`. Separado de
  `AuthRepository` porque esto es "el Administrador gestiona a otros usuarios", una
  responsabilidad distinta de "mi propia sesión/perfil".
- **`PaymentRepository`**: `getCurrentPayment(buildingId, uid, period): Payment?`,
  `listPaymentsForBuilding(buildingId, period): List<Payment>` (panel de cobros),
  `uploadReceipt(buildingId, uid, period, breakdown, receipt: Uri)` → crea el pago en
  `A_VERIFICAR`, `approvePayment(buildingId, paymentId)`, `rejectPayment(...)`,
  `createMercadoPagoPreference(buildingId, uid, period, amount): String` (devuelve la
  URL `init_point`, llamando a la Cloud Function HTTP).
- **`BuildingRepository`** se extiende (no se duplica) con `updateCbuAlias` o se cubre
  directo con el `updateBuilding` que ya existe, agregando los dos campos al modelo
  `Building`.

## Cloud Functions (`functions/index.js`)

Hoy el proyecto solo tiene funciones `onSchedule` (CLAUDE.md lo señala explícitamente).
Esto agrega el primer uso de funciones HTTP (`onRequest`, Gen 2):

- **`createMercadoPagoPreference`** (HTTP, requiere ID token de Firebase Auth válido):
  recibe `{ buildingId, uid, period, amount }`, llama a la API de Mercado Pago con el
  access token (variable de entorno de Functions, nunca en el repo ni en el cliente
  Android), crea el `Payment` en Firestore con `method: MERCADO_PAGO` y devuelve el
  `init_point`.
- **`mercadoPagoWebhook`** (HTTP, público pero valida la firma de Mercado Pago): recibe
  la notificación de pago confirmado, busca el `Payment` por `mercadoPagoPaymentId` o
  por los metadatos enviados al crear la preferencia, y lo marca `PAGADO`.
- **`checkUpcomingDueDates`** (`onSchedule`, diaria): recorre `buildings`, calcula si
  faltan `reminderDaysBefore` días para `expensesDueDay`/`rentDueDay`, y dispara un
  push (FCM) a los inquilinos de ese edificio sin pago `PAGADO` en el período actual.

En el cliente Android, el botón de Mercado Pago **no integra el SDK nativo**: abre el
`init_point` (una URL de checkout web) con un `Intent.ACTION_VIEW` / Custom Tab. Es
"Checkout Pro" real sin agregar una dependencia pesada nueva a la app.

## `firestore.rules`

- `users/{uid}.rentAmount` / `.expensesAmount`: solo escribibles por un Administrador
  del mismo `buildingId` — nunca por el propio usuario (para que no se autoasigne un
  monto).
- `buildings/{id}/payments/{paymentId}`:
  - `create`: solo el propio `uid` (método transferencia) o la Cloud Function vía
    Admin SDK (método Mercado Pago, que bypassea las reglas).
  - `update` de `status` a `PAGADO`/`RECHAZADO`: solo Administrador del edificio, o la
    Cloud Function del webhook.
  - `read`: el propio `uid`, o Administrador/Encargado del mismo edificio.

## Flavor mock

`MockResidentRepository` y `MockPaymentRepository` en `data/repository/mock/`, sobre
`MockDataStore` (se le agregan montos a los tres usuarios fijos y una lista mutable de
pagos). El botón de Mercado Pago en mock **no abre nada externo**: marca el pago como
`PAGADO` al instante, para poder demostrar el flujo de punta a punta sin cuenta real.

## Decisiones que tomé yo (marcadas para que las revises)

- Dónde vive el acceso a "Pagar" (Más + tarjeta clickeable en Inicio modo normal).
- Que el pago sea un solo monto combinado (con desglose interno) por período, no dos
  pagos separados por concepto.
- Que el "comprobante" sea un registro en la app, no un PDF.
- Que Mercado Pago se abra como checkout web (Custom Tab), no vía SDK nativo.
- **Monto congelado al crear el pago**: `amount`/`breakdown` se copian al documento de
  `Payment` en el momento de generarlo (transferencia o Mercado Pago) y no se
  recalculan después. Si el Administrador cambia `rentAmount`/`expensesAmount` de un
  vecino, afecta recién al período siguiente — el pago ya creado (`A_VERIFICAR` o
  `PAGADO`) no se mueve. Evita que un pago en curso cambie de monto bajo los pies del
  inquilino.
- **`RECHAZADO` reutiliza el mismo documento** (`{uid}_{period}`): al rechazar, el
  Administrador deja un `rejectionReason: String`; cuando el inquilino sube un nuevo
  comprobante, se sobreescribe `receiptUrl`/`breakdown`/`status` del mismo documento
  (vuelve a `A_VERIFICAR`). No se guarda un historial de intentos rechazados — solo el
  motivo del último rechazo, como dato de apoyo, no de auditoría formal.
- **Correlación del webhook de Mercado Pago**: la preferencia se crea con
  `external_reference = "{buildingId}_{uid}_{period}"` explícito (campo que Mercado
  Pago provee justamente para esto), y el webhook busca el `Payment` por ese valor, no
  por heurísticas sobre el id de pago de Mercado Pago. Si no encuentra el documento
  (caso raro: preferencia creada pero el doc no se guardó), la función lo loguea como
  error y no crea nada — no hay forma segura de adivinar `uid`/`period` sin esos datos.
- **Validación de monto en cero**: si `rentAmount`/`expensesAmount` del vecino suman
  0 (el Administrador todavía no le asignó nada), `PagarActivity` no ofrece los
  botones de pago — muestra "Todavía no tenés un monto asignado, consultá al
  Administrador". Evita pagos de $0 por Mercado Pago.

## Qué queda para el plan de implementación

- Nombres exactos de archivos/clases (siguiendo la convención ya usada), layouts XML,
  strings, validaciones de formulario (montos no negativos, etc.) y manejo de errores
  de red / Mercado Pago. Eso lo resuelve el plan de implementación (writing-plans), no
  este documento.
