/**
 * Reemplazo visual de <input type="radio"> — mismo motivo que Checkbox: el radio
 * nativo no se puede estilar de forma consistente. El <input> real sigue ahí (sr-only,
 * no display:none) para no perder teclado/lector de pantalla; el círculo visible es un
 * <span> hermano que seguimos con peer-checked. Agrupar varios con el mismo `name` les
 * da la navegación con flechas nativa del radio group del navegador.
 */
export interface RadioOption<T extends string> {
  value: T;
  label: string;
}

export function RadioGroup<T extends string>({
  name,
  value,
  onChange,
  options,
}: {
  name: string;
  value: T;
  onChange: (value: T) => void;
  options: RadioOption<T>[];
}) {
  return (
    <div role="radiogroup" className="flex flex-wrap gap-4">
      {options.map((option) => (
        <label key={option.value} className="inline-flex items-center gap-2 cursor-pointer select-none text-sm">
          <input
            type="radio"
            name={name}
            checked={value === option.value}
            onChange={() => onChange(option.value)}
            className="peer sr-only"
          />
          <span className="w-4 h-4 shrink-0 rounded-full border-2 border-border-dashed bg-white flex items-center justify-center transition-colors peer-checked:border-terracota peer-focus-visible:ring-2 peer-focus-visible:ring-terracota/40">
            <span
              className={`w-2 h-2 rounded-full bg-terracota transition-opacity ${
                value === option.value ? "opacity-100" : "opacity-0"
              }`}
            />
          </span>
          {option.label}
        </label>
      ))}
    </div>
  );
}
