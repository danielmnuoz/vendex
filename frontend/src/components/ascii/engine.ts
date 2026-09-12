/**
 * ASCII creature engine — samples a transparent PNG into a grid of glyph
 * cells and animates them on a 2D canvas.
 *
 * Motion layers (all cheap, all additive):
 *   - float:      whole body drifts on a slow Lissajous curve
 *   - breathe:    gentle scale pulse from the centroid
 *   - ripple:     serpentine sine displacement travelling along the body
 *   - dissolve:   edge cells fade in/out with hashed noise (see reference #2)
 *   - flicker:    a cell swaps its glyph only after it has physically travelled
 *                 a fraction of a cell (motion-driven, never on a timer)
 *   - flap:       wings (cells far from the centre line) beat vertically
 *   - assemble:   on load, cells fly in from scattered positions
 *   - cursor:     cells near the pointer get pushed aside and spring back
 */

export type Palette = "mono" | "sampled" | "brand" | "light";
export type Charset = "binary" | "digits" | "glyphs";
export type Motion = "flap" | "ripple";

export interface EngineOptions {
  /** Number of glyph columns across the creature's bounding box. */
  columns: number;
  palette: Palette;
  charset: Charset;
  /** Dominant hue for mono mode, plus an accent for highlights. */
  hue: { base: string; accent: string };
  fontFamily: string;
  /** Body-specific secondary motion: wing flap (Lugia) or serpentine ripple (Rayquaza). */
  motion: Motion;
  /** Fraction of the canvas the creature's bounding box may occupy (default 0.94). */
  fit?: number;
  /** Skip all motion and draw a single settled frame (prefers-reduced-motion). */
  still?: boolean;
  /** Tone controls for the brand/light palettes (each 0..1 unless noted). */
  tone?: ToneOptions;
}

export interface ToneOptions {
  /** Overall opacity of the ink. 1 = fully opaque darkest cells. */
  ink: number;
  /** How much the source luminance spreads the tone. 0 = flat, 1 = whites vanish. */
  contrast: number;
  /** Curve on the shadows. >1 keeps darks dark while lifting mid-tones; <1 darkens everything. */
  gamma: number;
}

export const DEFAULT_TONE: ToneOptions = { ink: 1, contrast: 0.4, gamma: 1 };

interface Cell {
  /** Grid coordinates within the sampled bitmap. */
  gx: number;
  gy: number;
  /** Source pixel colour and alpha (0..1). */
  r: number;
  g: number;
  b: number;
  a: number;
  /** 0 = interior, 1 = touching transparency. */
  edge: number;
  /** Perceived luminance 0..1. */
  lum: number;
  /** Whether the source pixel reads as a warm accent (Rayquaza's gold rings). */
  accent: boolean;
  /** Glyph state: current glyph, last drawn position, distance travelled since last flip,
   *  and the per-cell travel threshold that triggers the next flip. */
  ch: string;
  lastX: number;
  lastY: number;
  travel: number;
  flipAt: number;
  /** Assembly + cursor displacement state (canvas px). */
  offX: number;
  offY: number;
  velX: number;
  velY: number;
  seed: number;
}

const CHARSETS: Record<Charset, string> = {
  binary: "01",
  digits: "0123456789",
  glyphs: "·:+*#%",
};

const ALPHA_THRESHOLD = 40;

function hash(x: number, y: number, t: number) {
  // Cheap deterministic pseudo-noise in [0, 1).
  const s = Math.sin(x * 12.9898 + y * 78.233 + t * 0.61) * 43758.5453;
  return s - Math.floor(s);
}

function pick(set: string, seed: number) {
  return set[Math.floor(seed * set.length) % set.length];
}

