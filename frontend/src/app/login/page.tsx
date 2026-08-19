import Link from "next/link";
import { AuthForm } from "@/components/auth-form";

export default function LoginPage() {
  return (
    <main className="auth-shell">
      <section className="auth-card">
        <Link className="brand" href="/"><span className="brand-mark" aria-hidden="true">V</span><span>VenDex</span></Link>
        <div className="auth-heading"><p className="eyebrow">Welcome back</p><h1>Open your event plan.</h1><p>Sign in to manage inventory, buy lists, and live show opportunities.</p></div>
        <AuthForm mode="login" />
      </section>
      <aside className="auth-aside"><p className="eyebrow">Plan before show day</p><blockquote>“Stop walking the floor hoping the right card is at the next booth.”</blockquote><span>Inventory coordination, scoped to the event.</span></aside>
    </main>
  );
}
