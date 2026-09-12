"use client";

import { useEffect, useRef, useState } from "react";
import { AsciiCreature, type Charset, type Motion, type Palette } from "@/components/ascii/engine";
import styles from "./lab.module.css";

/**
 * /lab/ascii — experiment route. Not linked from anywhere; the landing page
 * is untouched. Renders a Pokémon silhouette as an animated glyph field so
 * we can decide whether (and where) it belongs on the marketing page.
 */

const CREATURES = {
  rayquaza: {
    label: "Rayquaza",
    src: "/lab/rayquaza.png",
    hue: { base: "#1f8a5b", accent: "#d4a017" },
    columns: 96,
    motion: "ripple" as Motion,
    title: "Find the right booth before the doors open.",
    copy: "Don't lap the hall hoping the right card is at the next table. Match inventory to buy lists before the show.",
  },
  lugia: {
    label: "Lugia",
    src: "/lugia.png",
    hue: { base: "#4b5ec9", accent: "#8a7dff" },
    columns: 96,
    motion: "flap" as Motion,
    title: "See the whole floor at once.",
    copy: "Every vendor in the room, every overlap, scoped to the event you're actually attending.",
  },
} as const;

type CreatureKey = keyof typeof CREATURES;

export default function AsciiLabPage() {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const engineRef = useRef<AsciiCreature | null>(null);
  const imgRef = useRef<HTMLImageElement | null>(null);
  const [creature, setCreature] = useState<CreatureKey>("lugia");
  const [palette, setPalette] = useState<Palette>("sampled");
  const [charset, setCharset] = useState<Charset>("binary");
  const [density, setDensity] = useState<number>(CREATURES.lugia.columns);

  // Boot the engine once.
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const fontFamily =
      getComputedStyle(document.body).getPropertyValue("--font-mono").trim() || "monospace";
    const engine = new AsciiCreature(canvas, {
      columns: density,
      palette,
      charset,
      hue: CREATURES[creature].hue,
      fontFamily,
      motion: CREATURES[creature].motion,
    });
    engineRef.current = engine;
    engine.play();

    const onResize = () => engine.layout();
    const onMove = (e: PointerEvent) => {
      const r = canvas.getBoundingClientRect();
      engine.setPointer(e.clientX - r.left, e.clientY - r.top, true);
    };
    const onLeave = () => engine.setPointer(0, 0, false);
    window.addEventListener("resize", onResize);
    canvas.addEventListener("pointermove", onMove);
    canvas.addEventListener("pointerleave", onLeave);
    return () => {
      engine.stop();
      window.removeEventListener("resize", onResize);
      canvas.removeEventListener("pointermove", onMove);
      canvas.removeEventListener("pointerleave", onLeave);
    };
    // Mount-only: later option changes go through setOptions below.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Swap creature: load the image, sample, and replay the assembly.
  useEffect(() => {
    const engine = engineRef.current;
    if (!engine) return;
    const spec = CREATURES[creature];
    const img = new Image();
    img.src = spec.src;
    img.decode().then(() => {
      imgRef.current = img;
      // Each creature has a density where its silhouette reads best.
      engine.setOptions({ hue: spec.hue, columns: spec.columns, motion: spec.motion });
      setDensity(spec.columns);
      engine.load(img);
    });
  }, [creature]);

  useEffect(() => {
    engineRef.current?.setOptions({ palette, charset, columns: density }, imgRef.current ?? undefined);
  }, [palette, charset, density]);

  const spec = CREATURES[creature];

  return (
    <main className={styles.shell}>
      <section className={styles.card} aria-label="ASCII creature experiment">
        <span className={`${styles.corner} ${styles.tl}`} aria-hidden="true" />
        <span className={`${styles.corner} ${styles.tr}`} aria-hidden="true" />
        <span className={`${styles.corner} ${styles.bl}`} aria-hidden="true" />
        <span className={`${styles.corner} ${styles.br}`} aria-hidden="true" />
        <canvas ref={canvasRef} className={styles.canvas} aria-label={`${spec.label} rendered as glyphs`} />
        <footer className={styles.caption}>
          <div>
            <h1>{spec.title}</h1>
            <p>{spec.copy}</p>
          </div>
          <button className={styles.replay} type="button" onClick={() => engineRef.current?.scatter()} title="Replay assembly">
            ↻
          </button>
        </footer>
      </section>

      <aside className={styles.controls} aria-label="Experiment controls">
        <div className={styles.group}>
          <span className={styles.groupLabel}>Creature</span>
          {(Object.keys(CREATURES) as CreatureKey[]).map((key) => (
            <button key={key} type="button" className={key === creature ? styles.active : ""} onClick={() => setCreature(key)}>
              {CREATURES[key].label}
            </button>
          ))}
        </div>
        <div className={styles.group}>
          <span className={styles.groupLabel}>Palette</span>
          {(["mono", "sampled", "brand"] as Palette[]).map((p) => (
            <button key={p} type="button" className={p === palette ? styles.active : ""} onClick={() => setPalette(p)}>
              {p}
            </button>
          ))}
        </div>
        <div className={styles.group}>
          <span className={styles.groupLabel}>Glyphs</span>
          {(["binary", "digits", "glyphs"] as Charset[]).map((c) => (
            <button key={c} type="button" className={c === charset ? styles.active : ""} onClick={() => setCharset(c)}>
              {c}
            </button>
          ))}
        </div>
        <div className={styles.group}>
          <span className={styles.groupLabel}>Density</span>
          {[64, 96, 128, 160].map((d) => (
            <button key={d} type="button" className={d === density ? styles.active : ""} onClick={() => setDensity(d)}>
              {d}
            </button>
          ))}
        </div>
        <p className={styles.hint}>Glyphs only change when their cell moves: wing beats, drift, or your cursor. Lab route only; the landing page is unchanged.</p>
      </aside>
    </main>
  );
}
