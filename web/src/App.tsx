import { lazy } from "react";
import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { LoginPage } from "./auth/LoginPage";
import { RequireAdmin } from "./auth/RequireAdmin";

// Una por ruta autenticada: el código de cada pantalla sólo se descarga la primera
// vez que se navega a ella, no en la carga inicial (que hoy es sólo /login).
const InicioPage = lazy(() => import("./pages/InicioPage").then((m) => ({ default: m.InicioPage })));
const UnidadesPage = lazy(() => import("./pages/UnidadesPage").then((m) => ({ default: m.UnidadesPage })));
const GastosPage = lazy(() => import("./pages/GastosPage").then((m) => ({ default: m.GastosPage })));
const LiquidacionPage = lazy(() =>
  import("./pages/LiquidacionPage").then((m) => ({ default: m.LiquidacionPage }))
);

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route
          path="/"
          element={
            <RequireAdmin>
              <InicioPage />
            </RequireAdmin>
          }
        />
        <Route
          path="/unidades"
          element={
            <RequireAdmin>
              <UnidadesPage />
            </RequireAdmin>
          }
        />
        <Route
          path="/gastos"
          element={
            <RequireAdmin>
              <GastosPage />
            </RequireAdmin>
          }
        />
        <Route
          path="/liquidacion"
          element={
            <RequireAdmin>
              <LiquidacionPage />
            </RequireAdmin>
          }
        />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
