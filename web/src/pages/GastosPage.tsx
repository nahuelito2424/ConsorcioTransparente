import { useRef, useState, type DragEvent, type FormEvent } from "react";
import { useAuth } from "../auth/useAuth";
import { useExpenses } from "../hooks/useExpenses";
import { toPeriod } from "../lib/period";
import { Select } from "../components/Select";
import type { ExpenseCategory } from "../types";

const categories: ExpenseCategory[] = [
  "REPUESTOS",
  "MANO_DE_OBRA",
  "PLOMERIA",
  "ELECTRICIDAD",
  "GAS",
  "ALBANILERIA",
  "LIMPIEZA",
  "OTRO",
];
const categoryOptions = categories.map((c) => ({ value: c, label: c }));

const currency = new Intl.NumberFormat("es-AR", { style: "currency", currency: "ARS" });

function Dropzone({
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

export function GastosPage() {
  const { user } = useAuth();
  const buildingId = user?.buildingId ?? "";
  const { expenses, loading, error: loadError, createExpense } = useExpenses(buildingId);
  const currentPeriod = toPeriod(new Date());

  const [showForm, setShowForm] = useState(false);
  const [description, setDescription] = useState("");
  const [category, setCategory] = useState<ExpenseCategory>("OTRO");
  const [providerName, setProviderName] = useState("");
  const [amount, setAmount] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const expensesThisMonth = expenses.filter(
    (e) => e.createdAt && toPeriod(e.createdAt) === currentPeriod
  );

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    const n = Number(amount);
    if (!description.trim() || Number.isNaN(n) || n <= 0 || !user) return;
    setSubmitting(true);
    setError(null);
    try {
      await createExpense({
        description: description.trim(),
        category,
        providerName: providerName.trim(),
        amount: n,
        createdByUid: user.uid,
        file,
      });
      setDescription("");
      setCategory("OTRO");
      setProviderName("");
      setAmount("");
      setFile(null);
      setShowForm(false);
    } catch {
      setError("No se pudo guardar el gasto. Probá de nuevo.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div>
      <h1 className="font-serif text-2xl mb-1">Gastos del mes</h1>
      <p className="text-sm text-pending mb-6">
        Sólo los gastos VERIFICADO (con comprobante) entran en la liquidación de expensas.
      </p>

      <button
        onClick={() => setShowForm((v) => !v)}
        className="bg-terracota text-white rounded px-4 py-2 text-sm mb-6"
      >
        {showForm ? "Cancelar" : "Agregar gasto"}
      </button>

      {showForm && (
        <form
          onSubmit={handleSubmit}
          className="bg-white border border-border-soft rounded-lg p-6 mb-6 max-w-md"
        >
          <label htmlFor="gasto-descripcion" className="block text-xs uppercase tracking-wide text-pending mb-1">
            Descripción
          </label>
          <input
            id="gasto-descripcion"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            className="w-full border border-border-soft rounded px-3 py-2 mb-3"
            required
          />

          <label htmlFor="gasto-categoria" className="block text-xs uppercase tracking-wide text-pending mb-1">
            Categoría
          </label>
          <div className="mb-3">
            <Select
              id="gasto-categoria"
              value={category}
              onChange={(v) => setCategory(v as ExpenseCategory)}
              options={categoryOptions}
              className="w-full"
            />
          </div>

          <label htmlFor="gasto-proveedor" className="block text-xs uppercase tracking-wide text-pending mb-1">
            Proveedor (opcional)
          </label>
          <input
            id="gasto-proveedor"
            value={providerName}
            onChange={(e) => setProviderName(e.target.value)}
            className="w-full border border-border-soft rounded px-3 py-2 mb-3"
          />

          <label htmlFor="gasto-monto" className="block text-xs uppercase tracking-wide text-pending mb-1">
            Monto
          </label>
          <input
            id="gasto-monto"
            type="number"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            className="w-full border border-border-soft rounded px-3 py-2 mb-3"
            required
          />

          <label htmlFor="gasto-comprobante" className="block text-xs uppercase tracking-wide text-pending mb-1">
            Comprobante (imagen, opcional — sin comprobante queda BLOQUEADO)
          </label>
          <div className="mb-4">
            <Dropzone id="gasto-comprobante" file={file} onChange={setFile} />
          </div>

          {error && <p className="text-warn text-sm mb-4">{error}</p>}

          <button
            type="submit"
            disabled={submitting}
            className="w-full bg-terracota text-white rounded py-2 font-semibold disabled:opacity-50"
          >
            Guardar gasto
          </button>
        </form>
      )}

      {loadError && <p className="text-warn text-sm mb-4">{loadError}</p>}

      <table className="w-full text-sm">
        <thead>
          <tr className="text-left text-xs uppercase tracking-wide text-pending">
            <th className="pb-2 pr-4">Descripción</th>
            <th className="pb-2 pr-4">Categoría</th>
            <th className="pb-2 pr-4">Monto</th>
            <th className="pb-2">Estado</th>
          </tr>
        </thead>
        <tbody>
          {expensesThisMonth.map((expense) => (
            <tr key={expense.id} className="border-t border-dashed border-border-soft">
              <td className="py-2 pr-4">{expense.description}</td>
              <td className="py-2 pr-4">{expense.category}</td>
              <td className="py-2 pr-4">{currency.format(expense.amount)}</td>
              <td className="py-2">
                <span
                  className={`text-xs font-mono font-bold uppercase px-2 py-0.5 rounded border ${
                    expense.status === "VERIFICADO"
                      ? "border-ok text-ok"
                      : "border-warn text-warn"
                  }`}
                  style={{ transform: "rotate(-4deg)", display: "inline-block" }}
                >
                  {expense.status}
                </span>
              </td>
            </tr>
          ))}
          {loading && (
            <tr>
              <td colSpan={4} className="py-4 text-pending text-sm">
                Cargando…
              </td>
            </tr>
          )}
          {!loading && expensesThisMonth.length === 0 && (
            <tr>
              <td colSpan={4} className="py-4 text-pending text-sm">
                Todavía no hay gastos cargados este mes.
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
