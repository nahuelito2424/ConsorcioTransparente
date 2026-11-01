import { useEffect, useState } from "react";
import { collection, doc, onSnapshot, setDoc } from "firebase/firestore";
import { db } from "../firebase";
import type { Provider, ProviderReview } from "../types";

/** providers — directorio global (no por edificio), igual que FirebaseProviderRepository.kt. */
export function useProviders() {
  const [providers, setProviders] = useState<Provider[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const unsubscribe = onSnapshot(
      collection(db, "providers"),
      (snap) => {
        setProviders(snap.docs.map((d) => ({ ...(d.data() as Omit<Provider, "id">), id: d.id })));
        setError(null);
        setLoading(false);
      },
      () => {
        setError("No se pudo cargar el directorio de proveedores. Recargá la página.");
        setLoading(false);
      }
    );
    return unsubscribe;
  }, []);

  return { providers, loading, error };
}

/** providers/{providerId}/reviews — el promedio/conteo del proveedor lo recalcula una
 * Cloud Function al crear la reseña, no el cliente. */
export function useProviderReviews(providerId: string | null) {
  const [reviews, setReviews] = useState<ProviderReview[]>([]);

  useEffect(() => {
    if (!providerId) {
      setReviews([]);
      return;
    }
    const unsubscribe = onSnapshot(collection(db, "providers", providerId, "reviews"), (snap) => {
      setReviews(snap.docs.map((d) => ({ ...(d.data() as Omit<ProviderReview, "id">), id: d.id })));
    });
    return unsubscribe;
  }, [providerId]);

  return reviews;
}

export async function addReview(providerId: string, authorUid: string, comment: string, stars: number) {
  const ref = doc(collection(db, "providers", providerId, "reviews"));
  await setDoc(ref, { authorUid, comment, stars, expenseId: "" });
}
