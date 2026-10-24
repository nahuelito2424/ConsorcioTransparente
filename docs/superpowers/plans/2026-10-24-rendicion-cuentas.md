# Rendición de Cuentas Descargable — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dar a los tres roles (Administrador, Encargado, Inquilino) una pantalla de
solo lectura que muestre, mes a mes, en qué se gastaron las expensas (desglose por
categoría + detalle por gasto + foto de comprobante), exportable a PDF, accesible
desde Pagar expensas y desde Más.

**Architecture:** Pantalla nueva `RendicionCuentasActivity` + `RendicionCuentasViewModel`
que reutiliza `ExpenseRepository.listExpenses(buildingId)` ya existente, filtra/agrupa
en el cliente (mismo criterio que ya usa la web en `/liquidacion`), y expone un
`RecyclerView` con dos tipos de fila (categoría / gasto individual). Un cambio previo,
independiente, convierte el campo `category` de `AddExpenseActivity` de texto libre a
un `Spinner` fijo (8 valores de `ExpenseCategory`) para que el desglose agrupe bien.
Exportar a PDF usa `android.graphics.pdf.PdfDocument` nativo + `FileProvider` (nuevo en
el Manifest) para compartir el archivo generado.

**Tech Stack:** Kotlin, Views + ViewBinding, `RecyclerView`, `Spinner` nativo,
`android.graphics.pdf.PdfDocument`, `FileProvider`, Glide (ya en el proyecto). Sin
dependencias nuevas.

**Spec:** [`docs/superpowers/specs/2026-10-24-rendicion-cuentas-design.md`](../specs/2026-10-24-rendicion-cuentas-design.md)

## Global Constraints

- **Sin tests automatizados**: CLAUDE.md documenta explícitamente que la app Android
  no tiene `test`/`androidTest` — la verificación de cada tarea es **compilar
  (`./gradlew assembleMockDebug`) + probar a mano en el flavor `mock`**, igual que
  todas las HU anteriores (ver `docs/roadmap.md`). Cada tarea reemplaza el ciclo
  RED→GREEN de TDD por: implementar → compilar → verificar a mano los pasos exactos
  que lista la tarea. No crear carpetas de test nuevas para esta feature.
- **Idioma**: comentarios de código y strings de UI en español (Argentina); nombres
  de código en inglés.
- **Sin capa de dominio ni DI**: cada ViewModel toma su Repository como default value
  del constructor (`= RepositoryProvider.expense`), igual que el resto del proyecto.
- **Sin dependencias nuevas**: PDF con `PdfDocument` nativo (no iText ni librerías de
  terceros).
- **Sin cambios en `firestore.rules`**: `expenses` ya es legible por cualquier
  miembro del edificio (`allow read: if belongsToBuilding(buildingId);`); `category`
  sigue siendo un `String` libre a nivel de esquema — la restricción a 8 valores es
  sólo de UI (Spinner), igual que ya es sólo de UI en la web.
- **Sin restricción de rol**: la pantalla nueva y el ítem de menú en Más son visibles
  para Administrador, Encargado e Inquilino por igual (no se oculta con
  `view.visibility` como sí pasa con `itemVecinos`/`itemCobros`, que son sólo-Admin).
- **Patrones a reusar, no reinventar**:
  - `Resource` (`util/Resource.kt`) para estados async.
  - `RecyclerView.Adapter` + `ViewBinding` + `submitList(newItems)` con
    `notifyDataSetChanged()` — mismo estilo que `ExpenseAdapter`/`CobroAdapter`, sin
    `ListAdapter`/`DiffUtil`.
  - `Spinner` nativo + `ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)`
    — mismo patrón que `ReservasActivity.setupSpinner()`, no un exposed dropdown de
    Material.
  - Diálogo con `ImageView` + Glide para ver una foto de comprobante — mismo patrón
    que `CobrosActivity` (`Glide.with(this).load(url).into(imageView)` dentro de un
    `AlertDialog` con `LinearLayout`).
- **FileProvider authority**: `com.consorciotransparente.app.fileprovider` (viene de
  `applicationId` + `.fileprovider`, patrón estándar de Android).
- **Período**: formato `"yyyy-MM"`, mismo que `util/Period.kt` (`currentPeriod()`) y
  que `Payment`/`Liquidacion` en el resto del proyecto.

