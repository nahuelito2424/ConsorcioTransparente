import { describe, expect, test } from "vitest";
import { calcularLiquidacion } from "./liquidacion";
import type { UnitInfo } from "../types";

describe("calcularLiquidacion", () => {
  test("reparte el total según la alícuota de cada unidad", () => {
    const units: UnitInfo[] = [
      { id: "a", label: "4B", aliquot: 50, tenantUid: "u1", ownerUid: "" },
      { id: "b", label: "7C", aliquot: 30, tenantUid: "u2", ownerUid: "" },
    ];
    const rows = calcularLiquidacion(units, 10000);
    expect(rows.find((r) => r.unitId === "a")?.amount).toBe(5000);
    expect(rows.find((r) => r.unitId === "b")?.amount).toBe(3000);
  });

  test("redondea a 2 decimales", () => {
    const units: UnitInfo[] = [{ id: "a", label: "4B", aliquot: 33.33, tenantUid: "u1", ownerUid: "" }];
    const rows = calcularLiquidacion(units, 100);
    expect(rows[0].amount).toBe(33.33);
  });

  test("una unidad sin tenantUid igual se calcula (quien emite decide qué hacer con ella)", () => {
    const units: UnitInfo[] = [{ id: "a", label: "9D", aliquot: 20, tenantUid: "", ownerUid: "" }];
    const rows = calcularLiquidacion(units, 1000);
    expect(rows[0].amount).toBe(200);
    expect(rows[0].tenantUid).toBe("");
  });

  test("con total 0, todas las unidades calculan 0", () => {
    const units: UnitInfo[] = [{ id: "a", label: "4B", aliquot: 50, tenantUid: "u1", ownerUid: "" }];
    const rows = calcularLiquidacion(units, 0);
    expect(rows[0].amount).toBe(0);
  });
});
