# Weight Logging — Implementation Plan

**Branch:** `feature/weight-logging` (from `main` @ `2ee7f86`)  
**Status:** Plan only. No product code in this document’s first commit except this file.  
**Depends on:** Backdated Logging Phase 1 (`event_timestamp` vs `logged_at`) and Timeline UI.  
**Out of scope:** Charts, prediction, Ask DevFuel, PATCH of arbitrary logs, multi-user accounts, unit conversion UI (lb).

---

## 1. Executive summary

Weight is already a first-class **event type** (`EventType.WEIGHT`) on `event_logs`. The parser prompt already maps `"Weight 93.5"` to `{ "value": 93.5, "unit": "kg" }`. What the product lacks is:

1. **Reliable classification** when OpenAI is down or the model returns `UNKNOWN`.
2. **A query surface** for “latest” and “history” that does not dump the whole timeline.
3. **A dedicated UX** so daily weigh-ins are fast and the latest number is always visible.
4. **A date-honest series** so “kal weight 93.5” lands on yesterday (already solved by TemporalResolver) and the widget reads **occurrence day**, not submit time.

This sprint **reuses `event_logs`**. It does not add a `weight_entries` table. Analytics later can still project a typed series from JSON + timestamp.

**Business value:** recovery and body-composition work need a trusted kg series. A lying series (capture time, missed parses) is worse than no widget.

---

## 2. Architecture audit (current)

| Layer | What exists | Gap |
| --- | --- | --- |
| Schema | `event_logs.event_type` includes `WEIGHT`; jsonb `structured_json`; V3 temporal columns | No WEIGHT-specific index; no numeric generated column |
| Parser | OpenAI prompt example for weight; `EventParserService` falls back to `UNKNOWN` if no API key | No deterministic kg extractor; live product often has no key in tests / local |
| Create | `POST /api/logs` + TemporalResolver | No typed `POST /api/weight`; NL path is the only path |
| Read | `GET /api/logs` full timeline | `GET /api/logs/search` returns **empty list** (stub). No latest/history endpoints |
| UI | Timeline shows `WEIGHT` like any other type; JSON dump of `structuredJson` | No widget, no history list, no dedicated input |
| Tests | Resolver tests include `"15 Aug ko weight 93.5"` for **time**, not value | No tests that `eventType == WEIGHT` and `value == 93.5` |

**Reuse, do not replace:** `EventLog`, `LogService.createLog`, `TemporalResolver`, `CreateLogResponse` flags (`loggedLater`, precision), Flyway, frontend `timelineDisplay` / `BackdatedNotice`.

---

## 3. Product requirements (this sprint)

| ID | Requirement | Notes |
| --- | --- | --- |
| W1 | Daily weight logging | Typed form **and** natural language (`Weight 93.5`, `kal weight 93.4`) |
| W2 | Historical weight history | Chronological series by **occurrence** (`event_timestamp`) |
| W3 | Latest weight widget | Always-visible kg + local date of that reading |
| W4 | Trend-ready series | Stable JSON contract (`value`, `unit`, `timestamp`) for a future chart; **no chart this sprint** |
| W5 | Honest dates | Reuse TemporalResolver; widget/history use occurrence, not `logged_at` |
| W6 | Same-day multiples | **Allowed.** Widget uses the latest `logged_at` among rows whose local occurrence date is the most recent measurement date. Do not unique-constrain yet (blocks catch-up corrections). |

**Units:** store and display **kg**. Reject lb in v1 (or convert in parser later). Range: `30.0`–`250.0` inclusive, one decimal.

---

## 4. Data model design

### 4.1 Persist in `event_logs` (no new table)

A weight reading is an `EventLog` with:

| Column | Rule for WEIGHT |
| --- | --- |
| `event_type` | `WEIGHT` |
| `structured_json` | `{ "value": 93.5, "unit": "kg" }` — `value` is JSON number, not string |
| `event_timestamp` | Occurrence (today noon / period / explicit date via TemporalResolver; typed API uses selected local date at 12:00 `Asia/Kolkata`) |
| `logged_at` | Submit time (existing) |
| `event_time_precision` | `NOW` / `DAY` / `PERIOD` / `EXACT` as resolver decides; typed date picker → `DAY` |
| `raw_text` | User message, or synthesized `"Weight 93.5 kg"` for the form |

