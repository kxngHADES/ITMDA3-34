"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { login } from "@/lib/api/auth";
import { saveSession } from "@/lib/auth/session";
import { Button } from "./Button";
import { TextField } from "./TextField";

export function LoginForm() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [formError, setFormError] = useState("");
  const [fieldErrors, setFieldErrors] = useState<{
    email?: string;
    password?: string;
  }>({});
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setFormError("");

    const errors: typeof fieldErrors = {};
    if (!email.trim()) errors.email = "Enter your work email.";
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim()))
      errors.email = "Enter a valid email address.";
    if (!password) errors.password = "Enter your password.";
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    setLoading(true);
    try {
      const session = await login({ email, password });
      saveSession(session);
      router.push("/");
    } catch (err) {
      setFormError(
        err instanceof Error ? err.message : "Sign-in failed. Try again.",
      );
    } finally {
      setLoading(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate>
      {formError && (
        <div
          role="alert"
          className="mb-[14px] flex items-start gap-2 rounded-[var(--swiftq-radius)] border border-swiftq-danger-border bg-swiftq-danger-bg px-3.5 py-3 text-[0.88rem] font-semibold text-swiftq-danger-ink"
        >
          {formError}
        </div>
      )}

      <TextField
        id="login-email"
        label="Work email address"
        type="email"
        autoComplete="username"
        placeholder="name@clinic.za"
        hint="Use your clinic-issued email."
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        error={fieldErrors.email}
        required
      />

      <TextField
        id="login-password"
        label="Password"
        type={showPassword ? "text" : "password"}
        autoComplete="current-password"
        placeholder="••••••••"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        error={fieldErrors.password}
        required
      />

      <label className="mb-[14px] flex cursor-pointer items-center gap-2 text-[0.86rem] text-swiftq-muted">
        <input
          type="checkbox"
          checked={showPassword}
          onChange={(e) => setShowPassword(e.target.checked)}
          className="h-4 w-4 accent-swiftq-accent"
        />
        Show password
      </label>

      <Button
        type="submit"
        variant="primary"
        disabled={loading}
        className="w-full"
      >
        {loading ? "Signing in…" : "Sign in"}
      </Button>
    </form>
  );
}
