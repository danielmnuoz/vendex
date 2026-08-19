import Link from "next/link";
import { AuthForm } from "@/components/auth-form";

export default function SignupPage() {
  return (
    <main className="auth-shell">
      <section className="auth-card">
        <Link className="brand" href="/"><span className="brand-mark" aria-hidden="true">V</span><span>VenDex</span></Link>
        <div className="auth-heading"><p className="eyebrow">Vendor onboarding</p><h1>Build your show-day edge.</h1><p>Create a private vendor workspace. Your first two moves: join an event and import inventory.</p></div>
        <AuthForm mode="register" />
      </section>
      <aside className="auth-aside signup"><p className="eyebrow">What happens next</p><ol><li><strong>01</strong><span>Register for your next convention.</span></li><li><strong>02</strong><span>Import stock or start your buy list.</span></li><li><strong>03</strong><span>Save the best overlaps to your plan.</span></li></ol></aside>
    </main>
  );
}
