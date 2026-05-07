# Heirlooms — Brand

**Status:** locked at v0.17.0, 7 May 2026.
**Source of truth:** this document. If `tailwind.config.js`, `src/index.css`,
or any component disagrees with what's written here, the discrepancy is a bug
in the code, not in the brand.

## Identity system

Wordmark + symbol, used together or apart:

- **Wordmark.** *Heirlooms*, italic Georgia. Primary identity in long contexts
  — the centre of arrival animations, settings headers, page titles, the site
  header. Will be replaced with a custom-drawn wordmark in a future milestone.
- **Symbol.** Olive branch — slim curving stem, three pairs of leaves
  descending in size, a single olive at the apex. Used at small scale (icons,
  favicons, share-sheet headers) where the wordmark would be illegible. Also
  used at large scale as a brand mark.

The wordmark and symbol are not interchangeable. Don't put the symbol where
the wordmark belongs to "save space," and don't shrink the wordmark below
~14px to "fit in" where the symbol belongs.

## Palette

Six colours. No others without a deliberate decision recorded back in this
document.

| Token        | Hex      | Role                                          |
| ------------ | -------- | --------------------------------------------- |
| `parchment`  | #F2EEDF  | Ground. Page backgrounds, cards.              |
| `forest`     | #3F4F33  | Primary. The colour of the brand mark, body type, in-progress states. |
| `bloom`      | #D89B85  | Arrival. The ripened olive. Success states. Used sparingly — every appearance is earned. |
| `earth`      | #B5694B  | Didn't-take. The seed that fell. Failure states. Also avatar/accent colour. |
| `new-leaf`   | #7DA363  | Secondary vegetation accent. Used in illustrations and the occasional pop of plant green. |
| `ink`        | #2C2A26  | Deepest text. Use sparingly — `forest` is preferred for body text on parchment. |

## The three signal colours

The brand replaces all utility-icon success/failure signalling with colour
discipline:

- **Forest** = in-progress, neutral, the act of growing.
- **Bloom** = arrival, success, completion.
- **Earth** = didn't-take, fault, drop.

No green checkmarks. No red X-marks. No warning triangles. No exclamation
glyphs. The colour, paired with typography, does the work.

This applies everywhere: arrival animations, form validation, toasts, inline
status indicators, empty states, error states, success confirmations.

## Typography

- **Serif italic.** Georgia, italic. Used for the wordmark, voice messages
  ("Something has been planted."), and any moment where the brand speaks in
  its own voice rather than the system's voice.
- **Sans.** System sans (`-apple-system`, `system-ui`, `Segoe UI`, Roboto).
  Used for body, UI labels, metadata.
- **Mono.** `ui-monospace`. Used for tags shown as code, version numbers,
  technical metadata.

The serif italic should be rare and earned — every appearance is a moment of
voice. Do not set body copy in italic Georgia.

## Voice

The user-facing word for the vault is **garden**. Use it for:

- Page titles and browser tab titles.
- Navigation ("View garden", "Your garden").
- Settings labels ("Export your garden").
- Welcome and help copy.

Do **not** use it for:

- UI element names. Settings stays *Settings*. Tags stays *Tags*. Filters
  stays *Filters*.
- Internal code. Database tables, API endpoints, and Kotlin/TypeScript
  identifiers stay neutral (`uploads`, `capsules`, `tags`).

### Verbs

- *uploading…* — utility verb during transit. Used by the working spinner
  and progress UI.
- *planted* — meaning verb at the moment of arrival. Used in the success
  message after a successful upload.
- *bloomed* — reserved for milestone delivery (capsule unlock). Do not use
  for routine uploads.
- *didn't take* — failure language for upload/save failures. Earth-coloured.

### Canonical strings

- Empty gallery: "A garden begins with a single seed."
- Empty gallery sub: "Drag a photo here, or share from your phone."
- Empty gallery CTA: "plant your first"
- Upload complete: "Something has been planted."
- Upload complete sub: "{n} photos saved to your garden."
- Upload failed: "didn't take"
- Milestone delivery (future, Milestone 6): "Something has bloomed for you."

## Motion language

Five named states. All currently target the arrival animation but the
vocabulary is reusable.

| State              | Visual                                                    |
| ------------------ | --------------------------------------------------------- |
| working            | Three forest-green dots, 1.4s pulse, 0.2s stagger.        |
| growing            | Branch draws, leaves emerge in pairs base→tip.            |
| blooming           | Olive forms in forest, then ripens to bloom colour.       |
| arrived            | Wordmark settles in beneath. Composition complete.        |
| didn't take        | Branch stops short, no olive, earth-coloured seed on soil, "didn't take" line. |

The arrival animation runs ~3 seconds total. `prefers-reduced-motion` falls
back to the static end state of each animation, with no transitions.

## What is NOT in this document

- The custom-drawn wordmark (still in stand-in form, future milestone).
- The capsule visual mechanic (Milestone 5).
- Milestone delivery brand language (Milestone 6).
- App store listing graphics, marketing site, email templates.
