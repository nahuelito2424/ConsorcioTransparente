import { readFileSync } from "node:fs";
import { afterAll, beforeAll, beforeEach, describe, test } from "vitest";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  type RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc, updateDoc } from "firebase/firestore";

// Verifica las reglas nuevas de buildings/{id}/units y buildings/{id}/liquidaciones
// (ver docs/superpowers/specs/2026-10-23-web-admin-liquidacion-design.md) contra el
// Firebase Local Emulator Suite. Requiere el emulador de Firestore corriendo — ver
// web/README.md ("firebase emulators:exec --only firestore \"cd web && npm test\"").

let testEnv: RulesTestEnvironment;

beforeAll(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: "demo-consorcio-transparente",
    firestore: {
      rules: readFileSync("../firestore.rules", "utf8"),
      host: "127.0.0.1",
      port: 8080,
    },
  });
});

afterAll(async () => {
  await testEnv.cleanup();
});

beforeEach(async () => {
  await testEnv.clearFirestore();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, "users/admin-a"), { role: "ADMINISTRADOR", buildingId: "building-a" });
    await setDoc(doc(db, "users/admin-b"), { role: "ADMINISTRADOR", buildingId: "building-b" });
    await setDoc(doc(db, "users/encargado-a"), { role: "ENCARGADO", buildingId: "building-a" });
    await setDoc(doc(db, "users/inquilino-a"), {
      role: "INQUILINO",
      buildingId: "building-a",
      expensesAmount: 10000,
    });
  });
});

describe("buildings/{buildingId}/units", () => {
  test("un Administrador de otro edificio no puede leer units de este edificio", async () => {
    const adminB = testEnv.authenticatedContext("admin-b");
    await assertFails(getDoc(doc(adminB.firestore(), "buildings/building-a/units/u1")));
  });

  test("el Administrador del edificio puede crear una unidad", async () => {
    const adminA = testEnv.authenticatedContext("admin-a");
    await assertSucceeds(
      setDoc(doc(adminA.firestore(), "buildings/building-a/units/u1"), {
        label: "4B",
        aliquot: 50,
        tenantUid: "",
        ownerUid: "",
      })
    );
  });

  test("un Encargado no puede escribir units", async () => {
    const encargadoA = testEnv.authenticatedContext("encargado-a");
    await assertFails(
      setDoc(doc(encargadoA.firestore(), "buildings/building-a/units/u1"), {
        label: "4B",
        aliquot: 50,
        tenantUid: "",
        ownerUid: "",
      })
    );
  });

  test("una alícuota fuera de 0-100 se rechaza", async () => {
    const adminA = testEnv.authenticatedContext("admin-a");
    await assertFails(
      setDoc(doc(adminA.firestore(), "buildings/building-a/units/u2"), {
        label: "4B",
        aliquot: 150,
        tenantUid: "",
        ownerUid: "",
      })
    );
    await assertFails(
      setDoc(doc(adminA.firestore(), "buildings/building-a/units/u3"), {
        label: "4B",
        aliquot: -10,
        tenantUid: "",
        ownerUid: "",
      })
    );
  });
});

describe("buildings/{buildingId}/liquidaciones", () => {
  test("el Administrador del edificio puede emitir una liquidación propia", async () => {
    const adminA = testEnv.authenticatedContext("admin-a");
    await assertSucceeds(
      setDoc(doc(adminA.firestore(), "buildings/building-a/liquidaciones/2026-10"), {
        period: "2026-10",
        totalExpenses: 0,
        breakdown: [],
        unassignedUnits: [],
        emittedByUid: "admin-a",
      })
    );
  });

  test("un Encargado no puede escribir liquidaciones", async () => {
    const encargadoA = testEnv.authenticatedContext("encargado-a");
    await assertFails(
      setDoc(doc(encargadoA.firestore(), "buildings/building-a/liquidaciones/2026-10"), {
        period: "2026-10",
        totalExpenses: 0,
        breakdown: [],
        unassignedUnits: [],
        emittedByUid: "encargado-a",
      })
    );
  });

  test("un Administrador de otro edificio no puede leer liquidaciones de este edificio", async () => {
    const adminB = testEnv.authenticatedContext("admin-b");
    await assertFails(getDoc(doc(adminB.firestore(), "buildings/building-a/liquidaciones/2026-10")));
  });

  test("un Administrador de otro edificio no puede escribir liquidaciones de este edificio", async () => {
    const adminB = testEnv.authenticatedContext("admin-b");
    await assertFails(
      setDoc(doc(adminB.firestore(), "buildings/building-a/liquidaciones/2026-10"), {
        period: "2026-10",
        totalExpenses: 0,
        breakdown: [],
        unassignedUnits: [],
        emittedByUid: "admin-b",
      })
    );
  });
});

describe("users/{uid}.expensesAmount — la escritura real que mueve plata", () => {
  // emitirLiquidacion (useLiquidaciones.ts) depende de esta regla preexistente para
  // que un Administrador sólo pueda tocar expensesAmount de inquilinos de su propio
  // edificio. No es una regla nueva de este diff, pero esta feature pasa a apoyarse en
  // ella, así que queda cubierta acá.
  test("un Administrador de otro edificio no puede actualizar expensesAmount de un inquilino ajeno", async () => {
    const adminB = testEnv.authenticatedContext("admin-b");
    await assertFails(updateDoc(doc(adminB.firestore(), "users/inquilino-a"), { expensesAmount: 999 }));
  });

  test("el Administrador del edificio sí puede actualizar expensesAmount de su propio inquilino", async () => {
    const adminA = testEnv.authenticatedContext("admin-a");
    await assertSucceeds(updateDoc(doc(adminA.firestore(), "users/inquilino-a"), { expensesAmount: 25000 }));
  });
});
