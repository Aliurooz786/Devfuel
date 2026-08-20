# DevFuel Phase 0 — Technical Design

**Status:** Design freeze approved (with post-freeze adjustments)  
**Scope source:** `01_Vision.md` … `08_Guardrails.md`  
**Rule:** If a design item is not required for 14-day logging, it does not belong here.

**Post-freeze adjustments:** `source` (default `web`), `parser_version`, richer UNKNOWN `structured_json`, optional `eventType` search filter.

---

## 1. Purpose

Phase 0 is a **truth collection system**.

The system must:

1. Accept natural-language life events
2. Parse them into structured events via OpenAI
3. Persist them in PostgreSQL
4. Show a chronological timeline
5. Support simple text search

The system must **not**:

- Predict, advise, recommend, or optimize
- Calculate calories/macros
- Use RAG, vector search, multi-agent flows, notifications, or wearables

---

## 2. System Context

```
┌─────────────┐     HTTP/JSON      ┌──────────────────┐     HTTPS      ┌────────────┐
│  React Web  │ ─────────────────► │  Spring Boot API │ ─────────────► │  OpenAI    │
│  Frontend   │ ◄───────────────── │  (single app)    │ ◄───────────── │  Chat API  │
└─────────────┘                    └────────┬─────────┘                └────────────┘
                                            │
                                            │ JDBC
                                            ▼
                                   ┌──────────────────┐
                                   │   PostgreSQL     │
                                   │   event_logs     │
                                   └──────────────────┘
```

**Stack (frozen from `06_Architecture.md`):**

| Layer    | Choice        |
|----------|---------------|
| Frontend | React         |
| Backend  | Spring Boot   |
| AI       | OpenAI API    |
| Database | PostgreSQL    |

**Design principles:** simple, maintainable, low cost, fast to implement, minimal dependencies.

---

## 3. Backend Architecture

### 3.1 Application style

Single Spring Boot application exposing REST JSON APIs under `/api`.

No microservices. No message queues. No background workers for Phase 0.

### 3.2 Logical layers

```
Controller  →  Service  →  Repository
                 │
                 ├─→ OpenAiParserClient (HTTP to OpenAI)
                 └─→ EventLogRepository (JPA / JDBC → PostgreSQL)
```

| Layer | Responsibility |
|-------|----------------|
| **Controller** | HTTP mapping, request validation, response mapping |
| **Service** | Orchestrate parse → persist → query; own transaction boundaries |
| **OpenAiParserClient** | Call OpenAI; map model output to domain parse result |
| **Repository** | CRUD and search against `event_logs` |
| **Entity / DTO** | Persistence model vs API contracts (keep separate) |

### 3.3 Backend modules (logical)

- `config` — CORS, OpenAI client config, Jackson, error advice
- `log` — controllers, services, entities, repositories, DTOs for event logs
- `parser` — OpenAI request/response models and client
- `common` — shared error types, enums (`EventType`)

### 3.4 Runtime concerns (Phase 0 only)

| Concern | Decision |
|---------|----------|
| Auth | **None.** Single-user local/dev tool for 14-day logging. |
| Caching | **None.** |
| Async parsing | **Synchronous.** One request waits for OpenAI then DB write. |
| Rate limiting | **None** in app; rely on OpenAI account limits. |
| Migrations | Flyway (or equivalent) applying `10_Schema.sql` / versioned SQL. |
| Observability | Application logs (request id optional); no APM required. |

### 3.5 Backend configuration (env)

| Variable | Purpose |
|----------|---------|
| `DATABASE_URL` / Spring datasource props | PostgreSQL connection |
| `OPENAI_API_KEY` | OpenAI authentication |
| `OPENAI_MODEL` | Model name (default fixed in config; overridable) |
| `PARSER_VERSION` | Parser/prompt version stamp stored on each row (e.g. `phase0-v1`) |
| `SERVER_PORT` | Default `8080` |
| `CORS_ALLOWED_ORIGINS` | Frontend origin (e.g. `http://localhost:5173`) |

