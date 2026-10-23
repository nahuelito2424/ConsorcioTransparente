# Web para Administradores — v1, Liquidación de Expensas Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Primera versión de la web para Administradores: login, gestión de unidades
(alícuota), carga de gastos del mes, y liquidación automática de expensas que calcula
y emite el monto de cada unidad — reemplazando la carga manual a mano que hoy hace
`VecinosActivity` en el celular.

**Architecture:** React + TypeScript + Vite, Tailwind CSS sin librería de componentes,
hablando directo al mismo proyecto de Firebase que la app Android (Auth/Firestore/
Storage) vía el SDK web — sin capa Repository/Mock propia (un solo rol, no hace falta).
Para desarrollo y pruebas, Firebase Local Emulator Suite con un script de semilla. La
app Android recibe un único cambio aditivo: un aviso de sólo lectura en
`VecinosActivity` cuando un monto viene de una liquidación.

**Tech Stack:** React 18, TypeScript, Vite, Tailwind CSS, `react-router-dom`, Firebase
JS SDK v10+ (`firebase/app`, `/auth`, `/firestore`, `/storage`), Vitest (cálculo puro
+ `@firebase/rules-unit-testing` contra el emulador), Firebase Hosting.

**Spec:** [`docs/superpowers/specs/2026-10-23-web-admin-liquidacion-design.md`](../specs/2026-10-23-web-admin-liquidacion-design.md).

## Global Constraints

- **`web/` en la raíz del repo**, hermana de `app/` y `functions/`. Mismo repo, mismo
  historial de git.
- **Cero cambios al modelo de datos de Android**, salvo el aviso de sólo lectura en
  `VecinosActivity` (Tarea 9) — ninguna escritura nueva, ninguna regla nueva sobre
  `users/{uid}`.
- **`users/{uid}.expensesAmount` es el único campo que la liquidación escribe** para
  comunicarse con el resto del sistema — es exactamente el campo que ya lee
  `createMercadoPagoPreference`/`uploadReceipt` del lado Android. No se crea ningún
  campo ni colección nueva que Android tendría que aprender a leer.
- **Sólo rol `ADMINISTRADOR` entra a la web** en esta v1. Si el perfil autenticado no
  es Administrador, `signOut()` inmediato y mensaje de error — nunca una sesión a
  medias.
- **Sin capa Mock/Repository**: cada hook habla directo al SDK de Firebase. Para
  desarrollo, Firebase Local Emulator Suite (Auth + Firestore + Storage), nunca un
  proyecto de Firebase real en este entorno (no existe uno — mismo límite que ya
  documenta `CLAUDE.md` para Android).
- **Sólo imágenes en comprobantes de gasto** (`image/*`, igual que
  `AddExpenseActivity`/`storage.rules` ya restringen) — no PDF, no se toca
  `storage.rules`.
- **Suma de alícuotas ≠ 100% avisa, no bloquea.**
- **Re-emitir un período sobrescribe** `buildings/{buildingId}/liquidaciones/{period}`
  — sin versiones.
- **Sin migración `users` → `units`**: el Administrador carga las unidades de cero.
- **Dirección visual ya validada** (no se re-discute en este plan): fondo crema
  (`#faf6f1`), acento terracota (`#a85c32`), verde para "ok" (`#2f6b47`), separadores
  punteados (`#c9b990`/`#e6dcc8`), títulos en Georgia/serif, metadata/números en
  `'Courier New'`/monospace, "sellos" de estado rotados (`transform: rotate(-4deg)`,
  borde 1.5px, sin relleno), KPIs en Georgia ~38px. Ver los mockups validados en
  `.superpowers/brainstorm/867-1792720633/content/dashboard-mock-v2.html` como
  referencia exacta de valores.
- **No responsive mobile** — herramienta de escritorio, usable en ventana angosta sin
  romperse, no optimizada para eso.
- **Idioma**: UI en español (Argentina), igual que la app. Nombres de código en inglés.

## Review Focus

- **Unidad sin inquilino asignado al emitir**: no debe crashear ni escribir
  `expensesAmount` para un uid vacío/inexistente — debe aparecer en
  `unassignedUnits` y seguir de largo con el resto.
- **Login de un rol no-Administrador** (Encargado, Inquilino, o un usuario sin
  `buildingId`): debe rechazarse con `signOut()` inmediato, nunca dejar una pantalla a
  medio cargar o un error críptico.
- **Un Administrador de un edificio no debe poder leer/escribir `units` ni
  `liquidaciones` de otro edificio**, aunque manipule la navegación del lado
  cliente — las reglas de Firestore son la barrera real, no la UI.
- **Re-emitir un período ya emitido, con unidades que cambiaron** (una se borró, una
  alícuota cambió): el documento de `liquidaciones/{period}` debe quedar con el
  cálculo nuevo completo, sin mezclar datos del cálculo anterior.
