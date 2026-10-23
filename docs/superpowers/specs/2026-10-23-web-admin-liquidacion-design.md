# Diseño: Web para Administradores — v1, Liquidación de Expensas

> Spec de referencia: [`docs/especificacion-funcional.md`](../../especificacion-funcional.md)
> (HU-03, HU-09, backlog ítem 4 "Prorrateo automático de expensas por alícuota").
> Arquitectura general del proyecto: [`docs/arquitectura.md`](../../arquitectura.md).

## Por qué

El planteo original del TP incluía una segunda plataforma (web, para Administradores)
junto con el MVP Android — quedó fuera de alcance hasta terminar las 10 Historias de
Usuario. Ya están todas implementadas. El usuario pidió arrancar la web, investigué
competencia (Unitify, Condo Control, CondoAide, etc. — todas con el mismo look azul
corporativo + tarjetas) y charlamos del alcance: **arranca sólo para el rol
Administrador** (Encargado/Inquilino quedan para una etapa futura), y el dolor más
real a resolver es **liquidar expensas**, que hoy es 100% manual: el Administrador
tipea un monto fijo en pesos, a mano, por cada vecino (`VecinosActivity`), sin ninguna
relación con los gastos reales del edificio. El modelo `UnitInfo.aliquot` (% de
alícuota por unidad) existe desde HU-03 pero nunca se usó — quedó documentado
explícitamente como "no incluido" en esa decisión para no bloquear esa entrega. Esta
es la oportunidad de terminarlo.

## Decisión de arquitectura clave: no tocar Android

`Payment.breakdown`/`amount` ya "congelan" el monto al momento en que el inquilino
genera su pago, leyendo `users/{uid}.expensesAmount` en ese instante (ver
`createMercadoPagoPreference`/`uploadReceipt`). Si la web, al **emitir** una
liquidación, escribe el monto calculado en ese mismo campo `expensesAmount` para cada
inquilino, **el resto del sistema no se entera de nada**: sigue siendo "el
Administrador le asignó un monto a cada vecino", sólo que ahora ese monto sale de un
cálculo en vez de salir de la nada. Cero cambios en la app Android, cero cambios en
`firestore.rules` de `users/{uid}` (ya sólo el Administrador puede escribir
`expensesAmount`, que es exactamente quien va a emitir la liquidación).

## Alcance

### Incluye

1. **Login** (Administrador): email/contraseña contra el mismo Firebase Auth que la
   app. Si el usuario autenticado no tiene `role == "ADMINISTRADOR"`, se lo rechaza
   con un mensaje claro — esta v1 no es para Encargado ni Inquilino.
2. **Inicio**: KPIs del mes actual, todos con datos reales ya existentes — nada
   inventado (ver "No incluye" sobre "fondo de reserva"):
   - % de inquilinos con pago `PAGADO` este período.
   - Cantidad de unidades sin pago `PAGADO` (morosos + a verificar).
   - Gastos verificados del mes (suma de `Expense` con `status: VERIFICADO`,
     `createdAt` en el mes en curso).
   - Tickets en `A_COBRAR` (HU-10).
3. **Unidades** (`buildings/{buildingId}/units`, hoy sin uso — se activa acá):
   ABM simple. Cada unidad: etiqueta (ej. "7B"), % de alícuota, inquilino asociado
   (selector sobre los `users` del edificio con `role == INQUILINO`). Si la suma de
   alícuotas no da 100%, se avisa con un texto, **no se bloquea** — el Administrador
   puede estar cargando unidades de a poco.
4. **Gastos del mes**: lista de `Expense` del período en curso (mismo modelo que
   HU-01/Facturas) + alta de gasto nuevo con comprobante (drag & drop de **imagen**,
   mismo flujo que `AddExpenseActivity` para web). **Sólo imagen, no PDF** — Android
   nunca aceptó PDF (`pickImage.launch("image/*")`) y `storage.rules` en
   `receipts/{buildingId}/{fileName}` ya restringe `contentType` a `image/.*`; la web
   respeta esa misma regla en vez de pedir tocarla. No se duplica `ExpenseRepository`
   — se implementa un hook equivalente del lado web (TypeScript) hablando a las mismas
   colecciones.
