import { toPeriod } from "../lib/period";

const monthLabel = new Intl.DateTimeFormat("es-AR", { month: "long", year: "numeric" });

export function shiftPeriod(period: string, delta: number): string {
  const [year, month] = period.split("-").map(Number);
  // Día 15 (no 1) para no caer nunca en el día inexistente de otro mes al sumar/restar
  // — no importa qué día del mes caiga, toPeriod sólo mira año y mes.
  return toPeriod(new Date(year, month - 1 + delta, 15));
}

/**
 * Reemplazo de <input type="month"> — el picker nativo de fecha se ve completamente
 * distinto según navegador/SO (y en algunos ni pide confirmación visual del mes en
 * español). Navegación mes a mes con flechas, que es como realmente se usa esta
 * pantalla (expensas es siempre "este mes" o "el anterior", casi nunca un salto lejano).
 */
export function PeriodPicker({
  value,
  onChange,
  labelledBy,
}: {
  value: string;
  onChange: (period: string) => void;
  labelledBy?: string;
}) {
  const [year, month] = value.split("-").map(Number);
  const label = monthLabel.format(new Date(year, month - 1, 1));

  return (
    <div
      role="group"
      aria-labelledby={labelledBy}
      className="inline-flex items-center border border-border-soft rounded overflow-hidden bg-white"
    >
      <button
        type="button"
        onClick={() => onChange(shiftPeriod(value, -1))}
        aria-label="Mes anterior"
        className="px-3 py-2 text-pending hover:bg-cream hover:text-stone-900"
      >
        ‹
      </button>
      <span className="font-serif px-2 w-40 text-center select-none">{label}</span>
      <button
        type="button"
        onClick={() => onChange(shiftPeriod(value, 1))}
        aria-label="Mes siguiente"
        className="px-3 py-2 text-pending hover:bg-cream hover:text-stone-900"
      >
        ›
      </button>
    </div>
  );
}
