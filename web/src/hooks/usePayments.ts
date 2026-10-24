import { useEffect, useState } from "react";
import { collection, onSnapshot, query, where } from "firebase/firestore";
import { db } from "../firebase";
import type { Payment } from "../types";

/** buildings/{buildingId}/payments del período dado — sólo lectura desde la web (v1). */
export function usePayments(buildingId: string, period: string) {
  const [payments, setPayments] = useState<Payment[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!buildingId) return;
    const q = query(
      collection(db, "buildings", buildingId, "payments"),
      where("period", "==", period)
    );
    const unsubscribe = onSnapshot(q, (snap) => {
      setPayments(snap.docs.map((d) => ({ ...(d.data() as Payment), id: d.id })));
      setLoading(false);
    });
    return unsubscribe;
  }, [buildingId, period]);

  return { payments, loading };
}
