import { useState } from "react";
import { emergencyContacts, emergencyProtocols, protocolsDisclaimer } from "../../lib/emergencyProtocols";

export function ProtocolosPage() {
  const [openId, setOpenId] = useState<string | null>(null);

  return (
    <div>
      <h1 className="font-serif text-2xl mb-1">Protocolos de emergencia</h1>
      <p className="text-sm text-warn mb-6 max-w-xl">{protocolsDisclaimer}</p>

      <h2 className="font-serif text-lg mb-3">Teléfonos de emergencia</h2>
      <ul className="flex flex-wrap gap-3 mb-8">
        {emergencyContacts.map((c) => (
          <li key={c.name} className="bg-white border border-border-soft rounded-lg px-4 py-2 text-sm">
            <span className="font-semibold">{c.name}</span> — {c.phone}
          </li>
        ))}
      </ul>

      <h2 className="font-serif text-lg mb-3">Guías</h2>
      <ul className="space-y-3 max-w-xl">
        {emergencyProtocols.map((p) => {
          const open = openId === p.id;
          return (
            <li key={p.id} className="bg-white border border-border-soft rounded-lg p-5">
              <button onClick={() => setOpenId(open ? null : p.id)} className="w-full text-left font-serif text-lg">
                {p.icon} {p.title}
              </button>
              {open && (
                <ol className="list-decimal list-inside text-sm mt-3 space-y-1.5">
                  {p.steps.map((step, i) => (
                    <li key={i}>{step}</li>
                  ))}
                </ol>
              )}
            </li>
          );
        })}
      </ul>
    </div>
  );
}
