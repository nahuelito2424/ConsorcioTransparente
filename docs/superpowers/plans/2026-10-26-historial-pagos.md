# Historial de Pagos por Inquilino Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Una pantalla de solo lectura (`HistorialPagosActivity`), reutilizada para que
un inquilino vea su propio historial de pagos y para que el Administrador vea el de
cualquier vecino, mostrando los últimos 12 meses con los períodos sin `Payment`
marcados explícitamente como "Sin pago registrado".

**Architecture:** Una sola Activity+ViewModel parametrizada por `uid` (extra del
Intent), reutilizando `PaymentRepository.listMyPayments(buildingId, uid)` —ya acepta
cualquier uid, sin cambios de repositorio ni de `firestore.rules`—. El ViewModel
genera los 12 períodos con `shiftPeriod` (ya existe en `util/Period.kt`) y los cruza
con los `Payment` que haya. Dos puntos de entrada reapuntados: el botón "Ver
historial" que ya existe en Pagar (reemplaza su diálogo de texto plano), y un link
nuevo por fila en el Panel de cobros.

**Tech Stack:** Kotlin, Views + ViewBinding, `RecyclerView`, Glide (ya en el proyecto).
Sin dependencias nuevas, sin cambios de esquema ni de `firestore.rules`.

**Spec:** [`docs/superpowers/specs/2026-10-26-historial-pagos-design.md`](../specs/2026-10-26-historial-pagos-design.md)

## Global Constraints

- **Sin tests automatizados**: CLAUDE.md documenta que la app Android no tiene
  `test`/`androidTest`. Cada tarea sustituye el ciclo RED→GREEN de TDD por
  "implementar → `./gradlew assembleMockDebug` → verificar a mano en el flavor mock",
  mismo criterio que el plan anterior (Rendición de cuentas).
- **Idioma**: comentarios de código y strings de UI en español (Argentina); nombres de
  código en inglés.
- **Sin capa de dominio ni DI**: el ViewModel toma su Repository como default value del
  constructor (`= RepositoryProvider.payment`), igual que el resto del proyecto.
- **Sin dependencias ni cambios de esquema nuevos**: `PaymentRepository.listMyPayments`
  ya acepta cualquier `uid`; `firestore.rules`/`storage.rules` ya permiten a
  Admin/Encargado leer el pago (y su comprobante) de cualquier vecino del edificio —
  no se toca ninguno de los dos archivos.
- **Patrones a reusar, no reinventar**:
  - `Resource` (`util/Resource.kt`) para estados async.
  - `RecyclerView.Adapter` + `ViewBinding` + `submitList(newItems)` con
    `notifyDataSetChanged()` para el adapter **nuevo** (mismo patrón que
    `ExpenseAdapter`/`RendicionCuentasAdapter` — no el de `CobroAdapter`, que usa
    `ListAdapter`+`DiffUtil`; `CobroAdapter` ya existe y se modifica en su propio
    estilo, sin migrarlo).
  - Diálogo de comprobante con `ImageView` + Glide + `AlertDialog`, mismo patrón que
    `CobrosActivity.showReceiptDialog()` y `RendicionCuentasActivity.showReceiptDialog()`.
  - `shiftPeriod`/`currentPeriod` de `util/Period.kt` para generar los 12 períodos —
    no escribir aritmética de fechas nueva.
  - Tag-pill de color por estado, mismo patrón inline que ya usan `CobroAdapter`/
    `ExpenseAdapter` (`bg_tag_pill` + `background.setTint(...)` + `setTextColor(...)`).
- **"Sin pago registrado" en un tono neutro**, no en rojo: reusar los colores ya
  existentes `@color/surface_secondary` (fondo) y `@color/text_secondary` (texto) — no
  agregar `tag_gray_bg`/`tag_gray_fg` nuevos, ya alcanza con lo que hay.

## Review Focus

- **Un período de los 12 sin ningún `Payment`**: debe listarse como "Sin pago
  registrado" en tono neutro, no como error ni fila vacía silenciosa — cubierto en
  Task 2 (generación de las 12 filas) y Task 3 (render de la fila sin pago).
- **Usuario sin perfil o sin `buildingId`**: mismo mensaje de error ya usado en el
  resto de la app ("Tu usuario todavía no está asignado a un edificio."), sin crash —
  cubierto en Task 2.
- **Pago `A_VERIFICAR` por Mercado Pago** (`receiptUrl` vacío, a diferencia de
  transferencia): el botón "Ver comprobante" no debe aparecer si no hay nada que
  mostrar — cubierto en Task 3.