### 4.2 Why not a `weight_logs` table

- One identity, one timeline, one backdate pipeline.
- Ask DevFuel and recovery later join on `event_logs`.
- Split tables would duplicate TemporalResolver application and Flyway risk.

A typed table is a **later** optimization if JSON aggregation becomes a bottleneck (it will not at personal volume).

### 4.3 Optional denormalized date (recommended, additive)

Flyway **V4** (see §7):

```sql
-- Expression index for history queries (Postgres 16)
CREATE INDEX idx_event_logs_weight_ts
  ON event_logs (event_timestamp DESC, logged_at DESC)
  WHERE event_type = 'WEIGHT';
```

Do **not** add a unique index on “one row per local day” in v1.

Optional later (not required for widget): a generated `measurement_local_date` column. Skip in v1 to keep V4 tiny; compute local date in Java with `eventTimezone` (already on the row).

### 4.4 Canonical JSON schema (freeze)

```json
{ "value": 93.5, "unit": "kg" }
```

- Extra keys from the LLM are allowed but ignored by the weight API mapper.
- If `value` is missing or unparseable after classification, treat as **invalid weight** (see parser): do not persist as WEIGHT with empty `{}`.

---

## 5. API design

Keep `POST /api/logs` working. Add a small **weight facade** so the UI does not scrape the timeline.

### 5.1 `POST /api/weight`

Request:

```json
{
  "value": 93.5,
  "unit": "kg",
  "occurrenceDate": "2026-08-20",
  "source": "web"
}
```

| Field | Required | Semantics |
| --- | --- | --- |
| `value` | yes | kg, 30–250, one decimal |
| `unit` | no | default `kg`; only `kg` accepted |
| `occurrenceDate` | no | `YYYY-MM-DD` in `Asia/Kolkata`. Omit = today. Past dates set precision `DAY` at 12:00 local |
| `source` | no | default `web` |

Behavior: validate → build `raw_text` → persist via shared `LogService` helper (same `logged_at` / timezone path as NL create). Response: reuse `CreateLogResponse` so `loggedLater` / `backdated` stay consistent.

### 5.2 Natural language (existing)

`POST /api/logs` `{ "message": "Kal weight 93.5" }`

After this sprint, parser **must** yield `WEIGHT` + `{value, unit}` **without** requiring OpenAI (deterministic first, LLM second).

### 5.3 `GET /api/weight/latest`

Returns `200` with:

```json
{
  "id": "...",
  "value": 93.5,
  "unit": "kg",
  "timestamp": "2026-08-21T06:30:00Z",
  "loggedAt": "...",
  "eventTimePrecision": "NOW",
  "loggedLater": false,
  "localDate": "2026-08-21"
}
```

`404` if no valid WEIGHT rows (`value` present).

**Selection rule:** among rows with parseable `value` and `event_type = WEIGHT`, pick max `event_timestamp`, then max `logged_at`.

### 5.4 `GET /api/weight`

Query: optional `from`, `to` as `YYYY-MM-DD` (inclusive, `Asia/Kolkata`).

Response:

```json
{
  "unit": "kg",
  "points": [
    { "id": "...", "value": 93.5, "timestamp": "...", "localDate": "2026-08-20", "loggedLater": true }
  ]
}
```

Order: `event_timestamp ASC` (trend-friendly). Cap: 365 points; document it.

### 5.5 Compatibility

- Do not change existing timeline JSON except that WEIGHT rows become more often correctly typed.
- Do not implement PATCH this sprint.
- Do not wire `GET /api/logs/search` except if a test needs it; out of scope unless trivial.

---

## 6. Frontend UX design

Keep the existing compose + timeline. Add a **Weight strip** above the timeline (below compose), not a new app.

### 6.1 Latest weight widget

