import { LogOut, History, Plus } from "lucide-react";
import { useNavigate } from "react-router-dom";

export default function TopBar({ user, onLogout, onNewConversation, activeConversationTitle }) {
  const navigate = useNavigate();
  return (
    <header className="topbar">
      <div>
        <p className="eyebrow">Workspace</p>
        <h1>{activeConversationTitle || "AI Document Assistant"}</h1>
      </div>
      <div className="user-actions">
        {onNewConversation ? (
          <button className="icon-button" title="New chat" aria-label="New chat" onClick={onNewConversation}>
            <Plus size={18} />
          </button>
        ) : null}
        <button className="icon-button" title="Conversation History" aria-label="Conversation History" onClick={() => navigate("/history")}>
          <History size={18} />
        </button>
        <span>{user.email}</span>
        <button className="icon-button" title="Logout" aria-label="Logout" onClick={onLogout}>
          <LogOut size={18} />
        </button>
      </div>
    </header>
  );
}
