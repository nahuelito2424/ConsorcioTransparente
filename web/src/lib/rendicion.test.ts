import { describe, expect, test } from "vitest";
import { calcularRendicion } from "./rendicion";
import type { Expense } from "../types";

function expense(overrides: Partial<Expense>): Expense {
  return {
    id: "x",
    description: "Gasto",
    category: "OTRO",
    providerName: "",
    amount: 100,
    receiptUrl: "",
    status: "VERIFICADO",
    createdByUid: "u1",
    createdAt: new Date(2026, 9, 15), // 2026-10-15 en hora local — ver nota en period.ts
    ...overrides,
  };
}

describe("calcularRendicion", () => {
  test("suma sólo los VERIFICADO del período en totalVerificado", () => {
    const expenses = [
      expense({ amount: 100, status: "VERIFICADO", createdAt: new Date(2026, 9, 1) }),
      expense({ amount: 200, status: "VERIFICADO", createdAt: new Date(2026, 9, 20) }),
      expense({ amount: 999, status: "VERIFICADO", createdAt: new Date(2026, 8, 30) }), // otro período
    ];
    const r = calcularRendicion(expenses, "2026-10");
    expect(r.totalVerificado).toBe(300);
  });

  test("los BLOQUEADO no entran en totalVerificado, pero sí en bloqueadoCount/bloqueadoTotal", () => {
    const expenses = [
      expense({ amount: 100, status: "VERIFICADO" }),
      expense({ amount: 50, status: "BLOQUEADO" }),
      expense({ amount: 30, status: "BLOQUEADO" }),
    ];
    const r = calcularRendicion(expenses, "2026-10");
    expect(r.totalVerificado).toBe(100);
    expect(r.bloqueadoCount).toBe(2);
    expect(r.bloqueadoTotal).toBe(80);
  });

  test("agrupa por categoría en el orden fijo del menú, categorías sin gasto no aparecen", () => {
    const expenses = [
      expense({ category: "GAS", amount: 100 }),
      expense({ category: "PLOMERIA", amount: 50 }),
      expense({ category: "PLOMERIA", amount: 25 }),
    ];
    const r = calcularRendicion(expenses, "2026-10");
    expect(r.categoryTotals.map((c) => c.category)).toEqual(["PLOMERIA", "GAS"]);
    expect(r.categoryTotals.find((c) => c.category === "PLOMERIA")?.total).toBe(75);
  });

  test("un mes sin ningún gasto VERIFICADO da total 0, no undefined", () => {
    const r = calcularRendicion([], "2026-10");
    expect(r.totalVerificado).toBe(0);
    expect(r.categoryTotals).toEqual([]);
    expect(r.bloqueadoCount).toBe(0);
  });

  test("una categoría fuera del enum (dato viejo, texto libre) cae en OTRO y entra en el desglose", () => {
    const expenses = [expense({ category: "Reparación varia" as Expense["category"], amount: 300 })];
    const r = calcularRendicion(expenses, "2026-10");
    expect(r.totalVerificado).toBe(300);
    expect(r.categoryTotals).toEqual([{ category: "OTRO", total: 300, expenses }]);
  });

  test("un mes sólo con BLOQUEADOs no se confunde con un mes sin gastos", () => {
    const expenses = [expense({ amount: 500, status: "BLOQUEADO" })];
    const r = calcularRendicion(expenses, "2026-10");
    expect(r.totalVerificado).toBe(0);
    expect(r.bloqueadoCount).toBe(1);
    expect(r.bloqueadoTotal).toBe(500);
  });
});
