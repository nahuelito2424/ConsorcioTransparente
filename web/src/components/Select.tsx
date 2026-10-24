import { useEffect, useId, useRef, useState, type KeyboardEvent } from "react";

export interface SelectOption {
  value: string;
  label: string;
}

/**
 * Reemplazo de <select> nativo — los combos del navegador no se pueden estilar (cada
 * SO/navegador los dibuja distinto) y rompían la estética del resto de la web.
 *
 * Patrón combobox de WAI-ARIA: el foco del teclado se queda siempre en el botón
 * disparador (las opciones no son tabulables — se navegan con flechas, no con Tab) y
 * aria-activedescendant le dice al lector de pantalla cuál está resaltada. Flechas
 * arriba/abajo mueven el resaltado, Home/End van al primero/último, Enter/Espacio
 * confirman, Escape cierra, y tipear una letra salta a la próxima opción que empiece
 * con ella (como el <select> nativo, aunque sin acumular varias letras seguidas).
 */
export function Select({
  value,
  onChange,
  options,
  placeholder = "Elegir…",
  className = "",
  id,
}: {
  value: string;
  onChange: (value: string) => void;
  options: SelectOption[];
  placeholder?: string;
  className?: string;
  id?: string;
}) {
  const [open, setOpen] = useState(false);
  const [highlighted, setHighlighted] = useState(0);
  const rootRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const listRef = useRef<HTMLDivElement>(null);
  const baseId = useId();
  const selected = options.find((o) => o.value === value);
  const selectedIndex = options.findIndex((o) => o.value === value);

  useEffect(() => {
    if (!open) return;
    function handleClickOutside(e: MouseEvent) {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const el = listRef.current?.children[highlighted] as HTMLElement | undefined;
    el?.scrollIntoView({ block: "nearest" });
  }, [open, highlighted]);

  function openList() {
    setHighlighted(selectedIndex >= 0 ? selectedIndex : 0);
    setOpen(true);
  }

  function confirmHighlighted() {
    const option = options[highlighted];
    if (option) onChange(option.value);
    setOpen(false);
  }

  function handleKeyDown(e: KeyboardEvent<HTMLButtonElement>) {
    switch (e.key) {
      case "ArrowDown":
        e.preventDefault();
        if (!open) openList();
        else setHighlighted((i) => Math.min(i + 1, options.length - 1));
        break;
      case "ArrowUp":
        e.preventDefault();
        if (!open) openList();
        else setHighlighted((i) => Math.max(i - 1, 0));
        break;
      case "Home":
        if (open) {
          e.preventDefault();
          setHighlighted(0);
        }
        break;
      case "End":
        if (open) {
          e.preventDefault();
          setHighlighted(options.length - 1);
        }
        break;
      case "Enter":
      case " ":
        e.preventDefault();
        if (open) confirmHighlighted();
        else openList();
        break;
      case "Escape":
        if (open) {
          e.preventDefault();
          setOpen(false);
        }
        break;
      default:
        if (e.key.length === 1 && /[a-z0-9]/i.test(e.key)) {
          const startFrom = open ? highlighted + 1 : 0;
          const match = findNextMatch(options, e.key, startFrom);
          if (match >= 0) {
            setHighlighted(match);
            setOpen(true);
          }
        }
    }
  }

  const activeId = open && options[highlighted] ? `${baseId}-option-${highlighted}` : undefined;

  return (
    <div ref={rootRef} className={`relative inline-block ${className}`}>
      <button
        ref={buttonRef}
        id={id}
        type="button"
        role="combobox"
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={`${baseId}-listbox`}
        aria-activedescendant={activeId}
        onClick={() => (open ? setOpen(false) : openList())}
        onKeyDown={handleKeyDown}
        onBlur={() => setOpen(false)}
        className="w-full flex items-center justify-between gap-2 border border-border-soft rounded px-3 py-2 bg-white text-left"
      >
        <span className={selected ? "" : "text-pending"}>{selected?.label ?? placeholder}</span>
        <span className={`text-pending transition-transform ${open ? "rotate-180" : ""}`}>▾</span>
      </button>

      {open && (
        <div
          ref={listRef}
          id={`${baseId}-listbox`}
          role="listbox"
          className="absolute z-20 mt-1 min-w-full w-max max-h-64 overflow-auto bg-white border border-border-soft rounded shadow-lg"
        >
          {options.map((option, index) => (
            <div
              key={option.value}
              id={`${baseId}-option-${index}`}
              role="option"
              aria-selected={option.value === value}
              onMouseEnter={() => setHighlighted(index)}
              onClick={() => {
                onChange(option.value);
                setOpen(false);
                buttonRef.current?.focus();
              }}
              className={`px-3 py-2 text-sm border-t border-dashed border-border-soft first:border-t-0 cursor-pointer ${
                index === highlighted ? "bg-cream" : ""
              } ${option.value === value ? "font-semibold" : ""}`}
            >
              {option.label}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function findNextMatch(options: SelectOption[], key: string, startFrom: number): number {
  const letter = key.toLowerCase();
  for (let i = 0; i < options.length; i++) {
    const idx = (startFrom + i) % options.length;
    if (options[idx].label.toLowerCase().startsWith(letter)) return idx;
  }
  return -1;
}
