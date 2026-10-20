# Pago de expensas (transferencia) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Un inquilino puede ver cuánto debe pagar este mes (alquiler y/o expensas, monto
fijo asignado por el Administrador), pagarlo por transferencia subiendo un comprobante,
y el Administrador puede aprobarlo o rechazarlo desde un panel de cobros. Esto es la
mitad "transferencia" de HU-03; quedan para planes aparte, apoyándose en el modelo de
datos que este plan deja listo: Mercado Pago (HU-04, Cloud Function + webhook + botón)
y la notificación push de vencimiento próximo (Cloud Function `onSchedule`, no toca
código Android y no tiene dependencias con las tareas de este plan más que leer
`Payment`/`Building`, así que no se gana nada mezclándola acá).

**Architecture:** Mismo patrón ya establecido en el proyecto: una interfaz por
repositorio en `data/repository/` con implementación `FirebaseX`/`MockX`, resuelta por
`RepositoryProvider` según el flavor; `ViewModel` con `LiveData<Resource<T>>`;
Activities con ViewBinding. Dos repos nuevos (`ResidentRepository`,
`PaymentRepository`), tres pantallas nuevas (Vecinos, Pagar, Panel de cobros).

**Tech Stack:** Kotlin, Firebase Firestore/Storage, Glide (preview del comprobante en el
panel de cobros), Navigation/Activities existentes. Sin librerías nuevas.

**Spec:** `docs/superpowers/specs/2026-10-20-pago-expensas-design.md` (leer antes de
implementar — este plan asume sus decisiones, no las repite todas).

## Global Constraints

- Nombres de código en inglés, strings de UI y comentarios en español (Argentina) —
  convención del proyecto (`CLAUDE.md`).
- Ningún repo usa un framework de DI: todo ViewModel toma el repo como default value de
  su constructor, resuelto vía `RepositoryProvider.x` (nunca instanciado directo).
- Ninguna regla de negocio con consecuencias reales vive solo en la UI — todo lo que
  dependa de rol/ownership va reforzado en `firestore.rules`.
- El monto de un pago queda congelado en el documento al crearse (spec, sección
  "Decisiones que tomé yo"): nunca se recalcula contra `users/{uid}` después.
- Cada período (`yyyy-MM`) es independiente — no se acumula deuda de un mes a otro
  (spec, sección "Períodos independientes").
- No hay tests automatizados en este proyecto (`CLAUDE.md`: carpetas `test`/
  `androidTest` vacías). La verificación de cada tarea es: compila
  (`./gradlew assembleMockDebug assembleProdDebug`) y, para tareas de UI, una prueba
  manual puntual en el emulador con el flavor `mock` (mismo método usado en el resto de
  la sesión: instalar el APK, loguearse, tocar la pantalla, revisar con `adb
  exec-out screencap`).
- Al terminar, actualizar `docs/roadmap.md` (tabla de estado) y el comentario de clase
  de los modelos/repos nuevos con qué HU implementan y por qué — mismo estilo que los
  ya existentes (convención de `CLAUDE.md`).

## Review Focus

- Monto en $0 o sin asignar (el Administrador todavía no le puso nada a ese vecino):
  `PagarActivity` no debe ofrecer los botones de pago, solo un aviso. (Task 6)
- Re-subida de comprobante después de un `RECHAZADO`: debe sobreescribir el mismo
  documento (`{uid}_{period}`) y volver a `A_VERIFICAR`, no crear uno nuevo. (Task 6)
- Edificio que no cobra un concepto (`chargesRent`/`chargesExpenses` en `false`): ni
  `VecinosActivity` ni `PagarActivity` deben pedir/mostrar ese concepto. (Tasks 5, 6)
- Un Encargado no debe ver "Vecinos" ni "Panel de cobros" en Más — son exclusivos del
  Administrador (a diferencia de Tickets, donde Encargado sí entra). (Task 8)
- Un inquilino no puede escribirse a sí mismo `rentAmount`/`expensesAmount` vía
  Firestore directo (la regla debe rechazarlo). Solo verificable contra un proyecto
  Firebase real (flavor `prod`), no contra el flavor `mock` — dejar la prueba anotada
  para cuando el usuario tenga su proyecto de Firebase configurado. (Task 1)

---

### Task 1: Modelos de datos + reglas de Firestore + índice

**Files:**
- Create: `app/src/main/java/com/consorciotransparente/app/data/model/Payment.kt`
- Modify: `app/src/main/java/com/consorciotransparente/app/data/model/User.kt`
- Modify: `app/src/main/java/com/consorciotransparente/app/data/model/Building.kt`
- Modify: `firestore.rules`
- Modify: `firestore.indexes.json`

**Interfaces:**
- Produces: `data class Payment(id, uid, period, breakdown: Map<String, Double>, amount: Double, method: String, status: String, receiptUrl: String, mercadoPagoPaymentId: String, rejectionReason: String, createdAt: Date?, verifiedAt: Date?, verifiedByUid: String)`, `enum class PaymentMethod { TRANSFERENCIA, MERCADO_PAGO }`, `enum class PaymentStatus { A_VERIFICAR, PAGADO, RECHAZADO }` (package `data.model`).
- Produces: `User.rentAmount: Double = 0.0`, `User.expensesAmount: Double = 0.0`.
- Produces: `Building.cbu: String = ""`, `Building.alias: String = ""`.

- [ ] **Step 1: Crear `Payment.kt`**

```kotlin
package com.consorciotransparente.app.data.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

enum class PaymentMethod { TRANSFERENCIA, MERCADO_PAGO }
enum class PaymentStatus { A_VERIFICAR, PAGADO, RECHAZADO }

/**
 * Documento en "buildings/{buildingId}/payments", doc id = "{uid}_{period}" (period =
 * "yyyy-MM"). Implementa HU-03: breakdown/amount quedan congelados al crear el pago —
 * si el Administrador cambia los montos del vecino después, no afecta a un pago ya
 * generado, recién al período siguiente. Reutiliza el mismo id tras un RECHAZADO (no
 * hay historial de intentos, solo el motivo del último rechazo).
 */
data class Payment(
    val id: String = "",
    val uid: String = "",
    val period: String = "",
    val breakdown: Map<String, Double> = emptyMap(),
    val amount: Double = 0.0,
    val method: String = PaymentMethod.TRANSFERENCIA.name,
    val status: String = PaymentStatus.A_VERIFICAR.name,
    val receiptUrl: String = "",
    val mercadoPagoPaymentId: String = "",
    val rejectionReason: String = "",
    @ServerTimestamp val createdAt: Date? = null,
    val verifiedAt: Date? = null,
    val verifiedByUid: String = ""
)
```

- [ ] **Step 2: Agregar campos a `User.kt`**

Agregar `val rentAmount: Double = 0.0` y `val expensesAmount: Double = 0.0` al final de
los parámetros del constructor de `User` (antes de la llave de cierre), con un
comentario corto: estos dos campos sólo los escribe un Administrador (ver
`firestore.rules` y `ResidentRepository`), nunca el propio usuario.

- [ ] **Step 3: Agregar campos a `Building.kt`**

Agregar `val cbu: String = ""` y `val alias: String = ""` al constructor de `Building`
(junto a los demás campos de HU-09).

- [ ] **Step 4: Endurecer la regla de `users/{uid}` en `firestore.rules:42-51`**

