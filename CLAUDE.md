# CLAUDE.md

Contexto para trabajar en este repo. Documentación completa en [`docs/`](./docs/) y en
[`README.md`](./README.md) — este archivo es el resumen orientado a cómo colaborar acá,
no un reemplazo de esos documentos.

## Qué es este proyecto

**Consorcio Transparente** es el MVP nativo Android (Kotlin + Firebase) de una tesis
universitaria (TP de Planificación de Seminario Final). Es una app para la gestión de
propiedades horizontales (edificios/consorcios) en **CABA, Argentina**: pago de
alquiler/expensas, votaciones vinculantes, muro con moderación, reserva de espacios
comunes, gestión de incidentes de mantenimiento, directorio de proveedores y protocolos
de emergencia. Tres roles: Administrador, Encargado (opcional) y Inquilino.

El trabajo está organizado en 10 Historias de Usuario (HU-01 a HU-10). Antes de tocar
una pantalla o agregar una funcionalidad, leer:

- [`docs/especificacion-funcional.md`](./docs/especificacion-funcional.md) — qué hace
  cada historia, roles, reglas de negocio, marco legal de CABA, ideas futuras.
- [`docs/arquitectura.md`](./docs/arquitectura.md) — cómo está construido: capas,
  modelo de datos de Firestore, seguridad, Cloud Functions, convenciones.
- [`docs/roadmap.md`](./docs/roadmap.md) — qué está implementado y qué falta, en orden
  sugerido.

## Estado actual (resumen — la tabla completa está en `docs/roadmap.md`)

Implementadas: HU-01 (Facturas/Auditoría), HU-02 (Votaciones), HU-05 (Muro), HU-06
(Reservas), HU-09 (Config. de edificio), HU-10 (Tickets de mantenimiento, con pago
manual como reemplazo temporal de Mercado Pago). Pendientes: HU-03 (pago de
alquiler/expensas), HU-04 (integración Mercado Pago), HU-07 (protocolos PDF), HU-08
(UI de proveedores — el modelo ya existe).

## Stack y convenciones (detalle en `docs/arquitectura.md`)

- Kotlin, Views + ViewBinding (sin Compose), Navigation Component, MVVM con
  `ViewModel` + `LiveData`, corrutinas. Sin capa de dominio ni inyección de
  dependencias — los `Repository` se instancian con valores por defecto de Firebase.
- Firebase: Auth, Firestore, Storage, Cloud Messaging, Cloud Functions (Node.js,
  `functions/index.js`, solo `onSchedule`, sin endpoints HTTP).
- Un `Repository` por feature en `data/repository/`, un `data class` por colección en
  `data/model/`, una pantalla por carpeta en `ui/<feature>/`. Estado async siempre con
  el sealed class `util/Resource.kt` (`Loading`/`Success`/`Error`) — no introducir otro
  patrón de estado sin que el usuario lo pida.
- **Idioma**: comentarios de código y strings de UI en español (Argentina); nombres de
  código en inglés (convención Kotlin estándar).
- Cada modelo/repositorio documenta qué HU implementa y **por qué** está modelado así
  (no qué hace la función — eso ya lo dice el nombre). Mantener ese estilo.
- **Ninguna regla de negocio con consecuencias reales vive solo en la UI.** Si agregás
  o cambiás una validación importante en una pantalla, revisá si `firestore.rules` (o
  `storage.rules`) necesita el mismo cambio.

## Antes de compilar

`app/google-services.json` en el repo es una plantilla con valores falsos. Sin
reemplazarlo por uno real de un proyecto de Firebase propio, la app compila pero ningún
llamado a Firebase funciona. Pasos completos en
[`README.md`](./README.md#antes-de-compilar-crear-el-proyecto-de-firebase).

```bash
./gradlew assembleDebug
```

`gradle.properties` fija `org.gradle.java.home` al JBR de Android Studio de esta
máquina (AGP 8.11 necesita Java 17+); si el build falla por versión de Java en otra
PC, ajustar o borrar esa línea.

No hay tests automatizados todavía (carpetas `test`/`androidTest` vacías del template).

## Cómo trabajar en este repo

- El repo **no tiene git inicializado todavía** (`git init` si el usuario pide empezar
  a versionar).
- Al implementar una historia pendiente, actualizar la tabla de estado en
  `docs/roadmap.md` y el comentario de clase correspondiente (mismo estilo que las HU
  ya implementadas), no solo el código.
- Si una decisión de esta guía queda obsoleta (por ejemplo, se agrega DI o se cambia el
  wrapper de estado), actualizar este archivo en el mismo cambio.
