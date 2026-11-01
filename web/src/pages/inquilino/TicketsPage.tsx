import { useState, type FormEvent } from "react";
import { useAuth } from "../../auth/useAuth";
import { useMyTickets } from "../../hooks/useTickets";
import { Select } from "../../components/Select";
import { Dropzone } from "../../components/Dropzone";
import type { Ticket, TicketStatus } from "../../types";

const currency = new Intl.NumberFormat("es-AR", { style: "currency", currency: "ARS" });
const categories = ["Plomería", "Electricidad", "Gas", "Albañilería", "Otro"];
const categoryOptions = categories.map((c) => ({ value: c, label: c }));

const statusLabel: Record<TicketStatus, string> = {
  REPORTADO: "Reportado — esperando presupuesto",
  EN_PRESUPUESTO: "Presupuestado — esperando la compra",
  A_COBRAR: "Listo para pagar",
  RESUELTO: "Resuelto",
};

function TicketCard({ ticket, onPay }: { ticket: Ticket; onPay: (id: string, file: File) => Promise<void> }) {
  const [file, setFile] = useState<File | null>(null);
  const [paying, setPaying] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handlePay() {
    if (!file) return;
    setPaying(true);
    setError(null);
    try {
      await onPay(ticket.id, file);
    } catch {
      setError("No se pudo registrar el pago. Probá de nuevo.");
    } finally {
      setPaying(false);
    }
  }

  return (
    <li className="bg-white border border-border-soft rounded-lg p-5">
      <div className="flex justify-between items-start mb-1">
        <h2 className="font-serif text-lg">{ticket.category}</h2>
        <span className="text-xs font-mono font-bold uppercase px-2 py-0.5 rounded border border-pending text-pending">
          {ticket.status}
        </span>
      </div>
      <p className="text-sm text-stone-700 mb-2">{ticket.description}</p>
      <p className="text-xs text-pending mb-3">{statusLabel[ticket.status]}</p>

      {ticket.budgetAmount > 0 && (
        <p className="text-sm mb-2">Presupuesto: {currency.format(ticket.budgetAmount)}</p>
      )}

      {ticket.status === "A_COBRAR" && (
        <div className="mt-3">
          <p className="text-xs uppercase tracking-wide text-pending mb-1">Subí el comprobante de pago</p>
          <Dropzone file={file} onChange={setFile} />
          {error && <p className="text-warn text-sm mt-2">{error}</p>}
          <button
            onClick={handlePay}
            disabled={!file || paying}
            className="mt-3 bg-terracota text-white rounded px-4 py-2 text-sm disabled:opacity-50"
          >
            {paying ? "Enviando…" : "Marcar como pagado"}
          </button>
        </div>
      )}
    </li>
  );
}

export function TicketsPage() {
  const { user } = useAuth();
  const buildingId = user?.buildingId ?? "";
  const { tickets, loading, error: loadError, createTicket, markPaid } = useMyTickets(buildingId, user?.uid ?? "");

  const [showForm, setShowForm] = useState(false);
  const [category, setCategory] = useState("Otro");
  const [description, setDescription] = useState("");
  const [photo, setPhoto] = useState<File | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!description.trim() || !user) return;
    setSubmitting(true);
    setError(null);
    try {
      await createTicket(category, description.trim(), user.uid, photo);
      setCategory("Otro");
      setDescription("");
      setPhoto(null);
      setShowForm(false);
    } catch {
      setError("No se pudo reportar el problema. Probá de nuevo.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div>
      <h1 className="font-serif text-2xl mb-1">Reclamar un problema</h1>
      <p className="text-sm text-pending mb-6">Tus reportes de mantenimiento y su estado.</p>

      <button
        onClick={() => setShowForm((v) => !v)}
        className="bg-terracota text-white rounded px-4 py-2 text-sm mb-6"
      >
        {showForm ? "Cancelar" : "Reportar un problema"}
      </button>

      {showForm && (
        <form
          onSubmit={handleSubmit}
          className="bg-white border border-border-soft rounded-lg p-6 mb-6 max-w-md"
        >
          <label htmlFor="ticket-categoria" className="block text-xs uppercase tracking-wide text-pending mb-1">
            Categoría
          </label>
          <div className="mb-3">
            <Select id="ticket-categoria" value={category} onChange={setCategory} options={categoryOptions} className="w-full" />
          </div>

          <label htmlFor="ticket-descripcion" className="block text-xs uppercase tracking-wide text-pending mb-1">
            Descripción
          </label>
          <textarea
            id="ticket-descripcion"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            rows={4}
            className="w-full border border-border-soft rounded px-3 py-2 mb-3"
            required
          />

          <label className="block text-xs uppercase tracking-wide text-pending mb-1">Foto (opcional)</label>
          <div className="mb-4">
            <Dropzone file={photo} onChange={setPhoto} />
          </div>

          {error && <p className="text-warn text-sm mb-4">{error}</p>}

          <button
            type="submit"
            disabled={submitting}
            className="w-full bg-terracota text-white rounded py-2 font-semibold disabled:opacity-50"
          >
            {submitting ? "Enviando…" : "Reportar"}
          </button>
        </form>
      )}

      {loadError && <p className="text-warn text-sm mb-4">{loadError}</p>}

      {loading ? (
        <p className="text-sm text-pending">Cargando…</p>
      ) : tickets.length === 0 ? (
        <p className="text-sm text-pending">Todavía no reportaste ningún problema.</p>
      ) : (
        <ul className="space-y-4 max-w-xl">
          {tickets.map((t) => (
            <TicketCard key={t.id} ticket={t} onPay={markPaid} />
          ))}
        </ul>
      )}
    </div>
  );
}
