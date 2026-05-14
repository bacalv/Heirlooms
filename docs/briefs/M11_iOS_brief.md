# Milestone 11 — iOS app for non-technical family members

*Briefed 13 May 2026. Implementation waits for M10 to ship.*

---

## Origin

Heirlooms's first non-developer human (a friend tester) was onboarded on 12 May
2026 alongside M9. The next non-developer user — and the canonical
family-experience user — is the project author's mother: iPhone owner, not
computer-literate, the person the multi-user infrastructure of M8 and the
sharing layer of M9–M10 were ultimately built for.

This milestone delivers the iOS app she will actually use. It is the project's
first move into the *family-experience product* shape recorded in the founding
ideas, distinct from the *developer tool* that Android currently is. The two
products share a codebase on the server but diverge sharply on the client: the
iOS app exposes none of the architectural model (no garden, plot, flow,
predicate, tag, friendship, or keypair concepts) and offers the smallest
possible set of actions that supports the use case.

## Goal

A non-technical iPhone user can, with help from a technical user setting up
their phone in person, share photos and videos with that technical user
trivially and continuously, and see what the technical user shares back. No
typed credentials, no endpoint configuration, no conceptual furniture.

## Audience and assumed environment

- Single canonical user: project author's mother. Design generalises to similar
  users (an aunt, a grandparent, a non-technical sibling) but is not optimised
  for users without a technical partner doing setup.
- iPhone running a recent iOS. iPad sometimes used.
- WiFi-mostly. Mobile data is metered and the user is cost-sensitive.
- Setup happens in person, with the technical user present and their Android
  app (or the web app) in hand.

## Distribution and dev environment

- TestFlight as the primary distribution channel for the foreseeable future.
  External-tester slot used for the canonical user; full App Store submission
  is a later concern, not in scope for this milestone.
- Apple Developer Program (£79/yr) is the only ongoing cost.
- Build environment: Xcode on macOS. SwiftUI for the UI layer.
- Free-tier sideloading to a personal iPad covers development before paid
  enrolment, with the known limits (7-day expiry, no push, no associated
  domains, no iCloud entitlements). None of those limits block the iOS app's
  scope as designed.

## Setup flow — two scans, in order

Setup is split into two QR scans rather than one. The split removes the
need for a delegate-key-in-QR mechanism (a weaker design briefly considered
when setup was conceived as a single scan) by ensuring the recipient's
pubkey is already registered on the server before the plot invite is
generated.

### Scan 1 — identity

On first run the iOS app shows a single screen with an **Activate** button.
Tapping it opens the camera to scan a QR generated from the technical user's
Android app or web app. The QR carries an M9 account-invite token.

Redemption performs, in one transaction:

- Provisions the new user's account on the server.
- Generates a P-256 sharing keypair on-device, stored in iOS Keychain (Secure
  Enclave-backed where supported).
- Registers the public key with the server.
- Establishes the friendship between the new user and the inviting user.

After this, the new user has a real Heirlooms account, indistinguishable on
the server from any other user. M7's E2EE, M8's data isolation, and M9's
friendship model all apply normally.

### Scan 2 — shared plot

The technical user creates a shared plot on Android or web (existing M10
functionality) and generates a plot-invite QR. The QR generator on the
technical user's side asks which friend to invite, so the resulting QR is
bound to a specific recipient account.

At QR-generation time, the technical user's device wraps the plot's E2EE
group key directly to the recipient's already-registered sharing pubkey. The
server holds the wrapped key for redemption. The server never sees the plot
key in cleartext (the M10 invariant is preserved end-to-end).

The iOS app's home screen, in its post-Activate / pre-plot state, shows a
**Scan QR Code** button. Scanning the plot-invite QR causes the device to
fetch the wrapped key, unwrap into Keychain, and bind the plot as the user's
single shared plot. The home screen transitions to its primary state.

### Token routing

The two QR types are URL-distinguishable so the app routes correctly:

- `heirlooms://invite/friend/<token>` — Scan 1, account-invite token.
- `heirlooms://invite/plot/<token>` — Scan 2, plot-invite token.

The app handles both via the same camera surface but with different
post-scan flows.

### Recovery / new phone

The user pairs a web browser with their phone as a recovery surface
(existing pairing flow, mirrored from Android). If the phone is lost,
broken, or replaced, the account is recoverable from that browser back onto
the new iPhone. The technical user guides setup of the recovery surface as
part of initial onboarding.

## Main screen — the cut-down garden

After both scans, the home screen shows two virtual views over the single
shared plot, plus a **Plant** button:

- **Shared with you** — items in the plot uploaded by other accounts.
- **You shared** — items in the plot uploaded by this account.

The virtual views are client-side filters over the plot's item list, not
backend concepts. The labels are hardcoded generic strings — "Shared with
you" and "You shared" — chosen so they read correctly regardless of plot
membership size, and so they read as instructions rather than as
identifiers.

The Plant button starts an in-app upload picker matching the Android Plant
flow: multi-select supported, grid preview, single confirm. No tagging step
— the app is tag-agnostic in this version. Items uploaded via Plant land in
the shared plot identically to items routed via the share sheet.

Tapping an item opens it full-screen. Videos play. The full-screen view
exposes the iOS share sheet, which provides:

- **Save Image** / **Save Video** to camera roll. Treated as a tier-one
  receive action — explicitly designed for and tested end-to-end. This is
  the dominant action a grandparent will take with received content
  (grandchild video → camera roll → show in person to a neighbour).
- **Share to other app** — standard iOS share-sheet routing to mail,
  messages, WhatsApp, etc.

There are no delete affordances anywhere in the iOS app. Mistaken uploads
(wrong photo, blurry shot, photo of a thumb) are cleaned up by the
technical user from the web or Android. The brief calls out: do not surface
a delete affordance that errors — do not render the affordance at all.

