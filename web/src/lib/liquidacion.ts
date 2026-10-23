import type { LiquidacionBreakdownRow, UnitInfo } from "../types";

/**
 * Calcula cuánto le corresponde pagar a cada unidad según su alícuota sobre el total
 * de gastos verificados del período. Función pura — no toca Firestore, para poder
 * probarla sin el emulador (ver docs/superpowers/specs/2026-10-23-web-admin-liquidacion-design.md).
 * No decide qué hacer con una unidad sin tenantUid — eso lo resuelve quien emite
 * (useLiquidaciones.emitirLiquidacion), que no escribe expensesAmount para esas filas.
 */
export function calcularLiquidacion(
  units: UnitInfo[],
  totalExpenses: number
): LiquidacionBreakdownRow[] {
  return units.map((unit) => ({
    unitId: unit.id,
    unitLabel: unit.label,
    tenantUid: unit.tenantUid,
    aliquot: unit.aliquot,
    amount: round2((totalExpenses * unit.aliquot) / 100),
  }));
}

function round2(n: number): number {
  return Math.round(n * 100) / 100;
}
