import { useRef, useState } from "react";
import {
  CheckCircle2,
  Download,
  FileText,
  Pencil,
  RefreshCw,
  Search,
  Trash2,
  Upload,
} from "lucide-react";

function formatBytes(bytes) {
  if (!bytes) return "0 KB";
  const units = ["B", "KB", "MB", "GB"];
  const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  return `${(bytes / 1024 ** index).toFixed(index === 0 ? 0 : 1)} ${units[index]}`;
}

function formatDate(value) {
  return new Intl.DateTimeFormat("en", {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

function formatType(contentType = "") {
  if (contentType.includes("pdf")) return "PDF";
  if (contentType.includes("wordprocessingml")) return "DOCX";
  if (contentType.includes("markdown")) return "Markdown";
  if (contentType.includes("plain")) return "TXT";
  return "File";
}

export default function DocumentPanel({
  documents,
  activeDocumentId,
  onSelect,
  onUpload,
  onDelete,
  onRename,
  onDownload,
  onReindex,
  onSearch,
  searchResults,
  isSearching,
  isLoading,
  isGuest = false,
  onRequireAuth,
}) {
  const fileInputRef = useRef(null);
  const [query, setQuery] = useState("");
  const [contentType, setContentType] = useState("");
  const [scope, setScope] = useState("all");
  const [renamingId, setRenamingId] = useState(null);
  const [renameValue, setRenameValue] = useState("");
  const indexedCount = documents.filter((document) => document.status === "indexed").length;

  async function handleFileChange(event) {
    const file = event.target.files?.[0];
    if (!file) return;
    if (isGuest) {
      event.target.value = "";
      onRequireAuth?.();
      return;
    }
    await onUpload(file);
    event.target.value = "";
  }

  async function handleSearch(event) {
    event.preventDefault();
    if (!query.trim()) return;
    if (isGuest) {
      onRequireAuth?.();
      return;
    }
    await onSearch({
      query: query.trim(),
      documentId: scope === "selected" ? activeDocumentId : null,
      contentType,
    });
  }

  function startRename(document) {
    setRenamingId(document.id);
    setRenameValue(document.filename);
  }

  async function commitRename(documentId) {
    const trimmed = renameValue.trim();
    setRenamingId(null);
    if (!trimmed) return;
    const current = documents.find((document) => document.id === documentId);
    if (!current || current.filename === trimmed) return;
    await onRename(documentId, trimmed);
  }

  function cancelRename() {
    setRenamingId(null);
    setRenameValue("");
  }

  return (
    <aside className="document-panel">
      <div className="panel-heading">
        <div>
          <p className="eyebrow">Documents</p>
          <h2>{documents.length} files</h2>
        </div>
        <button
          className="icon-button"
          title="Upload document"
          aria-label="Upload document"
          onClick={() => fileInputRef.current?.click()}
        >
          <Upload size={18} />
        </button>
        <input
          ref={fileInputRef}
          type="file"
          accept=".pdf,.docx,.md,.markdown,.txt,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/markdown,text/plain"
          className="hidden-input"
          onChange={handleFileChange}
        />
      </div>

      <div className="indexing-summary">
        <CheckCircle2 size={18} />
        <span>{indexedCount} indexed</span>
      </div>

      <form className="document-search" onSubmit={handleSearch}>
        <div className="search-row">
          <input
            type="text"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search vacation, Docker, policy..."
            aria-label="Semantic search query"
          />
          <button className="icon-button" type="submit" disabled={!query.trim() || isSearching} title="Search">
            <Search size={18} />
          </button>
        </div>
        <div className="filter-row">
          <select
            value={scope}
            onChange={(event) => setScope(event.target.value)}
            aria-label="Search scope"
            disabled={!activeDocumentId}
          >
            <option value="all">All documents</option>
            <option value="selected">Selected document</option>
          </select>
          <select
            value={contentType}
            onChange={(event) => setContentType(event.target.value)}
            aria-label="File type filter"
          >
            <option value="">Any type</option>
            <option value="pdf">PDF</option>
            <option value="wordprocessingml">DOCX</option>
            <option value="markdown">Markdown</option>
            <option value="plain">TXT</option>
          </select>
        </div>
      </form>

      {searchResults.length ? (
        <div className="search-results">
          {searchResults.map((result) => (
            <button
              key={`${result.documentId}-${result.page}-${result.chunkIndex}`}
              className="search-result"
              onClick={() => onSelect(result.documentId)}
            >
              <strong>{result.document}</strong>
              <small>
                {formatType(result.contentType)} · page {result.page} · score {result.score.toFixed(2)}
              </small>
              <span>{result.snippet}</span>
            </button>
          ))}
        </div>
      ) : null}

      <div className="document-list" aria-busy={isLoading}>
        {documents.map((document) => (
          <div
            key={document.id}
            className={`document-item ${document.id === activeDocumentId ? "active" : ""}`}
            onClick={() => onSelect(document.id)}
            onKeyDown={(event) => {
              if (event.key === "Enter" || event.key === " ") {
                event.preventDefault();
                onSelect(document.id);
              }
            }}
            role="button"
            tabIndex={0}
          >
            <FileText size={20} />
            {renamingId === document.id ? (
              <span
                className="rename-row"
                onClick={(event) => event.stopPropagation()}
              >
                <input
                  type="text"
                  value={renameValue}
                  autoFocus
                  onChange={(event) => setRenameValue(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter") commitRename(document.id);
                    if (event.key === "Escape") cancelRename();
                  }}
                  onBlur={() => commitRename(document.id)}
                  aria-label="New document name"
                />
              </span>
            ) : (
              <span>
                <strong>{document.filename}</strong>
                <small>
                  {formatType(document.contentType)} · {formatBytes(document.size)} · {formatDate(document.uploadTime)}
                </small>
              </span>
            )}
            <em className={document.status}>{document.status}</em>
            {renamingId !== document.id ? (
              <span
                className="document-item-actions"
                onClick={(event) => event.stopPropagation()}
              >
                <button
                  type="button"
                  className="icon-button"
                  title="Rename document"
                  aria-label={`Rename ${document.filename}`}
                  onClick={() => startRename(document)}
                >
                  <Pencil size={15} />
                </button>
                <button
                  type="button"
                  className="icon-button"
                  title="Download document"
                  aria-label={`Download ${document.filename}`}
                  onClick={() => onDownload(document.id, document.filename)}
                >
                  <Download size={15} />
                </button>
              </span>
            ) : null}
          </div>
        ))}
      </div>

      {activeDocumentId ? (
        <div className="document-actions-row">
          <button
            className="secondary-button"
            onClick={() => {
              const document = documents.find((item) => item.id === activeDocumentId);
              if (document) startRename(document);
            }}
          >
            <Pencil size={17} />
            Rename
          </button>
          <button
            className="secondary-button"
            onClick={() => {
              const document = documents.find((item) => item.id === activeDocumentId);
              if (document) onDownload(document.id, document.filename);
            }}
          >
            <Download size={17} />
            Download
          </button>
          <button
            className="secondary-button"
            onClick={() => onReindex(activeDocumentId)}
            title="Re-extract and re-index this document with the AI service"
          >
            <RefreshCw size={17} />
            Reindex
          </button>
          <button className="danger-button" onClick={() => onDelete(activeDocumentId)}>
            <Trash2 size={17} />
            Delete
          </button>
        </div>
      ) : null}
    </aside>
  );
}
