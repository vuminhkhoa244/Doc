import { useEffect, useMemo, useRef, useState } from "react";
import {
  askQuestionStream,
  createConversation,
  deleteConversation as deleteConversationApi,
  deleteDocument,
  downloadDocument,
  getConversation,
  listConversations,
  listDocuments,
  reindexDocument,
  renameDocument,
  searchDocuments,
  uploadDocument,
} from "../api.js";
import TopBar from "../components/TopBar.jsx";
import DocumentPanel from "../components/DocumentPanel.jsx";
import ChatPanel from "../components/ChatPanel.jsx";

const WELCOME_MESSAGE = {
  id: "welcome",
  role: "assistant",
  content: "Upload a PDF, DOCX, Markdown, or TXT file, wait for indexing, then ask a question about your documents.",
  sources: [],
};

const RESUME_KEY = "ai-doc-assistant-resume-conversation";
const POLL_INTERVAL_MS = 3000;

function toUiMessages(messages) {
  const uiMessages = [WELCOME_MESSAGE];
  for (const message of messages ?? []) {
    uiMessages.push({
      id: `user-${message.id}`,
      role: "user",
      content: message.question,
      sources: [],
      toolUsed: message.toolUsed,
    });
    uiMessages.push({
      id: `assistant-${message.id}`,
      role: "assistant",
      content: message.answer,
      sources: [],
      toolUsed: message.toolUsed,
    });
  }
  return uiMessages;
}