export function sampleImage(img: HTMLImageElement, columns: number): { cells: Cell[]; rows: number; cols: number } {
  const aspect = img.naturalHeight / img.naturalWidth;
  const cols = columns;
  // Glyph cells are taller than wide, so fewer rows keep the proportions honest.
  const rows = Math.round(columns * aspect * 0.62);
  const off = document.createElement("canvas");
  off.width = cols;
  off.height = rows;
  const ctx = off.getContext("2d", { willReadFrequently: true });
  if (!ctx) throw new Error("2d context unavailable");
  ctx.drawImage(img, 0, 0, cols, rows);
  const { data } = ctx.getImageData(0, 0, cols, rows);
  const alphaAt = (x: number, y: number) =>
    x < 0 || y < 0 || x >= cols || y >= rows ? 0 : data[(y * cols + x) * 4 + 3];

  const cells: Cell[] = [];
  for (let y = 0; y < rows; y++) {
    for (let x = 0; x < cols; x++) {
      const i = (y * cols + x) * 4;
      const a = data[i + 3];
      if (a < ALPHA_THRESHOLD) continue;
      const r = data[i];
      const g = data[i + 1];
      const b = data[i + 2];
      let transparentNeighbours = 0;
      for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1], [1, 1], [-1, -1], [1, -1], [-1, 1]]) {
        if (alphaAt(x + dx, y + dy) < ALPHA_THRESHOLD) transparentNeighbours++;
      }
      const seed = hash(x, y, 0);
      cells.push({
        gx: x,
        gy: y,
        r,
        g,
        b,
        a: a / 255,
        edge: transparentNeighbours >= 2 ? Math.min(1, (transparentNeighbours - 1) / 3) : 0,
        lum: (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255,
        accent: r > 150 && g > 130 && b < 110,
        ch: "0",
        lastX: NaN,
        lastY: NaN,
        travel: 0,
        flipAt: 0.35 + seed * 0.5,
        offX: 0,
        offY: 0,
        velX: 0,
        velY: 0,
        seed,
      });
    }
  }
  return { cells, rows, cols };
}

export class AsciiCreature {
  private cells: Cell[] = [];
  private rows = 0;
  private cols = 0;
  private raf = 0;
  private start = 0;
  private pointer = { x: -1e6, y: -1e6, active: false };
  private cellW = 8;
  private cellH = 12;
  private originX = 0;
  private originY = 0;
  private dpr = 1;

  constructor(
    private readonly canvas: HTMLCanvasElement,
    private options: EngineOptions,
  ) {}

  load(img: HTMLImageElement) {
    const sampled = sampleImage(img, this.options.columns);
    this.cells = sampled.cells;
    this.rows = sampled.rows;
    this.cols = sampled.cols;
    const set = CHARSETS[this.options.charset];
    for (const c of this.cells) c.ch = pick(set, c.seed);
    this.scatter();
    this.layout();
  }

  setOptions(next: Partial<EngineOptions>, img?: HTMLImageElement) {
    const columnsChanged = next.columns !== undefined && next.columns !== this.options.columns;
    const charsetChanged = next.charset !== undefined && next.charset !== this.options.charset;
    this.options = { ...this.options, ...next };
    if (img && columnsChanged) {
      this.load(img);
      return;
    }
    if (charsetChanged) {
      const set = CHARSETS[this.options.charset];
      for (const c of this.cells) c.ch = pick(set, c.seed);
    }
    this.layout();
  }

  setPointer(x: number, y: number, active: boolean) {
    this.pointer = { x, y, active };
  }

  /** Re-run the assembly animation. */
  scatter() {
    if (this.options.still) {
      for (const c of this.cells) { c.offX = 0; c.offY = 0; c.velX = 0; c.velY = 0; }
      this.start = performance.now();
      return;
    }
    const w = this.canvas.clientWidth || 600;
    const h = this.canvas.clientHeight || 600;
    for (const c of this.cells) {
      const ang = c.seed * Math.PI * 2;
      const dist = 0.35 * Math.max(w, h) + hash(c.gx, c.gy, 7) * 0.5 * Math.max(w, h);
      c.offX = Math.cos(ang) * dist;
      c.offY = Math.sin(ang) * dist;
      c.velX = 0;
      c.velY = 0;
    }
    this.start = performance.now();
  }

