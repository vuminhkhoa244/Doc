import { useEffect, useState } from "react";
import { FileText, X } from "lucide-react";
import { getPageChunks } from "../api.js";

export default function SourcePreviewModal({ source, onClose, onOpenDocument }) {
  const [chunks, setChunks] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!source?.documentId) return undefined;
    let isMounted = true;
    setIsLoading(true);
    setError("");
    setChunks([]);
    getPageChunks(source.documentId, source.page)
      .then((result) => {
        if (isMounted) setChunks(result);
      })
      .catch((err) => {
        if (isMounted) setError(err.message || "Could not load source text.");
      })
      .finally(() => {
        if (isMounted) setIsLoading(false);
      });
    return () => {
      isMounted = false;
    };
  }, [source?.documentId, source?.page]);

  useEffect(() => {
    function handleKeyDown(event) {
      if (event.key === "Escape") onClose();
    }
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [onClose]);

  if (!source) return null;

  return (
    <div className="auth-modal-overlay source-preview-overlay" onClick={onClose}>
      <div
        className="source-preview-modal"
        role="dialog"
        aria-modal="true"
        aria-label={`Source preview: ${source.document}, page ${source.page}`}
        onClick={(event) => event.stopPropagation()}
      >
        <button className="auth-modal-close" onClick={onClose} aria-label="Close" type="button">
          <X size={18} />
        </button>

        <div className="source-preview-header">
          <span className="source-preview-icon">
            <FileText size={18} aria-hidden="true" />
          </span>
          <div>
            <h3>{source.document}</h3>
            <p>Page {source.page} · extracted text used for this answer</p>
          </div>
        </div>

        <div className="source-preview-body">
          {isLoading ? (
            <p className="source-preview-note">Loading source text...</p>
          ) : error ? (
            <p className="source-preview-note source-preview-error" role="alert">
              {error}
            </p>
          ) : chunks.length === 0 ? (
            <p className="source-preview-note">No extracted text found for this page.</p>
          ) : (
            chunks.map((chunk) => (
              <p key={chunk.chunkIndex}>{chunk.text}</p>
            ))
          )}
        </div>

        <div className="source-preview-footer">
          <button className="secondary-button" type="button" onClick={onClose}>
            Close
          </button>
          <button
            className="primary-button"
            type="button"
            onClick={() => onOpenDocument(source.documentId)}
          >
            Open document
          </button>
        </div>
      </div>
    </div>
  );
}
