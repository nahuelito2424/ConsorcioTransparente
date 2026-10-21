# HU-04 (Integración Mercado Pago) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Agregar el botón "Pagar con Mercado Pago" a la pantalla Pagar (HU-03 ya
implementada), con una Cloud Function que genera la preferencia de Checkout Pro y otra
que recibe el webhook de confirmación, sin intervención del Administrador.

**Architecture:** Dos Cloud Functions nuevas (primeras funciones HTTP/callable del
proyecto, hasta ahora sólo `onSchedule`/Firestore triggers) usando el SDK oficial
`mercadopago` (npm, v3): `createMercadoPagoPreference` (callable, crea el `Payment` en
Firestore con `method: MERCADO_PAGO` y devuelve la URL de checkout) y
`mercadoPagoWebhook` (HTTP público, valida la firma con el validador que trae el propio
SDK y marca el pago `PAGADO`). Del lado Android, `PaymentRepository` gana un método
nuevo que invoca la función callable vía `firebase-functions-ktx`, y `PagarActivity`
abre la URL devuelta en una Custom Tab (`androidx.browser`) — sin SDK nativo de pagos.

**Tech Stack:** Firebase Functions Gen 2 (Node 20), paquete npm `mercadopago` v3;
Android: `firebase-functions-ktx` (ya cubierto por el BOM existente) y
`androidx.browser:browser` (Custom Tabs).

**Spec:** [`docs/superpowers/specs/2026-10-20-pago-expensas-design.md`](../specs/2026-10-20-pago-expensas-design.md)
(secciones "Mercado Pago real (HU-04)" y "Cloud Functions"). HU-03, ya implementada,
cubre el resto de ese documento (Vecinos/Pagar/Cobros, transferencia).

## Global Constraints

- `Payment.method` ya es `"TRANSFERENCIA" | "MERCADO_PAGO"` y `mercadoPagoPaymentId`
  ya existe en el modelo (`Payment.kt`) — no se toca el modelo de datos.
- `external_reference` de la preferencia = `"{buildingId}_{uid}_{period}"` (valor
  exacto, ver spec) — es lo único que el webhook usa para encontrar el `Payment`; nunca
  se infiere `uid`/`period` de otra forma.
- **Monto congelado al crear el pago** (ya implementado para transferencia): el mismo
  criterio aplica acá — `amount`/`breakdown` se calculan server-side en el momento de
  crear la preferencia (nunca se confía en un monto mandado por el cliente) y no se
  recalculan después.
- El botón de Mercado Pago **no se ofrece** si el monto total asignado es `<= 0`
  (mismo criterio que ya bloquea "Adjuntar comprobante" en `PagarActivity`).
- **Sin SDK nativo de Mercado Pago**: el cliente Android abre la URL de checkout
  (`init_point`/`sandbox_init_point`) con `androidx.browser` (Custom Tabs), nunca con
  `Intent.ACTION_VIEW` a secas (Custom Tabs da una barra de progreso y back button
  nativos, sin salir del todo de la experiencia de la app) ni con una librería nativa
  de pagos.
- **Flavor mock**: `MockPaymentRepository.createMercadoPagoPreference` marca el pago
  `PAGADO` al instante (sin red, sin URL), igual que especifica el diseño — así se
  puede demostrar el flujo de punta a punta sin cuenta real de Mercado Pago.
- **Sin credenciales reales de Mercado Pago ni proyecto Firebase real en este
  entorno**: las dos Cloud Functions nuevas sólo se pueden validar con
  `node --check functions/index.js` y `node -e "require('./index.js')"` (confirma
  sintaxis y que los imports de `mercadopago`/`firebase-functions` resuelven), igual
  que se hizo con `onTicketCreated`/`onTicketStatusChanged` en un plan anterior. No hay
  forma de probar la creación real de una preferencia ni la llegada de un webhook sin
  desplegar a un proyecto de Firebase real con plan Blaze y una cuenta de Mercado Pago
  (de prueba o real) — eso queda para cuando el usuario tenga ese entorno.