## Share sheet integration

iOS Share Extension registered against photos and videos, so "Heirlooms"
appears in the share sheet from Photos, Camera, Files, and any other app
exporting compatible items.

The extension hands picked items to the main app via an App Group shared
container; the main app performs the actual upload via
`URLSession.backgroundSessionConfiguration`. Background URLSession is the
right primitive for this milestone's bulk-upload requirement: it survives
app termination, runs while the screen is locked, handles network
transitions, and retries on transient failure.

All uploads — share-sheet or Plant — route to the single configured shared
plot. No picker, no choice, no confirmation beyond the system share sheet
itself.

Items are streamed from disk during upload (matching the Android v0.16.1
streaming fix). No video bytes are loaded into memory before transfer. This
is non-negotiable on iOS: the share extension is memory-constrained, and
the main app must handle 90 MB+ videos without OOM.

## WiFi-only setting

A single Settings toggle controls `allowsCellularAccess` on the background
URLSession. Default: WiFi only. When off-WiFi, queued items wait until a
WiFi network appears, then drain. The toggle is the only network-related
setting exposed.

## Day-one camera-roll import

After the second scan completes, the app offers a one-time prompt to
import the user's existing camera roll into the shared plot.

Honest copy is required. A typical phone has 5,000–20,000 photos, not 150.
The prompt states an approximate count, that the upload will take weeks of
opportunistic WiFi time rather than minutes, and that the phone is best
left charging overnight during active import. The prompt is skippable, and
the import is also available later from settings.

Each item is E2EE-encrypted on-device (M7) before upload, which is
CPU-intensive. Battery and temperature implications during active import
are real and the prompt says so.

Bulk-imported items are tagged `import:<date>` (or get an equivalent
server-side flag) so the technical user's Just Arrived surface does not get
drowned for weeks. The tag is invisible in the iOS app.

## Settings menu

- **Settings** — app version, **API key reset**. Reset semantics are
  deliberately deferred to a separate discussion. The non-obvious question
  is what "API key reset" means in a world with both an API key and a
  device keypair. This brief does not commit to an answer.
- **Devices & Access** — generate a pairing code for the web (mirrors the
  existing Android pairing flow). This is the user's recovery surface; the
  technical user walks them through pairing a browser as part of
  onboarding.
- **Diagnostics** — local-only log of the last 28 days, four columns:
  `Time | Category | Message | Detail`. Not synced to the server. Categories
  include upload, auth, network, crypto, background-session events. The
  surface is for the technical user to read when they next visit in person;
  it is not designed for the canonical user to interpret.
- **Reset shared plot** — wipes the current plot binding and returns the
  app to its post-Activate / pre-plot state, ready to scan a new
  plot-invite QR.

No endpoint configuration is exposed. No tag UI. No reference anywhere to
plots, flows, predicates, friendships, keypairs, or any other architectural
concept. All of those exist on the server and are used by the app; none are
named or surfaced.

## Crypto fit with M7 / M9 / M10

- Sharing keypair: P-256, generated on-device at activation, stored in iOS
  Keychain. P-256 was chosen as the curve at M7 specifically because iOS
  Secure Enclave supports it natively for ECDH and signing. The asymmetric
  scheme is curve-agnostic at the envelope-format level (recorded in M7),
  but P-256 is what gets used here.
- Friendship establishment: M9 primitive, unchanged.
- Plot key wrapping: happens on the inviting device at QR-generation time,
  bound to the recipient's already-registered sharing pubkey. Server holds
  the pre-wrapped key for redemption. Server never sees the plot key
  cleartext at any point.
- Background uploads: stream from disk, identical pattern to Android
  v0.16.1.

## Scope: explicitly out of v1

The following are deliberately omitted. Each is small to add later if real
usage demonstrates a need; none reshape the architecture.

- **Push notifications.** The canonical user will see new items when she
  next opens the app to share something. If usage shows she is missing
  items, push is additive (no other change required).
- **Capsule client.** The iOS app does not view or unlock sealed capsules.
  Capsule unlock remains a web concern, including for capsules delivered to
  this user.
- **Tagging UI.** Tags exist on the server and the technical user applies
  them via Android or web. The iOS app neither reads nor writes them.
- **Multiple shared plots.** One plot per device, replaceable via Settings
  → Reset shared plot. Multi-plot support is not blocked architecturally
  but is not needed for the canonical use case.
- **Receive-side notifications** of any kind (badge counts, in-app banners,
  email digests). The Shared with you view simply shows what is there when
  the app is opened.

## Sequencing and prerequisites

- **M10 must ship first.** Shared-plot primitives, plot-key wrapping API,
  and friend-scoped plot-invite generation must be real on the server
  before the iOS app has anything to call. Building against a moving target
  is the foreseeable failure mode and is rejected pre-emptively here.
- **Android plot-invite generator with friend picker** is a small but real
  M10-or-pre-M11 addition. The Android app and web both need a "Generate
  plot invite QR for friend X" affordance that does the client-side plot
  key wrap at generation time.
- **M9 invite-token redemption over API** (not via browser) needs to be
  confirmed to exist or built. If M9 only ships invite redemption as a
  browser flow, a small API endpoint that consumes the same token is
  required before the iOS Activate flow can work.
- **Apple Developer Program enrolment** can happen at any point before
  TestFlight is needed. Development to working share-extension on iPad is
  possible on the free tier first; paid enrolment unlocks TestFlight push
  to the canonical user's phone.

## Single open question carried forward

API key reset semantics in a multi-credential (API key + device keypair)
world. Set aside for separate discussion; the iOS app exposes the Settings
entry but its behaviour is not committed in this brief.