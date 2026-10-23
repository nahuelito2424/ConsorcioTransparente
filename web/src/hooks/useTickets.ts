import { useEffect, useState } from "react";
import { collection, onSnapshot, query, where } from "firebase/firestore";
import { db } from "../firebase";

/** Sólo el conteo de tickets en A_COBRAR (HU-10) — KPI de Inicio. */
export function useTicketsACobrarCount(buildingId: string): number {
  const [count, setCount] = useState(0);

  useEffect(() => {
    if (!buildingId) return;
    const q = query(
      collection(db, "buildings", buildingId, "tickets"),
      where("status", "==", "A_COBRAR")
    );
    const unsubscribe = onSnapshot(q, (snap) => setCount(snap.size));
    return unsubscribe;
  }, [buildingId]);

  return count;
}
