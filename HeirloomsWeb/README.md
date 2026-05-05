# HeirloomsWeb

Read-only gallery UI for browsing files uploaded to HeirloomsServer.

## Local development

Create a `.env` file (gitignored) in this directory:

```
VITE_API_URL=http://localhost:8080
VITE_API_KEY=your-api-key
```

Install dependencies and start the dev server:

```bash
npm install
npm run dev
```

The app is served at `http://localhost:5173`.

## Production build

Build and run with Docker:

```bash
docker build \
  --build-arg VITE_API_URL=https://your-server-url \
  --build-arg VITE_API_KEY=your-api-key \
  -t heirlooms-web .
docker run -p 80:80 heirlooms-web
```

Or build locally and serve the `dist/` folder with any static file server:

```bash
npm run build
npm run preview
```
