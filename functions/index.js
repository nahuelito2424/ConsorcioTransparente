/**
 * Cloud Functions de Consorcio Transparente.
 *
 * closeExpiredVotes implementa el lado "servidor" de HU-02 Escenario 2 (Cierre
 * Automático): la app ya calcula el cierre en el cliente comparando fechas
 * (ver VoteRepository.isClosed en la app Android), pero eso es sólo una lectura
 * "de prestado" — hasta que esta función corre, el documento sigue con
 * isClosed = false. Esta función es la que de verdad marca isClosed = true y
 * dispara la notificación push, para que el cierre no dependa de que alguien
 * tenga la app abierta en ese momento.
 *
 * sendDueDateReminders implementa HU-09 Escenario 2 (Notificación Anticipada):
 * corre una vez por día y, para cada edificio, compara el día de hoy contra el
 * día de vencimiento de alquiler y/o expensas, avisando cuando falten
 * exactamente `reminderDaysBefore` días. Simplificación consciente para el MVP:
 * compara sólo el número de día del mes, sin ajustar meses de 28-31 días —
 * en un mes más corto que el día de vencimiento configurado, ese mes no
 * dispara el recordatorio (queda anotado como mejora futura).
 *
 * onTicketCreated/onTicketStatusChanged implementan HU-10: avisan a Encargado/
 * Administrador cuando se reporta un ticket nuevo, y al inquilino que lo reportó
 * cuando cambia de estado. Usan dos tópicos nuevos que la app suscribe en
 * MainActivity.onResume: "building_{buildingId}_staff" (sólo Encargado/Admin) y
 * "user_{uid}" (un único usuario) — separados del tópico general
 * "building_{buildingId}" porque estas notificaciones no son para todo el edificio.
 *
 * No se despliegan solas: hay que correr `firebase deploy --only functions` una
 * vez configurado el proyecto de Firebase (ver README.md de la raíz).
 */

const { onSchedule } = require("firebase-functions/v2/scheduler");
const { onDocumentCreated, onDocumentUpdated } = require("firebase-functions/v2/firestore");
const { onCall, onRequest, HttpsError } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const { MercadoPagoConfig, Preference, Payment, WebhookSignatureValidator } = require("mercadopago");

initializeApp();

const mercadoPagoAccessToken = defineSecret("MERCADO_PAGO_ACCESS_TOKEN");
const mercadoPagoWebhookSecret = defineSecret("MERCADO_PAGO_WEBHOOK_SECRET");

exports.closeExpiredVotes = onSchedule("every 15 minutes", async () => {
  const db = getFirestore();
  const now = new Date();

  const buildingsSnap = await db.collection("buildings").get();

  for (const buildingDoc of buildingsSnap.docs) {
    const buildingId = buildingDoc.id;
    const votesSnap = await db
      .collection("buildings").doc(buildingId)
      .collection("votes")
      .where("isClosed", "==", false)
      .where("closesAt", "<=", now)
      .get();

    for (const voteDoc of votesSnap.docs) {
      const vote = voteDoc.data();

      // Tabula los porcentajes de aprobación (Escenario 2) antes de cerrar.
      const responsesSnap = await voteDoc.ref.collection("responses").get();
      const counts = new Array((vote.options || []).length).fill(0);
      responsesSnap.forEach((r) => {
        const idx = r.data().optionIndex;
        if (typeof idx === "number" && idx >= 0 && idx < counts.length) counts[idx]++;
      });

      await voteDoc.ref.update({ isClosed: true, finalCounts: counts });

      // Best-effort: notifica al tópico del edificio. Requiere que la app
      // suscriba a cada usuario al tópico `building_{buildingId}` al iniciar
      // sesión (todavía no implementado del lado de la app Android).
      try {
        await getMessaging().send({
          topic: `building_${buildingId}`,
          notification: {
            title: "Votación cerrada",
            body: `"${vote.title}" ya tiene resultado. Entrá a la app para verlo.`,
          },
        });
      } catch (err) {
        console.warn(`No se pudo notificar el cierre de la votación ${voteDoc.id}:`, err.message);
      }
    }
  }
});

