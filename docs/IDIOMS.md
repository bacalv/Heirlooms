# Heirlooms — Idioms & Brand Language

**Status:** established at v0.17.0, 7 May 2026; vocabulary cleanup at v0.20.3, 10 May 2026; plot system at v0.24.0, 8 May 2026.
**Purpose:** A working glossary of the product's vocabulary — what each term
means in this world, where it appears, and what it should never be confused with.
Consult this when writing copy, naming things in code, or deciding what to call
something new. BRAND.md is the source of truth for visual and typographic rules;
this document is the source of truth for *words*.

---

## Quick reference

Plain-English definitions of the terms you'll encounter in the app.

> **This is a user-facing subset.** The team source of truth for vocabulary decisions
> is the Core concepts section below. When updating vocabulary, update the Core concepts
> entries first; this table is then derived from those entries in plain-English style.

| Term | What it means |
|---|---|
| **Garden** | Your collection of photos and videos saved to Heirlooms. |
| **Plant / Planted** | Upload a photo or video to your garden. |
| **Capsule** | A bundle of photos and a message addressed to someone, set to open on a future date. |
| **Start a capsule** | Create a new capsule. Its contents can still be changed until you seal it. |
| **Seal / Sealed** | Lock a capsule's contents so they can't be changed before it opens. |
| **Keep something for someone** | The emotional act of setting content aside as a gift — the purpose a capsule serves. |
| **Recipients** | The people a capsule is for. |
| **To open on** | The date a capsule becomes visible to its recipients. |
| **Compost** | Move a photo to the compost heap. It stays there for 90 days before being permanently deleted — you can restore it at any time during that window. |
| **Compost heap** | A holding area for composted photos. Everything here is still recoverable. |
| **Restore** | Bring a composted photo back to your garden. |
| **Plot** | A named section of your Garden that groups items by tag. Garden is made of plots. |
| **Just arrived** | The system plot at the top of Garden showing newly uploaded items you haven't processed yet — no tags, no capsule, not yet viewed. |

---

## Core concepts

### Garden

The user's collection of uploaded photos and videos. The garden is everything
they have planted — the whole vault, unfiltered.

**Appears in:** page titles (*Garden*), navigation labels, success copy
(*saved to your garden*), empty-state copy, Settings (*Export your garden*).

**Does not appear in:** API paths (`/uploads`), database table names, Kotlin
identifiers. Internal code stays neutral.

**Do not confuse with:** the Garden *tab* on Android (a specific view of the
garden), which is a navigation destination, not the data concept itself. The
Garden tab shows the garden; it is not *called* the garden in copy.

---

### Upload

The internal, technical name for a single item in the garden — one photo or
video file, with its metadata. Used in code, API paths, and database columns.
Never used in user-facing copy.

**User-facing equivalent:** *photo*, *video*, or *item* depending on context.
In most copy just use the content type (*this photo*, *4 photos*).

**Appears in:** `/api/content/uploads/…`, `UploadRecord`, `Upload` data class,
Kotlin variable names.

---

### Capsule

A sealed (or sealable) bundle of photos and a message, addressed to one or
more recipients, set to open on a chosen future date. The core product mechanic.

A capsule has two axes:
- **Shape** — *open* (contents editable until delivery) or *sealed* (contents
  frozen at the moment of sealing).
- **State** — *open*, *sealed*, *delivered*, or *cancelled*.

Shape is set at creation or sealing and cannot change. State moves forward
through the lifecycle: open → sealed → delivered, or any → cancelled.

**Appears in:** all user-facing surfaces (*Capsules* tab, *Start a capsule*,
capsule detail). Also used in API paths (`/api/capsules/…`) and database tables —
*capsule* is neutral enough to work in code too.

**Do not confuse with:** a *message* (the text inside a capsule), or *upload*
(the photos inside a capsule).

---

### Plant / Planting

The act of uploading a photo to the garden. The verb at the moment of arrival.
*Plant* is exclusively the upload verb — it does not appear with a recipient
construction.

- *Uploading…* — utility verb during transit (working state).
- *Something has been planted.* — meaning verb at success (arrival state).
- *Plant your first* — the call to action on an empty garden.

**Use for:** success states, empty-state CTAs, onboarding copy.
**Do not use for:** the capsule create flow — that uses *Keep* (brand-voice
opening line) and *Start* / *Seal* (action verbs).
**Do not use:** *plant something for someone* — the construction has been retired
in favour of *Keep something for someone* (v0.20.3). The *plant + for [person]*
shape has unwanted connotations in a product adjacent to family and parenthood.

---

### Seed

The metaphor behind the empty garden. *A garden begins with a single seed.*
Not a UI control, not a verb — an image. Used in empty-state copy only.

**Appears in:** the empty-garden message. Does not appear in code or API.

