import { useState } from "react";
import { useAuth } from "../auth/useAuth";
import { useUnits } from "../hooks/useUnits";
import { useExpenses } from "../hooks/useExpenses";
import { useLiquidaciones } from "../hooks/useLiquidaciones";
import { useBuildingResidents } from "../hooks/useBuildingResidents";
import { calcularLiquidacion } from "../lib/liquidacion";
import { toPeriod } from "../lib/period";
import { StatusStamp } from "../components/StatusStamp";
import { PeriodPicker } from "../components/PeriodPicker";
import { Checkbox } from "../components/Checkbox";
import type { Liquidacion, LiquidacionBreakdownRow, UserProfile } from "../types";

const currency = new Intl.NumberFormat("es-AR", { style: "currency", currency: "ARS" });
const dateTime = new Intl.DateTimeFormat("es-AR", { dateStyle: "short", timeStyle: "short" });

function BreakdownTable({
  breakdown,
  residents,
}: {
  breakdown: LiquidacionBreakdownRow[];
  residents: UserProfile[];
}) {
  const total = breakdown.reduce((sum, row) => sum + row.amount, 0);

  function residentName(uid: string) {
    return residents.find((r) => r.uid === uid)?.fullName || residents.find((r) => r.uid === uid)?.email || uid;
  }

  return (
    <table className="w-full text-sm">
      <thead>
        <tr className="text-left text-xs uppercase tracking-wide text-pending">
          <th className="pb-2 pr-4">Unidad</th>
          <th className="pb-2 pr-4">Inquilino</th>
          <th className="pb-2 pr-4">Alícuota</th>
          <th className="pb-2">Monto</th>
        </tr>
      </thead>
      <tbody>
        {breakdown.map((row) => (
          <tr key={row.unitId} className="border-t border-dashed border-border-soft">
            <td className="py-2 pr-4">{row.unitLabel}</td>
            <td className="py-2 pr-4">{row.tenantUid ? residentName(row.tenantUid) : "—"}</td>
            <td className="py-2 pr-4">{row.aliquot}%</td>
            <td className="py-2">
              {row.tenantUid ? (
                currency.format(row.amount)
              ) : (
                <StatusStamp status="pending">Sin asignar</StatusStamp>
              )}
            </td>
          </tr>
        ))}
      </tbody>
      <tfoot>
        <tr className="border-t border-border-soft font-semibold">
          <td className="py-2 pr-4" colSpan={3}>
            Total repartido
          </td>
          <td className="py-2">{currency.format(total)}</td>
        </tr>
      </tfoot>
    </table>
  );
}

