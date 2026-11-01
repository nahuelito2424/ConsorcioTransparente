import { useEffect, useState } from "react";
import { collection, doc, onSnapshot, orderBy, query, serverTimestamp, setDoc, where } from "firebase/firestore";
import { getDownloadURL, ref, uploadBytes } from "firebase/storage";
import { db, storage } from "../firebase";
import type { Post } from "../types";

/**
 * buildings/{buildingId}/posts — equivalente web de FirebaseMuroRepository.kt
 * (Android, HU-05): sólo trae los APROBADO (toda publicación nace PENDIENTE y recién
 * se ve en el muro cuando el Administrador la aprueba — la moderación en sí no tiene
 * pantalla en la web todavía, sólo en Android). Usa el mismo índice compuesto
 * (status asc, createdAt desc) ya agregado a firestore.indexes.json.
 */
export function usePosts(buildingId: string) {
  const [posts, setPosts] = useState<Post[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!buildingId) return;
    const q = query(
      collection(db, "buildings", buildingId, "posts"),
      where("status", "==", "APROBADO"),
      orderBy("createdAt", "desc")
    );
    const unsubscribe = onSnapshot(
      q,
      (snap) => {
        setPosts(
          snap.docs.map((d) => {
            const data = d.data();
            return {
              ...(data as Omit<Post, "id" | "createdAt">),
              id: d.id,
              createdAt: data.createdAt?.toDate() ?? null,
            };
          })
        );
        setError(null);
        setLoading(false);
      },
      () => {
        setError("No se pudieron cargar los avisos. Recargá la página.");
        setLoading(false);
      }
    );
    return unsubscribe;
  }, [buildingId]);

  // Mismo path de Storage que FirebaseMuroRepository.kt: posts/{buildingId}/{uuid}.jpg.
  async function createPost(title: string, body: string, authorUid: string, authorName: string, file: File | null) {
    let imageUrl = "";
    if (file) {
      const fileName = `posts/${buildingId}/${crypto.randomUUID()}.jpg`;
      const storageRef = ref(storage, fileName);
      await uploadBytes(storageRef, file);
      imageUrl = await getDownloadURL(storageRef);
    }

    const postRef = doc(collection(db, "buildings", buildingId, "posts"));
    await setDoc(postRef, {
      title,
      body,
      imageUrl,
      status: "PENDIENTE",
      authorUid,
      authorName,
      createdAt: serverTimestamp(),
    });
  }

  return { posts, loading, error, createPost };
}