## Review Focus

- **Mes sin ningún gasto** (ni VERIFICADO ni BLOQUEADO): la pantalla debe mostrar un
  estado vacío claro, sin "$0" que parezca un error ni crash — cubierto en Task 2.
- **Mes con sólo gastos BLOQUEADOS** (total verificado = $0 pero sí hay gastos
  pendientes de comprobante): el aviso de bloqueados debe aparecer igual aunque el
  total mostrado sea $0 — es un caso distinto del anterior y fácil de confundir si el
  total en $0 se trata como "no hay nada que mostrar" — cubierto en Task 2.
- **Usuario sin `buildingId` asignado**: la pantalla no debe crashear; mismo mensaje
  de error ya usado en `PagarViewModel` ("Tu usuario todavía no está asignado a un
  edificio.") — cubierto en Task 2.
- **Generar el PDF de un período sin gastos**: no debe crashear ni producir un
  archivo corrupto; genera igual un PDF con "$0" y sin filas de categoría — cubierto
  en Task 5.
- **Compartir el PDF sin ninguna app que maneje `ACTION_SEND` + `application/pdf`**
  (poco común, pero posible en un emulador pelado): no debe crashear con
  `ActivityNotFoundException` — cubierto en Task 5.

---

### Task 1: Categoría de gasto como menú fijo en Android

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/layout/activity_add_expense.xml`
- Modify: `app/src/main/java/com/consorciotransparente/app/ui/facturas/AddExpenseActivity.kt`

**Interfaces:**
- Produces: `string-array name="expense_categories"` en `strings.xml`, 8 entradas en
  el mismo orden que el enum `ExpenseCategory` (`REPUESTOS, MANO_DE_OBRA, PLOMERIA,
  ELECTRICIDAD, GAS, ALBANILERIA, LIMPIEZA, OTRO`), con estas etiquetas exactas:
  "Repuestos", "Mano de obra", "Plomería", "Electricidad", "Gas", "Albañilería",
  "Limpieza", "Otro". Lo consume Task 2 para las etiquetas de categoría en la pantalla
  nueva.

- [ ] **Step 1: Agregar el `string-array` a `strings.xml`**

```xml
<string-array name="expense_categories">
    <item>Repuestos</item>
    <item>Mano de obra</item>
    <item>Plomería</item>
    <item>Electricidad</item>
    <item>Gas</item>
    <item>Albañilería</item>
    <item>Limpieza</item>
    <item>Otro</item>
</string-array>
```

- [ ] **Step 2: Reemplazar el `TextInputEditText` de categoría por un `Spinner`**

En `activity_add_expense.xml`, dentro del mismo `TextInputLayout` que hoy envuelve a
`inputCategory` (líneas 46-57): sacar ese `TextInputLayout` wrapper (un `Spinner` no
va dentro de uno — no tiene sentido visual) y poner en su lugar un `Spinner` suelto
con `android:id="@+id/spinnerCategory"`, mismo `android:layout_marginTop="12dp"` que
tenía el `TextInputLayout` que reemplaza, y `android:background="@drawable/bg_card"`
(mismo fondo que usa `spinnerSpace` en `activity_reservas.xml`).

- [ ] **Step 3: Cablear el `Spinner` en `AddExpenseActivity.kt`**

En `onCreate`, antes del listener de `buttonSave`:

```kotlin
val categoryValues = ExpenseCategory.entries.map { it.name }
binding.spinnerCategory.adapter = ArrayAdapter(
    this, android.R.layout.simple_spinner_dropdown_item, resources.getStringArray(R.array.expense_categories)
)
```

El gasto se guarda con el `.name` del enum (no la etiqueta en español): reemplazar la
línea `category = binding.inputCategory.text?.toString().orEmpty()` por
`category = categoryValues[binding.spinnerCategory.selectedItemPosition]`. Agregar el
import de `ExpenseCategory` (`com.consorciotransparente.app.data.model.ExpenseCategory`)
y de `android.widget.ArrayAdapter`.

- [ ] **Step 4: Compilar**

Run: `./gradlew assembleMockDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Verificar a mano (emulador, flavor mock)**

Loguear como `admin@mock.com` (cualquier contraseña) → Facturas → "Agregar gasto". El
campo de categoría es ahora un desplegable con las 8 etiquetas en español, no un
campo de texto. Elegir "Plomería", completar descripción/monto, adjuntar una foto
(requisito ya existente para habilitar "Guardar"), guardar. El nuevo gasto aparece en
la lista de Facturas con estado "Verificado" — confirma que `createExpense` siguió
funcionando con el valor que manda el `Spinner`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/layout/activity_add_expense.xml app/src/main/java/com/consorciotransparente/app/ui/facturas/AddExpenseActivity.kt
git commit -m "Categoría de gasto como menú fijo en vez de texto libre"
```

---

### Task 2: Pantalla "Rendición de cuentas" — desglose por categoría (sin detalle ni PDF)

**Files:**
- Create: `app/src/main/java/com/consorciotransparente/app/ui/rendicion/RendicionCuentasViewModel.kt`
- Create: `app/src/main/java/com/consorciotransparente/app/ui/rendicion/RendicionCuentasActivity.kt`
- Create: `app/src/main/res/layout/activity_rendicion_cuentas.xml`
- Modify: `app/src/main/java/com/consorciotransparente/app/util/Period.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/layout/fragment_mas.xml`
- Modify: `app/src/main/java/com/consorciotransparente/app/ui/mas/MasFragment.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `ExpenseRepository.listExpenses(buildingId): List<Expense>` (ya existe).
  `R.array.expense_categories` (Task 1).
- Produces:
  - `fun shiftPeriod(period: String, delta: Int): String` en `util/Period.kt` — mismo
    contrato que `shiftPeriod` de la web (`web/src/components/PeriodPicker.tsx`): usa
    el día 15 del mes para no caer en un día inexistente al sumar/restar.
  - `data class RendicionUiState(val period: String, val totalVerificado: Double, val categoryTotals: List<Pair<ExpenseCategory, Double>>, val bloqueadoCount: Int, val bloqueadoTotal: Double, val expensesByCategory: Map<ExpenseCategory, List<Expense>>)`
    en `RendicionCuentasViewModel.kt` — `categoryTotals` sólo incluye categorías con
    total > 0, en el orden del enum. `expensesByCategory` lo consume Task 3 (no se usa
    todavía en esta tarea, pero lo calcula el mismo filtrado para no repetirlo).
  - `RendicionCuentasActivity` se lanza sin extras (default: mes actual) o con un
    extra `"period"` (String, `"yyyy-MM"`) — lo consume Task 4.

- [ ] **Step 1: Agregar `shiftPeriod` a `util/Period.kt`**

```kotlin
/** Desplaza un período "yyyy-MM" delta meses (± ), cruzando años si corresponde. */
fun shiftPeriod(period: String, delta: Int): String {
    val (year, month) = period.split("-").map { it.toInt() }
    val calendar = Calendar.getInstance().apply {
        set(year, month - 1 + delta, 15) // día 15: no cae nunca en un día inexistente de otro mes
    }
    return SimpleDateFormat("yyyy-MM", Locale.US).format(calendar.time)
}
```

- [ ] **Step 2: Compilar y verificar `shiftPeriod` a mano**

Run: `./gradlew assembleMockDebug`
Expected: `BUILD SUCCESSFUL`. No hay test automatizado (ver Global Constraints); se
verifica indirectamente en el Step 5 de esta tarea, navegando meses en la pantalla.

- [ ] **Step 3: Implementar `RendicionCuentasViewModel`**

Constructor: `class RendicionCuentasViewModel(private val authRepo: AuthRepository = RepositoryProvider.auth, private val expenseRepo: ExpenseRepository = RepositoryProvider.expense) : ViewModel()`.
Expone `val state: LiveData<Resource<RendicionUiState>>` y `fun load(period: String)`.
`load`: obtiene el perfil con `authRepo.fetchCurrentUserProfile()` (si es `null` o
`buildingId.isBlank()`, `Resource.Error("Tu usuario todavía no está asignado a un
edificio.")`, igual que `PagarViewModel`); lista los gastos con
`expenseRepo.listExpenses(buildingId)`; filtra por `createdAt` dentro del `period`
dado (comparar formateando `createdAt` con `SimpleDateFormat("yyyy-MM")`, igual
criterio que usa el resto del proyecto para comparar períodos); separa por
`status == ExpenseStatus.VERIFICADO.name` / `BLOQUEADO.name`; agrupa los VERIFICADO
por `category` (parseando con `ExpenseCategory.valueOf(...)`, con fallback a `OTRO`
si el valor guardado es un texto libre histórico — ver "Limitaciones conocidas" del
diseño) sumando `amount`, ordenando por el orden del enum, descartando las categorías
en $0; calcula `bloqueadoCount`/`bloqueadoTotal` sobre los BLOQUEADO. Un mes sin
ningún gasto (ni VERIFICADO ni BLOQUEADO) da `totalVerificado = 0.0`,
`categoryTotals = emptyList()`, `bloqueadoCount = 0` — la Activity (Step 5) distingue
ese caso de "hay BLOQUEADOS pero verificado es 0".

- [ ] **Step 4: Implementar `RendicionCuentasActivity` + `activity_rendicion_cuentas.xml`**

Layout: `ScrollView` → `LinearLayout` vertical, mismo estilo que `activity_pagar.xml`
(`buttonBack` + título + `padding="20dp"`). Debajo del título: fila con dos
`ImageButton` (`‹`/`›`, reusar `ic_back` rotado 180° para `›`, o un ícono existente
equivalente — a criterio del implementador) + un `TextView` central con el mes en
español (`SimpleDateFormat("MMMM yyyy", esAr)`, con la primera letra en mayúscula).
Debajo: un `TextView` grande con el total verificado (`bg_metric_card`, mismo patrón
que `breakdownContainer` de Pagar). Debajo: un `TextView` de aviso (estilo
`tag_yellow_bg`/`tag_yellow_fg`, igual que `textReviewing` en Pagar) **visible sólo
si `bloqueadoCount > 0`**, independientemente de si `totalVerificado` es 0 o no (ver
Review Focus) — texto: `"+ $bloqueadoCount gasto(s) pendiente(s) de comprobante
($bloqueadoTotal formateado), no incluido(s) en este total."`. Debajo: un
`LinearLayout` vertical (`id="categoryContainer"`) donde la Activity agrega a mano un
`TextView` por cada entrada de `categoryTotals` (mismo patrón de `addView` ya usado en
`PagarActivity.render()` — sin `RecyclerView` todavía, eso es Task 3). Si
`categoryTotals` está vacío y `bloqueadoCount == 0`, mostrar un
`TextView` de estado vacío ("Todavía no hay gastos registrados este período.").
`ProgressBar` + `TextView` de error, mismo patrón que el resto de las Activities.
`onCreate` llama `viewModel.load(period)` con `period` = extra `"period"` del
`Intent` si vino, si no `currentPeriod()`; los botones `‹`/`›` llaman de nuevo a
`viewModel.load(shiftPeriod(currentPeriodMostrado, ±1))` actualizando el período
mostrado en el `TextView` central.

- [ ] **Step 5: Registrar la Activity y el ítem de menú en Más**

En `AndroidManifest.xml`, agregar junto a las demás:
```xml
<activity android:name=".ui.rendicion.RendicionCuentasActivity" android:exported="false" />
```
En `fragment_mas.xml`, agregar un `TextView` `id="itemRendicion"` con el mismo estilo
que `itemProtocolos` (sin `visibility="gone"` — visible para los tres roles), texto
nuevo `@string/rendicion_menu_item` = "📊 Rendición de cuentas". En `MasFragment.kt`,
agregar el `setOnClickListener` que lanza `RendicionCuentasActivity` (sin extras),
mismo patrón que `itemProtocolos`.

- [ ] **Step 6: Compilar**

Run: `./gradlew assembleMockDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Verificar a mano los 4 casos del Review Focus que cubre esta tarea**

Todo en el flavor `mock`, logueado como cualquiera de los tres roles
(`admin@mock.com`/`encargado@mock.com`/`inquilino@mock.com`):
1. Más → "Rendición de cuentas" → mes actual: total $45.000 (sólo `exp-1`,
   PLOMERIA), fila "Plomería: $45.000", y el aviso "+ 1 gasto pendiente de
   comprobante ($3.000), no incluido en este total" (por `exp-2`, BLOQUEADO).
2. Tocar ‹ para ir al mes anterior: no hay gastos ese mes en el seed → total $0, sin
   filas de categoría, sin aviso de bloqueados (`bloqueadoCount == 0` ahí), mensaje de
   estado vacío — confirma que "mes sin nada" y "mes con sólo bloqueados" se
   distinguen.
3. (Para el caso "sólo BLOQUEADOS con total $0": no hace falta un gasto real nuevo,
   alcanza con razonar sobre el código del Step 3 — si no se puede armar ese escenario
   fácil a mano con el seed existente, dejarlo verificado por lectura del código y
   anotarlo en el ledger.)

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/consorciotransparente/app/ui/rendicion/ app/src/main/res/layout/activity_rendicion_cuentas.xml app/src/main/java/com/consorciotransparente/app/util/Period.kt app/src/main/AndroidManifest.xml app/src/main/res/layout/fragment_mas.xml app/src/main/java/com/consorciotransparente/app/ui/mas/MasFragment.kt app/src/main/res/values/strings.xml
git commit -m "Pantalla Rendición de cuentas: desglose por categoría y aviso de bloqueados"
```

---

### Task 3: Detalle por gasto (tocar una categoría) + ver foto de comprobante

**Files:**
- Create: `app/src/main/java/com/consorciotransparente/app/ui/rendicion/RendicionCuentasAdapter.kt`
- Create: `app/src/main/res/layout/item_rendicion_categoria.xml`
- Create: `app/src/main/res/layout/item_rendicion_gasto.xml`
- Modify: `app/src/main/res/layout/activity_rendicion_cuentas.xml`
- Modify: `app/src/main/java/com/consorciotransparente/app/ui/rendicion/RendicionCuentasActivity.kt`

**Interfaces:**
- Consumes: `RendicionUiState.expensesByCategory` y `.categoryTotals` (Task 2).
- Produces: `RendicionCuentasAdapter` con `fun submitList(newItems: List<RendicionRow>)`
  (mismo patrón `notifyDataSetChanged()` que `ExpenseAdapter`), donde
  `sealed class RendicionRow { data class Categoria(...) ; data class Gasto(val expense: Expense) }`.

- [ ] **Step 1: Reemplazar `categoryContainer` por un `RecyclerView`**

En `activity_rendicion_cuentas.xml`, sacar el `LinearLayout id="categoryContainer"`
del Step 4 de la Task 2 y poner en su lugar un `RecyclerView id="recyclerCategorias"`
(`LinearLayoutManager`, se configura en la Activity).

- [ ] **Step 2: Implementar `RendicionCuentasAdapter`**

Dos `ViewHolder` (`viewType` por `is RendicionRow.Categoria` / `.Gasto`), igual
patrón que `ExpenseAdapter` (ViewBinding + `submitList`/`notifyDataSetChanged()`).
Fila de categoría (`item_rendicion_categoria.xml`): nombre + total + una flechita
que rota 180° según expandido/colapsado (mismo recurso `ic_back` rotado, o un ícono
nuevo simple a criterio del implementador); `onClick` llama a un callback
`onToggle: (ExpenseCategory) -> Unit` que recibe el adapter desde la Activity. Fila
de gasto (`item_rendicion_gasto.xml`): descripción, proveedor, monto, botón "Ver
comprobante"; `onClick` del botón llama a `onVerComprobante: (Expense) -> Unit`.

- [ ] **Step 3: Estado de expandido/colapsado en `RendicionCuentasActivity`**

Un `Set<ExpenseCategory>` (`var expandedCategories`) en la Activity (no en el
ViewModel — es puro estado de UI, no de datos). Al tocar una categoría, se
agrega/saca del set y se reconstruye la lista de `RendicionRow` que se manda a
`submitList`: por cada entrada de `categoryTotals`, una fila `Categoria`, seguida —
sólo si esa categoría está en `expandedCategories` — de una fila `Gasto` por cada
`Expense` en `expensesByCategory[esaCategoria]`.

- [ ] **Step 4: Ver foto de comprobante**

`onVerComprobante` abre un `AlertDialog` con un `ImageView` cargado con
`Glide.with(this).load(expense.receiptUrl).into(imageView)`, mismo patrón que
`CobrosActivity` (`LinearLayout` + `ImageView` de `240dp` de alto, `ScaleType.FIT_CENTER`,
`AlertDialog.Builder` con botón "Cerrar"). En el flavor mock, `receiptUrl` puede ser
una URI local (`content://...`) o el string fijo del seed (`"mock://receipt-1"`) —
Glide va a mostrar una imagen rota para ese string fijo, igual que ya pasa hoy en
Cobros con comprobantes de pago mock; no es un bug nuevo de esta tarea.

- [ ] **Step 5: Compilar**

Run: `./gradlew assembleMockDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Verificar a mano**

Rendición de cuentas, mes actual → tocar la fila "Plomería" → se expande mostrando
"Reparación de bomba de agua — Plomería del Sur — $45.000" con un botón "Ver
comprobante" → tocarlo → se abre el diálogo con la imagen (rota, ver Step 4, es
esperado en mock) → cerrar → tocar "Plomería" de nuevo → se colapsa.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/consorciotransparente/app/ui/rendicion/ app/src/main/res/layout/item_rendicion_categoria.xml app/src/main/res/layout/item_rendicion_gasto.xml app/src/main/res/layout/activity_rendicion_cuentas.xml
git commit -m "Detalle por gasto al expandir una categoría, con vista de comprobante"
```

---

### Task 4: Entrada desde Pagar expensas

**Files:**
- Modify: `app/src/main/res/layout/activity_pagar.xml`
- Modify: `app/src/main/java/com/consorciotransparente/app/ui/pagos/PagarActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `RendicionCuentasActivity`, extra `"period"` (Task 2).

- [ ] **Step 1: Agregar el link en `activity_pagar.xml`**

Debajo de `breakdownContainer` (antes de `textNoAmount`): un `TextView`
`id="textVerRendicion"` con `@string/pagar_ver_rendicion` = "🧾 Ver en qué se gastaron
las expensas", subrayado (`android:paint flags="underlineText"` o estilo de link ya
usado en algún lado del proyecto si existe uno — si no, ese flag alcanza), clickable.

- [ ] **Step 2: Cablear el click en `PagarActivity.kt`**

En `onCreate`:
```kotlin
binding.textVerRendicion.setOnClickListener {
    startActivity(Intent(this, RendicionCuentasActivity::class.java).putExtra("period", period))
}
```
`period` ya existe como variable privada del ViewModel (`PagarViewModel.period`,
`private`) — exponerla como `val currentPeriod: String get() = period` pública nueva
en `PagarViewModel`, o pasar directo `com.consorciotransparente.app.util.currentPeriod()`
desde la Activity (son el mismo valor; usar la función de utilidad directa es más
simple y evita tocar la visibilidad del ViewModel — preferir esta opción). El
`TextView` del link se muestra siempre que `render()` se ejecuta (no depende de si
hay o no un monto a pagar ese período).

- [ ] **Step 3: Compilar**

Run: `./gradlew assembleMockDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Verificar a mano**

Más → Pagar → aparece el link "Ver en qué se gastaron las expensas" → tocarlo → abre
Rendición de cuentas ya en el mes actual (mismo período que se está pagando).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/layout/activity_pagar.xml app/src/main/java/com/consorciotransparente/app/ui/pagos/PagarActivity.kt app/src/main/res/values/strings.xml
git commit -m "Link a Rendición de cuentas desde Pagar expensas"
```

---

### Task 5: Exportar a PDF y actualizar documentación

**Files:**
- Create: `app/src/main/java/com/consorciotransparente/app/ui/rendicion/RendicionPdfGenerator.kt`
- Create: `app/src/main/res/xml/file_paths.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/layout/activity_rendicion_cuentas.xml`
- Modify: `app/src/main/java/com/consorciotransparente/app/ui/rendicion/RendicionCuentasActivity.kt`
- Modify: `docs/roadmap.md`
- Modify: `docs/especificacion-funcional.md`

**Interfaces:**
- Consumes: `RendicionUiState` (Task 2), nombre del edificio (`BuildingRepository.getBuilding(buildingId)?.name`, ya existe, usado igual que en `PagarViewModel`).
- Produces: `fun generarPdf(context: Context, buildingName: String, period: String, state: RendicionUiState): File` en `RendicionPdfGenerator.kt`.

- [ ] **Step 1: Declarar el `FileProvider` en el Manifest**

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="com.consorciotransparente.app.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```
Reemplaza el comentario existente sobre FileProvider (líneas 6-11 de
`AndroidManifest.xml`, que hoy dice "si más adelante se agrega... hay que declarar
CAMERA + un FileProvider" — ese momento llegó, aunque por PDF, no por cámara).

- [ ] **Step 2: `res/xml/file_paths.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res-auto">
    <cache-path name="rendiciones" path="rendiciones/" />
</paths>
```

- [ ] **Step 3: Implementar `generarPdf`**

Usa `android.graphics.pdf.PdfDocument`: una página A4 (`PdfDocument.PageInfo.Builder(595, 842, 1)`,
puntos a 72dpi), dibuja con `Canvas` + `Paint`: encabezado (`buildingName`,
"Rendición de cuentas", `period` formateado igual que en la Activity), total
verificado, la línea de bloqueados si `bloqueadoCount > 0` (mismo texto que en
pantalla), y por cada categoría de `categoryTotals` su total seguido de sus gastos de
`expensesByCategory` (descripción · proveedor · monto, en texto — sin fotos, ver
diseño). Si el contenido no entra en una página, iniciar una página nueva
(`finishPage` + `startPage` con el mismo `PageInfo`) — paginación simple, sin
pretender un layout editorial. Pie: "Generado desde Consorcio Transparente el
{fecha de hoy, `dd/MM/yyyy`} — no reemplaza la rendición de cuentas formal del
edificio si existe una vía distinta." El archivo se guarda en
`File(context.cacheDir, "rendiciones").apply { mkdirs() }`, nombre
`"rendicion_${period}.pdf"`, se sobrescribe si ya existe (no hace falta versionar
archivos temporales). Un período sin ningún gasto genera igual el PDF, con "$0" y
sin filas de categoría (Review Focus) — no es un caso especial a bloquear.

- [ ] **Step 4: Botón "Descargar PDF" en la Activity**

Agregar el botón en `activity_rendicion_cuentas.xml` (mismo estilo que
`buttonAttachReceipt` en Pagar). En `RendicionCuentasActivity.kt`:
```kotlin
binding.buttonDescargarPdf.setOnClickListener {
    val file = RendicionPdfGenerator.generarPdf(this, buildingName, period, state)
    val uri = FileProvider.getUriForFile(this, "com.consorciotransparente.app.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        startActivity(Intent.createChooser(intent, "Compartir rendición de cuentas"))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, "No hay ninguna app instalada para compartir el PDF.", Toast.LENGTH_LONG).show()
    }
}
```
`buildingName` sale de `BuildingRepository.getBuilding(buildingId)?.name.orEmpty()` —
cargarlo junto con el resto del estado en el ViewModel (agregar a `RendicionUiState` o
guardarlo aparte en la Activity; a criterio del implementador, ambas son razonables).

- [ ] **Step 5: Compilar**

Run: `./gradlew assembleMockDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Verificar a mano**

Rendición de cuentas, mes actual → "Descargar PDF" → se abre el selector de
compartir de Android sin crashear. Elegir "Guardar en Archivos" (o equivalente del
emulador), abrir el PDF resultante con un visor y confirmar a ojo que tiene el
encabezado, el total, "Plomería: $45.000" y el aviso de 1 gasto bloqueado. Repetir en
un mes sin gastos (el del Step 7 de la Task 2) y confirmar que también genera el PDF,
con "$0" y sin filas, sin crashear.

- [ ] **Step 7: Actualizar documentación**

En `docs/roadmap.md`, agregar bajo "Próximos pasos" una entrada ✅ nueva (mismo
estilo que las demás) resumiendo qué se implementó y cómo se verificó. En
`docs/especificacion-funcional.md`, marcar el ítem 1 de "Sugerencias para versiones
futuras" como resuelto con el mismo criterio ya usado para el ítem 4 (`~~tachado~~` +
nota de qué pantalla lo implementa).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/consorciotransparente/app/ui/rendicion/RendicionPdfGenerator.kt app/src/main/res/xml/file_paths.xml app/src/main/AndroidManifest.xml app/src/main/res/layout/activity_rendicion_cuentas.xml app/src/main/java/com/consorciotransparente/app/ui/rendicion/RendicionCuentasActivity.kt docs/roadmap.md docs/especificacion-funcional.md
git commit -m "Exportar Rendición de cuentas a PDF y actualizar documentación"
```
