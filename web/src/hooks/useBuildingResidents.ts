import { useEffect, useState } from "react";
import { collection, onSnapshot, query, where } from "firebase/firestore";
import { db } from "../firebase";
import type { UserProfile } from "../types";

/** Inquilinos del edificio — para el selector de "qué unidad es de quién". */
export function useBuildingResidents(buildingId: string): UserProfile[] {
  const [residents, setResidents] = useState<UserProfile[]>([]);

  useEffect(() => {
    if (!buildingId) return;
    const q = query(
      collection(db, "users"),
      where("buildingId", "==", buildingId),
      where("role", "==", "INQUILINO")
    );
    const unsubscribe = onSnapshot(q, (snap) => {
      setResidents(snap.docs.map((d) => ({ ...(d.data() as UserProfile), uid: d.id })));
    });
    return unsubscribe;
  }, [buildingId]);

  return residents;
}
