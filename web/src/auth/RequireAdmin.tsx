import { Suspense, type ReactNode } from "react";
import { Navigate } from "react-router-dom";
import { useAuth } from "./useAuth";
import { Layout } from "../components/Layout";

export function RequireAdmin({ children }: { children: ReactNode }) {
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

  return (
    <Layout user={user}>
      <Suspense fallback={<p className="text-sm text-pending">Cargando…</p>}>{children}</Suspense>
    </Layout>
  );
}