- **El Admin abre el historial de un vecino sin ningún pago en absoluto** (12 meses
  "Sin pago registrado" seguidos): no debe crashear ni verse como un error de carga,
  tiene que listarse igual, fila por fila — cubierto en Task 1 (datos de prueba, deja
  al menos un tramo así) y Task 3.
- **Título de la pantalla**: debe decir "Mi historial de pagos" cuando el `uid` del
  extra es el del usuario logueado, y "Historial de pagos — {nombre}" en cualquier
  otro caso (incluido un `authRepo.currentUid` nulo, que no debería pasar estando
  logueado pero no debe romper la comparación) — cubierto en Task 3.

---

### Task 1: Datos de prueba en MockDataStore

**Files:**
- Modify: `app/src/main/java/com/consorciotransparente/app/data/repository/mock/MockDataStore.kt`

**Interfaces:**
- Produces: `MockDataStore.payments` deja de estar vacío — 2 `Payment` de ejemplo para
  `inquilinoUser.uid`, en períodos pasados (no el actual), con un hueco intermedio sin
  documento a propósito. Ningún otro task depende del contenido exacto, sólo de que
  exista variedad (pagado + rechazado + un hueco) para poder verificar las tareas
  siguientes a mano.

- [ ] **Step 1: Agregar los imports que falten**

`PaymentMethod` y `PaymentStatus` de `com.consorciotransparente.app.data.model`, y
`currentPeriod`/`shiftPeriod` de `com.consorciotransparente.app.util` — ninguno de los
dos grupos está importado hoy en este archivo.

- [ ] **Step 2: Reemplazar `val payments = mutableListOf<Payment>()`**

```kotlin
val payments = mutableListOf(
    Payment(
        id = "${inquilinoUser.uid}_${shiftPeriod(currentPeriod(), -1)}",
        uid = inquilinoUser.uid,
        period = shiftPeriod(currentPeriod(), -1),
        amount = 50000.0,
        method = PaymentMethod.TRANSFERENCIA.name,
        status = PaymentStatus.PAGADO.name,
        receiptUrl = "mock://receipt-historial-pagado",
        verifiedAt = Date(),
        verifiedByUid = adminUser.uid
    ),
    Payment(
        id = "${inquilinoUser.uid}_${shiftPeriod(currentPeriod(), -3)}",
        uid = inquilinoUser.uid,
        period = shiftPeriod(currentPeriod(), -3),
        amount = 48000.0,
        method = PaymentMethod.TRANSFERENCIA.name,
        status = PaymentStatus.RECHAZADO.name,
        receiptUrl = "mock://receipt-historial-rechazado",
        rejectionReason = "El monto no coincide con lo asignado"
    )
)
```

El período `-2` (entre los dos) y todo lo anterior a `-3` quedan sin documento a
propósito — eso es lo que ejercita el caso "Sin pago registrado" al verificar a mano.

- [ ] **Step 3: Compilar**

Run: `./gradlew assembleMockDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/consorciotransparente/app/data/repository/mock/MockDataStore.kt
git commit -m "Sembrar pagos de prueba en meses pasados para el flavor mock"
```

---

### Task 2: `HistorialPagosViewModel` — las 12 filas

**Files:**
- Create: `app/src/main/java/com/consorciotransparente/app/ui/pagos/HistorialPagosViewModel.kt`

**Interfaces:**
- Consumes: `PaymentRepository.listMyPayments(buildingId, uid): List<Payment>` (ya
  existe). `AuthRepository.fetchCurrentUserProfile()` (ya existe, mismo patrón que
  `PagarViewModel`/`RendicionCuentasViewModel`).
- Produces: `data class HistorialRow(val period: String, val payment: Payment?)` y
  `class HistorialPagosViewModel { val state: LiveData<Resource<List<HistorialRow>>>; fun load(uid: String) }`
  — lo consume Task 3.

- [ ] **Step 1: Implementar `HistorialPagosViewModel`**

