import { useState } from "react";
import { useAuth } from "../auth/useAuth";
import { useUnits } from "../hooks/useUnits";
import { useBuildingResidents } from "../hooks/useBuildingResidents";
import type { UnitInfo } from "../types";

function UnitRow({
  unit,
  residents,
  onUpdate,
  onDelete,
}: {
  unit: UnitInfo;
  residents: ReturnType<typeof useBuildingResidents>;
  onUpdate: (unitId: string, data: Partial<Omit<UnitInfo, "id">>) => void;
  onDelete: (unitId: string) => void;
}) {
  const [label, setLabel] = useState(unit.label);
  const [aliquot, setAliquot] = useState(String(unit.aliquot));

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
            if (!Number.isNaN(n) && n !== unit.aliquot) onUpdate(unit.id, { aliquot: n });
          }}
          className="border border-border-soft rounded px-2 py-1 w-20"
        />
        %
      </td>
      <td className="py-2 pr-4">
        <select
          value={unit.tenantUid}
          onChange={(e) => onUpdate(unit.id, { tenantUid: e.target.value })}
          className="border border-border-soft rounded px-2 py-1"
        >
          <option value="">Sin asignar</option>
          {residents.map((r) => (
            <option key={r.uid} value={r.uid}>
              {r.fullName || r.email}
            </option>
          ))}
        </select>
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
  const { units, createUnit, updateUnit, deleteUnit } = useUnits(buildingId);
  const residents = useBuildingResidents(buildingId);

  const [newLabel, setNewLabel] = useState("");
  const [newAliquot, setNewAliquot] = useState("");
  const [newTenantUid, setNewTenantUid] = useState("");

  const totalAliquot = units.reduce((sum, u) => sum + u.aliquot, 0);

  async function handleAdd() {
    const n = Number(newAliquot);
    if (!newLabel.trim() || Number.isNaN(n)) return;
    await createUnit({ label: newLabel.trim(), aliquot: n, tenantUid: newTenantUid, ownerUid: "" });
    setNewLabel("");
    setNewAliquot("");
    setNewTenantUid("");
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

      <table className="w-full text-sm mb-6">
        <thead>
          <tr className="text-left text-xs uppercase tracking-wide text-pending">
            <th className="pb-2 pr-4">Unidad</th>
            <th className="pb-2 pr-4">Alícuota</th>
            <th className="pb-2 pr-4">Inquilino</th>
            <th className="pb-2"></th>
          </tr>
        </thead>
        <tbody>
          {units.map((unit) => (
            <UnitRow
              key={unit.id}
              unit={unit}
              residents={residents}
              onUpdate={updateUnit}
              onDelete={deleteUnit}
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
              <select
                value={newTenantUid}
                onChange={(e) => setNewTenantUid(e.target.value)}
                className="border border-border-soft rounded px-2 py-1"
              >
                <option value="">Sin asignar</option>
                {residents.map((r) => (
                  <option key={r.uid} value={r.uid}>
                    {r.fullName || r.email}
                  </option>
                ))}
              </select>
            </td>
            <td className="py-2">
              <button onClick={handleAdd} className="bg-terracota text-white rounded px-3 py-1 text-sm">
                Agregar
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  );
}
