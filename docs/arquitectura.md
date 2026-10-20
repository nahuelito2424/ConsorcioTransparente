# Arquitectura técnica — Consorcio Transparente

> Documentación técnica de cómo está construido el MVP. Para qué hace cada pantalla y
> por qué, ver [`especificacion-funcional.md`](./especificacion-funcional.md). Para qué
> falta, ver [`roadmap.md`](./roadmap.md).

## 1. Stack

- **Cliente**: Android nativo, Kotlin, Views + `ViewBinding` (sin Compose ni data
  binding), `Navigation Component` para el bottom nav, `LiveData` + `ViewModel`
  (`androidx.lifecycle`), corrutinas de Kotlin (`kotlinx-coroutines-play-services` para
  adaptar las `Task` de Firebase con `.await()`).
- **Backend**: Firebase — Authentication (email/contraseña), Firestore (base de datos
  principal), Storage (fotos de comprobantes/tickets/publicaciones), Cloud Messaging
  (push), Cloud Functions Gen 2 (Node.js, `functions/index.js`) para lógica programada
  que no puede depender de que un cliente tenga la app abierta.
- **Imágenes**: Glide.
- **`applicationId` / `namespace`**: `com.consorciotransparente.app`.
- **SDK**: `minSdk 24`, `compileSdk`/`targetSdk 36`, AGP 8.11, Kotlin 2.0.21.
- **Product flavors**: `prod` (Firebase real) y `mock` (datos en memoria, sin Firebase;
  ver sección 2 y el README para cómo instalarlo). Mismo `applicationId` en los dos.

## 2. Patrón de capas

```
ui/<feature>/                Activity o Fragment (View) + ViewModel + (a veces) Adapter
data/repository/             una interfaz por feature (contrato) + su Firebase*Repository
data/repository/mock/        Mock*Repository: misma interfaz, datos en memoria (flavor "mock")
data/model/                  data classes que mapean 1:1 con documentos de Firestore
util/Resource.kt             sealed class Loading/Success/Error para exponer estado async a la UI
```

No hay capa de dominio ni inyección de dependencias (Hilt/Koin). Cada `XRepository` es
una interfaz chica con dos implementaciones: `FirebaseXRepository` (habla con Firebase,
la de siempre) y `MockXRepository` (datos en memoria, para poder probar la app sin un
proyecto Firebase real). `RepositoryProvider` (`data/repository/RepositoryProvider.kt`)
es un objeto con un val perezoso por repo que elige cuál instanciar según
`BuildConfig.USE_MOCK_DATA` — un `product flavor` (`mock`/`prod`, ver
`app/build.gradle.kts`), no un contenedor de DI. Cada `ViewModel` sigue tomando el
repo como default value en su constructor, ahora resuelto vía `RepositoryProvider`
en vez de instanciado directo:

```kotlin
class FooViewModel(private val repo: FooRepository = RepositoryProvider.foo) : ViewModel()
```

Es una decisión consciente de alcance de MVP/tesis, no un olvido — mantenerla así
salvo que el usuario pida explícitamente introducir DI (Hilt/Koin) o una capa de
dominio de verdad.

Cada pantalla sigue el mismo esqueleto: el `ViewModel` expone `LiveData<Resource<T>>`,
la `Activity`/`Fragment` observa y resuelve `Loading` (spinner) / `Success` (pinta datos)
/ `Error` (mensaje). Ver `util/Resource.kt`.

### 2.1 Modo simple (accesibilidad)

Todas las `Activity` heredan de `ui/BaseActivity` (nunca de `AppCompatActivity`
directo). `BaseActivity` lee `util/SimpleModePrefs` (un booleano en
`SharedPreferences`, por dispositivo, prendido/apagado desde un switch en Más) y, si
está prendido:

- Aplica `fontScale = 1.3` vía `Configuration` en `attachBaseContext` — el mecanismo
  nativo de Android para escalar texto, no hay que tocar cada layout.
- Cambia el tema a `Theme.ConsorcioTransparente.Simple` (`setTheme()` antes de
  `super.onCreate()`), que redefine el atributo `?attr/colorTextSecondary` igual a
  `text_primary` (negro pleno) en vez del gris de siempre.

Por eso los layouts usan `android:textColor="?attr/colorTextSecondary"` para texto
secundario, nunca `@color/text_secondary` directo — si un layout nuevo necesita texto
secundario, seguir ese mismo atributo. `DashboardFragment` además switchea entre el
bloque de métricas de siempre (`normalModeContent`) y 3 botones grandes
(`simpleModeContent`) leyendo el mismo flag.

## 3. Modelo de datos (Firestore)