Reemplazar el bloque `match /users/{uid} { ... }` completo por:

```
match /users/{uid} {
  allow read: if isSignedIn() && (request.auth.uid == uid || isAdmin());
  allow create: if isSignedIn() && request.auth.uid == uid;
  allow update: if isSignedIn() && (
    (isAdmin() && belongsToBuilding(resource.data.buildingId)) ||
    (request.auth.uid == uid && request.resource.data.role == resource.data.role &&
      request.resource.data.rentAmount == resource.data.rentAmount &&
      request.resource.data.expensesAmount == resource.data.expensesAmount) ||
    (request.auth.uid == uid && resource.data.buildingId == "" && resource.data.role == "INQUILINO"
      && request.resource.data.role == "ADMINISTRADOR" && request.resource.data.buildingId != "")
  );
}
```

Esto agrega `belongsToBuilding(resource.data.buildingId)` a la rama de Administrador
(antes cualquier admin de cualquier edificio podía editar cualquier perfil — se
aprovecha este cambio para cerrarlo) y bloquea que el propio usuario cambie
`rentAmount`/`expensesAmount` aunque no cambie de rol.

- [ ] **Step 5: Agregar el bloque de `payments` en `firestore.rules`, dentro de `match /buildings/{buildingId}`, después del bloque `tickets` (línea 111)**

```
      // HU-03: pago de alquiler/expensas. El monto queda congelado al crearse (no se
      // recalcula). El propio inquilino crea/re-sube su comprobante de transferencia;
      // sólo el Administrador (o la Cloud Function del webhook de Mercado Pago, que
      // usa el Admin SDK y no pasa por estas reglas) lo aprueba, rechaza, o crea un
      // pago por Mercado Pago.
      match /payments/{paymentId} {
        allow read: if belongsToBuilding(buildingId) &&
          (resource.data.uid == request.auth.uid || isEncargadoOrAdmin());
        allow create, update: if belongsToBuilding(buildingId) &&
          request.resource.data.uid == request.auth.uid &&
          request.resource.data.method == "TRANSFERENCIA" &&
          request.resource.data.status == "A_VERIFICAR";
        allow update: if isAdmin() && belongsToBuilding(buildingId) &&
          (request.resource.data.status == "PAGADO" || request.resource.data.status == "RECHAZADO");
      }
```

- [ ] **Step 6: Agregar índice compuesto en `firestore.indexes.json`**

Agregar, dentro del array `"indexes"`, junto al de `votes` ya existente:

```json
{
  "collectionGroup": "payments",
  "queryScope": "COLLECTION",
  "fields": [
    { "fieldPath": "uid", "order": "ASCENDING" },
    { "fieldPath": "period", "order": "DESCENDING" }
  ]
}
```

(Hace falta para `listMyPayments` en Task 3 — Firestore exige índice compuesto cuando
se combina `whereEqualTo` en un campo con `orderBy` en otro.)

- [ ] **Step 7: Compilar**

Run: `./gradlew assembleMockDebug assembleProdDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/consorciotransparente/app/data/model/Payment.kt \
        app/src/main/java/com/consorciotransparente/app/data/model/User.kt \
        app/src/main/java/com/consorciotransparente/app/data/model/Building.kt \
        firestore.rules firestore.indexes.json
git commit -m "Agregar modelo Payment y campos de monto/CBU para HU-03"
```

---

### Task 2: `ResidentRepository` (listar vecinos, asignar montos)

**Files:**
- Create: `app/src/main/java/com/consorciotransparente/app/data/repository/ResidentRepository.kt`
- Create: `app/src/main/java/com/consorciotransparente/app/data/repository/FirebaseResidentRepository.kt`
- Create: `app/src/main/java/com/consorciotransparente/app/data/repository/mock/MockResidentRepository.kt`
- Modify: `app/src/main/java/com/consorciotransparente/app/data/repository/RepositoryProvider.kt`
- Modify: `app/src/main/java/com/consorciotransparente/app/data/repository/mock/MockDataStore.kt`

**Interfaces:**
- Consumes: `User` (Task 1), `MockDataStore.users`, `MockDataStore.currentUser` (ya existentes).
- Produces: `interface ResidentRepository { suspend fun listResidents(buildingId: String): List<User>; suspend fun updateAmounts(buildingId: String, uid: String, rentAmount: Double, expensesAmount: Double) }`, `RepositoryProvider.resident: ResidentRepository`.

- [ ] **Step 1: Crear la interfaz `ResidentRepository.kt`**

```kotlin
package com.consorciotransparente.app.data.repository

import com.consorciotransparente.app.data.model.User

/**
 * Implementa la parte de HU-03 donde el Administrador le asigna un monto fijo de
 * alquiler y/o expensas a cada inquilino de su edificio (sin alícuotas/unidades, ver
 * docs/superpowers/specs/2026-10-20-pago-expensas-design.md).
 */
interface ResidentRepository {
    suspend fun listResidents(buildingId: String): List<User>
    suspend fun updateAmounts(buildingId: String, uid: String, rentAmount: Double, expensesAmount: Double)
}
```

- [ ] **Step 2: Crear `FirebaseResidentRepository.kt`**

```kotlin
package com.consorciotransparente.app.data.repository

import com.consorciotransparente.app.data.model.Role
import com.consorciotransparente.app.data.model.User
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirebaseResidentRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ResidentRepository {
    override suspend fun listResidents(buildingId: String): List<User> {
        val snapshot = db.collection("users")
            .whereEqualTo("buildingId", buildingId)
            .whereEqualTo("role", Role.INQUILINO.name)
            .get().await()
        return snapshot.documents.mapNotNull { it.toObject(User::class.java) }
    }

    override suspend fun updateAmounts(buildingId: String, uid: String, rentAmount: Double, expensesAmount: Double) {
        db.collection("users").document(uid).update(
            mapOf("rentAmount" to rentAmount, "expensesAmount" to expensesAmount)
        ).await()
    }
}
```

`buildingId` no se usa para construir la ruta (los usuarios viven en `users/{uid}`
plano, no en una subcolección del edificio) pero se mantiene en la firma por
consistencia con el resto de los repos y por si `updateAmounts` necesita en el futuro
validar pertenencia antes de escribir.

- [ ] **Step 3: Agregar a `MockDataStore.kt`**

Cambiar `val users = listOf(...)` por `val users = mutableListOf(...)` (ya es
`mutableListOf` en varias otras colecciones de este archivo, seguir ese estilo). No
hace falta sembrar montos de ejemplo (arrancan en 0.0 por el default del data class).

Agregar la función:
```kotlin
fun updateUserAmounts(uid: String, rentAmount: Double, expensesAmount: Double) {
    val index = users.indexOfFirst { it.uid == uid }
    if (index < 0) return
    val updated = users[index].copy(rentAmount = rentAmount, expensesAmount = expensesAmount)
    users[index] = updated
    if (currentUser?.uid == uid) currentUser = updated
}
```

- [ ] **Step 4: Crear `MockResidentRepository.kt`**

