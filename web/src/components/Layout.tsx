import { useState, type ReactNode } from "react";
import { NavLink } from "react-router-dom";
import { signOut } from "firebase/auth";
import { auth } from "../firebase";
import type { UserProfile } from "../types";

const links = [
  { to: "/", label: "Inicio" },
  { to: "/unidades", label: "Unidades" },
  { to: "/gastos", label: "Gastos" },
  { to: "/liquidacion", label: "Liquidación" },
];

const focusRing =
  "focus-visible:outline focus-visible:outline-2 focus-visible:outline-terracota focus-visible:outline-offset-2";

export function Layout({ user, children }: { user: UserProfile; children: ReactNode }) {
  // La barra lateral fija no entra en una pantalla angosta (celular/tablet) —
  // en md+ se ve siempre; debajo de eso se convierte en un cajón que se abre
  // con el botón de la barra superior, mismo contenido, sin tocar las rutas.
  const [navOpen, setNavOpen] = useState(false);

  return (
    <div className="min-h-screen flex bg-cream">
      <header className="md:hidden fixed top-0 inset-x-0 h-14 bg-cream border-b border-dashed border-border-dashed flex items-center justify-between px-4 z-30">
        <span className="font-serif text-lg">Consorcio Transparente</span>
        <button
          onClick={() => setNavOpen(true)}
          aria-label="Abrir menú"
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
        className={`w-56 shrink-0 border-r border-dashed border-border-dashed flex flex-col p-6 bg-cream
          fixed md:static inset-y-0 left-0 z-50 transition-transform duration-200 ease-out
          ${navOpen ? "translate-x-0" : "-translate-x-full"} md:translate-x-0`}
      >
        <h2 className="font-serif text-lg mb-1">Consorcio Transparente</h2>
        <p className="text-xs text-pending mb-6">{user.fullName}</p>

        <nav className="flex flex-col gap-1 flex-1">
          {links.map((link) => (
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