Secrets stay in environment / `.env` (never committed).

---

## 4. Frontend Architecture

### 4.1 Application style

Single-page React app. No routing complexity beyond what Phase 0 needs.

**Screens / regions (not components yet):**

1. **Log input** — text field + submit (primary action; &lt; 5 seconds to log)
2. **Timeline** — chronological list of events (newest first or oldest first — freeze: **newest first**)
3. **Search** — query box that filters/fetches matching logs

Optional later polish (still Phase 0 UI only): show `eventType` badge and `rawText` per row. No charts, trends, or analytics UI.

### 4.2 Frontend layers

```
UI views  →  API client  →  Spring Boot /api
```

| Piece | Responsibility |
|-------|----------------|
| Views | Capture input, render timeline/search results, show errors |
| API client | `POST /api/logs`, `GET /api/logs`, `GET /api/logs/search` |
| Local state | Form text, loading flags, list of logs, search query, error message |

No global state library required for Phase 0. React state + fetch (or thin wrapper) is enough.

### 4.3 Frontend config

| Variable | Purpose |
|----------|---------|
| `VITE_API_BASE_URL` (or equivalent) | Backend base URL, e.g. `http://localhost:8080` |

### 4.4 UX rules aligned with success criteria

- Logging is the default focus of the first screen
- After successful create: clear input, refresh timeline (or prepend new event)
- Show a clear error if parse or save fails; do not silently drop the log
- Search is secondary; does not block logging

---

## 5. Database Schema

Canonical DDL: **`10_Schema.sql`**.

### 5.1 Table: `event_logs`

| Column | Type | Notes |
|--------|------|-------|
| `id` | `UUID` | Primary key, generated by DB or app |
| `event_timestamp` | `TIMESTAMPTZ` | When the life event occurred (default: server receive time for Phase 0) |
| `raw_text` | `TEXT` | Exact user message |
| `event_type` | `VARCHAR(32)` | Enum-like: see supported types |
| `structured_json` | `JSONB` | Parsed fields; may be `{}` |
| `source` | `VARCHAR(32)` | Client origin; **default `web`** |
| `parser_version` | `VARCHAR(64)` | Parser/prompt version that produced `structured_json` |
| `created_at` | `TIMESTAMPTZ` | Row insert time |
| `updated_at` | `TIMESTAMPTZ` | Row update time |

### 5.2 Supported `event_type` values

`SMOKING` | `FOOD` | `WEIGHT` | `EXERCISE` | `ALCOHOL` | `MOOD` | `SLEEP` | `NOTE` | `UNKNOWN`

### 5.3 Indexes

- `(event_timestamp DESC)` — timeline
- `(event_type)` — optional search filter aid
- Phase 0 text search: `ILIKE '%q%'` on `raw_text` (no vector / pg_trgm)

### 5.4 Phase 0 timestamp rule

User does **not** supply a custom event time in the API for Phase 0.

- `event_timestamp` = server time at successful create (UTC stored as `TIMESTAMPTZ`)
- Future “backdating” is out of scope until Phase 0 succeeds

---

## 6. Entity Models

### 6.1 Persistence entity: `EventLog`

| Field | Java / domain type | DB column |
|-------|--------------------|-----------|
| `id` | `UUID` | `id` |
| `eventTimestamp` | `Instant` | `event_timestamp` |
| `rawText` | `String` | `raw_text` |
| `eventType` | `EventType` enum | `event_type` |
| `structuredJson` | `JsonNode` / `Map` / `String` | `structured_json` |
| `source` | `String` | `source` |
| `parserVersion` | `String` | `parser_version` |
| `createdAt` | `Instant` | `created_at` |
| `updatedAt` | `Instant` | `updated_at` |

### 6.2 Enum: `EventType`

```
SMOKING, FOOD, WEIGHT, EXERCISE, ALCOHOL, MOOD, SLEEP, NOTE, UNKNOWN
```

