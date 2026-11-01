import { useEffect, useState } from "react";
import { useAuth } from "../../auth/useAuth";
import { useBuilding } from "../../hooks/useBuilding";
import { useMyReservations, listReservationsForDate } from "../../hooks/useReservations";
import { Select } from "../../components/Select";
import type { Reservation } from "../../types";

// Mismas franjas fijas que ReservasViewModel.kt (Android, TIME_SLOTS) — un horario
// de texto libre en la web rompería la protección contra doble reserva, que depende
// de que los dos clientes generen exactamente el mismo id de documento para la misma
// franja (ver useReservations.ts, slotId()).
const TIME_SLOTS = ["09:00-13:00", "13:00-17:00", "17:00-21:00", "21:00-01:00"];

function todayIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

export function ReservasPage() {
  const { user } = useAuth();
  const buildingId = user?.buildingId ?? "";
  const today = todayIso();
  const { building } = useBuilding(buildingId);
  const { reservations, loading, error: loadError, reserve, cancel } = useMyReservations(
    buildingId,
    user?.uid ?? "",
    today
  );

  // Mismo fallback que Android: building.amenities vacío o sin cargar => sólo SUM.
  const spaces = building?.amenities?.length ? building.amenities : ["SUM"];
  const spaceOptions = spaces.map((s) => ({ value: s, label: s }));

  const [spaceName, setSpaceName] = useState(spaces[0]);
  const [date, setDate] = useState(today);
  const [taken, setTaken] = useState<Reservation[]>([]);
  const [submitting, setSubmitting] = useState<string | null>(null); // franja en curso
  const [error, setError] = useState<string | null>(null);
  const [cancelError, setCancelError] = useState<string | null>(null);

  // spaces deriva de building.amenities y cambia de referencia en cada render —
  // dispara el efecto por su contenido real (join), no por identidad del array.
  const spacesKey = spaces.join(",");
  useEffect(() => {
    if (!spaces.includes(spaceName)) setSpaceName(spaces[0]);
  }, [spacesKey]);

  useEffect(() => {
    if (!buildingId || !spaceName || !date) return;
    let cancelled = false;
    listReservationsForDate(buildingId, spaceName, date)
      .then((r) => {
        if (!cancelled) setTaken(r);
      })
      .catch(() => {
        if (!cancelled) setTaken([]);
      });
    return () => {
      cancelled = true;
    };
  }, [buildingId, spaceName, date]);

  async function handleReserve(timeSlot: string) {
    if (!user) return;
    setSubmitting(timeSlot);
    setError(null);
    try {
      await reserve(spaceName, date, timeSlot, user.uid, user.fullName);
      setTaken(await listReservationsForDate(buildingId, spaceName, date));
    } catch {
      setError("Ese horario ya está reservado, o no se pudo guardar. Probá con otro.");
    } finally {
      setSubmitting(null);
    }
  }

  async function handleCancel(id: string) {
    setCancelError(null);
    try {
      await cancel(id);
    } catch {
      setCancelError("No se pudo cancelar la reserva. Probá de nuevo.");
    }
  }

  return (
    <div>
      <h1 className="font-serif text-2xl mb-1">Reserva de espacios comunes</h1>
      <p className="text-sm text-pending mb-6">Por fecha y franja horaria.</p>

      <div className="bg-white border border-border-soft rounded-lg p-6 mb-8 max-w-md">
        <label htmlFor="reserva-espacio" className="block text-xs uppercase tracking-wide text-pending mb-1">
          Espacio
        </label>
        <div className="mb-3">
          <Select id="reserva-espacio" value={spaceName} onChange={setSpaceName} options={spaceOptions} className="w-full" />
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
          className="w-full border border-border-soft rounded px-3 py-2 mb-4"
        />

        <p className="block text-xs uppercase tracking-wide text-pending mb-2">Franja horaria</p>
        <div className="space-y-2 mb-2">
          {TIME_SLOTS.map((slot) => {
            const owner = taken.find((r) => r.timeSlot === slot);
            return (
              <button
                key={slot}
                type="button"
                onClick={() => handleReserve(slot)}
                disabled={Boolean(owner) || submitting !== null}
                className="w-full flex justify-between items-center border border-border-soft rounded px-3 py-2 text-sm disabled:opacity-50 hover:bg-cream disabled:hover:bg-transparent"
              >
                <span>{slot}</span>
                <span className="text-pending">
                  {submitting === slot ? "Reservando…" : owner ? "Ocupado" : "Libre"}
                </span>
              </button>
            );
          })}
        </div>

        {error && <p className="text-warn text-sm mt-2">{error}</p>}
      </div>

      <h2 className="font-serif text-lg mb-3">Mis próximas reservas</h2>
      {cancelError && <p className="text-warn text-sm mb-3">{cancelError}</p>}
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
