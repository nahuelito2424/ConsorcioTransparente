# Diseño: Rendición de cuentas descargable (app Android)

> Spec de referencia: [`docs/especificacion-funcional.md`](../../especificacion-funcional.md)
> (sección 6 "Sugerencias para versiones futuras", ítem 1 — "Rendición de cuentas /
> balance descargable"). Arquitectura general del proyecto:
> [`docs/arquitectura.md`](../../arquitectura.md).

## Por qué

Con las 10 HU y la v1 de la web ya terminadas, el usuario eligió retomar el backlog de
ideas futuras. De las candidatas (rendición de cuentas, estado de cuenta por unidad,
libro de actas digital, fondo de reserva), esta es la que mejor encaja con el nombre
del proyecto y con una obligación legal real: la Ley 941 de CABA obliga al
Administrador a rendir cuentas de los gastos del consorcio, y hoy esa información
—los `Expense` con `status == VERIFICADO`— existe en Firestore pero ningún vecino
tiene una forma de verla agrupada ni de llevarse un comprobante fuera de la app.

## Alcance

### Incluye

1. **Pantalla nueva "Rendición de cuentas"**, para los tres roles (Administrador,
   Encargado, Inquilino) — es de solo lectura, y `expenses` ya es legible por
   cualquier miembro del edificio en `firestore.rules`, así que no hace falta ninguna
   regla nueva.
   - Selector de período mes a mes (flechas ‹ ›, como ya funciona `/liquidacion` en la
     web), default: mes actual.
   - Total gastado en el período (suma de `Expense.amount` con `status == VERIFICADO`
     y `createdAt` dentro del mes).
   - Desglose por categoría: una fila por cada una de las 8 categorías de
     `ExpenseCategory` con total > 0, en el orden fijo del enum, con su total.
   - Tocar una categoría expande la lista de gastos individuales de esa categoría en
     ese período: descripción, proveedor, monto, y un botón para ver la foto del
     comprobante (mismo patrón ya usado en `CobrosActivity` — diálogo con `ImageView` +
     Glide cargando `receiptUrl`, no hace falta inventar nada nuevo ahí).
   - Botón **"Descargar PDF"**: genera un PDF del período visible (ver sección
     dedicada) y abre el selector de compartir de Android (`Intent.ACTION_SEND`) para
     guardarlo o mandarlo por WhatsApp/mail/etc.
   - Si en el período hay gastos `BLOQUEADO` (sin comprobante, HU-01 Escenario 1) que
     por eso no entran en el total ni en el desglose, una línea aparte lo avisa (ej.
     "+ 2 gastos pendientes de comprobante, no incluidos en este total") — sin esto,
     el total de una pantalla que vende "transparencia" podría no coincidir con lo
     que un vecino espera ver si de algún otro lado se entera de un gasto de ese mes,
     sin ninguna pista de por qué. Decidido con el usuario tras el builder-check.
     Misma línea se agrega al PDF.
2. **Dos puntos de entrada**, decidido con el usuario:
   - Desde **Pagar expensas** (`PagarActivity`): un link "Ver en qué se gastaron las
     expensas" cerca del monto a pagar, que abre la Rendición de cuentas en el período
     que se está pagando — es el momento en que más le importa al vecino.
   - Desde **Más** (`MasFragment`): un ítem de menú nuevo, mismo estilo que
     "Protocolos de emergencia" o "Directorio de proveedores", para poder consultarlo
     en cualquier momento sin tener que pasar por Pagar.
3. **Categoría de gasto pasa a ser un menú fijo en Android**, no texto libre. Hoy
   `AddExpenseActivity` usa un `EditText` (`binding.inputCategory`) sin ninguna
   restricción — la web, en cambio, ya usa un `<Select>` con las 8 categorías del
   enum `ExpenseCategory`. Si la Rendición de cuentas agrupara por el texto libre tal
   cual está hoy, con el tiempo aparecerían variantes de la misma categoría
   ("Plomería", "plomeria", "Caño roto") como filas separadas, rompiendo el sentido
   del reporte. Se cambia el `EditText` por un `Spinner` con las mismas 8 categorías
   y las mismas etiquetas en español que ya usa la web, unificando ambas
   plataformas. **No se migra la categoría de los gastos históricos** (quedan con el
   texto libre que tengan guardado) — ver "Limitaciones conocidas" más abajo.
4. **PDF generado nativamente**, sin librería nueva: `android.graphics.pdf.PdfDocument`
   (parte del SDK de Android). Contenido, una página (dos si no entra):
   - Encabezado: nombre del edificio, "Rendición de cuentas", período.
   - Total gastado.
   - Tabla de categoría + total (las mismas filas que la pantalla).
   - Debajo de cada categoría, el detalle de sus gastos (descripción, proveedor,
     monto) **en texto** — sin incrustar las fotos de comprobante, para no tener que
     descargar imágenes de forma síncrona al generar el archivo. Quien quiera ver un
     comprobante puntual lo hace desde la pantalla, no desde el PDF.
   - Pie: "Generado desde Consorcio Transparente el {fecha}" + el disclaimer de que
     no reemplaza la rendición de cuentas formal si el edificio ya tiene una vía legal
     distinta (frase corta, no un texto legal largo — mismo criterio ya usado en el
     disclaimer de Protocolos de emergencia).
   - El archivo se guarda en el cache interno de la app y se comparte vía
     `FileProvider` (`Intent.ACTION_SEND`, tipo `application/pdf`) — exponer un
     `file://` directo rompe en Android 7+ (`FileUriExposedException`). Esto requiere
     declarar un `<provider>` en `AndroidManifest.xml` y un `res/xml/file_paths.xml`
     nuevos — hoy el proyecto no tiene ninguno (el comentario que ya está en el
     Manifest sobre esto es sobre la cámara, no sobre compartir archivos).

### No incluye (deliberadamente)

- **Vista anual acumulada**: se arranca solo mes a mes, igual que ya funciona
  `/liquidacion` en la web. Si hace falta más adelante, se agrega sin romper lo que
  ya existe (el cálculo mensual sigue siendo la unidad base).
- **Migración de categorías históricas**: los gastos ya guardados con texto libre no
  se reescriben. En la Rendición de cuentas de un mes viejo, esos gastos van a
  aparecer agrupados por su texto exacto en vez de por una de las 8 categorías fijas
  — es una limitación conocida, no un bug, y sólo afecta a meses anteriores a este
  cambio.
- **Fotos de comprobante dentro del PDF**: quedan sólo en la pantalla (ver arriba).
- **Editar o accionar desde esta pantalla**: es sólo lectura para los tres roles; dar
  de alta/editar un gasto sigue siendo cosa de Facturas (`AddExpenseActivity`), no se
  duplica esa funcionalidad acá.
- **Firma digital o validez legal del PDF**: es un reporte informativo, no un
  documento con valor legal formal — coherente con cómo ya se resolvió HU-07
  (protocolos propios, no oficiales).

## Modelo de datos

Sin cambios de forma en Firestore. Se reutiliza `Expense` tal cual (`ExpenseRepository
.listExpenses(buildingId)` ya existe) y se filtra/agrupa en el cliente —mismo criterio
que ya usa la web en `/liquidacion` y `/gastos`, y que ya usa `PagarActivity` para el
desglose de un pago—. Único cambio: `AddExpenseActivity` empieza a escribir
`category` restringido a los valores de `ExpenseCategory` en vez de texto arbitrario
(el campo sigue siendo un `String` en el modelo, igual que hoy; la restricción es de
UI, no de esquema — mismo criterio que ya aplica la web, donde `firestore.rules`
tampoco restringe `category` a esos 8 valores).

## Categorías en español

Se agrega un `string-array` nuevo (`expense_categories`) en `strings.xml`, en el mismo
orden que el enum `ExpenseCategory`, para no repetir el mapeo en dos lugares: lo usa
el `Spinner` de `AddExpenseActivity` y las etiquetas de la Rendición de cuentas.
**Corrección post builder-check**: la redacción original decía "las mismas etiquetas
que ya usa la web", asumiendo que existían — no es así: el `<Select>` de
`GastosPage.tsx` muestra el `.name` crudo del enum ("MANO_DE_OBRA", "ALBANILERIA",
etc.), no hay ningún mapeo a español en `web/src`. Las etiquetas en español se
redactan de cero para Android, con el mismo criterio ya usado en `strings.xml`
(`category_hint`, `ticket_category_hint`, `providers_category_hint` ya nombran
"Plomería", "Electricidad", "Gas", "Albañilería", "Otro") — el valor persistido sigue
siendo el `.name()` del enum, así que un error de redacción en el label no tiene
consecuencias reales.

## Limitaciones conocidas

- Gastos cargados antes de este cambio, con categoría en texto libre, se muestran
  agrupados por su texto exacto en la Rendición de cuentas de esos meses viejos — no
  hay forma de inferir a qué categoría del enum corresponderían sin adivinar.
- El PDF no incluye las fotos de comprobante (ver "No incluye").

## Decisiones que tomé yo (quedan marcadas para que el usuario las revise si quiere)

- **Cambiar la categoría de Android a menú fijo**: lo charlamos explícitamente y el
  usuario lo eligió, pero la decisión de *qué* 8 valores usar (reusar el enum
  `ExpenseCategory` que ya existe, en vez de definir uno nuevo) es mía — es el que ya
  usa la web, evita una tercera lista de categorías dando vueltas.
- **PDF sin fotos de comprobante incrustadas**: por simplicidad de generación
  (síncrona, sin depender de que todas las imágenes carguen antes de poder exportar).
- **`PdfDocument` nativo, sin librería externa**: coherente con el resto del proyecto
  (sin dependencias nuevas que no haga falta agregar).
- **Sin vista anual en esta primera versión**: el usuario lo confirmó, pero la
  estructura del cálculo (agrupar por categoría dentro de un rango de fechas) queda
  armada de forma que sumar meses después sea un cambio chico, no una reescritura.
- **No migrar categorías históricas**: ningún dato se puede inferir con certeza, así
  que no se inventa una regla de migración.

## Qué queda para el plan de implementación

Nombres exactos de archivos/clases dentro de `ui/rendicion/` (Activity, ViewModel,
adapters), el layout de la pantalla y de cada fila, el detalle de
`RendicionPdfGenerator` (paginación si el contenido no entra en una hoja), el
`file_paths.xml` y la entrada de `<provider>` en el Manifest, y los strings nuevos
exactos. Eso lo resuelve `superpowers:writing-plans`, no este documento.
