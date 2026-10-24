import { useEffect, useState } from "react";
import { collection, doc, onSnapshot, orderBy, query, serverTimestamp, setDoc } from "firebase/firestore";
import { getDownloadURL, ref, uploadBytes } from "firebase/storage";
import { db, storage } from "../firebase";
import type { Expense, ExpenseCategory } from "../types";

interface CreateExpenseParams {
  description: string;
  category: ExpenseCategory;
  providerName: string;
  amount: number;
  createdByUid: string;
  file: File | null;
}

/**
 * buildings/{buildingId}/expenses — mismo modelo que HU-01/Facturas (Android).
 * Consumida también por Liquidación (suma de VERIFICADO del período) e Inicio (KPI).
 */
export function useExpenses(buildingId: string) {
  const [expenses, setExpenses] = useState<Expense[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!buildingId) return;
    const q = query(collection(db, "buildings", buildingId, "expenses"), orderBy("createdAt", "desc"));
    const unsubscribe = onSnapshot(
      q,
      (snap) => {
        setExpenses(
          snap.docs.map((d) => {
            const data = d.data();
            return {
              ...(data as Omit<Expense, "id" | "createdAt">),
              id: d.id,
              createdAt: data.createdAt?.toDate() ?? null,
            };
          })
        );
        setError(null);
        setLoading(false);
      },
      () => {
        setError("No se pudieron cargar los gastos. Recargá la página.");
        setLoading(false);
      }
    );
    return unsubscribe;
  }, [buildingId]);

  async function createExpense(params: CreateExpenseParams) {
    let receiptUrl = "";
    if (params.file) {
      // Mismo path que FirebaseExpenseRepository.kt (Android): receipts/{buildingId}/{uuid}.jpg
      const fileName = `receipts/${buildingId}/${crypto.randomUUID()}.jpg`;
      const storageRef = ref(storage, fileName);
      await uploadBytes(storageRef, params.file);
      receiptUrl = await getDownloadURL(storageRef);
    }

    const expenseRef = doc(collection(db, "buildings", buildingId, "expenses"));
    await setDoc(expenseRef, {
      description: params.description,
      category: params.category,
      providerName: params.providerName,
      amount: params.amount,
      receiptUrl,
      status: receiptUrl ? "VERIFICADO" : "BLOQUEADO",
      createdByUid: params.createdByUid,
      createdAt: serverTimestamp(),
    });
  }

  return { expenses, loading, error, createExpense };
}