- **Emitir con cero gastos verificados ese mes** (total = 0): no debe crashear:
  cada unidad calcula `$0`, y la pantalla debe dejarlo claro antes de confirmar (no
  silenciosamente poner a todo el mundo en expensas $0 sin aviso).

---

### Task 1: Scaffold del proyecto web + conexión a Firebase (real y emulador)

**Files:**
- Create: `web/package.json`, `web/vite.config.ts`, `web/tsconfig.json`,
  `web/tailwind.config.ts`, `web/postcss.config.js`, `web/index.html`,
  `web/.env.example`, `web/src/main.tsx`, `web/src/App.tsx`, `web/src/firebase.ts`,
  `web/src/types.ts`, `web/src/styles/index.css`, `web/README.md`
- Modify: `firebase.json` (raíz del repo — agregar `hosting` y `emulators`)
- Modify: `.gitignore` (agregar `web/node_modules`, `web/dist`, `web/.env`,
  si no están ya cubiertos por patrones existentes)

**Interfaces:**
- Produces: `web/src/firebase.ts` exporta `auth: Auth`, `db: Firestore`,
  `storage: FirebaseStorage` — inicializados contra el emulador si
  `import.meta.env.VITE_USE_EMULATOR === "true"`, contra el proyecto real si no.
  Todas las tareas siguientes importan de acá, nunca inicializan Firebase por su
  cuenta.
- Produces: `web/src/types.ts` — interfaces TypeScript espejo de los modelos Kotlin
  que la web toca: `UserProfile` (uid, email, role, buildingId, unitId, expensesAmount,
  rentAmount), `Building` (id, name, chargesExpenses, etc. — sólo los campos que la
  web lee), `Expense` (id, description, category, providerName, amount, receiptUrl,
  status, createdByUid, createdAt), `UnitInfo` (id, label, aliquot, tenantUid,
  ownerUid), `Liquidacion` (period, totalExpenses, breakdown:
  `LiquidacionBreakdownRow[]`, unassignedUnits: string[], emittedAt, emittedByUid),
  `LiquidacionBreakdownRow` (unitId, unitLabel, tenantUid, aliquot, amount). Tareas
  siguientes importan estos tipos, no definen los suyos.

- [ ] **Step 1: `npm create vite@latest web -- --template react-ts`**

Run desde la raíz del repo. Borrar los archivos de ejemplo que trae la plantilla
(`App.css`, el contenido de ejemplo en `App.tsx`) — quedan vacíos/mínimos para que
las tareas siguientes los llenen.

- [ ] **Step 2: Instalar dependencias**

Run: `cd web && npm install firebase react-router-dom && npm install -D tailwindcss postcss autoprefixer vitest @firebase/rules-unit-testing`

- [ ] **Step 3: Configurar Tailwind**

`web/tailwind.config.ts` — `content: ["./index.html", "./src/**/*.{ts,tsx}"]`, y en
`theme.extend.colors` los tokens de la dirección visual: `cream: "#faf6f1"`,
`terracota: "#a85c32"`, `ok: "#2f6b47"`, `warn: "#a85c32"`, `pending: "#8a7e6f"`,
`border-dashed-color: "#c9b990"`. En `theme.extend.fontFamily`: `serif: ["Georgia",
"Times New Roman", "serif"]`, `mono: ["Courier New", "monospace"]`.
`web/src/styles/index.css` con las 3 directivas `@tailwind`, importado desde
`main.tsx`.

- [ ] **Step 4: `web/src/firebase.ts`**

```typescript
import { initializeApp } from "firebase/app";
import { getAuth, connectAuthEmulator } from "firebase/auth";
import { getFirestore, connectFirestoreEmulator } from "firebase/firestore";
import { getStorage, connectStorageEmulator } from "firebase/storage";

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET,
  appId: import.meta.env.VITE_FIREBASE_APP_ID,
};

export const app = initializeApp(firebaseConfig);
export const auth = getAuth(app);
export const db = getFirestore(app);
export const storage = getStorage(app);

if (import.meta.env.VITE_USE_EMULATOR === "true") {
  connectAuthEmulator(auth, "http://127.0.0.1:9099", { disableWarnings: true });
  connectFirestoreEmulator(db, "127.0.0.1", 8080);
  connectStorageEmulator(storage, "127.0.0.1", 9199);
}
```

- [ ] **Step 5: `web/.env.example`**

