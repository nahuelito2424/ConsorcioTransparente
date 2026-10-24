import { describe, expect, test } from "vitest";
import { paymentFilterStatus } from "./paymentFilter";
import type { Payment } from "../types";

function payment(status: Payment["status"]): Payment {
  return { id: "x", uid: "u1", period: "2026-03", amount: 1000, status };
}

describe("paymentFilterStatus", () => {
  test("sin pago registrado devuelve SIN_PAGO", () => {
    expect(paymentFilterStatus(undefined)).toBe("SIN_PAGO");
  });

  test("pago PAGADO devuelve PAGADO", () => {
    expect(paymentFilterStatus(payment("PAGADO"))).toBe("PAGADO");
  });

  test("pago A_VERIFICAR devuelve A_VERIFICAR", () => {
    expect(paymentFilterStatus(payment("A_VERIFICAR"))).toBe("A_VERIFICAR");
  });

  test("pago RECHAZADO devuelve RECHAZADO", () => {
    expect(paymentFilterStatus(payment("RECHAZADO"))).toBe("RECHAZADO");
  });
});
