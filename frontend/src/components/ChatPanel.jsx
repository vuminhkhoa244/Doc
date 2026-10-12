import { useEffect, useRef, useState } from "react";
import {
  Bot,
  MessageSquare,
  Search,
  Send,
  Brain,
  Globe,
  Database,
  Layers,
  X,
  Sparkles,
  FileText,
} from "lucide-react";

const TOOL_OPTIONS = [
  { id: "rag", label: "RAG Search", icon: Search, description: "Search your documents" },
  { id: "web", label: "Web Search", icon: Globe, description: "Search the internet" },
  { id: "sql", label: "SQL Query", icon: Database, description: "Query structured data" },
];

export default function ChatPanel({
  activeDocument,
  documents = [],
  messages,
  onAsk,
  isAsking,
  onToggleDocument,
  activeDocumentIds = [],
  selectedTool = "rag",
  onToolChange,
}) {
  const [question, setQuestion] = useState("");
  const [showToolSelector, setShowToolSelector] = useState(false);
  const [showDocumentSelector, setShowDocumentSelector] = useState(false);
  const [showMemoryPanel, setShowMemoryPanel] = useState(false);
  const messagesEndRef = useRef(null);
  const headerActionsRef = useRef(null);

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

  useEffect(() => {
    if (!showToolSelector && !showDocumentSelector) return undefined;
    function handlePointerDown(event) {
      if (!headerActionsRef.current?.contains(event.target)) {
        setShowToolSelector(false);
        setShowDocumentSelector(false);
      }
    }
    document.addEventListener("pointerdown", handlePointerDown);
    return () => document.removeEventListener("pointerdown", handlePointerDown);
  }, [showToolSelector, showDocumentSelector]);

  useEffect(() => {
    function handleKeyDown(event) {
      if (event.key !== "Escape") return;
      setShowToolSelector(false);
      setShowDocumentSelector(false);
    }
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, []);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [messages, isAsking]);

  async function handleSubmit(event) {
    event.preventDefault();
    if (!canAsk) return;
    const nextQuestion = question.trim();
    setQuestion("");
    await onAsk(nextQuestion, { tool: selectedTool, documentIds: activeDocumentIds });
  }

  return (
    <section className="chat-panel">
      <header className="chat-header">
        <div className="chat-header-title">
          <p className="eyebrow">Chat with documents</p>
          <h2>{activeDocument ? activeDocument.filename : "No document selected"}</h2>
        </div>
        <div className="chat-header-actions" ref={headerActionsRef}>
          <span className="search-pill">
            <ActiveToolIcon size={14} aria-hidden="true" />
            {activeTool.label}
          </span>
          <div className="tool-selector">
            <button
              type="button"
              className={`icon-button ${showToolSelector ? "active" : ""}`}
              onClick={() => {
                setShowToolSelector((open) => !open);
                setShowDocumentSelector(false);
              }}
              aria-label="Choose AI tool"
              title="AI tools"
              aria-expanded={showToolSelector}
            >
              <Sparkles size={18} aria-hidden="true" />
            </button>
            {showToolSelector && (
              <div className="tool-dropdown" role="menu" aria-label="AI tools">
                {TOOL_OPTIONS.map((tool) => (
                  <button
                    key={tool.id}
                    type="button"
                    role="menuitemradio"
                    aria-checked={selectedTool === tool.id}
                    className={`tool-option ${selectedTool === tool.id ? "selected" : ""}`}
                    onClick={() => {
                      onToolChange(tool.id);
                      setShowToolSelector(false);
                    }}
                  >
                    <tool.icon size={16} aria-hidden="true" />
                    <span>{tool.label}</span>
                    <small>{tool.description}</small>
                  </button>
                ))}
              </div>
            )}
          </div>
          <div className="document-selector">
            <button
              type="button"
              className={`icon-button ${showDocumentSelector ? "active" : ""}`}
              onClick={() => {
                setShowDocumentSelector((open) => !open);
                setShowToolSelector(false);
              }}
              aria-label="Select documents for chat"
              title="Select documents"
              aria-expanded={showDocumentSelector}
            >
              <Layers size={18} aria-hidden="true" />
            </button>
            {showDocumentSelector && (
              <div className="document-dropdown">
                {indexedDocuments.length === 0 ? (
                  <p className="dropdown-note">
                    No indexed documents yet. Upload a file and wait for indexing.
                  </p>
                ) : (
                  indexedDocuments.map((doc) => (
                    <label key={doc.id} className="document-option">
                      <input
                        type="checkbox"
                        checked={activeDocumentIds.includes(doc.id)}
                        onChange={(event) => onToggleDocument(doc.id, event.target.checked)}
                      />
                      <span>{doc.filename}</span>
                      <small>{activeDocumentIds.includes(doc.id) ? "Selected" : "Off"}</small>
                    </label>
                  ))
                )}
              </div>
            )}
          </div>
          <div className="memory-toggle">
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
        </div>
      </header>

      <div className="message-list">
        {messages.map((message) => (
          <article key={message.id} className={`message ${message.role}`}>
            <div className="message-icon">
              {message.role === "assistant" ? (
                <Bot size={18} aria-hidden="true" />
              ) : (
                <MessageSquare size={18} aria-hidden="true" />
              )}
            </div>
            <div className="message-body">
              <p>{message.content}</p>
              {message.sources?.length ? (
                <div className="sources">
                  {message.sources.map((source, index) => (
                    <span key={`${source.document}-${source.page}-${index}`}>
                      {source.document}, page {source.page}
                    </span>
                  ))}
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
        {isAsking ? (
          <article className="message assistant message-thinking">
            <div className="message-icon">
              <Bot size={18} aria-hidden="true" />
            </div>
            <div className="message-body">
              <span className="thinking-dots" aria-label="Assistant is thinking">
                <span />
                <span />
                <span />
              </span>
            </div>
          </article>
        ) : null}
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

      <form className="chat-input" onSubmit={handleSubmit}>
        <input
          type="text"
          value={question}
          onChange={(event) => setQuestion(event.target.value)}
          placeholder="Ask about annual leave, Docker setup, policies..."
          aria-label="Question"
        />
        <button className="send-button" type="submit" disabled={!canAsk} title="Send question">
          <Send size={18} aria-hidden="true" />
        </button>
      </form>
    </section>
  );
}
