# CLAUDE.md

Contexto para trabajar en este repo. Documentación completa en [`docs/`](./docs/) y en
[`README.md`](./README.md) — este archivo es el resumen orientado a cómo colaborar acá,
no un reemplazo de esos documentos.

## Qué es este proyecto

**Consorcio Transparente** es el MVP de una tesis universitaria (TP de Planificación de
Seminario Final): una app para la gestión de propiedades horizontales
(edificios/consorcios) en **CABA, Argentina**: pago de alquiler/expensas, votaciones
vinculantes, muro con moderación, reserva de espacios comunes, gestión de incidentes de
mantenimiento, directorio de proveedores y protocolos de emergencia. Tres roles:
Administrador, Encargado (opcional) y Inquilino. Dos plataformas, mismo backend de
Firebase: la app Android (Kotlin, `app/`, los tres roles) y, desde esta v1, una web
sólo para Administradores (React, `web/`) — ver "Web (Administradores)" más abajo.

El trabajo está organizado en 10 Historias de Usuario (HU-01 a HU-10). Antes de tocar
una pantalla o agregar una funcionalidad, leer:

- [`docs/especificacion-funcional.md`](./docs/especificacion-funcional.md) — qué hace
  cada historia, roles, reglas de negocio, marco legal de CABA, ideas futuras.
- [`docs/arquitectura.md`](./docs/arquitectura.md) — cómo está construido: capas,
  modelo de datos de Firestore, seguridad, Cloud Functions, convenciones.
- [`docs/roadmap.md`](./docs/roadmap.md) — qué está implementado y qué falta, en orden
  sugerido.

## Estado actual (resumen — la tabla completa está en `docs/roadmap.md`)

Las 10 Historias de Usuario (HU-01 a HU-10) de la app Android están implementadas,
incluida la mejora de Mercado Pago para reparaciones. Sobre eso se agregó la v1 de la
web para Administradores (liquidación automática de expensas por alícuota — ver abajo).
Dos detalles a tener en cuenta al tocar código relacionado:
- HU-07 (protocolos de emergencia) no terminó siendo PDFs oficiales descargados: es una
  guía de texto redactada a partir de lineamientos generales, con el disclaimer
  correspondiente en la pantalla (ver `docs/especificacion-funcional.md`).
- El monto de expensas de cada inquilino (`users/{uid}.expensesAmount`) ahora puede
  venir de dos lugares no coordinados entre sí: tipeado a mano en `VecinosActivity`
  (Android) o calculado y emitido desde `/liquidacion` (web). `VecinosActivity` avisa
  cuándo un monto viene de una liquidación, pero no impide pisarlo a mano — ver
  "Web (Administradores)".

## Stack y convenciones (detalle en `docs/arquitectura.md`)

- Kotlin, Views + ViewBinding (sin Compose), Navigation Component, MVVM con
  `ViewModel` + `LiveData`, corrutinas. Sin capa de dominio ni inyección de
  dependencias (Hilt/Koin).
- Cada `XRepository` es una interfaz en `data/repository/` con dos implementaciones:
  `FirebaseXRepository` (real) y `MockXRepository` en `data/repository/mock/` (datos en
  memoria, sin Firebase). `RepositoryProvider` elige cuál instanciar según
  `BuildConfig.USE_MOCK_DATA`, que fija el product flavor (`prod`/`mock`, ver
  `app/build.gradle.kts`). Cada `ViewModel` sigue tomando el repo como default value en
  su constructor (`= RepositoryProvider.foo`), no un contenedor de DI. El flavor `mock`
  se loguea con `admin@mock.com` / `encargado@mock.com` / `inquilino@mock.com`
  (cualquier contraseña) — ver README para el detalle.
- **Modo simple** (accesibilidad): toggle en Más que guarda `SimpleModePrefs`
  (`SharedPreferences` del dispositivo). Todas las Activity heredan de `ui/BaseActivity`
  en vez de `AppCompatActivity` directo; cuando el modo está prendido, aplica letra más
  grande app-wide (`Configuration.fontScale` en `attachBaseContext`) y cambia el texto
  secundario a negro pleno (tema `Theme.ConsorcioTransparente.Simple`, atributo
  `?attr/colorTextSecondary` — no usar `@color/text_secondary` directo en layouts
  nuevos). `DashboardFragment`/`fragment_dashboard.xml` además muestra 3 botones
  gigantes en vez de las tarjetas de métricas cuando el modo está prendido.
