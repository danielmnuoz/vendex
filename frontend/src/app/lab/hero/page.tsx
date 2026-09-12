"use client";

import Link from "next/link";
import { Suspense, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { ArrowRight, CalendarCheck, ListChecks, ScanSearch, Upload } from "lucide-react";
import { AsciiCreatureCanvas } from "@/components/ascii/AsciiCreatureCanvas";
import type { ToneOptions } from "@/components/ascii/engine";
import styles from "./hero.module.css";

/**
 * /lab/hero — landing-page layout experiments with the glyph Lugia.
 * Reuses the real landing classes from globals.css so what you see here is
 * what would ship. The production landing page (src/app/page.tsx) is untouched.
 *
 * Variants:
 *   beside   — copy left, Lugia large and off-centre right, card moved below
 *   backdrop — Lugia faded behind the whole hero, copy on top, card moved below
 *   band     — navy hero band, light glyphs, copy in white, card moved below
 */

type Variant = "beside" | "backdrop" | "band";

const steps = [
  { icon: Upload, title: "Bring your inventory", copy: "Import a CSV or add cards as you go. Scope stock to the shows where it will actually be available." },
  { icon: CalendarCheck, title: "Join the same event", copy: "VenDex only compares vendors who will be in the same room. No global marketplace noise." },
  { icon: ScanSearch, title: "See actionable overlaps", copy: "Know who wants what you have—and who has what you need—before the doors open." },
] as const;

const LUGIA = { src: "/lugia.png", motion: "flap" as const, columns: 160 };

/** Tone presets for the backdrop layout. ink = overall darkness, contrast = how
 *  far light areas fall off, gamma = keeps darks dark while lifting mid-tones. */
const TONES: Record<string, ToneOptions> = {
  soft:     { ink: 0.42, contrast: 0.35, gamma: 1.0 },
  balanced: { ink: 1.0,  contrast: 0.5,  gamma: 0.5 },
  punchy:   { ink: 0.78, contrast: 0.85, gamma: 1.5 },
  ink:      { ink: 1.0,  contrast: 1.0,  gamma: 1.8 },
};

function Nav({ light }: { light?: boolean }) {
  return (
    <header className={`landing-nav ${light ? styles.navLight : ""}`}>
      <Link className="brand" href="/" aria-label="VenDex home">
        <span className="brand-mark" aria-hidden="true">V</span>
        <span>VenDex</span>
      </Link>
      <nav aria-label="Account">
        <Link className="text-link" href="/login">Sign in</Link>
        <Link className={`button ${light ? "light" : "primary"}`} href="/signup">Start matching</Link>
      </nav>
    </header>
  );
}

function HeroCopy({ light }: { light?: boolean }) {
  return (
    <div className={`hero-copy ${light ? styles.copyLight : ""}`}>
      <p className="eyebrow">Built for convention vendors</p>
      <h1>Find the right booth before the doors open.</h1>
      <p className="hero-lede">VenDex turns inventory and buy lists into event-scoped opportunities, so your next deal starts with a plan instead of another lap around the hall.</p>
      <div className="hero-actions">
        <Link className={`button ${light ? "light" : "primary"} icon-label`} href="/signup">Create your vendor account <ArrowRight size={17} aria-hidden="true" /></Link>
        <a className={`button ${light ? styles.ghostLight : "secondary"}`} href="#plan">See how it works</a>
      </div>
      <div className="proof-row">
        <span><strong>Event-first</strong> matching</span>
        <span><strong>Private</strong> inventory search</span>
        <span><strong>Free</strong> to start</span>
      </div>
    </div>
  );
}

/** The existing hero card, unchanged, now living in its own section. */
function PlanCard() {
  return (
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
  );
}

function parseVariant(v: string | null): Variant {
  return v === "backdrop" || v === "band" ? v : "beside";
}

export default function HeroLabPage() {
  // useSearchParams needs a Suspense boundary for static rendering.
  return (
    <Suspense fallback={null}>
      <HeroLab />
    </Suspense>
  );
}

function HeroLab() {
  // Variant lives in the URL so layouts are deep-linkable: /lab/hero?v=band
  const router = useRouter();
  const variant = parseVariant(useSearchParams().get("v"));
  const setVariant = (v: Variant) => router.replace(`?v=${v}`, { scroll: false });
  const light = variant === "band";
  const [tone, setTone] = useState<ToneOptions>(TONES.balanced);
  const setToneKey = (key: keyof ToneOptions, value: number) => setTone((t) => ({ ...t, [key]: value }));

  return (
    <div className={`landing-shell ${styles[variant]}`}>
      <div className={styles.switcher} role="group" aria-label="Layout variant">
        {(["beside", "backdrop", "band"] as Variant[]).map((v) => (
          <button key={v} type="button" className={v === variant ? styles.on : ""} onClick={() => setVariant(v)}>{v}</button>
        ))}
      </div>

      {variant === "backdrop" ? (
        <div className={styles.tonePanel} aria-label="Tone controls">
          <div className={styles.toneRow}>
            {Object.entries(TONES).map(([name, preset]) => (
              <button
                key={name}
                type="button"
                className={preset === tone ? styles.on : ""}
                onClick={() => setTone(preset)}
              >
                {name}
              </button>
            ))}
          </div>
          {(
            [
              ["ink", "Ink", 0.1, 1, 0.01],
              ["contrast", "Contrast", 0, 1, 0.01],
              ["gamma", "Shadow curve", 0.5, 2.5, 0.05],
            ] as const
          ).map(([key, label, min, max, step]) => (
            <label key={key} className={styles.toneSlider}>
              <span>{label}</span>
              <input type="range" min={min} max={max} step={step} value={tone[key]} onChange={(e) => setToneKey(key, Number(e.target.value))} />
              <output>{tone[key].toFixed(2)}</output>
            </label>
          ))}
        </div>
      ) : null}

      <div className={styles.heroWrap}>
        <Nav light={light} />
        <main>
          <section className={`hero ${styles.hero}`}>
            <HeroCopy light={light} />
            <div className={styles.creature} aria-hidden={variant === "backdrop"}>
              <AsciiCreatureCanvas
                {...LUGIA}
                palette={light ? "light" : "brand"}
                fit={variant === "backdrop" ? 1 : 0.98}
                tone={variant === "backdrop" ? tone : undefined}
                className={styles.canvas}
                label="Lugia rendered as a field of glyphs"
              />
            </div>
          </section>
        </main>
      </div>

      <main>
        <section className={styles.planSection} id="plan">
          <div className={styles.planCopy}>
            <p className="eyebrow">What show day looks like</p>
            <h2>Every overlap, already on your plan.</h2>
            <p>Before the hall opens you know which booths to hit, what you’re selling them, and what you’re buying. No spreadsheets, no Discord scroll-back.</p>
          </div>
          <PlanCard />
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
