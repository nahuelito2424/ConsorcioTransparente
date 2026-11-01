import { lazy } from "react";
import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { LoginPage } from "./auth/LoginPage";
import { RequireRole } from "./auth/RequireRole";
import { useAuth } from "./auth/useAuth";

// Una por ruta autenticada: el código de cada pantalla sólo se descarga la primera
// vez que se navega a ella, no en la carga inicial (que hoy es sólo /login).
const InicioPage = lazy(() => import("./pages/InicioPage").then((m) => ({ default: m.InicioPage })));
const UnidadesPage = lazy(() => import("./pages/UnidadesPage").then((m) => ({ default: m.UnidadesPage })));
const GastosPage = lazy(() => import("./pages/GastosPage").then((m) => ({ default: m.GastosPage })));
const LiquidacionPage = lazy(() =>
  import("./pages/LiquidacionPage").then((m) => ({ default: m.LiquidacionPage }))
);
const FacturasPage = lazy(() =>
  import("./pages/inquilino/FacturasPage").then((m) => ({ default: m.FacturasPage }))
);

const adminRoles = ["ADMINISTRADOR", "ENCARGADO"] as const;

// "/" no tiene una sola pantalla: para Administrador/Encargado es el dashboard de
// liquidación, para Inquilino es directamente Mis facturas (no hay todavía un hub
// propio del Inquilino — ver FacturasPage). RequireRole ya garantiza que acá adentro
// user no es null.
function Home() {
  const { user } = useAuth();
  if (user?.role === "INQUILINO") return <Navigate to="/facturas" replace />;
  return <InicioPage />;
}

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route
          path="/"
          element={
            <RequireRole roles={[...adminRoles, "INQUILINO"]}>
              <Home />
            </RequireRole>
          }
        />
        <Route
          path="/unidades"
          element={
            <RequireRole roles={[...adminRoles]}>
              <UnidadesPage />
            </RequireRole>
          }
        />
        <Route
          path="/gastos"
          element={
            <RequireRole roles={[...adminRoles]}>
              <GastosPage />
            </RequireRole>
          }
        />
        <Route
          path="/liquidacion"
          element={
            <RequireRole roles={[...adminRoles]}>
              <LiquidacionPage />
            </RequireRole>
          }
        />
        <Route
          path="/facturas"
          element={
            <RequireRole roles={["INQUILINO"]}>
              <FacturasPage />
            </RequireRole>
          }
        />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