- **No se duplica `sendDueDateReminders`**: el spec original mencionaba una función
  `checkUpcomingDueDates`, pero esa necesidad ya la cubre `sendDueDateReminders`
  (preexistente, HU-09). No se crea una función nueva para esto.
- **`firestore.rules` no cambia en este plan**: el spec lo menciona como sección
  propia, pero el cliente Android nunca escribe un `Payment` con
  `method: MERCADO_PAGO` — lo crea y lo marca `PAGADO` la Cloud Function, vía Admin
  SDK, que no pasa por las reglas (ya documentado así en el comentario de
  `payments/{paymentId}` desde el plan de HU-03). El cliente sólo *lee* ese documento,
  y la regla de lectura ya alcanza para cualquier `method` (no distingue por método).
- **Credenciales de Mercado Pago**: nunca en el repo ni en el cliente Android. Se leen
  en las Cloud Functions vía `defineSecret` de `firebase-functions/params` (patrón Gen2
  actual, reemplaza al `functions.config()` deprecado) — `MERCADO_PAGO_ACCESS_TOKEN` y
  `MERCADO_PAGO_WEBHOOK_SECRET`. Documentar en README cómo setearlas
  (`firebase functions:secrets:set NOMBRE`).
- API de `mercadopago` (npm, v3.6.1) confirmada contra el código fuente del SDK
  (no contra memoria): `new MercadoPagoConfig({ accessToken })`;
  `new Preference(client).create({ body })` → `Promise<PreferenceResponse>` con
  `id`/`init_point`/`sandbox_init_point`/`external_reference`; `PreferenceRequest.body`
  acepta `items`/`external_reference`/`back_urls`/`notification_url`/`auto_return`;
  `new Payment(client).get({ id })` → `Promise<PaymentResponse>` con
  `id`/`status`/`external_reference`/`transaction_amount`; el validador de firma de
  webhook es `WebhookSignatureValidator.validate({ xSignature, xRequestId, dataId,
  secret, toleranceSeconds? })` (lanza `InvalidWebhookSignatureError` si no matchea),
  también exportado por el paquete `mercadopago`.
- Convención ya establecida del proyecto: sin test automatizado (carpetas vacías). El
  "test" de cada tarea Android es `./gradlew assembleMockDebug assembleProdDebug` +
  verificación manual en el emulador (flavor `mock`) donde sea posible.

## Review Focus

- **Monto en $0 o sin asignar**: el botón de Mercado Pago no debe aparecer, igual que
  "Adjuntar comprobante" — mismo bloqueo que ya existe para transferencia.
- **Reintento**: tocar "Pagar con Mercado Pago" una segunda vez para el mismo período
  (ej. el usuario cerró el checkout sin pagar y volvió) debe generar una preferencia
  nueva sin duplicar el documento de pago ni crashear — reusa el mismo id
  `{uid}_{period}` que ya usa transferencia.
- **El Administrador no debe poder aprobar/rechazar a mano un pago en curso por
  Mercado Pago**: `CobroAdapter` sólo debe permitir tocar una fila cuando
  `status == A_VERIFICAR && method == TRANSFERENCIA`; un pago Mercado Pago pendiente
  debe verse pero no ser clickeable (el webhook es la única vía para marcarlo
  `PAGADO`).
- **Firma de webhook inválida o payload inesperado**: `mercadoPagoWebhook` debe
  devolver 401 ante una firma que no matchea, y no debe tirar una excepción no
  controlada ante un body sin los campos esperados (Mercado Pago reintenta
  notificaciones agresivamente ante timeouts/500s).
- **`external_reference` que no corresponde a ningún `Payment` existente** (preferencia
  creada pero el documento no llegó a guardarse, o un intento de notificación
  fabricada): el webhook debe loguear el caso y no escribir nada — nunca inferir
  `uid`/`period`/`buildingId` por otra vía.