```kotlin
package com.consorciotransparente.app.data.repository.mock

import com.consorciotransparente.app.data.model.Role
import com.consorciotransparente.app.data.model.User
import com.consorciotransparente.app.data.repository.ResidentRepository

class MockResidentRepository : ResidentRepository {
    override suspend fun listResidents(buildingId: String): List<User> =
        MockDataStore.users.filter { it.buildingId == buildingId && it.role == Role.INQUILINO.name }

    override suspend fun updateAmounts(buildingId: String, uid: String, rentAmount: Double, expensesAmount: Double) {
        MockDataStore.updateUserAmounts(uid, rentAmount, expensesAmount)
    }
}
```

- [ ] **Step 5: Agregar a `RepositoryProvider.kt`**

```kotlin
val resident: ResidentRepository by lazy {
    if (BuildConfig.USE_MOCK_DATA) MockResidentRepository() else FirebaseResidentRepository()
}
```
(y el import `com.consorciotransparente.app.data.repository.mock.MockResidentRepository`).

- [ ] **Step 6: Compilar**

Run: `./gradlew assembleMockDebug assembleProdDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/consorciotransparente/app/data/repository/ResidentRepository.kt \
        app/src/main/java/com/consorciotransparente/app/data/repository/FirebaseResidentRepository.kt \
        app/src/main/java/com/consorciotransparente/app/data/repository/mock/MockResidentRepository.kt \
        app/src/main/java/com/consorciotransparente/app/data/repository/RepositoryProvider.kt \
        app/src/main/java/com/consorciotransparente/app/data/repository/mock/MockDataStore.kt
git commit -m "Agregar ResidentRepository para listar vecinos y asignar montos"
```

---

### Task 3: `PaymentRepository` (sin Mercado Pago todavía) + helper de período

**Files:**
- Create: `app/src/main/java/com/consorciotransparente/app/util/Period.kt`
- Create: `app/src/main/java/com/consorciotransparente/app/data/repository/PaymentRepository.kt`
- Create: `app/src/main/java/com/consorciotransparente/app/data/repository/FirebasePaymentRepository.kt`
- Create: `app/src/main/java/com/consorciotransparente/app/data/repository/mock/MockPaymentRepository.kt`
- Modify: `app/src/main/java/com/consorciotransparente/app/data/repository/RepositoryProvider.kt`
- Modify: `app/src/main/java/com/consorciotransparente/app/data/repository/mock/MockDataStore.kt`

**Interfaces:**
- Consumes: `Payment`, `PaymentMethod`, `PaymentStatus` (Task 1).
- Produces: `fun currentPeriod(): String` (package `util`). `interface PaymentRepository { suspend fun getCurrentPayment(buildingId: String, uid: String, period: String): Payment?; suspend fun listPaymentsForBuilding(buildingId: String, period: String): List<Payment>; suspend fun listMyPayments(buildingId: String, uid: String): List<Payment>; suspend fun uploadReceipt(buildingId: String, uid: String, period: String, breakdown: Map<String, Double>, receipt: Uri): Payment; suspend fun approvePayment(buildingId: String, paymentId: String, verifiedByUid: String); suspend fun rejectPayment(buildingId: String, paymentId: String, reason: String) }`, `RepositoryProvider.payment: PaymentRepository`.

- [ ] **Step 1: Crear `util/Period.kt`**

```kotlin
package com.consorciotransparente.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Período de pago actual en formato "yyyy-MM", usado como parte del id de Payment. */
fun currentPeriod(): String = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
```

- [ ] **Step 2: Crear la interfaz `PaymentRepository.kt`**

```kotlin
package com.consorciotransparente.app.data.repository

import android.net.Uri
import com.consorciotransparente.app.data.model.Payment

/**
 * Implementa HU-03 (pago por transferencia). El método Mercado Pago (HU-04) se agrega
 * en un plan aparte; esta interfaz ya deja el modelo de datos listo para eso
 * (Payment.method/mercadoPagoPaymentId).
 */
interface PaymentRepository {
    suspend fun getCurrentPayment(buildingId: String, uid: String, period: String): Payment?
    suspend fun listPaymentsForBuilding(buildingId: String, period: String): List<Payment>
    suspend fun listMyPayments(buildingId: String, uid: String): List<Payment>

    /** Escenario transferencia: crea o re-sube (tras un RECHAZADO) el comprobante. */
    suspend fun uploadReceipt(
        buildingId: String,
        uid: String,
        period: String,
        breakdown: Map<String, Double>,
        receipt: Uri
    ): Payment

    suspend fun approvePayment(buildingId: String, paymentId: String, verifiedByUid: String)
    suspend fun rejectPayment(buildingId: String, paymentId: String, reason: String)
}
```

- [ ] **Step 3: Crear `FirebasePaymentRepository.kt`**

```kotlin
package com.consorciotransparente.app.data.repository

import android.net.Uri
import com.consorciotransparente.app.data.model.Payment
import com.consorciotransparente.app.data.model.PaymentMethod
import com.consorciotransparente.app.data.model.PaymentStatus
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID

class FirebasePaymentRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) : PaymentRepository {
    private fun paymentsRef(buildingId: String) =
        db.collection("buildings").document(buildingId).collection("payments")

    private fun docId(uid: String, period: String) = "${uid}_$period"

    override suspend fun getCurrentPayment(buildingId: String, uid: String, period: String): Payment? {
        val doc = paymentsRef(buildingId).document(docId(uid, period)).get().await()
        return doc.toObject(Payment::class.java)?.copy(id = doc.id)
    }

    override suspend fun listPaymentsForBuilding(buildingId: String, period: String): List<Payment> {
        val snapshot = paymentsRef(buildingId).whereEqualTo("period", period).get().await()
        return snapshot.documents.mapNotNull { it.toObject(Payment::class.java)?.copy(id = it.id) }
    }

    override suspend fun listMyPayments(buildingId: String, uid: String): List<Payment> {
        val snapshot = paymentsRef(buildingId)
            .whereEqualTo("uid", uid)
            .orderBy("period", Query.Direction.DESCENDING)
            .get().await()
        return snapshot.documents.mapNotNull { it.toObject(Payment::class.java)?.copy(id = it.id) }
    }

    override suspend fun uploadReceipt(
        buildingId: String,
        uid: String,
        period: String,
        breakdown: Map<String, Double>,
        receipt: Uri
    ): Payment {
        val fileName = "payments/$buildingId/${uid}_${period}_${UUID.randomUUID()}.jpg"
        val ref = storage.reference.child(fileName)
        ref.putFile(receipt).await()
        val url = ref.downloadUrl.await().toString()

        val payment = Payment(
            uid = uid,
            period = period,
            breakdown = breakdown,
            amount = breakdown.values.sum(),
            method = PaymentMethod.TRANSFERENCIA.name,
            status = PaymentStatus.A_VERIFICAR.name,
            receiptUrl = url,
            createdAt = Date()
        )
        paymentsRef(buildingId).document(docId(uid, period)).set(payment).await()
        return payment.copy(id = docId(uid, period))
    }

    override suspend fun approvePayment(buildingId: String, paymentId: String, verifiedByUid: String) {
        paymentsRef(buildingId).document(paymentId).update(
            mapOf(
                "status" to PaymentStatus.PAGADO.name,
                "verifiedAt" to Date(),
                "verifiedByUid" to verifiedByUid
            )
        ).await()
    }

    override suspend fun rejectPayment(buildingId: String, paymentId: String, reason: String) {
        paymentsRef(buildingId).document(paymentId).update(
            mapOf(
                "status" to PaymentStatus.RECHAZADO.name,
                "rejectionReason" to reason
            )
        ).await()
    }
}
```