export function LiquidacionPage() {
  const { user } = useAuth();
  const buildingId = user?.buildingId ?? "";
  const { units, loading: unitsLoading, error: unitsError } = useUnits(buildingId);
  const { expenses, loading: expensesLoading, error: expensesError } = useExpenses(buildingId);
  const {
    liquidaciones,
    loading: liquidacionesLoading,
    error: liquidacionesError,
    emitirLiquidacion,
  } = useLiquidaciones(buildingId);
  const loading = unitsLoading || expensesLoading || liquidacionesLoading;
  const loadError = unitsError || expensesError || liquidacionesError;
  const residents = useBuildingResidents(buildingId);

  const [period, setPeriod] = useState(toPeriod(new Date()));
  const [selectedHistorico, setSelectedHistorico] = useState<Liquidacion | null>(null);
  const [emitting, setEmitting] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [reviewed, setReviewed] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // "visible" arranca true (el sello entra con stamp-slam) y pasa a false antes de
  // que se desmonte justEmitted, para que salga con un fundido en vez de desaparecer
  // de golpe.
  const [justEmitted, setJustEmitted] = useState(false);
  const [stampVisible, setStampVisible] = useState(false);

  const totalExpenses = expenses
    .filter((e) => e.status === "VERIFICADO" && e.createdAt && toPeriod(e.createdAt) === period)
    .reduce((sum, e) => sum + e.amount, 0);

  const breakdown = calcularLiquidacion(units, totalExpenses);
  const assignedTenants = new Set(breakdown.filter((r) => r.tenantUid).map((r) => r.tenantUid));
  const alreadyEmitted = liquidaciones.some((l) => l.period === period);

  const totalAliquot = units.reduce((sum, u) => sum + u.aliquot, 0);
  const aliquotWarning = units.length > 0 && Math.abs(totalAliquot - 100) > 0.01;

  // liquidaciones viene ordenado period desc (ver useLiquidaciones) — el primero es el
  // período más reciente ya emitido. Emitir uno anterior a ése pisa igual el monto
  // VIGENTE de cada inquilino (expensesAmount es un solo campo, sin período), aunque
  // ya no sea el gasto más reciente — avisar antes de dejar confirmar.
  const latestEmittedPeriod = liquidaciones[0]?.period ?? null;
  const isPastPeriod = latestEmittedPeriod !== null && period < latestEmittedPeriod;

  async function handleConfirmEmitir() {
    if (!user) return;
    setEmitting(true);
    setError(null);
    try {
      await emitirLiquidacion(period, totalExpenses, breakdown, user.uid);
      setConfirming(false);
      setJustEmitted(true);
      setStampVisible(true);
      window.setTimeout(() => setStampVisible(false), 1600);
      window.setTimeout(() => setJustEmitted(false), 2100);
    } catch {
      setError("No se pudo emitir la liquidación. Probá de nuevo.");
    } finally {
      setEmitting(false);
    }
  }

  return (
    <div>
      <h1 className="font-serif text-2xl mb-1">Liquidación de expensas</h1>
      <p className="text-sm text-pending mb-6">
        Reparte los gastos verificados del período entre las unidades, según su alícuota.
      </p>

      <label id="liquidacion-periodo" className="block text-xs uppercase tracking-wide text-pending mb-1">
        Período
      </label>
      <div className="mb-6">
        <PeriodPicker value={period} onChange={setPeriod} labelledBy="liquidacion-periodo" />
      </div>

      {loading ? (
        <p className="text-sm text-pending">Cargando…</p>
      ) : loadError ? (
        <p className="text-warn text-sm">{loadError}</p>
      ) : (
        <>
      <div className="relative bg-white border border-border-soft rounded-lg p-6 mb-8 max-w-2xl">
        {justEmitted && (
          <div
            className={`absolute inset-0 z-10 flex items-center justify-center rounded-lg bg-cream/90 ${
              stampVisible ? "" : "stamp-fade-out"
            }`}
          >
            <div className="stamp-slam border-4 border-ok text-ok font-mono font-bold text-3xl uppercase px-8 py-4 rounded">
              Emitido
            </div>
          </div>
        )}
        <p className="font-serif text-2xl mb-1">{currency.format(totalExpenses)}</p>
        <p className="text-xs uppercase tracking-wide text-pending mb-4">
          Total gastos verificados — {period}
        </p>

        {totalExpenses === 0 && (
          <p className="text-warn text-sm mb-4">
            No hay gastos verificados este período — todos los montos van a quedar en $0.
          </p>
        )}
        {aliquotWarning && (
          <p className="text-warn text-sm mb-4">
            Las alícuotas suman {totalAliquot}%, no 100% — revisá Unidades antes de emitir.
          </p>
        )}
        {isPastPeriod && (
          <p className="text-warn text-sm mb-4">
            Ya emitiste {latestEmittedPeriod}, más reciente que {period}. Emitir igual va a
            actualizar el monto vigente de cada inquilino con el de este período más viejo.
          </p>
        )}
        {alreadyEmitted && (
          <p className="text-warn text-sm mb-4">
            Ya emitiste una liquidación para {period}. Emitir de nuevo sobrescribe esa emisión.
          </p>
        )}

        <BreakdownTable breakdown={breakdown} residents={residents} />

        {error && <p className="text-warn text-sm mt-4">{error}</p>}

        {!confirming ? (
          <button
            onClick={() => {
              setReviewed(false);
              setConfirming(true);
            }}
            disabled={units.length === 0}
            className="mt-6 bg-terracota text-white rounded px-4 py-2 text-sm disabled:opacity-50"
          >
            Emitir liquidación
          </button>
        ) : (
          <div className="mt-6 border border-warn rounded p-4">
            <p className="text-sm mb-3">
              Esto va a actualizar el monto de expensas de {assignedTenants.size}{" "}
              {assignedTenants.size === 1 ? "inquilino" : "inquilinos"}. ¿Confirmar?
            </p>
            <div className="mb-4">
              <Checkbox
                id="reviewed-liquidacion"
                checked={reviewed}
                onChange={setReviewed}
                label="Revisé los montos de esta liquidación"
              />
            </div>
            <button
              onClick={handleConfirmEmitir}
              disabled={emitting || !reviewed}
              className="bg-terracota text-white rounded px-4 py-2 text-sm mr-2 disabled:opacity-50"
            >
              {emitting ? "Emitiendo…" : "Sí, emitir"}
            </button>
            <button
              onClick={() => {
                setConfirming(false);
                setReviewed(false);
              }}
              className="border border-border-soft rounded px-4 py-2 text-sm"
            >
              Cancelar
            </button>
          </div>
        )}
      </div>

      <h2 className="font-serif text-lg mb-3">Historial</h2>
      {liquidaciones.length === 0 && (
        <p className="text-sm text-pending">Todavía no se emitió ninguna liquidación.</p>
      )}
      <ul className="space-y-1 mb-6">
        {liquidaciones.map((l) => (
          <li key={l.period}>
            <button
              onClick={() => setSelectedHistorico(l)}
              className="text-sm underline decoration-dashed"
            >
              {l.period} — {currency.format(l.totalExpenses)}
            </button>
          </li>
        ))}
      </ul>

      {selectedHistorico && (
        <div className="bg-white border border-border-soft rounded-lg p-6 max-w-2xl">
          <div className="flex justify-between items-start mb-1">
            <h3 className="font-serif text-xl">{selectedHistorico.period}</h3>
            <button onClick={() => setSelectedHistorico(null)} className="text-sm text-pending">
              Cerrar
            </button>
          </div>
          <p className="text-xs text-pending mb-4 font-mono">
            Emitido {selectedHistorico.emittedAt ? dateTime.format(selectedHistorico.emittedAt) : "—"}
            {" por "}
            {selectedHistorico.emittedByUid === user?.uid
              ? user.fullName
              : selectedHistorico.emittedByUid}
          </p>
          <BreakdownTable breakdown={selectedHistorico.breakdown} residents={residents} />
        </div>
      )}
        </>
      )}
    </div>
  );
}
