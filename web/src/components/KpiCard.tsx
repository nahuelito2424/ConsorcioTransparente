export function KpiCard({ value, label }: { value: string; label: string }) {
  return (
    <div className="border-l-[3px] border-terracota pl-5 py-1">
      <p className="font-serif text-4xl text-stone-900">{value}</p>
      <p className="text-xs uppercase tracking-wide text-pending mt-1">{label}</p>
    </div>
  );
}
