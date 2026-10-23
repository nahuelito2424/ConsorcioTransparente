import { useState, type FormEvent } from "react";
import { useAuth } from "../auth/useAuth";
import { useExpenses } from "../hooks/useExpenses";
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

const currentPeriod = new Date().toISOString().slice(0, 7); // "yyyy-MM"

const currency = new Intl.NumberFormat("es-AR", { style: "currency", currency: "ARS" });

export function GastosPage() {
  const { user } = useAuth();
  const buildingId = user?.buildingId ?? "";
  const { expenses, createExpense } = useExpenses(buildingId);

  const [showForm, setShowForm] = useState(false);
  const [description, setDescription] = useState("");
  const [category, setCategory] = useState<ExpenseCategory>("OTRO");
  const [providerName, setProviderName] = useState("");
  const [amount, setAmount] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const expensesThisMonth = expenses.filter(
    (e) => e.createdAt && e.createdAt.toISOString().slice(0, 7) === currentPeriod
  );

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    const n = Number(amount);
    if (!description.trim() || Number.isNaN(n) || n <= 0 || !user) return;
    setSubmitting(true);
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
          <label className="block text-xs uppercase tracking-wide text-pending mb-1">
            Descripción
          </label>
          <input
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            className="w-full border border-border-soft rounded px-3 py-2 mb-3"
            required
          />

          <label className="block text-xs uppercase tracking-wide text-pending mb-1">
            Categoría
          </label>
          <select
            value={category}
            onChange={(e) => setCategory(e.target.value as ExpenseCategory)}
            className="w-full border border-border-soft rounded px-3 py-2 mb-3"
          >
            {categories.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </select>

          <label className="block text-xs uppercase tracking-wide text-pending mb-1">
            Proveedor (opcional)
          </label>
          <input
            value={providerName}
            onChange={(e) => setProviderName(e.target.value)}
            className="w-full border border-border-soft rounded px-3 py-2 mb-3"
          />

          <label className="block text-xs uppercase tracking-wide text-pending mb-1">Monto</label>
          <input
            type="number"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            className="w-full border border-border-soft rounded px-3 py-2 mb-3"
            required
          />

          <label className="block text-xs uppercase tracking-wide text-pending mb-1">
            Comprobante (imagen, opcional — sin comprobante queda BLOQUEADO)
          </label>
          <input
            type="file"
            accept="image/*"
            onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            className="w-full mb-4 text-sm"
          />

          <button
            type="submit"
            disabled={submitting}
            className="w-full bg-terracota text-white rounded py-2 font-semibold disabled:opacity-50"
          >
            Guardar gasto
          </button>
        </form>
      )}

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
          {expensesThisMonth.length === 0 && (
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
