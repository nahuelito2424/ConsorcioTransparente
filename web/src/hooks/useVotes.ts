import { useEffect, useState } from "react";
import { collection, doc, getDoc, getDocs, onSnapshot, orderBy, query, setDoc } from "firebase/firestore";
import { db } from "../firebase";
import type { VoteResponse, VoteTopic } from "../types";

/** buildings/{buildingId}/votes — equivalente web de FirebaseVoteRepository.kt (HU-02). */
export function useVotes(buildingId: string) {
  const [votes, setVotes] = useState<VoteTopic[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!buildingId) return;
    const q = query(collection(db, "buildings", buildingId, "votes"), orderBy("createdAt", "desc"));
    const unsubscribe = onSnapshot(
      q,
      (snap) => {
        setVotes(
          snap.docs.map((d) => {
            const data = d.data();
            return {
              ...(data as Omit<VoteTopic, "id" | "closesAt" | "createdAt">),
              id: d.id,
              closesAt: data.closesAt?.toDate() ?? null,
              createdAt: data.createdAt?.toDate() ?? null,
            };
          })
        );
        setError(null);
        setLoading(false);
      },
      () => {
        setError("No se pudieron cargar las votaciones. Recargá la página.");
        setLoading(false);
      }
    );
    return unsubscribe;
  }, [buildingId]);

  return { votes, loading, error };
}

export function isVoteClosed(vote: VoteTopic): boolean {
  return vote.isClosed || (vote.closesAt !== null && vote.closesAt < new Date());
}

/** La propia respuesta a una votación puntual — doc id = uid, igual que Android. */
export async function getMyResponse(buildingId: string, voteId: string, uid: string): Promise<VoteResponse | null> {
  const snap = await getDoc(doc(db, "buildings", buildingId, "votes", voteId, "responses", uid));
  return snap.exists() ? (snap.data() as VoteResponse) : null;
}

/** Conteo por opción — sólo se pide una vez (ya votaste, o la votación cerró), no en vivo. */
export async function getResultCounts(buildingId: string, voteId: string, optionCount: number): Promise<number[]> {
  const counts = new Array(optionCount).fill(0);
  const snap = await getDocs(collection(db, "buildings", buildingId, "votes", voteId, "responses"));
  for (const d of snap.docs) {
    const idx = (d.data() as VoteResponse).optionIndex;
    if (idx >= 0 && idx < optionCount) counts[idx]++;
  }
  return counts;
}

export async function submitVote(buildingId: string, voteId: string, uid: string, optionIndex: number) {
  await setDoc(doc(db, "buildings", buildingId, "votes", voteId, "responses", uid), { uid, optionIndex });
}
