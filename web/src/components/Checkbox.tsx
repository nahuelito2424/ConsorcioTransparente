/**
 * Reemplazo visual de <input type="checkbox"> — el checkbox nativo tampoco se puede
 * estilar de forma consistente entre navegadores. El <input> real sigue ahí (oculto con
 * sr-only, no display:none) para no perder teclado/lector de pantalla; el cuadrado
 * visible es un <span> hermano que seguimos con peer-checked.
 */
export function Checkbox({
  checked,
  onChange,
  label,
  id,
}: {
  checked: boolean;
  onChange: (checked: boolean) => void;
  label: string;
  id?: string;
}) {
  return (
    <label htmlFor={id} className="inline-flex items-center gap-2 cursor-pointer select-none">
      <input
        id={id}
        type="checkbox"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
        className="peer sr-only"
      />
      <span className="w-5 h-5 shrink-0 flex items-center justify-center rounded border-2 border-border-dashed bg-white text-white text-xs font-mono leading-none transition-colors peer-checked:bg-terracota peer-checked:border-terracota peer-focus-visible:ring-2 peer-focus-visible:ring-terracota/40">
        {checked && "✓"}
      </span>
      <span className="text-sm">{label}</span>
    </label>
  );
}
