import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { LoginPage } from "./auth/LoginPage";
import { RequireAdmin } from "./auth/RequireAdmin";

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
              <div>Unidades (Tarea 5)</div>
            </RequireAdmin>
          }
        />
        <Route
          path="/gastos"
          element={
            <RequireAdmin>
              <div>Gastos (Tarea 6)</div>
            </RequireAdmin>
          }
        />
        <Route
          path="/liquidacion"
          element={
            <RequireAdmin>
              <div>Liquidación (Tarea 7)</div>
            </RequireAdmin>
          }
        />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