**Qué de esta lista se puede verificar en este entorno, y qué no:** el flavor mock
marca `PAGADO` al instante (ver Tarea 3) — nunca pasa por un estado intermedio
"A_VERIFICAR + MERCADO_PAGO", así que los puntos 3, 4 y 5 (el tag "Mercado Pago
(pendiente)" no clickeable, y el comportamiento del webhook ante firma inválida o
referencia desconocida) **no se pueden ver funcionando en el emulador** — se verifican
leyendo el código en la revisión final, no con una prueba manual, y quedan pendientes
de un chequeo real el día que haya un proyecto de Firebase y una cuenta de Mercado Pago
de prueba. El punto 2 (reintento) es una garantía estructural, no algo a probar en
runtime: como el id del documento es siempre `{uid}_{period}` (mismo esquema que ya
usa la re-subida de transferencia tras un `RECHAZADO`), volver a tocar el botón sólo
pisa el mismo documento — no puede duplicar nada por construcción. El punto 1 (monto
en $0) sí es verificable en mock, igual que ya lo es para transferencia.

---

### Task 1: Dependencia `mercadopago` + `createMercadoPagoPreference` (Cloud Function callable)

**Files:**
- Modify: `functions/package.json`
- Modify: `functions/index.js`

**Interfaces:**
- Produces: `exports.createMercadoPagoPreference` — función callable (`onCall` de
  `firebase-functions/v2/https`) que Android invoca como
  `getHttpsCallable("createMercadoPagoPreference")` con
  `data = { buildingId: string, period: string }` (el `uid` sale de
  `request.auth.uid`, nunca del cliente). Devuelve
  `{ initPoint: string }` (usa `sandbox_init_point` si existe — cuenta de prueba —
  si no `init_point`).

- [ ] **Step 1: Agregar la dependencia**

En `functions/package.json`, agregar `"mercadopago": "^3.6.1"` a `dependencies` (mismo
estilo `^` que `firebase-admin`/`firebase-functions`).

- [ ] **Step 2: Implementar `createMercadoPagoPreference`**

En `functions/index.js`, agregar los imports:
```js
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const { MercadoPagoConfig, Preference } = require("mercadopago");

const mercadoPagoAccessToken = defineSecret("MERCADO_PAGO_ACCESS_TOKEN");
```

Implementar:
```js
exports.createMercadoPagoPreference = onCall(
  { secrets: [mercadoPagoAccessToken] },
  async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Tenés que iniciar sesión.");
    const { buildingId, period } = request.data || {};
    if (!buildingId || !period) throw new HttpsError("invalid-argument", "Falta buildingId o period.");

    const uid = request.auth.uid;
    const db = getFirestore();

    // El monto se calcula acá, server-side, a partir de lo que el Admin le asignó al
    // usuario — nunca se confía en un monto mandado por el cliente (mismo criterio que
    // "monto congelado" de la transferencia).
    const [userSnap, buildingSnap] = await Promise.all([
      db.collection("users").doc(uid).get(),
      db.collection("buildings").doc(buildingId).get(),
    ]);
    if (!userSnap.exists || !buildingSnap.exists) {
      throw new HttpsError("failed-precondition", "No se encontró tu perfil o tu edificio.");
    }
    const user = userSnap.data();
    const building = buildingSnap.data();
    const breakdown = {};
    if (building.chargesRent && user.rentAmount > 0) breakdown.alquiler = user.rentAmount;
    if (building.chargesExpenses && user.expensesAmount > 0) breakdown.expensas = user.expensesAmount;
    const amount = Object.values(breakdown).reduce((a, b) => a + b, 0);
    if (amount <= 0) throw new HttpsError("failed-precondition", "No tenés un monto asignado.");

    const paymentId = `${uid}_${period}`;
    const paymentRef = db.collection("buildings").doc(buildingId)
      .collection("payments").doc(paymentId);

    const client = new MercadoPagoConfig({ accessToken: mercadoPagoAccessToken.value() });
    const preference = await new Preference(client).create({
      body: {
        items: [{ title: "Expensas/alquiler", quantity: 1, unit_price: amount }],
        external_reference: `${buildingId}_${uid}_${period}`,
        notification_url: process.env.MERCADO_PAGO_WEBHOOK_URL || undefined,
      },
    });

    await paymentRef.set({
      uid,
      period,
      breakdown,
      amount,
      method: "MERCADO_PAGO",
      status: "A_VERIFICAR",
      createdAt: new Date(),
    });

    return { initPoint: preference.sandbox_init_point || preference.init_point };
  }
);
```

