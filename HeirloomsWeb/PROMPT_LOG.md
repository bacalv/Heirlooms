# Prompt Log — HeirloomsWeb Development Session

Timestamps are not available and are marked as `[unknown]`.

---

## Entry [2026-05-06]
**Prompt:** Create a new HeirloomsWeb sub-project at the monorepo root. React application with Tailwind CSS and Vite as the build tool, nginx to serve built files in production. Read two environment variables at build time: VITE_API_URL and VITE_API_KEY. Gallery/grid of uploaded files with thumbnails for images, generic file icon for videos, upload date, MIME type, file size. Clicking a thumbnail opens a lightbox. All requests include X-Api-Key header. Multi-stage Dockerfile (Node build → nginx alpine). .gitignore and README included.

**Response:** Created HeirloomsWeb/ with React 18, Tailwind CSS 3, Vite 6. Gallery grid with image thumbnails fetched via the new /api/content/uploads/{id}/file endpoint, generic SVG file icon for non-images, upload date, MIME type, formatted file size. Lightbox on thumbnail click (click background or Escape to close). Multi-stage Dockerfile. Vite build verified clean.

**Files created:**
- `package.json`, `vite.config.js`, `tailwind.config.js`, `postcss.config.js`
- `index.html`
- `src/main.jsx`, `src/index.css`, `src/App.jsx`
- `Dockerfile`
- `.gitignore`
- `README.md`

---

## Entry [2026-05-06] — In-memory auth
**Prompt:** Replace VITE_API_KEY build-time env var with a login screen. API key entered at runtime, held in React state only — cleared on page reload, never written to localStorage or disk.

**Response:** Added LoginScreen component that validates the key against the server with a real 401 check before proceeding. Removed VITE_API_KEY entirely. Images now fetched as blob URLs (fetch + createObjectURL) so the X-Api-Key header can be sent — plain `<img src>` cannot carry custom headers. Sign out button clears state. VITE_API_URL remains as the only build-time variable.

**Files changed:**
- `src/App.jsx` — login screen, blob URL thumbnails, sign out button
- `Dockerfile` — removed VITE_API_KEY ARG
- `README.md` — updated instructions

---

## Entry [2026-05-06] — Validated
**Validated end-to-end:** Dev server run via `npm run dev`. Login screen confirmed. Gallery loaded against production server after server was redeployed with CORS support (revision `heirlooms-server-00007-7vw`). Image thumbnails displayed correctly.