Constructor: `class HistorialPagosViewModel(private val authRepo: AuthRepository = RepositoryProvider.auth, private val paymentRepo: PaymentRepository = RepositoryProvider.payment) : ViewModel()`.
`fun load(uid: String)`: obtiene el perfil con `authRepo.fetchCurrentUserProfile()` (si
es `null` o `buildingId.isBlank()`, `Resource.Error("Tu usuario todavía no está
asignado a un edificio.")`, igual que `PagarViewModel`/`RendicionCuentasViewModel`);
pide `paymentRepo.listMyPayments(user.buildingId, uid)` (ojo: el `uid` es el parámetro
de `load`, el perfil cargado es el del usuario logueado — quien mira la pantalla, no
necesariamente el dueño del historial); arma un `Map<String, Payment>` por `period`
con `associateBy { it.period }`; genera los 12 períodos con
`(0..11).map { shiftPeriod(currentPeriod(), -it) }` (ya vienen en orden descendente,
más reciente primero — no hace falta ordenar después); mapea cada período a un
`HistorialRow(period, paymentsByPeriod[period])`.

- [ ] **Step 2: Compilar**

Run: `./gradlew assembleMockDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/consorciotransparente/app/ui/pagos/HistorialPagosViewModel.kt
git commit -m "ViewModel de Historial de pagos: 12 períodos cruzados con Payment"
```

---

### Task 3: `HistorialPagosActivity` + adapter + layout

**Files:**
- Create: `app/src/main/java/com/consorciotransparente/app/ui/pagos/HistorialPagosActivity.kt`
- Create: `app/src/main/java/com/consorciotransparente/app/ui/pagos/HistorialPagosAdapter.kt`
- Create: `app/src/main/res/layout/activity_historial_pagos.xml`
- Create: `app/src/main/res/layout/item_historial_pago.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `HistorialPagosViewModel` (Task 2). `R.string.rendicion_ver_comprobante`
  (ya existe, "Ver comprobante" — se reusa tal cual para el botón de cada fila).
- Produces: `HistorialPagosActivity` se lanza con dos extras: `"uid"` (String,
  obligatorio) y `"userName"` (String, el nombre a mostrar en el título si `uid` no es
  el del usuario logueado) — los consumen Task 4 y Task 5.

- [ ] **Step 1: Strings nuevos en `strings.xml`**

```xml
<string name="historial_title_self">Mi historial de pagos</string>
<string name="historial_title_other">Historial de pagos — %1$s</string>
<string name="historial_status_paid">Pagado</string>
<string name="historial_status_reviewing">A verificar</string>
<string name="historial_status_rejected">Rechazado</string>
<string name="historial_status_missing">Sin pago registrado</string>
<string name="historial_motivo_rechazo">Motivo: %1$s</string>
```

- [ ] **Step 2: `item_historial_pago.xml`**

`LinearLayout` vertical, mismo estilo de card que `item_cobro.xml`/
`item_rendicion_gasto.xml` (`bg_card`, `layout_marginBottom="8dp"`, `padding="14dp"`).
Fila superior horizontal: `textPeriod` (peso 1, el mes en español, p. ej. "Octubre
2026") + `textStatus` (tag pill, igual patrón que `item_cobro.xml`). Debajo:
`textAmountMethod` (monto + método, sólo visible si hay `Payment`), `textMotivo`
(sólo visible si `RECHAZADO` y `rejectionReason` no está vacío), `buttonVerComprobante`
(`Button` estilo `OutlinedButton`, sólo visible si hay `Payment` con `receiptUrl` no
vacío).

- [ ] **Step 3: `activity_historial_pagos.xml`**

`ScrollView` → `LinearLayout`, mismo patrón que `activity_rendicion_cuentas.xml`:
`buttonBack` + `textTitle` + `RecyclerView` (`id="recyclerHistorial"`,
`nestedScrollingEnabled="false"`) + `ProgressBar` + `textError`. Sin período ni
botones de navegación (acá el rango ya es fijo, los 12 meses).

- [ ] **Step 4: Implementar `HistorialPagosAdapter`**

`class HistorialPagosAdapter(private val onVerComprobante: (Payment) -> Unit) : RecyclerView.Adapter<HistorialPagosAdapter.ViewHolder>()`
con `fun submitList(newItems: List<HistorialRow>)` (patrón `notifyDataSetChanged()`,
ver Global Constraints). `onBindViewHolder`: el mes en español con
`SimpleDateFormat("MMMM yyyy", esAr)` sobre el `period` parseado (mismo cálculo que
`RendicionCuentasActivity.load()`, con `.replaceFirstChar { it.uppercase() }`). Si
`row.payment == null`: `textStatus` = "Sin pago registrado" con
`@color/surface_secondary`/`@color/text_secondary`; ocultar monto/motivo/botón. Si no:
estado según `row.payment.status` (`PAGADO`→verde, `A_VERIFICAR`→amarillo,
`RECHAZADO`→rojo, mismos colores que `CobroAdapter`); `textAmountMethod` con el monto
formateado + el método (`PaymentMethod.MERCADO_PAGO.name` → "Mercado Pago", si no →
"Transferencia" — mismo texto que `PagarActivity.methodLabel()`, duplicado inline acá,
no se extrae un helper compartido por dos líneas); `textMotivo` visible sólo si
`status == RECHAZADO.name && rejectionReason.isNotBlank()`; `buttonVerComprobante`
visible sólo si `receiptUrl.isNotBlank()` (cubre el caso Mercado Pago del Review
Focus, que no tiene comprobante propio) y su click llama `onVerComprobante(row.payment)`.

- [ ] **Step 5: Implementar `HistorialPagosActivity`**

Lee `uid = intent.getStringExtra("uid")!!` y `userName =
intent.getStringExtra("userName").orEmpty()` en `onCreate`. Título:
`binding.textTitle.text = if (uid == RepositoryProvider.auth.currentUid) getString(R.string.historial_title_self) else getString(R.string.historial_title_other, userName)`.
Configura el `RecyclerView` con `LinearLayoutManager` y el adapter de Task 4, pasando
`onVerComprobante` = un diálogo con `ImageView` + Glide + `AlertDialog`, mismo cuerpo
que `RendicionCuentasActivity.showReceiptDialog()`/`dpToPx()` (título = el período en
español de esa fila). Observa `viewModel.state` igual que el resto de las Activities
(`Resource.Loading` → `progressBar` visible; `Resource.Success` → `adapter.submitList`;
`Resource.Error` → `textError`). Llama `viewModel.load(uid)` al final de `onCreate`.

- [ ] **Step 6: Registrar la Activity en el Manifest**

```xml
<activity android:name=".ui.pagos.HistorialPagosActivity" android:exported="false" />
```

- [ ] **Step 7: Compilar**

Run: `./gradlew assembleMockDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/consorciotransparente/app/ui/pagos/HistorialPagosActivity.kt app/src/main/java/com/consorciotransparente/app/ui/pagos/HistorialPagosAdapter.kt app/src/main/res/layout/activity_historial_pagos.xml app/src/main/res/layout/item_historial_pago.xml app/src/main/AndroidManifest.xml app/src/main/res/values/strings.xml
git commit -m "Pantalla Historial de pagos: 12 meses, estado y comprobante por fila"
```

---

### Task 4: Reapuntar el botón de historial en Pagar

**Files:**
- Modify: `app/src/main/java/com/consorciotransparente/app/ui/pagos/PagarActivity.kt`
- Modify: `app/src/main/java/com/consorciotransparente/app/ui/pagos/PagarViewModel.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `HistorialPagosActivity`, extras `"uid"`/`"userName"` (Task 3).