exports.sendDueDateReminders = onSchedule("every day 09:00", async () => {
  const db = getFirestore();
  const today = new Date().getDate();

  const buildingsSnap = await db.collection("buildings").get();

  for (const buildingDoc of buildingsSnap.docs) {
    const building = buildingDoc.data();
    const anticipation = building.reminderDaysBefore ?? 5;

    const concepts = [];
    if (building.chargesRent && today + anticipation === building.rentDueDay) {
      concepts.push("el alquiler");
    }
    if (building.chargesExpenses && today + anticipation === building.expensesDueDay) {
      concepts.push("las expensas");
    }
    if (concepts.length === 0) continue;

    try {
      await getMessaging().send({
        topic: `building_${buildingDoc.id}`,
        notification: {
          title: "Vencimiento próximo",
          body: `En ${anticipation} día(s) vence ${concepts.join(" y ")}. Pagalo desde la app.`,
        },
      });
    } catch (err) {
      console.warn(`No se pudo notificar el vencimiento del edificio ${buildingDoc.id}:`, err.message);
    }
  }
});

exports.onTicketCreated = onDocumentCreated(
  "buildings/{buildingId}/tickets/{ticketId}",
  async (event) => {
    const ticket = event.data?.data();
    if (!ticket) return;

    try {
      await getMessaging().send({
        topic: `building_${event.params.buildingId}_staff`,
        notification: {
          title: "Nuevo ticket de mantenimiento",
          body: `${ticket.category || "Incidente"}: ${ticket.description || ""}`.trim(),
        },
      });
    } catch (err) {
      console.warn(`No se pudo notificar el ticket nuevo ${event.params.ticketId}:`, err.message);
    }
  }
);

exports.onTicketStatusChanged = onDocumentUpdated(
  "buildings/{buildingId}/tickets/{ticketId}",
  async (event) => {
    const before = event.data?.before.data();
    const after = event.data?.after.data();
    if (!before || !after || before.status === after.status || !after.reportedByUid) return;

    const statusLabels = {
      REPORTADO: "Reportado",
      EN_PRESUPUESTO: "En presupuesto",
      A_COBRAR: "A cobrar",
      RESUELTO: "Resuelto",
    };

    try {
      await getMessaging().send({
        topic: `user_${after.reportedByUid}`,
        notification: {
          title: "Tu ticket cambió de estado",
          body: `Ahora está "${statusLabels[after.status] || after.status}".`,
        },
      });
    } catch (err) {
      console.warn(`No se pudo notificar el cambio de estado del ticket ${event.params.ticketId}:`, err.message);
    }
  }
);

/**
 * HU-04: genera una preferencia de Checkout Pro para que el inquilino pague por
 * Mercado Pago. El monto se calcula acá (server-side) a partir de lo que el
 * Administrador le asignó al usuario en Firestore — nunca se confía en un monto
 * mandado por el cliente (mismo criterio que "monto congelado" ya usa la
 * transferencia). external_reference = "{buildingId}_{uid}_{period}": es lo único
 * que mercadoPagoWebhook usa después para encontrar este Payment.
 */
