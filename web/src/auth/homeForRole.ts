import type { Role } from "../types";

/** A dónde mandar a cada rol cuando entra a "/" o cae en una ruta que no le
 * corresponde — ver RequireRole.tsx. */
export function homeForRole(role: Role): string {
  return role === "INQUILINO" ? "/facturas" : "/";
}
