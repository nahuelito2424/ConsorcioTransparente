import { useState } from "react";
import { useAuth } from "../auth/useAuth";
import { useBuilding } from "../hooks/useBuilding";
import { useExpenses } from "../hooks/useExpenses";
import { calcularRendicion } from "../lib/rendicion";
import { toPeriod } from "../lib/period";
import { PeriodPicker } from "../components/PeriodPicker";

const currency = new Intl.NumberFormat("es-AR", { style: "currency", currency: "ARS" });
const monthLabel = new Intl.DateTimeFormat("es-AR", { month: "long", year: "numeric" });
const today = new Intl.DateTimeFormat("es-AR", { dateStyle: "short" }).format(new Date());
const categoryLabel: Record<string, string> = {
  REPUESTOS: "Repuestos",
  MANO_DE_OBRA: "Mano de obra",
  PLOMERIA: "Plomería",
  ELECTRICIDAD: "Electricidad",
  GAS: "Gas",
  ALBANILERIA: "Albañilería",
  LIMPIEZA: "Limpieza",
  OTRO: "Otro",
};

export function RendicionPage() {
  const { user } = useAuth();
  const buildingId = user?.buildingId ?? "";
  const { building, loading: buildingLoading } = useBuilding(buildingId);
  const { expenses, loading: expensesLoading, error } = useExpenses(buildingId);
  const [period, setPeriod] = useState(toPeriod(new Date()));

  const loading = buildingLoading || expensesLoading;
  const rendicion = calcularRendicion(expenses, period);
  const [year, month] = period.split("-").map(Number);
  const periodLabel = monthLabel.format(new Date(year, month - 1, 1));
  const periodLabelCapitalized = periodLabel.charAt(0).toUpperCase() + periodLabel.slice(1);

  return (
    <div>
      {/* Mismo contenido que RendicionPdfGenerator.kt (Android), en una página que se
          puede "Guardar como PDF" desde el diálogo de impresión del navegador — sin
          agregar una librería de PDF nueva sólo para esto. */}
      <style>{`
        @media print {
          .no-print { display: none !important; }
          #rendicion-doc { box-shadow: none !important; border: none !important; }
        }
      `}</style>

      <div className="no-print flex items-center justify-between mb-6 flex-wrap gap-3">
        <div>
          <h1 className="font-serif text-2xl mb-1">Rendición de cuentas</h1>
          <p className="text-sm text-pending">Desglose de gastos verificados por categoría.</p>
        </div>
        <div className="flex items-center gap-3">
          <PeriodPicker value={period} onChange={setPeriod} />
          <button
            onClick={() => window.print()}
            className="bg-terracota text-white rounded px-4 py-2 text-sm"
          >
            Descargar / Imprimir
          </button>
        </div>
      </div>

      {loading ? (
        <p className="text-sm text-pending">Cargando…</p>
      ) : error ? (
        <p className="text-warn text-sm">{error}</p>
      ) : (
        <div id="rendicion-doc" className="bg-white border border-border-soft rounded-lg p-8 max-w-2xl">
          <h2 className="font-serif text-xl mb-1">{building?.name || "Consorcio"}</h2>
          <p className="text-sm text-pending mb-6">Rendición de cuentas — {periodLabelCapitalized}</p>

          <div className="flex justify-between font-semibold mb-2 pb-2 border-b border-border-soft">
            <span>Total gastado</span>
            <span>{currency.format(rendicion.totalVerificado)}</span>
          </div>

          {rendicion.bloqueadoCount > 0 && (
            <p className="text-xs text-pending mb-4">
              + {rendicion.bloqueadoCount} {rendicion.bloqueadoCount === 1 ? "gasto pendiente" : "gastos pendientes"}{" "}
              de comprobante ({currency.format(rendicion.bloqueadoTotal)}), no incluido(s) en este total.
            </p>
          )}

          {rendicion.categoryTotals.length === 0 ? (
            <p className="text-sm text-pending mt-4">No hay gastos verificados este período.</p>
          ) : (
            <div className="mt-4 space-y-4">
              {rendicion.categoryTotals.map(({ category, total, expenses: catExpenses }) => (
                <div key={category}>
                  <div className="flex justify-between font-semibold text-sm mb-1">
                    <span>{categoryLabel[category] ?? category}</span>
                    <span>{currency.format(total)}</span>
                  </div>
                  <ul className="text-sm text-stone-700 space-y-0.5">
                    {catExpenses.map((e) => (
                      <li key={e.id} className="flex justify-between pl-4">
                        <span>
                          {e.description} — {e.providerName || "sin proveedor"}
                        </span>
                        <span>{currency.format(e.amount)}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              ))}
            </div>
          )}

          <p className="text-xs text-pending mt-8 pt-4 border-t border-dashed border-border-soft">
            Generado desde Consorcio Transparente el {today} — no reemplaza la rendición de cuentas formal del
            edificio si existe una vía distinta.
          </p>
        </div>
      )}
    </div>
  );
}
