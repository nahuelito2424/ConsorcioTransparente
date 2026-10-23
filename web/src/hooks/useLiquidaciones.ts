import { useEffect, useState } from "react";
import { collection, doc, onSnapshot, orderBy, query, serverTimestamp, writeBatch } from "firebase/firestore";
import { db } from "../firebase";
import type { Liquidacion, LiquidacionBreakdownRow } from "../types";

/**
 * buildings/{buildingId}/liquidaciones — historial de liquidaciones emitidas (nuevo
 * en esta v1). emitirLiquidacion es la única escritura que mueve plata real: actualiza
 * users/{tenantUid}.expensesAmount para cada fila con tenantUid no vacío, y deja un
 * snapshot inmutable del cálculo. Re-emitir el mismo period sobrescribe ese snapshot
 * (ver design doc — intencional, sin versiones en v1).
 */
export function useLiquidaciones(buildingId: string) {
  const [liquidaciones, setLiquidaciones] = useState<Liquidacion[]>([]);

  useEffect(() => {
    if (!buildingId) return;
    const q = query(
      collection(db, "buildings", buildingId, "liquidaciones"),
      orderBy("period", "desc")
    );
    const unsubscribe = onSnapshot(q, (snap) => {
      setLiquidaciones(
        snap.docs.map((d) => {
          const data = d.data();
          return {
            ...(data as Omit<Liquidacion, "emittedAt">),
            emittedAt: data.emittedAt?.toDate() ?? null,
          };
        })
      );
    });
    return unsubscribe;
  }, [buildingId]);

  async function emitirLiquidacion(
    period: string,
    totalExpenses: number,
    breakdown: LiquidacionBreakdownRow[],
    emittedByUid: string
  ) {
    // totalExpenses es el total real de gastos VERIFICADO del período (lo que ya
    // calculó la pantalla para generar breakdown) — nunca se re-deriva sumando el
    // breakdown, porque si una unidad quedó "sin asignar" esa suma da un número menor
    // al gasto real, y el historial dejaría de servir como auditoría ("en marzo se
    // gastó esto") para pasar a mostrar sólo lo efectivamente repartido.
    const unassignedUnits = breakdown.filter((row) => !row.tenantUid).map((row) => row.unitId);

    const batch = writeBatch(db);
    for (const row of breakdown) {
      if (!row.tenantUid) continue;
      batch.update(doc(db, "users", row.tenantUid), { expensesAmount: row.amount });
    }
    batch.set(doc(db, "buildings", buildingId, "liquidaciones", period), {
      period,
      totalExpenses,
      breakdown,
      unassignedUnits,
      emittedAt: serverTimestamp(),
      emittedByUid,
    });
    await batch.commit();
  }

  return { liquidaciones, emitirLiquidacion };
}