**Do not extend:** *seed* preserves its single-purpose role. It is the only
place the word appears. Do not promote it to a general noun for content items.

---

### Keep / Keeping

The brand verb for setting something aside for someone — the emotional register
of a capsule's purpose. *Keep* preserves the gift register without invoking
sealing, planting, or any other verb that has its own meaning.

**Appears in:** the welcome screen line *"A place to keep what matters."*, the
capsule create form's brand-voice opening line *"Keep something for someone."*

**Verb forms:** *keep* (active), *keeping* (in progress). No past tense —
*kept* doesn't appear in copy because the kept-ness of a capsule is conveyed
by other state words (*sealed*, *delivered*).

**Do not use:** *save*, *preserve*, *store*, *file away* — none of these carry
the gift register. *Keep* is specifically about the emotional act of setting
something aside *for someone*.

---

### Seal / Sealing

The act of committing a capsule's contents — freezing what's inside so it
cannot be changed before delivery. Sealing changes a capsule's shape from
*open* to *sealed*.

**Appears in:** *Seal capsule* button, sealing confirmation dialog, sealed-state
metadata, the wax-seal olive animation.

**Do not use for:** anything outside the capsule mechanic. *Sealed* is a loaded
word in this product; using it casually (e.g. "sealed the deal", "seal the form")
would dilute it.

---

### Bloom / Blooming / Bloomed

*Bloom* is the **visual moment** of delivery — the animation, the colour shift,
the petal-opening that occurs when a capsule reaches its unlock date. *Delivered*
is the **technical state** — the database state, the API field, the capsule
lifecycle position. Both refer to the same event from different angles.

Use *bloom* in copy that describes the moment (*"Something has bloomed for you."* —
Milestone 10 future copy). Use *delivered* in copy that describes the state
(*"Delivered on 14 May 2042."* in capsule detail metadata; *Delivered* as a filter
option in the capsule list).

Reserved for Milestone 10 (delivery). Used now only as a colour name (`bloom`) and
the future-tense promise.

**Do not use prematurely.** The verb *bloomed* is held for the delivery moment;
using it for uploads, tags, or other success states would spend it early.

---

### Compost / Composting

The act of soft-deleting a photo — moving it out of the garden into the compost
heap, where it sits quietly for 90 days before permanent deletion. A photo can
only be composted if it has no tags and no active capsule memberships.

Composting is **not** a trash can. It is considered removal — the product's
way of saying *this isn't useful to me anymore, but it isn't garbage either;
it's part of what becomes the garden's future*. The 90-day window is the safety
net, not the mechanism.

**Appears in:** *Compost* button, *Composted on [date]* metadata, the Compost
heap link, *Compost heap (N)*.

**Verb forms:** *compost* (to compost a photo), *composted* (past tense, also
used as metadata: *Composted 9 May*), *composting* (in progress).

**Do not use:** *delete*, *trash*, *remove*, *archive* — none of these carry
the right weight.

---

### The compost heap

The view that lists composted photos, each with its days-remaining countdown
and a *Restore* button. Not a bin, not a trash folder — a *heap*. The heap is
temporary, quiet, and patient. It is a place of return, not disposal.

**Appears in:** *Compost heap (N)* link in the Garden, the Compost heap screen
title, the Settings entry.

**Empty-state language:** five randomised lines, all short and still — *Nothing
has been set aside.*, *The heap is quiet.*, etc. See `BrandStrings.kt` and
`brandStrings.js` for the full pool.

---

### Restore / Restoring

The act of bringing a composted photo back to the garden — reversing a compost.
Returns the photo to active status; it loses its compost timestamp.

**Appears in:** *Restore* button in the compost heap and on composted photo
detail view.

**Do not use for:** anything outside the compost mechanic. *Restore* has a
specific, narrow meaning here.

---

### Recipients

The people a capsule is addressed to. Free-text names at v1 (Milestone 9
will introduce proper connections). A capsule must have at least one recipient.

**Appears in:** capsule identity (*For Sophie*, *For Sophie and James*),
capsule create form (*For* field), confirmation dialogs (*Sophie won't
receive it.*).

**Voice rule:** the recipient line is always *For [Name]*, not *To [Name]*
or *Recipient: [Name]*. The *for* phrasing is deliberate — it conveys gift
rather than transaction.

---

### Unlock date / To open on

The date a capsule becomes visible to its recipients. Called *unlock_at* in
the API and data model; called *To open on [date]* in all user-facing copy.

**Copy pattern:** always *To open on 14 May 2042*, not *Opens 14 May*,
*Delivery date: 14 May*, or *Scheduled for 14 May*. The phrase *to open on*
is the brand vocabulary.

---

## Lifecycle verbs at a glance

