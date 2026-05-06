# HeirloomsWeb

Read-only gallery UI for browsing files uploaded to HeirloomsServer.

The API key is entered at login each session and held in memory only — it is
never written to disk or browser storage.

## Local development

Create a `.env` file (gitignored) in this directory:

```
VITE_API_URL=http://localhost:8080
```

Install dependencies and start the dev server:

```bash
npm install
npm run dev
```

The app is served at `http://localhost:5173`. You will be prompted for the API
key on first load; enter the same key configured as `API_KEY` on the server.

## Production build

Build and run with Docker:

```bash
docker build \
  --build-arg VITE_API_URL=https://your-server-url \
  -t heirlooms-web .
docker run -p 80:80 heirlooms-web
```

Or build locally and serve the `dist/` folder with any static file server:

```bash
npm run build
npm run preview
```
