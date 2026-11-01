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

  return (
    <Layout user={user}>
      <Suspense fallback={<p className="text-sm text-pending">Cargando…</p>}>{children}</Suspense>
    </Layout>
  );
}
