import { useEffect, useState, type ReactNode } from "react";
import { NavLink } from "react-router-dom";
import { signOut } from "firebase/auth";
import { auth } from "../firebase";
import type { Role, UserProfile } from "../types";

// Administrador y Encargado comparten el mismo panel (paridad de permisos — ver
// firestore.rules); Inquilino tiene su propio set de pantallas, que va a ir creciendo
// a medida que la web alcance paridad de funciones con la app Android.
const linksByRole: Record<Role, { to: string; label: string }[]> = {
  ADMINISTRADOR: [
    { to: "/", label: "Inicio" },
    { to: "/unidades", label: "Unidades" },
    { to: "/gastos", label: "Gastos" },
    { to: "/liquidacion", label: "Liquidación" },
    { to: "/rendicion", label: "Rendición de cuentas" },
  ],
  ENCARGADO: [
    { to: "/", label: "Inicio" },
    { to: "/unidades", label: "Unidades" },
    { to: "/gastos", label: "Gastos" },
    { to: "/liquidacion", label: "Liquidación" },
    { to: "/rendicion", label: "Rendición de cuentas" },
  ],
  INQUILINO: [
    { to: "/facturas", label: "Mis facturas" },
    { to: "/votar", label: "Votar" },
    { to: "/muro", label: "Muro" },
    { to: "/tickets", label: "Reclamar un problema" },
    { to: "/reservas", label: "Reservar espacios" },
    { to: "/proveedores", label: "Proveedores" },
    { to: "/protocolos", label: "Protocolos de emergencia" },
    { to: "/rendicion", label: "Rendición de cuentas" },
  ],
};

const focusRing =
  "focus-visible:outline focus-visible:outline-2 focus-visible:outline-terracota focus-visible:outline-offset-2";

export function Layout({ user, children }: { user: UserProfile; children: ReactNode }) {
  // La barra lateral fija no entra en una pantalla angosta (celular/tablet) —
  // en md+ se ve siempre; debajo de eso se convierte en un cajón que se abre
  // con el botón de la barra superior, mismo contenido, sin tocar las rutas.
  const [navOpen, setNavOpen] = useState(false);

  // Cerrado en mobile, el cajón quedaba desplazado fuera de pantalla con
  // translate-x pero sus links seguían en el DOM, tabulables e invisibles
  // (WCAG 2.4.3/2.4.7) — Escape también lo cierra, como cualquier menú/diálogo.
  useEffect(() => {
    if (!navOpen) return;
    function handleEscape(e: KeyboardEvent) {
      if (e.key === "Escape") setNavOpen(false);
    }
    document.addEventListener("keydown", handleEscape);
    return () => document.removeEventListener("keydown", handleEscape);
  }, [navOpen]);

  return (
    <div className="min-h-screen flex bg-cream">
      <header className="print:hidden md:hidden fixed top-0 inset-x-0 h-14 bg-cream border-b border-dashed border-border-dashed flex items-center justify-between px-4 z-30">
        <span className="font-serif text-lg">Consorcio Transparente</span>
        <button
          onClick={() => setNavOpen(true)}
          aria-label="Abrir menú"
          aria-expanded={navOpen}
          aria-controls="mobile-nav"
          className={`p-2 -mr-2 rounded hover:bg-white transition-colors ${focusRing}`}
        >
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
            <path d="M4 7h16M4 12h16M4 17h16" />
          </svg>
        </button>
      </header>

      {navOpen && (
        <div
          className="md:hidden fixed inset-0 bg-stone-900/30 z-40"
          onClick={() => setNavOpen(false)}
          aria-hidden="true"
        />
      )}

      <aside
        id="mobile-nav"
        className={`print:hidden w-56 shrink-0 border-r border-dashed border-border-dashed flex flex-col p-6 bg-cream
          fixed md:static inset-y-0 left-0 z-50 transition-[transform,visibility] duration-200 ease-out
          ${navOpen ? "translate-x-0 visible" : "-translate-x-full invisible"} md:translate-x-0 md:visible`}
      >
        <h2 className="font-serif text-lg mb-1">Consorcio Transparente</h2>
        <p className="text-xs text-pending mb-6">{user.fullName}</p>

        <nav className="flex flex-col gap-1 flex-1">
          {linksByRole[user.role].map((link) => (
            <NavLink
              key={link.to}
              to={link.to}
              end={link.to === "/"}
              onClick={() => setNavOpen(false)}
              className={({ isActive }) =>
                `px-3 py-2 rounded text-sm transition-colors ${focusRing} ${
                  isActive ? "bg-terracota text-white" : "text-stone-700 hover:bg-white"
                }`
              }
            >
              {link.label}
            </NavLink>
          ))}
        </nav>

        <button
          onClick={() => signOut(auth)}
          className={`text-sm border border-border-soft rounded py-2 mt-6 hover:bg-white transition-colors ${focusRing}`}
        >
          Cerrar sesión
        </button>
      </aside>
      <main className="flex-1 p-8 pt-20 md:pt-8 min-w-0">{children}</main>
    </div>
  );
}
