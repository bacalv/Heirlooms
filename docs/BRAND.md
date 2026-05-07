# Heirlooms — Brand

**Status:** foundation locked at v0.17.0, 7 May 2026; capsule visual
mechanic added at v0.18.2, 8 May 2026; *compost* verb and empty-state
pool reference added at v0.20.0, 9 May 2026.
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

Six primary colours plus a small set of derived tokens (see below). No
new primary colours without a deliberate decision recorded back in this
document.

| Token        | Hex      | Role                                          |
| ------------ | -------- | --------------------------------------------- |
| `parchment`  | #F2EEDF  | Ground. Page backgrounds, cards.              |
| `forest`     | #3F4F33  | Primary. The colour of the brand mark, body type, in-progress states. |
| `bloom`      | #D89B85  | Arrival. The ripened olive. Success states. Used sparingly — every appearance is earned. |
| `earth`      | #B5694B  | Didn't-take. The seed that fell. Failure states. Also avatar/accent colour. |
| `new-leaf`   | #7DA363  | Secondary vegetation accent. Used in illustrations and the occasional pop of plant green. |
| `ink`        | #2C2A26  | Deepest text. Use sparingly — `forest` is preferred for body text on parchment. |

### Derived tokens

Five tokens derived from the primary palette via opacity, used where a
softer or layered version of a primary colour is needed. These are not
new colours — they're alpha-blended versions of the six primary tokens,
documented here so the codebase's Tailwind config matches what BRAND.md
prescribes.

| Token        | Derived from              | Used for                              |
| ------------ | ------------------------- | ------------------------------------- |
| `forest-75`  | forest at 75% opacity     | Inactive navigation link text — muted version of full-forest for non-active nav items. |
| `bloom-15`   | bloom at 15% opacity      | Delivered capsule card background in list view (the parchment-bloom wash). |
| `bloom-25`   | bloom at 25% opacity      | Defined in config; not yet applied to any component. Reserved for bloom-tinted surfaces at higher intensity than bloom-15. |
| `earth-10`   | earth at 10% opacity      | Cancelled capsule card background in list view; hover background on earth-style action buttons; "didn't take" error banner background in the create form. |
| `earth-20`   | earth at 20% opacity      | Defined in config; not yet applied to any component. Reserved for earth-tinted surfaces at higher intensity than earth-10. |

The discipline above the primary table — *no new primary colours without a
deliberate decision* — applies to *new hex values*. Derived tokens
(opacity-blended versions of the primary palette) are acceptable as
needed, and should be documented in this sub-table when they enter the
codebase.

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
- *sealed* — meaning verb at the moment of commitment. Used when a
  capsule's contents are frozen by the user. The sealing moment is
  visualised by the wax-seal olive (see "Capsule states" below). Do
  not use *sealed* for routine UI affordances elsewhere — it is
  reserved for the capsule mechanic.
- *bloomed* — reserved for milestone delivery (capsule unlock). Do not use
  for routine uploads.
- *compost* — meaning verb at the moment of removal. A photo that is no longer
  needed in the garden goes to the compost heap, where it sits quietly until it
  is gone for good (90 days). Used in the *Compost* affordance and in metadata
  copy ("Composted 9 May"). Not a destructive verb — *compost* says *this isn't
  useful to me anymore, but it isn't garbage either; it's part of what becomes
  the garden's future*.
- *didn't take* — failure language for upload/save failures. Earth-coloured.

### Canonical strings

- Empty gallery: "A garden begins with a single seed."
- Empty gallery sub: "Drag a photo here, or share from your phone."
- Empty gallery CTA: "plant your first"
- Upload complete: "Something has been planted."
- Upload complete sub: "{n} photos saved to your garden."
- Upload failed: "didn't take"
- Milestone delivery (future, Milestone 6): "Something has bloomed for you."
- Compost-heap empty state (randomised per session): see the `compostHeapEmptyState`
  array in `HeirloomsWeb/src/brand/brandStrings.js`. Five lines; pool can be
  expanded with PA review.

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

The five states above describe phases of the upload arrival animation.
The two states below describe transitions between capsule states. Same
motion vocabulary, different surface.

| State              | Visual                                                    |
| ------------------ | --------------------------------------------------------- |
| sealing            | Wax-seal olive forms in top-left of capsule card, ~700ms. |
| delivering         | Wax-seal olive grows from corner to fill, card background washes from parchment to bloom-tinted, ~2.5s. Reserved for Milestone 6. |

