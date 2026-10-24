import { describe, expect, test } from "vitest";
import { shiftPeriod } from "./PeriodPicker";

describe("shiftPeriod", () => {
  test("suma un mes dentro del mismo año", () => {
    expect(shiftPeriod("2026-03", 1)).toBe("2026-04");
  });

  test("resta un mes dentro del mismo año", () => {
    expect(shiftPeriod("2026-03", -1)).toBe("2026-02");
  });

  test("diciembre + 1 pasa al enero del año siguiente", () => {
    expect(shiftPeriod("2026-12", 1)).toBe("2027-01");
  });

  test("enero - 1 pasa al diciembre del año anterior", () => {
    expect(shiftPeriod("2027-01", -1)).toBe("2026-12");
  });
});
