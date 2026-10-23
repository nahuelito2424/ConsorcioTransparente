# Cobro de reparaciones vía Mercado Pago (HU-10 Escenario 3) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Agregar "Pagar con Mercado Pago" a la pantalla de detalle de un ticket en
estado `A_COBRAR`, para que el inquilino que lo reportó pueda saldar el costo de la
reparación sin intervención manual — reusando la integración de Mercado Pago de HU-04
(mismas credenciales, mismo webhook).

**Architecture:** Una Cloud Function callable nueva (`createTicketMercadoPagoPreference`,
mismo patrón que `createMercadoPagoPreference` de HU-04) y una extensión del
`mercadoPagoWebhook` existente para que, además de pagos de alquiler/expensas, también
resuelva pagos de tickets — rutea por un prefijo distinto en `external_reference`
(`"ticket_{buildingId}_{ticketId}"` vs `"{buildingId}_{uid}_{period}"`). A diferencia de
HU-04, acá no se escribe ningún estado especulativo al generar la preferencia: el
`Ticket` ya existe en `A_COBRAR`, así que no hay nada que crear de antemano — esto evita
por construcción el bug que encontró la revisión final de HU-04 (quedar sin poder pagar
si se cierra el checkout sin completar el pago: acá el ticket simplemente se queda en
`A_COBRAR` y el botón sigue disponible para reintentar).

**Tech Stack:** Mismo que HU-04 (Cloud Functions Gen 2, SDK `mercadopago`,
`firebase-functions-ktx`, `androidx.browser` — ya son dependencias del proyecto, no se
agrega nada nuevo).

**Spec:** [`docs/especificacion-funcional.md`](../../especificacion-funcional.md) (HU-10,
Escenario 3) y la Cloud Function `mercadoPagoWebhook` ya implementada
(`functions/index.js`), que esta plan extiende en vez de reemplazar.

## Global Constraints

- **Quién paga**: sólo el inquilino que reportó el ticket (`ticket.reportedByUid`).
  Decisión tomada con el usuario: el Administrador/Encargado **no** paga por Mercado
  Pago — sigue teniendo la opción manual de siempre (subir comprobante), para
  reparaciones de áreas comunes que no le corresponden a un vecino puntual. El botón de
  Mercado Pago sólo se muestra al propio reportero (`isReporter`), nunca a
  `canManage`.