```
VITE_FIREBASE_API_KEY=REEMPLAZAR-CON-TU-API-KEY
VITE_FIREBASE_AUTH_DOMAIN=REEMPLAZAR-CON-TU-PROYECTO.firebaseapp.com
VITE_FIREBASE_PROJECT_ID=REEMPLAZAR-CON-TU-PROYECTO-FIREBASE
VITE_FIREBASE_STORAGE_BUCKET=REEMPLAZAR-CON-TU-PROYECTO-FIREBASE.firebasestorage.app
VITE_FIREBASE_APP_ID=REEMPLAZAR-CON-TU-APP-ID
VITE_USE_EMULATOR=true
```

Mismo espíritu que `app/google-services.json`: plantilla con valores falsos, nunca
credenciales reales en el repo. `web/.env` (el real, gitignored) se arma copiando este
archivo y completando con la config web del proyecto de Firebase (Project Settings →
Add app → Web, en la consola).

- [ ] **Step 6: `web/src/types.ts`**

Las interfaces listadas en el bloque "Produces" de arriba, con comentarios cortos
indicando a qué colección/documento de Firestore corresponde cada una (mismo estilo
que los comentarios de clase en Kotlin: qué, no por qué obvio).

- [ ] **Step 7: `web/src/App.tsx` y `web/src/main.tsx`**

`App.tsx`: `BrowserRouter` con una sola ruta placeholder (`<div>Consorcio
Transparente — Admin</div>`) — las rutas reales las agrega la Tarea 4. `main.tsx`:
monta `<App />` en `#root`, importa `./styles/index.css`.

- [ ] **Step 8: `firebase.json` (raíz) — agregar hosting y emulators**

```json
{
  "firestore": { "rules": "firestore.rules", "indexes": "firestore.indexes.json" },
  "storage": { "rules": "storage.rules" },
  "functions": [{ "source": "functions", "codebase": "default", "ignore": ["node_modules", ".git", "firebase-debug.log"] }],
  "hosting": {
    "public": "web/dist",
    "ignore": ["firebase.json", "**/.*", "**/node_modules/**"],
    "rewrites": [{ "source": "**", "destination": "/index.html" }]
  },
  "emulators": {
    "auth": { "port": 9099 },
    "firestore": { "port": 8080 },
    "storage": { "port": 9199 },
    "ui": { "enabled": true, "port": 4000 }
  }
}
```

- [ ] **Step 9: `web/README.md`**

Cómo correr el emulador (`firebase emulators:start`, requiere Firebase CLI — anotar
cómo instalarla si no está: `npm install -g firebase-tools`), cómo correr la semilla
(referencia a la Tarea 3, todavía no existe — anotar "ver Tarea 3"), cómo levantar el
dev server (`npm run dev`), cómo buildear (`npm run build`) y cómo desplegar
(`firebase deploy --only hosting`). Mismo nivel de detalle que el README principal
tiene para Android.

- [ ] **Step 10: Verificar que compila**

Run: `cd web && npm run build`
Expected: build exitoso, genera `web/dist/`.

- [ ] **Step 11: Commit**

```bash
git add web/ firebase.json .gitignore
git commit -m "Scaffold de la web para Administradores (React + TS + Vite + Tailwind)"
```

---

### Task 2: Reglas de Firestore para `units`/`liquidaciones` + tests contra el emulador

**Files:**
- Modify: `firestore.rules`
- Create: `web/tests/firestore-rules.test.ts`
- Create: `web/vitest.config.ts`

**Interfaces:**
- Produces: reglas para `buildings/{buildingId}/units/{unitId}` y
  `buildings/{buildingId}/liquidaciones/{period}` — Tareas 5 y 7 asumen estas reglas
  ya desplegadas al correr contra el emulador.

- [ ] **Step 1: Agregar las reglas**

En `firestore.rules`, dentro de `match /buildings/{buildingId}`, junto a los demás
`match` de subcolecciones:

```
match /units/{unitId} {
  allow read: if belongsToBuilding(buildingId);
  allow write: if isAdmin() && myBuildingId() == buildingId;
}

match /liquidaciones/{period} {
  allow read: if belongsToBuilding(buildingId);
  allow write: if isAdmin() && myBuildingId() == buildingId;
}
```

- [ ] **Step 2: Configurar Vitest para estas tests**

`web/vitest.config.ts` mínimo (`test: { environment: "node" }`). Agregar script
`"test": "vitest run"` a `web/package.json`.

- [ ] **Step 3: Escribir el test de reglas (falla primero: sin emulador corriendo, falla por conexión — correrlo con el emulador activo es parte del Step 4)**

`web/tests/firestore-rules.test.ts`, usando `@firebase/rules-unit-testing`
(`initializeTestEnvironment`, apuntando al emulador de Firestore en el puerto 8080,
cargando las reglas reales de `../../firestore.rules`). Casos:

```typescript
test("un Administrador de otro edificio no puede leer units de este edificio", async () => { /* assertFails */ });
test("el Administrador del edificio puede crear una unidad", async () => { /* assertSucceeds */ });
test("un Encargado no puede escribir units", async () => { /* assertFails */ });
test("un Administrador de otro edificio no puede escribir liquidaciones de este edificio", async () => { /* assertFails */ });
```

