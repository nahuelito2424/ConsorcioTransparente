import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { LoginPage } from "./auth/LoginPage";
import { RequireAdmin } from "./auth/RequireAdmin";
import { UnidadesPage } from "./pages/UnidadesPage";
import { GastosPage } from "./pages/GastosPage";
import { LiquidacionPage } from "./pages/LiquidacionPage";

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route
          path="/"
          element={
            <RequireAdmin>
              <div>Inicio (Tarea 8)</div>
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