- **No se reemplaza `markPaid`** (el comentario de clase de `TicketRepository` decía
  "alcanza con reemplazar markPaid... sin tocar el resto" — ya no aplica: se agrega
  Mercado Pago como alternativa, se mantiene el pago manual para Admin/Encargado y
  como respaldo para el propio inquilino. Actualizar ese comentario.
- **Monto**: `ticket.budgetAmount`, calculado server-side leyendo el propio documento
  del ticket — nunca se confía en un monto mandado por el cliente (mismo criterio que
  HU-04).
- **Sin escritura especulativa**: a diferencia de `createMercadoPagoPreference` (que sí
  crea un `Payment` en `A_VERIFICAR` al generar la preferencia), acá no se escribe nada
  en Firestore al generar la preferencia — el `Ticket` ya existe. El reintento
  (cerrar el checkout sin pagar y volver a tocar el botón) es seguro por construcción,
  no por una regla de negocio a mantener.
- **Validación server-side de la Cloud Function nueva**: `request.auth.uid ==
  ticket.reportedByUid` y `ticket.status == "A_COBRAR"` antes de generar nada — mismo
  criterio que el fix de I1 en la revisión final de HU-04 (no confiar en lo que mande
  el cliente, ni siquiera implícitamente a través de qué botón decide mostrar la UI).
- **`init_point`, nunca `sandbox_init_point`**: mismo fix ya aplicado en HU-04 (con
  credenciales de producción, Mercado Pago puede devolver igual el campo sandbox).
- **El webhook cruza el pago contra el recurso antes de escribir**: mismo criterio que
  el fix de I4 en HU-04 — status correcto (`A_COBRAR`, no ya `RESUELTO`) y monto
  coincidente (`transaction_amount` vs `budgetAmount`, tolerancia de centavos) antes de
  marcar `RESUELTO`. Nunca sobrescribe un ticket que ya no está `A_COBRAR`.
- **El webhook responde 500 ante errores transitorios** (no 200) — mismo fix de I3 de
  HU-04: la escritura es idempotente, así que un reintento de Mercado Pago es seguro,
  y evita que un pago ya hecho quede sin confirmar para siempre por un error de
  nuestro lado.
- **Flavor mock**: `MockTicketRepository.createTicketMercadoPagoPreference` marca el
  ticket `RESUELTO` al instante (sin red, sin URL) — mismo criterio que HU-04.
- **Sin credenciales reales de Mercado Pago ni proyecto Firebase real en este
  entorno**: la Cloud Function nueva y la extensión del webhook sólo se validan con
  `node --check` + `require` local, igual que en HU-04. El flujo completo del lado
  Android sí se puede probar a mano en el emulador (mock).
- **`firestore.rules` no cambia**: el cliente nunca escribe `status: RESUELTO` por
  Mercado Pago — lo hace la Cloud Function del webhook vía Admin SDK, que no pasa por
  las reglas (mismo criterio ya documentado para `payments/{paymentId}`). La regla de
  `tickets/{ticketId}` ya permite que el propio reportero actualice su ticket en
  `A_COBRAR` (para el pago manual, que sigue existiendo) — no hace falta tocarla.

## Review Focus

- **El Administrador/Encargado no debe ver el botón de Mercado Pago** en ningún ticket,
  ni siquiera en uno que reportaron ellos mismos (si un Encargado reporta un ticket,
  sigue siendo "el reportero", pero el botón de Mercado Pago es una decisión de
  producto sobre vecinos pagando su propia reparación — igual se evalúa si
  corresponde mostrarlo también a Encargado/Admin cuando `isReporter` sea cierto para
  ellos, ver Tarea 3).
- **Reintento sin estado previo**: cerrar el checkout sin pagar y volver a tocar
  "Pagar con Mercado Pago" debe generar una preferencia nueva sin crashear — el ticket
  nunca sale de `A_COBRAR` hasta que el webhook confirma, así que no hay estado que
  limpiar.
- **El webhook no debe resolver un ticket que ya no está `A_COBRAR`** (ya se resolvió
  por el método manual mientras tanto, o ya se resolvió por un webhook anterior) — debe
  loguear y no escribir nada.
- **Pantalla RESUELTO sin comprobante de imagen**: cuando el ticket se resuelve por
  Mercado Pago, `paymentReceiptUrl` queda vacío — la pantalla no debe mostrar un
  `ImageView` roto/vacío, sino un texto que indique que se pagó por Mercado Pago.
- **Un pago de Mercado Pago con `external_reference` del formato de ticket pero un
  `ticketId` inexistente** (building o id mal formado): el webhook debe loguear y no
  escribir nada, igual que ya hace con un `Payment` inexistente.

---

### Task 1: Modelo + Cloud Function `createTicketMercadoPagoPreference` + extensión del webhook

**Files:**
- Modify: `app/src/main/java/com/consorciotransparente/app/data/model/Ticket.kt`
- Modify: `functions/index.js`

**Interfaces:**
- Produces: `Ticket.mercadoPagoPaymentId: String = ""` (nuevo campo, igual criterio que
  `Payment.mercadoPagoPaymentId`).
- Produces: `exports.createTicketMercadoPagoPreference` — callable, consumida en la
  Tarea 2 por `TicketRepository.createTicketMercadoPagoPreference`.
- Modifica (no reemplaza la interfaz pública): `exports.mercadoPagoWebhook` ahora
  rutea por el prefijo de `external_reference`.

- [ ] **Step 1: Agregar el campo al modelo**

En `Ticket.kt`, agregar `val mercadoPagoPaymentId: String = ""` (mismo lugar/estilo que
`purchaseReceiptUrl`/`paymentReceiptUrl`).

- [ ] **Step 2: Cloud Function `createTicketMercadoPagoPreference`**

En `functions/index.js`, después de `createMercadoPagoPreference`:
```js
/**
 * HU-10 Escenario 3: genera una preferencia de Checkout Pro para que el inquilino que
 * reportó el ticket pague el costo de la reparación. A diferencia de
 * createMercadoPagoPreference, no escribe nada en Firestore acá — el Ticket ya existe
 * en A_COBRAR, y no hay estado especulativo que crear (ver Global Constraints).
 */
exports.createTicketMercadoPagoPreference = onCall(
  { secrets: [mercadoPagoAccessToken] },
  async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Tenés que iniciar sesión.");
    const { buildingId, ticketId } = request.data || {};
    if (!buildingId || !ticketId) throw new HttpsError("invalid-argument", "Falta buildingId o ticketId.");

    const ticketRef = getFirestore()
      .collection("buildings").doc(buildingId)
      .collection("tickets").doc(ticketId);
    const ticketSnap = await ticketRef.get();
    if (!ticketSnap.exists) throw new HttpsError("not-found", "No se encontró el ticket.");
    const ticket = ticketSnap.data();

    if (ticket.reportedByUid !== request.auth.uid) {
      throw new HttpsError("permission-denied", "Sólo quien reportó el ticket puede pagarlo.");
    }
    if (ticket.status !== "A_COBRAR") {
      throw new HttpsError("failed-precondition", "Este ticket no está listo para cobrar.");
    }
    const amount = ticket.budgetAmount;
    if (!(amount > 0)) throw new HttpsError("failed-precondition", "El ticket no tiene un monto a cobrar.");

    let preference;
    try {
      const client = new MercadoPagoConfig({ accessToken: mercadoPagoAccessToken.value() });
      preference = await new Preference(client).create({
        body: {
          items: [{ title: `Reparación: ${ticket.category || "Ticket"}`, quantity: 1, unit_price: amount }],
          external_reference: `ticket_${buildingId}_${ticketId}`,
          notification_url: process.env.MERCADO_PAGO_WEBHOOK_URL || undefined,
        },
      });
    } catch (err) {
      console.error("Error creando la preferencia de Mercado Pago para el ticket:", err.message);
      throw new HttpsError("unavailable", "No se pudo conectar con Mercado Pago. Probá de nuevo en un rato.");
    }

    return { initPoint: preference.init_point };
  }
);
```

- [ ] **Step 3: Extraer la resolución de pago de alquiler/expensas a una función, y rutear el webhook**

En `mercadoPagoWebhook`, extraer el bloque que hoy resuelve el `Payment` (desde el
cálculo de `parts`/`buildingId`/`uid`/`period` hasta el `paymentRef.update(...)`) a una
función `async function resolveRentPayment(externalReference, mpPayment)` que hace
exactamente lo mismo que hoy (sin cambiar ninguna de las validaciones ya existentes:
formato de referencia, documento inexistente, ya `PAGADO`, método distinto, monto que
no coincide). Agregar una función hermana:
```js
async function resolveTicketPayment(externalReference, mpPayment) {
  const rest = externalReference.slice("ticket_".length);
  const parts = rest.split("_");
  if (parts.length < 2) {
    console.warn("external_reference de ticket con formato inesperado:", externalReference);
    return;
  }
  const buildingId = parts[0];
  const ticketId = parts.slice(1).join("_");
  const ticketRef = getFirestore()
    .collection("buildings").doc(buildingId)
    .collection("tickets").doc(ticketId);
  const ticketSnap = await ticketRef.get();
  if (!ticketSnap.exists) {
    console.warn(`Webhook de Mercado Pago sin Ticket existente: ${buildingId}/${ticketId}`);
    return;
  }
  const ticket = ticketSnap.data();
  if (ticket.status === "RESUELTO") return;
  if (ticket.status !== "A_COBRAR") {
    console.warn(`Webhook de Mercado Pago para un ticket que no está A_COBRAR: ${buildingId}/${ticketId}`);
    return;
  }
  if (
    typeof mpPayment.transaction_amount === "number" &&
    Math.abs(mpPayment.transaction_amount - ticket.budgetAmount) > 0.01
  ) {
    console.warn(`Monto del pago de Mercado Pago no coincide con el ticket: ${buildingId}/${ticketId}`);
    return;
  }
  await ticketRef.update({
    status: "RESUELTO",
    mercadoPagoPaymentId: String(mpPayment.id),
  });
}
```
En el cuerpo de `mercadoPagoWebhook`, después de confirmar `mpPayment.status ===
"approved"`, reemplazar la resolución directa por:
```js
const externalReference = mpPayment.external_reference || "";
if (externalReference.startsWith("ticket_")) {
  await resolveTicketPayment(externalReference, mpPayment);
} else {
  await resolveRentPayment(externalReference, mpPayment);
}
res.status(200).send("ok");
```
(El `catch` externo que responde 500 ante errores transitorios ya envuelve todo esto —
no hace falta tocarlo.)

- [ ] **Step 4: Verificar sintaxis e imports**

Run: `cd functions && npm install && node --check index.js && node -e "const fns = require('./index.js'); console.log(Object.keys(fns));" && rm -rf node_modules package-lock.json`
Expected: `node --check` no imprime nada; el segundo comando lista
`createTicketMercadoPagoPreference` además de las funciones ya existentes, sin
excepción al cargar el módulo.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/consorciotransparente/app/data/model/Ticket.kt functions/index.js
git commit -m "Agregar Cloud Function createTicketMercadoPagoPreference y rutear el webhook por tipo de recurso"
```

---

### Task 2: `TicketRepository.createTicketMercadoPagoPreference` (Firebase + Mock)

**Files:**
- Modify: `app/src/main/java/com/consorciotransparente/app/data/repository/TicketRepository.kt`
- Modify: `app/src/main/java/com/consorciotransparente/app/data/repository/FirebaseTicketRepository.kt`
- Modify: `app/src/main/java/com/consorciotransparente/app/data/repository/mock/MockTicketRepository.kt`

**Interfaces:**
- Consumes: `exports.createTicketMercadoPagoPreference` (Tarea 1, nombre exacto de la
  función callable).
- Produces: `TicketRepository.createTicketMercadoPagoPreference(buildingId: String,
  ticketId: String): String` — URL de checkout (prod) o `""` (mock, ya quedó
  `RESUELTO`). Tarea 3 consume esta firma exacta.

- [ ] **Step 1: Extender la interfaz y actualizar el comentario de clase**

En `TicketRepository.kt`, reemplazar el comentario de clase (ya no es cierto que
"alcanza con reemplazar markPaid... sin tocar el resto" — se agregó Mercado Pago como
alternativa, no un reemplazo) y agregar:
```kotlin
/**
 * Escenario 3: Mercado Pago, sólo para quien reportó el ticket. El Admin/Encargado
 * sigue usando [markPaid] (pago manual) para reparaciones de áreas comunes que no le
 * corresponden a un vecino puntual — ver HU-10/HU-04 en docs/especificacion-funcional.md.
 */
suspend fun createTicketMercadoPagoPreference(buildingId: String, ticketId: String): String
```

- [ ] **Step 2: Implementar `FirebaseTicketRepository`**

Mismo patrón que `FirebasePaymentRepository.createMercadoPagoPreference` (constructor
con `functions: FirebaseFunctions = FirebaseFunctions.getInstance()`,
`getHttpsCallable("createTicketMercadoPagoPreference").call(mapOf("buildingId" to
buildingId, "ticketId" to ticketId)).await()`, parsear `result.getData()` como
`Map<*, *>` y devolver `data?.get("initPoint") as? String`).

- [ ] **Step 3: Implementar `MockTicketRepository`**

```kotlin
override suspend fun createTicketMercadoPagoPreference(buildingId: String, ticketId: String): String {
    update(ticketId) {
        it.copy(status = TicketStatus.RESUELTO.name, mercadoPagoPaymentId = MockDataStore.newId("mp"))
    }
    return ""
}
```
(Reusa el `update(ticketId, transform)` privado que ya existe en el archivo.)

- [ ] **Step 4: Compilar ambos flavors**

Run: `./gradlew assembleMockDebug assembleProdDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/consorciotransparente/app/data/repository/TicketRepository.kt \
  app/src/main/java/com/consorciotransparente/app/data/repository/FirebaseTicketRepository.kt \
  app/src/main/java/com/consorciotransparente/app/data/repository/mock/MockTicketRepository.kt
git commit -m "Agregar TicketRepository.createTicketMercadoPagoPreference"
```

---

### Task 3: Botón "Pagar con Mercado Pago" en `TicketDetailActivity`

**Files:**
- Modify: `app/src/main/res/layout/activity_ticket_detail.xml`
- Modify: `app/src/main/java/com/consorciotransparente/app/ui/tickets/TicketDetailViewModel.kt`
- Modify: `app/src/main/java/com/consorciotransparente/app/ui/tickets/TicketDetailActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `TicketRepository.createTicketMercadoPagoPreference(buildingId, ticketId):
  String` (Tarea 2).

- [ ] **Step 1: Layout**

En `activity_ticket_detail.xml`, dentro de `paymentSection` (antes de
`buttonMarkPaid`), agregar `buttonPayMercadoPago` (mismo estilo que `buttonMarkPaid`,
texto `@string/pagar_mercado_pago` — reusar el string ya existente de HU-04, no crear
uno nuevo). En `resolvedSection`, después de `imagePaymentReceipt`, agregar
`textPaidByMercadoPago` (un `TextView` tipo tag, mismo estilo que
`ticket_resolved_notice`, `android:visibility="gone"` por defecto, texto
`@string/ticket_paid_by_mercado_pago`).

- [ ] **Step 2: `TicketDetailViewModel`**

Mismo patrón que `PagarViewModel.payWithMercadoPago`:
```kotlin
private val _mercadoPagoUrl = MutableLiveData<Resource<String>>()
val mercadoPagoUrl: LiveData<Resource<String>> = _mercadoPagoUrl

fun payWithMercadoPago(buildingId: String, ticketId: String) {
    _mercadoPagoUrl.value = Resource.Loading
    viewModelScope.launch {
        try {
            val url = ticketRepo.createTicketMercadoPagoPreference(buildingId, ticketId)
            _mercadoPagoUrl.value = Resource.Success(url)
            load(buildingId, ticketId)
        } catch (e: Exception) {
            _mercadoPagoUrl.value = Resource.Error(e.message ?: "No se pudo iniciar el pago con Mercado Pago.")
        }
    }
}
```

- [ ] **Step 3: `TicketDetailActivity`**

Imports: `androidx.browser.customtabs.CustomTabsIntent`, `android.net.Uri` (ya puede
estar importado — revisar).

```kotlin
binding.buttonPayMercadoPago.setOnClickListener { viewModel.payWithMercadoPago(buildingId, ticketId) }

viewModel.mercadoPagoUrl.observe(this) { state ->
    binding.buttonPayMercadoPago.isEnabled = state !is Resource.Loading
    when (state) {
        is Resource.Success -> if (state.data.isNotBlank()) {
            CustomTabsIntent.Builder().build().launchUrl(this, Uri.parse(state.data))
        }
        is Resource.Error -> showError(state.message)
        else -> Unit
    }
}
```
Agregar `override fun onResume() { super.onResume(); viewModel.load(buildingId, ticketId) }`
(mismo motivo que `PagarActivity`: refrescar al volver del checkout).

En `render()`, dentro del branch `TicketStatus.A_COBRAR.name`:
```kotlin
binding.paymentSection.visibility = if (data.canManage || isReporter) View.VISIBLE else View.GONE
binding.buttonPayMercadoPago.visibility = if (isReporter) View.VISIBLE else View.GONE
```
(`buttonMarkPaid` sigue visible siempre que `paymentSection` lo esté — no cambia.)

En el branch `TicketStatus.RESUELTO.name`, reemplazar el manejo de
`imagePurchaseReceipt`/`imagePaymentReceipt` para que, si
`ticket.mercadoPagoPaymentId.isNotBlank()`, se muestre `textPaidByMercadoPago` en vez
de `imagePaymentReceipt` (que debe quedar `View.GONE` explícitamente en ese caso, no
sólo "sin cargar nada" — hoy `resolvedSection` no esconde el `ImageView` si no hay
URL):
```kotlin
binding.resolvedSection.visibility = View.VISIBLE
if (ticket.mercadoPagoPaymentId.isNotBlank()) {
    binding.textPaidByMercadoPago.visibility = View.VISIBLE
    binding.imagePaymentReceipt.visibility = View.GONE
} else if (ticket.paymentReceiptUrl.isNotBlank()) {
    binding.imagePaymentReceipt.visibility = View.VISIBLE
    Glide.with(this).load(ticket.paymentReceiptUrl).centerCrop().into(binding.imagePaymentReceipt)
}
```

- [ ] **Step 4: Strings**

```xml
<string name="ticket_paid_by_mercado_pago">Pagado por Mercado Pago.</string>
```

- [ ] **Step 5: Compilar y probar a mano (mock)**

Run: `./gradlew assembleMockDebug assembleProdDebug`
Expected: `BUILD SUCCESSFUL`.

Prueba manual en el emulador (flavor mock): como Encargado/Admin, poner un ticket en
`A_COBRAR` (presupuestarlo y confirmar la compra); como el inquilino que lo reportó,
abrir el ticket y confirmar que el botón "Pagar con Mercado Pago" aparece (y que
`buttonMarkPaid` también sigue ahí); tocarlo → el ticket debe pasar a "Resuelto" y
mostrar "Pagado por Mercado Pago" (sin imagen de comprobante). Como Admin/Encargado,
abrir el mismo ticket en `A_COBRAR` **antes** de que el inquilino pague y confirmar que
NO ve el botón de Mercado Pago (sólo "Adjuntar comprobante de pago").

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/layout/activity_ticket_detail.xml \
  app/src/main/java/com/consorciotransparente/app/ui/tickets/TicketDetailViewModel.kt \
  app/src/main/java/com/consorciotransparente/app/ui/tickets/TicketDetailActivity.kt \
  app/src/main/res/values/strings.xml
git commit -m "Agregar botón Pagar con Mercado Pago a TicketDetailActivity (HU-10 Escenario 3)"
```

---

### Task 4: Documentación + QA manual completa (mock)

**Files:**
- Modify: `docs/especificacion-funcional.md` (HU-10, Escenario 3: ya no pendiente)
- Modify: `docs/roadmap.md` (sacar el ítem de "Próximos pasos")
- Modify: `docs/arquitectura.md` (sección 5, agregar `createTicketMercadoPagoPreference`
  y que el webhook ahora resuelve dos tipos de recurso)

- [ ] **Step 1: Actualizar los 3 documentos**

Mismo criterio que en HU-04: estado real, qué quedó sin probar en vivo (las Cloud
Functions) y por qué.

- [ ] **Step 2: Compilar una vez más**

Run: `./gradlew assembleMockDebug assembleProdDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add docs/especificacion-funcional.md docs/roadmap.md docs/arquitectura.md
git commit -m "Actualizar documentación: cobro de reparaciones vía Mercado Pago (HU-10)"
```
