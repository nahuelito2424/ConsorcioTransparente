import { describe, expect, test } from "vitest";
import { toPeriod } from "./period";

describe("toPeriod", () => {
  test("formatea año y mes en hora local, no UTC", () => {
    // 31/10 23:30 hora de Buenos Aires (UTC-3) es 1/11 02:30 en UTC — toISOString().slice(0,7)
    // da "2026-11" acá, un mes adelantado. toPeriod tiene que seguir dando "2026-10".
    const d = new Date("2026-10-31T23:30:00-03:00");
    expect(toPeriod(d)).toBe("2026-10");
  });

  test("meses de un dígito van con cero adelante", () => {
    const d = new Date("2026-03-05T12:00:00-03:00");
    expect(toPeriod(d)).toBe("2026-03");
  });
});