(`process.env.MERCADO_PAGO_WEBHOOK_URL` es opcional: si no está seteada, Mercado Pago
usa la URL de notificaciones configurada en el panel de la cuenta — documentar ambas
opciones en el Step de README de la Tarea 6.)

- [ ] **Step 3: Verificar sintaxis e imports**

Run: `cd functions && npm install && node --check index.js && node -e "const fns = require('./index.js'); console.log(Object.keys(fns));"`
Expected: `node --check` no imprime nada (sintaxis válida); el segundo comando lista
`createMercadoPagoPreference` entre las funciones exportadas, sin tirar excepción al
cargar el módulo. Borrar `functions/node_modules`/`functions/package-lock.json`
después (no se commitean, ver `.gitignore`).

- [ ] **Step 4: Commit**

```bash
git add functions/package.json functions/index.js
git commit -m "Agregar Cloud Function createMercadoPagoPreference (HU-04)"
```

---

### Task 2: Cloud Function `mercadoPagoWebhook`

**Files:**
- Modify: `functions/index.js`

**Interfaces:**
- Consumes: el documento `Payment` creado por la Tarea 1 (`{uid}_{period}`, campo
  `status: "A_VERIFICAR"`, `method: "MERCADO_PAGO"`).
- Produces: `exports.mercadoPagoWebhook` — función HTTP pública (`onRequest`) que
  Mercado Pago llama directamente (no pasa por Firebase Auth).

- [ ] **Step 1: Implementar el handler**

Imports nuevos en `functions/index.js`:
```js
const { onRequest } = require("firebase-functions/v2/https");
const { Payment, WebhookSignatureValidator } = require("mercadopago");

const mercadoPagoWebhookSecret = defineSecret("MERCADO_PAGO_WEBHOOK_SECRET");
```

Implementar:
```js
exports.mercadoPagoWebhook = onRequest(
  { secrets: [mercadoPagoAccessToken, mercadoPagoWebhookSecret] },
  async (req, res) => {
    const dataId = req.query["data.id"] || req.body?.data?.id;
    try {
      WebhookSignatureValidator.validate({
        xSignature: req.headers["x-signature"],
        xRequestId: req.headers["x-request-id"],
        dataId: String(dataId || ""),
        secret: mercadoPagoWebhookSecret.value(),
      });
    } catch (err) {
      console.warn("Firma de webhook de Mercado Pago inválida:", err.message);
      res.status(401).send("invalid signature");
      return;
    }

    if (req.body?.type !== "payment" || !dataId) {
      res.status(200).send("ignored");
      return;
    }

    try {
      const client = new MercadoPagoConfig({ accessToken: mercadoPagoAccessToken.value() });
      const mpPayment = await new Payment(client).get({ id: dataId });

      if (mpPayment.status !== "approved") {
        res.status(200).send("not approved yet");
        return;
      }

      // external_reference = "{buildingId}_{uid}_{period}" (ver Tarea 1). uid y period
      // nunca se infieren de otra forma — si no matchea este formato o el documento no
      // existe, se loguea y no se escribe nada.
      const parts = (mpPayment.external_reference || "").split("_");
      if (parts.length < 3) {
        console.warn("external_reference con formato inesperado:", mpPayment.external_reference);
        res.status(200).send("unrecognized reference");
        return;
      }
      const [buildingId, uid, period] = [parts[0], parts[1], parts.slice(2).join("_")];
      const paymentRef = getFirestore()
        .collection("buildings").doc(buildingId)
        .collection("payments").doc(`${uid}_${period}`);
      const paymentSnap = await paymentRef.get();
      if (!paymentSnap.exists) {
        console.warn(`Webhook de Mercado Pago sin Payment existente: ${buildingId}_${uid}_${period}`);
        res.status(200).send("payment not found");
        return;
      }

      await paymentRef.update({
        status: "PAGADO",
        mercadoPagoPaymentId: String(mpPayment.id),
        verifiedAt: new Date(),
      });
      res.status(200).send("ok");
    } catch (err) {
      console.error("Error procesando webhook de Mercado Pago:", err.message);
      res.status(200).send("error logged"); // 200 para que MP no reintente en loop; el error queda en logs
    }
  }
);
```

