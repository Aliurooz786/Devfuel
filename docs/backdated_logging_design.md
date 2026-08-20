# DevFuel V2 — Feature #1: Backdated Logging

**Status:** Design only. Do not implement from this document until explicitly approved.  
**Audience:** Engineering, product, QA.  
**Depends on:** Phase 0 truth collection (`event_logs`, `POST /api/logs`, timeline).  
**Unblocks:** Honest timelines, recovery logging, and Ask DevFuel time-window queries.

---

## Design principles

1. **Two clocks, never one.** Every event has an occurrence time and a capture time. Mixing them is a data bug, not a UX preference.
2. **Calendar days are local.** “Kal”, “yesterday”, and “last week” resolve in the user’s timezone (`Asia/Kolkata` for this product), not UTC and not “24 hours ago”.
3. **Tense beats the dictionary.** In Hindi/Hinglish, **kal** can mean yesterday or tomorrow. Past markers (`thi`, `tha`, `khaya`, `pi`, `liye`) select the past. The life-log default is **past-biased**.
4. **Do not invent false precision.** If the user said “kal raat”, store a night bucket, not a fake `21:07:14`.
5. **Never rewrite history on migrate.** Existing rows keep their current `event_timestamp`. Backfill only the new capture column.
6. **Ask DevFuel answers from occurrence time.** “What did I eat yesterday?” must not include food logged yesterday about the day before, and must include food logged today about yesterday.

---

## 1. Problem Statement

### 1.1 Current behavior

Phase 0 stores one meaningful instant for “when”:

| Column | What it actually is today |
|--------|---------------------------|
| `event_timestamp` | Server `Instant.now()` at successful create (`LogService.createLog` / `createImageLog`) |
| `created_at` | Same instant (row insert) |
| `updated_at` | Same at insert; trigger updates on row change |

The natural-language parser classifies **what happened** (`eventType` + `structured_json`). It does **not** extract when it happened. The system prompt has no temporal schema.

The public API exposes a single `timestamp` field, documented as `event_timestamp`. Timeline sorts `event_timestamp DESC, created_at DESC`. There is no `logged_at`, no time-precision flag, and no update endpoint.

`09_Technical_Design.md` §5.4 made this explicit: *user does not supply a custom event time; future backdating is out of scope until Phase 0 succeeds.*

### 1.2 Why current behavior is incorrect

DevFuel is a **truth collection** system. Truth for a life event is **when it occurred**, not when the user remembered to type it.

If occurrence and capture are collapsed:

- The timeline is a **memory log**, not a **life log**.
- Daily / weekly counts (cigarettes, meals) attach to the wrong calendar day.
- Recovery journeys look like binge days: three missed meals appear as “today” because they were typed today.
- Ask DevFuel (V2) cannot answer “yesterday” or “last week” without lying or missing data.
- Later analytics (smoking, food, sleep) inherit a systematic bias toward logging-session days (evenings, weekends, post-relapse catch-up).

Phase 0 was a valid freeze for 14-day capture. V2 cannot keep that freeze if the product is to be honest.

### 1.3 Real examples (failure under current behavior)

Assume the user submits on **Thursday 20 Aug 2026, 17:00 IST**.

| User text | What should be stored | What is stored today |
|-----------|----------------------|----------------------|
| “Kal raat tahri khayi thi” | FOOD, **Wed 19 Aug ~21:00 IST** | FOOD, Thu 20 Aug 17:00 IST |
| “Parso 2 cigarette pi thi” | SMOKING qty 2, **Tue 18 Aug** (day, time unknown) | SMOKING, Thu 20 Aug 17:00 IST |
| “Kal office lunch me samosa khaya tha” | FOOD samosa, **Wed 19 Aug ~13:00 IST** | FOOD, Thu 20 Aug 17:00 IST |

The raw text still contains the truth. The **indexed time** does not. Search by substring can find “tahri”; a day filter cannot.

---

## 2. User Stories

Format: *As a user / I want / So that*. Stories are acceptance-oriented, not implementation.

### 2.1 Core logging

**US-1 — Same-day implicit now**  
As a user, I want “1 cigarette pee li” with no date words to log as **now**, so ordinary logging stays a 5-second action.

**US-2 — Today explicit**  
As a user, I want “Aaj subah 2 cigarette pi” to land on **today morning**, so morning vs evening is preserved even if I log at night.

**US-3 — Yesterday (English)**  
As a user, I want “yesterday I had tahri” to attach to **yesterday’s calendar date** in my timezone.

**US-4 — Kal (Hinglish, past)**  
As a user, I want “Kal raat tahri khayi thi” to attach to **yesterday night**, not this evening’s clock.

**US-5 — Parso**  
As a user, I want “Parso 2 cigarette pi thi” to attach to **the calendar day before yesterday**.

**US-6 — Explicit calendar date**  
As a user, I want “15 Aug ko weight 93.5 tha” (and ISO-like “2026-08-15”) to attach to that **date**, so I can fill gaps without relative language.

**US-7 — Time of day**  
As a user, I want “subah / dopahar / lunch / shaam / raat / 9 pm / 21:30” to influence **time of day**, not only the date.

