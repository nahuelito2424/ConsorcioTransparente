# Diseño: Historial de pagos por inquilino (app Android)

> Spec de referencia: [`docs/especificacion-funcional.md`](../../especificacion-funcional.md)
> (sección 6 "Sugerencias para versiones futuras", ítem 6 — "Estado de cuenta por
> unidad"). Arquitectura general del proyecto: [`docs/arquitectura.md`](../../arquitectura.md).

## Por qué

Siguiente ítem del backlog elegido por el usuario tras terminar Rendición de cuentas.
La idea original decía "por unidad", pero el modelo de datos de Android es por
inquilino (`VecinosActivity` asigna montos directo a cada vecino; el concepto de
"unidad" como entidad sólo existe en la web, sin uso en la app) — se construye sobre
lo que ya existe, no se introduce un concepto nuevo en paralelo.

Hoy el único lugar donde un inquilino ve su propio historial es un `AlertDialog` de
texto plano en Pagar (`PagarActivity.showHistory()`), y el Administrador no tiene
ninguna forma de ver el historial de pagos de un vecino puntual — sólo el estado del
mes en curso, en el Panel de cobros (`CobrosActivity`). Para encontrar un patrón de
morosidad hoy hay que adivinarlo mes a mes, sin ninguna pantalla que lo muestre junto.

## Decisión de arquitectura clave: no hace falta tocar repositorio ni reglas

`PaymentRepository.listMyPayments(buildingId, uid)` ya acepta cualquier `uid` como
parámetro (el nombre es por su único uso actual, no una restricción). Las reglas de
`firestore.rules` para `payments` ya permiten `isEncargadoOrAdmin()` leer el pago de
cualquier vecino del edificio (ver línea `allow read: if belongsToBuilding(buildingId)
&& (resource == null || resource.data.uid == request.auth.uid || isEncargadoOrAdmin())`).
Esta feature es casi enteramente una pantalla nueva: una sola `HistorialPagosActivity`,
parametrizada por `uid`, reutilizada tanto para "mi propio historial" como para "el
historial de este vecino".

## Alcance

### Incluye

1. **`HistorialPagosActivity`** — recibe `uid` (String) y `userName` (String, para el
   título) por extras del Intent. Muestra los **últimos 12 meses** (mes calendario
   actual + 11 anteriores), uno por fila, orden descendente (más reciente primero):
   - Si existe un `Payment` para ese período: estado (`Pagado`/`A verificar`/
     `Rechazado`), mismo patrón de tag-pill por colores que ya usan `CobroAdapter`/
     `TicketAdapter`/`VoteAdapter`/`ExpenseAdapter` (no hay una clase `StatusStamp`
     compartida en Android — eso es un componente de la web; en la app es el mismo
     patrón repetido inline en cada adapter), monto, método (transferencia/Mercado
     Pago), fecha de verificación si corresponde,
     motivo si fue rechazado. Si tiene comprobante (`receiptUrl`), un botón "Ver
     comprobante" abre el mismo diálogo con `Glide` que ya usan `CobrosActivity` y la
     Rendición de cuentas — hoy, una vez aprobado un pago, no hay ninguna forma de
     volver a ver su comprobante; esta pantalla lo agrega de regalo.
   - Si no existe ningún `Payment` para ese período: fila "Sin pago registrado", en un
     tono **neutro** (gris, no rojo) — no hay forma de saber si ese mes directamente no
     le correspondía pagar nada a este inquilino (building sin cobro ese concepto,
     inquilino asignado después), así que no se trata como alarma automática. Es
     información, no una acusación.
2. **Dos puntos de entrada, ambos ya existentes, reapuntados**:
   - **Pagar expensas**: el botón "Ver historial" (`buttonHistory`, ya existe) deja de
     abrir el `AlertDialog` de texto y abre `HistorialPagosActivity` con el uid propio.
     Se borra `PagarViewModel.loadHistory()`/`PagarActivity.showHistory()` (código
     muerto tras el cambio).
   - **Panel de cobros**: cada fila de `CobrosActivity` (`item_cobro.xml`) suma un
     botón/texto "Ver historial" — separado del tap que abre el diálogo de
     aprobar/rechazar, para que no haya ambigüedad sobre qué hace cada toque — que abre
     `HistorialPagosActivity` con el uid de ese vecino.
3. Sin ítem nuevo en "Más": ya se llega desde los dos lugares donde tiene sentido
   usarlo (pagando, o revisando cobros).
4. **Datos de prueba en `MockDataStore.payments`** (hoy arranca vacío —
   `mutableListOf<Payment>()`, a diferencia de `expenses`/`posts`/`tickets`, que sí
   vienen precargados): se agregan 2-3 `Payment` de ejemplo en períodos *pasados*
   (usando `shiftPeriod` sobre `currentPeriod()`, nunca tocando el período actual para
   no interferir con los flujos de Pagar/Cobros ya probados a mano) con una mezcla de
   `PAGADO`/`RECHAZADO`, dejando además al menos un mes intermedio sin documento a
   propósito. Sin esto, la única forma de ver la pantalla nueva en el flavor `mock` es
   12 filas de "Sin pago registrado" seguidas — exactamente el caso que peor demuestra
   para qué sirve.

### No incluye (deliberadamente)

- **Rango configurable** (ver más de 12 meses, elegir un rango): un año fijo alcanza
  para detectar un patrón de morosidad; si hace falta ir más atrás, es una extensión
  natural para después, no algo que haya que resolver ahora.
- **Acciones desde esta pantalla** (aprobar/rechazar un pago viejo): sigue siendo sólo
  de lectura. Aprobar/rechazar sigue siendo cosa del Panel de cobros, sobre el período
  en curso — no se duplica esa lógica acá.
- **Cambiar el modelo `Payment`** o agregar campos nuevos: se lee tal cual existe hoy.
- **Encargado como punto de entrada propio**: el Panel de cobros ya es sólo para
  Administrador en el menú (`itemCobros` oculto para Encargado en `MasFragment`,
  aunque las reglas técnicamente lo permitirían) — esta pantalla hereda esa misma
  restricción simplemente por heredar su único punto de entrada admin-side. No se
  decide nada nuevo acá.

## Pantalla

`ActivityHistorialPagosBinding`: `ScrollView` → `LinearLayout`, mismo estilo que el
resto de la app (`buttonBack` + título). Título: `"Mi historial de pagos"` si
`uid == currentUid`, si no `"Historial de pagos — $userName"` — siempre queda claro de
quién es, sobre todo para el Admin revisando un vecino. Debajo, un `RecyclerView` con
las 12 filas (mismo patrón `RecyclerView.Adapter` + `ViewBinding` + `submitList` del
resto del proyecto, no `ListAdapter`/`DiffUtil`).

## Decisiones que tomé yo (el usuario me dejó el criterio explícitamente)

- **12 meses fijos, no un rango configurable ni "desde que existe el inquilino"**: no
  hay ningún campo de "fecha de alta" en `User` para saber desde cuándo contar, y
  armar esa lógica es complejidad que nadie pidió. Un año fijo es un default honesto.
- **"Sin pago registrado" en gris, no en rojo**: evitar falsas alarmas de morosidad
  para períodos donde quizás ni correspondía pagar — más honesto que gritar "moroso"
  sin poder probarlo.
- **Agregar "Ver comprobante" también a pagos ya aprobados/rechazados** (hoy sólo se
  puede ver el comprobante mientras está `A_VERIFICAR`, desde Cobros): es un
  complemento natural y barato de una pantalla de historial, y encaja con el espíritu
  de transparencia del proyecto — no es que lo haya pedido el usuario explícitamente,
  pero es el tipo de detalle que separa una pantalla útil de una que sólo cumple el
  mínimo.
- **Borrar el diálogo de historial viejo de Pagar** en vez de dejarlo como alternativa:
  tener dos formas de ver lo mismo (una peor que la otra) no suma, sólo confunde.
- **Sembrar `MockDataStore.payments`** (hallazgo del builder-check: hoy arranca vacío,
  y ningún flujo de la app permite crear un pago fuera del período actual) — sin esto
  la pantalla no se puede verificar a mano de forma significativa en el flavor mock.

## Qué queda para el plan de implementación

Nombres exactos de archivos/clases dentro de `ui/pagos/` o una carpeta nueva, el
layout de la pantalla y de cada fila, el detalle exacto del adapter (filas con y sin
`Payment`), y el string nuevo del botón "Ver historial" en `item_cobro.xml`. Eso lo
resuelve `superpowers:writing-plans`, no este documento.
