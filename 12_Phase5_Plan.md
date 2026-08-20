# Phase 5 Plan — Mobile Access + Image Logging

**Status:** Planning only. No implementation in this document.  
**Guardrail:** Both features exist only to reduce 14-day logging friction. Collect Truth. Do not add coaching, calories, or analytics.

---

## Current architecture (as built)

```
Phone / laptop browser
        │
        ▼
Vite :5173  ──proxy /api──►  Spring Boot :8080  ──►  OpenAI Chat Completions
                                    │
                                    ▼
                              PostgreSQL event_logs
```

Relevant facts:

- Frontend API client uses **relative** `/api` when `VITE_API_BASE_URL` is empty (Vite proxy). Absolute `localhost:8080` breaks on a phone.
- Vite has been started with `--host 127.0.0.1` in agent sessions (loopback only).
- CORS allowlist is `http://localhost:5173` and `http://127.0.0.1:5173`.
- `POST /api/logs` accepts `{ message, source? }`. Parse is **text-only**. Failure → `UNKNOWN` + `{ parseError, source: "openai" }`. Still saves `raw_text`.
- `event_logs` has no image column. `raw_text` is `NOT NULL` and must be non-blank.
- `source` already exists (`web` default). No auth.
- Docker Compose runs **Postgres only**. Frontend is not containerized.
- Agent-owned `npm run dev` dies; durable process (IntelliJ/user terminal) is required for a tunnel to stay up.

---

## Feature 1 — Mobile access (ngrok)

### Goal

Open the existing logging UI on a phone browser and submit text logs through a public HTTPS URL, without a native app.

### Recommended topology (one tunnel)

Tunnel **Vite :5173 only**. Keep the existing `/api` proxy.

```
Phone browser
    │  HTTPS
    ▼
ngrok  ──►  Vite :5173  ──proxy /api──►  Spring Boot :8080  ──►  OpenAI
                                            │
                                            ▼
                                         PostgreSQL
```

Why this, not two tunnels:

- Phone talks to **one origin**. Relative `/api` keeps working.
- CORS stays same-origin through ngrok; no extra allowlist for API host.
- Matches current frontend client (`API_BASE_URL` empty).
- Fewer moving parts for Phase 0.

**Do not** tunnel only the backend and leave the UI on `localhost` — the phone cannot load the SPA.

**Do not** point `VITE_API_BASE_URL` at `http://localhost:8080` when using the phone — that host is the phone, not the laptop.

### Environment / config updates

| Change | Why |
|--------|-----|
| Vite listen `0.0.0.0` **or** ngrok target `127.0.0.1:5173` | ngrok can forward to loopback; LAN IP access needs `0.0.0.0`. Prefer ngrok → `127.0.0.1:5173` so the app is not exposed on Wi‑Fi without the tunnel. |
| Keep `VITE_API_BASE_URL` empty | Same-origin proxy through the tunnel. |
| `CORS_ALLOWED_ORIGINS` | Needed **only if** a second tunnel is added for the API. With one-tunnel-to-Vite: no CORS change. |
| `NGROK_AUTHTOKEN` | ngrok v3 login. **Never commit.** `.env.example` placeholder only. |
| Optional `NGROK_BASIC_AUTH` | Username/password on the public URL (ngrok edge). Strongly recommended because the app has **no auth**. |
| Frontend/backend stay on laptop | Tunnel is an access path, not a deploy. |

Runbooks (docs only at implementation):

1. Start Postgres + Spring Boot + Vite in a **durable terminal** (not a Cursor agent job).
2. `ngrok http 5173` (or config file pointing at `127.0.0.1:5173`).
3. Open the `https://*.ngrok-free.app` URL on the phone.
4. Dismiss ngrok interstitial if present (free tier).
5. Log a test event; confirm row in `event_logs`.

`source`: frontend may send `source: "mobile"` when `window.innerWidth` is small or when the host is `*.ngrok`. Optional; default `web` is acceptable if we do not want extra logic.

### Security considerations

| Risk | Mitigation (Phase 0) |
|------|----------------------|
| Public URL, **no app auth** — anyone with the link can write/read logs | Treat ngrok URL as a secret. Use ngrok **basic auth**. Rotate URL (stop/start ngrok). Do not post the URL in chat/docs. |
| Intercepted traffic | ngrok provides HTTPS to the phone. |
| Camera later requires secure context | HTTPS tunnel is required for image capture on mobile Safari/Chrome. |
| Binding Vite to `0.0.0.0` on café Wi‑Fi | Prefer ngrok → loopback only. |
| Secrets in ngrok dashboard / local config | Local `ngrok.yml` / env only; gitignore. |
| Phone on cellular vs laptop asleep | Tunnel dies if laptop sleeps or Vite/agent process dies. Same as current frontend reliability issue. |

