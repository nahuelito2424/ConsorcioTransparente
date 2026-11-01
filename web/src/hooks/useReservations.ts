import { useEffect, useState } from "react";
import { collection, deleteDoc, doc, getDocs, onSnapshot, query, setDoc, where } from "firebase/firestore";
import { db } from "../firebase";
import type { Reservation } from "../types";

// Mismo algoritmo que FirebaseReservationRepository.kt (Android): un id determinístico
// por espacio+fecha+horario. set() sobre un id ya tomado por otro usuario lo evalúa
// firestore.rules como "update" (el documento ya existe) y lo rechaza porque
// reservedByUid no coincide — así se evita el doble booking sin una transacción.
function slotId(spaceName: string, date: string, timeSlot: string): string {
  const safeSpace = spaceName.trim().toLowerCase().replace(/[^a-z0-9]+/g, "-");
  const safeSlot = timeSlot.replace(/:/g, "").replace(/-/g, "_");
  return `${safeSpace}_${date}_${safeSlot}`;
}

/** Reservas propias futuras (fecha >= hoy), ordenadas por fecha. */
export function useMyReservations(buildingId: string, uid: string, todayIso: string) {
  const [reservations, setReservations] = useState<Reservation[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!buildingId || !uid) return;
    const q = query(collection(db, "buildings", buildingId, "reservations"), where("reservedByUid", "==", uid));
    const unsubscribe = onSnapshot(
      q,
      (snap) => {
        const list = snap.docs
          .map((d) => ({ ...(d.data() as Omit<Reservation, "id">), id: d.id }))
          .filter((r) => r.date >= todayIso)
          .sort((a, b) => a.date.localeCompare(b.date));
        setReservations(list);
        setError(null);
        setLoading(false);
      },
      () => {
        setError("No se pudieron cargar tus reservas. Recargá la página.");
        setLoading(false);
      }
    );
    return unsubscribe;
  }, [buildingId, uid, todayIso]);

  async function reserve(spaceName: string, date: string, timeSlot: string, uid: string, name: string) {
    const id = slotId(spaceName, date, timeSlot);
    await setDoc(doc(db, "buildings", buildingId, "reservations", id), {
      spaceName,
      date,
      timeSlot,
      reservedByUid: uid,
      reservedByName: name,
    });
  }

  async function cancel(reservationId: string) {
    await deleteDoc(doc(db, "buildings", buildingId, "reservations", reservationId));
  }

  return { reservations, loading, error, reserve, cancel };
}

/** Reservas ya tomadas para un espacio+fecha, para mostrar qué horarios están ocupados. */
export async function listReservationsForDate(
  buildingId: string,
  spaceName: string,
  date: string
): Promise<Reservation[]> {
  const q = query(
    collection(db, "buildings", buildingId, "reservations"),
    where("spaceName", "==", spaceName),
    where("date", "==", date)
  );
  const snap = await getDocs(q);
  return snap.docs.map((d) => ({ ...(d.data() as Omit<Reservation, "id">), id: d.id }));
}
