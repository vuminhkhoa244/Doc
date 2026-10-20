import { Routes, Route, Navigate } from "react-router-dom";
import { Analytics } from "@vercel/analytics/react";
import { AuthProvider, useAuth } from "./AuthContext.jsx";
import AuthModal from "./components/AuthModal.jsx";
import DashboardPage from "./pages/DashboardPage.jsx";
import ConversationHistoryPage from "./pages/ConversationHistoryPage.jsx";

function AppRoutes() {
  const { isRestoring } = useAuth();

  if (isRestoring) {
    return <div className="loading-placeholder">Restoring session...</div>;
  }

  return (
    <>
      <Routes>
        <Route path="/" element={<DashboardPage />} />
        <Route path="/history" element={<ConversationHistoryPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
      <AuthModal />
    </>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <AppRoutes />
      <Analytics />
    </AuthProvider>
  );
}
