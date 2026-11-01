import { useEffect, useState } from "react";
import { onAuthStateChanged, signOut } from "firebase/auth";
import { doc, getDoc } from "firebase/firestore";
import { auth, db } from "../firebase";
import type { UserProfile } from "../types";

interface UseAuthResult {
  user: UserProfile | null;
  loading: boolean;
  error: string | null;
}

/**
 * Expone el perfil del usuario autenticado (o null), para cualquiera de los tres roles
 * (ADMINISTRADOR, ENCARGADO, INQUILINO — los mismos que la app Android). Si el perfil no
 * existe o no tiene un rol válido, desloguea de inmediato: no hay un cuarto estado a
 * medio loguear. Qué pantallas ve cada rol lo decide el ruteo (ver App.tsx), no este hook.
 */
export function useAuth(): UseAuthResult {
  const [user, setUser] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const unsubscribe = onAuthStateChanged(auth, async (firebaseUser) => {
      if (!firebaseUser) {
        setUser(null);
        setLoading(false);
        return;
      }

      try {
        const snap = await getDoc(doc(db, "users", firebaseUser.uid));
        const profile = snap.data() as UserProfile | undefined;
        const validRole = profile?.role === "ADMINISTRADOR" || profile?.role === "ENCARGADO" || profile?.role === "INQUILINO";

        if (!profile || !validRole) {
          setError("No se pudo verificar tu perfil. Probá de nuevo.");
          await signOut(auth);
          setUser(null);
          setLoading(false);
          return;
        }

        setError(null);
        setUser({ ...profile, uid: firebaseUser.uid });
        setLoading(false);
      } catch {
        // Si falla la lectura del perfil (sin red, permisos, etc.) no hay que dejar
        // "Cargando…" para siempre con una sesión de Auth a medias — cerrar sesión y
        // avisar, igual que el caso de rol inválido.
        setError("No se pudo verificar tu perfil. Probá de nuevo.");
        await signOut(auth);
        setUser(null);
        setLoading(false);
      }
    });

    return unsubscribe;
  }, []);

  return { user, loading, error };
}
