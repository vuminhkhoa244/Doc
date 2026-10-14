import { useState, useEffect } from "react";
import { X, ShieldCheck } from "lucide-react";
import { useAuth } from "../AuthContext.jsx";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogClose,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";

const BENEFITS = "Save your chat history, upload documents, and get answers grounded in your files.";

export default function AuthModal() {
  const { authModal, closeAuth, authenticate } = useAuth();
  const [mode, setMode] = useState(authModal.mode);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (authModal.open) {
      setMode(authModal.mode);
      setEmail("");
      setPassword("");
      setError("");
      setIsSubmitting(false);
    }
  }, [authModal.open, authModal.mode]);

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    setIsSubmitting(true);
    try {
      await authenticate(mode, email, password);
      closeAuth();
    } catch (err) {
      setError(err.message);
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Dialog
      open={authModal.open}
      onOpenChange={(open) => {
        if (!open) closeAuth();
      }}
    >
      <DialogContent
        className="auth-modal flex flex-col gap-0 border-0"
        overlayClassName="auth-modal-overlay"
        showCloseButton={false}
      >
        <DialogClose asChild>
          <button className="auth-modal-close" aria-label="Close" type="button">
            <X size={18} />
          </button>
        </DialogClose>

        <DialogHeader className="auth-modal-header">
          <DialogTitle>{mode === "login" ? "Welcome back" : "Create your account"}</DialogTitle>
          <DialogDescription>{BENEFITS}</DialogDescription>
        </DialogHeader>

        <Button variant="outline" type="button" disabled className="google-button" title="Coming soon">
          <svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true">
            <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 0 1-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z"/>
            <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
            <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/>
            <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/>
          </svg>
          Continue with Google
          <span className="coming-soon-tag">Coming soon</span>
        </Button>

        <div className="auth-divider"><span>or</span></div>

        <form onSubmit={handleSubmit} className="auth-modal-form">
          <label>
            Email
            <Input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@example.com"
              required
              autoFocus
            />
          </label>
          <label>
            Password
            <Input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              minLength={6}
              required
            />
          </label>

          {error ? <p className="form-error">{error}</p> : null}

          <Button className="primary-button auth-submit" type="submit" disabled={isSubmitting}>
            <ShieldCheck size={18} />
            {isSubmitting ? "Working..." : mode === "login" ? "Log in" : "Sign up"}
          </Button>
        </form>

        <p className="auth-modal-toggle">
          {mode === "login" ? (
            <>Don&apos;t have an account? <button type="button" onClick={() => setMode("register")}>Sign up</button></>
          ) : (
            <>Already have an account? <button type="button" onClick={() => setMode("login")}>Log in</button></>
          )}
        </p>
      </DialogContent>
    </Dialog>
  );
}
