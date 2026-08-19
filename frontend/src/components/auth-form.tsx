"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";

type Mode = "login" | "register";

export function AuthForm({ mode }: { mode: Mode }) {
  const router = useRouter();
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");
    const form = new FormData(event.currentTarget);
    const body = mode === "login"
      ? { email: form.get("email"), password: form.get("password") }
      : {
          email: form.get("email"),
          password: form.get("password"),
          shopName: form.get("shopName"),
          city: form.get("city"),
          state: form.get("state"),
        };
    const response = await fetch(`/api/session/${mode === "login" ? "login" : "register"}`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(body),
    });
    if (!response.ok) {
      const payload = await response.json().catch(() => ({})) as { message?: string };
      setError(payload.message ?? "We could not complete that request.");
      setBusy(false);
      return;
    }
    router.replace("/dashboard");
    router.refresh();
  }

  const register = mode === "register";
  return (
    <form className="auth-form" onSubmit={submit}>
      {register ? (
        <>
          <label className="field"><span>Shop name</span><input name="shopName" required maxLength={120} autoComplete="organization" placeholder="Moonlight Collectibles" /></label>
          <div className="field-grid two">
            <label className="field"><span>City</span><input name="city" maxLength={120} autoComplete="address-level2" placeholder="Dallas" /></label>
            <label className="field"><span>State</span><input name="state" maxLength={64} autoComplete="address-level1" placeholder="TX" /></label>
          </div>
        </>
      ) : null}
      <label className="field"><span>Email</span><input name="email" type="email" required autoComplete="email" placeholder="you@yourshop.com" /></label>
      <label className="field"><span>Password</span><input name="password" type="password" minLength={register ? 8 : 1} required autoComplete={register ? "new-password" : "current-password"} placeholder={register ? "At least 8 characters" : "Your password"} /></label>
      {error ? <p className="form-error" role="alert">{error}</p> : null}
      <button className="button primary wide" disabled={busy} type="submit">{busy ? "Working…" : register ? "Create vendor account" : "Sign in"}</button>
      <p className="auth-switch">{register ? "Already have a workspace?" : "New to VenDex?"} <Link href={register ? "/login" : "/signup"}>{register ? "Sign in" : "Create an account"}</Link></p>
    </form>
  );
}
