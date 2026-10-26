# Especificación funcional — Consorcio Transparente

> Este documento reconstruye y ordena la idea original del proyecto (dictada por el
> autor como base para el TP de Planificación de Seminario Final) en un catálogo de
> Historias de Usuario (HU) verificable contra el código. Es la fuente de verdad
> *funcional* del proyecto; la fuente de verdad *técnica* es [`arquitectura.md`](./arquitectura.md)
> y el estado de avance vive en [`roadmap.md`](./roadmap.md).

## 1. Contexto y alcance

**Consorcio Transparente** es una aplicación (Android nativo primero, con la web y iOS
planteados como siguientes plataformas) pensada para la gestión de **propiedades
horizontales** (edificios de departamentos / consorcios) en **CABA (Ciudad Autónoma de
Buenos Aires), Argentina**. El acotamiento geográfico es deliberado: el régimen de
propiedad horizontal, expensas y administración de consorcios está regulado a nivel
nacional por el Código Civil y Comercial (arts. 2037–2072) y, específicamente en CABA,
por la **Ley 941** (Registro Público de Administradores de Consorcios). Mantener el
alcance a una sola jurisdicción evita tener que generalizar reglas de negocio (días
hábiles, formatos de comprobante, requisitos de rendición de cuentas) que varían de
provincia en provincia.

El nombre del proyecto no es casual: el objetivo central es la **transparencia** en el
manejo de fondos comunes (expensas) — de ahí que la primera historia implementada
(HU-01) sea justamente la que impide cargar un gasto sin comprobante.

## 2. Roles

| Rol | Quién lo usa | Permisos clave |
|---|---|---|
| **Administrador** | El consorcio / administración profesional (Ley 941) o quien haga sus veces | Configura el edificio (cobros, vencimientos, espacios comunes), crea votaciones, modera el muro, gestiona proveedores, resuelve tickets, es el único que puede cargar gastos como `VERIFICADO` sin restricciones adicionales de rol |
| **Encargado** *(opcional)* | Personal de mantenimiento del edificio, si existe | Gestiona el ciclo de tickets de mantenimiento (presupuesto, comprobante de compra); mismo nivel que Administrador sobre gastos y tickets, pero no sobre configuración del edificio ni moderación del muro |
| **Inquilino** *(incluye propietarios que habitan la unidad)* | Vecinos del edificio | Paga alquiler/expensas, vota, publica en el muro (sujeto a moderación), reserva espacios comunes, reporta tickets, consulta proveedores |