exports.createMercadoPagoPreference = onCall(
  { secrets: [mercadoPagoAccessToken] },
  async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Tenés que iniciar sesión.");
    const { buildingId, period } = request.data || {};
    if (!buildingId || !period || !/^\d{4}-\d{2}$/.test(period)) {
      throw new HttpsError("invalid-argument", "Falta buildingId o period, o period no tiene el formato yyyy-MM.");
    }

    const uid = request.auth.uid;
    const db = getFirestore();

    const [userSnap, buildingSnap] = await Promise.all([
      db.collection("users").doc(uid).get(),
      db.collection("buildings").doc(buildingId).get(),
    ]);
    if (!userSnap.exists || !buildingSnap.exists) {
      throw new HttpsError("failed-precondition", "No se encontró tu perfil o tu edificio.");
    }
    const user = userSnap.data();
    // El usuario tiene que pertenecer a este edificio — si no, un llamado directo a
    // esta función (fuera de la UI) podría escribir un Payment bajo un edificio ajeno.
    if (user.buildingId !== buildingId) {
      throw new HttpsError("permission-denied", "No pertenecés a ese edificio.");
    }
    const building = buildingSnap.data();
    const breakdown = {};
    if (building.chargesRent && user.rentAmount > 0) breakdown.alquiler = user.rentAmount;
    if (building.chargesExpenses && user.expensesAmount > 0) breakdown.expensas = user.expensesAmount;
    const amount = Object.values(breakdown).reduce((a, b) => a + b, 0);
    if (amount <= 0) throw new HttpsError("failed-precondition", "No tenés un monto asignado.");

    const paymentId = `${uid}_${period}`;
    const paymentRef = db.collection("buildings").doc(buildingId)
      .collection("payments").doc(paymentId);

    // No pisar un pago ya resuelto (PAGADO) ni uno de transferencia en curso — sólo se
    // puede crear una preferencia nueva si no hay Payment todavía, o si el que hay es
    // un intento de Mercado Pago anterior que nunca se confirmó (reintento tras cerrar
    // el checkout sin pagar: ver PagarActivity.render()).
    await db.runTransaction(async (tx) => {
      const existingSnap = await tx.get(paymentRef);
      if (existingSnap.exists) {
        const existing = existingSnap.data();
        const canOverwrite = existing.status === "A_VERIFICAR" && existing.method === "MERCADO_PAGO";
        if (!canOverwrite) {
          throw new HttpsError("failed-precondition", "Ya tenés un pago registrado para este período.");
        }
      }
      tx.set(paymentRef, {
        uid,
        period,
        breakdown,
        amount,
        method: "MERCADO_PAGO",
        status: "A_VERIFICAR",
        createdAt: new Date(),
      });
    });

    let preference;
    try {
      const client = new MercadoPagoConfig({ accessToken: mercadoPagoAccessToken.value() });
      preference = await new Preference(client).create({
        body: {
          items: [{ title: "Expensas/alquiler", quantity: 1, unit_price: amount }],
          external_reference: `${buildingId}_${uid}_${period}`,
          notification_url: process.env.MERCADO_PAGO_WEBHOOK_URL || undefined,
        },
      });
    } catch (err) {
      console.error("Error creando la preferencia de Mercado Pago:", err.message);
      throw new HttpsError("unavailable", "No se pudo conectar con Mercado Pago. Probá de nuevo en un rato.");
    }

    // sandbox_init_point sólo viene poblado cuando se usan credenciales de prueba de
    // Mercado Pago; con credenciales de producción, init_point ya es la URL correcta.
    // Nunca preferir sandbox "si existe": con cuenta real, Mercado Pago puede devolver
    // ambos campos y el fallback dejaría a todo el mundo pagando en el ambiente de
    // prueba (ver revisión final del plan de HU-04).
    return { initPoint: preference.init_point };
  }
);

/**
 * HU-10 Escenario 3: genera una preferencia de Checkout Pro para que el inquilino que
 * reportó el ticket pague el costo de la reparación. A diferencia de
 * createMercadoPagoPreference, no escribe nada en Firestore acá — el Ticket ya existe
 * en A_COBRAR, y no hay estado especulativo que crear: cerrar el checkout sin pagar y
 * reintentar más tarde es seguro porque el ticket nunca sale de A_COBRAR hasta que el
 * webhook confirma. external_reference = "ticket_{buildingId}_{ticketId}" (prefijo
 * "ticket_" para que mercadoPagoWebhook lo distinga de un pago de alquiler/expensas).
 */
