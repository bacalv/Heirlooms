# Heirlooms — Idioms & Brand Language

**Status:** established at v0.21.0, 10 May 2026.
**Purpose:** A working glossary of the product's vocabulary — what each term
means in this world, where it appears, and what it should never be confused with.
Consult this when writing copy, naming things in code, or deciding what to call
something new. BRAND.md is the source of truth for visual and typographic rules;
this document is the source of truth for *words*.

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

- *Uploading…* — utility verb during transit (working state).
- *Something has been planted.* — meaning verb at success (arrival state).
- *Plant your first* — the call to action on an empty garden.

**Use for:** success states, empty-state CTAs, onboarding copy.
**Do not use for:** the capsule create flow — that uses *Start* and *Seal*,
not *plant*.

---

### Seed

The metaphor behind the empty garden. *A garden begins with a single seed.*
Not a UI control, not a verb — an image. Used in empty-state copy only.

**Appears in:** the empty-garden message. Does not appear in code or API.

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

### Bloom / Blooming

The moment of delivery — when a capsule opens for its recipient. Reserved for
Milestone 6. Used now only as a colour name (`bloom`) and a future-tense promise
in the motion language (*Something has bloomed for you*).

**Do not use prematurely.** The word *bloomed* is held for the delivery moment;
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

### Didn't take

The failure idiom. Used wherever an upload, save, or server action fails. Styled
in earth colour. Derived from the seed metaphor — *the seed didn't take*.

**Appears in:** upload failure screens, error states on any screen that makes a
server call, the *didn't take* animation (the stopped-short branch with the earth
seed on the soil).

**Do not use:** *error*, *failed*, *something went wrong* — these are fine in
code (exception messages, logs) but never in user-facing copy.

---

### Recipients

The people a capsule is addressed to. Free-text names at v1 (Milestone 7
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
| Upload failure | *didn't take* | earth | *didn't take* |
| Capsule created (open) | *started* | forest | *Start a capsule* |
| Capsule frozen | *sealed* | bloom (wax olive) | *Seal capsule* |
| Capsule delivered | *bloomed* | bloom (reserved) | *Something has bloomed for you.* |
| Capsule cancelled | — | earth | *Cancel capsule* |
| Photo composted | *composted* | — | *Compost* / *Composted 9 May* |
| Photo restored | *restored* | — | *Restore* |

---

## What not to say

| Instead of | Say |
|---|---|
| Photo library / gallery / vault | Garden |
| Upload (user-facing) | Photo, video, item |
| Delete, trash, remove | Compost |
| Error, failed, something went wrong | Didn't take |
| Delivery date, scheduled for | To open on |
| Recipient: / To: | For |
| Archive | (no equivalent — Heirlooms does not archive; it plants, seals, composts, or delivers) |

---

## Words that live only in code

These are intentionally neutral and should never appear in user-facing copy:

- `upload` / `UploadRecord` — use *photo* or *video* for users
- `capsule` (in API paths, DB tables) — acceptable in user-facing copy too, but the data model identifier is neutral by design
- `state` / `shape` — internal capsule data model terms
- `storageKey`, `thumbnailKey` — internal file references
- `compostedAt`, `deliveredAt`, `cancelledAt` — database timestamps

---

## The naming discipline

New features should be named to fit the existing vocabulary before reaching for
neutral or technical terms. Ask:

1. Is there an existing idiom that fits? (plant, seal, bloom, compost, restore)
2. Is the thing part of the garden world? (seed, leaf, branch, root, harvest…)
3. Does the name earn a place in the brand, or is it just a label?

If none of the above, a neutral technical name is fine — but record the choice
here if it's user-facing.