El primer usuario que da de alta un edificio se auto-promueve a Administrador de ese
edificio (lógica de "bootstrap", ver [`arquitectura.md`](./arquitectura.md#bootstrap-de-administrador)).
Todavía no existe una pantalla de invitación de vecinos — ver [`roadmap.md`](./roadmap.md).

## 3. Catálogo de Historias de Usuario

> Numeración HU-01 a HU-10 tal como aparece en el código (`README.md`, comentarios de
> modelos, `firestore.rules`, `functions/index.js`). El detalle de HU-03 y HU-04 se
> reconstruyó a partir de la descripción original del autor y de las referencias
> cruzadas al pago con Mercado Pago que ya existen en el código de HU-10
> (`TicketRepository.kt`); si al retomar la tesis el enunciado real de esas dos
> historias difiere, actualizar esta sección.

### HU-01 — Auditoría Documental Obligatoria ✅ implementada
Todo gasto del edificio (expensa) debe llevar foto/comprobante para poder marcarse
como `VERIFICADO`. Sin comprobante, el gasto se guarda como `BLOQUEADO` y no entra en
el prorrateo de expensas. Esto está reforzado tanto en la UI como en `firestore.rules`
(no es solo una validación de cliente).

### HU-02 — Votaciones Vinculantes ✅ implementada
El Administrador crea una votación con un tema, una fecha de cierre y **dos o más**
opciones (nunca puede existir una votación de una sola opción). Cada vecino vota una
única vez — garantizado por reglas de seguridad usando el uid del votante como id del
documento de respuesta, no solo por la UI. Al cerrar (automáticamente, vía Cloud
Function programada cada 15 minutos), se tabulan los resultados y se dispara una
notificación push.

### HU-03 — Pago de Alquiler y/o Expensas ✅ implementada
El inquilino recibe una notificación cuando se aproxima el vencimiento configurado por
el Administrador (fecha y anticipación resueltos en HU-09), puede pagar alquiler,
expensas, o ambos —según lo que cobre ese edificio—, por transferencia (con comprobante
que aprueba/rechaza el Administrador) o por Mercado Pago (HU-04). El monto de cada
inquilino (`users/{uid}.rentAmount`/`.expensesAmount`) lo asigna el Administrador: a
mano en `VecinosActivity` (celular, ambos conceptos), o, sólo para expensas, calculado
automáticamente por alícuota desde la web (`/liquidacion` — ver "Prorrateo automático
de expensas por alícuota" más abajo, que ya no es sólo una idea a futuro).

### HU-04 — Integración con Pasarela de Pago (Mercado Pago) ✅ implementada
El inquilino puede pagar alquiler/expensas con Mercado Pago (botón "Pagar con Mercado
Pago" en la pantalla Pagar, HU-03), y también el cobro de reparaciones de
mantenimiento del Escenario 3 de HU-10 (botón en la ficha del ticket, sólo visible para
quien lo reportó): dos Cloud Functions callable generan la preferencia de Checkout Pro
con el monto calculado server-side (una por cada recurso — pago de alquiler/expensas o
ticket), y un único webhook compartido recibe la confirmación y rutea según el
`external_reference` de cada preferencia. El "comprobante" es un registro dentro de la
app (fecha, monto, método) — no un PDF descargable; HU-07 terminó siendo una guía de
texto, no infraestructura de generación de PDFs, así que esto queda como backlog sin
una historia que lo cubra todavía (ver "Sugerencias para versiones futuras"). El
Admin/Encargado sigue teniendo el pago manual como alternativa en ambos flujos, para
los casos donde el pago no lo hace el propio inquilino por Mercado Pago.

### HU-05 — Muro con moderación previa ✅ implementada
Cualquier vecino puede proponer una publicación (texto + imagen opcional), pero nace en
estado `PENDIENTE` y no aparece en el muro hasta que el Administrador la aprueba desde
una cola de moderación. Evita contenido indebido o irrelevante en el espacio común de
comunicación. Reforzado en `firestore.rules`, no solo escondiendo el botón en la UI.

### HU-06 — Reserva de Espacios Comunes ✅ implementada
Turnera por franjas horarias y día para cada amenity del edificio (SUM, parrilla,
pileta, etc., configurables por el Administrador en HU-09). El propio vecino que
reservó puede editar o cancelar su reserva (con un margen de 24hs). El doble booking no
se previene solo en el cliente: el id del documento de reserva es
`{espacio}_{fecha}_{franja}`, así que un segundo intento de tomar la misma franja choca
contra la regla de seguridad de Firestore, no contra una validación que se pueda
saltear.

### HU-07 — Protocolos de emergencia ✅ implementada (guía propia, no PDFs oficiales)
Pantalla con qué hacer ante cada siniestro: incendio, sismo, corte de luz, inundación.
La idea original era una carpeta de descarga de los protocolos oficiales del Gobierno
de la Ciudad (Defensa Civil GCBA, SAME); eso no se pudo concretar porque no hay forma
de descargar y verificar esos PDFs reales desde el entorno de desarrollo, y el
contenido de un protocolo de emergencia no es algo que se pueda inventar. En su lugar
(decisión tomada con el usuario), la app trae una guía general redactada a partir de
lineamientos de seguridad reconocidos, más los teléfonos de emergencia nacionales
(Bomberos, SAME, Policía, Defensa Civil), con un aviso explícito en la pantalla de que
no reemplaza las instrucciones oficiales. Funciona sin conexión por estar bundleada con
la app (no por la caché offline de Firestore, que sigue sin usarse acá — RNF-07 queda
pendiente para si en el futuro se agregan protocolos subidos por el Administrador).

### HU-08 — Directorio de Proveedores ✅ implementada
Ficha de proveedor con cinco datos: teléfono, nombre/perfil, categoría (Plomería,
Electricidad, Gas, Albañilería), comentarios y calificación (estrellas) de vecinos que
ya usaron el servicio. El Administrador da de alta, edita o elimina proveedores —
incluyendo eliminar perfiles con malos comentarios reiterados. Los vecinos consultan el
listado y agregan reseñas después de usar el servicio.

### HU-09 — Configuración de Cobros y Vencimientos ✅ implementada
El Administrador define si el edificio cobra alquiler, expensas o ambos, el día de
vencimiento de cada concepto y con cuánta anticipación se dispara el recordatorio. La
misma pantalla resuelve el alta de edificio (bootstrap de Administrador, ver arriba). El
envío diario del recordatorio corre en una Cloud Function (`sendDueDateReminders`).

### HU-10 — Gestión de Incidentes de Mantenimiento ✅ implementada
Ciclo completo de un ticket de reparación:

1. **Reportado** — el inquilino describe el problema, con foto opcional (no
   obligatoria), eligiendo una categoría (Plomería, Electricidad, Gas, Albañilería,
   Otro). El Encargado/Administrador recibe notificación del nuevo ticket
   (`onTicketCreated`, tópico `building_{buildingId}_staff`).
2. **En Presupuesto** — el Encargado/Administrador revisa el problema con el inquilino
   y carga un monto, o lo deja "a cotizar" si todavía no hay precio.
3. **A Cobrar** — se sube el comprobante de compra de materiales (ferretería u otro
   proveedor). El Escenario 3 de esta historia ("generar un cobro a través de Mercado
   Pago", = HU-04) se salda de dos formas: el inquilino que reportó el ticket paga con
   Mercado Pago, o el Encargado/Administrador sube un comprobante de pago manual —
   pensado para reparaciones de áreas comunes que no le corresponden a un vecino
   puntual, no como reemplazo del flujo del inquilino.
4. **Resuelto** — queda archivado con el comprobante de pago adjunto (o, si se pagó por
   Mercado Pago, con el id de ese pago en vez de una imagen).

## 4. Reglas de negocio transversales

- **Multi-tenant por edificio**: casi todos los datos (gastos, votaciones, muro,
  reservas, tickets) cuelgan de `buildings/{buildingId}/...`; un usuario solo ve datos
  de su propio edificio (`belongsToBuilding` en `firestore.rules`).
- **La UI nunca es la única barrera de seguridad**: cada regla de negocio con
  consecuencias (comprobante obligatorio, un voto por persona, moderación previa, no
  doble reserva, permisos por rol) está reforzada en `firestore.rules`, para que no se
  pueda saltear con un cliente modificado o llamadas directas a la API de Firestore.
- **CABA / Argentina como alcance único**: fechas, moneda (ARS) y flujo de pago asumen
  este contexto. Expandir a otras jurisdicciones (o a alquiler temporario, por ejemplo)
  queda fuera de alcance del MVP.

## 5. Consideraciones legales (CABA / Argentina)

Esto es una guía orientativa para decisiones de producto, **no asesoramiento legal**;
conviene validarla con un profesional antes de la defensa de la tesis si el jurado
pregunta por el marco normativo.

- **Código Civil y Comercial de la Nación, arts. 2037–2072** (Propiedad Horizontal):
  regula la existencia del consorcio, el reglamento de copropiedad, las asambleas y la
  obligación de expensas comunes. Las "votaciones vinculantes" (HU-02) son una
  digitalización de lo que el Código llama asamblea/decisión de copropietarios — vale
  la pena, en la tesis, aclarar que la votación en la app es una **herramienta de
  consulta y registro**, no un reemplazo legal del acta de asamblea salvo que el
  reglamento de copropiedad del edificio lo admita expresamente.
- **Ley 941 (CABA)**: exige que los administradores de consorcios estén inscriptos en
  el Registro Público de Administradores y establece obligaciones de **rendición de
  cuentas** documentada. HU-01 (Auditoría Documental Obligatoria) está directamente
  alineada con este requisito: un gasto sin comprobante no debería poder rendirse.
- **Ley 25.326 (Protección de Datos Personales)**: la app guarda datos de contacto
  (teléfono, email), fotos de incidentes dentro de domicilios y, eventualmente, datos
  de pago. Ninguna credencial de pago debería tocar los servidores propios de la app —
  eso lo resuelve delegar el cobro al Checkout de Mercado Pago (HU-04) en lugar de
  construir un formulario de tarjeta propio.
- **Fondo de reserva (art. 2050 CCyC)**: el Código exige que el consorcio mantenga un
  fondo de reserva. No está modelado todavía; ver sugerencia en la sección 6.

## 6. Sugerencias para versiones futuras

Ideas evaluadas contra el contexto de CABA y la propuesta de valor ("transparencia"),
para considerar más allá de las 10 HU ya definidas:

1. ~~**Rendición de cuentas / balance descargable**~~ — ✅ implementado, en la app
   Android: pantalla de solo lectura para los tres roles con el desglose mensual de
   `Expense` `VERIFICADO` por categoría (más aviso de `BLOQUEADO`s excluidos del
   total), detalle por gasto con su comprobante, y exportación a PDF nativo
   (`android.graphics.pdf.PdfDocument`) compartible por fuera de la app. Ver
   `docs/superpowers/specs/2026-10-24-rendicion-cuentas-design.md`.
2. **Fondo de reserva**: campo en `Building` + pantalla de seguimiento de aportes y uso,
   alineado con el art. 2050 CCyC.
3. **Libro de actas digital**: historial de votaciones cerradas con sus resultados y
   fecha, exportable — un registro de decisiones del consorcio con valor probatorio
   informal.
4. ~~**Prorrateo automático de expensas por alícuota**~~ — ✅ implementado, en la web
   para Administradores (`/liquidacion`), no en la app: el Admin carga las unidades con
   su alícuota (`buildings/{id}/units`, el modelo existía desde HU-03 pero no lo usaba
   nadie) y la web calcula y emite el monto de expensas de cada inquilino a partir de
   los gastos verificados del mes. Ver `docs/superpowers/specs/2026-10-23-web-admin-liquidacion-design.md`
   y la sección 8 de `arquitectura.md`.
5. **Contactos de emergencia oficiales**: HU-07 ya incluye Bomberos/SAME/Policía/Defensa
   Civil fijos en la pantalla de Protocolos; falta la línea de la empresa de gas/luz
   (varía por proveedor/zona, no se puede dejar fija para cualquier edificio de CABA).
6. **Estado de cuenta por unidad**: historial de pagos de alquiler/expensas por unidad
   funcional, visible para el Administrador (morosidad) y para el propio inquilino (sus
   propios pagos).
7. **Notificación de nuevo ticket / cambio de estado** (ya anotado como pendiente en
   `roadmap.md`): hoy hay que entrar a la app para enterarse.
8. **Verificación de email / segundo factor** para el rol Administrador, dado que
   maneja fondos y aprueba pagos.
