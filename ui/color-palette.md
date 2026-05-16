# VenDex Color Palette

`ui/tokens.css` is the single source of truth for color and font tokens.
This document mirrors what's in `tokens.css` and adds context for the
deferred dark theme. The pen file (`ui/vendex.pen`) is the canonical source
for any visual design — any HTML/CSS code in the project should consume
the variables defined in `tokens.css`.

## Light theme (active)

This is the in-app default and the only theme any UI currently uses. It was
chosen for an operator-tool aesthetic — neutral, legible under booth
lighting and on phones in the wild.

| Role        | Hex       | RGB             | Usage                                      |
|-------------|-----------|-----------------|---------------------------------------------|
| bg          | `#f0f0f8` | (240, 240, 248) | Page background                             |
| bg-panel    | `#ffffff` | (255, 255, 255) | Cards, terminal windows, elevated surfaces  |
| bg-card     | `#f7f7fc` | (247, 247, 252) | Nested card backgrounds                     |
| red-deep    | `#a82028` | (168, 32, 40)   | Primary CTA, active chips, feature numbers  |
| red-warm    | `#c85048` | (200, 80, 72)   | Accents, eyebrow text, highlights, badges   |
| blue        | `#70b8f0` | (112, 184, 240) | Cursor, titlebar dot, anon indicator, "new" |
| navy        | `#304160` | (48, 65, 96)    | ASCII art color                             |
| fg          | `#1e1e2a` | (30, 30, 42)    | Primary text (headlines, card names)        |
| fg-mid      | `#5a6270` | (90, 98, 112)   | Secondary text, descriptions, labels        |
| fg-dim      | `#a8b8c8` | (168, 184, 200) | Tertiary text (step numbers, placeholders)  |
| border      | `#d4dce6` | (212, 220, 230) | All borders and dividers                    |

## Dark theme (planned, Phase 6+)

Reserved for a user-selectable dark mode. Not implemented yet — the in-app
surface is light-only until the vendor-vendor product validates and the
intelligence/analytics surfaces (Phase 6+) introduce screens where a dark
mode improves readability. The hex values below are the canonical dark
palette; do not change them without versioning.

| Role        | Hex       | RGB             | Usage                                      |
|-------------|-----------|-----------------|---------------------------------------------|
| bg          | `#272536` | (39, 37, 54)    | Page background                             |
| bg-panel    | `#2e2c3e` | (46, 44, 62)    | Cards, elevated surfaces                    |
| bg-card     | `#333145` | (51, 49, 69)    | Nested card backgrounds                     |
| red-deep    | `#a82028` | (168, 32, 40)   | Primary CTA (shared with light)             |
| red-warm    | `#c85048` | (200, 80, 72)   | Accents, badges (shared with light)         |
| navy        | `#304160` | (48, 65, 96)    | Secondary backgrounds, badge fills          |
| navy-light  | `#3d5478` | (61, 84, 120)   | Indicator dots, subtle accents              |
| fg          | `#eaeef2` | (234, 238, 242) | Primary text (headlines, card names)        |
| fg-mid      | `#8895a5` | (136, 149, 165) | Secondary text (labels, meta)               |
| fg-dim      | `#4a4860` | (74, 72, 96)    | Tertiary text (step numbers, placeholders)  |
| border      | `#3a3850` | (58, 56, 80)    | All borders and dividers                    |
| steel       | `#a8b8c8` | (168, 184, 200) | Body text, ASCII art, descriptions          |

`tokens.css` keeps the dark palette as a `:root[data-theme="dark"]` block so
the future theme toggle is just a `data-theme` attribute flip.

## Fonts

- **Headlines / display**: Space Grotesk (300–700) — `--font-display`
- **Monospace / code / labels**: Space Mono (400, 700) — `--font-mono`