- [ ] **Step 4: Levantar el emulador de Firestore y correr el test**

Run: `firebase emulators:exec --only firestore "cd web && npm test"`
Expected: los 4 casos pasan.

- [ ] **Step 5: Commit**

```bash
git add firestore.rules web/tests/firestore-rules.test.ts web/vitest.config.ts web/package.json
git commit -m "Agregar reglas de Firestore para units/liquidaciones con tests contra el emulador"
```

---

### Task 3: Script de semilla para el emulador

**Files:**
- Create: `web/scripts/seed-emulator.ts`
- Modify: `web/package.json` (agregar script `"seed"`)

**Interfaces:**
- Consumes: `web/src/types.ts` (Tarea 1) para tipar los documentos que crea.
- Produces: datos de prueba en el emulador que las Tareas 4-9 usan para verificación
  manual — un edificio, un Administrador, 3 inquilinos con y sin unidad asignada, 2
  gastos (uno VERIFICADO, uno BLOQUEADO), ninguna liquidación emitida todavía (para
  poder probar el camino "primera vez").

- [ ] **Step 1: Escribir el script**

`web/scripts/seed-emulator.ts`, usando `firebase-admin` apuntado al emulador
(`process.env.FIRESTORE_EMULATOR_HOST = "127.0.0.1:8080"`,
`process.env.FIREBASE_AUTH_EMULATOR_HOST = "127.0.0.1:9099"` antes de inicializar).
Crea:
- Un usuario Auth + doc `users/{uid}` con `role: "ADMINISTRADOR"`,
  `email: "admin@test.com"` (password fijo para probar el login a mano).
- Un usuario Auth + doc `users/{uid}` con `role: "ENCARGADO"` (para probar el rechazo
  de login).
- Un `buildings/{id}` (`chargesExpenses: true`).
- 3 `users` con `role: "INQUILINO"` y ese `buildingId`.
- 2 `buildings/{id}/units`: una con `tenantUid` de uno de los inquilinos y alícuota
  50, otra sin `tenantUid` y alícuota 30 (suma 80, a propósito — para ver el aviso de
  "no suma 100%").
- 2 `buildings/{id}/expenses`: uno `status: VERIFICADO` con `receiptUrl` no vacío y
  `amount: 50000`, otro `status: BLOQUEADO` sin `receiptUrl`.

- [ ] **Step 2: Agregar el script de npm**

`web/package.json`: `"seed": "tsx scripts/seed-emulator.ts"` (agregar `tsx` como
dependencia de desarrollo si no está — `npm install -D tsx`).

- [ ] **Step 3: Verificar que corre contra el emulador**

Run: `firebase emulators:exec --only auth,firestore "cd web && npm run seed"`
Expected: termina sin error; opcionalmente, loguea un resumen de lo creado.

- [ ] **Step 4: Commit**

```bash
git add web/scripts/seed-emulator.ts web/package.json
git commit -m "Agregar script de semilla para el emulador de Firebase"
```

---

### Task 4: Autenticación + layout + navegación

**Files:**
- Create: `web/src/auth/useAuth.ts`, `web/src/auth/LoginPage.tsx`,
  `web/src/auth/RequireAdmin.tsx`
- Create: `web/src/components/Layout.tsx`
- Modify: `web/src/App.tsx` (rutas reales)

**Interfaces:**
- Consumes: `auth`, `db` de `web/src/firebase.ts` (Tarea 1); `UserProfile` de
  `web/src/types.ts` (Tarea 1).
- Produces: `useAuth(): { user: UserProfile | null, loading: boolean }` — hook que
  expone el perfil del Administrador autenticado (o `null`). Tareas 5-8 lo consumen
  para obtener `buildingId`. `<RequireAdmin>`: wrapper de ruta que redirige a
  `/login` si `user` es `null`.

- [ ] **Step 1: `useAuth.ts`**

`onAuthStateChanged(auth, ...)`: al loguearse, hace `getDoc(doc(db, "users", uid))`;
si `role !== "ADMINISTRADOR"`, llama `signOut(auth)` y deja `user: null` con un
mensaje de error expuesto en el hook (`error: string | null`). Si es Administrador,
expone el perfil completo.

- [ ] **Step 2: `LoginPage.tsx`**

Formulario email/contraseña, `signInWithEmailAndPassword(auth, email, password)`.
Muestra `error` de `useAuth` si el login tuvo éxito pero el rol no es válido
("Esta web es sólo para Administradores."), o el error de Firebase Auth si las
credenciales son incorrectas. Dirección visual: fondo crema, título serif, inputs con
borde fino, botón terracota sólido.

