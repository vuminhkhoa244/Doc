import { useEffect, useState } from "react";
import { FileText, X } from "lucide-react";
import { getPageChunks } from "../api.js";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
  DialogClose,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";

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

  if (!source) return null;

  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
    >
      <DialogContent
        className="source-preview-modal flex flex-col gap-0 border-0"
        style={{ maxWidth: "none" }}
        overlayClassName="auth-modal-overlay source-preview-overlay"
        showCloseButton={false}
      >
        <DialogClose asChild>
          <button className="auth-modal-close" aria-label="Close" type="button">
            <X size={18} />
          </button>
        </DialogClose>

        <div className="source-preview-header">
          <span className="source-preview-icon">
            <FileText size={18} aria-hidden="true" />
          </span>
          <div>
            <DialogTitle asChild>
              <h3>{source.document}</h3>
            </DialogTitle>
            <DialogDescription asChild>
              <p>Page {source.page} · extracted text used for this answer</p>
            </DialogDescription>
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
          <Button variant="secondary" className="secondary-button" type="button" onClick={onClose}>
            Close
          </Button>
          <Button
            className="primary-button"
            type="button"
            onClick={() => onOpenDocument(source.documentId)}
          >
            Open document
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
