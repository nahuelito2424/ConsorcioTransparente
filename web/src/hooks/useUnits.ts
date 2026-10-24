import { useEffect, useState } from "react";
import { collection, deleteDoc, doc, onSnapshot, setDoc } from "firebase/firestore";
import { db } from "../firebase";
import type { UnitInfo } from "../types";

/**
 * buildings/{buildingId}/units — sin uso hasta esta v1 (ver design doc). Consumida
 * también por la pantalla de Liquidación para leer unidad + alícuota.
 */
export function useUnits(buildingId: string) {
  const [units, setUnits] = useState<UnitInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!buildingId) return;
    const unsubscribe = onSnapshot(
      collection(db, "buildings", buildingId, "units"),
      (snap) => {
        setUnits(snap.docs.map((d) => ({ ...(d.data() as UnitInfo), id: d.id })));
        setError(null);
        setLoading(false);
      },
      () => {
        // Sin esto, un error del listener (permisos, red) deja la pantalla en
        // "Cargando…" para siempre — ver mismo cuidado en useAuth.ts.
        setError("No se pudieron cargar las unidades. Recargá la página.");
        setLoading(false);
      }
    );
    return unsubscribe;
  }, [buildingId]);

  async function createUnit(data: Omit<UnitInfo, "id">) {
    const ref = doc(collection(db, "buildings", buildingId, "units"));
    await setDoc(ref, { ...data, id: ref.id });
  }

  async function updateUnit(unitId: string, data: Partial<Omit<UnitInfo, "id">>) {
    await setDoc(doc(db, "buildings", buildingId, "units", unitId), data, { merge: true });
  }

  async function deleteUnit(unitId: string) {
    await deleteDoc(doc(db, "buildings", buildingId, "units", unitId));
  }

  return { units, loading, error, createUnit, updateUnit, deleteUnit };
}