- [ ] **Step 4: Agregar a `MockDataStore.kt`**

```kotlin
val payments = mutableListOf<Payment>()
```
(lista vacía: el flujo de demo arranca con el Administrador asignando montos y el
inquilino pagando desde cero, no hace falta sembrar pagos de ejemplo.)

- [ ] **Step 5: Crear `MockPaymentRepository.kt`**

```kotlin
package com.consorciotransparente.app.data.repository.mock

import android.net.Uri
import com.consorciotransparente.app.data.model.Payment
import com.consorciotransparente.app.data.model.PaymentMethod
import com.consorciotransparente.app.data.model.PaymentStatus
import com.consorciotransparente.app.data.repository.PaymentRepository
import java.util.Date

class MockPaymentRepository : PaymentRepository {
    private fun docId(uid: String, period: String) = "${uid}_$period"

    override suspend fun getCurrentPayment(buildingId: String, uid: String, period: String): Payment? =
        MockDataStore.payments.firstOrNull { it.id == docId(uid, period) }

    override suspend fun listPaymentsForBuilding(buildingId: String, period: String): List<Payment> =
        MockDataStore.payments.filter { it.period == period }

    override suspend fun listMyPayments(buildingId: String, uid: String): List<Payment> =
        MockDataStore.payments.filter { it.uid == uid }.sortedByDescending { it.period }

    override suspend fun uploadReceipt(
        buildingId: String,
        uid: String,
        period: String,
        breakdown: Map<String, Double>,
        receipt: Uri
    ): Payment {
        val payment = Payment(
            id = docId(uid, period),
            uid = uid,
            period = period,
            breakdown = breakdown,
            amount = breakdown.values.sum(),
            method = PaymentMethod.TRANSFERENCIA.name,
            status = PaymentStatus.A_VERIFICAR.name,
            receiptUrl = receipt.toString(),
            createdAt = Date()
        )
        val index = MockDataStore.payments.indexOfFirst { it.id == payment.id }
        if (index >= 0) MockDataStore.payments[index] = payment else MockDataStore.payments.add(payment)
        return payment
    }

    override suspend fun approvePayment(buildingId: String, paymentId: String, verifiedByUid: String) {
        update(paymentId) { it.copy(status = PaymentStatus.PAGADO.name, verifiedAt = Date(), verifiedByUid = verifiedByUid) }
    }

    override suspend fun rejectPayment(buildingId: String, paymentId: String, reason: String) {
        update(paymentId) { it.copy(status = PaymentStatus.RECHAZADO.name, rejectionReason = reason) }
    }

    private fun update(paymentId: String, transform: (Payment) -> Payment) {
        val index = MockDataStore.payments.indexOfFirst { it.id == paymentId }
        if (index >= 0) MockDataStore.payments[index] = transform(MockDataStore.payments[index])
    }
}
```

- [ ] **Step 6: Agregar a `RepositoryProvider.kt`**

```kotlin
val payment: PaymentRepository by lazy {
    if (BuildConfig.USE_MOCK_DATA) MockPaymentRepository() else FirebasePaymentRepository()
}
```

- [ ] **Step 7: Compilar**

Run: `./gradlew assembleMockDebug assembleProdDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/consorciotransparente/app/util/Period.kt \
        app/src/main/java/com/consorciotransparente/app/data/repository/PaymentRepository.kt \
        app/src/main/java/com/consorciotransparente/app/data/repository/FirebasePaymentRepository.kt \
        app/src/main/java/com/consorciotransparente/app/data/repository/mock/MockPaymentRepository.kt \
        app/src/main/java/com/consorciotransparente/app/data/repository/RepositoryProvider.kt \
        app/src/main/java/com/consorciotransparente/app/data/repository/mock/MockDataStore.kt
git commit -m "Agregar PaymentRepository (pago por transferencia)"
```

---

### Task 4: Config. del edificio — CBU/alias

**Files:**
- Modify: `app/src/main/res/layout/activity_building_config.xml`
- Modify: `app/src/main/java/com/consorciotransparente/app/ui/building/BuildingConfigActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `Building.cbu`/`Building.alias` (Task 1).

- [ ] **Step 1: Agregar dos strings a `strings.xml`**

```xml
<string name="building_cbu_hint">CBU para transferencias</string>
<string name="building_alias_hint">Alias para transferencias</string>
```

- [ ] **Step 2: Agregar dos `TextInputLayout`/`TextInputEditText` a `activity_building_config.xml`**

Mismo estilo que `inputName`/`inputAddress` ya existentes en ese layout: dos campos
nuevos `inputCbu` (`inputType="number"`) e `inputAlias` (texto libre), ubicados después
de `inputAddress` y antes de los switches de `chargesRent`/`chargesExpenses`.

- [ ] **Step 3: Wirear en `BuildingConfigActivity.kt`**

En `renderLoaded()`: `binding.inputCbu.setText(building.cbu)` y
`binding.inputAlias.setText(building.alias)`.
En `onSaveClicked()`: agregar `cbu = binding.inputCbu.text?.toString()?.trim().orEmpty()`
y `alias = binding.inputAlias.text?.toString()?.trim().orEmpty()` al `Building(...)` que
ya se construye ahí.

- [ ] **Step 4: Compilar**

Run: `./gradlew assembleMockDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Prueba manual (emulador, flavor mock)**

Instalar, loguearse como `admin@mock.com`, entrar a Más → Configuración del edificio,
cargar un CBU y un alias, guardar, volver a entrar y confirmar que quedaron los mismos
valores cargados (se guardaron en `MockDataStore.building`).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/layout/activity_building_config.xml \
        app/src/main/java/com/consorciotransparente/app/ui/building/BuildingConfigActivity.kt \
        app/src/main/res/values/strings.xml
git commit -m "Agregar CBU/alias a la configuración del edificio"
```

---

### Task 5: Pantalla Vecinos (Administrador asigna montos)

**Files:**
- Create: `app/src/main/java/com/consorciotransparente/app/ui/vecinos/VecinosActivity.kt`
- Create: `app/src/main/java/com/consorciotransparente/app/ui/vecinos/VecinosViewModel.kt`
- Create: `app/src/main/java/com/consorciotransparente/app/ui/vecinos/VecinoAdapter.kt`
- Create: `app/src/main/res/layout/activity_vecinos.xml`
- Create: `app/src/main/res/layout/item_vecino.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `RepositoryProvider.resident`, `RepositoryProvider.auth`, `RepositoryProvider.building` (Tasks 1-2), `Resource` (existente), patrón de back button (`ic_back`, `?attr/selectableItemBackgroundBorderless`, ya usado en 11 pantallas).
- Produces: `data class VecinoRow(val user: User, val showRent: Boolean, val showExpenses: Boolean)` (package `ui.vecinos`), `class VecinosViewModel(...) { val state: LiveData<Resource<List<VecinoRow>>>; val saveState: LiveData<Resource<Unit>>; fun load(); fun saveAmounts(uid: String, rentAmount: Double, expensesAmount: Double) }`.

- [ ] **Step 1: Agregar strings**