Out of scope for this feature: Spring Security, JWT, VPN, Cloudflare.

---

## Feature 2 — Image-based logging (OpenAI Vision)

### Goal

From web or mobile: pick/capture a food photo → Vision extracts food facts → save one `FOOD` event. Never drop the log if Vision fails (`UNKNOWN` + parseError).

### Flow

```
UI file/camera input
    │  multipart POST
    ▼
LogController  →  store file on disk
               →  OpenAiVisionClient (image_url data/base64 or file)
               →  normalize to FOOD structured fields
               →  persist event_logs
               →  201 + timeline refresh
```

Synchronous, same as text parse. No queue.

### Vision contract (frozen for implementation)

Model: vision-capable chat model via env, e.g. `OPENAI_VISION_MODEL` (default can match `OPENAI_MODEL` if it supports images, else `gpt-4o-mini`).

JSON-only output, example:

```json
{
  "eventType": "FOOD",
  "structured": {
    "item": "samosa",
    "quantity": 2,
    "items": [{ "item": "samosa", "quantity": 2 }]
  },
  "description": "Two samosas on a plate"
}
```

Rules:

- Extract **food** only. Do not invent calories/macros/advice.
- If not food / unclear → `eventType: "UNKNOWN"`, still save.
- `raw_text` = model `description` if present, else `"image log"` (satisfies NOT NULL / not-blank).
- Store **full model content string** as raw AI output (column below).
- `parser_version` = `v1` or `vision-v1` (prefer **`vision-v1`** to distinguish from text parser).
- `source` = `web` or `mobile` from client.

Failure (no key, 4xx/5xx, invalid JSON):

```json
{
  "eventType": "UNKNOWN",
  "structuredJson": { "parseError": true, "source": "openai" }
}
```

Still save the image file + a row. Truth collection wins.

### Image storage (MVP)

Do **not** put binary in PostgreSQL.

- Directory: `backend/data/uploads/` (gitignored).
- Filename: `{eventId}.jpg` (or original extension, allow jpeg/png/webp).
- DB stores **relative path** only, e.g. `uploads/{uuid}.jpg`.
- Optional later: `GET /api/logs/{id}/image` to show a thumbnail on timeline. MVP can skip serving images if timeline shows `raw_text` only; **still store the file** for reference.

Max size: **4 MB** after client compress (or reject). Resize on server if easy; otherwise reject oversized uploads.

---

## 1. Architecture changes

| Area | Change |
|------|--------|
| Ingress | Optional ngrok process in front of Vite. Not in Docker Compose for MVP. |
| Frontend | Camera/file control; multipart upload client; slightly denser mobile layout (CSS only). |
| Backend | New `OpenAiVisionClient` next to existing `OpenAiParserClient`. New create path in `LogService`. Multipart max size in Spring. |
| Storage | Local upload directory + Flyway columns on `event_logs`. |
| Config | `OPENAI_VISION_MODEL`, `APP_UPLOAD_DIR`, ngrok documented in README / `.env.example`. |
| Auth | Still none. ngrok basic auth is the public-edge control. |

No new services, no S3, no CDN, no React Native.

---

## 2. API changes

Keep `POST /api/logs` (JSON text) unchanged.

**Add:**

`POST /api/logs/image`  
`Content-Type: multipart/form-data`

| Part | Rules |
|------|--------|
| `file` | Required. image/jpeg, image/png, image/webp. |
| `source` | Optional. Default `web`. `mobile` from phone UI. |

**Response:** same `CreateLogResponse` as text create (`201`), including `eventType`, `rawText`, `structuredJson`, `source`, `parserVersion`, `timestamp`, plus:

```json
{
  "imageRef": "uploads/550e8400-....jpg"
}
```

Add `imageRef` to timeline `LogItemResponse` (nullable). Text logs: `null`.

**Do not add** a public unauthenticated image CDN. If UI needs a thumbnail, add `GET /api/logs/{id}/image` in the same implementation slice (authenticated only by “you have the ngrok URL”).

Search remains text `ILIKE` on `raw_text` (image descriptions become searchable).

---

## 3. Database changes

Flyway `V2__image_logging.sql` — additive only. Do not rewrite `event_logs`.

| Column | Type | Notes |
|--------|------|--------|
| `image_ref` | `VARCHAR(512) NULL` | Relative path to stored file. Null for text logs. |
| `raw_model_output` | `JSONB NULL` | Full parsed/raw Vision (or text parser) payload. Null for older rows. |

Optional (only if it avoids stuffing flags into JSON): `input_kind VARCHAR(16)` with `text` \| `image`. Can be inferred from `image_ref IS NOT NULL` — **skip extra column** unless needed.