```
users/{uid}                                   perfil (rol, buildingId, unitId)
buildings/{buildingId}                         config del edificio (HU-09)
buildings/{buildingId}/units/{unitId}          unidad funcional (alícuota, owner/tenant uid)
buildings/{buildingId}/expenses/{expenseId}    gastos, HU-01
buildings/{buildingId}/votes/{voteId}          votaciones, HU-02
buildings/{buildingId}/votes/{voteId}/responses/{voterUid}   un voto por usuario (id = uid)
buildings/{buildingId}/posts/{postId}          muro, HU-05
buildings/{buildingId}/reservations/{id}       reservas, HU-06; id = "{espacio}_{fecha}_{franja}"
buildings/{buildingId}/tickets/{ticketId}      mantenimiento, HU-10
providers/{providerId}                         directorio global (no por edificio), HU-08
providers/{providerId}/reviews/{reviewId}      reseñas de proveedores
```

Dos trucos de modelado vale la pena recordar porque son la base de invariantes de
negocio (no son solo "cómo se guardan los datos"):

- **Un voto por usuario** (HU-02): el id del documento en `responses` es el propio
  `uid` del votante, así un segundo `create` sobre el mismo id es rechazado por
  Firestore/las reglas sin necesitar una transacción ni una consulta previa.
- **Sin doble reserva** (HU-06): el id del documento de reserva es la clave compuesta
  `{espacio}_{fecha}_{franja}`. Tomar una franja ya reservada es, literalmente, intentar
  crear un documento que ya existe.

### Bootstrap de Administrador

No hay pantalla de "alta de edificio por un superadmin": el primer usuario que
configura un edificio (pantalla HU-09, `BuildingConfigActivity`) se auto-promueve a
Administrador de ese edificio en el mismo flujo (`BuildingRepository.createBuilding`).
La regla de seguridad en `firestore.rules` (`match /users/{uid}`) permite ese único
caso de auto-cambio de rol: un `INQUILINO` sin `buildingId` puede pasar a
`ADMINISTRADOR` únicamente al mismo tiempo que se le asigna el `buildingId` de un
edificio recién creado por él. Cualquier otro cambio de rol requiere que lo haga un
Administrador existente.

## 4. Seguridad

Regla de oro del proyecto: **ninguna invariante de negocio con consecuencias reales se
apoya solo en la UI**. Todo lo que importa (comprobante obligatorio, un voto por
persona, moderación previa del muro, no doble reserva, quién puede cambiar el estado de
un ticket) está reforzado en `firestore.rules`. Al tocar una pantalla que valida algo
"importante", revisar si `firestore.rules` necesita el mismo cambio — agregar la
validación solo en Kotlin no alcanza.

`storage.rules` protege la subida de imágenes (comprobantes, fotos de tickets/posts) de
forma análoga, scopeada por `buildingId`.

## 5. Cloud Functions (`functions/index.js`)

Dos funciones programadas (Firebase Functions Gen 2, `onSchedule`), sin funciones HTTP:

- `closeExpiredVotes` — corre cada 15 minutos, cierra votaciones vencidas
  (`isClosed = true`), tabula `finalCounts` y notifica por FCM al tópico
  `building_{buildingId}`.
- `sendDueDateReminders` — corre una vez por día (09:00), compara el día del mes actual
  contra `rentDueDay`/`expensesDueDay` de cada edificio y notifica cuando falten
  exactamente `reminderDaysBefore` días. Simplificación consciente: compara solo el
  número de día del mes, sin ajustar meses de 28-31 días (ver comentario en el archivo).

Ambas dependen de que los clientes estén suscriptos al tópico FCM
`building_{buildingId}` — **todavía no implementado del lado Android** (ver
`roadmap.md`), así que hoy las notificaciones push no le llegan a nadie aunque las
Cloud Functions ya las disparen.

Requieren plan Blaze en el proyecto de Firebase para desplegarse; el resto de la app
funciona sin ellas (solo no hay cierre automático de votación ni recordatorio de
vencimiento).

## 6. Setup de un proyecto de Firebase nuevo

Ver los pasos detallados en el [`README.md`](../README.md#antes-de-compilar-crear-el-proyecto-de-firebase)
de la raíz — no se duplican acá para no tener dos fuentes desactualizables por
separado. Resumen: crear proyecto en la consola, agregar app Android con el paquete
`com.consorciotransparente.app`, reemplazar el `google-services.json` de plantilla,
habilitar Auth/Firestore/Storage (y opcionalmente Functions con plan Blaze), desplegar
reglas e índices con el Firebase CLI.

## 7. Convenciones

- **Idioma**: comentarios de código, mensajes de UI y strings en **español**
  (rioplatense/Argentina); nombres de clases, variables y funciones en inglés siguiendo
  convención Kotlin estándar.
- **Comentarios**: cada modelo/repositorio lleva un comentario corto que dice **qué
  Historia de Usuario implementa y por qué está modelado así** (no qué hace la
  operación, eso ya lo dice el nombre). Mantener ese estilo al agregar código nuevo.
- **Un Repository por feature**, sin lógica de negocio compartida entre repositorios;
  cada uno recibe sus dependencias de Firebase con valor por defecto en el constructor.
- **`Resource<T>`** es el único envoltorio de estado async — no introducir uno nuevo
  (ni Flow/StateFlow) sin que lo pida el usuario explícitamente.