- [ ] **Step 2: Verificar sintaxis e imports**

Run: `cd functions && node --check index.js && node -e "const fns = require('./index.js'); console.log(Object.keys(fns));"`
Expected: lista `mercadoPagoWebhook` además de las funciones de la Tarea 1, sin
excepción al cargar el módulo.

- [ ] **Step 3: Commit**

```bash
git add functions/index.js
git commit -m "Agregar Cloud Function mercadoPagoWebhook (HU-04)"
```

---

### Task 3: `PaymentRepository.createMercadoPagoPreference` (Firebase + Mock)

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`
- Modify: `app/src/main/java/com/consorciotransparente/app/data/repository/PaymentRepository.kt`
- Modify: `app/src/main/java/com/consorciotransparente/app/data/repository/FirebasePaymentRepository.kt`
- Modify: `app/src/main/java/com/consorciotransparente/app/data/repository/mock/MockPaymentRepository.kt`

**Interfaces:**
- Consumes: ninguna de este plan (es la primera tarea Android).
- Produces: `PaymentRepository.createMercadoPagoPreference(buildingId: String, period: String): String`
  — devuelve la URL de checkout (prod) o `""` (mock: ya quedó `PAGADO`, nada que abrir).
  Task 4 consume esta firma exacta.

- [ ] **Step 1: Agregar la dependencia `firebase-functions-ktx`**

En `gradle/libs.versions.toml`, junto a las demás `firebase-*-ktx` (sin version.ref
propio, lo resuelve el BOM):
```toml
firebase-functions-ktx = { group = "com.google.firebase", name = "firebase-functions-ktx" }
```
En `app/build.gradle.kts`, junto a `implementation(libs.firebase.messaging.ktx)`:
```kotlin
implementation(libs.firebase.functions.ktx)
```

- [ ] **Step 2: Extender la interfaz**

En `PaymentRepository.kt`, agregar:
```kotlin
/**
 * HU-04: genera una preferencia de Checkout Pro para el período actual y devuelve la
 * URL a abrir (Custom Tab). El monto se calcula server-side (ver Cloud Function
 * createMercadoPagoPreference) — acá no se manda ningún monto.
 */