- Large number: `93.5 kg`
- Subline: local date (`Today` / `Yesterday` / `18 Aug 2026`) using `timelineDisplay.dateGroupLabel`
- Empty state: `No weight yet. Log kg below.`
- After NL or form submit of WEIGHT, refresh widget (same `refreshTimeline` plus `getLatestWeight`)

### 6.2 Daily log control

Compact row: number input (step `0.1`) + optional date (`type="date"`, default today) + `Save weight`.

Do not replace `LogInput`. Power users still type `"kal weight 93.4"` in the main box; widget still updates.

### 6.3 History

Collapsible `"Weight history"` under the widget: last 14 points as a simple list (`localDate — 93.5 kg`), not a chart. Full list via the same `GET /api/weight`.

### 6.4 Timeline

If `eventType === 'WEIGHT'` and `structuredJson.value` is a number, show **`93.5 kg`** instead of raw JSON stringify (small change to `LogListItem`). Keep Logged later badge.

### 6.5 Accessibility

Number input labelled `Weight in kilograms`. Widget `aria-live="polite"` on update.

---

## 7. Database migration plan

**V4__weight_query_index.sql**

1. Partial index on WEIGHT timestamps (exact SQL in §4.3).
2. Comment: analytics reads `structured_json->>'value'`.
3. **No** CHECK on JSON (Postgres JSON CHECKs are brittle with LLM extras).
4. Rollback: `DROP INDEX idx_event_logs_weight_ts;` (forward-only Flyway as usual; document).

**Not in V4:** new tables, unique-per-day, generated columns.

Existing WEIGHT rows (if any) stay; widget skips rows without numeric `value`.

---

## 8. File-wise implementation plan

### 8.1 Backend — new

| File | Why |
| --- | --- |
| `backend/src/main/java/com/devfuel/weight/WeightValue.java` | Parse/validate kg from JSON or form |
| `backend/src/main/java/com/devfuel/weight/WeightParser.java` | Deterministic NL: `weight 93.5`, `93.5 kg`, Hinglish `vajan`/`kg` |
| `backend/src/main/java/com/devfuel/weight/WeightService.java` | latest, history, typed create; maps EventLog → DTO |
| `backend/src/main/java/com/devfuel/weight/WeightController.java` | `/api/weight`, `/api/weight/latest` |
| `backend/src/main/java/com/devfuel/weight/dto/*.java` | request/response records |
| `backend/src/main/resources/db/migration/V4__weight_query_index.sql` | Index only |
| `backend/src/test/java/com/devfuel/weight/WeightParserTest.java` | Table-driven NL |
| `backend/src/test/java/com/devfuel/weight/WeightServiceTest.java` | Latest/history rules |
| `backend/src/test/java/com/devfuel/weight/WeightLoggingIT.java` | HTTP + Postgres |

### 8.2 Backend — update

| File | Change |
| --- | --- |
| `EventParserService` | If `WeightParser` matches, **override** LLM/`UNKNOWN` to WEIGHT + structured kg (OpenAI still used when parser does not match) |
| `EventLogRepository` | `findByEventTypeOrderByEventTimestampDescLoggedAtDesc(EventType)` |
| `LogService` | Package-visible persist helper **or** WeightService calls existing `createLog` with synthesized message — prefer **one persist path** (`createLog`) for NL; typed API synthesizes message then same path |
| `OpenAiParserClient` | Extra examples: `kal weight 93.5`, `93.4 kg` (prompt only; not a substitute for WeightParser) |
| `CorsConfig` | Unchanged if already `/**` |

### 8.3 Frontend — new

| File | Why |
| --- | --- |
| `frontend/src/features/weight/WeightWidget.tsx` | Latest + empty state |
| `frontend/src/features/weight/WeightForm.tsx` | kg + date |
| `frontend/src/features/weight/WeightHistory.tsx` | Compact list |
| `frontend/src/api/weightApi.ts` | Client |
| `frontend/src/types/weight.ts` | DTO types |

### 8.4 Frontend — update

