import { useEffect, useRef, useState } from "react";

export interface SelectOption {
  value: string;
  label: string;
}

/**
 * Reemplazo de <select> nativo — los combos del navegador no se pueden estilar (cada
 * SO/navegador los dibuja distinto) y rompían la estética del resto de la web. Patrón
 * de botón + panel flotante, no un combobox ARIA completo (sin navegación con flechas):
 * alcanza para listas cortas (categorías, inquilinos), que es todo lo que hay acá.
 */
export function Select({
  value,
  onChange,
  options,
  placeholder = "Elegir…",
  className = "",
}: {
  value: string;
  onChange: (value: string) => void;
  options: SelectOption[];
  placeholder?: string;
  className?: string;
}) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const selected = options.find((o) => o.value === value);

  useEffect(() => {
    if (!open) return;
    function handleClickOutside(e: MouseEvent) {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) setOpen(false);
    }
    function handleEscape(e: KeyboardEvent) {
      if (e.key === "Escape") setOpen(false);
    }
    document.addEventListener("mousedown", handleClickOutside);
    document.addEventListener("keydown", handleEscape);
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
      document.removeEventListener("keydown", handleEscape);
    };
  }, [open]);

  return (
    <div ref={rootRef} className={`relative inline-block ${className}`}>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="w-full flex items-center justify-between gap-2 border border-border-soft rounded px-3 py-2 bg-white text-left"
      >
        <span className={selected ? "" : "text-pending"}>{selected?.label ?? placeholder}</span>
        <span className={`text-pending transition-transform ${open ? "rotate-180" : ""}`}>▾</span>
      </button>

      {open && (
        <div className="absolute z-20 mt-1 min-w-full w-max max-h-64 overflow-auto bg-white border border-border-soft rounded shadow-lg">
          {options.map((option) => (
            <button
              key={option.value}
              type="button"
              onClick={() => {
                onChange(option.value);
                setOpen(false);
              }}
              className={`block w-full text-left px-3 py-2 text-sm border-t border-dashed border-border-soft first:border-t-0 hover:bg-cream ${
                option.value === value ? "bg-cream font-semibold" : ""
              }`}
            >
              {option.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
