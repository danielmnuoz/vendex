"use client";

import { useEffect, useRef } from "react";
import { AsciiCreature, type Charset, type Motion, type Palette, type ToneOptions } from "./engine";

export interface AsciiCreatureCanvasProps {
  src: string;
  columns: number;
  palette: Palette;
  charset?: Charset;
  motion: Motion;
  hue?: { base: string; accent: string };
  /** Fraction of the canvas the creature may occupy. */
  fit?: number;
  /** Tone controls for brand/light palettes; changes apply live without replaying the assembly. */
  tone?: ToneOptions;
  /** Enable cursor repulsion. Off by default for page placements. */
  interactive?: boolean;
  className?: string;
  label: string;
}

/**
 * Drop-in canvas that renders an animated glyph creature. Sizes itself to its
 * parent (give the parent a definite height) and honours prefers-reduced-motion
 * by drawing a single settled frame.
 */
export function AsciiCreatureCanvas({
  src,
  columns,
  palette,
  charset = "binary",
  motion,
  hue = { base: "#304160", accent: "#70b8f0" },
  fit,
  tone,
  interactive = false,
  className,
  label,
}: AsciiCreatureCanvasProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const engineRef = useRef<AsciiCreature | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    // jsdom (tests) and very old browsers have no 2D canvas: render nothing, throw nothing.
    if (!canvas || typeof canvas.getContext !== "function" || !canvas.getContext("2d")) return;
    const fontFamily =
      getComputedStyle(document.body).getPropertyValue("--font-mono").trim() || "monospace";
    const still = window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ?? false;
    const engine = new AsciiCreature(canvas, { columns, palette, charset, hue, fontFamily, motion, fit, still, tone });
    engineRef.current = engine;

    let cancelled = false;
    const img = new Image();
    const start = () => {
      if (cancelled) return;
      engine.load(img);
      engine.play();
    };
    img.onload = start;
    img.src = src;
    if (img.complete && img.naturalWidth > 0) start();

    const observer = typeof ResizeObserver === "function" ? new ResizeObserver(() => engine.layout()) : null;
    observer?.observe(canvas);

    const onMove = (e: PointerEvent) => {
      const r = canvas.getBoundingClientRect();
      engine.setPointer(e.clientX - r.left, e.clientY - r.top, true);
    };
    const onLeave = () => engine.setPointer(0, 0, false);
    if (interactive) {
      canvas.addEventListener("pointermove", onMove);
      canvas.addEventListener("pointerleave", onLeave);
    }
    return () => {
      cancelled = true;
      engine.stop();
      engineRef.current = null;
      observer?.disconnect();
      canvas.removeEventListener("pointermove", onMove);
      canvas.removeEventListener("pointerleave", onLeave);
    };
    // Structural props only; look-and-feel props are applied live below.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [src, columns, motion, fit, interactive]);

  // Live restyle: no engine rebuild, no assembly replay.
  useEffect(() => {
    engineRef.current?.setOptions({ palette, charset, hue, tone });
  }, [palette, charset, hue, tone]);

  return <canvas ref={canvasRef} className={className} aria-label={label} role="img" />;
}