suspend fun createMercadoPagoPreference(buildingId: String, period: String): String
```

- [ ] **Step 3: Implementar `FirebasePaymentRepository`**

```kotlin
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.ktx.functions
import com.google.firebase.Firebase
```
(agregar `functions: FirebaseFunctions = Firebase.functions` al constructor, mismo
patrón que `db`/`storage`)
```kotlin
override suspend fun createMercadoPagoPreference(buildingId: String, period: String): String {
    val result = functions.getHttpsCallable("createMercadoPagoPreference")
        .call(mapOf("buildingId" to buildingId, "period" to period))
        .await()
    val data = result.data as? Map<*, *>
    return data?.get("initPoint") as? String ?: error("Mercado Pago no devolvió una URL de pago.")
}
```

- [ ] **Step 4: Implementar `MockPaymentRepository`**

Marca el pago `PAGADO` al instante (mismo criterio que el diseño: "sin cuenta real").
Reusa el mismo `docId(uid, period)`/`update` que ya usan `approvePayment` y el resto del
mock:
```kotlin
override suspend fun createMercadoPagoPreference(buildingId: String, period: String): String {
    val user = MockDataStore.currentUser ?: error("Iniciá sesión de nuevo.")
    val uid = user.uid
    val building = MockDataStore.building
    val breakdown = buildMap {
        if (building.chargesRent && user.rentAmount > 0.0) put("alquiler", user.rentAmount)
        if (building.chargesExpenses && user.expensesAmount > 0.0) put("expensas", user.expensesAmount)
    }
    val payment = Payment(
        id = docId(uid, period), uid = uid, period = period, breakdown = breakdown,
        amount = breakdown.values.sum(), method = PaymentMethod.MERCADO_PAGO.name,
        status = PaymentStatus.PAGADO.name, createdAt = Date(), verifiedAt = Date()
    )
    val index = MockDataStore.payments.indexOfFirst { it.id == payment.id }
    if (index >= 0) MockDataStore.payments[index] = payment else MockDataStore.payments.add(payment)
    return ""
}
```
(`docId(uid, period) = "${uid}_$period"` ya es un helper privado de
`MockPaymentRepository` — reusarlo tal cual, como ya hacen `uploadReceipt` y
`getCurrentPayment` en ese mismo archivo.)

- [ ] **Step 5: Compilar ambos flavors**

Run: `./gradlew assembleMockDebug assembleProdDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts \
  app/src/main/java/com/consorciotransparente/app/data/repository/PaymentRepository.kt \
  app/src/main/java/com/consorciotransparente/app/data/repository/FirebasePaymentRepository.kt \
  app/src/main/java/com/consorciotransparente/app/data/repository/mock/MockPaymentRepository.kt
git commit -m "Agregar PaymentRepository.createMercadoPagoPreference (HU-04)"
```

---

### Task 4: Botón "Pagar con Mercado Pago" en `PagarActivity` (Custom Tab)

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`
- Modify: `app/src/main/res/layout/activity_pagar.xml`
- Modify: `app/src/main/java/com/consorciotransparente/app/ui/pagos/PagarViewModel.kt`
- Modify: `app/src/main/java/com/consorciotransparente/app/ui/pagos/PagarActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `PaymentRepository.createMercadoPagoPreference(buildingId, period): String`
  (Task 3).

- [ ] **Step 1: Agregar la dependencia `androidx.browser`**

En `gradle/libs.versions.toml`:
```toml
# [versions]
browser = "1.8.0"
# [libraries]
androidx-browser = { group = "androidx.browser", name = "browser", version.ref = "browser" }
```
En `app/build.gradle.kts`, junto a los demás `implementation`:
```kotlin
implementation(libs.androidx.browser)
```

- [ ] **Step 2: Agregar el botón al layout**

En `activity_pagar.xml`, dentro de `sectionPending` (después de
`buttonAttachReceipt`), un botón `buttonPayMercadoPago` (mismo estilo que
`buttonAttachReceipt`, texto `@string/pagar_mercado_pago`).

- [ ] **Step 3: `PagarViewModel`**

Agregar un evento de un solo disparo para la URL a abrir (no un `state` normal, para
no reabrir el navegador si el usuario rota la pantalla):
```kotlin
private val _mercadoPagoUrl = MutableLiveData<Resource<String>>()
val mercadoPagoUrl: LiveData<Resource<String>> = _mercadoPagoUrl

fun payWithMercadoPago() {
    _mercadoPagoUrl.value = Resource.Loading
    viewModelScope.launch {
        try {
            val url = paymentRepo.createMercadoPagoPreference(buildingId, period)
            _mercadoPagoUrl.value = Resource.Success(url)
            load() // refresca el estado (mock: ya quedó PAGADO al volver a cargar)
        } catch (e: Exception) {
            _mercadoPagoUrl.value = Resource.Error(e.message ?: "No se pudo iniciar el pago con Mercado Pago.")
        }
    }
}
```

- [ ] **Step 4: `PagarActivity`**

```kotlin
binding.buttonPayMercadoPago.setOnClickListener { viewModel.payWithMercadoPago() }

