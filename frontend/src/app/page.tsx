import Link from "next/link";
import { ArrowRight, CalendarCheck, ListChecks, ScanSearch, Upload } from "lucide-react";
import { AsciiCreatureCanvas } from "@/components/ascii/AsciiCreatureCanvas";

const steps = [
  { icon: Upload, title: "Bring your inventory", copy: "Import a CSV or add cards as you go. Scope stock to the shows where it will actually be available." },
  { icon: CalendarCheck, title: "Join the same event", copy: "VenDex only compares vendors who will be in the same room. No global marketplace noise." },
  { icon: ScanSearch, title: "See actionable overlaps", copy: "Know who wants what you have—and who has what you need—before the doors open." },
] as const;

/** Tone tuned by hand on /lab/hero: full ink, mid contrast, lifted shadows. */
const heroTone = { ink: 1, contrast: 0.5, gamma: 0.5 };

export default function Home() {
  return (
    <div className="landing-shell">
      <header className="landing-nav">
        <Link className="brand" href="/" aria-label="VenDex home">
          <span className="brand-mark" aria-hidden="true">V</span>
          <span>VenDex</span>
        </Link>
        <nav aria-label="Account">
          <Link className="text-link" href="/login">Sign in</Link>
          <Link className="button primary" href="/signup">Start matching</Link>
        </nav>
      </header>

      <main>
        <section className="hero hero-backdrop-layout">
          <div className="hero-backdrop" aria-hidden="true">
            <AsciiCreatureCanvas
              src="/lugia.png"
              columns={160}
              palette="brand"
              motion="flap"
              fit={1}
              tone={heroTone}
              className="hero-backdrop-canvas"
              label="Lugia rendered as a field of glyphs"
            />
          </div>
          <div className="hero-copy">
            <p className="eyebrow">Built for convention vendors</p>
            <h1>Find the right booth before the doors open.</h1>
            <p className="hero-lede">VenDex turns inventory and buy lists into event-scoped opportunities, so your next deal starts with a plan instead of another lap around the hall.</p>
            <div className="hero-actions">
              <Link className="button primary icon-label" href="/signup">Create your vendor account <ArrowRight size={17} aria-hidden="true" /></Link>
              <a className="button secondary" href="#plan">See how it works</a>
            </div>
            <div className="proof-row">
              <span><strong>Event-first</strong> matching</span>
              <span><strong>Private</strong> inventory search</span>
              <span><strong>Free</strong> to start</span>
            </div>
          </div>
        </section>

        <section className="plan-section" id="plan">
          <div className="plan-copy">
            <p className="eyebrow">What show day looks like</p>
            <h2>Every overlap, already on your plan.</h2>
            <p>Before the hall opens you know which booths to hit, what you’re selling them, and what you’re buying. No spreadsheets, no Discord scroll-back.</p>
          </div>
          <div className="hero-visual" aria-label="Example VenDex opportunity plan">
            <div className="hero-event-card">
              <p className="eyebrow">Collect-A-Con Dallas</p>
              <strong>8 opportunities found</strong>
              <span>Aug 22–23 · Dallas, TX</span>
            </div>
            <article className="hero-match one">
              <span className="direction-dot warm" />
              <div><small>You can sell</small><strong>Charizard ex</strong><span>Moonlight Collectibles · B-17</span></div>
              <b>$72</b>
            </article>
            <article className="hero-match two">
              <span className="direction-dot blue" />
              <div><small>You can buy</small><strong>Umbreon VMAX</strong><span>Northstar Cards · C-04</span></div>
              <b>$615</b>
            </article>
            <div className="hero-plan"><ListChecks size={20} aria-hidden="true" /><span><strong>5 saved to your event plan</strong><small>Ready for show day</small></span></div>
          </div>
        </section>

        <section className="how-section" id="how-it-works">
          <div className="section-heading">
            <p className="eyebrow">A shorter path to the deal</p>
            <h2>Coordinate before the convention gets loud.</h2>
          </div>
          <div className="step-grid">
            {steps.map(({ icon: Icon, title, copy }, index) => (
              <article className="step-card" key={title}>
                <span className="step-number">0{index + 1}</span>
                <Icon size={23} aria-hidden="true" />
                <h3>{title}</h3>
                <p>{copy}</p>
              </article>
            ))}
          </div>
        </section>

        <section className="landing-cta">
          <div><p className="eyebrow">Your next show starts here</p><h2>Spend less time hunting. Arrive with a route.</h2></div>
          <Link className="button light icon-label" href="/signup">Build your event plan <ArrowRight size={17} aria-hidden="true" /></Link>
        </section>
      </main>
      <footer className="landing-footer"><span>VenDex</span><span>Event-scoped inventory coordination for trading card vendors.</span></footer>
    </div>
  );
}