```xml
<string name="vecinos_menu_item">👥 Vecinos</string>
<string name="vecinos_title">Vecinos y montos</string>
<string name="vecinos_empty">Todavía no hay inquilinos asignados a este edificio.</string>
<string name="vecinos_rent_hint">Monto de alquiler</string>
<string name="vecinos_expenses_hint">Monto de expensas</string>
<string name="vecinos_save">Guardar</string>
<string name="vecinos_saved">Monto actualizado.</string>
```

- [ ] **Step 2: Crear `VecinosViewModel.kt`**

```kotlin
package com.consorciotransparente.app.ui.vecinos

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.consorciotransparente.app.data.model.User
import com.consorciotransparente.app.data.repository.AuthRepository
import com.consorciotransparente.app.data.repository.BuildingRepository
import com.consorciotransparente.app.data.repository.RepositoryProvider
import com.consorciotransparente.app.data.repository.ResidentRepository
import com.consorciotransparente.app.util.Resource
import kotlinx.coroutines.launch

data class VecinoRow(val user: User, val showRent: Boolean, val showExpenses: Boolean)

class VecinosViewModel(
    private val residentRepo: ResidentRepository = RepositoryProvider.resident,
    private val authRepo: AuthRepository = RepositoryProvider.auth,
    private val buildingRepo: BuildingRepository = RepositoryProvider.building
) : ViewModel() {
    private val _state = MutableLiveData<Resource<List<VecinoRow>>>()
    val state: LiveData<Resource<List<VecinoRow>>> = _state

    private val _saveState = MutableLiveData<Resource<Unit>>()
    val saveState: LiveData<Resource<Unit>> = _saveState

    private var buildingId = ""

    fun load() {
        _state.value = Resource.Loading
        viewModelScope.launch {
            try {
                val user = authRepo.fetchCurrentUserProfile() ?: error("No se encontró tu perfil.")
                buildingId = user.buildingId
                val building = buildingRepo.getBuilding(buildingId)
                val residents = residentRepo.listResidents(buildingId)
                val rows = residents.map { VecinoRow(it, building?.chargesRent ?: false, building?.chargesExpenses ?: false) }
                _state.value = Resource.Success(rows)
            } catch (e: Exception) {
                _state.value = Resource.Error(e.message ?: "No se pudieron cargar los vecinos.")
            }
        }
    }

    fun saveAmounts(uid: String, rentAmount: Double, expensesAmount: Double) {
        _saveState.value = Resource.Loading
        viewModelScope.launch {
            try {
                residentRepo.updateAmounts(buildingId, uid, rentAmount, expensesAmount)
                _saveState.value = Resource.Success(Unit)
            } catch (e: Exception) {
                _saveState.value = Resource.Error(e.message ?: "No se pudo guardar el monto.")
            }
        }
    }
}
```

- [ ] **Step 3: Crear `item_vecino.xml`**

Tarjeta (`bg_card`) con: nombre + email del vecino arriba; debajo, una fila horizontal
con dos `TextInputLayout.OutlinedBox` chicos (monto alquiler, monto expensas —
visibilidad controlada en el adapter según `showRent`/`showExpenses`) y un botón
pequeño "Guardar" a la derecha (estilo `android.R.attr.buttonStyleSmall`, como en
`ReservasActivity.buildReservationRow`).

- [ ] **Step 4: Crear `VecinoAdapter.kt`**

```kotlin
package com.consorciotransparente.app.ui.vecinos

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.consorciotransparente.app.databinding.ItemVecinoBinding

class VecinoAdapter(
    private val onSave: (uid: String, rentAmount: Double, expensesAmount: Double) -> Unit
) : ListAdapter<VecinoRow, VecinoAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<VecinoRow>() {
            override fun areItemsTheSame(oldItem: VecinoRow, newItem: VecinoRow) = oldItem.user.uid == newItem.user.uid
            override fun areContentsTheSame(oldItem: VecinoRow, newItem: VecinoRow) = oldItem == newItem
        }
    }

    inner class ViewHolder(val binding: ItemVecinoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemVecinoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val row = getItem(position)
        // bind: nombre/email, visibilidad de los dos inputs según row.showRent/showExpenses,
        // precargar los montos actuales (row.user.rentAmount/expensesAmount), y en el click
        // del botón guardar: onSave(row.user.uid, parsear input alquiler, parsear input expensas)
    }
}
```

(El cuerpo exacto de `onBindViewHolder` queda a criterio del implementador — la firma y
los nombres de campo a usar ya están fijados arriba y en `item_vecino.xml`.)

- [ ] **Step 5: Crear `activity_vecinos.xml`**

`SwipeRefreshLayout` > `LinearLayout` (back button + título `vecinos_title` + mensaje
vacío `textEmpty` + `RecyclerView recyclerVecinos`) — mismo esqueleto que
`activity_tickets.xml`.

- [ ] **Step 6: Crear `VecinosActivity.kt`**

```kotlin
package com.consorciotransparente.app.ui.vecinos

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.consorciotransparente.app.R
import com.consorciotransparente.app.databinding.ActivityVecinosBinding
import com.consorciotransparente.app.ui.BaseActivity
import com.consorciotransparente.app.util.Resource

class VecinosActivity : BaseActivity() {
    private lateinit var binding: ActivityVecinosBinding
    private val viewModel: VecinosViewModel by lazy { ViewModelProvider(this)[VecinosViewModel::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVecinosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonBack.setOnClickListener { finish() }

        val adapter = VecinoAdapter { uid, rentAmount, expensesAmount ->
            viewModel.saveAmounts(uid, rentAmount, expensesAmount)
        }
        binding.recyclerVecinos.layoutManager = LinearLayoutManager(this)
        binding.recyclerVecinos.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { viewModel.load() }

        viewModel.state.observe(this) { state ->
            binding.swipeRefresh.isRefreshing = state is Resource.Loading
            if (state is Resource.Success) {
                adapter.submitList(state.data)
                binding.textEmpty.visibility = if (state.data.isEmpty()) View.VISIBLE else View.GONE
            }
        }
        viewModel.saveState.observe(this) { state ->
            if (state is Resource.Success) {
                // feedback simple (ej. Toast.makeText(this, R.string.vecinos_saved, Toast.LENGTH_SHORT).show())
            }
        }

        viewModel.load()
    }
}
```

- [ ] **Step 7: Registrar en `AndroidManifest.xml`**

```xml
<activity
    android:name=".ui.vecinos.VecinosActivity"
    android:exported="false" />
```

- [ ] **Step 8: Compilar**

Run: `./gradlew assembleMockDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/consorciotransparente/app/ui/vecinos/ \
        app/src/main/res/layout/activity_vecinos.xml \
        app/src/main/res/layout/item_vecino.xml \
        app/src/main/res/values/strings.xml \
        app/src/main/AndroidManifest.xml
git commit -m "Agregar pantalla Vecinos (asignar montos de alquiler/expensas)"
```

(La prueba manual de esta pantalla se hace en Task 8, una vez que tiene entrada desde
Más.)

---

### Task 6: Pantalla Pagar (Inquilino)