5. **Liquidación del mes**: toma los gastos `VERIFICADO` del período, los suma, y por
   cada unidad calcula `total × alícuota / 100`. Tabla de previsualización (unidad,
   inquilino, %, monto) **antes de confirmar nada**. Botón **"Emitir liquidación"**:
   - Escribe `users/{tenantUid}.expensesAmount` para cada unidad con inquilino
     asignado.
   - Guarda un snapshot inmutable en `buildings/{buildingId}/liquidaciones/{period}`
     (historial — ver más abajo).
   - Una unidad sin inquilino asignado se salta (no hay a quién cobrarle) y queda
     marcada en la tabla.
6. **Historial de liquidaciones**: lista de períodos ya emitidos (mes, total, quién lo
   emitió, cuándo) con el detalle de esa emisión (igual a la tabla de previsualización
   que tuvo en su momento). Decisión tomada: sí guardar historial — es barato (un
   documento más por mes) y encaja con el espíritu de "Consorcio Transparente"
   (trazabilidad). Re-emitir un período ya emitido **sobrescribe** ese documento — no
   hay versiones de versiones; es intencional para mantenerlo simple en v1.
7. Dirección visual (ya validada con el usuario vía mockups en el companion visual):
   estructura tipo libro contable (filas, separadores punteados, "sellos" de estado
   rotados para PAGADO/VENCIDO/A VERIFICAR) + paleta cálida editorial (crema,
   terracota, verde, tipografía serif para títulos, mono para números/metadata).
   KPIs grandes arriba de cada pantalla.
8. `firestore.rules` para `units` y `liquidaciones` (ver sección dedicada).

### No incluye (deliberadamente, para no sobre-diseñar)

- **Encargado/Inquilino en la web**: decisión explícita del usuario, se evalúa después.
- **Gestión de alquiler** (sólo expensas): el campo `rentAmount` existe y sigue
  editable desde `VecinosActivity` en el celular como hoy; la liquidación automática
  de esta v1 sólo calcula `expensesAmount`. Si el edificio cobra alquiler, ese monto
  lo sigue tipeando el Administrador a mano, igual que ahora.
- **"Fondo de reserva" como KPI**: no existe ese concepto modelado en ningún lado del
  proyecto (es un ítem del backlog de ideas futuras en `especificacion-funcional.md`).
  No se inventa un número — se usó sólo como texto de ejemplo en el mockup visual, acá
  se reemplaza por "Gastos verificados del mes", que sí es un dato real.
- **`UnitInfo.ownerUid`**: el campo existe en el modelo pero esta v1 sólo usa
  `tenantUid` (quien paga). El propietario queda para cuando haga falta.
- **Aprobar/rechazar comprobantes de transferencia desde la web**: ya funciona bien en
  el celular (`CobrosActivity`, un tap); no es el dolor que se pidió resolver. El
  Inicio de la web sólo *muestra* el estado (lectura), no permite accionar.
- **Gestión de tickets (HU-10), Muro, Votaciones, Reservas, Proveedores, Protocolos**
  desde la web: siguen siendo pantallas de celular. Esta v1 es expensas, nada más.
- **Prorrateo con reglas más finas** (ej. unidades que no pagan ciertos rubros,
  exenciones, prorrateo por rubro en vez de sobre el total): una sola alícuota por
  unidad sobre el total de gastos verificados, como pidió el usuario — no se agrega
  complejidad que no se pidió.
- **Exportar a PDF/Excel**: aunque es uno de los dolores "incómodos en el celular" que
  identifiqué en la investigación, no es el que el usuario priorizó. Queda para una
  siguiente iteración de la web, no de esta v1.
- **Responsive para celular**: es una web para Administrador en computadora — no se
  invierte en un layout mobile-first. Debe ser usable en una ventana angosta sin
  romperse, pero no es un objetivo de diseño.

## Modelo de datos (Firestore) — lo nuevo

```
buildings/{buildingId}/units/{unitId}       // ya existía el modelo, sin uso hasta ahora
  label: String             // "7B"
  aliquot: Double            // 0-100, % de alícuota
  tenantUid: String = ""     // uid de users/, vacío = sin asignar
  ownerUid: String = ""      // sin usar en v1, se deja en el modelo para no romperlo

buildings/{buildingId}/liquidaciones/{period}   // period = "yyyy-MM", nuevo
  period: String
  totalExpenses: Double
  breakdown: Array<{ unitId, unitLabel, tenantUid, aliquot, amount }>
  unassignedUnits: Array<String>   // unitIds sin tenantUid al momento de emitir
  emittedAt: Timestamp
  emittedByUid: String
```

`users/{uid}.expensesAmount` y `buildings/{buildingId}/expenses/{expenseId}` no
cambian de forma — la web lee y escribe exactamente los mismos campos que ya usa
Android.