### 6.3 Domain parse result (not persisted alone)

Used between OpenAI client and service:

| Field | Type | Meaning |
|-------|------|---------|
| `eventType` | `EventType` | Classified category |
| `structured` | object / map | Type-specific fields |
| `confidence` | optional number | **Not stored in Phase 0** unless useful for logging; omit from DB |

### 6.4 Suggested `structured_json` shapes (non-binding examples)

These are **guidance for the parser prompt**, not separate tables.

| Type | Example structured fields |
|------|---------------------------|
| `SMOKING` | `{ "quantity": 1, "unit": "cigarette" }` |
| `FOOD` | `{ "item": "samosa", "quantity": 2 }` |
| `WEIGHT` | `{ "value": 93.5, "unit": "kg" }` |
| `EXERCISE` | `{ "activity": "walk", "durationMinutes": 30 }` |
| `ALCOHOL` | `{ "item": "beer", "quantity": 1 }` |
| `MOOD` | `{ "mood": "kharab", "intensity": "high" }` |
| `SLEEP` | `{ "hours": 6 }` or `{ "note": "late sleep" }` |
| `NOTE` | `{ "text": "..." }` or `{}` |
| `UNKNOWN` (unclassified) | `{ "reason": "unclassified" }` or `{}` |
| `UNKNOWN` (parse failure) | `{ "parseError": true, "source": "openai" }` |

**Parse-failure UNKNOWN shape (frozen):**

```json
{
  "parseError": true,
  "source": "openai"
}
```

| Field | Meaning |
|-------|---------|
| `parseError` | Always `true` when the log was saved because parsing failed |
| `source` | Where the failure originated (e.g. `openai`); not the client `source` column |

Missing fields are allowed on successful parses. Prefer partial structure over failing the log.

---

## 7. API Endpoints

Base path: `/api`  
Content-Type: `application/json`  
No authentication headers in Phase 0.

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/api/logs` | Create log from natural language |
| `GET` | `/api/logs` | Timeline (all events, newest first) |
| `GET` | `/api/logs/search` | Search by query string; optional `eventType` filter |

No update/delete endpoints in Phase 0 (not required for 14-day collection). Add later only if logging friction requires corrections.

---

## 8. Request / Response Contracts

Field naming in JSON: **camelCase** (aligned with `05_API_Contract.md`).

### 8.1 Create Log — `POST /api/logs`

**Request**

```json
{
  "message": "1 cigarette pee li",
  "source": "web"
}
```

| Field | Type | Rules |
|-------|------|-------|
| `message` | string | Required; trimmed; min length 1; max length 2000 |
| `source` | string | Optional; trimmed; max length 32; **default `web`** if omitted/blank |

**Success response — `201 Created`**

```json
{
  "success": true,
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "SMOKING",
  "rawText": "1 cigarette pee li",
  "structuredJson": {
    "quantity": 1,
    "unit": "cigarette"
  },
  "source": "web",
  "parserVersion": "phase0-v1",
  "timestamp": "2026-08-16T20:00:00Z"
}
```

| Field | Meaning |
|-------|---------|
| `success` | Always `true` on 2xx |
| `id` | New row UUID |
| `eventType` | Stored type |
| `rawText` | Echo of stored raw text |
| `structuredJson` | Stored structured object |
| `source` | Client origin stored on the row |
| `parserVersion` | Parser version used for this row |
| `timestamp` | ISO-8601 `event_timestamp` |

**Note:** `05_API_Contract.md` listed a minimal response. This design freeze **extends** it with `id`, `rawText`, and `structuredJson` so the UI can update without an extra fetch. Minimal fields remain present.

**Error responses** — see §9.

---

### 8.2 Get Timeline — `GET /api/logs`

**Query params (Phase 0)**

| Param | Required | Default | Notes |
|-------|----------|---------|-------|
| none | — | — | Return all events; optional `limit` may be added later if volume hurts UX |

**Success response — `200 OK`**

```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "timestamp": "2026-08-16T20:00:00Z",
    "eventType": "SMOKING",
    "rawText": "1 cigarette pee li",
    "structuredJson": {
      "quantity": 1,
      "unit": "cigarette"
    },
    "source": "web",
    "parserVersion": "phase0-v1"
  }
]
```

Order: `event_timestamp DESC`, then `created_at DESC` for ties.

---

### 8.3 Search Logs — `GET /api/logs/search?q=cigarette&eventType=SMOKING`

**Query params**

| Param | Required | Rules |
|-------|----------|-------|
| `q` | yes | Trimmed; min length 1; max length 200 |
| `eventType` | no | Optional filter; must be a valid `EventType` when present |

**Success response — `200 OK`**

Same item shape as timeline array.

- Match: case-insensitive substring on `raw_text`
- If `eventType` is provided: also require `event_type = eventType`
- Order: newest first

Empty array if no matches (not an error). Invalid `eventType` → `400 VALIDATION_ERROR`.

---

### 8.4 Shared error body

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "message must not be blank"
  }
}
```

