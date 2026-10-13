import { useEffect, useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import {
  ArrowLeft,
  ChevronRight,
  CircleAlert,
  Clock,
  Inbox,
  LogOut,
  Lock,
  MessageSquareText,
  RotateCcw,
  Search,
  SearchX,
} from "lucide-react";
import { listConversationHistory } from "../api.js";
import { useAuth } from "../AuthContext.jsx";

const RESUME_KEY = "ai-doc-assistant-resume-conversation";

export default function ConversationHistoryPage() {
  const { user, openAuth, logout } = useAuth();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const [conversations, setConversations] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [searchQuery, setSearchQuery] = useState("");
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    if (!user?.id) {
      setIsLoading(false);
      return undefined;
    }
    let isMounted = true;
    setIsLoading(true);
    setLoadError("");
    listConversationHistory()
      .then((result) => {
        if (isMounted) setConversations(result);
      })
      .catch((error) => {
        if (isMounted) setLoadError(error.message || "Could not load conversation history.");
      })
      .finally(() => {
        if (isMounted) setIsLoading(false);
      });
    return () => { isMounted = false; };
  }, [user?.id, reloadToken]);

  const filteredConversations = useMemo(() => {
    const needle = searchQuery.trim().toLowerCase();
    if (!needle) return conversations;
    return conversations.filter(
      (conv) =>
        (conv.question ?? "").toLowerCase().includes(needle) ||
        (conv.answer ?? "").toLowerCase().includes(needle),
    );
  }, [conversations, searchQuery]);

  const selectedId = searchParams.get("entry");
  const selected =
    conversations.find((conv) => String(conv.id) === selectedId) ?? null;

  const countLabel = isLoading
    ? "Loading..."
    : `${conversations.length} ${conversations.length === 1 ? "entry" : "entries"}`;

  function formatDate(value) {
    return new Intl.DateTimeFormat("en", {
      month: "short",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    }).format(new Date(value));
  }

  function selectConversation(conv) {
    setSearchParams({ entry: String(conv.id) });
  }

  function openInChat(conv) {
    if (!conv.conversationId) return;
    window.localStorage.setItem(
      RESUME_KEY,
      JSON.stringify({ conversationId: conv.conversationId }),
    );
    navigate("/", { replace: true });
  }

  function goToChat() {
    navigate("/");
  }

  return (
    <main className="app-shell">
      <header className="topbar">
        <div className="topbar-leading">
          <button
            className="icon-button"
            type="button"
            onClick={goToChat}
            title="Back to chat"
            aria-label="Back to chat"
          >
            <ArrowLeft size={18} aria-hidden="true" />
          </button>
          <div>
            <p className="eyebrow">AI Document Assistant</p>
            <h1>Conversation history</h1>
          </div>
        </div>
        <div className="user-actions">
          {user ? (
            <>
              <span>{user.email}</span>
              <button
                className="icon-button"
                type="button"
                title="Log out"
                aria-label="Log out"
                onClick={logout}
              >
                <LogOut size={18} aria-hidden="true" />
              </button>
            </>
          ) : (
            <>
              <button className="ghost-button" onClick={() => openAuth("login")}>
                Log in
              </button>
              <button className="primary-button" onClick={() => openAuth("register")}>
                Sign up
              </button>
            </>
          )}
        </div>
      </header>

      {!user ? (
        <div className="history-locked">
          <Lock size={32} aria-hidden="true" />
          <h2>Sign in to view your history</h2>
          <p>Your conversation history is saved to your account. Log in or create an account to access it.</p>
          <div className="history-locked-actions">
            <button className="primary-button" onClick={() => openAuth("login")}>
              Log in
            </button>
            <button className="secondary-button" onClick={() => openAuth("register")}>
              Sign up
            </button>
          </div>
        </div>
      ) : (
        <div className="conversation-history-shell">
          <aside className="history-sidebar">
            <div className="panel-heading">
              <div>
                <p className="eyebrow">Your chats</p>
                <h2 aria-live="polite">{countLabel}</h2>
              </div>
            </div>

            <form
              className="history-search"
              role="search"
              onSubmit={(event) => event.preventDefault()}
            >
              <Search size={16} aria-hidden="true" />
              <input
                type="search"
                value={searchQuery}
                onChange={(event) => setSearchQuery(event.target.value)}
                placeholder="Search questions and answers..."
                aria-label="Search conversation history"
              />
            </form>

            <div className="history-list" aria-busy={isLoading}>
              {isLoading ? (
                <>
                  <span className="sr-only">Loading conversation history</span>
                  <div className="history-skeleton" aria-hidden="true">
                    {[0, 1, 2, 3].map((row) => (
                      <div className="history-skeleton-row" key={row}>
                        <span />
                        <span />
                        <span />
                      </div>
                    ))}
                  </div>
                </>
              ) : loadError ? (
                <div className="history-state error" role="alert">
                  <span className="history-state-icon">
                    <CircleAlert size={26} aria-hidden="true" />
                  </span>
                  <h3>Couldn&apos;t load history</h3>
                  <p>{loadError}</p>
                  <button
                    className="secondary-button"
                    type="button"
                    onClick={() => setReloadToken((token) => token + 1)}
                  >
                    <RotateCcw size={16} aria-hidden="true" />
                    Retry
                  </button>
                </div>
              ) : conversations.length === 0 ? (
                <div className="history-state">
                  <span className="history-state-icon">
                    <Inbox size={26} aria-hidden="true" />
                  </span>
                  <h3>No conversations yet</h3>
                  <p>Ask a question about your documents and it will show up here.</p>
                  <button className="primary-button" type="button" onClick={goToChat}>
                    Start chatting
                  </button>
                </div>
              ) : filteredConversations.length === 0 ? (
                <div className="history-state">
                  <span className="history-state-icon">
                    <SearchX size={26} aria-hidden="true" />
                  </span>
                  <h3>No matches for &ldquo;{searchQuery.trim()}&rdquo;</h3>
                  <p>Try a different keyword, or search for a phrase from an answer.</p>
                  <button
                    className="secondary-button"
                    type="button"
                    onClick={() => setSearchQuery("")}
                  >
                    Clear search
                  </button>
                </div>
              ) : (
                filteredConversations.map((conv) => {
                  const isSelected = String(conv.id) === selectedId;
                  return (
                    <button
                      key={conv.id}
                      type="button"
                      className={`conversation-item${isSelected ? " selected" : ""}`}
                      aria-current={isSelected ? "true" : undefined}
                      onClick={() => selectConversation(conv)}
                      title={
                        conv.conversationId
                          ? "Preview this conversation"
                          : "Preview this standalone question"
                      }
                    >
                      <span className="conversation-question">
                        <MessageSquareText size={16} aria-hidden="true" />
                        <span>{conv.question}</span>
                      </span>
                      <span className="conversation-meta">
                        <span className="conversation-time">
                          <Clock size={13} aria-hidden="true" />
                          <time dateTime={conv.timestamp}>{formatDate(conv.timestamp)}</time>
                        </span>
                        <span className="conversation-badge">
                          {conv.conversationId ? "Thread" : "Q&A"}
                        </span>
                      </span>
                      <span className="conversation-preview">{conv.answer}</span>
                    </button>
                  );
                })
              )}
            </div>
          </aside>

          <section className="history-preview">
            {selected ? (
              <article className="preview-card">
                <div className="preview-meta">
                  <span className="conversation-time">
                    <Clock size={14} aria-hidden="true" />
                    <time dateTime={selected.timestamp}>{formatDate(selected.timestamp)}</time>
                  </span>
                  <span className="conversation-badge">
                    {selected.conversationId ? "Conversation" : "Standalone Q&A"}
                  </span>
                </div>
                <h2 className="preview-question">{selected.question}</h2>
                <div className="preview-divider" />
                <p className="preview-answer">{selected.answer}</p>
                <footer className="preview-actions">
                  <button
                    className="primary-button"
                    type="button"
                    disabled={!selected.conversationId}
                    onClick={() => openInChat(selected)}
                  >
                    Continue in chat
                    <ChevronRight size={18} aria-hidden="true" />
                  </button>
                  <button className="secondary-button" type="button" onClick={goToChat}>
                    <ArrowLeft size={16} aria-hidden="true" />
                    Back to chat
                  </button>
                  {selected.conversationId ? null : (
                    <p className="preview-note">
                      One-off question — there is no thread to reopen, but you can ask a
                      follow-up in chat.
                    </p>
                  )}
                </footer>
              </article>
            ) : (
              <div className="empty-state">
                <span className="history-state-icon">
                  <MessageSquareText size={26} aria-hidden="true" />
                </span>
                <h3>Select a conversation</h3>
                <p>Choose an entry on the left to preview the full question and answer.</p>
                <button className="secondary-button" type="button" onClick={goToChat}>
                  <ArrowLeft size={16} aria-hidden="true" />
                  Back to chat
                </button>
              </div>
            )}
          </section>
        </div>
      )}
    </main>
  );
}
