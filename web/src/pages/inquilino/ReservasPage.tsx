import { useEffect, useState, type FormEvent } from "react";
import { useAuth } from "../../auth/useAuth";
import { useMyReservations, listReservationsForDate } from "../../hooks/useReservations";
import { Select } from "../../components/Select";
import type { Reservation } from "../../types";

const spaces = ["SUM", "Parrilla", "Pileta"];
const spaceOptions = spaces.map((s) => ({ value: s, label: s }));

function todayIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

export function ReservasPage() {
  const { user } = useAuth();
  const buildingId = user?.buildingId ?? "";
  const today = todayIso();
  const { reservations, loading, error: loadError, reserve, cancel } = useMyReservations(
    buildingId,
    user?.uid ?? "",
    today
  );

  const [spaceName, setSpaceName] = useState(spaces[0]);
  const [date, setDate] = useState(today);
  const [timeSlot, setTimeSlot] = useState("");
  const [taken, setTaken] = useState<Reservation[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!buildingId || !spaceName || !date) return;
    listReservationsForDate(buildingId, spaceName, date).then(setTaken);
  }, [buildingId, spaceName, date]);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!timeSlot.trim() || !user) return;
    setSubmitting(true);
    setError(null);
    try {
      await reserve(spaceName, date, timeSlot.trim(), user.uid, user.fullName);
      setTimeSlot("");
      setTaken(await listReservationsForDate(buildingId, spaceName, date));
    } catch {
      setError("Ese horario ya está reservado, o no se pudo guardar. Probá con otro.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleCancel(id: string) {
    await cancel(id);
  }

  return (
    <div>
      <h1 className="font-serif text-2xl mb-1">Reserva de espacios comunes</h1>
      <p className="text-sm text-pending mb-6">SUM, parrilla y pileta — por fecha y horario.</p>

      <form onSubmit={handleSubmit} className="bg-white border border-border-soft rounded-lg p-6 mb-8 max-w-md">
        <label className="block text-xs uppercase tracking-wide text-pending mb-1">Espacio</label>
        <div className="mb-3">
          <Select value={spaceName} onChange={setSpaceName} options={spaceOptions} className="w-full" />
        </div>

        <label htmlFor="reserva-fecha" className="block text-xs uppercase tracking-wide text-pending mb-1">
          Fecha
        </label>
        <input
          id="reserva-fecha"
          type="date"
          value={date}
          min={today}
          onChange={(e) => setDate(e.target.value)}
          className="w-full border border-border-soft rounded px-3 py-2 mb-3"
          required
        />

        {taken.length > 0 && (
          <p className="text-xs text-pending mb-3">
            Ya reservado ese día: {taken.map((r) => r.timeSlot).join(", ")}
          </p>
        )}

        <label htmlFor="reserva-horario" className="block text-xs uppercase tracking-wide text-pending mb-1">
          Horario
        </label>
        <input
          id="reserva-horario"
          value={timeSlot}
          onChange={(e) => setTimeSlot(e.target.value)}
          placeholder="18:00-22:00"
          className="w-full border border-border-soft rounded px-3 py-2 mb-3"
          required
        />

        {error && <p className="text-warn text-sm mb-4">{error}</p>}

        <button
          type="submit"
          disabled={submitting}
          className="w-full bg-terracota text-white rounded py-2 font-semibold disabled:opacity-50"
        >
          {submitting ? "Reservando…" : "Reservar"}
        </button>
      </form>

      <h2 className="font-serif text-lg mb-3">Mis próximas reservas</h2>
      {loading ? (
        <p className="text-sm text-pending">Cargando…</p>
      ) : loadError ? (
        <p className="text-warn text-sm">{loadError}</p>
      ) : reservations.length === 0 ? (
        <p className="text-sm text-pending">No tenés reservas próximas.</p>
      ) : (
        <ul className="space-y-2 max-w-md">
          {reservations.map((r) => (
            <li
              key={r.id}
              className="bg-white border border-border-soft rounded-lg p-4 flex justify-between items-center"
            >
              <span className="text-sm">
                {r.spaceName} — {r.date} {r.timeSlot}
              </span>
              <button onClick={() => handleCancel(r.id)} className="text-warn text-sm underline">
                Cancelar
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
