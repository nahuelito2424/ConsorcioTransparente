import { useState } from "react";
import { useAuth } from "../../auth/useAuth";
import { addReview, useProviderReviews, useProviders } from "../../hooks/useProviders";

function Stars({ value }: { value: number }) {
  return <span className="text-warn">{"★".repeat(Math.round(value))}{"☆".repeat(5 - Math.round(value))}</span>;
}

export function ProveedoresPage() {
  const { user } = useAuth();
  const { providers, loading, error } = useProviders();
  const [openId, setOpenId] = useState<string | null>(null);
  const reviews = useProviderReviews(openId);

  const [comment, setComment] = useState("");
  const [stars, setStars] = useState(5);
  const [submitting, setSubmitting] = useState(false);
  const [reviewError, setReviewError] = useState<string | null>(null);

  async function handleAddReview() {
    if (!comment.trim() || !openId || !user) return;
    setSubmitting(true);
    setReviewError(null);
    try {
      await addReview(openId, user.uid, comment.trim(), stars);
      setComment("");
      setStars(5);
    } catch {
      setReviewError("No se pudo guardar la reseña. Probá de nuevo.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div>
      <h1 className="font-serif text-2xl mb-1">Directorio de proveedores</h1>
      <p className="text-sm text-pending mb-6">Plomería, electricidad, gas, albañilería.</p>

      {loading ? (
        <p className="text-sm text-pending">Cargando…</p>
      ) : error ? (
        <p className="text-warn text-sm">{error}</p>
      ) : providers.length === 0 ? (
        <p className="text-sm text-pending">Todavía no hay proveedores cargados.</p>
      ) : (
        <ul className="space-y-3 max-w-xl">
          {providers.map((p) => {
            const open = openId === p.id;
            return (
              <li key={p.id} className="bg-white border border-border-soft rounded-lg p-5">
                <button
                  onClick={() => setOpenId(open ? null : p.id)}
                  className="w-full text-left"
                >
                  <div className="flex justify-between items-start">
                    <h2 className="font-serif text-lg">{p.name}</h2>
                    <Stars value={p.averageRating} />
                  </div>
                  <p className="text-sm text-pending">
                    {p.category} · {p.phone || "sin teléfono"} · {p.ratingCount}{" "}
                    {p.ratingCount === 1 ? "reseña" : "reseñas"}
                  </p>
                </button>

                {open && (
                  <div className="mt-4 border-t border-dashed border-border-soft pt-4">
                    {reviews.length === 0 ? (
                      <p className="text-sm text-pending mb-3">Todavía no hay reseñas.</p>
                    ) : (
                      <ul className="space-y-2 mb-4">
                        {reviews.map((r) => (
                          <li key={r.id} className="text-sm">
                            <Stars value={r.stars} /> {r.comment}
                          </li>
                        ))}
                      </ul>
                    )}

                    <div className="flex items-center gap-2 mb-2">
                      {[1, 2, 3, 4, 5].map((n) => (
                        <button
                          key={n}
                          type="button"
                          onClick={() => setStars(n)}
                          className={n <= stars ? "text-warn" : "text-border-soft"}
                          aria-label={`${n} estrellas`}
                        >
                          ★
                        </button>
                      ))}
                    </div>
                    <textarea
                      value={comment}
                      onChange={(e) => setComment(e.target.value)}
                      placeholder="Contá tu experiencia con este proveedor"
                      rows={2}
                      className="w-full border border-border-soft rounded px-3 py-2 mb-2 text-sm"
                    />
                    {reviewError && <p className="text-warn text-sm mb-2">{reviewError}</p>}
                    <button
                      onClick={handleAddReview}
                      disabled={!comment.trim() || submitting}
                      className="bg-terracota text-white rounded px-3 py-1.5 text-sm disabled:opacity-50"
                    >
                      {submitting ? "Guardando…" : "Dejar reseña"}
                    </button>
                  </div>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
