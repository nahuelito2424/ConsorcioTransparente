type StampStatus = "ok" | "warn" | "pending";

const colorClass: Record<StampStatus, string> = {
  ok: "border-ok text-ok",
  warn: "border-warn text-warn",
  pending: "border-pending text-pending",
};

/** El "sello" rotado de la dirección visual validada (ver dashboard-mock-v2.html). */
export function StatusStamp({
  status,
  children,
}: {
  status: StampStatus;
  children: React.ReactNode;
}) {
  return (
    <span
      className={`text-xs font-mono font-bold uppercase px-2 py-0.5 rounded border inline-block ${colorClass[status]}`}
      style={{ transform: "rotate(-4deg)" }}
    >
      {children}
    </span>
  );
}