- [ ] **Step 1: `PagarActivity.kt` — repuntar `buttonHistory`**

Reemplazar `binding.buttonHistory.setOnClickListener { viewModel.loadHistory() }` por:
```kotlin
binding.buttonHistory.setOnClickListener {
    startActivity(Intent(this, HistorialPagosActivity::class.java).putExtra("uid", authRepo.currentUid))
}
```
`PagarActivity` no tiene hoy una referencia directa a `AuthRepository` — usar
`com.consorciotransparente.app.data.repository.RepositoryProvider.auth.currentUid`
importado como `RepositoryProvider`, mismo patrón que el resto de las Activities que
no pasan por su ViewModel para esto. No hace falta `userName` (es el propio usuario,
cae en la rama "Mi historial de pagos" de la Task 3). Borrar el observer de
`viewModel.history` y el método privado `showHistory()` y `statusLabel()` (quedan sin
uso). Agregar el import de `HistorialPagosActivity`.

- [ ] **Step 2: `PagarViewModel.kt` — borrar el código muerto**

Borrar `_history`/`history`/`loadHistory()` enteros.

- [ ] **Step 3: `strings.xml` — borrar los strings que quedan sin uso**

Borrar `pagar_status_paid`, `pagar_status_reviewing`, `pagar_status_rejected`,
`pagar_history_empty`, `pagar_history_row` (verificar antes con un grep que ninguno se
usa en otro lado — ya se confirmó durante el diseño que son exclusivos de este
código). `pagar_history` (el texto del botón, "Ver historial de meses anteriores")
**se mantiene** — el botón sigue ahí, sólo cambia a dónde lleva.

- [ ] **Step 4: Compilar**

