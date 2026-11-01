import { useEffect, useState } from "react";
import { useAuth } from "../../auth/useAuth";
import { getMyResponse, getResultCounts, isVoteClosed, submitVote, useVotes } from "../../hooks/useVotes";
import type { VoteTopic } from "../../types";

const dateTime = new Intl.DateTimeFormat("es-AR", { dateStyle: "short", timeStyle: "short" });

function VoteCard({ vote, buildingId, uid }: { vote: VoteTopic; buildingId: string; uid: string }) {
  const [myOption, setMyOption] = useState<number | null | undefined>(undefined); // undefined = cargando
  const [counts, setCounts] = useState<number[] | null>(null);
  const [selected, setSelected] = useState<number | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const closed = isVoteClosed(vote);

  useEffect(() => {
    let cancelled = false;
    getMyResponse(buildingId, vote.id, uid).then((r) => {
      if (!cancelled) setMyOption(r ? r.optionIndex : null);
    });
    return () => {
      cancelled = true;
    };
  }, [buildingId, vote.id, uid]);

  const showResults = myOption !== undefined && (myOption !== null || closed);

  useEffect(() => {
    if (!showResults) return;
    let cancelled = false;
    getResultCounts(buildingId, vote.id, vote.options.length).then((c) => {
      if (!cancelled) setCounts(c);
    });
    return () => {
      cancelled = true;
    };
  }, [showResults, buildingId, vote.id, vote.options.length]);

  async function handleVote() {
    if (selected === null) return;
    setSubmitting(true);
    setError(null);
    try {
      await submitVote(buildingId, vote.id, uid, selected);
      setMyOption(selected);
    } catch {
      setError("No se pudo registrar tu voto. Probá de nuevo.");
    } finally {
      setSubmitting(false);
    }
  }

  const total = counts?.reduce((sum, c) => sum + c, 0) ?? 0;

  return (
    <li className="bg-white border border-border-soft rounded-lg p-5">
      <h2 className="font-serif text-lg mb-1">{vote.title}</h2>
      <p className="text-sm text-stone-700 mb-2">{vote.description}</p>
      <p className="text-xs text-pending mb-3 font-mono">
        {closed ? "Cerrada" : vote.closesAt ? `Cierra ${dateTime.format(vote.closesAt)}` : "Sin fecha de cierre"}
      </p>

      {myOption === undefined ? (
        <p className="text-sm text-pending">Cargando…</p>
      ) : showResults && counts ? (
        <ul className="space-y-1">
          {vote.options.map((opt, i) => (
            <li key={i} className="text-sm">
              <div className="flex justify-between">
                <span className={myOption === i ? "font-semibold" : ""}>
                  {opt}
                  {myOption === i && " (tu voto)"}
                </span>
                <span className="text-pending">
                  {counts[i]} {total > 0 ? `(${Math.round((counts[i] / total) * 100)}%)` : ""}
                </span>
              </div>
              <div className="h-1.5 bg-cream rounded overflow-hidden mt-1">
                <div
                  className="h-full bg-terracota"
                  style={{ width: total > 0 ? `${(counts[i] / total) * 100}%` : "0%" }}
                />
              </div>
            </li>
          ))}
        </ul>
      ) : (
        <div>
          <div className="space-y-1 mb-3">
            {vote.options.map((opt, i) => (
              <label key={i} className="flex items-center gap-2 text-sm cursor-pointer">
                <input
                  type="radio"
                  name={`vote-${vote.id}`}
                  checked={selected === i}
                  onChange={() => setSelected(i)}
                />
                {opt}
              </label>
            ))}
          </div>
          {error && <p className="text-warn text-sm mb-2">{error}</p>}
          <button
            onClick={handleVote}
            disabled={selected === null || submitting}
            className="bg-terracota text-white rounded px-4 py-2 text-sm disabled:opacity-50"
          >
            {submitting ? "Votando…" : "Votar"}
          </button>
        </div>
      )}
    </li>
  );
}

export function VotarPage() {
  const { user } = useAuth();
  const buildingId = user?.buildingId ?? "";
  const { votes, loading, error } = useVotes(buildingId);

  return (
    <div>
      <h1 className="font-serif text-2xl mb-1">Votar</h1>
      <p className="text-sm text-pending mb-6">Un voto por convocatoria — no se puede cambiar una vez emitido.</p>

      {loading ? (
        <p className="text-sm text-pending">Cargando…</p>
      ) : error ? (
        <p className="text-warn text-sm">{error}</p>
      ) : votes.length === 0 ? (
        <p className="text-sm text-pending">Todavía no hay votaciones.</p>
      ) : (
        <ul className="space-y-4 max-w-xl">
          {votes.map((vote) => (
            <VoteCard key={vote.id} vote={vote} buildingId={buildingId} uid={user!.uid} />
          ))}
        </ul>
      )}
    </div>
  );
}