exports.createTicketMercadoPagoPreference = onCall(
  { secrets: [mercadoPagoAccessToken] },
  async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Tenés que iniciar sesión.");
    const { buildingId, ticketId } = request.data || {};
    if (!buildingId || !ticketId) throw new HttpsError("invalid-argument", "Falta buildingId o ticketId.");

    const ticketRef = getFirestore()
      .collection("buildings").doc(buildingId)
      .collection("tickets").doc(ticketId);
    const ticketSnap = await ticketRef.get();
    if (!ticketSnap.exists) throw new HttpsError("not-found", "No se encontró el ticket.");
    const ticket = ticketSnap.data();

    // Sólo quien reportó el ticket puede pagarlo (decisión de producto: el
    // Admin/Encargado sigue usando el pago manual, ver TicketRepository.kt) — nunca se
    // confía en qué botón muestra la UI, se valida de nuevo acá. Chequea también el rol:
    // un Encargado/Administrador que reportó su propio ticket no debe poder pagarlo por
    // Mercado Pago (Review Focus del plan), no sólo no ver el botón.
    if (ticket.reportedByUid !== request.auth.uid) {
      throw new HttpsError("permission-denied", "Sólo quien reportó el ticket puede pagarlo.");
    }
    const userSnap = await getFirestore().collection("users").doc(request.auth.uid).get();
    const userRole = userSnap.exists ? userSnap.data().role : "";
    if (userRole === "ADMINISTRADOR" || userRole === "ENCARGADO") {
      throw new HttpsError("permission-denied", "El Encargado/Administrador paga con el comprobante manual.");
    }
    if (ticket.status !== "A_COBRAR") {
      throw new HttpsError("failed-precondition", "Este ticket no está listo para cobrar.");
    }
    const amount = ticket.budgetAmount;
    if (!(amount > 0)) throw new HttpsError("failed-precondition", "El ticket no tiene un monto a cobrar.");

    let preference;
    try {
      const client = new MercadoPagoConfig({ accessToken: mercadoPagoAccessToken.value() });
      preference = await new Preference(client).create({
        body: {
          items: [{ title: `Reparación: ${ticket.category || "Ticket"}`, quantity: 1, unit_price: amount }],
          external_reference: `ticket_${buildingId}_${ticketId}`,
          notification_url: process.env.MERCADO_PAGO_WEBHOOK_URL || undefined,
        },
      });
    } catch (err) {
      console.error("Error creando la preferencia de Mercado Pago para el ticket:", err.message);
      throw new HttpsError("unavailable", "No se pudo conectar con Mercado Pago. Probá de nuevo en un rato.");
    }

    // Ver el comentario de createMercadoPagoPreference: nunca sandbox_init_point.
    return { initPoint: preference.init_point };
  }
);

/**
 * HU-04/HU-10: recibe la notificación de Mercado Pago cuando un pago se confirma.
 * Público (Mercado Pago lo llama directo, sin pasar por Firebase Auth) — por eso
 * valida la firma con el validador que trae el propio SDK antes de hacer nada.
 * Resuelve dos tipos de recurso según el prefijo de external_reference: un pago de
 * alquiler/expensas (HU-04, resolveRentPayment) o un pago de reparación de ticket
 * (HU-10 Escenario 3, resolveTicketPayment). Responde 200 salvo firma inválida (401) o
 * un error nuestro (500): la escritura de cada resolve* es idempotente, así que
 * Mercado Pago reintentando ante un 500 es seguro, y evita que un pago ya hecho quede
 * sin confirmar para siempre por un error transitorio de nuestro lado.
 */
exports.mercadoPagoWebhook = onRequest(
  { secrets: [mercadoPagoAccessToken, mercadoPagoWebhookSecret] },
  async (req, res) => {
    const dataId = req.query["data.id"] || req.body?.data?.id;
    try {
      WebhookSignatureValidator.validate({
        xSignature: req.headers["x-signature"],
        xRequestId: req.headers["x-request-id"],
        dataId: String(dataId || ""),
        secret: mercadoPagoWebhookSecret.value(),
      });
    } catch (err) {
      console.warn("Firma de webhook de Mercado Pago inválida:", err.message);
      res.status(401).send("invalid signature");
      return;
    }

    if (req.body?.type !== "payment" || !dataId) {
      res.status(200).send("ignored");
      return;
    }

    try {
      const client = new MercadoPagoConfig({ accessToken: mercadoPagoAccessToken.value() });
      const mpPayment = await new Payment(client).get({ id: dataId });

      if (mpPayment.status !== "approved") {
        res.status(200).send("not approved yet");
        return;
      }

      const externalReference = mpPayment.external_reference || "";
      if (externalReference.startsWith("ticket_")) {
        await resolveTicketPayment(externalReference, mpPayment);
      } else {
        await resolveRentPayment(externalReference, mpPayment);
      }
      res.status(200).send("ok");
    } catch (err) {
      // 500 (no 200): las escrituras de resolveRentPayment/resolveTicketPayment son
      // idempotentes, así que un reintento de Mercado Pago ante un error transitorio
      // (timeout de su propia API, un hiccup de Firestore) es seguro. Si devolviéramos
      // 200 acá, un pago ya hecho podría quedar sin confirmar para siempre sin que nada
      // lo reintente.
      console.error("Error procesando webhook de Mercado Pago:", err.message);
      res.status(500).send("error logged");
    }
  }
);

