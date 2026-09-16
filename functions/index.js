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
 * No se despliegan solas: hay que correr `firebase deploy --only functions` una
 * vez configurado el proyecto de Firebase (ver README.md de la raíz).
 */

const { onSchedule } = require("firebase-functions/v2/scheduler");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

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