---

## 9. Error Handling Strategy

### 9.1 Error codes

| Code | HTTP | When |
|------|------|------|
| `VALIDATION_ERROR` | 400 | Blank/oversized `message` or `q` |
| `PARSE_FAILED` | 502 | OpenAI unreachable, timeout, or invalid model JSON after retries |
| `PERSISTENCE_ERROR` | 500 | DB write/read failure |
| `INTERNAL_ERROR` | 500 | Unexpected server fault |

### 9.2 Create-log failure modes

| Failure | Behavior |
|---------|----------|
| Invalid request | 400; nothing stored |
| OpenAI fails / bad JSON | **Do not invent analytics.** Either: (A) store as `UNKNOWN` with `structured_json` explaining parse failure and return 201, **or** (B) return 502 and store nothing. **Freeze: Option A** — prefer collecting truth (raw text) over losing the log. |
| DB fails after successful parse | 500; no partial silent success; client may retry |

**Option A detail (frozen):**

1. Attempt OpenAI parse
2. On failure → `event_type = UNKNOWN`, `structured_json = { "parseError": true, "source": "openai" }`, still save `raw_text`; set `parser_version` to the attempted parser version
3. Return 201 with `eventType: "UNKNOWN"`
4. Log server-side warning with cause (no secrets)

**Config constant:** `PARSER_VERSION` (e.g. `phase0-v1`) is set by the backend on every create; clients do not supply it.

This matches Phase 0 success: **user can still log for 14 days** even if the model blips.

### 9.3 OpenAI client errors

- Timeouts: treat as parse failure → UNKNOWN path
- Non-200 from OpenAI: parse failure → UNKNOWN path
- Schema mismatch: attempt best-effort map; if `event_type` invalid → `UNKNOWN`

### 9.4 Search / timeline errors

- Invalid `q` → 400
- DB errors → 500 with `PERSISTENCE_ERROR`

### 9.5 Frontend error UX

- Show `error.message` near the input or as a banner
- On UNKNOWN success, still treat as success (log saved); optionally show neutral label “Unclassified”
- Do not block further logging after an error

### 9.6 What we explicitly do not do

- No retry storms against OpenAI beyond **one** retry with short backoff
- No dead-letter queues
- No user-facing stack traces

---

## 10. OpenAI Parsing Flow

### 10.1 Goal

Convert `message` → `{ eventType, structuredJson }` only. No advice, no coaching language in the model instructions.

### 10.2 Sequence

```
POST /api/logs
    → validate message
    → build prompt (system + user message)
    → call OpenAI Chat Completions (or Responses API — freeze one at implementation)
    → parse model output as JSON
    → normalize eventType to enum
    → insert event_logs
    → return CreateLogResponse
```

### 10.3 Model I/O contract

**Input to model:** raw user string (Hinglish / English allowed).

**Required model output (JSON object only):**