## Pantallas (React + TypeScript)

Rutas (`react-router`), todas detrás del login:

1. **`/login`** — email/contraseña, Firebase Auth. Si el perfil no es
   `ADMINISTRADOR`, mensaje de error y `signOut()` inmediato (no se deja una sesión
   semi-logueada dando vueltas).
2. **`/`** (Inicio) — KPIs grandes arriba (tarjetas con borde izquierdo terracota,
   número en serif), tabla de estado de pagos del mes abajo (sólo lectura).
3. **`/unidades`** — tabla editable (label, alícuota, inquilino) + fila nueva. Alerta
   suave si la suma de alícuotas ≠ 100%.
4. **`/gastos`** — lista de `Expense` del mes (mismas categorías que HU-01) + botón
   "Agregar gasto" (modal con drag & drop de comprobante).
5. **`/liquidacion`** — selector de período (default: mes actual), tabla de
   previsualización del cálculo, botón "Emitir liquidación" (con confirmación — esto
   escribe plata real en el sistema). Debajo, el historial de períodos ya emitidos
   (click para ver el detalle congelado de ese mes).

## Coordinación con `VecinosActivity` (Android)

La decisión central de este diseño es que la web escribe `users/{uid}.expensesAmount`
— el mismo campo que `VecinosActivity` ya deja editar a mano desde el celular. Esto
deja dos pantallas, no coordinadas entre sí, escribiendo el mismo monto en plata real:
el Administrador podría, sin querer, pisar en el celular un valor que la web acababa
de calcular, sin ningún indicio de que ese número no es "el de siempre". El riesgo es
bajo (es el propio Administrador compitiendo contra sí mismo, no una falla de
seguridad — el write ya está correctamente restringido a Admin en `firestore.rules`),
pero no está bien dejarlo sin ningún aviso.

Mitigación elegida, mínima a propósito: `VecinosActivity` pasa a mostrar, debajo del
campo de expensas de cada vecino, un texto de sólo lectura si existe una liquidación
que generó ese monto — "Calculado por liquidación de octubre 2026" — leyendo el
`buildings/{buildingId}/liquidaciones/{period más reciente}` y buscando si el
`tenantUid` de ese vecino aparece en su `breakdown`. **No bloquea la edición manual**
(sigue siendo válida, por ejemplo para una corrección puntual), sólo avisa de dónde
salió el número antes de que el Administrador lo pise. Es el único cambio que este
diseño toca del lado Android — una lectura, sin escritura nueva ni cambio de regla.

## Entorno de desarrollo: Firebase Local Emulator Suite

No existe todavía un proyecto de Firebase real en este entorno (`google-services.json`
es una plantilla, igual que documenta `CLAUDE.md`). En vez de portar el patrón
Mock/Repository de Android (que el diseño ya decidió no replicar, ver abajo), el
plan de implementación arranca el **Firebase Local Emulator Suite** (Firestore, Auth,
Storage) con un script de semilla (`firebase-admin` contra el emulador) que carga un
edificio de prueba con usuarios, unidades y gastos — equivalente en espíritu al
flavor `mock` de Android, pero como infraestructura oficial de Firebase en vez de una
capa de código propia. Esto permite desarrollar y probar la funcionalidad completa
(login, unidades, gastos, liquidación, emitir, historial) sin credenciales reales, y
el mismo código de la app apunta al emulador en desarrollo y al proyecto real en
producción sólo cambiando la configuración de conexión (patrón estándar del SDK de
Firebase, `connectFirestoreEmulator`/`connectAuthEmulator`/`connectStorageEmulator`
detrás de una variable de entorno).

## Sin migración de `users` a `units`

`UnitInfo`/`units` nunca se usó (cero lecturas/escrituras en todo el código Android
hoy). Los inquilinos existentes tienen un campo `User.unitId` de texto libre (sólo
cosmético, no es una referencia a un documento) que **no se migra automáticamente** a
la colección `units` nueva. El Administrador carga las unidades de cero en la pantalla
`/unidades`, asociando a cada una el inquilino correspondiente desde una lista de los
`users` del edificio. Es carga manual, no importación — para un edificio con un puñado
de unidades es rápido, y evita escribir lógica de migración que sólo se usa una vez.
Hasta que el Administrador cargue sus unidades, el Inicio y la Liquidación están
vacíos — comportamiento esperado, no un bug.

## Repositorios (TypeScript, espejo liviano del patrón Android)

