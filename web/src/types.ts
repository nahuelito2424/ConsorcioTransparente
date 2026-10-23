// Interfaces espejo de los modelos Kotlin que la web toca (ver
// app/src/main/java/com/consorciotransparente/app/data/model/). No son una copia 1:1
// de toda la app — sólo los campos que esta web lee o escribe.

export type Role = "ADMINISTRADOR" | "ENCARGADO" | "INQUILINO";

/** users/{uid} */
export interface UserProfile {
  uid: string;
  fullName: string;
  email: string;
  role: Role;
  buildingId: string;
  unitId: string;
  rentAmount: number;
  expensesAmount: number;
}

/** buildings/{buildingId} */
export interface Building {
  id: string;
  name: string;
  address: string;
  chargesRent: boolean;
  chargesExpenses: boolean;
}

export type ExpenseCategory =
  | "REPUESTOS"
  | "MANO_DE_OBRA"
  | "PLOMERIA"
  | "ELECTRICIDAD"
  | "GAS"
  | "ALBANILERIA"
  | "LIMPIEZA"
  | "OTRO";

export type ExpenseStatus = "VERIFICADO" | "BLOQUEADO";

/** buildings/{buildingId}/expenses/{expenseId} */
export interface Expense {
  id: string;
  description: string;
  category: ExpenseCategory;
  providerName: string;
  amount: number;
  receiptUrl: string;
  status: ExpenseStatus;
  createdByUid: string;
  createdAt: Date | null;
}

/** buildings/{buildingId}/units/{unitId} — sin uso hasta esta v1 (ver design doc). */
export interface UnitInfo {
  id: string;
  label: string;
  aliquot: number; // 0-100, % de alícuota
  tenantUid: string; // "" = sin asignar
  ownerUid: string; // sin usar en v1
}

/** Una fila del cálculo/snapshot de una liquidación — no es un documento propio. */
export interface LiquidacionBreakdownRow {
  unitId: string;
  unitLabel: string;
  tenantUid: string;
  aliquot: number;
  amount: number;
}

/** buildings/{buildingId}/liquidaciones/{period} — period = "yyyy-MM". Nuevo en esta v1. */
export interface Liquidacion {
  period: string;
  totalExpenses: number;
  breakdown: LiquidacionBreakdownRow[];
  unassignedUnits: string[]; // unitIds sin tenantUid al momento de emitir
  emittedAt: Date | null;
  emittedByUid: string;
}

export type PaymentStatus = "A_VERIFICAR" | "PAGADO" | "RECHAZADO";

/** buildings/{buildingId}/payments/{uid}_{period} — sólo lectura desde la web. */
export interface Payment {
  id: string;
  uid: string;
  period: string;
  amount: number;
  status: PaymentStatus;
}