```json
{
  "eventType": "SMOKING",
  "structured": {
    "quantity": 1,
    "unit": "cigarette"
  }
}
```

| Field | Rules |
|-------|-------|
| `eventType` | One of the nine supported values |
| `structured` | Object; may be empty |

Use JSON mode / response format if the chosen OpenAI API supports it, to reduce parse errors.

### 10.4 System prompt responsibilities (design)

The system prompt must:

- Classify into the fixed enum only
- Extract obvious structured fields when present
- Use `NOTE` for clear freeform notes
- Use `UNKNOWN` when classification is unclear
- **Never** add calorie estimates, recommendations, or moral commentary
- Preserve meaning of Hinglish phrases (e.g. “pee li”, “kha liye”)

Exact prompt text is finalized at implementation; behavior above is frozen.

### 10.5 Normalization rules

| Condition | Result |
|-----------|--------|
| Unknown / misspelled type from model | `UNKNOWN` |
| `structured` null | `{}` |
| Extra model fields | Dropped; not stored |
| Empty user message | Rejected before OpenAI |

### 10.6 Cost / latency posture

- One model call per create
- No embeddings
- No multi-step tool calling
- Prefer the smallest capable model that classifies reliably (name chosen at implementation; config-driven)

---

## 11. Local Development Setup

### 11.1 Prerequisites

- JDK 17+ (or version chosen at implementation; document in README then)
- Node.js LTS
- Docker (recommended for PostgreSQL) **or** local PostgreSQL 14+
- OpenAI API key

### 11.2 Services

| Service | Default |
|---------|---------|
| PostgreSQL | `localhost:5432`, database `devfuel`, user/password via env |
| Backend | `http://localhost:8080` |
| Frontend | `http://localhost:5173` (Vite default) or CRA equivalent |

### 11.3 Startup order

1. Start PostgreSQL; apply schema (`10_Schema.sql` / Flyway)
2. Export `OPENAI_API_KEY` and datasource env
3. Start Spring Boot
4. Start React app with `VITE_API_BASE_URL=http://localhost:8080`
5. Smoke test: create log → timeline → search

### 11.4 Suggested Docker Compose (design only)

Single `postgres` service with volume + port `5432`. Backend/frontend run on host for faster iteration in Phase 0. Full containerization of app is optional and not required for design freeze.

### 11.5 Seed data

Optional: none required. Empty DB is valid. Manual logs during the 14-day run are the real dataset.

---

## 12. Cross-Cutting Decisions (Frozen)

| Topic | Decision |
|-------|----------|
| Multi-user | No |
| Soft delete | No |
| Edit/delete logs | No (Phase 0) |
| Pagination | No (revisit if timeline becomes heavy) |
| i18n framework | No; accept Hinglish in free text |
| Tests (design intent) | Unit-test parser normalization; one integration test for create→list if time allows — not a product feature |
| Backlog items (`07_Backlog.md`) | Explicitly excluded until Phase 0 succeeds |

---

## 13. Design Freeze Checklist

Before writing application code, reviewers confirm:

- [ ] Backend layering and no-auth decision
- [ ] Frontend regions (log / timeline / search) only
- [ ] Schema matches `10_Schema.sql`
- [ ] Entity fields match schema
- [ ] Three endpoints and JSON contracts
- [ ] OpenAI single-call parse + UNKNOWN fallback on failure
- [ ] Error codes and Option A persistence on parse failure
- [ ] Local setup path is runnable
- [ ] Folder structure in `11_Project_Structure.md` is acceptable

---

## 14. Related Documents

| Doc | Role |
|-----|------|
| `02_MVP_Scope.md` | In / out of scope |
| `04_Data_Model.md` | Conceptual model |
| `05_API_Contract.md` | Original API sketch (superseded in detail by §8 here) |
| `06_Architecture.md` | Stack choice |
| `08_Guardrails.md` | Scope discipline |
| `10_Schema.sql` | Executable schema |
| `11_Project_Structure.md` | Repo layout |