Run: `./gradlew assembleMockDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Verificar a mano**

Loguear como `inquilino@mock.com` → Pagar → "Ver historial de meses anteriores" → abre
"Mi historial de pagos" con 12 filas: la más reciente "Sin pago registrado", el mes
`-1` en verde "Pagado" con "Ver comprobante", `-2` "Sin pago registrado", `-3` en rojo
"Rechazado" con el motivo, el resto "Sin pago registrado". Tocar "Ver comprobante" del
mes pagado abre el diálogo con la imagen (rota, mismo caso ya documentado para
`mock://` en la Rendición de cuentas — no es un bug nuevo).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/consorciotransparente/app/ui/pagos/PagarActivity.kt app/src/main/java/com/consorciotransparente/app/ui/pagos/PagarViewModel.kt app/src/main/res/values/strings.xml
git commit -m "Pagar: el botón de historial abre la pantalla nueva, se borra el diálogo viejo"
```

---

### Task 5: Link "Ver historial" desde el Panel de cobros

**Files:**
- Modify: `app/src/main/res/layout/item_cobro.xml`
- Modify: `app/src/main/java/com/consorciotransparente/app/ui/pagos/CobroAdapter.kt`
- Modify: `app/src/main/java/com/consorciotransparente/app/ui/pagos/CobrosActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `HistorialPagosActivity`, extras `"uid"`/`"userName"` (Task 3).
- Produces: `CobroAdapter` pasa a recibir un segundo callback,
  `onVerHistorial: (CobroRow) -> Unit`, además del `onRowClick` que ya tenía.

- [ ] **Step 1: String nuevo en `strings.xml`**

```xml
<string name="cobros_ver_historial">Ver historial</string>
```

- [ ] **Step 2: `item_cobro.xml` — agregar el link**

Envolver el `LinearLayout` horizontal actual (`textName` + `textStatus`) dentro de un
`LinearLayout` vertical nuevo (mismo `bg_card`/`padding`/`layout_marginBottom` que hoy
tiene el horizontal — pasan al contenedor vertical). Debajo de esa fila, un `TextView`
`id="textVerHistorial"` con `@string/cobros_ver_historial`, clickable, estilo de link
(mismo criterio que `textVerRendicion` en `activity_pagar.xml`: color `@color/navy`,
subrayado por código en vez de `android:paintFlags`, que no es un atributo XML real —
ver el ruling de la Rendición de cuentas), alineado a la derecha
(`layout_gravity="end"`).

- [ ] **Step 3: `CobroAdapter.kt` — el segundo callback**

Agregar `onVerHistorial: (CobroRow) -> Unit` al constructor. En `onBindViewHolder`,
después de lo que ya hay:
```kotlin
binding.textVerHistorial.paintFlags = binding.textVerHistorial.paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
binding.textVerHistorial.setOnClickListener { onVerHistorial(row) }
```
No toca `binding.root.isClickable`/`setOnClickListener` existentes (siguen igual,
sólo activos para `isPendingTransfer`) — el nuevo botón es un target de toque aparte,
no compite con esa lógica.

- [ ] **Step 4: `CobrosActivity.kt` — pasar el callback y lanzar la Activity**

```kotlin
val adapter = CobroAdapter(
    onRowClick = { row -> showReceiptDialog(row) },
    onVerHistorial = { row ->
        startActivity(
            Intent(this, HistorialPagosActivity::class.java)
                .putExtra("uid", row.user.uid)
                .putExtra("userName", row.user.fullName)
        )
    }
)
```
Agregar los imports de `android.content.Intent` y `HistorialPagosActivity`.

- [ ] **Step 5: Compilar**

Run: `./gradlew assembleMockDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Verificar a mano**

Loguear como `admin@mock.com` → Panel de cobros → la fila de Inés Inquilina ahora
tiene un link "Ver historial" debajo del nombre/estado → tocarlo abre "Historial de
pagos — Inés Inquilina" (no "Mi historial...", porque el uid no es el del admin
logueado) con las mismas 12 filas que en la Task 4. Confirmar que tocar el resto de la
fila (fuera del link) sigue abriendo el diálogo de aprobar/rechazar sólo cuando
corresponde (transferencia pendiente), sin que el link lo interfiera.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/res/layout/item_cobro.xml app/src/main/java/com/consorciotransparente/app/ui/pagos/CobroAdapter.kt app/src/main/java/com/consorciotransparente/app/ui/pagos/CobrosActivity.kt app/src/main/res/values/strings.xml
git commit -m "Panel de cobros: link a Historial de pagos por vecino"
```