viewModel.mercadoPagoUrl.observe(this) { state ->
    binding.buttonPayMercadoPago.isEnabled = state !is Resource.Loading
    when (state) {
        is Resource.Success -> if (state.data.isNotBlank()) {
            CustomTabsIntent.Builder().build().launchUrl(this, Uri.parse(state.data))
        }
        is Resource.Error -> {
            binding.textError.text = state.message
            binding.textError.visibility = View.VISIBLE
        }
        else -> Unit
    }
}
```
Agregar `override fun onResume() { super.onResume(); viewModel.load() }` — así, al
volver del checkout web (la Activity nunca se destruye, sólo pasa a segundo plano),
se refresca el estado por si el webhook ya confirmó el pago.

- [ ] **Step 5: Strings**

```xml
<string name="pagar_mercado_pago">Pagar con Mercado Pago</string>
```

- [ ] **Step 6: Compilar y probar a mano (mock)**

Run: `./gradlew assembleMockDebug assembleProdDebug`
Expected: `BUILD SUCCESSFUL`.

Prueba manual en el emulador (flavor mock, como `inquilino@mock.com` con un monto ya
asignado): tocar "Pagar con Mercado Pago" → la pantalla debe recargar y mostrar el
estado "Pagado" con método "Mercado Pago" (sin abrir ningún navegador, según el mock
de la Tarea 3).

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts \
  app/src/main/res/layout/activity_pagar.xml \
  app/src/main/java/com/consorciotransparente/app/ui/pagos/PagarViewModel.kt \
  app/src/main/java/com/consorciotransparente/app/ui/pagos/PagarActivity.kt \
  app/src/main/res/values/strings.xml
git commit -m "Agregar botón Pagar con Mercado Pago a PagarActivity (HU-04)"
```

---

### Task 5: Mensaje "A verificar" según método + Admin no puede aprobar un Mercado Pago pendiente

**Files:**
- Modify: `app/src/main/java/com/consorciotransparente/app/ui/pagos/PagarActivity.kt`
- Modify: `app/src/main/java/com/consorciotransparente/app/ui/pagos/CobroAdapter.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:** ninguna nueva — ajusta UI existente de HU-03 para distinguir método.

- [ ] **Step 1: `PagarActivity` — texto de "en revisión" según método**

En `render()`, el branch `PaymentStatus.A_VERIFICAR.name` hoy siempre muestra
`binding.textReviewing` con el mismo texto. Cambiarlo para que muestre un string
distinto según `payment.method`:
```kotlin
PaymentStatus.A_VERIFICAR.name -> {
    binding.textReviewing.visibility = View.VISIBLE
    binding.textReviewing.text = getString(
        if (payment.method == PaymentMethod.MERCADO_PAGO.name) R.string.pagar_reviewing_mercado_pago
        else R.string.pagar_pending_review
    )
}
```
(`R.string.pagar_pending_review` ya existe con el texto de transferencia; agregar
`pagar_reviewing_mercado_pago` = "Tu pago por Mercado Pago está siendo confirmado. Esto
puede tardar unos minutos." En `activity_pagar.xml`, el `TextView` `textReviewing`
tiene `android:text="@string/pagar_pending_review"` fijo — sacar ese atributo del
layout, porque de acá en adelante el texto siempre se setea desde `render()`.)

- [ ] **Step 2: `CobroAdapter` — no dejar aprobar/rechazar un Mercado Pago pendiente**

```kotlin
val isPendingTransfer = row.payment?.status == PaymentStatus.A_VERIFICAR.name &&
    row.payment.method == PaymentMethod.TRANSFERENCIA.name
val isPendingMercadoPago = row.payment?.status == PaymentStatus.A_VERIFICAR.name &&
    row.payment.method == PaymentMethod.MERCADO_PAGO.name

