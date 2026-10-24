import { useEffect, useState } from "react";
import {
  collection,
  doc,
  getDoc,
  onSnapshot,
  orderBy,
  query,
  serverTimestamp,
  writeBatch,
} from "firebase/firestore";
import { db } from "../firebase";
import type { Liquidacion, LiquidacionBreakdownRow } from "../types";

/**
 * buildings/{buildingId}/liquidaciones — historial de liquidaciones emitidas (nuevo
 * en esta v1). emitirLiquidacion es la única escritura que mueve plata real: actualiza
 * users/{tenantUid}.expensesAmount para cada inquilino con al menos una unidad
 * asignada, y deja un snapshot inmutable del cálculo. Re-emitir el mismo period
 * sobrescribe ese snapshot (ver design doc — intencional, sin versiones en v1).
 */
export function useLiquidaciones(buildingId: string) {
  const [liquidaciones, setLiquidaciones] = useState<Liquidacion[]>([]);
  const [loading, setLoading] = useState(true);

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
      setLoading(false);
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

    // Un mismo inquilino puede tener más de una unidad (depto + cochera/baulera, algo
    // habitual) — agrupar y sumar antes de escribir. Dos `update` sobre el mismo
    // users/{uid} en el mismo batch se pisarían entre sí y el inquilino terminaría
    // pagando sólo una de sus unidades.
    const amountByTenant = new Map<string, number>();
    for (const row of breakdown) {
      if (!row.tenantUid) continue;
      amountByTenant.set(row.tenantUid, (amountByTenant.get(row.tenantUid) ?? 0) + row.amount);
    }

    // Si ya había una liquidación emitida para este period, un inquilino que estaba ahí
    // y ya no tiene unidad asignada (se borró la unidad, o se la reasignaron a otro)
    // tiene que volver a expensesAmount: 0 — si no, se queda pagando el monto de la
    // emisión anterior para siempre, sin que ninguna liquidación vigente lo respalde.
    const previousSnap = await getDoc(doc(db, "buildings", buildingId, "liquidaciones", period));
    const previousTenants: string[] = previousSnap.exists()
      ? (previousSnap.data().breakdown as LiquidacionBreakdownRow[])
          .map((row) => row.tenantUid)
          .filter((uid): uid is string => Boolean(uid))
      : [];

    const batch = writeBatch(db);
    for (const [tenantUid, amount] of amountByTenant) {
      batch.update(doc(db, "users", tenantUid), { expensesAmount: amount });
    }
    for (const tenantUid of previousTenants) {
      if (!amountByTenant.has(tenantUid)) {
        batch.update(doc(db, "users", tenantUid), { expensesAmount: 0 });
      }
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

  return { liquidaciones, loading, emitirLiquidacion };
}