| Moment | Verb | Colour | Example copy |
|---|---|---|---|
| Upload in progress | *uploading* | forest | *uploading…* |
| Upload success | *planted* | bloom | *Something has been planted.* |
| Upload failure | (no brand verb) | earth | *Couldn't upload. Try again.* |
| Capsule created (open) | *started* | forest | *Start a capsule* |
| Capsule frozen | *sealed* | bloom (wax olive) | *Seal capsule* |
| Capsule delivered | *bloomed* | bloom (reserved) | *Something has bloomed for you.* |
| Capsule cancelled | *cancelled* | earth | *Cancel capsule* |
| Photo composted | *composted* | — | *Compost* / *Composted 9 May* |
| Photo restored | *restored* | — | *Restore* |

---

## What not to say

| Instead of | Say |
|---|---|
| Photo library / gallery / vault | Garden |
| Upload (user-facing) | Photo, video, item |
| Delete, trash, remove | Compost |
| (intentionally absent — error states use plain operational copy; see Errors section) |
| Delivery date, scheduled for | To open on |
| Recipient: / To: | For |
| Archive | (no equivalent — Heirlooms does not archive; it plants, seals, composts, or delivers) |

---

## Errors

Heirlooms does not have a brand vocabulary for errors. Error states use plain
operational copy: *"Couldn't save. Try again."*, *"Couldn't upload. Try again."*,
*"Couldn't connect. Check your network."*, matching the specific failure where
possible. Sans, earth-coloured, with a retry affordance.

The reasoning: the brand voice carries the product's emotional weight. Errors are
moments when the user is already frustrated; pulling them into a metaphor at that
moment is exactly the wrong time. The brand voice should be present in copy that
*carries meaning* (sealed, bloomed, composted, planted), not in copy that's purely
operational. The visual discipline (earth colour, retry affordance, no blame on the
user) preserves the brand's emotional register without requiring metaphor-decoding.

**Do not use:** *Didn't take*. (Removed at v0.20.3 — was opaque to first-time readers.)
**Do not invent:** Other gardening idioms for failure (*withered*, *unrooted*, etc.).
Errors are where the metaphor stays out.

---

## Words that live only in code

These are intentionally neutral and should never appear in user-facing copy:

- `upload` / `UploadRecord` — use *photo* or *video* for users
- `capsule` (in API paths, DB tables) — acceptable in user-facing copy too, but the data model identifier is neutral by design
- `state` / `shape` — internal capsule data model terms
- `storageKey`, `thumbnailKey` — internal file references
- `compostedAt`, `deliveredAt`, `cancelledAt` — database timestamps

---

## The unit of content

The brand has no canonical user-facing noun for "the unit of content" (a photo,
a video, a future document). The brand voice lives in the verbs (plant, seal,
bloom, compost) and in the structural nouns (garden, capsule, recipient). The
unit of content stays plain.

**Plural and count contexts:** *items*. *4 items selected.* *127 items in your
garden.* *3 items in this capsule.*

**Singular reference, type known and salient:** *this photo*, *this video*.
*Add this photo to a capsule.* *This video will play when opened.*

**Singular reference, type unknown or doesn't matter:** *this item*.
*Compost this item?*

**Empty-state copy:** *seed* preserves its single-purpose role.
*"A garden begins with a single seed."* This is the only place *seed* appears.

**Do not invent:** *seed*, *leaf*, *keepsake*, *treasure*, or any other unifier
brand-vocabulary noun for content. Each was considered during the v0.20.3 review
and rejected — the brand doesn't need one.

---

## Known unsettled

Vocabulary decisions deferred to future milestones. Recorded here so the next
session doesn't have to rediscover them.

### The *open* overload (Milestone 10)

*Open* currently has three meanings in the product:
- **Capsule shape:** *open* (contents editable until delivery) vs *sealed*
  (frozen at the moment of sealing).
- **Capsule state:** one of *open*, *sealed*, *delivered*, *cancelled* in
  the lifecycle.
- **User-facing date phrase:** *To open on [date]* — when the capsule
  becomes available to recipients.

Milestone 10 (delivery) will introduce a fourth meaning: the recipient's action
of *opening* a delivered capsule. This will conflict with the existing *open*
meanings and force a vocabulary decision.

Possible resolutions: rename the recipient action (*read*, *receive*, *unwrap*,
*discover*); rename the date phrase (*To unlock on*, *To arrive on*, *Available
from*); accept that *open* is context-disambiguated and only the recipient action
gets a new word.

No commitment yet. Settle when Milestone 10 design starts.

### The *recipients-as-categories* question (Milestone 9)

The current *For [Name]* phrasing assumes named individuals. As recipients
evolve from free-text to connections (Milestone 9), the phrasing may want to
handle categories (*for the family*, *for my children*) and self-future (*for
my future self*). Worth flagging; the *for* construction is brand-defining and
should be examined rather than reflexively extended.

---

## Checking combinations, not just words