**US-8 — Image + note**  
As a user, I want a photo with note “kal ki tahri” to use the **same temporal rules** as text logs.

### 2.2 Recovery journey (first-class)

Recovery is the primary reason backdating exists. People do not fail to live; they fail to open the app.

**US-9 — Catch-up after a missed day**  
As a user who did not open DevFuel yesterday, I want to dump yesterday’s meals and smokes **today**, and still see them on **yesterday** in the timeline.

**US-10 — Relapse honesty**  
As a user who smoked after a gap, I want “parso 4 cigarette” and “kal 2” to count on **those days**, so a single catch-up session does not look like a 6-cigarette Thursday.

**US-11 — Multi-event paste**  
As a user, I want to send **one message per event** (current product: one row per `POST`). Catch-up is several submits, each with its own relative date. (Multi-event split in one message is **out of scope** for Feature #1; if the model sees two events, still persist **one** row as today unless we later add splitting.)

**US-12 — Review the reconstructed day**  
As a user, I want yesterday’s timeline group to show catch-up events **in occurrence order**, with a **logged later** mark, so I can tell memory from live logging.

**US-13 — Ask after catch-up**  
As a user, I want “What did I eat yesterday?” after a morning catch-up to include **kal’s food**, not only food typed yesterday.

**US-14 — Streak / guilt reduction**  
As a user, I want missing a logging day **not** to erase that day’s life, so I continue logging instead of abandoning the product.

### 2.3 Timeline and trust

**US-15 — Actual event time**  
As a user, I want each row to show **when it happened** (date + time or period label).

**US-16 — Logged-later indicator**  
As a user, I want a clear **Logged later** (or equivalent) when capture time is not the occurrence time, so I do not think the system “moved” my day silently.

**US-17 — Sort by life, not by typing**  
As a user, I want the default timeline ordered by **occurrence**, newest first (Phase 0 sort preserved, key corrected).

### 2.4 Correction and duplicates

**US-18 — Fix the time of an old event**  
As a user, I want to correct “I meant parso, not kal” without creating a second event and without changing **when I originally logged it**.

**US-19 — Duplicate awareness**  
As a user, I want the system to **warn** if I likely double-logged the same cigarette/meal, but **not block** me (two samosas at lunch is valid).

### 2.5 Non-goals (explicit)

- No coaching, calories, or “you forgot to log” nags in this feature.
- No multi-user timezones in v1 of this feature (single-user; fixed `Asia/Kolkata`).
- No natural-language **range** in one log (“all last week I smoked”) as a single event. That is Ask, not create.
- No automatic rewrite of historical Phase 0 timestamps from `raw_text`.

---

## 3. Functional Requirements

### 3.1 Timezone and “now”

| Rule | Decision |
|------|----------|
| Default timezone | `Asia/Kolkata` (IST, UTC+5:30) |
| Clock source | Server UTC instant, converted to local for calendar math |
| Optional later | `X-User-Timezone` or client `loggedAt` — **not** in Feature #1 |
| Day boundary | Local midnight `00:00:00.000` → next midnight |

“Now” for implicit timestamps is **request handling time**, not OpenAI latency completion time. Capture `Instant now` once at the start of `createLog` and pass it through parse + persist.

### 3.2 Relative calendar tokens

All of the following must resolve against **local calendar dates**, not rolling 24h windows.

| Family | Tokens (non-exhaustive) | Meaning |
|--------|-------------------------|---------|
| Today | aaj, aaj raat, today, tonight (if still same local date) | Current local date |
| Yesterday | kal *(past tense)*, yesterday, last night *(if after local midnight, last night = previous date)* | Previous local date |
| Day before yesterday | parso, parson, day before yesterday | Current local date minus 2 |
| Older relative | 3 din pehle, last Monday | Resolve if unambiguous; else DAY precision on best date or fall back to now + `time_resolution = UNRESOLVED` |

**Hindi “kal” disambiguation (normative):**

1. If the utterance has **past** aspect (`tha`/`thi`/`the`, `khaya`, `pi`, `li`, `hui`) → **yesterday**.
2. If **future** aspect (`unga`/`ungi`, `khaunga`, `will`) → **do not treat as occurred yesterday**. Feature #1: persist as `NOTE` or original type with `event_timestamp` = **tomorrow** only if explicitly future; Ask **excludes** future timestamps from “what did I eat/smoke”. Prefer documenting this in `temporal` metadata `tense: future`.
3. If no tense → **yesterday** (life-log prior).

### 3.3 Explicit dates

Must support:

- ISO date: `2026-08-18`, `18-08-2026`
- Spoken: `15 August`, `15 Aug`, `August 15`, `15 tarikh`, `pichle Sunday` when uniquely resolvable in the current year (if that date is in the future, prefer **previous** occurrence of that month-day unless year is given)

Ambiguous `03/04/2026`: interpret as **DMY** (India).

### 3.4 Time references

| User language | Precision | Stored clock (local, then UTC) |
|---------------|-----------|--------------------------------|
| `9 pm`, `21:30`, `saade nau` | `EXACT` | That time on the resolved date |
| `subah`, `morning` | `PERIOD` | **08:00** local |
| `dopahar`, `afternoon` | `PERIOD` | **14:00** local |
| `lunch`, `office lunch` | `PERIOD` | **13:00** local |
| `shaam`, `evening` | `PERIOD` | **18:00** local |
| `raat`, `night`, `last night` | `PERIOD` | **21:00** local |
| Date only / parso with no tod | `DAY` | **12:00** local (noon sentinel) |
| No temporal language | `NOW` | Request `now` (full instant) |

Sentinel times are **deterministic defaults**, not claims of accuracy. UI and Ask must not say “exactly 21:00” when precision is `PERIOD`; they may say “night” / “evening”.

### 3.5 Worked examples

Clock: **Thu 20 Aug 2026, 17:10 IST**. Timezone `Asia/Kolkata`.

| Input | Date | Time (local) | Precision | `event_timestamp` (UTC) |
|-------|------|--------------|-----------|-------------------------|
| `1 cigarette pee li` | 20 Aug | 17:10 | `NOW` | `2026-08-20T11:40:00Z` |
| `Aaj subah 2 cigarette pi` | 20 Aug | 08:00 | `PERIOD` | `2026-08-20T02:30:00Z` |
| `Kal raat tahri khayi thi` | 19 Aug | 21:00 | `PERIOD` | `2026-08-19T15:30:00Z` |
| `Parso 2 cigarette pi thi` | 18 Aug | 12:00 | `DAY` | `2026-08-18T06:30:00Z` |
| `Kal office lunch me samosa khaya tha` | 19 Aug | 13:00 | `PERIOD` | `2026-08-19T07:30:00Z` |
| `Yesterday morning mood kharab tha` | 19 Aug | 08:00 | `PERIOD` | `2026-08-19T02:30:00Z` |
| `15 Aug ko weight 93.5` (year current) | 15 Aug 2026 | 12:00 | `DAY` | `2026-08-15T06:30:00Z` |

### 3.6 Capture time

Every create sets:

- `logged_at` = request `now` (**immutable** after insert)
- `created_at` = same as `logged_at` at insert
- `event_timestamp` = resolved occurrence (may equal `logged_at`)

### 3.7 Backdated flag (derived)

An event is **backdated** when occurrence is not “this logging moment”:

```
backdated = (time_precision != NOW)
            OR abs(event_timestamp - logged_at) > 15 minutes
```

`NOW` events within 15 minutes are live logs. `PERIOD`/`DAY` on **today** still show a softer indicator only if the sentinel is far from `logged_at` (e.g. “aaj subah” logged at 17:10 → show period “morning”, optionally “logged this evening”). Product copy:

- Different **local date**: always **Logged later**
- Same date, precision `NOW`: no badge
- Same date, precision `PERIOD`/`DAY`: show period label; badge optional (“Logged at 5:10 pm”)

### 3.8 Parser pipeline (behavioral, not code)

1. Stamp `now` (UTC) + zone.
2. Classify event type + structured fields (existing).
3. Extract temporal span(s) via **deterministic resolver first** (regex/token map for aaj/kal/parso/yesterday/dates/clocks), LLM **only** as assist for leftovers.
4. If LLM and deterministic disagree on **date**, deterministic wins for known tokens (`kal`, `parso`, `yesterday`).
5. Persist type, structure, occurrence, precision, raw temporal parse in `structured_json.temporal` **and** first-class columns (see §4).

LLM must receive `nowIso`, `timezone`, and `localDate` in the prompt so it does not guess the year. It must **not** be the sole source of `event_timestamp`.

### 3.9 Image logs

Apply the same resolver to `note` if present; otherwise `NOW`. Vision description text is not a reliable date source unless it contains explicit dates.

---

## 4. Database Design

### 4.1 Current schema (as deployed)

From Flyway `V1__event_logs.sql` + `V2__image_logging.sql`:

| Column | Type | Role today |
|--------|------|------------|
| `id` | UUID PK | Identity |
| `event_timestamp` | TIMESTAMPTZ NOT NULL | **Create time** (mislabeled as occurrence) |
| `raw_text` | TEXT NOT NULL | User / vision text |
| `event_type` | VARCHAR(32) | Classifier |
| `structured_json` | JSONB | Type payload |
| `source` | VARCHAR(32) | Client |
| `parser_version` | VARCHAR(64) | Prompt stamp |
| `image_ref` | VARCHAR(512) NULL | Phase 5A |
| `raw_model_output` | JSONB NULL | Phase 5A |
| `created_at` | TIMESTAMPTZ NOT NULL | Insert |
| `updated_at` | TIMESTAMPTZ NOT NULL | Last row write |

Indexes: `event_timestamp DESC`, `event_type`.

Comment on `event_timestamp` still says: *when the life event is recorded (Phase 0: server time at create)*.

### 4.2 Proposed schema

**Keep** all existing columns. **Add**:

| Column | Type | Null | Meaning |
|--------|------|------|---------|
| `logged_at` | TIMESTAMPTZ | NOT NULL | When the user submitted this row. Immutable. |
| `event_time_precision` | VARCHAR(16) | NOT NULL | `NOW` \| `EXACT` \| `PERIOD` \| `DAY` \| `UNRESOLVED` |
| `event_timezone` | VARCHAR(64) | NOT NULL | IANA zone used to resolve (`Asia/Kolkata`) |

**Repurpose:**

| Column | New meaning |
|--------|-------------|
| `event_timestamp` | When the **life event occurred** (UTC instant). Sentinel clock for `PERIOD`/`DAY` as in §3.4. |
| `created_at` | Physical insert (equals `logged_at` at create; unused as product time). |
| `updated_at` | Last mutation (edits to text or occurrence). |

**Check constraint:** `event_time_precision IN ('NOW','EXACT','PERIOD','DAY','UNRESOLVED')`.

**Optional JSON (not queried as primary filter):**

```json
"temporal": {
  "rawSpan": "kal raat",
  "localDate": "2026-08-19",
  "localTime": "21:00:00",
  "period": "NIGHT",
  "tense": "past",
  "resolver": "deterministic",
  "confidence": 0.9
}
```

Store under `structured_json.temporal` so type-specific fields remain siblings (`quantity`, `item`, …). Do not require this key for old rows.

**Indexes:**

- Keep `(event_timestamp DESC)` — timeline + Ask windows.
- Add `(logged_at DESC)` — “what did I capture today?” / debug.
- Composite later if needed: `(event_type, event_timestamp DESC)` for Ask aggregations.

**Do not** drop `created_at`. Three timestamps is acceptable:

| Name | User-visible? | Mutates? |
|------|----------------|----------|
| `event_timestamp` | Yes (happened) | Yes, on explicit edit |
| `logged_at` | Yes (logged later) | **No** |
| `created_at` | No (ops) | No |
| `updated_at` | No (ops) | Yes |

Invariant: `logged_at >= created_at` is false if we set them equal at insert; after that `logged_at` stays, `updated_at` moves. **Invariant: `logged_at` never changes.**

### 4.3 `event_timestamp` vs `logged_at`

| Question | Column |
|----------|--------|
| When did I eat / smoke / weigh? | `event_timestamp` |
| When did I tell DevFuel? | `logged_at` |
| Should timeline order use? | `event_timestamp` |
| Should “logged later” use? | Compare local dates (and precision), §3.7 |
| Should Ask “yesterday” use? | `event_timestamp` in `Asia/Kolkata` |
| Should “what did I log this session?” use? | `logged_at` |

### 4.4 Migration strategy

Flyway **V3** (name illustrative):

1. `ADD COLUMN logged_at TIMESTAMPTZ NULL`
2. `UPDATE event_logs SET logged_at = created_at WHERE logged_at IS NULL`
3. `ALTER COLUMN logged_at SET NOT NULL`
4. `ADD COLUMN event_time_precision VARCHAR(16) NULL`
5. `UPDATE event_logs SET event_time_precision = 'NOW' WHERE event_time_precision IS NULL`  
   Rationale: Phase 0 **defined** timestamp as create time. Marking them `NOW` is honest. Do **not** NLP-backfill.
6. `ALTER COLUMN event_time_precision SET NOT NULL` + CHECK
7. `ADD COLUMN event_timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Kolkata'`
8. Update COMMENT on `event_timestamp`: occurrence time; Phase 0 rows = capture time.
9. Create `idx_event_logs_logged_at_desc`

**No downtime concerns** at current volume (single user, small table). One transaction is enough.

**Forbidden:** parsing historical `raw_text` to move `event_timestamp`. That silently corrupts the 14-day dataset. A **optional offline script** may *suggest* corrections; it must not auto-apply.

### 4.5 Backward compatibility

| Consumer | Behavior |
|----------|----------|
| Old frontend reading only `timestamp` | Still occurrence time. After V3, new logs may show **past** times — that is the feature. Old rows unchanged. |
| `CreateLogResponse.timestamp` | Remains `event_timestamp` (ISO-8601 UTC). Additive fields only. |
| JSON clients ignoring unknown fields | Safe. |
| `structured_json` without `temporal` | Valid. |
| Image rows | Same columns; null `image_ref` unchanged. |
| SQL `10_Schema.sql` | Documentation copy; Flyway remains source of truth for running DBs. |

API field `timestamp` is **not** renamed in Feature #1 (avoid breaking the SPA). Add `loggedAt` and `eventTimePrecision` beside it. A later version may deprecate `timestamp` in favor of `eventTimestamp`; not required now.

---

## 5. API Changes

No new resource. Additive fields. One new method for corrections.

### 5.1 Request models

**`POST /api/logs`** — unchanged required shape:

```json
{
  "message": "Kal raat tahri khayi thi",
  "source": "web"
}
```

| Field | Change |
|-------|--------|
| `message` | Unchanged (required, trim, max 2000) |
| `source` | Unchanged |
| `eventTimestamp` | **Not accepted in Feature #1.** Time comes from language. Prevents silent client clock skew as a second source of truth. |

**`POST /api/logs/image`** — unchanged multipart; `note` participates in temporal parse.

**`PATCH /api/logs/{id}`** — **new**, correction only (US-18):

```json
{
  "eventTimestamp": "2026-08-18T06:30:00Z",
  "eventTimePrecision": "DAY"
}
```

Rules:

- At least one of `eventTimestamp`, `message` (re-parse optional — **defer re-parse of type** unless product asks; Feature #1 PATCH is **time fields only** to reduce risk).
- `logged_at` must not be in the body; ignore if sent.
- `updated_at` changes.
- 404 if id missing.

**Out of scope:** `DELETE` (can be a later honesty tool).

### 5.2 Response models

**`CreateLogResponse` and `LogItemResponse`** — additive:

```json
{
  "success": true,
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "FOOD",
  "rawText": "Kal raat tahri khayi thi",
  "structuredJson": {
    "item": "tahri",
    "quantity": 1,
    "temporal": {
      "rawSpan": "Kal raat",
      "localDate": "2026-08-19",
      "period": "NIGHT",
      "tense": "past",
      "resolver": "deterministic"
    }
  },
  "source": "web",
  "parserVersion": "phase0-v1",
  "timestamp": "2026-08-19T15:30:00Z",
  "loggedAt": "2026-08-20T11:40:00Z",
  "eventTimePrecision": "PERIOD",
  "backdated": true,
  "loggedLater": true,
  "imageRef": null,
  "possibleDuplicate": false
}
```

| Field | Required | Meaning |
|-------|----------|---------|
| `timestamp` | yes | Occurrence (`event_timestamp`). **Existing clients keep working.** |
| `loggedAt` | yes after V3 | Capture time |
| `eventTimePrecision` | yes after V3 | Enum string |
| `backdated` | yes | §3.7 |
| `loggedLater` | yes | Same as product badge; may equal `backdated` or be “different local date only” — **freeze: `loggedLater` = different local calendar date OR precision not `NOW`** |
| `possibleDuplicate` | yes on create | Hint only |

Timeline/search items use the same item shape (`success` omitted).

### 5.3 Required endpoint changes

| Endpoint | Change |
|----------|--------|
| `POST /api/logs` | Resolve occurrence; set `logged_at`; return new fields |
| `POST /api/logs/image` | Same |
| `GET /api/logs` | Return new fields; **sort still** `event_timestamp DESC, logged_at DESC` (replace `created_at` as tie-break) |
| `GET /api/logs/search` | Same item shape; match still `raw_text` ILIKE; order by occurrence |
| `PATCH /api/logs/{id}` | New |
| Ask endpoints | Not built in Feature #1; **contract they must use** is §7 |

Errors: invalid PATCH instant → `400 VALIDATION_ERROR`. Unresolvable time → **still 201**, `eventTimePrecision = UNRESOLVED`, `event_timestamp = logged_at`, do not drop the log (Phase 0 collect-truth).

---

## 6. Timeline Impact

### 6.1 Sort and grouping

- Default order: **occurrence newest first** (`event_timestamp DESC`).
- Recommended UI grouping: **local date headings** (Today, Yesterday, Wed 19 Aug, …) using `Asia/Kolkata` of `event_timestamp`, not of `logged_at`.
- Within a day: occurrence time descending.

A catch-up session on Thursday will **insert rows under Wednesday and Tuesday headings**, not at the top as a Thursday cluster (except the live “now” logs).

### 6.2 Actual Event Time

Primary line on each card:

| Precision | Display |
|-----------|---------|
| `NOW` / `EXACT` | Localized time, e.g. `Thu 20 Aug, 5:10 pm` |
| `PERIOD` | Date + period label, e.g. `Wed 19 Aug · Night` (do not show `:00` as if exact) |
| `DAY` | Date only, e.g. `Tue 18 Aug` |
| `UNRESOLVED` | `Logged at <loggedAt>` plus muted “time unclear” |

### 6.3 Logged Later indicator

Show a badge **Logged later** when `loggedLater` is true.

Secondary line (always available, visually secondary):

`Logged Thu 20 Aug, 5:10 pm`

If same local date and precision `NOW`, omit secondary line.

### 6.4 Examples

**A. Catch-up Thursday 5:10 pm**

1. `Kal raat tahri khayi thi`  
   - Heading: **Yesterday** (19 Aug)  
   - Primary: Night  
   - Badge: Logged later  
   - Secondary: Logged Thu 20 Aug, 5:10 pm  

2. `Parso 2 cigarette pi thi`  
   - Heading: **Tue 18 Aug**  
   - Primary: Tue 18 Aug (day)  
   - Badge: Logged later  

3. `1 cigarette pee li` (just now)  
   - Heading: **Today**  
   - Primary: 5:10 pm  
   - No badge  

**B. Live evening log of this morning**

`Aaj subah 2 cigarette pi` at 5:10 pm:

- Heading: Today  
- Primary: Morning  
- Optional: Logged 5:10 pm (same day, period mismatch — still honest)

**C. Phase 0 historical row**

`event_timestamp == logged_at`, precision `NOW` → looks like today’s live logs. No badge. We do not pretend we recovered occurrence.

---

## 7. Ask DevFuel Impact

Ask DevFuel is **not implemented** in the repo today. Feature #1 is a **data contract** for it. If Ask ships against capture time, it will be wrong on day one.

### 7.1 Query clock

| Query class | Filter |
|-------------|--------|
| “Yesterday / kal / last week / on 15 Aug / today” | `event_timestamp` in zone `Asia/Kolkata` |
| “What did I log today?” / “when did I last open the app” | `logged_at` |
| Aggregations (counts, sums) | Occurrence windows + `event_type` + `structured_json` |

**Week:** ISO week in `Asia/Kolkata` unless copy says “last 7 days” (rolling). Product freeze for “last week”: **previous ISO week Monday 00:00 to Sunday 24:00 local**, not rolling 7×24h. “In the last 7 days”: rolling from local start of today minus 6 days 00:00 through now.

**Exclude** `event_timestamp` in the future from consumption/smoking/food answers, unless the question is about plans.

**Precision:** When summing cigarettes for a `DAY` event of quantity 2, still count 2 on that date. Do not spread across hours.

**UNRESOLVED:** Include in “all time” lists; exclude from strict day questions **or** include with a caveat “time wasn’t clear”. Freeze: **include in the day of `logged_at`** only if precision is `UNRESOLVED` (failed parse ≈ live log). Prefer not to guess a past day.

### 7.2 Examples

User catch-up **Thu 20 Aug 17:10 IST**:

- Wed 19 21:00 FOOD tahri (logged Thu)
- Tue 18 12:00 SMOKING qty 2 (logged Thu)
- Thu 20 17:10 SMOKING qty 1 (live)

**Q: What did I eat yesterday?**  
Window: Wed 19 Aug 00:00–24:00 IST on `event_timestamp`, `event_type = FOOD`.  
**A:** Tahri (night). Does **not** include Thursday snacks. Does **not** miss tahri because it was typed Thursday.

**Q: How many cigarettes did I smoke last week?**  
If “last week” = previous ISO week, count SMOKING quantities whose **occurrence** local date falls in that week — including cigarettes logged this week about last week.  
Does **not** count Thursday’s live cigarette in last week.  
Does **not** dump all catch-up smokes into this week.

**Q: What did I eat yesterday?** asked at **01:10 IST Friday**  
Yesterday = Thursday 20 Aug. Wednesday tahri is **not** in the answer.

**Q: Kal kya khaya?** (past)  
Same as yesterday, with kal disambiguation = previous local date.

### 7.3 Implementation note for later

Ask should resolve the **question’s** relative dates with the **same** `TemporalResolver` (same zone, same kal rules). Do not implement a second calendar in the LLM.

---

## 8. Edge Cases

These are binding. Tests in §9 must cover them.

### 8.1 “Kal” at 1 AM

**Situation:** Fri 21 Aug 2026, **01:15 IST**. User: `kal raat tahri khayi thi`.

**Wrong:** 24h ago → Thursday 01:15, or “last night” as Friday 21:00.  
**Right:**

- `kal` + past → **yesterday’s date** = **Thu 20 Aug**
- `raat` → 21:00 Thu  
- Not Wed, unless they said parso.

**Related:** `last night` at 01:15 Fri → still **Thu night** (the night that just ended), which equals kal raat in this case. At **15:00 Fri**, `last night` → Thu 21:00; `kal raat` → Thu 21:00. At **15:00 Fri**, `kal` without raat → Thu (calendar), not “last night” vs “yesterday” split.

**Night owl rule:** Between 00:00 and 04:59 local, `aaj raat` is ambiguous (this clock-day’s small hours vs previous evening). Freeze:

- `aaj raat` in `[00:00, 05:00)` → **previous local date 21:00** (the night they are still in)
- `aaj raat` in `[05:00, 24:00)` → **current date 21:00** (upcoming or this evening)

Document in UI later if needed; do not ask a clarifying question in Feature #1 (no extra round-trip).

### 8.2 “Yesterday morning”

**Situation:** Thu 17:10. `yesterday morning` / `kal subah`.

- Date: Wed 19 Aug  
- Time: 08:00, precision `PERIOD`  
- Not Wed 17:10, not Thu 08:00.

If the user says `yesterday morning at 7:30`, precision `EXACT`, 07:30.

### 8.3 User edits an old event

**Situation:** Event id E, tahri on Wed night, `logged_at` Thursday. User meant **parso** (Tue).

- `PATCH` sets `event_timestamp` to Tue 12:00 or Tue 21:00 if they keep “raat”.
- `logged_at` **unchanged** (still Thursday capture).
- `updated_at` now.
- Timeline **moves** the card from Wed heading to Tue.
- Ask “what did I eat yesterday?” (asked Thursday) **drops** tahri; “day before yesterday” **gains** it.
- Do not append a second row.

If Feature #1 ships **without** PATCH, document as known gap; recovery users will double-log. **Recommendation: ship PATCH in the same release** — small API, high honesty value.

### 8.4 Duplicate event

**Situation:** User sends `Parso 2 cigarette pi thi` twice in five minutes.

**Detect (hint, not unique index):** same `event_type`, same `raw_text` (normalized trim/case), same **local occurrence date**, `logged_at` within **2 hours**.

- Still **insert** the second row (truth: they might have meant two separate memories, or a retry after a UI glitch).
- `possibleDuplicate: true` on the second create.
- UI: non-blocking “Looks similar to an event already on Tue 18 Aug”.
- **No** DB unique constraint on `(raw_text, event_timestamp)` — smoking repeats are normal (`1 cigarette` many times a day).

Do **not** treat two `1 cigarette pee li` NOW logs 10 minutes apart as duplicates.

### 8.5 Missing time reference

**Situation:** `tahri khayi thi` with no kal/aaj.

- Precision `NOW`, timestamp = `logged_at`.
- Past tense without a date is **not** sufficient to backdate (could be five minutes ago).
- Only **explicit** relative/absolute date tokens move the day.

**Situation:** `parso tahri` with no clock.

- Precision `DAY`, noon sentinel, date = minus 2.

**Situation:** `kal` only, no event (`kal`).

- Still save (Phase 0). Type likely `NOTE`/`UNKNOWN`. Date = yesterday, precision `DAY`.

### 8.6 Additional cases (required)

| Case | Behavior |
|------|----------|
| Future explicit date `2030-01-01` | Persist, precision `DAY`, Ask excludes from past consumption questions; optional `400` if date > now + 1 day — **freeze: allow up to now + 12h**, else clamp to `logged_at` + `UNRESOLVED` and keep raw text (avoid junk future years from parse errors) |
| `kal` with future tense | Not yesterday; see §3.2 |
| DST | IST has no DST. Resolver still uses IANA zone for correctness. |
| Parser / OpenAI down | UNKNOWN type **and** deterministic time still applied to `raw_text`. Time must not depend on OpenAI. |
| Conflicting tokens `kal` + `2026-08-01` | Explicit date wins. |
| `this morning` after 18:00 | Today 08:00 `PERIOD`, not tomorrow. |
| Sleep “2 baje soya” at 02:30 | EXACT 02:30 **today** (already in the small hours). |
| Sleep “2 baje soya” at 22:00 | 02:30 **next** calendar day would be future — freeze: 02:30 **today** is in the past only if now > 02:30; at 22:00, 2 AM means **today 02:30 already passed** (morning) or tonight’s 2 AM? Hindi at 22:00 usually means **upcoming**. Freeze: if `hour < 05:00` and `now` is evening (`>= 18:00`), map to **next local date**. |
| Very old `10 saal pehle` | Allow; Ask/timeline still work; no max-age. |
| Client clock vs server | Server wins. |
| Concurrent creates | Two rows; no locking beyond DB PK. |

---

## 9. Testing Strategy

Goal: temporal behavior is **deterministic** and **LLM-independent** for the token set in §3.

### 9.1 Unit tests

Clock is **injected** (`Clock` / `Instant` + `ZoneId`). Never call `Instant.now()` inside the resolver.

| Suite | Examples |
|-------|----------|
| Calendar tokens | aaj, kal+past, parso, yesterday, day before yesterday at several `now` values including **01:15** and **23:45** |
| Periods | subah, lunch, raat → sentinel minutes |
| Explicit dates | ISO, DMY, `15 Aug` |
| Kal at 1 AM | §8.1 |
| Yesterday morning | §8.2 |
| No temporal span | `NOW` |
| Conflict | date + kal → date wins |
| Small hours `aaj raat` | §8.1 night owl |
| `2 baje` evening vs morning | §8.6 |
| Precision enum mapping | EXACT vs PERIOD vs DAY |
| Backdated / loggedLater derivation | same-day NOW vs cross-day |
| Duplicate detector | true/false cases in §8.4 |
| Hindi tense | `thi` vs `khaunga` |
| Parser normalize | existing `EventParserServiceTest` still passes; temporal fields optional |

No network. Table-driven tests: `{ nowIst, message, expectedLocalDate, expectedLocalTime, precision }`.

### 9.2 Integration tests

Spring + Testcontainers/Postgres (or existing test slice):

1. `POST` “Kal raat tahri…” with frozen clock → row `event_timestamp` Wednesday 21:00 IST, `logged_at` = frozen now, precision `PERIOD`.
2. `GET /api/logs` order: occurrence desc (a Tuesday event appears after a Wednesday event even if logged later).
3. Image `POST` with note “parso…” applies resolver.
4. OpenAI failure: UNKNOWN + **still** backdated if message has `kal`.
5. Migration: insert pre-V3 shaped row (or migrate test DB) → `logged_at = created_at`, precision `NOW`.
6. `PATCH` time: `logged_at` unchanged, `event_timestamp` changes, `updated_at` changes.
7. Response JSON includes `timestamp` **and** `loggedAt` for old SPA compatibility.

### 9.3 Acceptance tests

Manual / Playwright against local app (frozen or real clock documented in the run):

| ID | Steps | Expect |
|----|-------|--------|
| A1 | Log `Kal raat tahri khayi thi` | Timeline under **yesterday**, Night, Logged later |
| A2 | Log `Parso 2 cigarette pi thi` | Two days ago, day precision, Logged later |
| A3 | Log `Kal office lunch me samosa khaya tha` | Yesterday ~lunch, Logged later |
| A4 | Log `1 cigarette pee li` | Today, exact-ish now, no later badge |
| A5 | Ask (when built): “What did I eat yesterday?” | Tahri + samosa; not today’s tea |
| A6 | Ask: “How many cigarettes last week?” | Counts by **occurrence** week |
| A7 | Log at ~01:15: `kal raat…` | Previous calendar date, not “today 1 am” |
| A8 | Log `yesterday morning` | Previous date morning label |
| A9 | Duplicate parso line twice | Two rows, second flagged |
| A10 | PATCH parso correction | Card moves day; logged-at line unchanged |
| A11 | Phase 0 old events | Still listed; no false Logged later |
| A12 | Photo + note `kal ki tahri` | Same as A1 |

Do not block Feature #1 on Ask UI if Ask is a later feature; **A5–A6** become Ask’s acceptance suite but use fixtures created by this feature.

---

## 10. Release Plan

### 10.1 Implementation steps (when coding is approved)

1. **TemporalResolver** (pure Java, clock injected, zone `Asia/Kolkata`) + unit tables.  
2. **Flyway V3** columns + comments + index.  
3. **Entity / DTOs** additive fields; `timestamp` still occurrence.  
4. **LogService**: single `now`; set `logged_at`; call resolver **before or after** type parse but **not only inside OpenAI**.  
5. **Prompt assist** (optional second JSON field) — must not override deterministic tokens.  
6. **Timeline UI**: date groups, period labels, Logged later, secondary logged-at.  
7. **PATCH** + duplicate hint.  
8. **Tests** §9.1–9.2.  
9. **Docs sync:** comment in `09_Technical_Design.md` that §5.4 is superseded by this document; do not silently contradict.  
10. **Ask** (separate feature): wire windows to `event_timestamp`.

Parser version: bump only if the classification prompt changes (e.g. `phase0-v2`). Temporal resolver version can live in `structured_json.temporal.resolver` = `v1`.

### 10.2 Risk assessment

| Risk | Severity | Mitigation |
|------|----------|------------|
| Hindi `kal` wrong direction | High | Tense rules + past default; unit tests; do not leave this to the LLM alone |
| False precision (noon/21:00 shown as exact) | Medium | `event_time_precision` + UI labels |
| Historical data looks “live” | Low (accepted) | No NLP backfill; comments on columns |
| Timeline “jumps” after deploy (new logs sit on past days) | Low | Intended; mention in release note |
| OpenAI invents dates | High | Deterministic overlay |
| PATCH abuse / no auth | Accepted (Phase 0 single user) | Same as create |
| Duplicate false positives | Low | Hint only |
| Ask ships before this | High | This feature is a prerequisite; do not query `created_at` for “yesterday” |
| Image EXIF vs note | Low | Ignore EXIF in Feature #1 (privacy + clock wrong on phones) |

### 10.3 Migration steps (ops)

1. Backup Postgres.  
2. Deploy API that understands new columns **after** V3 applied (Flyway on boot).  
3. Confirm `SELECT count(*) FROM event_logs WHERE logged_at IS NULL` = 0.  
4. Deploy frontend that can ignore or show new fields (additive JSON).  
5. Spot-check: create kal/parso logs; confirm old rows still load.

Rollback: restore DB backup **or** keep columns (additive) and revert app to ignore them. Do not `DROP COLUMN` in a panic if new logs already used past `event_timestamp`.

### 10.4 Rollout plan

| Stage | Who | Success |
|-------|-----|---------|
| Dev | Local Flyway + unit tests | All §9.1 green |
| Private | Same single user as Phase 0 | A1–A4, A7–A11 feel right in IST |
| Ask follow-on | After 3–5 days of catch-up logs | Yesterday/last-week answers match timeline headings |
| Docs | This file + short release note | Users know “kal” goes to yesterday, not now |

**Feature flag:** optional `app.backdated-logging.enabled` default **true** after tests. If false: `event_timestamp = logged_at = now` (Phase 0). Use only as emergency kill switch.

**Release note (user-facing):**  
You can log things that already happened — *kal raat*, *parso*, yesterday, or a date. The timeline shows **when it happened**. If you logged it later, you’ll see **Logged later**. Old entries stay at the time you originally typed them.

---

## Appendix A — Mapping to current code (read-only)

| Area | Today | After Feature #1 |
|------|-------|------------------|
| `LogService.createLog` | `setEventTimestamp(now)` | Resolver → occurrence; `logged_at = now` |
| `OpenAiParserClient` SYSTEM_PROMPT | Type only | Optional temporal assist; not authoritative |
| `LogItemResponse.timestamp` | Create time | Occurrence |
| `GET /api/logs` order | `eventTimestamp, createdAt` | `eventTimestamp, loggedAt` |
| Search | Stub empty list | Unchanged by time except response fields |
| Auth / timezone | None / implicit UTC instants | Still no auth; zone constant IST |

---

## Appendix B — Open questions (do not block design freeze)

1. Multi-event one message (“kal tahri, parso 2 cigarette”) — still one row in Feature #1.  
2. Whether `loggedLater` on same-day “aaj subah” is shown — §3.7 allows optional.  
3. Confirm ISO week vs rolling 7 days for “last week” in Ask copy (both defined in §7.1).

**Design freeze recommendation:** Approve §1–8 as specified; implement resolver + V3 + API additives + timeline before Ask.
)
