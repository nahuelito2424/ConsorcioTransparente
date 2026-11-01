import { useState } from "react";
import { useAuth } from "../../auth/useAuth";
import { useBuilding } from "../../hooks/useBuilding";
import { useMyPayments } from "../../hooks/useMyPayments";
import { toPeriod } from "../../lib/period";
import { StatusStamp } from "../../components/StatusStamp";
import { Dropzone } from "../../components/Dropzone";
import type { PaymentStatus } from "../../types";

const currency = new Intl.NumberFormat("es-AR", { style: "currency", currency: "ARS" });
const breakdownLabel: Record<string, string> = { alquiler: "Alquiler", expensas: "Expensas" };

function stampFor(status: PaymentStatus) {
  if (status === "PAGADO") return { status: "ok" as const, label: "Pagado" };
  if (status === "A_VERIFICAR") return { status: "pending" as const, label: "A verificar" };
  return { status: "warn" as const, label: "Rechazado" };
}

export function FacturasPage() {
  const { user } = useAuth();
  const buildingId = user?.buildingId ?? "";
  const { building, loading: buildingLoading } = useBuilding(buildingId);
  const { payments, loading: paymentsLoading, error: loadError, uploadReceipt } = useMyPayments(
    buildingId,
    user?.uid ?? ""
  );
  const currentPeriod = toPeriod(new Date());

  const [file, setFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);

  const loading = buildingLoading || paymentsLoading;
  const currentPayment = payments.find((p) => p.period === currentPeriod) ?? null;
  const history = payments.filter((p) => p.period !== currentPeriod);

  // Mismo criterio que PagarViewModel.kt (Android): sólo entra en el breakdown lo que
  // el edificio cobra y el inquilino tiene cargado (>0) — ver CLAUDE.md sobre
  // expensesAmount/rentAmount.
  const breakdown: Record<string, number> = {};
  if (building?.chargesRent && (user?.rentAmount ?? 0) > 0) breakdown.alquiler = user!.rentAmount;
  if (building?.chargesExpenses && (user?.expensesAmount ?? 0) > 0) breakdown.expensas = user!.expensesAmount;
  const total = Object.values(breakdown).reduce((sum, v) => sum + v, 0);

  const canUpload = !currentPayment || currentPayment.status === "RECHAZADO";

  async function handleUpload() {
    if (!file || total <= 0) return;
    setUploading(true);
    setUploadError(null);
    try {
      await uploadReceipt(currentPeriod, breakdown, file);
      setFile(null);
    } catch {
      setUploadError("No se pudo subir el comprobante. Probá de nuevo.");
    } finally {
      setUploading(false);
    }
  }

  return (
    <div>
      <h1 className="font-serif text-2xl mb-1">Mis facturas</h1>
      <p className="text-sm text-pending mb-6">{currentPeriod}</p>

      {loading ? (
        <p className="text-sm text-pending">Cargando…</p>
      ) : loadError ? (
        <p className="text-warn text-sm">{loadError}</p>
      ) : (
        <>
          <div className="bg-white border border-border-soft rounded-lg p-6 mb-8 max-w-xl">
            {total <= 0 ? (
              <p className="text-sm text-pending">
                No tenés un monto de alquiler/expensas cargado todavía — consultá con tu administración.
              </p>
            ) : (
              <>
                <p className="font-serif text-2xl mb-1">{currency.format(total)}</p>
                <p className="text-xs uppercase tracking-wide text-pending mb-4">Total del período</p>

                <ul className="text-sm mb-4 space-y-1">
                  {Object.entries(breakdown).map(([key, amount]) => (
                    <li key={key} className="flex justify-between border-t border-dashed border-border-soft pt-1">
                      <span>{breakdownLabel[key] ?? key}</span>
                      <span>{currency.format(amount)}</span>
                    </li>
                  ))}
                </ul>

                {currentPayment && (
                  <div className="mb-4">
                    <StatusStamp status={stampFor(currentPayment.status).status}>
                      {stampFor(currentPayment.status).label}
                    </StatusStamp>
                    {currentPayment.status === "RECHAZADO" && currentPayment.rejectionReason && (
                      <p className="text-warn text-sm mt-2">Motivo: {currentPayment.rejectionReason}</p>
                    )}
                    {currentPayment.receiptUrl && (
                      <p className="text-sm mt-2">
                        <a
                          href={currentPayment.receiptUrl}
                          target="_blank"
                          rel="noreferrer"
                          className="underline decoration-dashed text-pending"
                        >
                          Ver comprobante subido
                        </a>
                      </p>
                    )}
                  </div>
                )}

                {canUpload && (
                  <div className="mt-4">
                    <p className="text-xs uppercase tracking-wide text-pending mb-1">
                      {currentPayment ? "Volver a subir el comprobante" : "Subí tu comprobante de transferencia"}
                    </p>
                    <Dropzone file={file} onChange={setFile} />
                    {uploadError && <p className="text-warn text-sm mt-2">{uploadError}</p>}
                    <button
                      onClick={handleUpload}
                      disabled={!file || uploading}
                      className="mt-4 bg-terracota text-white rounded px-4 py-2 text-sm disabled:opacity-50"
                    >
                      {uploading ? "Subiendo…" : "Enviar comprobante"}
                    </button>
                  </div>
                )}
              </>
            )}
          </div>

          <h2 className="font-serif text-lg mb-3">Historial</h2>
          {history.length === 0 ? (
            <p className="text-sm text-pending">Todavía no hay pagos de períodos anteriores.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm min-w-[480px]">
                <thead>
                  <tr className="text-left text-xs uppercase tracking-wide text-pending">
                    <th className="pb-2 pr-4">Período</th>
                    <th className="pb-2 pr-4">Monto</th>
                    <th className="pb-2">Estado</th>
                  </tr>
                </thead>
                <tbody>
                  {history.map((p) => (
                    <tr key={p.id} className="border-t border-dashed border-border-soft">
                      <td className="py-2 pr-4">{p.period}</td>
                      <td className="py-2 pr-4">{currency.format(p.amount)}</td>
                      <td className="py-2">
                        <StatusStamp status={stampFor(p.status).status}>{stampFor(p.status).label}</StatusStamp>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </div>
  );
}