When considering new vocabulary, check the *combinations* that arise, not just
the words in isolation. A word that's safe alone (*seed*, *plant*, *for*) can
produce loaded phrases when paired with brand verbs and recipient constructions.
The product's commercial register and the families it speaks to require care that
the metaphor system never composes into language that reads as inappropriate or
out-of-register.

Examples of compositions that have been considered and rejected:
- *Plant a seed for someone* — reproductive connotations.
- *Seed bank* — fertility-clinic adjacency.
- *Plant something for someone* (the capsule create form's original opening line)
  — softer than the above but close enough to the same shape that it was retired
  in v0.20.3 in favour of *Keep something for someone*.

The discipline: when proposing new vocabulary, mentally compose it with the
existing verbs and recipient constructions. If any combination reads as
out-of-register or inappropriate, the vocabulary doesn't fit — even if it's
safe in isolation.

### The voice is solemn; the room belongs to the user

The brand voice stays dignified at all times — the product never cracks
wise, never softens its register to seem fun, never adds emoji or
exclamation marks to feel approachable. *Compost* doesn't get a smiley face.

But the brand voice does not constrain what the *user* puts into the
product. A capsule of cursed photos for a daughter's wedding day gets the
same care from the product as a deathbed letter, because both are gifts.
The waiter at a good restaurant doesn't tell jokes — but if the diners are
laughing, the waiter doesn't shush them. The room is theirs.

The discipline cuts in both directions:

- **Don't make the voice fun.** Heirlooms is not a casino app, a productivity
  app, or a social media app. The dignity is load-bearing — it's what makes
  the product trustworthy for the moments that matter.
- **Don't make the voice grim.** Heirlooms is not a grief product. It is a
  product about time, and most uses of it have nothing to do with death.
  Copy that assumes solemnity in the user's *content* (rather than the
  product's *voice*) is over-correcting.

When in doubt: the voice is the waiter, not the diner.

---

## The naming discipline

New features should be named to fit the existing vocabulary before reaching for
neutral or technical terms. Ask:

1. Is there an existing idiom that fits? (plant, seal, bloom, compost, restore)
2. Is the thing part of the garden world? (seed, leaf, branch, root, harvest…, plot)
3. Does the name earn a place in the brand, or is it just a label?

---

## Plot

A named section of the Garden. The Garden contains plots. The system plot (*Just arrived*) is a
plot. User-created plots are plots. The gardening metaphor extends naturally — a plot is a
defined area where specific things grow.

**Appears in:** Garden page section headings ("Family", "Holidays"), affordance copy ("Add a plot",
"Edit plot", "Delete plot"), the system plot label "Just arrived".

**Does not appear as a verb.** The forms *plotted* and *plotting* stay out of user-facing copy.
*"You're plotting your garden"* reads as scheming. The noun carries the metaphor on its own.

**Affordance copy:**
- *Add a plot* — yes.
- *Edit plot* — yes.
- *Delete plot* — yes.
- *Plotting* — no, ever.

**Internal code:** `plots` table, `plot_tag_criteria` table. The sentinel name `__just_arrived__`
is translated to the user-facing label at render time.

---

## Just arrived

The user-facing label for the system-defined plot at the top of Garden. Items appear here when
newly uploaded and haven't been processed yet: no tags, not in any capsule, not yet opened in
detail view.

The label is plain and slightly anticipatory. It does not imply backlog (*Untended* would have) or
urgency. Items leave *Just arrived* when any of the following happen: a tag is added, the item is
added to a capsule, or the detail page is opened (which records `last_viewed_at`).

**Appears in:** Garden page, as the first and immutable row title.

**Does not appear in:** navigation, API paths, database identifiers. The schema sentinel name is
`__just_arrived__`; the render-time translation is `Just arrived`.

**UI rules:** The *Just arrived* row has no drag handle, no gear menu, and no management
affordances. It is always fixed at the top of Garden. Users cannot reorder it, edit it, or
delete it — server returns 403 on any modify/delete attempt.

---

## Negative-action button separation

A design principle, not a user-facing term. Destructive actions (compost, delete, cancel) are
visually separated from positive actions (add to capsule, tag, save) in any UI surface where they
coexist.

**Forms the separation can take:**
- A divider line between regions.
- Different colour treatments (earth tones for destructive; default for positive).
- Different visual weights (ghost button for destructive; solid for positive).
- Spatial separation (different sides of the screen, different sections of a menu).

**Why:** Prevents accidental destructive clicks. Signals the emotional weight of removal. Composes
with the *compost-not-trash* positioning — destructive actions shouldn't feel routine.

**In practice:** In Garden flavour (detail page), *Compost* appears below a divider, visually
separated from *Add this to a capsule*. In Explore flavour, *Compost* appears in the kebab menu
with a separator between it and the positive actions above.

If none of the above, a neutral technical name is fine — but record the choice
here if it's user-facing.