No se porta `RepositoryProvider`/interfaces Mock — es sobre-ingeniería para un cliente
web de un solo rol. Cada pantalla usa un hook (`useUnits(buildingId)`,
`useExpenses(buildingId, period)`, etc.) que envuelve llamadas directas al SDK de
Firebase (`firebase/firestore`, `firebase/auth`, `firebase/storage`). Es el mismo
criterio que ya aplica el proyecto ("no introducir un patrón nuevo sin que el usuario
lo pida") — acá el patrón nuevo sería innecesario, no el que ya existe en Android.

## `firestore.rules` — reglas nuevas

```
buildings/{buildingId}/units/{unitId}
  allow read: if belongsToBuilding(buildingId);
  allow write: if isAdmin() && myBuildingId() == buildingId;

buildings/{buildingId}/liquidaciones/{period}
  allow read: if belongsToBuilding(buildingId);
  allow write: if isAdmin() && myBuildingId() == buildingId;
```

(`isAdmin()`, `belongsToBuilding()`, `myBuildingId()` ya existen en `firestore.rules`,
se reusan tal cual.) No hace falta tocar nada de `users/{uid}` — la regla actual
("sólo un Administrador del mismo edificio puede escribir `expensesAmount`") ya cubre
exactamente lo que la liquidación necesita hacer.

## Stack técnico

- **React 18 + TypeScript + Vite** — build rápido, sin servidor propio (hostea estático
  en Firebase Hosting, al lado de las Cloud Functions existentes).
- **Tailwind CSS**, sin librería de componentes (Material/Bootstrap/etc.) — son
  justamente las que hacen que toda esta clase de producto se vea igual. Paleta y
  tipografía custom según la dirección visual validada.
- **Firebase JS SDK** (`firebase/app`, `/auth`, `/firestore`, `/storage`) contra el
  mismo proyecto de Firebase que ya usa la app (mismas credenciales de
  `google-services.json`, equivalentes para web — hay que generar la config web desde
  la consola de Firebase, documentado en el plan de implementación).
- **`react-router-dom`** para las 5 rutas.
- Vive en `web/` en la raíz del repo, hermano de `app/` y `functions/` — mismo repo,
  mismo historial de git, un solo lugar para la tesis.

## Decisiones que tomé yo (el usuario me dejó el criterio, marcadas para que las revise si quiere)

- **Guardar historial de liquidaciones** (`liquidaciones/{period}`): la pregunta que
  quedó abierta en la charla. Elegí guardar — es un documento más por mes, barato, y
  encaja con el espíritu de trazabilidad del proyecto.
- **Re-emitir un período sobrescribe, sin versiones**: simplicidad para v1 por sobre
  robustez ante el caso raro de "me equivoqué y quiero ver qué decía antes de
  corregir". Si hace falta después, se agrega auditoría más fina.
- **KPI "Gastos verificados del mes" en vez de "fondo de reserva"**: el mockup usaba
  un dato que no existe en el sistema; lo reemplacé por uno real.
- **Suma de alícuotas ≠ 100% no bloquea, sólo avisa**: para no trabar al
  Administrador mientras carga las unidades de a poco.
- **Sin capa de Repository/Mock en el cliente web**: un solo rol, un solo flujo de
  datos — el patrón de la app Android (pensado para 3 roles y dos backends) sería
  ingeniería de más acá.
- **No-responsive mobile**: es una herramienta de escritorio por diseño: el propio
  pedido original era "lo que es incómodo o imposible desde el celular".
- **Sólo imágenes en comprobantes de gasto, no PDF**: mismo límite que ya tiene
  Android; evita tocar `storage.rules`.
- **Aviso de sólo lectura en `VecinosActivity`** cuando un monto viene de una
  liquidación (ver sección dedicada) — el único cambio Android de este diseño.
- **Firebase Local Emulator Suite para desarrollo**, no una capa Mock propia — ver
  sección dedicada.
- **Sin importación `users` → `units`**: carga manual desde cero — ver sección
  dedicada.

## Qué queda para el plan de implementación

Estructura exacta de carpetas en `web/`, nombres de componentes, configuración de
Vite/Tailwind, cómo se obtiene y dónde se guarda la config de Firebase para web (no
puede ir hardcodeada igual que `google-services.json` no va con credenciales reales en
el repo — ver cómo lo resuelve el plan), y el detalle de cada pantalla. Eso lo resuelve
`superpowers:writing-plans`, no este documento.
