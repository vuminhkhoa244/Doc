import { useState, useEffect, useRef } from "react";
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
const GOOGLE_CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID || "";

function loadGoogleScript() {
  if (window.google?.accounts?.id) return Promise.resolve();
  if (document.getElementById("gis-script")) {
    return new Promise((resolve) => {
      const check = setInterval(() => {
        if (window.google?.accounts?.id) {
          clearInterval(check);
          resolve();
        }
      }, 100);
    });
  }
  return new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.id = "gis-script";
    script.src = "https://accounts.google.com/gsi/client";
    script.async = true;
    script.defer = true;
    script.onload = resolve;
    script.onerror = reject;
    document.head.appendChild(script);
  });
}

export default function AuthModal() {
  const { authModal, closeAuth, authenticate, loginWithGoogle } = useAuth();
  const [mode, setMode] = useState(authModal.mode);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const googleButtonRef = useRef(null);

  const googleEnabled = Boolean(GOOGLE_CLIENT_ID);

  useEffect(() => {
    if (!authModal.open) return;
    setMode(authModal.mode);
    setEmail("");
    setPassword("");
    setError("");
    setIsSubmitting(false);
  }, [authModal.open, authModal.mode]);

  useEffect(() => {
    if (!authModal.open || !googleEnabled) return;
    let cancelled = false;
    loadGoogleScript().then(() => {
      if (cancelled || !window.google?.accounts?.id || !googleButtonRef.current) return;
      window.google.accounts.id.initialize({
        client_id: GOOGLE_CLIENT_ID,
        callback: async (response) => {
          try {
            await loginWithGoogle(response.credential);
            closeAuth();
          } catch (err) {
            setError(err.message);
          }
        },
      });
      window.google.accounts.id.renderButton(googleButtonRef.current, {
        theme: "outline",
        size: "large",
        width: 320,
        text: "continue_with",
        shape: "rectangular",
      });
    }).catch(() => {});
    return () => { cancelled = true; };
  }, [authModal.open, googleEnabled, loginWithGoogle, closeAuth]);

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

        {googleEnabled ? (
          <>
            <div ref={googleButtonRef} className="google-button-wrapper" />
            <div className="auth-divider"><span>or</span></div>
          </>
        ) : null}

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
