import { useRef, useState, type DragEvent } from "react";

export function Dropzone({
  file,
  onChange,
  id,
}: {
  file: File | null;
  onChange: (file: File | null) => void;
  id?: string;
}) {
  const [dragging, setDragging] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  function handleDrop(e: DragEvent<HTMLDivElement>) {
    e.preventDefault();
    setDragging(false);
    const dropped = e.dataTransfer.files?.[0];
    if (dropped && dropped.type.startsWith("image/")) onChange(dropped);
  }

  return (
    <div
      onClick={() => inputRef.current?.click()}
      onDragOver={(e) => {
        e.preventDefault();
        setDragging(true);
      }}
      onDragLeave={() => setDragging(false)}
      onDrop={handleDrop}
      className={`cursor-pointer rounded border-2 border-dashed px-4 py-6 text-center text-sm transition-colors ${
        dragging ? "border-terracota bg-terracota/5" : "border-border-dashed"
      }`}
    >
      {file ? (
        <p className="text-stone-700">
          📎 {file.name}{" "}
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              onChange(null);
            }}
            className="text-warn underline"
          >
            quitar
          </button>
        </p>
      ) : (
        <p className="text-pending">Arrastrá una imagen acá, o hacé clic para elegirla</p>
      )}
      <input
        ref={inputRef}
        id={id}
        type="file"
        accept="image/*"
        onChange={(e) => onChange(e.target.files?.[0] ?? null)}
        className="hidden"
      />
    </div>
  );
}
