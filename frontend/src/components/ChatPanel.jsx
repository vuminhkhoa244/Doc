import { useRef, useState } from "react";
import {
  Bot,
  User,
  Search,
  Send,
  Brain,
  Globe,
  Database,
  Layers,
  X,
  Sparkles,
  FileText,
  Plus,
  Wand2,
} from "lucide-react";
import MarkdownText from "./MarkdownText";
import CopyButton from "./CopyButton";
import SourcePreviewModal from "./SourcePreviewModal.jsx";
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuLabel,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

const TOOL_OPTIONS = [
  { id: "auto", label: "Auto (Agent)", icon: Wand2, description: "Let the agent pick the best tool" },
  { id: "rag", label: "RAG Search", icon: Search, description: "Search your documents" },
  { id: "web", label: "Web Search", icon: Globe, description: "Search the internet" },
  { id: "sql", label: "SQL Query", icon: Database, description: "Query structured data" },
];

function getFileTag(filename = "") {
  const ext = filename.split(".").pop()?.toLowerCase() ?? "";
  if (ext === "pdf") return "tag-pdf";
  if (ext === "docx") return "tag-docx";
  if (ext === "md" || ext === "markdown") return "tag-md";
  if (ext === "txt") return "tag-txt";
  return "tag-file";
}