- Firebase: Auth, Firestore, Storage, Cloud Messaging, Cloud Functions (Node.js,
  `functions/index.js`: `onSchedule`, triggers de Firestore `onDocumentCreated`/
  `onDocumentUpdated`, una función callable (`onCall`) y una HTTP pública (`onRequest`,
  el webhook de Mercado Pago). Sólo las implementaciones `FirebaseXRepository` hablan
  con esto; las `MockXRepository` no.
- Un `Repository` (interfaz + Firebase/Mock) por feature, un `data class` por colección
  en `data/model/`, una pantalla por carpeta en `ui/<feature>/`. Estado async siempre con
  el sealed class `util/Resource.kt` (`Loading`/`Success`/`Error`) — no introducir otro
  patrón de estado sin que el usuario lo pida.
- **Idioma**: comentarios de código y strings de UI en español (Argentina); nombres de
  código en inglés (convención Kotlin estándar).
- Cada modelo/repositorio documenta qué HU implementa y **por qué** está modelado así
  (no qué hace la función — eso ya lo dice el nombre). Mantener ese estilo.
- **Ninguna regla de negocio con consecuencias reales vive solo en la UI.** Si agregás
  o cambiás una validación importante en una pantalla, revisá si `firestore.rules` (o
  `storage.rules`) necesita el mismo cambio.

## Web (Administradores)

`web/` — React 19 + TypeScript + Vite + Tailwind CSS v4 (sin librería de componentes:
estaban haciendo que todo este tipo de producto se vea igual), `react-router-dom`,
Firebase JS SDK contra el **mismo proyecto de Firebase** que la app Android. Sólo rol
`ADMINISTRADOR` — cualquier otro rol se desloguea al instante (ver
`src/auth/useAuth.ts`). v1 = una sola cosa: liquidación automática de expensas por
alícuota (`buildings/{id}/units`, hasta ahora sin uso), reemplazando la carga manual de
`VecinosActivity`. Al emitir, escribe `users/{uid}.expensesAmount` — el mismo campo que
ya lee todo el flujo de pagos de Android, así que esa parte de Android no se tocó.
Detalle completo: `docs/superpowers/specs/2026-10-23-web-admin-liquidacion-design.md`.

Para desarrollar sin un proyecto de Firebase real: Firebase Local Emulator Suite
(`firebase emulators:start --project demo-consorcio-transparente` desde la raíz,
`cd web && npm run seed` para datos de prueba) — ver `web/README.md`. A diferencia de
Android, acá sí hay tests automatizados: `web/src/lib/liquidacion.test.ts` (cálculo
puro, Vitest) y `web/tests/firestore-rules.test.ts` (reglas de Firestore nuevas,
`@firebase/rules-unit-testing` contra el emulador real).

## Antes de compilar

`app/google-services.json` en el repo es una plantilla con valores falsos. Sin
reemplazarlo por uno real de un proyecto de Firebase propio, la app compila pero ningún
llamado a Firebase funciona — salvo que uses el flavor `mock` (ver abajo), que no lo
necesita. Pasos completos para el proyecto real en
[`README.md`](./README.md#antes-de-compilar-crear-el-proyecto-de-firebase).

```bash
./gradlew assembleProdDebug   # contra Firebase real
./gradlew assembleMockDebug   # sin Firebase, con datos de prueba en memoria
```

`gradle.properties` fija `org.gradle.java.home` al JBR de Android Studio de esta
máquina (AGP 8.11 necesita Java 17+); si el build falla por versión de Java en otra
PC, ajustar o borrar esa línea.

No hay tests automatizados en la app Android (carpetas `test`/`androidTest` vacías del
template) — la verificación es compilar + probar a mano en el emulador. La web sí tiene
tests (ver "Web (Administradores)" arriba).

## Cómo trabajar en este repo

- El repo ya tiene git inicializado, con commit inicial hecho. Dos ramas principales:
  `main` (producción, estable) y `develop` (integración de desarrollo). El trabajo del
  día a día se hace en `develop` (o en ramas de feature sobre `develop`) y se mergea a
  `main` para lo que esté listo para producción.
- Al implementar una historia pendiente, actualizar la tabla de estado en
  `docs/roadmap.md` y el comentario de clase correspondiente (mismo estilo que las HU
  ya implementadas), no solo el código.
- Si una decisión de esta guía queda obsoleta (por ejemplo, se agrega DI o se cambia el
  wrapper de estado), actualizar este archivo en el mismo cambio.
