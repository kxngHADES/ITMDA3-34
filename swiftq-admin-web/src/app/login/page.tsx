"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth/AuthProvider";
import { Button } from "@/components/ui/Button";
import { CheckboxField, TextField } from "@/components/ui/Field";
import { FullPageLoading } from "@/components/layout/FullPage";
import { focusFirstInvalid } from "@/lib/dom";
import { isValidEmail } from "@/lib/validation";

export default function LoginPage() {
  const { state, signIn } = useAuth();
  const router = useRouter();
  const formRef = useRef<HTMLFormElement>(null);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  // Once the API has accepted the email and password, move on.
  useEffect(() => {
    if (state.status === "ready") router.replace("/dashboard/");
    if (state.status === "denied") router.replace("/access-denied/");
  }, [state.status, router]);

  const emailError = !email.trim() ? "Enter your email address." : !isValidEmail(email) ? "Enter a valid email address." : undefined;
  const passwordError = !password ? "Enter your password." : undefined;

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitted(true);
    setFormError(null);
    if (emailError || passwordError) {
      focusFirstInvalid(formRef.current);
      return;
    }
    setBusy(true);
    try {
      await signIn(email, password);
      // Keep the button busy: the effect above redirects as soon as the account check finishes.
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "We couldn't sign you in. Try again in a moment.");
      setBusy(false);
    }
  }

  if (state.status === "loading" || state.status === "ready" || state.status === "denied") return <FullPageLoading />;

  return (
    <div className="login">
      <div className="card login-card">
        <div>
          <div className="wordmark" style={{ padding: 0 }}>SwiftQ</div>
          <h1>Sign in to Admin</h1>
          <p className="muted">For IT admins and clinic managers.</p>
        </div>
        <form ref={formRef} className="stack" onSubmit={onSubmit} noValidate>
          {formError && <div className="banner banner-error" role="alert">{formError}</div>}
          <TextField
            label="Email address"
            type="email"
            autoComplete="username"
            inputMode="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            error={submitted ? emailError : undefined}
          />
          <TextField
            label="Password"
            type={showPassword ? "text" : "password"}
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            error={submitted ? passwordError : undefined}
          />
          <CheckboxField label="Show password" checked={showPassword} onChange={(e) => setShowPassword(e.target.checked)} />
          <Button type="submit" variant="primary" size="xl" loading={busy} className="btn-block">Sign in</Button>
        </form>
      </div>
    </div>
  );
}