export default function ChatPanel({
  activeDocument,
  documents = [],
  messages,
  onAsk,
  isAsking,
  onToggleDocument,
  onSelectDocument,
  onDeselectDocument,
  onUpload,
  activeDocumentIds = [],
  selectedTool = "auto",
  onToolChange,
  isGuest = false,
  onRequireAuth,
}) {
  const [question, setQuestion] = useState("");
  const [showMemoryPanel, setShowMemoryPanel] = useState(false);
  const [previewSource, setPreviewSource] = useState(null);
  const messagesEndRef = useRef(null);
  const chatFileRef = useRef(null);

  const canAsk = question.trim().length > 0 && !isAsking;
  const activeTool = TOOL_OPTIONS.find((tool) => tool.id === selectedTool) ?? TOOL_OPTIONS[0];
  const ActiveToolIcon = activeTool.icon;
  const indexedDocuments = documents.filter((doc) => doc.status === "indexed");

  const userTurns = messages.filter((m) => m.role === "user").length;
  const assistantTurns = messages.filter((m) => m.role === "assistant").length;
  const toolCounts = messages.reduce(
    (acc, m) => {
      if (m.toolUsed) {
        acc[m.toolUsed] = (acc[m.toolUsed] || 0) + 1;
      }
      return acc;
    },
    { rag: 0 },
  );
  const contextDocLabels = Array.from(
    new Set(
      messages
        .filter((m) => m.sources?.length)
        .flatMap((m) => m.sources.map((s) => s.document)),
    ),
  );

  async function handleSubmit(event) {
    event.preventDefault();
    if (!canAsk) return;
    const nextQuestion = question.trim();
    setQuestion("");
    if (isGuest) {
      onRequireAuth?.({ type: "ask", question: nextQuestion, tool: selectedTool, documentIds: activeDocumentIds });
      return;
    }
    await onAsk(nextQuestion, { tool: selectedTool, documentIds: activeDocumentIds });
  }

  async function handleChatFileChange(event) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;
    await onUpload?.(file);
  }

  return (
    <section className="chat-panel">
      <header className="chat-header">
        <div className="chat-header-title">
          <p className="eyebrow">Chat with documents</p>
          {activeDocument ? (
            <div className="active-doc-tag">
              <FileText size={15} className={getFileTag(activeDocument.filename)} aria-hidden="true" />
              <span>{activeDocument.filename}</span>
              <button
                type="button"
                className="active-doc-tag-close"
                onClick={() => onDeselectDocument?.()}
                aria-label="Remove file from focus"
                title="Deselect document"
              >
                <X size={13} aria-hidden="true" />
              </button>
            </div>
          ) : (
            <h2>No document selected</h2>
          )}
        </div>
        <div className="chat-header-actions">
          <span className="search-pill">
            <ActiveToolIcon size={14} aria-hidden="true" />
            {activeTool.label}
          </span>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <button type="button" className="icon-button" aria-label="Choose AI tool" title="AI tools">
                <Sparkles size={18} aria-hidden="true" />
              </button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-72">
              <DropdownMenuLabel>AI tools</DropdownMenuLabel>
              <DropdownMenuRadioGroup value={selectedTool} onValueChange={onToolChange}>
                {TOOL_OPTIONS.map((tool) => (
                  <DropdownMenuRadioItem key={tool.id} value={tool.id} className="py-2">
                    <tool.icon aria-hidden="true" />
                    <span className="flex min-w-0 flex-col gap-0.5">
                      <span className="font-semibold">{tool.label}</span>
                      <span className="text-xs text-muted-foreground">{tool.description}</span>
                    </span>
                  </DropdownMenuRadioItem>
                ))}
              </DropdownMenuRadioGroup>
            </DropdownMenuContent>
          </DropdownMenu>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <button
                type="button"
                className="icon-button"
                aria-label="Select documents for chat"
                title="Select documents"
              >
                <Layers size={18} aria-hidden="true" />
              </button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-80">
              <DropdownMenuLabel>Chat with documents</DropdownMenuLabel>
              {indexedDocuments.length === 0 ? (
                <p className="dropdown-note">
                  No indexed documents yet. Upload a file and wait for indexing.
                </p>
              ) : (
                <DropdownMenuGroup>
                  {indexedDocuments.map((doc) => (
                    <DropdownMenuCheckboxItem
                      key={doc.id}
                      checked={activeDocumentIds.includes(doc.id)}
                      onCheckedChange={(checked) => onToggleDocument(doc.id, Boolean(checked))}
                      onSelect={(event) => event.preventDefault()}
                      className="py-2"
                    >
                      <span className="truncate font-medium">{doc.filename}</span>
                    </DropdownMenuCheckboxItem>
                  ))}
                </DropdownMenuGroup>
              )}
            </DropdownMenuContent>
          </DropdownMenu>
          <button
            type="button"
            className={`icon-button ${showMemoryPanel ? "active" : ""}`}
            onClick={() => setShowMemoryPanel((open) => !open)}
            aria-label="Conversation memory"
            title="Conversation memory"
            aria-pressed={showMemoryPanel}
          >
            <Brain size={18} aria-hidden="true" />
          </button>
        </div>
      </header>

      <div className="message-list">
        {messages.map((message) => (
          <article key={message.id} className={`message ${message.role}`}>
            <div className="message-icon">
              {message.role === "assistant" ? (
                <Bot size={18} aria-hidden="true" />
              ) : (
                <User size={18} aria-hidden="true" />
              )}
            </div>
            <div className="message-body">
              {message.role === "assistant" ? (
                message.streaming && !message.content ? (
                  <span className="thinking-dots" aria-label="Assistant is thinking">
                    <span />
                    <span />
                    <span />
                  </span>
                ) : (
                  <>
                    {!message.streaming ? <CopyButton text={message.content} /> : null}
                    <div className={message.streaming ? "md-streaming" : undefined}>
                      <MarkdownText>{message.content}</MarkdownText>
                    </div>
                  </>
                )
              ) : (
                <p>{message.content}</p>
              )}
              {message.sources?.length ? (
                <div className="sources">
                  {message.sources.map((source, index) => {
                    const key = `${source.document}-${source.page}-${index}`;
                    return source.documentId ? (
                      <button
                        key={key}
                        type="button"
                        className="source-chip"
                        title={`Preview ${source.document}, page ${source.page}`}
                        onClick={() => setPreviewSource(source)}
                      >
                        {source.document}, page {source.page}
                      </button>
                    ) : (
                      <span key={key}>
                        {source.document}, page {source.page}
                      </span>
                    );
                  })}
                </div>
              ) : null}
              {message.role === "assistant" && message.toolUsed ? (
                <div className="tool-badge">
                  <span className={`tool-badge-${message.toolUsed}`}>
                    {message.toolUsed.toUpperCase()}
                  </span>
                </div>
              ) : null}
            </div>
          </article>
        ))}
        <div ref={messagesEndRef} />
      </div>

      {showMemoryPanel && messages.length > 1 && (
        <div className="memory-panel">
          <div className="memory-header">
            <Brain size={16} aria-hidden="true" />
            <span>Conversation context</span>
            <button
              type="button"
              className="icon-button"
              onClick={() => setShowMemoryPanel(false)}
              aria-label="Close memory panel"
            >
              <X size={16} aria-hidden="true" />
            </button>
          </div>
          <div className="memory-content">
            <div className="memory-stats">
              <span>{userTurns} user turns</span>
              <span>{assistantTurns} assistant turns</span>
              <span>tool: {selectedTool}</span>
              {toolCounts.rag ? <span>rag {toolCounts.rag}</span> : null}
            </div>
            {contextDocLabels.length > 0 && (
              <div className="memory-documents">
                {contextDocLabels.map((label) => (
                  <span key={label} className="memory-doc">
                    <FileText size={12} aria-hidden="true" />
                    {label}
                  </span>
                ))}
              </div>
            )}
            <div className="memory-turns">
              {messages
                .filter((m) => m.role === "user" || m.role === "assistant")
                .slice(-6)
                .map((message) => (
                  <div key={message.id} className={`memory-item ${message.role}`}>
                    <span className="memory-role">
                      {message.role === "assistant" ? "AI" : "You"}
                    </span>
                    <span className="memory-text">
                      {(message.content || "").substring(0, 150)}
                    </span>
                    {message.toolUsed ? (
                      <span className={`memory-tool memory-tool-${message.toolUsed}`}>
                        {message.toolUsed}
                      </span>
                    ) : null}
                  </div>
                ))}
            </div>
          </div>
        </div>
      )}

      {isGuest && (
        <div className="guest-chat-hint">
          <span>Sign up or log in to send messages and chat with your documents.</span>
        </div>
      )}

      <form className="chat-input" onSubmit={handleSubmit}>
        <button
          type="button"
          className="chat-input-action"
          onClick={() => chatFileRef.current?.click()}
          title="Upload document"
          aria-label="Upload document"
        >
          <Plus size={18} aria-hidden="true" />
        </button>
        <input
          ref={chatFileRef}
          type="file"
          accept=".pdf,.docx,.md,.markdown,.txt,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/markdown,text/plain"
          className="hidden-input"
          onChange={handleChatFileChange}
        />
        <input
          type="text"
          className="chat-input-field"
          value={question}
          onChange={(event) => setQuestion(event.target.value)}
          placeholder={isGuest ? "Sign up to ask questions..." : "Ask about annual leave, Docker setup, policies..."}
          aria-label="Question"
        />
        <button className="send-button" type="submit" disabled={!canAsk} title="Send question">
          <Send size={18} aria-hidden="true" />
        </button>
      </form>

      {previewSource ? (
        <SourcePreviewModal
          source={previewSource}
          onClose={() => setPreviewSource(null)}
          onOpenDocument={(documentId) => {
            onSelectDocument?.(documentId);
            setPreviewSource(null);
          }}
        />
      ) : null}
    </section>
  );
}
