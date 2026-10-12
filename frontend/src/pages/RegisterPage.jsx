import { useEffect } from "react";
import { useNavigate, Link } from "react-router-dom";
import { Bot } from "lucide-react";
import AuthForm from "../components/AuthForm.jsx";

export default function RegisterPage({ onAuthenticated }) {
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
          <h2>Create account</h2>
          <p>Get started with your document workspace</p>
        </div>
        <AuthForm mode="register" onAuthenticated={handleAuthenticated} />
        <p className="auth-switch">
          Already have an account? <Link to="/login">Sign in</Link>
        </p>
      </section>
    </main>
  );
}
