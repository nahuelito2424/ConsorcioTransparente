import { useState, type FormEvent } from "react";
import { Navigate } from "react-router-dom";
import { signInWithEmailAndPassword } from "firebase/auth";
import { auth } from "../firebase";
import { useAuth } from "./useAuth";

export function LoginPage() {
  const { user, loading, error: authError } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loginError, setLoginError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setLoginError(null);
    try {
      await signInWithEmailAndPassword(auth, email, password);
    } catch {
      setLoginError("Correo o contraseña incorrectos.");
    } finally {
      setSubmitting(false);
    }
  }

  // Ya logueado como Administrador (p.ej. volvió a /login con la sesión todavía
  // activa) — no tiene sentido mostrarle el formulario de nuevo.
  if (!loading && user) {
    return <Navigate to="/" replace />;
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-cream">
      <form
        onSubmit={handleSubmit}
        className="w-full max-w-sm bg-white border border-border-soft rounded-lg p-8"
      >
        <h1 className="font-serif text-2xl text-center mb-1">Consorcio Transparente</h1>
        <p className="text-center text-sm text-pending mb-6">Acceso para Administradores</p>

        <label htmlFor="login-email" className="block text-xs uppercase tracking-wide text-pending mb-1">
          Correo electrónico
        </label>
        <input
          id="login-email"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          className="w-full border border-border-soft rounded px-3 py-2 mb-4"
          required
        />

        <label htmlFor="login-password" className="block text-xs uppercase tracking-wide text-pending mb-1">
          Contraseña
        </label>
        <input
          id="login-password"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          className="w-full border border-border-soft rounded px-3 py-2 mb-4"
          required
        />

        {(loginError || authError) && (
          <p className="text-warn text-sm mb-4">{loginError ?? authError}</p>
        )}

        <button
          type="submit"
          disabled={submitting}
          className="w-full bg-terracota text-white rounded py-2 font-semibold disabled:opacity-50"
        >
          Ingresar
        </button>
      </form>
    </div>
  );
}