**Files:**
- Create: `app/src/main/java/com/consorciotransparente/app/ui/pagos/PagarActivity.kt`
- Create: `app/src/main/java/com/consorciotransparente/app/ui/pagos/PagarViewModel.kt`
- Create: `app/src/main/res/layout/activity_pagar.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `RepositoryProvider.{auth,building,payment}` (Tasks 1-3), `currentPeriod()` (Task 3).
- Produces: `data class PagoUiState(val period: String, val breakdown: Map<String, Double>, val total: Double, val cbu: String, val alias: String, val payment: Payment?)` (package `ui.pagos`), `class PagarViewModel(...) { val state: LiveData<Resource<PagoUiState>>; val uploadState: LiveData<Resource<Unit>>; val history: LiveData<Resource<List<Payment>>>; fun load(); fun uploadReceipt(receipt: Uri); fun loadHistory() }`.

- [ ] **Step 1: Agregar strings**

```xml
<string name="pagar_menu_item">💳 Pagar alquiler/expensas</string>
<string name="pagar_title">Pagar este mes</string>
<string name="pagar_no_amount">Todavía no tenés un monto asignado. Consultá a tu Administrador.</string>
<string name="pagar_concept_rent">Alquiler</string>
<string name="pagar_concept_expenses">Expensas</string>
<string name="pagar_total">Total a pagar</string>
<string name="pagar_cbu">CBU: %1$s</string>
<string name="pagar_alias">Alias: %1$s</string>
<string name="pagar_copy_cbu">Copiar CBU</string>
<string name="pagar_copy_alias">Copiar alias</string>
<string name="pagar_copied">Copiado al portapapeles.</string>
<string name="pagar_attach_receipt">Adjuntar comprobante de transferencia</string>
<string name="pagar_pending_review">Tu comprobante está en revisión por el Administrador.</string>
<string name="pagar_rejected">Tu comprobante fue rechazado: %1$s. Subí uno nuevo.</string>
<string name="pagar_paid_on">Pagado el %1$s por %2$s.</string>
<string name="pagar_history">Ver historial de meses anteriores</string>
```

- [ ] **Step 2: Crear `PagarViewModel.kt`**

```kotlin
package com.consorciotransparente.app.ui.pagos

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.consorciotransparente.app.data.model.Payment
import com.consorciotransparente.app.data.repository.AuthRepository
import com.consorciotransparente.app.data.repository.BuildingRepository
import com.consorciotransparente.app.data.repository.PaymentRepository
import com.consorciotransparente.app.data.repository.RepositoryProvider
import com.consorciotransparente.app.util.Resource
import com.consorciotransparente.app.util.currentPeriod
import kotlinx.coroutines.launch

data class PagoUiState(
    val period: String,
    val breakdown: Map<String, Double>,
    val total: Double,
    val cbu: String,
    val alias: String,
    val payment: Payment?
)

class PagarViewModel(
    private val authRepo: AuthRepository = RepositoryProvider.auth,
    private val buildingRepo: BuildingRepository = RepositoryProvider.building,
    private val paymentRepo: PaymentRepository = RepositoryProvider.payment
) : ViewModel() {
    private val _state = MutableLiveData<Resource<PagoUiState>>()
    val state: LiveData<Resource<PagoUiState>> = _state

    private val _uploadState = MutableLiveData<Resource<Unit>>()
    val uploadState: LiveData<Resource<Unit>> = _uploadState

    private val _history = MutableLiveData<Resource<List<Payment>>>()
    val history: LiveData<Resource<List<Payment>>> = _history

    private var buildingId = ""
    private var uid = ""
    private val period = currentPeriod()

    fun load() {
        _state.value = Resource.Loading
        viewModelScope.launch {
            try {
                val user = authRepo.fetchCurrentUserProfile() ?: error("No se encontró tu perfil.")
                buildingId = user.buildingId
                uid = user.uid
                if (buildingId.isBlank()) error("Tu usuario todavía no está asignado a un edificio.")

                val building = buildingRepo.getBuilding(buildingId)
                val breakdown = buildMap {
                    if (building?.chargesRent == true && user.rentAmount > 0.0) put("alquiler", user.rentAmount)
                    if (building?.chargesExpenses == true && user.expensesAmount > 0.0) put("expensas", user.expensesAmount)
                }
                val payment = paymentRepo.getCurrentPayment(buildingId, uid, period)

                _state.value = Resource.Success(
                    PagoUiState(
                        period = period,
                        breakdown = breakdown,
                        total = breakdown.values.sum(),
                        cbu = building?.cbu.orEmpty(),
                        alias = building?.alias.orEmpty(),
                        payment = payment
                    )
                )
            } catch (e: Exception) {
                _state.value = Resource.Error(e.message ?: "No se pudo cargar tu pago.")
            }
        }
    }

    fun uploadReceipt(receipt: Uri) {
        val current = (state.value as? Resource.Success)?.data ?: return
        _uploadState.value = Resource.Loading
        viewModelScope.launch {
            try {
                paymentRepo.uploadReceipt(buildingId, uid, period, current.breakdown, receipt)
                _uploadState.value = Resource.Success(Unit)
                load()
            } catch (e: Exception) {
                _uploadState.value = Resource.Error(e.message ?: "No se pudo subir el comprobante.")
            }
        }
    }

    fun loadHistory() {
        _history.value = Resource.Loading
        viewModelScope.launch {
            try {
                _history.value = Resource.Success(paymentRepo.listMyPayments(buildingId, uid).filter { it.period != period })
            } catch (e: Exception) {
                _history.value = Resource.Error(e.message ?: "No se pudo cargar el historial.")
            }
        }
    }
}
```

(`loadHistory` filtra el período actual porque ese ya se ve arriba, en `sectionPaid`;
el historial es solo "meses anteriores", tal como dice la spec.)

- [ ] **Step 3: Crear `activity_pagar.xml`**

`ScrollView` > `LinearLayout` (padding 20dp, como el resto de las pantallas
secundarias): back button, título, desglose (una fila por concepto presente en
`breakdown`, más una fila de total en negrita), y un contenedor `paymentSection` que
cambia de contenido según el estado — en XML alcanza con declarar los tres bloques
(`sectionPending` con CBU/alias + botones copiar + "Adjuntar comprobante";
`sectionReviewing` con el texto de revisión; `sectionPaid` con los datos del
comprobante y un botón `buttonHistory` (`@string/pagar_history`)) y toggle de
visibilidad en la Activity, igual que `TicketDetailActivity.render()` ya hace con sus
secciones por estado.

- [ ] **Step 4: Crear `PagarActivity.kt`**

```kotlin
package com.consorciotransparente.app.ui.pagos

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import com.consorciotransparente.app.R
import com.consorciotransparente.app.data.model.PaymentStatus
import com.consorciotransparente.app.databinding.ActivityPagarBinding
import com.consorciotransparente.app.ui.BaseActivity
import com.consorciotransparente.app.util.Resource

class PagarActivity : BaseActivity() {
    private lateinit var binding: ActivityPagarBinding
    private val viewModel: PagarViewModel by lazy { ViewModelProvider(this)[PagarViewModel::class.java] }

