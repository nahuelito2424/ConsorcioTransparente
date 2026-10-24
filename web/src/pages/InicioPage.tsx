import { useState } from "react";
import { useAuth } from "../auth/useAuth";
import { useUnits } from "../hooks/useUnits";
import { useExpenses } from "../hooks/useExpenses";
import { usePayments } from "../hooks/usePayments";
import { useTicketsACobrarCount } from "../hooks/useTickets";
import { useBuildingResidents } from "../hooks/useBuildingResidents";
import { KpiCard } from "../components/KpiCard";
import { StatusStamp } from "../components/StatusStamp";
import { RadioGroup } from "../components/RadioGroup";
import { toPeriod } from "../lib/period";
import { paymentFilterStatus, type PaymentFilterStatus } from "../lib/paymentFilter";

const currency = new Intl.NumberFormat("es-AR", { style: "currency", currency: "ARS", maximumFractionDigits: 0 });

type Filter = "TODOS" | PaymentFilterStatus;

const filterOptions: { value: Filter; label: string }[] = [
  { value: "TODOS", label: "Todos" },
  { value: "PAGADO", label: "Pagado" },
  { value: "A_VERIFICAR", label: "A verificar" },
  { value: "RECHAZADO", label: "Rechazado" },
  { value: "SIN_PAGO", label: "Pendiente" },
];

export function InicioPage() {
  const { user } = useAuth();
  const buildingId = user?.buildingId ?? "";
  const currentPeriod = toPeriod(new Date());
  const { units, loading: unitsLoading } = useUnits(buildingId);
  const { expenses, loading: expensesLoading } = useExpenses(buildingId);
  const { payments, loading: paymentsLoading } = usePayments(buildingId, currentPeriod);
  const { count: ticketsACobrar, loading: ticketsLoading } = useTicketsACobrarCount(buildingId);
  const residents = useBuildingResidents(buildingId);
  const loading = unitsLoading || expensesLoading || paymentsLoading || ticketsLoading;
  const [filter, setFilter] = useState<Filter>("TODOS");

  const assignedUnits = units.filter((u) => u.tenantUid);
  const paidCount = assignedUnits.filter((u) =>
    payments.some((p) => p.uid === u.tenantUid && p.status === "PAGADO")
  ).length;
  const pctCobrado = assignedUnits.length > 0 ? Math.round((paidCount / assignedUnits.length) * 100) : 0;
  const morosos = assignedUnits.length - paidCount;

  const gastosDelMes = expenses
    .filter((e) => e.status === "VERIFICADO" && e.createdAt && toPeriod(e.createdAt) === currentPeriod)
    .reduce((sum, e) => sum + e.amount, 0);

  function residentName(uid: string) {
    return residents.find((r) => r.uid === uid)?.fullName ?? uid;
  }

  const filteredUnits = assignedUnits.filter((unit) => {
    if (filter === "TODOS") return true;
    const payment = payments.find((p) => p.uid === unit.tenantUid);
    return paymentFilterStatus(payment) === filter;
  });

  return (
    <div>
      <h1 className="font-serif text-2xl mb-1">Hola, {user?.fullName}</h1>
      <p className="text-sm text-pending mb-8">{currentPeriod}</p>

      {loading ? (
        <p className="text-sm text-pending">Cargando…</p>
      ) : (
        <>
          <div className="grid grid-cols-4 gap-8 mb-10">
            <KpiCard value={`${pctCobrado}%`} label="Expensas cobradas" />
            <KpiCard value={String(morosos)} label="Unidades morosas" />
            <KpiCard value={currency.format(gastosDelMes)} label="Gastos verificados del mes" />
            <KpiCard value={String(ticketsACobrar)} label="Tickets a cobrar" />
          </div>

          <h2 className="font-serif text-lg mb-3">Estado de pagos — {currentPeriod}</h2>

          <div className="mb-4">
            <RadioGroup name="payment-filter" value={filter} onChange={setFilter} options={filterOptions} />
          </div>

          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-xs uppercase tracking-wide text-pending">
                <th className="pb-2 pr-4">Unidad</th>
                <th className="pb-2 pr-4">Inquilino</th>
                <th className="pb-2">Estado</th>
              </tr>
            </thead>
            <tbody>
              {filteredUnits.map((unit) => {
                const payment = payments.find((p) => p.uid === unit.tenantUid);
                return (
                  <tr key={unit.id} className="border-t border-dashed border-border-soft">
                    <td className="py-2 pr-4">{unit.label}</td>
                    <td className="py-2 pr-4">{residentName(unit.tenantUid)}</td>
                    <td className="py-2">
                      {payment?.status === "PAGADO" && <StatusStamp status="ok">Pagado</StatusStamp>}
                      {payment?.status === "A_VERIFICAR" && (
                        <StatusStamp status="pending">A verificar</StatusStamp>
                      )}
                      {payment?.status === "RECHAZADO" && (
                        <StatusStamp status="warn">Rechazado</StatusStamp>
                      )}
                      {!payment && <StatusStamp status="warn">Pendiente</StatusStamp>}
                    </td>
                  </tr>
                );
              })}
              {assignedUnits.length === 0 && (
                <tr>
                  <td colSpan={3} className="py-4 text-pending text-sm">
                    Todavía no hay unidades con inquilino asignado — cargalas en Unidades.
                  </td>
                </tr>
              )}
              {assignedUnits.length > 0 && filteredUnits.length === 0 && (
                <tr>
                  <td colSpan={3} className="py-4 text-pending text-sm">
                    Ninguna unidad coincide con este filtro.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </>
      )}
    </div>
  );
}
