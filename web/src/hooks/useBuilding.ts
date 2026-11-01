import { useEffect, useState } from "react";
import { doc, onSnapshot } from "firebase/firestore";
import { db } from "../firebase";
import type { Building } from "../types";

/** buildings/{buildingId} — flags chargesRent/chargesExpenses, para armar el breakdown
 * de una factura igual que PagarViewModel.kt (Android). */
export function useBuilding(buildingId: string) {
  const [building, setBuilding] = useState<Building | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!buildingId) return;
    const unsubscribe = onSnapshot(doc(db, "buildings", buildingId), (snap) => {
      setBuilding(snap.exists() ? { ...(snap.data() as Omit<Building, "id">), id: snap.id } : null);
      setLoading(false);
    });
    return unsubscribe;
  }, [buildingId]);

  return { building, loading };
}