- [ ] **Step 3: `RequireAdmin.tsx`**

Si `loading`, pantalla en blanco/spinner mínimo. Si `!user`, `<Navigate to="/login"
/>`. Si `user`, renderiza `children` envueltos en `<Layout>`.

- [ ] **Step 4: `Layout.tsx`**

Barra lateral fija (fondo crema, borde derecho punteado `#c9b990`) con los 4 links
(Inicio, Unidades, Gastos, Liquidación) + nombre del edificio + botón "Cerrar sesión"
(`signOut(auth)`) abajo. Usa `NavLink` de `react-router-dom` para el estado activo.

- [ ] **Step 5: `App.tsx` — rutas reales**

`/login` → `LoginPage`. `/`, `/unidades`, `/gastos`, `/liquidacion` → cada una
envuelta en `<RequireAdmin>`, apuntando por ahora a un placeholder (`<div>Unidades
(Tarea 5)</div>`, etc.) — las Tareas 5-8 reemplazan cada placeholder por la pantalla
real.

- [ ] **Step 6: Verificar a mano contra el emulador**

Run: `firebase emulators:start` (una terminal) y `cd web && npm run dev` (otra),
con `web/.env` apuntando al emulador y los datos de la Tarea 3 ya sembrados. Entrar
con `admin@test.com` → debe mostrar el layout con las 4 secciones. Cerrar sesión,
entrar con el usuario `ENCARGADO` sembrado → debe rechazar con el mensaje esperado,
sin dejar sesión iniciada.

- [ ] **Step 7: Commit**

```bash
git add web/src/auth/ web/src/components/Layout.tsx web/src/App.tsx
git commit -m "Agregar login, guard de rol Administrador y layout de navegación"
```

---

### Task 5: Pantalla Unidades (ABM + alícuota)

**Files:**
- Create: `web/src/hooks/useUnits.ts`, `web/src/pages/UnidadesPage.tsx`
- Create: `web/src/hooks/useBuildingResidents.ts`
- Modify: `web/src/App.tsx` (reemplazar el placeholder de `/unidades`)

**Interfaces:**
- Consumes: `useAuth` (Tarea 4) para `buildingId`; `UnitInfo` de `types.ts`
  (Tarea 1); reglas de `units` (Tarea 2).
- Produces: `useUnits(buildingId): { units: UnitInfo[], createUnit(...), updateUnit(...),
  deleteUnit(unitId) }` — consumida por la Tarea 7 (Liquidación) para leer las
  unidades y sus alícuotas.

- [ ] **Step 1: `useBuildingResidents.ts`**

`listResidents`-equivalente: query `where("buildingId", "==", buildingId)` y
`where("role", "==", "INQUILINO")` sobre `users`, para poblar el selector de
inquilino de cada unidad.

- [ ] **Step 2: `useUnits.ts`**

`onSnapshot` sobre `collection(db, "buildings", buildingId, "units")` para la lista
en vivo; `createUnit`/`updateUnit` con `setDoc`/`updateDoc`; `deleteUnit` con
`deleteDoc`.

- [ ] **Step 3: `UnidadesPage.tsx`**

Tabla (estructura de libro contable: filas con separador punteado) con columnas
Unidad / Alícuota % / Inquilino / acciones. Fila de alta al final (inputs + botón
"Agregar"). Si `sum(aliquot) !== 100`, un texto de aviso arriba de la tabla (no
bloqueante) — ej. "Las alícuotas suman 80% — revisá si falta cargar alguna unidad."

- [ ] **Step 4: Verificar a mano**

Con el emulador + semilla de la Tarea 3 corriendo: entrar como Administrador, ir a
Unidades, ver las 2 unidades sembradas (suma 80%, debe mostrar el aviso), agregar una
tercera con alícuota 20 (debe desaparecer el aviso), editar la alícuota de una
existente, borrar una.

- [ ] **Step 5: Commit**

```bash
git add web/src/hooks/useUnits.ts web/src/hooks/useBuildingResidents.ts web/src/pages/UnidadesPage.tsx web/src/App.tsx
git commit -m "Agregar pantalla de gestión de unidades y alícuotas"
```

---

### Task 6: Pantalla Gastos (lista + alta con comprobante)

**Files:**
- Create: `web/src/hooks/useExpenses.ts`, `web/src/pages/GastosPage.tsx`
- Modify: `web/src/App.tsx` (reemplazar el placeholder de `/gastos`)

**Interfaces:**
- Consumes: `useAuth` (Tarea 4); `Expense` de `types.ts` (Tarea 1).
- Produces: `useExpenses(buildingId, period?): { expenses: Expense[], createExpense(...) }`
  — consumida por la Tarea 7 (Liquidación, para sumar los `VERIFICADO` del período) y
  la Tarea 8 (Inicio, KPI de gastos del mes).

