import { useState } from "react";
import { useAuth } from "../auth/useAuth";
import { useUnits } from "../hooks/useUnits";
import { useExpenses } from "../hooks/useExpenses";
import { useLiquidaciones } from "../hooks/useLiquidaciones";
import { calcularLiquidacion } from "../lib/liquidacion";
import { StatusStamp } from "../components/StatusStamp";
import type { Liquidacion, LiquidacionBreakdownRow } from "../types";

const currency = new Intl.NumberFormat("es-AR", { style: "currency", currency: "ARS" });

function BreakdownTable({ breakdown }: { breakdown: LiquidacionBreakdownRow[] }) {
  return (
    <table className="w-full text-sm">
      <thead>
        <tr className="text-left text-xs uppercase tracking-wide text-pending">
          <th className="pb-2 pr-4">Unidad</th>
          <th className="pb-2 pr-4">Alícuota</th>
          <th className="pb-2">Monto</th>
        </tr>
      </thead>
      <tbody>
        {breakdown.map((row) => (
          <tr key={row.unitId} className="border-t border-dashed border-border-soft">
            <td className="py-2 pr-4">{row.unitLabel}</td>
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
    </table>
  );
}

export function LiquidacionPage() {
  const { user } = useAuth();
  const buildingId = user?.buildingId ?? "";
  const { units } = useUnits(buildingId);
  const { expenses } = useExpenses(buildingId);
  const { liquidaciones, emitirLiquidacion } = useLiquidaciones(buildingId);

  const [period, setPeriod] = useState(new Date().toISOString().slice(0, 7));
  const [selectedHistorico, setSelectedHistorico] = useState<Liquidacion | null>(null);
  const [emitting, setEmitting] = useState(false);
  const [confirming, setConfirming] = useState(false);

  const totalExpenses = expenses
    .filter((e) => e.status === "VERIFICADO" && e.createdAt?.toISOString().slice(0, 7) === period)
    .reduce((sum, e) => sum + e.amount, 0);

  const breakdown = calcularLiquidacion(units, totalExpenses);
  const assignedCount = breakdown.filter((r) => r.tenantUid).length;
  const alreadyEmitted = liquidaciones.some((l) => l.period === period);

  async function handleConfirmEmitir() {
    if (!user) return;
    setEmitting(true);
    try {
      await emitirLiquidacion(period, totalExpenses, breakdown, user.uid);
      setConfirming(false);
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

      <label className="block text-xs uppercase tracking-wide text-pending mb-1">Período</label>
      <input
        type="month"
        value={period}
        onChange={(e) => setPeriod(e.target.value)}
        className="border border-border-soft rounded px-3 py-2 mb-6"
      />

      <div className="bg-white border border-border-soft rounded-lg p-6 mb-8 max-w-2xl">
        <p className="font-serif text-2xl mb-1">{currency.format(totalExpenses)}</p>
        <p className="text-xs uppercase tracking-wide text-pending mb-4">
          Total gastos verificados — {period}
        </p>

        {totalExpenses === 0 && (
          <p className="text-warn text-sm mb-4">
            No hay gastos verificados este período — todos los montos van a quedar en $0.
          </p>
        )}
        {alreadyEmitted && (
          <p className="text-warn text-sm mb-4">
            Ya emitiste una liquidación para {period}. Emitir de nuevo sobrescribe esa emisión.
          </p>
        )}

        <BreakdownTable breakdown={breakdown} />

        {!confirming ? (
          <button
            onClick={() => setConfirming(true)}
            disabled={units.length === 0}
            className="mt-6 bg-terracota text-white rounded px-4 py-2 text-sm disabled:opacity-50"
          >
            Emitir liquidación
          </button>
        ) : (
          <div className="mt-6 border border-warn rounded p-4">
            <p className="text-sm mb-3">
              Esto va a actualizar el monto de expensas de {assignedCount}{" "}
              {assignedCount === 1 ? "inquilino" : "inquilinos"}. ¿Confirmar?
            </p>
            <button
              onClick={handleConfirmEmitir}
              disabled={emitting}
              className="bg-terracota text-white rounded px-4 py-2 text-sm mr-2 disabled:opacity-50"
            >
              {emitting ? "Emitiendo…" : "Sí, emitir"}
            </button>
            <button
              onClick={() => setConfirming(false)}
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
          <div className="flex justify-between items-center mb-4">
            <h3 className="font-serif text-xl">{selectedHistorico.period}</h3>
            <button onClick={() => setSelectedHistorico(null)} className="text-sm text-pending">
              Cerrar
            </button>
          </div>
          <BreakdownTable breakdown={selectedHistorico.breakdown} />
        </div>
      )}
    </div>
  );
}
