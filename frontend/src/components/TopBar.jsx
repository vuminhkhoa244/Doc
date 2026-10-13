import { LogOut, History, Plus, LogIn, UserPlus } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../AuthContext.jsx";

export default function TopBar({ onNewConversation, activeConversationTitle }) {
  const { user, openAuth, logout } = useAuth();
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
        {user ? (
          <button className="icon-button" title="Conversation History" aria-label="Conversation History" onClick={() => navigate("/history")}>
            <History size={18} />
          </button>
        ) : null}
        {user ? (
          <>
            <span>{user.email}</span>
            <button className="icon-button" title="Logout" aria-label="Logout" onClick={logout}>
              <LogOut size={18} />
            </button>
          </>
        ) : (
          <>
            <button className="ghost-button" onClick={() => openAuth("login")}>
              <LogIn size={16} /> Log in
            </button>
            <button className="primary-button" onClick={() => openAuth("register")}>
              <UserPlus size={16} /> Sign up
            </button>
          </>
        )}
      </div>
    </header>
  );
}
