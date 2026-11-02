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
  amenities: string[];
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

/** buildings/{buildingId}/votes/{voteId} — espejo de VoteTopic.kt (Android, HU-02). */
export interface VoteTopic {
  id: string;
  title: string;
  description: string;
  options: string[];
  closesAt: Date | null;
  isClosed: boolean;
  createdByUid: string;
  createdAt: Date | null;
}

/** buildings/{buildingId}/votes/{voteId}/responses/{uid} — espejo de VoteResponse.kt. */
export interface VoteResponse {
  uid: string;
  optionIndex: number;
}

/** providers/{providerId} — espejo de Provider.kt (Android, HU-08, directorio global). */
export interface Provider {
  id: string;
  name: string;
  category: string;
  phone: string;
  averageRating: number;
  ratingCount: number;
}

/** providers/{providerId}/reviews/{reviewId} — espejo de ProviderReview.kt. */
export interface ProviderReview {
  id: string;
  authorUid: string;
  comment: string;
  stars: number;
  expenseId: string;
}

/** buildings/{buildingId}/reservations/{id} — espejo de Reservation.kt (Android, HU-06). */
export interface Reservation {
  id: string;
  spaceName: string;
  date: string; // yyyy-MM-dd
  timeSlot: string;
  reservedByUid: string;
  reservedByName: string;
}

export type TicketStatus = "REPORTADO" | "EN_PRESUPUESTO" | "A_COBRAR" | "RESUELTO";

/** buildings/{buildingId}/tickets/{ticketId} — espejo de Ticket.kt (Android, HU-10). */
export interface Ticket {
  id: string;
  category: string;
  description: string;
  photoUrl: string;
  status: TicketStatus;
  budgetAmount: number;
  purchaseReceiptUrl: string;
  paymentReceiptUrl: string;
  mercadoPagoPaymentId: string;
  reportedByUid: string;
  assignedToUid: string;
  createdAt: Date | null;
}

export type PostStatus = "PENDIENTE" | "APROBADO" | "RECHAZADO";

/** buildings/{buildingId}/posts/{postId} — espejo de Post.kt (Android, HU-05). */
export interface Post {
  id: string;
  title: string;
  body: string;
  imageUrl: string;
  status: PostStatus;
  authorUid: string;
  authorName: string;
  createdAt: Date | null;
}

export type PaymentStatus = "A_VERIFICAR" | "PAGADO" | "RECHAZADO";

/** buildings/{buildingId}/payments/{uid}_{period} — espejo de Payment.kt (Android).
 * El Administrador/Encargado sólo la lee y la verifica/rechaza; el propio Inquilino
 * además la crea al subir su comprobante (ver useMyPayments.ts). */
export interface Payment {
  id: string;
  uid: string;
  period: string;
  breakdown: Record<string, number>;
  amount: number;
  method: string;
  status: PaymentStatus;
  receiptUrl: string;
  rejectionReason: string;
  createdAt: Date | null;
}