    private val pickReceipt = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.uploadReceipt(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPagarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonAttachReceipt.setOnClickListener { pickReceipt.launch("image/*") }
        binding.buttonCopyCbu.setOnClickListener { copyToClipboard(binding.textCbu.text.toString()) }
        binding.buttonCopyAlias.setOnClickListener { copyToClipboard(binding.textAlias.text.toString()) }
        binding.buttonHistory.setOnClickListener { viewModel.loadHistory() }

        viewModel.history.observe(this) { state ->
            if (state is Resource.Success) {
                // AlertDialog.Builder simple listando "period — $amount (method)" por fila,
                // o el texto "Todavía no tenés pagos de meses anteriores." si está vacío.
            }
        }

        viewModel.state.observe(this) { state ->
            binding.progressBar.visibility = if (state is Resource.Loading) View.VISIBLE else View.GONE
            if (state is Resource.Success) render(state.data)
            if (state is Resource.Error) { /* mostrar binding.textError, mismo patrón que el resto de las pantallas */ }
        }
        viewModel.uploadState.observe(this) { state ->
            binding.buttonAttachReceipt.isEnabled = state !is Resource.Loading
        }

        viewModel.load()
    }

    private fun render(data: PagoUiState) {
        // armar las filas de desglose en un contenedor LinearLayout (igual a como
        // VoteDetailActivity arma filas dinámicas), setear textCbu/textAlias, y
        // mostrar sectionPending / sectionReviewing / sectionPaid según:
        //   data.total <= 0.0                              -> ninguna sección, mensaje R.string.pagar_no_amount
        //   data.payment == null || status == RECHAZADO     -> sectionPending (+ motivo de rechazo si corresponde)
        //   status == A_VERIFICAR                           -> sectionReviewing
        //   status == PAGADO                                -> sectionPaid
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("CBU/alias", text))
    }
}
```

- [ ] **Step 5: Registrar en `AndroidManifest.xml`**

```xml
<activity
    android:name=".ui.pagos.PagarActivity"
    android:exported="false" />
```

- [ ] **Step 6: Compilar**

Run: `./gradlew assembleMockDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/consorciotransparente/app/ui/pagos/PagarActivity.kt \
        app/src/main/java/com/consorciotransparente/app/ui/pagos/PagarViewModel.kt \
        app/src/main/res/layout/activity_pagar.xml \
        app/src/main/res/values/strings.xml \
        app/src/main/AndroidManifest.xml
git commit -m "Agregar pantalla Pagar (transferencia + comprobante)"
```

(Prueba manual en Task 8, con la entrada desde Más ya armada — incluye ahí los dos
casos del Review Focus: monto en $0 y re-subida tras un rechazo.)

---

### Task 7: Panel de cobros (Administrador)

**Files:**
- Create: `app/src/main/java/com/consorciotransparente/app/ui/pagos/CobrosActivity.kt`
- Create: `app/src/main/java/com/consorciotransparente/app/ui/pagos/CobrosViewModel.kt`
- Create: `app/src/main/java/com/consorciotransparente/app/ui/pagos/CobroAdapter.kt`
- Create: `app/src/main/res/layout/activity_cobros.xml`
- Create: `app/src/main/res/layout/item_cobro.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `RepositoryProvider.{auth,resident,payment}` (Tasks 1-3), `currentPeriod()` (Task 3), `Payment`/`PaymentStatus` (Task 1).
- Produces: `data class CobroRow(val user: User, val payment: Payment?)` (package `ui.pagos`), `class CobrosViewModel(...) { val state: LiveData<Resource<List<CobroRow>>>; val actionState: LiveData<Resource<Unit>>; fun load(); fun approve(paymentId: String); fun reject(paymentId: String, reason: String) }`.

- [ ] **Step 1: Agregar strings**

```xml
<string name="cobros_menu_item">📋 Panel de cobros</string>
<string name="cobros_title">Cobros de este mes</string>
<string name="cobros_status_paid">Pagado</string>
<string name="cobros_status_reviewing">A verificar</string>
<string name="cobros_status_pending">Pendiente</string>
<string name="cobros_approve">Aprobar</string>
<string name="cobros_reject">Rechazar</string>
<string name="cobros_reject_reason_hint">Motivo del rechazo</string>
```

- [ ] **Step 2: Crear `CobrosViewModel.kt`**

```kotlin
package com.consorciotransparente.app.ui.pagos

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.consorciotransparente.app.data.model.User
import com.consorciotransparente.app.data.repository.AuthRepository
import com.consorciotransparente.app.data.repository.PaymentRepository
import com.consorciotransparente.app.data.repository.RepositoryProvider
import com.consorciotransparente.app.data.repository.ResidentRepository
import com.consorciotransparente.app.data.model.Payment
import com.consorciotransparente.app.util.Resource
import com.consorciotransparente.app.util.currentPeriod
import kotlinx.coroutines.launch

data class CobroRow(val user: User, val payment: Payment?)

class CobrosViewModel(
    private val authRepo: AuthRepository = RepositoryProvider.auth,
    private val residentRepo: ResidentRepository = RepositoryProvider.resident,
    private val paymentRepo: PaymentRepository = RepositoryProvider.payment
) : ViewModel() {
    private val _state = MutableLiveData<Resource<List<CobroRow>>>()
    val state: LiveData<Resource<List<CobroRow>>> = _state

    private val _actionState = MutableLiveData<Resource<Unit>>()
    val actionState: LiveData<Resource<Unit>> = _actionState

    private var buildingId = ""
    private val period = currentPeriod()

    fun load() {
        _state.value = Resource.Loading
        viewModelScope.launch {
            try {
                val admin = authRepo.fetchCurrentUserProfile() ?: error("No se encontró tu perfil.")
                buildingId = admin.buildingId
                val residents = residentRepo.listResidents(buildingId)
                val payments = paymentRepo.listPaymentsForBuilding(buildingId, period)
                val rows = residents.map { resident ->
                    CobroRow(resident, payments.firstOrNull { it.uid == resident.uid })
                }
                _state.value = Resource.Success(rows)
            } catch (e: Exception) {
                _state.value = Resource.Error(e.message ?: "No se pudo cargar el panel de cobros.")
            }
        }
    }

    fun approve(paymentId: String) {
        _actionState.value = Resource.Loading
        viewModelScope.launch {
            try {
                val adminUid = authRepo.currentUid ?: error("Tenés que iniciar sesión de nuevo.")
                paymentRepo.approvePayment(buildingId, paymentId, adminUid)
                _actionState.value = Resource.Success(Unit)
                load()
            } catch (e: Exception) {
                _actionState.value = Resource.Error(e.message ?: "No se pudo aprobar el pago.")
            }
        }
    }

    fun reject(paymentId: String, reason: String) {
        _actionState.value = Resource.Loading
        viewModelScope.launch {
            try {
                paymentRepo.rejectPayment(buildingId, paymentId, reason)
                _actionState.value = Resource.Success(Unit)
                load()
            } catch (e: Exception) {
                _actionState.value = Resource.Error(e.message ?: "No se pudo rechazar el pago.")
            }
        }
    }
}
```

- [ ] **Step 3: Crear `item_cobro.xml`**

Tarjeta (`bg_card`) con nombre del vecino + tag de estado (`bg_tag_pill`, colores
`tag_green_*`/`tag_yellow_*`/`tag_red_*` según PAGADO/A_VERIFICAR/pendiente, mismo
patrón que `TicketDetailActivity` usa para sus tags de estado).

- [ ] **Step 4: Crear `CobroAdapter.kt`**

`ListAdapter<CobroRow, CobroAdapter.ViewHolder>` con un callback
`onRowClick: (CobroRow) -> Unit` — solo relevante (clickeable) cuando
`row.payment?.status == PaymentStatus.A_VERIFICAR.name`, mismo patrón de
`ModerationAdapter` (callbacks `onApprove`/`onReject`, aunque acá el click abre el
diálogo de detalle en la Activity en vez de tener botones en la fila).