| File | Change |
| --- | --- |
| `App.tsx` | Mount widget + form; refresh weight after any log create if `eventType === 'WEIGHT'` |
| `App.css` | Widget / form styles matching existing stone/green language |
| `LogListItem.tsx` | Human kg label for WEIGHT |
| `logsApi` / types | No breaking changes |

### 8.5 Docs

Update this plan’s progress in `docs/weight_logging_progress.md` **during** implementation (not now).

---

## 9. Testing strategy

### 9.1 Unit

- WeightParser: `Weight 93.5`, `93.5 kg`, `kal weight 93.4` (value only; date still TemporalResolver), reject `5 kg rice`, reject `weight loss`, reject `400 kg`.
- WeightValue: rounding to 1 decimal, bounds.
- Latest: two rows same timestamp → higher `logged_at` wins; backdated yesterday vs today morning → today wins on timestamp.

### 9.2 Integration (Postgres `devfuel_test`)

- `POST /api/weight` `{value:93.5}` → 201, `event_type=WEIGHT`, V4 index unused but schema valid.
- `POST /api/logs` `"Weight 93.5"` **without** OpenAI key → WEIGHT (proves parser override).
- `GET /api/weight/latest` after two posts.
- `GET /api/weight?from=&to=` range.
- `"Kal weight 93.5"` → `loggedLater=true`, history point on yesterday local date.

### 9.3 Frontend

- `npm run build && npm run lint`.
- Manual: widget empty → save → widget updates; timeline shows `93.5 kg`; backdated kal still shows notice.

### 9.4 Acceptance (product)

See §11.

---

## 10. Implementation sequence

1. `WeightValue` + `WeightParser` + unit tests.  
2. `EventParserService` override + parser tests.  
3. Flyway V4.  
4. Repository query + `WeightService` + DTOs + controller.  
5. Typed `POST /api/weight`.  
6. Integration tests.  
7. Frontend types/API + widget + form + history.  
8. Timeline kg label.  
9. Manual smoke on live DB after V4.  

Do not start charts or Ask in this sequence.

---

## 11. Acceptance criteria

The sprint is done only if all are true:

- [ ] Daily kg can be saved from the dedicated form without using the LLM.  
- [ ] `"Weight 93.5"` via `POST /api/logs` persists `WEIGHT` with `value=93.5` even when `OPENAI_API_KEY` is unset.  
- [ ] `"Kal weight 93.5"` appears under **Yesterday** and in weight history on yesterday’s `localDate`.  
- [ ] Widget shows the latest occurrence reading, not the latest submit.  
- [ ] History is ordered by occurrence and is usable as a trend series (`points[].value` + `timestamp`).  
- [ ] Same-day second reading does not 409; widget reflects the later capture.  
- [ ] Timeline WEIGHT rows show `93.5 kg`, not only raw JSON.  
- [ ] `mvn test` green; `npm run build` green.  
- [ ] No new table; V4 is index-only.  
- [ ] No chart library added.

---

## 12. Risks and recommendations

| Risk | Mitigation |
| --- | --- |
| LLM classifies `"ate 93.5"` as WEIGHT | WeightParser must require a weight cue (`weight`/`kg`/`vajan`/`wajan`), not a bare decimal |
| `"2 kg paneer"` | Food cues + kg of food → do not match WeightParser; leave to LLM/FOOD |
| Missing OpenAI today | Parser override is **mandatory**; otherwise the widget stays empty in local/prod-without-key |
| Same-day duplicates distort a future chart | v1 keep both; chart sprint can downsample to last-per-`localDate` |
| Unique-per-day constraint | **Do not** add; catch-up logging will fight it |
| Search stub still empty | Unrelated; do not expand scope |
| `cursor/backdated-logging` leftover | Safe to delete locally/remotely after this main merge (contained in `2ee7f86`) |
| Flyway on live `devfuel` | V4 is additive index; low risk on next backend start |

**Recommendation:** ship parser + read APIs + widget before any Recharts/prediction work. The series is the product; the sparkline is decoration.

**Non-goals reminder:** PATCH, Ask DevFuel, multi-profile, Apple Health import.
