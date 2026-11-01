// Siembra datos de prueba en el Firebase Local Emulator Suite (Auth + Firestore) para
// desarrollar y probar la web sin un proyecto de Firebase real. Ver web/README.md.
//
// Uso: firebase emulators:exec --only auth,firestore "cd web && npm run seed"

const PROJECT_ID = "demo-consorcio-transparente";

process.env.FIRESTORE_EMULATOR_HOST = "127.0.0.1:8080";
process.env.FIREBASE_AUTH_EMULATOR_HOST = "127.0.0.1:9099";
process.env.GCLOUD_PROJECT = PROJECT_ID;

import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { FieldValue, getFirestore, Timestamp } from "firebase-admin/firestore";

initializeApp({ projectId: PROJECT_ID });

const auth = getAuth();
const db = getFirestore();

async function createAuthUser(email: string, password: string): Promise<string> {
  const existing = await auth.getUserByEmail(email).catch(() => null);
  if (existing) return existing.uid;
  const user = await auth.createUser({ email, password });
  return user.uid;
}

async function seed() {
  const buildingId = "building-demo";

  const adminUid = await createAuthUser("admin@test.com", "test1234");
  const encargadoUid = await createAuthUser("encargado@test.com", "test1234");
  const tenant1Uid = await createAuthUser("inquilino1@test.com", "test1234");
  const tenant2Uid = await createAuthUser("inquilino2@test.com", "test1234");
  const tenant3Uid = await createAuthUser("inquilino3@test.com", "test1234");

  await db.doc(`users/${adminUid}`).set({
    uid: adminUid,
    fullName: "Marta Gómez (Admin)",
    email: "admin@test.com",
    role: "ADMINISTRADOR",
    buildingId,
    unitId: "",
    phone: "",
    rentAmount: 0,
    expensesAmount: 0,
  });

  await db.doc(`users/${encargadoUid}`).set({
    uid: encargadoUid,
    fullName: "Carlos Encargado",
    email: "encargado@test.com",
    role: "ENCARGADO",
    buildingId,
    unitId: "",
    phone: "",
    rentAmount: 0,
    expensesAmount: 0,
  });

  // inquilino1 con expensesAmount > 0 (y un pago ya PAGADO el mes pasado) para poder
  // probar Mis facturas/Pagar de punta a punta sin tener que cargar una liquidación
  // primero; los otros dos quedan en 0, como antes.
  const tenants = [
    { uid: tenant1Uid, fullName: "Inés Robledo (4B)", email: "inquilino1@test.com", expensesAmount: 25000 },
    { uid: tenant2Uid, fullName: "Juan Pereyra (7C)", email: "inquilino2@test.com", expensesAmount: 0 },
    { uid: tenant3Uid, fullName: "Lucía Fariña (sin unidad)", email: "inquilino3@test.com", expensesAmount: 0 },
  ];
  for (const t of tenants) {
    await db.doc(`users/${t.uid}`).set({
      uid: t.uid,
      fullName: t.fullName,
      email: t.email,
      role: "INQUILINO",
      buildingId,
      unitId: "",
      phone: "",
      rentAmount: 0,
      expensesAmount: t.expensesAmount,
    });
  }

  await db.doc(`buildings/${buildingId}`).set({
    id: buildingId,
    name: "Edificio de prueba (seed)",
    address: "Av. Libertador 1420",
    chargesRent: false,
    chargesExpenses: true,
    expensesDueDay: 10,
    rentDueDay: 10,
    reminderDaysBefore: 5,
    hasEncargado: true,
    amenities: [],
    adminUid,
    cbu: "",
    alias: "",
  });

  // Alícuotas suman 80% a propósito (50 + 30) — para ver el aviso de "no suma 100%"
  // en la pantalla de Unidades sin tener que cargar una tercera a mano primero.
  await db.collection(`buildings/${buildingId}/units`).doc("unit-4b").set({
    id: "unit-4b",
    label: "4B",
    aliquot: 50,
    tenantUid: tenant1Uid,
    ownerUid: "",
  });
  await db.collection(`buildings/${buildingId}/units`).doc("unit-7c").set({
    id: "unit-7c",
    label: "7C",
    aliquot: 30,
    tenantUid: "",
    ownerUid: "",
  });

  const now = new Date();
  const lastMonth = new Date(now.getFullYear(), now.getMonth() - 1, 1);
  const lastPeriod = `${lastMonth.getFullYear()}-${String(lastMonth.getMonth() + 1).padStart(2, "0")}`;
  await db.doc(`buildings/${buildingId}/payments/${tenant1Uid}_${lastPeriod}`).set({
    uid: tenant1Uid,
    period: lastPeriod,
    breakdown: { expensas: 25000 },
    amount: 25000,
    method: "TRANSFERENCIA",
    status: "PAGADO",
    receiptUrl: "https://example.com/comprobante-fake.jpg",
    mercadoPagoPaymentId: "",
    rejectionReason: "",
    createdAt: Timestamp.fromDate(lastMonth),
    verifiedAt: Timestamp.fromDate(lastMonth),
    verifiedByUid: adminUid,
  });

  // Ids explícitos (no .add()) para que correr la semilla dos veces no duplique gastos.
  await db.doc(`buildings/${buildingId}/expenses/expense-sueldo`).set({
    description: "Sueldo encargado - octubre",
    category: "OTRO",
    providerName: "",
    amount: 50000,
    receiptUrl: "https://example.com/comprobante-fake.jpg",
    status: "VERIFICADO",
    createdByUid: adminUid,
    createdAt: FieldValue.serverTimestamp(),
  });
  await db.doc(`buildings/${buildingId}/expenses/expense-porton`).set({
    description: "Reparación portón (sin comprobante todavía)",
    category: "MANO_DE_OBRA",
    providerName: "",
    amount: 12000,
    receiptUrl: "",
    status: "BLOQUEADO",
    createdByUid: adminUid,
    createdAt: FieldValue.serverTimestamp(),
  });

  console.log("Semilla cargada:");
  console.log(`  Edificio: ${buildingId}`);
  console.log(`  Admin: admin@test.com / test1234`);
  console.log(`  Encargado: encargado@test.com / test1234 (misma paridad que el Admin en la web)`);
  console.log(`  Inquilinos: inquilino1@test.com (expensas $25.000, con pago PAGADO el mes pasado), inquilino2@test.com, inquilino3@test.com / test1234`);
  console.log(`  Unidades: 4B (alícuota 50%, con inquilino), 7C (alícuota 30%, sin inquilino)`);
  console.log(`  Gastos: 1 VERIFICADO ($50.000), 1 BLOQUEADO ($12.000)`);
}

seed()
  .then(() => process.exit(0))
  .catch((err) => {
    console.error("Error sembrando datos:", err);
    process.exit(1);
  });