  layout() {
    const { canvas } = this;
    this.dpr = Math.min(window.devicePixelRatio || 1, 2);
    const w = canvas.clientWidth;
    const h = canvas.clientHeight;
    canvas.width = Math.round(w * this.dpr);
    canvas.height = Math.round(h * this.dpr);
    if (!this.cols || !this.rows) return;
    // Fit the grid inside 84% of the canvas, preserving the glyph cell ratio.
    const fit = this.options.fit ?? 0.94;
    const fitW = (w * fit) / this.cols;
    const fitH = (h * fit) / this.rows;
    const cellH = Math.min(fitW / 0.62, fitH);
    this.cellH = cellH;
    this.cellW = cellH * 0.62;
    this.originX = (w - this.cols * this.cellW) / 2;
    this.originY = (h - this.rows * this.cellH) / 2;
  }

  play() {
    if (this.raf) return;
    if (!this.start) this.start = performance.now();
    const tick = (now: number) => {
      this.frame(now);
      this.raf = requestAnimationFrame(tick);
    };
    this.raf = requestAnimationFrame(tick);
  }

  stop() {
    cancelAnimationFrame(this.raf);
    this.raf = 0;
  }

  private colorFor(c: Cell, alpha: number): string {
    const { palette, hue } = this.options;
    if (palette === "sampled") {
      // Lift dark source pixels a little so line-art edges don't read as black.
      const lift = 0.18;
      const r = Math.round(c.r + (255 - c.r) * lift * (1 - c.lum));
      const g = Math.round(c.g + (255 - c.g) * lift * (1 - c.lum));
      const b = Math.round(c.b + (255 - c.b) * lift * (1 - c.lum));
      return `rgba(${r},${g},${b},${alpha})`;
    }
    const base = palette === "brand" ? "#304160" : palette === "light" ? "#eaeef2" : hue.base;
    const accent = palette === "brand" || palette === "light" ? "#70b8f0" : hue.accent;
    const color = c.accent ? accent : base;
    // Darker source pixels -> denser ink; mimic the reference's tonal falloff.
    // "light" inverts that: dark outlines stay faint, bright body reads solid on navy.
    const { ink, contrast, gamma } = this.options.tone ?? DEFAULT_TONE;
    const shade = Math.pow(palette === "light" ? c.lum : 1 - c.lum, gamma);
    const tone = palette === "mono"
      ? 0.86 + 0.14 * (1 - c.lum)
      : ink * ((1 - contrast) + contrast * shade);
    return hexToRgba(color, alpha * tone);
  }

  private frame(now: number) {
    const ctx = this.canvas.getContext("2d");
    if (!ctx || !this.cells.length) return;
    const t = this.options.still ? 4 : (now - this.start) / 1000;
    const { cellW, cellH, dpr } = this;
    const w = this.canvas.width / dpr;
    const h = this.canvas.height / dpr;
    const set = CHARSETS[this.options.charset];

    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, w, h);
    ctx.font = `700 ${Math.max(7, cellH * 1.0).toFixed(1)}px ${this.options.fontFamily}`;
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";

    // Whole-body motion.
    const floatX = Math.sin(t * 0.55) * cellW * 1.2;
    const floatY = Math.sin(t * 0.85) * cellH * 0.9 + Math.sin(t * 1.7) * cellH * 0.25;
    const breathe = 1 + Math.sin(t * 0.9) * 0.012;
    const cx = this.originX + (this.cols * cellW) / 2;
    const cy = this.originY + (this.rows * cellH) / 2;

    // Assembly progress: staggered ease-out over ~1.8s.
    const assembleT = Math.min(1, t / 1.8);

    const p = this.pointer;
    const repelR = Math.max(w, h) * 0.14;

    const halfCols = this.cols / 2;
    const flap = this.options.motion === "flap";

