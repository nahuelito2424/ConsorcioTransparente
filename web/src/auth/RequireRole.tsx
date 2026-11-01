import { Suspense, type ReactNode } from "react";
import { Navigate } from "react-router-dom";
import { useAuth } from "./useAuth";
import { homeForRole } from "./homeForRole";
import { Layout } from "../components/Layout";
import type { Role } from "../types";

/**
 * Deja pasar sólo a los roles en `roles` (los mismos tres de la app Android). Un
 * usuario logueado con un rol fuera de la lista no se desloguea (su sesión es válida,
 * useAuth.ts ya lo verificó) — vuelve al home, que lo manda a la pantalla que sí le
 * corresponde según App.tsx.
 */
export function RequireRole({ roles, children }: { roles: Role[]; children: ReactNode }) {
  const { user, loading } = useAuth();

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-cream text-pending">
        Cargando…
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  if (!roles.includes(user.role)) {
    return <Navigate to={homeForRole(user.role)} replace />;
  }

  // Todas las pantallas de acá en más asumen un buildingId válido (consultan
  // buildings/{buildingId}/...) — un usuario recién registrado en Android que
  // todavía no fue asignado a un edificio se quedaba viendo "Cargando…" para
  // siempre en vez de un mensaje, porque cada hook corta en seco con
  // `if (!buildingId) return;`. Mismo mensaje que PagarViewModel.kt (Android).
  if (!user.buildingId) {
    return (
      <Layout user={user}>
        <p className="text-sm text-pending">Tu usuario todavía no está asignado a un edificio.</p>
      </Layout>
    );
  }

  return (
    <Layout user={user}>
      <Suspense fallback={<p className="text-sm text-pending">Cargando…</p>}>{children}</Suspense>
    </Layout>
  );
}
