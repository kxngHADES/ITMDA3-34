"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { login } from "@/lib/api/auth";
import { saveSession } from "@/lib/auth/session";
import { Button } from "./Button";
import { EyeIcon, EyeOffIcon } from "./Icons";
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
        trailing={
          <button
            type="button"
            onClick={() => setShowPassword((v) => !v)}
            aria-label={showPassword ? "Hide password" : "Show password"}
            aria-pressed={showPassword}
            className="flex h-10 w-10 cursor-pointer items-center justify-center rounded-md text-[1.25rem] text-swiftq-muted hover:text-swiftq-text focus-visible:ring-2 focus-visible:ring-swiftq-focus"
          >
            {showPassword ? <EyeIcon /> : <EyeOffIcon />}
          </button>
        }
      />

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
