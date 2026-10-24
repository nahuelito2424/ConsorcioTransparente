import { useAuth } from "../auth/useAuth";
import { useUnits } from "../hooks/useUnits";
import { useExpenses } from "../hooks/useExpenses";
import { usePayments } from "../hooks/usePayments";
import { useTicketsACobrarCount } from "../hooks/useTickets";
import { useBuildingResidents } from "../hooks/useBuildingResidents";
import { KpiCard } from "../components/KpiCard";
import { StatusStamp } from "../components/StatusStamp";
import { toPeriod } from "../lib/period";

const currency = new Intl.NumberFormat("es-AR", { style: "currency", currency: "ARS", maximumFractionDigits: 0 });

export function InicioPage() {
  const { user } = useAuth();
  const buildingId = user?.buildingId ?? "";
  const currentPeriod = toPeriod(new Date());
  const { units } = useUnits(buildingId);
  const { expenses } = useExpenses(buildingId);
  const payments = usePayments(buildingId, currentPeriod);
  const ticketsACobrar = useTicketsACobrarCount(buildingId);
  const residents = useBuildingResidents(buildingId);

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

  return (
    <div>
      <h1 className="font-serif text-2xl mb-1">Hola, {user?.fullName}</h1>
      <p className="text-sm text-pending mb-8">{currentPeriod}</p>

      <div className="grid grid-cols-4 gap-8 mb-10">
        <KpiCard value={`${pctCobrado}%`} label="Expensas cobradas" />
        <KpiCard value={String(morosos)} label="Unidades morosas" />
        <KpiCard value={currency.format(gastosDelMes)} label="Gastos verificados del mes" />
        <KpiCard value={String(ticketsACobrar)} label="Tickets a cobrar" />
      </div>

      <h2 className="font-serif text-lg mb-3">Estado de pagos — {currentPeriod}</h2>
      <table className="w-full text-sm">
        <thead>
          <tr className="text-left text-xs uppercase tracking-wide text-pending">
            <th className="pb-2 pr-4">Unidad</th>
            <th className="pb-2 pr-4">Inquilino</th>
            <th className="pb-2">Estado</th>
          </tr>
        </thead>
        <tbody>
          {assignedUnits.map((unit) => {
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
                  {payment?.status === "RECHAZADO" && <StatusStamp status="warn">Rechazado</StatusStamp>}
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
        </tbody>
      </table>
    </div>
  );
}
