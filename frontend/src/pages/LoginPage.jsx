import { useEffect } from "react";
import { useNavigate, Link } from "react-router-dom";
import { Bot } from "lucide-react";
import AuthForm from "../components/AuthForm.jsx";

export default function LoginPage({ onAuthenticated }) {
  const navigate = useNavigate();

  useEffect(() => {
    const token = window.localStorage.getItem("accessToken");
    if (token) {
      navigate("/", { replace: true });
    }
  }, [navigate]);

  function handleAuthenticated(userData) {
    onAuthenticated(userData);
    navigate("/", { replace: true });
  }

  return (
    <main className="auth-shell">
      <section className="auth-brand">
        <div className="brand-mark">
          <Bot size={28} />
        </div>
        <h1>AI Document Assistant</h1>
        <p>Phase 1 workspace for authenticated PDF upload, indexing status, document chat, and citations.</p>
      </section>

      <section className="auth-panel" aria-label="Authentication">
        <div className="auth-page-header">
          <h2>Welcome back</h2>
          <p>Sign in to continue to your workspace</p>
        </div>
        <AuthForm mode="login" onAuthenticated={handleAuthenticated} />
        <p className="auth-switch">
          Don&apos;t have an account? <Link to="/register">Create account</Link>
        </p>
      </section>
    </main>
  );
}