- [ ] **Step 5: Crear `activity_cobros.xml`**

Mismo esqueleto que `activity_vecinos.xml` (`SwipeRefreshLayout` + back + título +
`textEmpty` + `RecyclerView`).

- [ ] **Step 6: Crear `CobrosActivity.kt`**

Al hacer click en una fila `A_VERIFICAR`: `AlertDialog.Builder` con la imagen del
comprobante (`Glide.with(this).load(row.payment.receiptUrl).into(imageView)`, imageView
armado por código como en `ReservasActivity.buildReservationRow`) y dos botones:
"Aprobar" (`viewModel.approve(row.payment.id)`) y "Rechazar" (abre un segundo diálogo
chico con un `EditText` para el motivo, luego `viewModel.reject(id, reason)`) — mismo
patrón de diálogos encadenados que `ReservasActivity.confirmSlot`.

- [ ] **Step 7: Registrar en `AndroidManifest.xml`**

```xml
<activity
    android:name=".ui.pagos.CobrosActivity"
    android:exported="false" />
```

- [ ] **Step 8: Compilar**

Run: `./gradlew assembleMockDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/consorciotransparente/app/ui/pagos/CobrosActivity.kt \
        app/src/main/java/com/consorciotransparente/app/ui/pagos/CobrosViewModel.kt \
        app/src/main/java/com/consorciotransparente/app/ui/pagos/CobroAdapter.kt \
        app/src/main/res/layout/activity_cobros.xml \
        app/src/main/res/layout/item_cobro.xml \
        app/src/main/res/values/strings.xml \
        app/src/main/AndroidManifest.xml
git commit -m "Agregar panel de cobros (aprobar/rechazar comprobantes)"
```

---

### Task 8: Navegación (Más + Dashboard) y roadmap

**Files:**
- Modify: `app/src/main/res/layout/fragment_mas.xml`
- Modify: `app/src/main/java/com/consorciotransparente/app/ui/mas/MasFragment.kt`
- Modify: `app/src/main/res/layout/fragment_dashboard.xml`
- Modify: `app/src/main/java/com/consorciotransparente/app/ui/dashboard/DashboardFragment.kt`
- Modify: `docs/roadmap.md`

**Interfaces:**
- Consumes: `VecinosActivity`, `PagarActivity`, `CobrosActivity` (Tasks 5-7), `Role` (existente).

- [ ] **Step 1: Agregar tres filas a `fragment_mas.xml`**

Mismo patrón que `itemTickets`/`itemReservas` ya existentes (`TextView` clickeable,
`bg_card`): `itemVecinos` (texto `@string/vecinos_menu_item`), `itemPagar` (texto
`@string/pagar_menu_item`), `itemCobros` (texto `@string/cobros_menu_item`).
`itemVecinos` e `itemCobros` arrancan con `android:visibility="gone"` (solo
Administrador, igual que `itemBuildingConfig`); `itemPagar` siempre visible.

- [ ] **Step 2: Wirear clicks y visibilidad en `MasFragment.kt`**

En el bloque de `setOnClickListener` (junto a `itemTickets`/`itemReservas`):
`itemVecinos` → `startActivity(Intent(requireContext(), VecinosActivity::class.java))`;
`itemPagar` → `PagarActivity::class.java`; `itemCobros` → `CobrosActivity::class.java`.

En `onResume()`, junto al cálculo existente de `showBuildingConfig`: agregar
`val isAdmin = user?.roleEnum() == Role.ADMINISTRADOR` y setear
`binding.itemVecinos.visibility` / `binding.itemCobros.visibility` según `isAdmin`
(gone para Encargado e Inquilino — a diferencia de Tickets, que si ven Encargado y
Administrador).

- [ ] **Step 3: Hacer clickeable la tarjeta de expensas en `fragment_dashboard.xml`**

Agregar `android:id="@+id/cardExpenses"` a la `LinearLayout` (`style="@style/MetricCard"
android:layout_marginEnd="4dp"`) que contiene `metricExpensesValue`, más
`android:clickable="true"` y `android:focusable="true"` (y
`android:foreground="?attr/selectableItemBackground"` para el feedback táctil).

- [ ] **Step 4: Wirear el click en `DashboardFragment.kt`**

En `onViewCreated`, junto a los listeners del modo simple ya existentes:
```kotlin
binding.cardExpenses.setOnClickListener {
    startActivity(Intent(requireContext(), PagarActivity::class.java))
}
```
(import `com.consorciotransparente.app.ui.pagos.PagarActivity`).

- [ ] **Step 5: Actualizar `docs/roadmap.md`**

Marcar HU-03 como "🟡 parcial (pago por transferencia; Mercado Pago en plan aparte)" en
la tabla de estado, mismo estilo que las demás filas.

- [ ] **Step 6: Compilar**

Run: `./gradlew assembleMockDebug assembleProdDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Prueba manual completa (emulador, flavor mock) — cubre el Review Focus**

1. Instalar `app-mock-debug.apk`, loguearse como `admin@mock.com`.
2. Más → Vecinos: confirmar que aparece `inquilino@mock.com`, asignarle un monto de
   expensas (ej. 15000) y guardar. Confirmar que Más → Panel de cobros muestra a ese
   vecino en rojo ("Pendiente").
3. Cerrar sesión, entrar como `inquilino@mock.com`. Tocar la tarjeta "Expensas del mes"
   en Inicio (o Más → Pagar): debe mostrarse el monto de 15000 y los botones de pago.
4. Adjuntar cualquier imagen como comprobante. Confirmar que la pantalla pasa a "en
   revisión" y que **no** aparecen de nuevo los botones de pago.
5. Como `admin@mock.com` otra vez, Panel de cobros: la fila del vecino debe estar en
   amarillo ("A verificar"); tocarla, ver la imagen del comprobante, **Rechazar** con un
   motivo.
6. Como `inquilino@mock.com`: la pantalla Pagar debe volver a mostrar los botones de
   pago (no quedó trabado en "en revisión") junto con el motivo del rechazo.
7. Subir un comprobante de nuevo. Como admin, **Aprobar**. Confirmar que el panel de
   cobros lo muestra en verde ("Pagado") y que la pantalla Pagar del inquilino muestra
   el comprobante dentro de la app (fecha, monto, método).
8. Como `encargado@mock.com`: confirmar que Más **no** muestra "Vecinos" ni "Panel de
   cobros" (sí debe seguir viendo "Tickets de mantenimiento").
9. Como `admin@mock.com`, entrar a un vecino sin monto asignado (si quedó alguno) o
   crear este caso manualmente: la pantalla Pagar de ese vecino debe mostrar el aviso
   de "sin monto asignado", no los botones de pago ni un total en $0.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/res/layout/fragment_mas.xml \
        app/src/main/java/com/consorciotransparente/app/ui/mas/MasFragment.kt \
        app/src/main/res/layout/fragment_dashboard.xml \
        app/src/main/java/com/consorciotransparente/app/ui/dashboard/DashboardFragment.kt \
        docs/roadmap.md
git commit -m "Conectar navegación de Vecinos/Pagar/Panel de cobros"
```

