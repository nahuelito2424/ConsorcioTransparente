# Consorcio Transparente — Web (Administrador)

Primera versión de la plataforma web, sólo para el rol Administrador: gestión de
unidades (alícuota), carga de gastos del mes y liquidación automática de expensas.
Diseño completo en
[`docs/superpowers/specs/2026-10-23-web-admin-liquidacion-design.md`](../docs/superpowers/specs/2026-10-23-web-admin-liquidacion-design.md).

Habla contra el **mismo proyecto de Firebase** que la app Android (Auth, Firestore,
Storage) — no tiene backend propio.

## Stack

React 19 + TypeScript + Vite, Tailwind CSS v4 (sin librería de componentes),
`react-router-dom`, Firebase JS SDK. Hostea en Firebase Hosting, al lado de las
Cloud Functions existentes (`firebase deploy --only hosting`).

## Antes de arrancar

### Opción A — Firebase Local Emulator Suite (recomendado para desarrollar)

No hace falta un proyecto de Firebase real. Desde la raíz del repo:

```bash
firebase emulators:start
```

Levanta Auth (puerto 9099), Firestore (8080), Storage (9199) y una UI en
http://localhost:4000. En otra terminal, sembrá datos de prueba (ver
`scripts/seed-emulator.ts`):

```bash
cd web
npm run seed
```

Copiá `.env.example` a `.env` — con `VITE_USE_EMULATOR=true` ya alcanza, los demás
valores de `VITE_FIREBASE_*` pueden quedar como están (el emulador no los valida).

### Opción B — Proyecto de Firebase real

Copiá `.env.example` a `.env` y completá los 5 valores `VITE_FIREBASE_*` con la
config web de tu proyecto (consola de Firebase → ⚙️ Configuración del proyecto →
pestaña General → "Tus apps" → agregar app Web si no existe una todavía). Dejá
`VITE_USE_EMULATOR=false`. Mismo espíritu que `app/google-services.json`: nunca se
commitea un `.env` con credenciales reales.

## Desarrollo

```bash
cd web
npm install
npm run dev        # servidor de desarrollo
npm run build       # build de producción a web/dist
npm test             # Vitest (cálculo de liquidación + reglas de Firestore)
```

Los tests de reglas de Firestore (`tests/firestore-rules.test.ts`) necesitan el
emulador de Firestore corriendo — se pueden correr con:

```bash
firebase emulators:exec --only firestore "cd web && npm test"
```

## Desplegar

```bash
cd web && npm run build
cd ..
firebase deploy --only hosting
```
