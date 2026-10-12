import { useState } from "react";
import { ShieldCheck } from "lucide-react";
import { login as loginApi, register as registerApi } from "../api.js";

export default function AuthForm({ mode, onAuthenticated }) {
  const [email, setEmail] = useState("demo@example.com");
  const [password, setPassword] = useState("password");
  const [error, setError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    setError("");
    setIsSubmitting(true);

    try {
      const result =
        mode === "login"
          ? await loginApi({ email, password })
          : await registerApi({ email, password });
      window.localStorage.setItem("accessToken", result.accessToken);
      window.localStorage.setItem("refreshToken", result.refreshToken);
      onAuthenticated(result.user);
    } catch (submitError) {
      setError(submitError.message);
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit}>
      <label>
        Email
        <input
          type="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          required
        />
      </label>

      <label>
        Password
        <input
          type="password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          minLength={6}
          required
        />
      </label>

      {error ? <p className="form-error">{error}</p> : null}

      <button className="primary-button" type="submit" disabled={isSubmitting}>
        <ShieldCheck size={18} />
        {isSubmitting
          ? "Working..."
          : mode === "login"
            ? "Login"
            : "Create account"}
      </button>
    </form>
  );
}
