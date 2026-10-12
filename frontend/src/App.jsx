import { useEffect, useState } from "react";
import { Routes, Route, Navigate } from "react-router-dom";
import { clearSession, getCurrentUser } from "./api.js";
import LoginPage from "./pages/LoginPage.jsx";
import RegisterPage from "./pages/RegisterPage.jsx";
import DashboardPage from "./pages/DashboardPage.jsx";
import ConversationHistoryPage from "./pages/ConversationHistoryPage.jsx";

export default function App() {
  const [user, setUser] = useState(null);
  const [isRestoring, setIsRestoring] = useState(() => Boolean(window.localStorage.getItem("accessToken")));

  useEffect(() => {
    let isMounted = true;
    const token = window.localStorage.getItem("accessToken");
    if (!token) {
      setIsRestoring(false);
      return () => {
        isMounted = false;
      };
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

    return () => {
      isMounted = false;
    };
  }, []);

  function handleAuthenticated(userData) {
    setUser(userData);
  }

  function handleLogout() {
    clearSession();
    setUser(null);
  }

  if (isRestoring) {
    return <div className="loading-placeholder">Restoring session...</div>;
  }

  return (
    <Routes>
      <Route path="/login" element={<LoginPage onAuthenticated={handleAuthenticated} />} />
      <Route path="/register" element={<RegisterPage onAuthenticated={handleAuthenticated} />} />
      <Route
        path="/"
        element={
          user ? (
            <DashboardPage user={user} onLogout={handleLogout} />
          ) : (
            <Navigate to="/login" replace />
          )
        }
      />
      <Route
        path="/history"
        element={
          user ? (
            <ConversationHistoryPage user={user} onLogout={handleLogout} />
          ) : (
            <Navigate to="/login" replace />
          )
        }
      />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