/**
 * HU-04: resuelve un pago de alquiler/expensas. external_reference =
 * "{buildingId}_{uid}_{period}" (ver createMercadoPagoPreference) — uid y period nunca
 * se infieren de otra forma; si no matchea este formato o el documento no existe, se
 * loguea y no se escribe nada. Cruza el pago de Mercado Pago contra el Payment antes de
 * marcarlo PAGADO: una preferencia vieja que se paga tarde, o una notificación para un
 * pago que ya se resolvió por otro medio, no debe pisar nada.
 */
async function resolveRentPayment(externalReference, mpPayment) {
  const parts = externalReference.split("_");
  if (parts.length < 3) {
    console.warn("external_reference con formato inesperado:", externalReference);
    return;
  }
  const [buildingId, uid, period] = [parts[0], parts[1], parts.slice(2).join("_")];
  const paymentRef = getFirestore()
    .collection("buildings").doc(buildingId)
    .collection("payments").doc(`${uid}_${period}`);
  const paymentSnap = await paymentRef.get();
  if (!paymentSnap.exists) {
    console.warn(`Webhook de Mercado Pago sin Payment existente: ${buildingId}_${uid}_${period}`);
    return;
  }

  const existing = paymentSnap.data();
  if (existing.status === "PAGADO") return;
  if (existing.method !== "MERCADO_PAGO") {
    console.warn(`Webhook de Mercado Pago para un Payment de otro método: ${buildingId}_${uid}_${period}`);
    return;
  }
  if (
    typeof mpPayment.transaction_amount === "number" &&
    Math.abs(mpPayment.transaction_amount - existing.amount) > 0.01
  ) {
    console.warn(`Monto del pago de Mercado Pago no coincide con el Payment: ${buildingId}_${uid}_${period}`);
    return;
  }

  await paymentRef.update({
    status: "PAGADO",
    mercadoPagoPaymentId: String(mpPayment.id),
    verifiedAt: new Date(),
  });
}

/**
 * HU-10 Escenario 3: resuelve un pago de reparación de ticket. external_reference =
 * "ticket_{buildingId}_{ticketId}" (ver createTicketMercadoPagoPreference). Mismo
 * criterio que resolveRentPayment: nunca sobrescribe un ticket que ya no está
 * A_COBRAR, ni uno cuyo monto no coincide con lo que pagó el inquilino.
 */
async function resolveTicketPayment(externalReference, mpPayment) {
  const rest = externalReference.slice("ticket_".length);
  const parts = rest.split("_");
  if (parts.length < 2) {
    console.warn("external_reference de ticket con formato inesperado:", externalReference);
    return;
  }
  const buildingId = parts[0];
  const ticketId = parts.slice(1).join("_");
  const ticketRef = getFirestore()
    .collection("buildings").doc(buildingId)
    .collection("tickets").doc(ticketId);
  const ticketSnap = await ticketRef.get();
  if (!ticketSnap.exists) {
    console.warn(`Webhook de Mercado Pago sin Ticket existente: ${buildingId}/${ticketId}`);
    return;
  }

  const ticket = ticketSnap.data();
  if (ticket.status === "RESUELTO") {
    // Reintento idempotente del mismo pago (p.ej. Mercado Pago reenvía el webhook):
    // silencioso. Un pago aprobado DISTINTO en un ticket ya resuelto es un cobro
    // duplicado real (el Admin lo marcó pagado a mano mientras el inquilino estaba en
    // el checkout, o el inquilino pagó dos preferencias) — hay que poder detectarlo.
    if (ticket.mercadoPagoPaymentId !== String(mpPayment.id)) {
      console.warn(
        `Webhook de Mercado Pago: pago aprobado distinto para un ticket ya RESUELTO (posible cobro duplicado): ${buildingId}/${ticketId}, mercadoPagoPaymentId=${mpPayment.id}`
      );
    }
    return;
  }
  if (ticket.status !== "A_COBRAR") {
    console.warn(`Webhook de Mercado Pago para un ticket que no está A_COBRAR: ${buildingId}/${ticketId}`);
    return;
  }
  if (
    typeof mpPayment.transaction_amount === "number" &&
    Math.abs(mpPayment.transaction_amount - ticket.budgetAmount) > 0.01
  ) {
    console.warn(`Monto del pago de Mercado Pago no coincide con el ticket: ${buildingId}/${ticketId}`);
    return;
  }

  await ticketRef.update({
    status: "RESUELTO",
    mercadoPagoPaymentId: String(mpPayment.id),
  });
}
