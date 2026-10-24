import { useEffect, useState } from "react";
import { collection, onSnapshot, query, where } from "firebase/firestore";
import { db } from "../firebase";

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
