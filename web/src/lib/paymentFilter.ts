import type { Payment } from "../types";

export type PaymentFilterStatus = "PAGADO" | "A_VERIFICAR" | "RECHAZADO" | "SIN_PAGO";

/** Estado para filtrar la tabla de pagos de Inicio — "SIN_PAGO" es un estado propio
 * de la web (no existe en Firestore): significa que la unidad todavía no generó el
 * documento de pago del período. */
export function paymentFilterStatus(payment: Payment | undefined): PaymentFilterStatus {
  return payment?.status ?? "SIN_PAGO";
}
