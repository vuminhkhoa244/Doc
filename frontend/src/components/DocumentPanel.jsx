import { useEffect, useMemo, useRef, useState } from "react";
import {
  Download,
  FileText,
  MoreHorizontal,
  Pencil,
  PanelLeftClose,
  PanelLeftOpen,
  RefreshCw,
  Search,
  Trash2,
  Upload,
} from "lucide-react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

const ACCEPTED_DROP_FILE = /\.(pdf|docx|md|markdown|txt)$/i;

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
  const dragDepthRef = useRef(0);
  const [isPanelOpen, setIsPanelOpen] = useState(true);
  const [isSearchOpen, setIsSearchOpen] = useState(false);
  const [sortBy, setSortBy] = useState("newest");
  const [query, setQuery] = useState("");
  const [contentType, setContentType] = useState("");
  const [scope, setScope] = useState("all");
  const [renamingId, setRenamingId] = useState(null);
  const [renameValue, setRenameValue] = useState("");
  const [isDraggingFile, setIsDraggingFile] = useState(false);
  const [dropError, setDropError] = useState("");

  const sortedDocuments = useMemo(() => {
    const list = [...documents];
    if (sortBy === "oldest") {
      list.sort((a, b) => new Date(a.uploadTime) - new Date(b.uploadTime));
    } else if (sortBy === "name") {
      list.sort((a, b) => a.filename.localeCompare(b.filename));
    } else {
      list.sort((a, b) => new Date(b.uploadTime) - new Date(a.uploadTime));
    }
    return list;
  }, [documents, sortBy]);

  // Keep the browser from opening dropped files outside the panel
  useEffect(() => {
    function preventDefault(event) {
      event.preventDefault();
    }
    window.addEventListener("dragover", preventDefault);
    window.addEventListener("drop", preventDefault);
    return () => {
      window.removeEventListener("dragover", preventDefault);
      window.removeEventListener("drop", preventDefault);
    };
  }, []);

  function handleDragEnter(event) {
    event.preventDefault();
    if (!Array.from(event.dataTransfer?.types ?? []).includes("Files")) return;
    dragDepthRef.current += 1;
    setIsDraggingFile(true);
  }

  function handleDragOver(event) {
    event.preventDefault();
    if (event.dataTransfer) event.dataTransfer.dropEffect = "copy";
  }

  function handleDragLeave(event) {
    event.preventDefault();
    dragDepthRef.current = Math.max(0, dragDepthRef.current - 1);
    if (dragDepthRef.current === 0) setIsDraggingFile(false);
  }

  async function handleDrop(event) {
    event.preventDefault();
    dragDepthRef.current = 0;
    setIsDraggingFile(false);
    const files = Array.from(event.dataTransfer?.files ?? []);
    const file = files.find((candidate) => ACCEPTED_DROP_FILE.test(candidate.name));
    if (!file) {
      setDropError("Only PDF, DOCX, Markdown, and TXT files can be uploaded.");
      return;
    }
    setDropError("");
    if (isGuest) {
      onRequireAuth?.();
      return;
    }
    await onUpload(file);
  }

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

  function handleItemClick(documentId) {
    onSelect(documentId);
    setRenamingId(null);
    setRenameValue("");
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
    <aside
      className={`document-panel${isDraggingFile ? " drag-over" : ""}${isPanelOpen ? "" : " collapsed"}`}
      onDragEnter={handleDragEnter}
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
    >
        {isDraggingFile ? (
          <div className="drop-overlay" aria-hidden="true">
            <Upload size={28} />
            <p>Drop your document here</p>
            <small>PDF, DOCX, Markdown, or TXT</small>
          </div>
        ) : null}
        <div className="panel-heading">
          <div>
            <p className="eyebrow">Documents</p>
            <h2>{documents.length} files</h2>
          </div>
          <div className="panel-heading-actions">
            <button
              className={`icon-button${isSearchOpen ? " active" : ""}`}
              title="Search documents"
              aria-label="Search documents"
              aria-expanded={isSearchOpen}
              onClick={() => setIsSearchOpen((open) => !open)}
            >
              <Search size={18} />
            </button>
            <button
              className="icon-button"
              title="Upload document"
              aria-label="Upload document"
              onClick={() => fileInputRef.current?.click()}
            >
              <Upload size={18} />
            </button>
            <button
              className="icon-button"
              title="Hide sidebar"
              aria-label="Hide sidebar"
              onClick={() => setIsPanelOpen(false)}
            >
              <PanelLeftClose size={18} />
            </button>
          </div>
          <input
            ref={fileInputRef}
            type="file"
            accept=".pdf,.docx,.md,.markdown,.txt,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/markdown,text/plain"
            className="hidden-input"
            onChange={handleFileChange}
          />
        </div>

        {dropError ? (
          <p className="drop-error" role="alert">
            {dropError}
            <button type="button" onClick={() => setDropError("")} aria-label="Dismiss">
              ×
            </button>
          </p>
        ) : null}

        {isSearchOpen ? (
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
            <div className="filter-row">
              <select
                value={sortBy}
                onChange={(event) => setSortBy(event.target.value)}
                aria-label="Sort documents"
              >
                <option value="newest">Newest first</option>
                <option value="oldest">Oldest first</option>
                <option value="name">Name A–Z</option>
              </select>
            </div>
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
          </form>
        ) : null}

        <div className="document-list" aria-busy={isLoading}>
          {sortedDocuments.map((document) => (
            <div className={`document-item ${document.id === activeDocumentId ? "active" : ""}`} key={document.id}>
              {renamingId === document.id ? (
                <span className="rename-row">
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
                <button
                  type="button"
                  className="document-item-select"
                  onClick={() => handleItemClick(document.id)}
                >
                  <FileText size={18} />
                  <span>
                    <strong>{document.filename}</strong>
                    <small>
                      {formatType(document.contentType)} · {formatBytes(document.size)} · {formatDate(document.uploadTime)}
                    </small>
                  </span>
                  <em className={document.status}>{document.status}</em>
                </button>
              )}
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <button
                    type="button"
                    className="doc-more"
                    aria-label={`Actions for ${document.filename}`}
                    title="More options"
                  >
                    <MoreHorizontal size={16} />
                  </button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" className="w-56">
                  <DropdownMenuItem onSelect={() => startRename(document)}>
                    <Pencil />
                    Rename
                  </DropdownMenuItem>
                  <DropdownMenuItem onSelect={() => onDownload(document.id, document.filename)}>
                    <Download />
                    Download
                  </DropdownMenuItem>
                  <DropdownMenuItem
                    onSelect={() => onReindex(document.id)}
                    title="Re-extract and re-index this document with the AI service"
                  >
                    <RefreshCw />
                    Reindex
                  </DropdownMenuItem>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem variant="destructive" onSelect={() => onDelete(document.id)}>
                    <Trash2 />
                    Delete
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
          ))}
        </div>
        {!isPanelOpen ? (
          <button
            className="sidebar-expand"
            title="Show sidebar"
            aria-label="Show sidebar"
            onClick={() => setIsPanelOpen(true)}
          >
            <PanelLeftOpen size={18} />
          </button>
        ) : null}
    </aside>
  );
}
