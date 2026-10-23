import type { ReactNode } from "react";
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

export function Layout({ user, children }: { user: UserProfile; children: ReactNode }) {
  return (
    <div className="min-h-screen flex bg-cream">
      <aside className="w-56 shrink-0 border-r border-dashed border-border-dashed flex flex-col p-6">
        <h2 className="font-serif text-lg mb-1">Consorcio Transparente</h2>
        <p className="text-xs text-pending mb-6">{user.fullName}</p>

        <nav className="flex flex-col gap-1 flex-1">
          {links.map((link) => (
            <NavLink
              key={link.to}
              to={link.to}
              end={link.to === "/"}
              className={({ isActive }) =>
                `px-3 py-2 rounded text-sm ${
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
          className="text-sm border border-border-soft rounded py-2 mt-6"
        >
          Cerrar sesión
        </button>
      </aside>
      <main className="flex-1 p-8">{children}</main>
    </div>
  );
}