    for (const c of this.cells) {
      let rippleX = 0;
      let rippleY = 0;
      if (flap) {
        // Wing beat: displacement grows with distance from the body's centre line,
        // with a slight phase lag toward the tips so the wing bends rather than hinges.
        const wx = (c.gx - halfCols) / halfCols; // -1 .. 1
        const reach = Math.pow(Math.abs(wx), 1.7);
        const beat = Math.sin(t * 1.15 - reach * 1.1);
        rippleY = beat * reach * cellH * 2.2;
        rippleX = -beat * wx * reach * cellW * 0.6; // tips pull inward on the downbeat
        rippleY += Math.sin(c.gy * 0.18 + t * 0.9) * cellH * 0.08; // faint body sway
      } else {
        // Serpentine ripple: travels head-to-tail along the grid's diagonal.
        const wave = Math.sin(c.gy * 0.32 - c.gx * 0.08 + t * 1.9);
        rippleX = wave * cellW * 0.55 * (0.4 + c.edge * 0.6);
        rippleY = Math.cos(c.gx * 0.21 + t * 1.3) * cellH * 0.18;
      }

      let hx = this.originX + (c.gx + 0.5) * cellW;
      let hy = this.originY + (c.gy + 0.5) * cellH;
      hx = cx + (hx - cx) * breathe + floatX + rippleX;
      hy = cy + (hy - cy) * breathe + floatY + rippleY;

      // Assembly: each cell has its own slightly delayed ease.
      const delay = c.seed * 0.5;
      const local = Math.max(0, Math.min(1, (assembleT * 1.8 - delay) / 1.3));
      const ease = 1 - Math.pow(1 - local, 3);

      // Cursor repulsion as a spring on the offset vector.
      if (p.active) {
        const dx = hx - p.x;
        const dy = hy - p.y;
        const d = Math.hypot(dx, dy);
        if (d < repelR && d > 0.001) {
          const push = ((repelR - d) / repelR) ** 2 * cellW * 2.4;
          c.velX += (dx / d) * push * 0.35;
          c.velY += (dy / d) * push * 0.35;
        }
      }
      // Spring the offset back to zero; assembly scatter also rides this offset.
      const k = 0.09;
      const damp = 0.8;
      c.velX = (c.velX - c.offX * k * ease) * damp;
      c.velY = (c.velY - c.offY * k * ease) * damp;
      c.offX += c.velX;
      c.offY += c.velY;
      // While assembling, the scatter offset collapses with the ease curve.
      const ox = c.offX * (1 - ease) + c.offX * ease * 0.15;
      const oy = c.offY * (1 - ease) + c.offY * ease * 0.15;

      // Motion-driven flicker: a glyph only changes once its cell has moved far
      // enough on screen (float, flap, cursor push). Still cells keep their glyph.
      const drawX = hx + ox;
      const drawY = hy + oy;
      if (Number.isNaN(c.lastX)) {
        c.lastX = drawX;
        c.lastY = drawY;
      }
      c.travel += Math.hypot(drawX - c.lastX, drawY - c.lastY);
      c.lastX = drawX;
      c.lastY = drawY;
      if (c.travel > c.flipAt * cellH) {
        c.ch = pick(set, hash(c.gx, c.gy, c.travel * 13.7));
        c.travel = 0;
      }

      // Edge dissolve.
      let alpha = c.a;
      if (c.edge > 0) {
        const n = hash(c.gx, c.gy, Math.floor(t * 4));
        alpha *= 0.55 + 0.45 * (0.5 + 0.5 * Math.sin(t * 1.4 + n * 6.28));
      }
      alpha *= 0.15 + 0.85 * ease;

      ctx.fillStyle = this.colorFor(c, alpha);
      ctx.fillText(c.ch, drawX, drawY);
    }
  }
}

function hexToRgba(hex: string, alpha: number) {
  const n = parseInt(hex.replace("#", ""), 16);
  const r = (n >> 16) & 255;
  const g = (n >> 8) & 255;
  const b = n & 255;
  return `rgba(${r},${g},${b},${Math.max(0, Math.min(1, alpha)).toFixed(3)})`;
}