## Capsule states

A capsule has four states (open, sealed, delivered, cancelled), defined
by the data model. Each has a visual treatment that uses the existing
palette without expanding it. Three states map directly onto the
forest/bloom/earth signal-colour vocabulary; the fourth, sealed,
introduces a new motif — the wax-seal olive — that threads through the
capsule's lifecycle.

### The wax-seal olive

A new brand element, distinct from the brand mark's apex olive.

- **Form.** A simplified ovoid, vertically oriented, slight taper at
  the top, no stem. About 1.6:1 height-to-width ratio.
- **Colour.** Always bloom (#D89B85). The wax-seal olive is one of two
  appearances of bloom in a capsule's lifecycle — the other is the
  delivered state — and the two appearances are causally linked: the
  small bloom olive at sealing is the *promise* of the larger bloom
  state at delivery.
- **Sizes.**
  - 16-20px: list-view capsule card, top-left corner.
  - ~24px: photo detail view's "in N capsules" line, beside sealed
    capsule names.
  - Full ceremonial size (~48-64px): capsule detail view of sealed
    capsules.
  - Card-filling: backdrop of delivered capsules.

The wax-seal olive is not interchangeable with the brand mark's apex
olive. The brand mark olive sits at the apex of the branch with the
stem visible — it is *the* olive, the company's mark. The wax-seal is
*an* olive — a promise made, not the whole tree. Using the same shape
for both would dilute the brand mark.

Reference SVG (working draft — refine as needed when first rendered):

```svg
<svg viewBox="0 0 20 32" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
  <path d="M 10 2.5
           C 5 3, 2.5 10, 2.5 18
           C 2.5 25.5, 5.5 29.5, 10 29.5
           C 14.5 29.5, 17.5 25.5, 17.5 18
           C 17.5 10, 15 3, 10 2.5 Z"
        fill="currentColor" />
</svg>
```

The path is intentionally simple — it should read clearly at 16px and
not become busy at 64px. `currentColor` lets the SVG inherit the
appropriate bloom colour from its container.

### State visual treatments

#### Open

Forest treatment. Card has parchment background, forest typography,
soft 1px earth-tinted border (same border weight as form fields
elsewhere). No olive. The capsule is still growing.

#### Sealed

Forest treatment, identical to open at the card level — except for a
small bloom-coloured wax-seal olive in the top-left corner. The olive
sits above the recipient line like a wax seal above a salutation. The
fruit has formed; the day hasn't come.

#### Delivered

Bloom-tinted treatment. The card background shifts from parchment to a
parchment-bloom wash (a soft tint, not a hard fill). The wax-seal olive
grows to ceremonial size, used as a backdrop element or a centred
focal point depending on the surface. Bloom-dominant. The card is a
*bloomed* capsule. *Something has bloomed for you.*

#### Cancelled

Earth-tinted treatment. The card is desaturated, slightly faded.
Recipient and date remain readable but in a *this is no longer
happening* register. No earth-coloured strikethrough or other literal
marks — the colour shift alone carries the meaning. Cancelled cards
are excluded from the default Capsules list view (see "Visibility"
below).

### Capsule card (list view)

The list-view card holds:

- The recipient line — *"For Sophie"* or *"For Sophie and James"* —
  set in italic Georgia, the brand voice typeface. The recipient is
  the emotional centre of a capsule and deserves the voice.
- Below the recipient: the unlock date — *"To open on 14 May 2042"* —
  set in system sans, smaller. The phrase *to open on* is the brand
  vocabulary; the date is just a date.
- Below the date, a small metadata row: photo count, message-present
  indicator. System sans, muted forest. Quiet.
- For sealed capsules: the wax-seal olive in the top-left corner.
- For delivered and cancelled capsules: the state's tinted treatment
  applies to the card background.

The card holds the user's attention with the recipient and date.
Everything else is supporting metadata.

### Capsule detail view

The detail view is where the brand work lands most. The list view is
glance; the detail view is *dwelling*.

- **Top of the view.** The recipient line in italic Georgia, set
  large. Below it, the unlock date in sans, smaller. This is the
  capsule's identity — its name is its recipient and date.
- **Sealed capsules:** the wax-seal olive at full ceremonial size,
  prominent. Placement to the right of the recipient/date block, or
  above. The olive at full size says *this is a sealed thing, it
  carries a promise*.
- **Message body.** Italic Georgia for sealed and delivered capsules
  (the message is committed, the brand is now speaking on the user's
  behalf). System serif for open capsules (the message is still
  draft, the user is still composing). This typography shift is
  meaningful — sealing promotes the message from *draft* to
  *delivery-bound*.
- **Photo grid.** Same visual treatment as gallery thumbnails — the
  user shouldn't have to learn a new way of looking at photos.
- **Recipients list** (when displayed separately from the top
  recipient line — e.g. "Also for: ..."). Italic Georgia, brand
  voice for the people involved.
- **Action region at the bottom.** Buttons differ by state — open
  capsules show *Edit*, *Seal capsule*, *Cancel capsule*; sealed
  capsules show *Edit message*, *Edit recipients*, *Edit unlock
  date*, *Cancel capsule*; delivered shows no actions; cancelled
  shows no actions.

### The Start and Seal buttons in the create form

The capsule create form ends with two buttons. Both are committed
actions, but they create capsules in different shapes.

- **Start capsule** — primary. Forest fill, parchment text. Larger,
  wider button. The default action, the more forgiving choice.
- **Seal capsule** — secondary. Forest outline, forest text,
  parchment fill. Smaller, ghost-styled. Carries a small
  bloom-coloured wax-seal olive to the left of the button text — a
  visual foreshadowing of what the user will see when they look at
  their newly-sealed capsule in the list.

The hierarchy says *most users start, some seal*, which is the right
default. Both buttons are committed; there is no draft state in the
create form.

### Photo detail view's "in N capsules" line

When a photo is in one or more active capsules (open or sealed), the
photo detail view shows them as ambient metadata:

> In capsules: For Sophie, For the family

For sealed capsules, a small wax-seal olive (~24px) sits to the right
of the capsule's name:

> In capsules: For Sophie 〔olive〕, For the family 〔olive〕, For James

(The 〔olive〕 placeholder represents the wax-seal SVG inline.)

Open capsules get the bare name. Sealed capsules get the olive.
Cancelled and delivered capsules don't appear here at all — the
reverse-lookup endpoint excludes them.

This gives a glance-readable distinction between *capsules I can still
edit this photo into* and *capsules where this photo is locked in*.

### Visibility — cancelled capsules

Cancelled capsules are excluded from the default Capsules list view.
The data is there; the API supports filtering by state. The UI
exposes cancelled capsules through:

- A state filter dropdown above the Capsules list, or
- A small *Show cancelled* link below the list.

The user has decided these aren't happening; the UI respects that
decision but doesn't pretend the data doesn't exist. Cancelled
capsules in their tinted treatment, accessible but not in the default
eye-line.

### Animations

#### Sealing transition

When the user presses *Seal capsule* (in the create form, or on an
open capsule in the detail view):

- Duration: ~700ms.
- The capsule card's wax-seal olive *forms* in the top-left corner,
  growing from zero to its 16-20px size. Same easing family as the
  existing "growing" state in the arrival animation (the leaves
  emerging in pairs).
- The card otherwise unchanged. The forest treatment doesn't shift;
  only the olive appears.

The animation is short and deliberate — sealing is a single committed
gesture, not a slow growth. Faster than the arrival animation, which
runs ~3 seconds.

#### Delivery transition (Milestone 6 territory)

When a capsule is delivered (Milestone 6, scheduled at unlock_at):

- Duration: ~2.5s.
- The wax-seal olive *grows* from its corner-size to fill the card.
- The card background *washes* from parchment to bloom-tinted.
- The brand voice line *Something has bloomed for you* appears as the
  composition completes.

This is the moment the brand has been promising the entire time the
capsule was sealed. The bloom colour fulfils its second appearance —
the seal becoming the bloom.

The recipient-side delivery moment — when a recipient (Milestone 7+)
opens their bloomed capsule for the first time — is the strongest
brand moment in the whole product. Specific motion design for that
moment is reserved for the milestone where it ships.

#### Reduced motion

Both the sealing and delivery transitions fall back to their static
end states under `prefers-reduced-motion`, with no transitions —
matching the existing arrival animation's reduced-motion behaviour.

## What is NOT in this document

- The custom-drawn wordmark (still in stand-in form, future milestone).
- Milestone delivery brand language (Milestone 6).
- App store listing graphics, marketing site, email templates.
