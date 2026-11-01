import { toPeriod } from "./period";
import type { Expense, ExpenseCategory } from "../types";

// Mismo orden fijo que el menú de categorías (GastosPage/RendicionCuentasViewModel.kt,
// Android) — categorías sin ningún gasto VERIFICADO ese período no aparecen.
const categoryOrder: ExpenseCategory[] = [
  "REPUESTOS",
  "MANO_DE_OBRA",
  "PLOMERIA",
  "ELECTRICIDAD",
  "GAS",
  "ALBANILERIA",
  "LIMPIEZA",
  "OTRO",
];

export interface RendicionCategoryTotal {
  category: ExpenseCategory;
  total: number;
  expenses: Expense[];
}

export interface RendicionResult {
  totalVerificado: number;
  categoryTotals: RendicionCategoryTotal[];
  bloqueadoCount: number;
  bloqueadoTotal: number;
}

/**
 * Desglose por categoría de los gastos VERIFICADO de un período, más cuántos
 * BLOQUEADO (sin comprobante) quedaron afuera del total — mismo cálculo que
 * RendicionCuentasViewModel.kt (Android). Función pura, no toca Firestore.
 */
export function calcularRendicion(expenses: Expense[], period: string): RendicionResult {
  const thisPeriod = expenses.filter((e) => e.createdAt && toPeriod(e.createdAt) === period);
  const verificados = thisPeriod.filter((e) => e.status === "VERIFICADO");
  const bloqueados = thisPeriod.filter((e) => e.status === "BLOQUEADO");

  const categoryTotals = categoryOrder
    .map((category) => {
      const inCategory = verificados.filter((e) => e.category === category);
      return { category, total: inCategory.reduce((sum, e) => sum + e.amount, 0), expenses: inCategory };
    })
    .filter((c) => c.total > 0);

  return {
    totalVerificado: verificados.reduce((sum, e) => sum + e.amount, 0),
    categoryTotals,
    bloqueadoCount: bloqueados.length,
    bloqueadoTotal: bloqueados.reduce((sum, e) => sum + e.amount, 0),
  };
}
