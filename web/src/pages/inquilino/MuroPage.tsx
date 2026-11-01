import { useState, type FormEvent } from "react";
import { useAuth } from "../../auth/useAuth";
import { usePosts } from "../../hooks/usePosts";
import { Dropzone } from "../../components/Dropzone";

const dateTime = new Intl.DateTimeFormat("es-AR", { dateStyle: "short", timeStyle: "short" });

export function MuroPage() {
  const { user } = useAuth();
  const buildingId = user?.buildingId ?? "";
  const { posts, loading, error: loadError, createPost } = usePosts(buildingId);

  const [showForm, setShowForm] = useState(false);
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [justSent, setJustSent] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!title.trim() || !body.trim() || !user) return;
    setSubmitting(true);
    setError(null);
    try {
      await createPost(title.trim(), body.trim(), user.uid, user.fullName, file);
      setTitle("");
      setBody("");
      setFile(null);
      setShowForm(false);
      setJustSent(true);
    } catch {
      setError("No se pudo publicar. Probá de nuevo.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div>
      <h1 className="font-serif text-2xl mb-1">Muro</h1>
      <p className="text-sm text-pending mb-6">
        Avisos del edificio. Toda publicación pasa primero por la aprobación del Administrador.
      </p>

      {justSent && (
        <p className="text-ok text-sm mb-4">
          Tu publicación se envió y va a aparecer acá apenas el Administrador la apruebe.
        </p>
      )}

      <button
        onClick={() => {
          setShowForm((v) => !v);
          setJustSent(false);
        }}
        className="bg-terracota text-white rounded px-4 py-2 text-sm mb-6"
      >
        {showForm ? "Cancelar" : "Publicar un aviso"}
      </button>

      {showForm && (
        <form
          onSubmit={handleSubmit}
          className="bg-white border border-border-soft rounded-lg p-6 mb-6 max-w-md"
        >
          <label htmlFor="post-titulo" className="block text-xs uppercase tracking-wide text-pending mb-1">
            Título
          </label>
          <input
            id="post-titulo"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            className="w-full border border-border-soft rounded px-3 py-2 mb-3"
            required
          />

          <label htmlFor="post-cuerpo" className="block text-xs uppercase tracking-wide text-pending mb-1">
            Mensaje
          </label>
          <textarea
            id="post-cuerpo"
            value={body}
            onChange={(e) => setBody(e.target.value)}
            rows={4}
            className="w-full border border-border-soft rounded px-3 py-2 mb-3"
            required
          />

          <label htmlFor="post-imagen" className="block text-xs uppercase tracking-wide text-pending mb-1">
            Imagen (opcional)
          </label>
          <div className="mb-4">
            <Dropzone id="post-imagen" file={file} onChange={setFile} />
          </div>

          {error && <p className="text-warn text-sm mb-4">{error}</p>}

          <button
            type="submit"
            disabled={submitting}
            className="w-full bg-terracota text-white rounded py-2 font-semibold disabled:opacity-50"
          >
            {submitting ? "Publicando…" : "Enviar a moderación"}
          </button>
        </form>
      )}

      {loadError && <p className="text-warn text-sm mb-4">{loadError}</p>}

      {loading ? (
        <p className="text-sm text-pending">Cargando…</p>
      ) : posts.length === 0 ? (
        <p className="text-sm text-pending">Todavía no hay avisos publicados.</p>
      ) : (
        <ul className="space-y-4 max-w-xl">
          {posts.map((post) => (
            <li key={post.id} className="bg-white border border-border-soft rounded-lg p-5">
              <h2 className="font-serif text-lg mb-1">{post.title}</h2>
              <p className="text-xs text-pending mb-3 font-mono">
                {post.authorName}
                {post.createdAt && ` — ${dateTime.format(post.createdAt)}`}
              </p>
              {post.imageUrl && (
                <img src={post.imageUrl} alt="" className="rounded mb-3 max-h-64 object-cover" />
              )}
              <p className="text-sm whitespace-pre-wrap">{post.body}</p>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