`raw_text` stays required: Vision `description` or `"image log"`.

No new tables. No vector columns.

---

## 4. UI changes

Stay on the single screen (log + timeline). No new routes.

| Change | Detail |
|--------|--------|
| Text input | Unchanged primary path. |
| Image control | “Photo” button: `input type="file" accept="image/*" capture="environment"` so mobile opens camera. |
| After image submit | Clear file input, refresh timeline, same error banner pattern. |
| Timeline row | Show `FOOD` / `UNKNOWN`, `rawText`, structured JSON. If `imageRef` present, small “photo” badge. Thumbnail only if `GET .../image` is in MVP. |
| Layout | Touch-friendly padding; input + Photo + Log on small screens (stack). |
| ngrok | No special UI. Works because of HTTPS + relative API. |

Out of scope: galleries, crop editor, multi-photo burst, barcode scanner.

---

## 5. Implementation order

Do **ngrok first**. It unblocks phone text logging with zero schema change and proves the tunnel before adding Vision cost/latency.

1. **Durable local runbook** — README: start DB, backend, Vite from a real terminal; Vite host documented.
2. **ngrok** — tunnel to 5173; `.env.example` placeholders; basic auth; smoke test text log from phone.
3. **Flyway V2** — `image_ref`, `raw_model_output`.
4. **Upload + persist without Vision** — save file, insert `FOOD` or `NOTE` with placeholder text (optional internal spike) **or** go straight to Vision if time is short.
5. **OpenAiVisionClient** — JSON mode + image part; UNKNOWN fallback; store raw output.
6. **API** — `POST /api/logs/image`; extend response/timeline DTOs.
7. **UI** — file/camera control; wire multipart; badge on timeline.
8. **Optional** — `GET /api/logs/{id}/image` + thumbnail.
9. **Verify** — phone photo over ngrok → DB row + file on disk + timeline.

Do not refactor the text parser while doing this.

---

## 6. Risks

| Risk | Impact | Handling |
|------|--------|----------|
| Agent/Cursor Vite dies → ngrok 502 | Phone logging stops | Start Vite in a durable terminal (already observed). |
| Laptop sleep | Tunnel dead | Keep laptop awake during logging windows. |
| Leaked ngrok URL | Strangers write health logs | ngrok basic auth; rotate URL. |
| Free ngrok interstitial / URL change every start | Bookmark breaks | Document “copy new URL each session”; paid reserved domain later if needed. |
| Vite HMR websocket over ngrok | Dev noise, not logging | Ignore for MVP; disable HMR on tunnel if it breaks the page. |
| Vision mislabels non-food | Wrong `eventType` | Still stored; UNKNOWN prompt rule; user can add a text log. No edit API in Phase 0. |
| Multiple foods in one photo | One row only | `structured.items[]` allowed; do not split into many events. |
| Large images / slow 4G | Timeouts | Client downscale; 30s read timeout already in OpenAI client; show submitting state. |
| Cost | Vision tokens > text | Cap size; one image per log; no retries beyond one. |
| Disk fill | Upload dir grows | 14-day volume is small; still gitignore; no cloud store. |
| `raw_text` constraint | Image-only insert fails | Always write description or `"image log"`. |
| Scope creep | Calories, plate OCR, gallery | Explicitly out. |

---

## 7. MVP scope

**In**

- One ngrok tunnel to the Vite app; phone browser text logging.
- ngrok basic auth + documented env placeholders (no real secrets).
- Photo capture/upload → Vision → `FOOD` (or `UNKNOWN`) → Postgres + disk file.
- `image_ref` + `raw_model_output` on `event_logs`.
- Same UNKNOWN-on-failure behavior as text.
- Timeline shows new events; image indicated, thumbnail optional.

**Out**

- Native iOS/Android apps.
- Two-tunnel / split frontend-backend public URLs (unless one-tunnel fails).
- App-level login.
- S3 / cloud image hosting.
- Calorie or macro estimates.
- Multi-event split from one photo.
- Image search, gallery, delete/edit.
- Dockerizing frontend/backend for Phase 5.
- Search UI (still deferred unless already trivial).

**Success**

User can log from a phone (text today, photo for food) for the 14-day collection window without sitting at the laptop for every event.

---

## Review checklist before coding

- [ ] One-tunnel-to-Vite vs two tunnels — confirm one-tunnel.
- [ ] ngrok basic auth required vs optional.
- [ ] `parser_version` for images: `vision-v1`.
- [ ] Thumbnail endpoint in first slice vs badge-only.
- [ ] Durable process reminder accepted (Cursor agent Vite is not production for Day 1).
