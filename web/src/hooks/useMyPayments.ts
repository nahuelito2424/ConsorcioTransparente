import { useEffect, useState } from "react";
import { collection, doc, onSnapshot, orderBy, query, serverTimestamp, setDoc, where } from "firebase/firestore";
import { getDownloadURL, ref, uploadBytes } from "firebase/storage";
import { db, storage } from "../firebase";
import type { Payment } from "../types";

/**
 * buildings/{buildingId}/payments propios del usuario logueado, todos los períodos —
 * equivalente web de listMyPayments (PaymentRepository.kt, Android). Usa el mismo
 * índice compuesto (uid asc, period desc) ya desplegado para Android — ver
 * firestore.indexes.json.
 */
export function useMyPayments(buildingId: string, uid: string) {
  const [payments, setPayments] = useState<Payment[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!buildingId || !uid) return;
    const q = query(
      collection(db, "buildings", buildingId, "payments"),
      where("uid", "==", uid),
      orderBy("period", "desc")
    );
    const unsubscribe = onSnapshot(
      q,
      (snap) => {
        setPayments(
          snap.docs.map((d) => {
            const data = d.data();
            return {
              ...(data as Omit<Payment, "id" | "createdAt">),
              id: d.id,
              createdAt: data.createdAt?.toDate() ?? null,
            };
          })
        );
        setError(null);
        setLoading(false);
      },
      () => {
        setError("No se pudieron cargar tus pagos. Recargá la página.");
        setLoading(false);
      }
    );
    return unsubscribe;
  }, [buildingId, uid]);

  // Mismo path de Storage y misma forma de documento que FirebasePaymentRepository.kt
  // (Android): payments/{buildingId}/{uid}_{period}_{uuid}.jpg, y un set() completo
  // sobre buildings/{buildingId}/payments/{uid}_{period} — Firestore lo evalúa como
  // "create" si el documento no existía (primer pago del período) o como "update" si
  // ya existía (reintento tras un RECHAZADO), según las reglas de seguridad.
  async function uploadReceipt(period: string, breakdown: Record<string, number>, file: File) {
    const fileName = `payments/${buildingId}/${uid}_${period}_${crypto.randomUUID()}.jpg`;
    const storageRef = ref(storage, fileName);
    await uploadBytes(storageRef, file);
    const receiptUrl = await getDownloadURL(storageRef);

    const amount = Object.values(breakdown).reduce((sum, v) => sum + v, 0);
    // set() completo (no merge): un reintento tras RECHAZADO tiene que limpiar
    // rejectionReason del intento anterior, igual que el Payment() nuevo que crea
    // uploadReceipt en Android — no alcanza con escribir sólo los campos que cambian.
    await setDoc(doc(db, "buildings", buildingId, "payments", `${uid}_${period}`), {
      uid,
      period,
      breakdown,
      amount,
      method: "TRANSFERENCIA",
      status: "A_VERIFICAR",
      receiptUrl,
      mercadoPagoPaymentId: "",
      rejectionReason: "",
      createdAt: serverTimestamp(),
      verifiedAt: null,
      verifiedByUid: "",
    });
  }

  return { payments, loading, error, uploadReceipt };
}