- [ ] **Step 1: `useExpenses.ts`**

`onSnapshot` sobre `collection(db, "buildings", buildingId, "expenses")`, ordenado
por `createdAt` descendente. `createExpense(description, category, providerName,
amount, createdByUid, file: File | null)`: si hay `file`, sube a
`receipts/{buildingId}/{uuid}.jpg` vía `uploadBytes`/`getDownloadURL` (mismo path que
`FirebaseExpenseRepository.kt`), arma el `Expense` con `status` derivado de si hay
`receiptUrl`, `setDoc` en un doc nuevo.

- [ ] **Step 2: `GastosPage.tsx`**

Lista de gastos del mes en curso (filtrada client-side por `createdAt` en el mes
actual) con sello de estado (VERIFICADO verde / BLOQUEADO terracota). Botón "Agregar
gasto" abre un formulario (descripción, categoría — mismo enum que
`ExpenseCategory.kt` transcripto a TS, proveedor, monto, drag & drop de imagen).

- [ ] **Step 3: Verificar a mano**

Con emulador + semilla: ver los 2 gastos sembrados (uno VERIFICADO, uno BLOQUEADO);
agregar un gasto nuevo con una imagen de prueba, confirmar que queda VERIFICADO;
agregar uno sin imagen, confirmar que queda BLOQUEADO.

- [ ] **Step 4: Commit**

```bash
git add web/src/hooks/useExpenses.ts web/src/pages/GastosPage.tsx web/src/App.tsx
git commit -m "Agregar pantalla de gastos del mes con alta de comprobantes"
```

---

### Task 7: Pantalla Liquidación (cálculo, emisión, historial)

**Files:**
- Create: `web/src/lib/liquidacion.ts`, `web/src/lib/liquidacion.test.ts`
- Create: `web/src/hooks/useLiquidaciones.ts`, `web/src/pages/LiquidacionPage.tsx`
- Create: `web/src/components/StatusStamp.tsx`
- Modify: `web/src/App.tsx` (reemplazar el placeholder de `/liquidacion`)

**Interfaces:**
- Consumes: `useUnits` (Tarea 5), `useExpenses` (Tarea 6), `Liquidacion`/
  `LiquidacionBreakdownRow`/`UnitInfo`/`Expense` de `types.ts` (Tarea 1).
- Produces: `calcularLiquidacion(units: UnitInfo[], totalExpenses: number):
  LiquidacionBreakdownRow[]` (función pura, Tarea 9/Inicio no la usa pero queda
  disponible); `<StatusStamp status="ok" | "warn" | "pending">` — reusada por la
  Tarea 8 (Inicio) para el estado de pagos.

- [ ] **Step 1: Test de `calcularLiquidacion` (falla primero)**

```typescript
test("reparte el total según la alícuota de cada unidad", () => {
  const units = [
    { id: "a", label: "4B", aliquot: 50, tenantUid: "u1", ownerUid: "" },
    { id: "b", label: "7C", aliquot: 30, tenantUid: "u2", ownerUid: "" },
  ];
  const rows = calcularLiquidacion(units, 10000);
  expect(rows.find(r => r.unitId === "a")?.amount).toBe(5000);
  expect(rows.find(r => r.unitId === "b")?.amount).toBe(3000);
});

test("una unidad sin tenantUid no se excluye del cálculo, pero queda marcable como sin asignar", () => {
  // calcularLiquidacion devuelve la fila igual (amount calculado); quien la llama
  // (emitirLiquidacion) decide no escribir expensesAmount si tenantUid está vacío.
});
```

Run: `cd web && npm test -- liquidacion`
Expected: FAIL (`calcularLiquidacion` no existe todavía).

- [ ] **Step 2: Implementar `calcularLiquidacion`**

`web/src/lib/liquidacion.ts`: función pura, `amount = round2(totalExpenses *
aliquot / 100)` (redondeo a 2 decimales — plata). Sin dependencias de Firebase, para
que el test de arriba corra sin emulador.

- [ ] **Step 3: Verificar que el test pasa**

Run: `cd web && npm test -- liquidacion`
Expected: PASS.

- [ ] **Step 4: `StatusStamp.tsx`**

Componente del "sello" rotado de los mockups validados (ver Global Constraints):
`border: 1.5px solid`, color según `status` (`ok` verde / `warn` terracota /
`pending` gris), `transform: rotate(-4deg)`, texto en mayúsculas monoespaciado.

- [ ] **Step 5: `useLiquidaciones.ts`**

