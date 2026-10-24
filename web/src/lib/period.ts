/**
 * "yyyy-MM" en hora LOCAL del navegador — nunca `toISOString().slice(0, 7)`, que usa
 * UTC. CABA es UTC-3: un gasto cargado a las 23:00 del último día del mes cae, en UTC,
 * en el 1 del mes siguiente. Mismo criterio que Android (`util/Period.kt`,
 * `SimpleDateFormat("yyyy-MM")` con la zona del dispositivo) — los períodos tienen que
 * coincidir entre las dos plataformas, es la misma clave que usa `buildings/{id}/payments`.
 */
export function toPeriod(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  return `${year}-${month}`;
}