val (label, bg, fg) = when {
    row.payment?.status == PaymentStatus.PAGADO.name ->
        Triple(R.string.cobros_status_paid, R.color.tag_green_bg, R.color.tag_green_fg)
    isPendingMercadoPago ->
        Triple(R.string.cobros_status_mercado_pago_pending, R.color.tag_yellow_bg, R.color.tag_yellow_fg)
    isPendingTransfer ->
        Triple(R.string.cobros_status_reviewing, R.color.tag_yellow_bg, R.color.tag_yellow_fg)
    else -> Triple(R.string.cobros_status_pending, R.color.tag_red_bg, R.color.tag_red_fg)
}
binding.textStatus.setText(label)
binding.textStatus.background.setTint(context.getColor(bg))
binding.textStatus.setTextColor(context.getColor(fg))

binding.root.isClickable = isPendingTransfer
binding.root.setOnClickListener { if (isPendingTransfer) onRowClick(row) }
```
Agregar el import de `PaymentMethod`.

- [ ] **Step 3: Strings**

```xml
<string name="pagar_reviewing_mercado_pago">Tu pago por Mercado Pago está siendo confirmado. Esto puede tardar unos minutos.</string>
<string name="cobros_status_mercado_pago_pending">Mercado Pago (pendiente)</string>
```

- [ ] **Step 4: Compilar**

Run: `./gradlew assembleMockDebug assembleProdDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/consorciotransparente/app/ui/pagos/PagarActivity.kt \
  app/src/main/java/com/consorciotransparente/app/ui/pagos/CobroAdapter.kt \
  app/src/main/res/values/strings.xml
git commit -m "Distinguir Mercado Pago de transferencia en Pagar y Panel de cobros (HU-04)"
```

---

### Task 6: Documentación + QA manual completa (mock) + revisión final

**Files:**
- Modify: `CLAUDE.md` (bullet de Firebase: sacar "sin endpoints HTTP todavía" — ya no
  es cierto una vez agregadas las Tareas 1-2 de este plan)
- Modify: `README.md` (sección de Cloud Functions / variables de entorno: documentar
  `firebase functions:secrets:set MERCADO_PAGO_ACCESS_TOKEN` y
  `MERCADO_PAGO_WEBHOOK_SECRET`, y cómo configurar la URL de notificaciones en el panel
  de Mercado Pago apuntando a `mercadoPagoWebhook`)
- Modify: `docs/arquitectura.md` (sección 5, agregar las dos funciones nuevas y que ya
  no es "sin funciones HTTP")
- Modify: `docs/especificacion-funcional.md` (línea de estado de HU-04)
- Modify: `docs/roadmap.md` (fila de HU-04 → Implementada; sacar de "Próximos pasos")

- [ ] **Step 1: Actualizar los 5 documentos**

Mismo criterio que en los planes anteriores de esta sesión: estado real, qué quedó
afuera (sin probar contra Mercado Pago real), y por qué.

- [ ] **Step 2: Compilar una vez más**

Run: `./gradlew assembleMockDebug assembleProdDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: QA manual completa (emulador, flavor mock)**

Como `admin@mock.com`: confirmar que Vecinos/Panel de cobros siguen funcionando (no
deberían haber cambiado). Como `inquilino@mock.com` con un monto asignado: tocar
"Pagar con Mercado Pago" → confirmar que pasa a "Pagado" con método "Mercado Pago" sin
abrir nada externo. Como `admin@mock.com` de nuevo: confirmar que el Panel de cobros
muestra ese vecino "Pagado" en verde (no "Mercado Pago (pendiente)", porque el mock lo
marca pagado al instante — ese tag sólo se vería contra Firebase real mientras el
webhook no llegó).

- [ ] **Step 4: Commit**

```bash
git add CLAUDE.md README.md docs/arquitectura.md docs/especificacion-funcional.md docs/roadmap.md
git commit -m "Actualizar documentación para HU-04 (Mercado Pago)"
```
