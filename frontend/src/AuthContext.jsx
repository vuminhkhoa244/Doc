import { createContext, useCallback, useContext, useEffect, useRef, useState } from "react";
import { clearSession, getCurrentUser, login as loginApi, register as registerApi } from "./api.js";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [isRestoring, setIsRestoring] = useState(() => Boolean(window.localStorage.getItem("accessToken")));
  const [authModal, setAuthModal] = useState({ open: false, mode: "login" });
  const [pendingAction, setPendingAction] = useState(null);
  const pendingRef = useRef(null);

  useEffect(() => {
    let isMounted = true;
    const token = window.localStorage.getItem("accessToken");
    if (!token) {
      setIsRestoring(false);
      return () => { isMounted = false; };
    }

    getCurrentUser()
      .then((me) => {
        if (!isMounted) return;
        window.localStorage.setItem("user", JSON.stringify(me));
        setUser(me);
      })
      .catch(() => {
        if (!isMounted) return;
        clearSession();
        setUser(null);
      })
      .finally(() => {
        if (isMounted) setIsRestoring(false);
      });

    return () => { isMounted = false; };
  }, []);

  const openAuth = useCallback((mode = "login", action = null) => {
    pendingRef.current = action;
    setPendingAction(action);
    setAuthModal({ open: true, mode });
  }, []);

  const closeAuth = useCallback(() => {
    setAuthModal((prev) => ({ ...prev, open: false }));
  }, []);

  const authenticate = useCallback(async (mode, email, password) => {
    const result = mode === "login"
      ? await loginApi({ email, password })
      : await registerApi({ email, password });
    window.localStorage.setItem("accessToken", result.accessToken);
    window.localStorage.setItem("refreshToken", result.refreshToken);
    window.localStorage.setItem("user", JSON.stringify(result.user));
    setUser(result.user);
    return result.user;
  }, []);

  const consumePendingAction = useCallback(() => {
    const action = pendingRef.current;
    pendingRef.current = null;
    setPendingAction(null);
    return action;
  }, []);

  const logout = useCallback(() => {
    clearSession();
    setUser(null);
  }, []);

  return (
    <AuthContext.Provider
      value={{
        user,
        isRestoring,
        authModal,
        pendingAction,
        openAuth,
        closeAuth,
        authenticate,
        consumePendingAction,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
