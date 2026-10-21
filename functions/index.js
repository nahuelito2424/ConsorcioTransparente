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
    if (!buildingId || !period) throw new HttpsError("invalid-argument", "Falta buildingId o period.");

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
    const building = buildingSnap.data();
    const breakdown = {};
    if (building.chargesRent && user.rentAmount > 0) breakdown.alquiler = user.rentAmount;
    if (building.chargesExpenses && user.expensesAmount > 0) breakdown.expensas = user.expensesAmount;
    const amount = Object.values(breakdown).reduce((a, b) => a + b, 0);
    if (amount <= 0) throw new HttpsError("failed-precondition", "No tenés un monto asignado.");

    const paymentId = `${uid}_${period}`;
    const paymentRef = db.collection("buildings").doc(buildingId)
      .collection("payments").doc(paymentId);

    const client = new MercadoPagoConfig({ accessToken: mercadoPagoAccessToken.value() });
    const preference = await new Preference(client).create({
      body: {
        items: [{ title: "Expensas/alquiler", quantity: 1, unit_price: amount }],
        external_reference: `${buildingId}_${uid}_${period}`,
        notification_url: process.env.MERCADO_PAGO_WEBHOOK_URL || undefined,
      },
    });

    await paymentRef.set({
      uid,
      period,
      breakdown,
      amount,
      method: "MERCADO_PAGO",
      status: "A_VERIFICAR",
      createdAt: new Date(),
    });

    return { initPoint: preference.sandbox_init_point || preference.init_point };
  }
);
