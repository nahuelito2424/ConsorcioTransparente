import { useState } from "react";
import { useAuth } from "../auth/useAuth";
import { useUnits } from "../hooks/useUnits";
import { useBuildingResidents } from "../hooks/useBuildingResidents";
import { Select, type SelectOption } from "../components/Select";
import type { UnitInfo } from "../types";

// 0 ≤ alícuota ≤ 100 — ver CLAUDE.md: ninguna regla de negocio con consecuencias
// reales vive solo en la UI (la regla equivalente está en firestore.rules).
function isValidAliquot(n: number): boolean {
  return !Number.isNaN(n) && n >= 0 && n <= 100;
}

function UnitRow({
  unit,
  tenantOptions,
  onUpdate,
  onDelete,
}: {
  unit: UnitInfo;
  tenantOptions: SelectOption[];
  onUpdate: (unitId: string, data: Partial<Omit<UnitInfo, "id">>) => void;
  onDelete: (unitId: string) => void;
}) {
  const [label, setLabel] = useState(unit.label);
  const [aliquot, setAliquot] = useState(String(unit.aliquot));
  const [aliquotError, setAliquotError] = useState(false);

  return (
    <tr className="border-t border-dashed border-border-soft">
      <td className="py-2 pr-4">
        <input
          value={label}
          onChange={(e) => setLabel(e.target.value)}
          onBlur={() => label !== unit.label && onUpdate(unit.id, { label })}
          className="border border-border-soft rounded px-2 py-1 w-20"
        />
      </td>
      <td className="py-2 pr-4">
        <input
          type="number"
          value={aliquot}
          onChange={(e) => setAliquot(e.target.value)}
          onBlur={() => {
            const n = Number(aliquot);
            if (!isValidAliquot(n)) {
              setAliquotError(true);
              return;
            }
            setAliquotError(false);
            if (n !== unit.aliquot) onUpdate(unit.id, { aliquot: n });
          }}
          className={`border rounded px-2 py-1 w-20 ${aliquotError ? "border-warn" : "border-border-soft"}`}
        />
        %{aliquotError && <p className="text-warn text-xs">0 a 100</p>}
      </td>
      <td className="py-2 pr-4">
        <Select
          value={unit.tenantUid}
          onChange={(tenantUid) => onUpdate(unit.id, { tenantUid })}
          options={tenantOptions}
        />
      </td>
      <td className="py-2">
        <button onClick={() => onDelete(unit.id)} className="text-warn text-sm">
          Borrar
        </button>
      </td>
    </tr>
  );
}

export function UnidadesPage() {
  const { user } = useAuth();
  const buildingId = user?.buildingId ?? "";
  const { units, loading, error: loadError, createUnit, updateUnit, deleteUnit } = useUnits(buildingId);
  const residents = useBuildingResidents(buildingId);

  const [newLabel, setNewLabel] = useState("");
  const [newAliquot, setNewAliquot] = useState("");
  const [newTenantUid, setNewTenantUid] = useState("");
  const [addError, setAddError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const totalAliquot = units.reduce((sum, u) => sum + u.aliquot, 0);
  const tenantOptions: SelectOption[] = [
    { value: "", label: "Sin asignar" },
    ...residents.map((r) => ({ value: r.uid, label: r.fullName || r.email })),
  ];

  async function handleUpdate(unitId: string, data: Partial<Omit<UnitInfo, "id">>) {
    setActionError(null);
    try {
      await updateUnit(unitId, data);
    } catch {
      setActionError("No se pudo guardar el cambio. Probá de nuevo.");
    }
  }

  async function handleDelete(unitId: string) {
    setActionError(null);
    try {
      await deleteUnit(unitId);
    } catch {
      setActionError("No se pudo borrar la unidad. Probá de nuevo.");
    }
  }

  async function handleAdd() {
    const n = Number(newAliquot);
    if (!newLabel.trim()) {
      setAddError("Falta la etiqueta de la unidad.");
      return;
    }
    if (!isValidAliquot(n)) {
      setAddError("La alícuota tiene que ser un número entre 0 y 100.");
      return;
    }
    setAddError(null);
    try {
      await createUnit({ label: newLabel.trim(), aliquot: n, tenantUid: newTenantUid, ownerUid: "" });
      setNewLabel("");
      setNewAliquot("");
      setNewTenantUid("");
    } catch {
      setAddError("No se pudo agregar la unidad. Probá de nuevo.");
    }
  }

  return (
    <div>
      <h1 className="font-serif text-2xl mb-1">Unidades</h1>
      <p className="text-sm text-pending mb-6">
        Alícuota de cada unidad — se usa para calcular la liquidación de expensas.
      </p>

      {units.length > 0 && Math.abs(totalAliquot - 100) > 0.01 && (
        <p className="text-warn text-sm mb-4">
          Las alícuotas suman {totalAliquot}% — revisá si falta cargar alguna unidad.
        </p>
      )}
      {loadError && <p className="text-warn text-sm mb-4">{loadError}</p>}
      {actionError && <p className="text-warn text-sm mb-4">{actionError}</p>}

      <table className="w-full text-sm mb-2">
        <thead>
          <tr className="text-left text-xs uppercase tracking-wide text-pending">
            <th className="pb-2 pr-4">Unidad</th>
            <th className="pb-2 pr-4">Alícuota</th>
            <th className="pb-2 pr-4">Inquilino</th>
            <th className="pb-2"></th>
          </tr>
        </thead>
        <tbody>
          {loading && (
            <tr>
              <td colSpan={4} className="py-2 text-pending text-sm">
                Cargando…
              </td>
            </tr>
          )}
          {units.map((unit) => (
            <UnitRow
              key={unit.id}
              unit={unit}
              tenantOptions={tenantOptions}
              onUpdate={handleUpdate}
              onDelete={handleDelete}
            />
          ))}
          <tr className="border-t border-dashed border-border-soft">
            <td className="py-2 pr-4">
              <input
                value={newLabel}
                onChange={(e) => setNewLabel(e.target.value)}
                placeholder="7B"
                className="border border-border-soft rounded px-2 py-1 w-20"
              />
            </td>
            <td className="py-2 pr-4">
              <input
                type="number"
                value={newAliquot}
                onChange={(e) => setNewAliquot(e.target.value)}
                placeholder="0"
                className="border border-border-soft rounded px-2 py-1 w-20"
              />
              %
            </td>
            <td className="py-2 pr-4">
              <Select value={newTenantUid} onChange={setNewTenantUid} options={tenantOptions} />
            </td>
            <td className="py-2">
              <button onClick={handleAdd} className="bg-terracota text-white rounded px-3 py-1 text-sm">
                Agregar
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      {addError && <p className="text-warn text-sm">{addError}</p>}
    </div>
  );
}
