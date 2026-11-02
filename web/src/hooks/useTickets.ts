import { useEffect, useState } from "react";
import { collection, doc, onSnapshot, query, serverTimestamp, setDoc, updateDoc, where } from "firebase/firestore";
import { getDownloadURL, ref, uploadBytes } from "firebase/storage";
import { httpsCallable } from "firebase/functions";
import { db, functions, storage } from "../firebase";
import type { Ticket } from "../types";

/** Sólo el conteo de tickets en A_COBRAR (HU-10) — KPI de Inicio. */
export function useTicketsACobrarCount(buildingId: string) {
  const [count, setCount] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!buildingId) return;
    const q = query(
      collection(db, "buildings", buildingId, "tickets"),
      where("status", "==", "A_COBRAR")
    );
    const unsubscribe = onSnapshot(
      q,
      (snap) => {
        setCount(snap.size);
        setError(null);
        setLoading(false);
      },
      () => {
        setError("No se pudo cargar los tickets. Recargá la página.");
        setLoading(false);
      }
    );
    return unsubscribe;
  }, [buildingId]);

  return { count, loading, error };
}

/**
 * Tickets propios (reportados por uid), ordenados por fecha — igual que
 * listMyTickets (Android): sin orderBy en la query, ordenado en el cliente, así no
 * hace falta un índice compuesto nuevo (mismo truco que ya usa FirebaseTicketRepository.kt).
 */
export function useMyTickets(buildingId: string, uid: string) {
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!buildingId || !uid) return;
    const q = query(collection(db, "buildings", buildingId, "tickets"), where("reportedByUid", "==", uid));
    const unsubscribe = onSnapshot(
      q,
      (snap) => {
        const list = snap.docs.map((d) => {
          const data = d.data();
          return {
            ...(data as Omit<Ticket, "id" | "createdAt">),
            id: d.id,
            createdAt: data.createdAt?.toDate() ?? null,
          };
        });
        list.sort((a, b) => (b.createdAt?.getTime() ?? 0) - (a.createdAt?.getTime() ?? 0));
        setTickets(list);
        setError(null);
        setLoading(false);
      },
      () => {
        setError("No se pudieron cargar tus tickets. Recargá la página.");
        setLoading(false);
      }
    );
    return unsubscribe;
  }, [buildingId, uid]);

  // Mismo path de Storage que FirebaseTicketRepository.kt: tickets/{buildingId}/problem_{uuid}.jpg.
  async function createTicket(category: string, description: string, uid: string, photo: File | null) {
    let photoUrl = "";
    if (photo) {
      const fileName = `tickets/${buildingId}/problem_${crypto.randomUUID()}.jpg`;
      const storageRef = ref(storage, fileName);
      await uploadBytes(storageRef, photo);
      photoUrl = await getDownloadURL(storageRef);
    }
    const ticketRef = doc(collection(db, "buildings", buildingId, "tickets"));
    await setDoc(ticketRef, {
      category,
      description,
      photoUrl,
      status: "REPORTADO",
      budgetAmount: 0,
      purchaseReceiptUrl: "",
      paymentReceiptUrl: "",
      mercadoPagoPaymentId: "",
      reportedByUid: uid,
      assignedToUid: "",
      createdAt: serverTimestamp(),
    });
  }

  // Pago manual del propio inquilino cuando el ticket está A_COBRAR — mismo camino
  // que markPaid (Android) y la única combinación de campos que permite
  // firestore.rules para el reportero (no el Encargado/Admin): paymentReceiptUrl +
  // status, nada más.
  async function markPaid(ticketId: string, receipt: File) {
    const fileName = `tickets/${buildingId}/payment_${crypto.randomUUID()}.jpg`;
    const storageRef = ref(storage, fileName);
    await uploadBytes(storageRef, receipt);
    const paymentReceiptUrl = await getDownloadURL(storageRef);
    await updateDoc(doc(db, "buildings", buildingId, "tickets", ticketId), {
      paymentReceiptUrl,
      status: "RESUELTO",
    });
  }

  // Mismo callable que payWithMercadoPago (Android, TicketDetailViewModel.kt): el
  // server valida de nuevo que quien llama es el reportero y no Admin/Encargado — acá
  // sólo se pide la URL de Checkout Pro y se abre, nada de lógica de negocio.
  async function createMercadoPagoPreference(ticketId: string): Promise<string> {
    const call = httpsCallable<{ buildingId: string; ticketId: string }, { initPoint: string }>(
      functions,
      "createTicketMercadoPagoPreference"
    );
    const result = await call({ buildingId, ticketId });
    return result.data.initPoint;
  }

  return { tickets, loading, error, createTicket, markPaid, createMercadoPagoPreference };
}