export default function DashboardPage({ user, onLogout }) {
  const [conversations, setConversations] = useState([]);
  const [activeConversationId, setActiveConversationId] = useState(null);
  const [isLoadingMessages, setIsLoadingMessages] = useState(false);
  const [documents, setDocuments] = useState([]);
  const [activeDocumentId, setActiveDocumentId] = useState("");
  const [activeDocumentIds, setActiveDocumentIds] = useState([]);
  const [isLoadingDocuments, setIsLoadingDocuments] = useState(false);
  const [isAsking, setIsAsking] = useState(false);
  const [searchResults, setSearchResults] = useState([]);
  const [isSearching, setIsSearching] = useState(false);
  const [selectedTool, setSelectedTool] = useState("rag");
  const [errorMessage, setErrorMessage] = useState("");
  const resumeChecked = useRef(false);

  const activeConversation = conversations.find((c) => c.id === activeConversationId);
  const messages = activeConversation?.messages ?? [WELCOME_MESSAGE];

  const activeDocument = useMemo(
    () => documents.find((document) => document.id === activeDocumentId),
    [activeDocumentId, documents],
  );

  const hasPendingIndexing = documents.some(
    (document) => document.status === "indexing" || document.status === "INDEXING",
  );

  useEffect(() => {
    let isMounted = true;
    setIsLoadingDocuments(true);
    Promise.all([listDocuments(), listConversations()])
      .then(([documentResult, conversationResult]) => {
        if (!isMounted) return;
        setDocuments(documentResult);
        setActiveDocumentId(documentResult[0]?.id ?? "");
        setActiveDocumentIds(documentResult[0]?.id ? [documentResult[0].id] : []);
        setConversations(
          conversationResult.map((conversation) => ({ ...conversation, messages: null })),
        );
      })
      .catch((error) => {
        if (isMounted) setErrorMessage(error.message);
      })
      .finally(() => {
        if (isMounted) setIsLoadingDocuments(false);
      });

    return () => {
      isMounted = false;
    };
  }, []);

  useEffect(() => {
    if (resumeChecked.current) return;
    resumeChecked.current = true;
    const raw = window.localStorage.getItem(RESUME_KEY);
    if (!raw) return;
    window.localStorage.removeItem(RESUME_KEY);
    try {
      const payload = JSON.parse(raw);
      if (payload?.conversationId) {
        switchConversation(payload.conversationId);
      }
    } catch {
      // ignore malformed resume payload
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (!hasPendingIndexing) return undefined;
    const interval = window.setInterval(() => {
      listDocuments()
        .then(setDocuments)
        .catch(() => {
          // polling failures are non-fatal; next tick retries
        });
    }, POLL_INTERVAL_MS);
    return () => window.clearInterval(interval);
  }, [hasPendingIndexing]);

  async function refreshDocuments() {
    const result = await listDocuments();
    setDocuments(result);
    return result;
  }

  async function handleUpload(file) {
    setErrorMessage("");
    try {
      const document = await uploadDocument(file);
      setDocuments((current) => [document, ...current]);
      setActiveDocumentId(document.id);
      setActiveDocumentIds([document.id]);
    } catch (error) {
      setErrorMessage(error.message);
    }
  }

  async function handleDelete(documentId) {
    setErrorMessage("");
    try {
      await deleteDocument(documentId);
      setDocuments((current) => current.filter((document) => document.id !== documentId));
      setActiveDocumentId((current) => (current === documentId ? "" : current));
      setActiveDocumentIds((current) => current.filter((id) => id !== documentId));
    } catch (error) {
      setErrorMessage(error.message);
    }
  }

  async function handleRename(documentId, filename) {
    setErrorMessage("");
    try {
      const updated = await renameDocument(documentId, filename);
      setDocuments((current) =>
        current.map((document) => (document.id === documentId ? { ...document, ...updated } : document)),
      );
    } catch (error) {
      setErrorMessage(error.message);
    }
  }

  async function handleDownload(documentId, filename) {
    setErrorMessage("");
    try {
      await downloadDocument(documentId, filename);
    } catch (error) {
      setErrorMessage(error.message);
    }
  }

  async function handleReindex(documentId) {
    setErrorMessage("");
    try {
      const updated = await reindexDocument(documentId);
      setDocuments((current) =>
        current.map((document) => (document.id === documentId ? updated : document)),
      );
    } catch (error) {
      setErrorMessage(error.message);
    }
  }

  async function createNewConversation() {
    setErrorMessage("");
    try {
      const created = await createConversation("New chat");
      setConversations((current) => [{ ...created, messages: [WELCOME_MESSAGE] }, ...current]);
      setActiveConversationId(created.id);
    } catch (error) {
      setErrorMessage(error.message);
    }
  }

  async function switchConversation(id) {
    setActiveConversationId(id);
    const existing = conversations.find((c) => c.id === id);
    if (existing?.messages !== null && existing?.messages !== undefined) return;

    setIsLoadingMessages(true);
    try {
      const detail = await getConversation(id);
      setConversations((current) =>
        current.map((conversation) =>
          conversation.id === id
            ? { ...conversation, title: detail.title, messages: toUiMessages(detail.messages) }
            : conversation,
        ),
      );
    } catch (error) {
      setErrorMessage(error.message);
    } finally {
      setIsLoadingMessages(false);
    }
  }

  async function handleDeleteConversation(id) {
    setErrorMessage("");
    try {
      await deleteConversationApi(id);
      setConversations((current) => current.filter((c) => c.id !== id));
      if (activeConversationId === id) {
        setConversations((current) => {
          setActiveConversationId(current[0]?.id ?? null);
          return current;
        });
      }
    } catch (error) {
      setErrorMessage(error.message);
    }
  }

  async function handleAsk(question, options = {}) {
    const { tool = "rag", documentIds = [] } = options;
    setErrorMessage("");

    let conversationId = activeConversationId;
    if (!conversationId) {
      try {
        const created = await createConversation(question.slice(0, 60));
        conversationId = created.id;
        setConversations((current) => [
          { ...created, messages: [WELCOME_MESSAGE] },
          ...current,
        ]);
        setActiveConversationId(created.id);
      } catch (error) {
        setErrorMessage(error.message);
        return;
      }
    }

    const userMessage = {
      id: `user-${Date.now()}`,
      role: "user",
      content: question,
      sources: [],
      toolUsed: tool,
    };
    const assistantId = `assistant-${Date.now()}`;
    const assistantPlaceholder = {
      id: assistantId,
      role: "assistant",
      content: "",
      sources: [],
      toolUsed: tool,
      streaming: true,
    };

    setConversations((current) =>
      current.map((c) =>
        c.id === conversationId
          ? {
              ...c,
              messages: [...(c.messages ?? [WELCOME_MESSAGE]), userMessage, assistantPlaceholder],
            }
          : c,
      ),
    );
    setIsAsking(true);

    const updateAssistant = (updater) =>
      setConversations((current) =>
        current.map((c) =>
          c.id === conversationId
            ? {
                ...c,
                messages: (c.messages ?? [WELCOME_MESSAGE]).map((m) =>
                  m.id === assistantId ? updater(m) : m,
                ),
              }
            : c,
        ),
      );

    try {
      const final = await askQuestionStream(question, {
        tool,
        documentIds,
        conversationId,
        onToken: (token) => {
          updateAssistant((message) => ({ ...message, content: message.content + token }));
        },
      });
      updateAssistant(() => ({
        id: assistantId,
        role: "assistant",
        content: final.answer,
        sources: final.sources ?? [],
        toolUsed: final.toolUsed ?? tool,
      }));
    } catch (error) {
      setErrorMessage(error.message);
      setConversations((current) =>
        current.map((c) =>
          c.id === conversationId
            ? {
                ...c,
                messages: (c.messages ?? [WELCOME_MESSAGE]).filter(
                  (m) => m.id !== userMessage.id && m.id !== assistantId,
                ),
              }
            : c,
        ),
      );
    } finally {
      setIsAsking(false);
    }
  }

  function handleSelectDocument(documentId) {
    setActiveDocumentId(documentId);
  }

  function handleToggleDocument(documentId, checked) {
    setActiveDocumentIds((current) =>
      checked ? [...current, documentId] : current.filter((id) => id !== documentId)
    );
  }

  function handleToolChange(tool) {
    setSelectedTool(tool);
  }

  async function handleSearch(filters) {
    setErrorMessage("");
    setIsSearching(true);
    try {
      const response = await searchDocuments(filters);
      setSearchResults(response.results ?? []);
    } catch (error) {
      setErrorMessage(error.message);
    } finally {
      setIsSearching(false);
    }
  }

  return (
    <main className="app-shell">
      <TopBar
        user={user}
        onLogout={onLogout}
        onNewConversation={createNewConversation}
        activeConversationTitle={activeConversation?.title}
      />
      {errorMessage ? (
        <div className="error-banner" role="alert">
          <span>{errorMessage}</span>
          <button type="button" onClick={() => setErrorMessage("")} aria-label="Dismiss error">
            ×
          </button>
        </div>
      ) : null}
      <div className="conversation-toolbar">
        <button className="primary-button" type="button" onClick={createNewConversation}>
          New chat
        </button>
        <div className="conversation-list" role="tablist">
          {conversations.map((conv) => (
            <button
              key={conv.id}
              role="tab"
              aria-selected={conv.id === activeConversationId}
              className={`conversation-tab ${conv.id === activeConversationId ? "active" : ""}`}
              onClick={() => switchConversation(conv.id)}
              title={conv.title}
            >
              <span>{conv.title}</span>
              <span
                className="conversation-delete"
                role="button"
                tabIndex={0}
                aria-label={`Delete ${conv.title}`}
                onClick={(event) => {
                  event.stopPropagation();
                  handleDeleteConversation(conv.id);
                }}
              >
                ×
              </span>
            </button>
          ))}
        </div>
      </div>
      <div className="workspace">
        <DocumentPanel
          documents={documents}
          activeDocumentId={activeDocumentId}
          onSelect={handleSelectDocument}
          onUpload={handleUpload}
          onDelete={handleDelete}
          onRename={handleRename}
          onDownload={handleDownload}
          onReindex={handleReindex}
          onSearch={handleSearch}
          searchResults={searchResults}
          isSearching={isSearching}
          isLoading={isLoadingDocuments}
          onRefresh={refreshDocuments}
        />
        <ChatPanel
          activeDocument={activeDocument}
          documents={documents}
          messages={isLoadingMessages ? [WELCOME_MESSAGE] : messages}
          onAsk={handleAsk}
          isAsking={isAsking}
          onSelectDocument={handleSelectDocument}
          activeDocumentIds={activeDocumentIds}
          onToggleDocument={handleToggleDocument}
          selectedTool={selectedTool}
          onToolChange={handleToolChange}
        />
      </div>
    </main>
  );
}