`listLiquidaciones(buildingId): Liquidacion[]` (para el historial, `onSnapshot`
ordenado por `period` descendente); `emitirLiquidacion(buildingId, period,
breakdown: LiquidacionBreakdownRow[], emittedByUid)`: en una sola `writeBatch`,
actualiza `users/{tenantUid}.expensesAmount` para cada fila con `tenantUid` no vacío,
y hace `setDoc` (no `addDoc` — el id del documento es el propio `period`) en
`buildings/{buildingId}/liquidaciones/{period}` con el breakdown completo y
`unassignedUnits` (las filas con `tenantUid` vacío).

- [ ] **Step 6: `LiquidacionPage.tsx`**

Selector de período (default: mes actual, formato `yyyy-MM`). Tabla de
previsualización: para cada unidad de `useUnits`, su alícuota, el monto calculado
(`calcularLiquidacion` sobre la suma de `expenses` `VERIFICADO` de ese período desde
`useExpenses`), y una fila de total. Las unidades sin `tenantUid` se muestran con un
`<StatusStamp status="pending">SIN ASIGNAR</StatusStamp>` en vez de un monto
accionable. Botón "Emitir liquidación" con confirmación (modal simple: "Esto va a
actualizar el monto de expensas de N inquilinos. ¿Confirmar?"). Si el total de gastos
es $0, el botón de confirmación muestra un aviso extra ("No hay gastos verificados
este período — todos los montos van a quedar en $0") en vez de emitir en silencio.
Debajo, lista del historial (`useLiquidaciones`) — cada fila clickeable abre el
detalle (mismo layout de tabla, de sólo lectura, con los datos congelados de ese
`Liquidacion`).

- [ ] **Step 7: Verificar a mano**

Con emulador + semilla: ir a Liquidación del mes sembrado, ver la previsualización
($50.000 de gastos VERIFICADO repartidos 50%/30%, la unidad sin `tenantUid` marcada
SIN ASIGNAR), emitir, confirmar que `users/{tenantUid}.expensesAmount` del inquilino
asignado quedó en $25.000 (consultando el emulador UI o Firestore directamente), y
que aparece en el historial. Volver a emitir el mismo período después de cambiar una
alícuota — confirmar que el documento se sobreescribe (no se duplica).

- [ ] **Step 8: Commit**

```bash
git add web/src/lib/liquidacion.ts web/src/lib/liquidacion.test.ts web/src/hooks/useLiquidaciones.ts web/src/pages/LiquidacionPage.tsx web/src/components/StatusStamp.tsx web/src/App.tsx
git commit -m "Agregar cálculo y emisión de liquidación de expensas por alícuota"
```

---

### Task 8: Pantalla Inicio (KPIs)

**Files:**
- Create: `web/src/hooks/usePayments.ts`, `web/src/hooks/useTickets.ts`,
  `web/src/components/KpiCard.tsx`, `web/src/pages/InicioPage.tsx`
- Modify: `web/src/App.tsx` (reemplazar el placeholder de `/`)

**Interfaces:**
- Consumes: `useUnits` (Tarea 5), `useExpenses` (Tarea 6), `StatusStamp` (Tarea 7).
- Produces: nada que otra tarea consuma — es la pantalla final del flujo de v1.

- [ ] **Step 1: `usePayments.ts`**

`listPaymentsForBuilding`-equivalente: query sobre `buildings/{buildingId}/payments`
filtrando `period == mes actual`, de sólo lectura (no hay acción, ver Global
Constraints: no se aprueba/rechaza desde la web en v1).

- [ ] **Step 2: `useTickets.ts`**

Query sobre `buildings/{buildingId}/tickets` con `status == "A_COBRAR"`, sólo el
conteo.

- [ ] **Step 3: `KpiCard.tsx`**

Tarjeta con borde izquierdo terracota 3px, número en Georgia ~38px, label debajo en
mayúsculas pequeñas — mismo tratamiento que los mockups validados.

- [ ] **Step 4: `InicioPage.tsx`**

4 `KpiCard`: "% Expensas cobradas" (pagos `PAGADO` / total inquilinos con unidad
asignada), "Unidades morosas" (total − pagados), "Gastos verificados del mes" (suma
de `useExpenses` filtrado a `VERIFICADO` + mes actual), "Tickets a cobrar"
(`useTickets`). Debajo, tabla de estado de pagos del mes (unidad, inquilino, `
<StatusStamp>` del estado) — sólo lectura.

- [ ] **Step 5: Verificar a mano**

Con emulador + semilla (+ los datos que dejaron las Tareas 5-7 si se corrieron en
orden): los 4 KPIs muestran números coherentes con lo sembrado/emitido.

- [ ] **Step 6: Commit**

```bash
git add web/src/hooks/usePayments.ts web/src/hooks/useTickets.ts web/src/components/KpiCard.tsx web/src/pages/InicioPage.tsx web/src/App.tsx
git commit -m "Agregar pantalla de inicio con KPIs del edificio"
```

---

### Task 9: Aviso en `VecinosActivity` (Android) cuando el monto viene de una liquidación

**Files:**
- Modify: `app/src/main/java/com/consorciotransparente/app/data/repository/BuildingRepository.kt` (o el repo que corresponda — agregar el método de lectura)
- Modify: `app/src/main/java/com/consorciotransparente/app/data/repository/FirebaseBuildingRepository.kt`
  y `data/repository/mock/MockBuildingRepository.kt`
- Modify: `app/src/main/java/com/consorciotransparente/app/ui/vecinos/VecinosViewModel.kt`,
  `VecinosActivity.kt`
- Modify: `app/src/main/res/layout/item_vecino.xml` (o el layout de fila que use
  `VecinosActivity` — confirmar nombre exacto leyendo el archivo antes de tocarlo)

**Interfaces:**
- Consumes: `buildings/{buildingId}/liquidaciones/{period}` — la misma colección que
  escribe la Tarea 7, leída de sólo lectura desde Android.

- [ ] **Step 1: Leer `VecinosActivity.kt`/`VecinosViewModel.kt`/el layout de fila actuales**

Confirmar el nombre exacto del layout de cada fila de vecino y la estructura de
`VecinoRow` antes de modificar — no asumir nombres sin mirar el archivo real primero
(el repo ya tiene estos archivos, descriptos en el spec de diseño de HU-03 pero
pueden haber cambiado desde entonces).

- [ ] **Step 2: Agregar el método de lectura**

`BuildingRepository`: `suspend fun getLatestLiquidacionTenants(buildingId: String):
Map<String, String>` (tenantUid → period de la liquidación más reciente que lo
incluye) — o la forma que mejor encaje con el `VecinoRow` existente.
`FirebaseBuildingRepository`: query a `liquidaciones` ordenado por `period`
descendente, `limit(1)`, busca el `breakdown` por `tenantUid`.
`MockBuildingRepository`: devuelve mapa vacío (sin red, no hay liquidaciones en mock
— consistente con que esta feature vive enteramente en la web).

- [ ] **Step 3: Mostrar el aviso en la fila**

`VecinosViewModel`: expone el resultado junto a cada `VecinoRow`.
`VecinosActivity`/layout: un `TextView` chico de sólo lectura debajo del campo de
expensas, visible sólo si ese vecino aparece en el mapa — "Calculado por liquidación
de {period}." Mismo estilo que otros textos secundarios de la app
(`?attr/colorTextSecondary`, no `@color/text_secondary` directo — ver CLAUDE.md).

- [ ] **Step 4: Compilar y probar a mano**

Run: `./gradlew assembleMockDebug assembleProdDebug`
Expected: BUILD SUCCESSFUL. En mock, la fila de vecino no muestra el aviso (mapa
vacío) — comportamiento esperado, no un bug: esta feature no tiene datos en mock
porque la liquidación vive sólo en la web/Firestore real.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/consorciotransparente/app/data/repository/BuildingRepository.kt \
  app/src/main/java/com/consorciotransparente/app/data/repository/FirebaseBuildingRepository.kt \
  app/src/main/java/com/consorciotransparente/app/data/repository/mock/MockBuildingRepository.kt \
  app/src/main/java/com/consorciotransparente/app/ui/vecinos/VecinosViewModel.kt \
  app/src/main/java/com/consorciotransparente/app/ui/vecinos/VecinosActivity.kt \
  app/src/main/res/layout/
git commit -m "Avisar en Vecinos cuando un monto de expensas viene de una liquidación web"
```

---

### Task 10: Documentación

**Files:**
- Modify: `CLAUDE.md`, `docs/arquitectura.md`, `docs/roadmap.md`,
  `docs/especificacion-funcional.md`

- [ ] **Step 1: Actualizar los 4 documentos**

`CLAUDE.md`: agregar `web/` a la sección de stack/convenciones (React+TS+Vite+
Tailwind, mismo Firebase, sólo Administrador en v1). `docs/arquitectura.md`: sección
nueva "Web para Administradores" con el modelo de `units`/`liquidaciones` y el
mecanismo de coordinación con `expensesAmount`. `docs/roadmap.md`: agregar la web
como entrega, con su propio estado (no es una HU numerada del TP original, aclarar
eso). `docs/especificacion-funcional.md`: nota en HU-03 sobre que la alícuota
(backlog ítem 4) ya se usa desde la web.

- [ ] **Step 2: Compilar Android una vez más**

Run: `./gradlew assembleMockDebug assembleProdDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Build de la web una vez más**

Run: `cd web && npm run build`
Expected: build exitoso.

- [ ] **Step 4: Commit**

```bash
git add CLAUDE.md docs/arquitectura.md docs/roadmap.md docs/especificacion-funcional.md
git commit -m "Documentar la web para Administradores (liquidación de expensas)"
```
