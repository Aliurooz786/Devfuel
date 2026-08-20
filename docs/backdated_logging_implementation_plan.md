# Backdated Logging — Phase 1 Implementation Plan

**Status:** Implementation plan only. Do not write application code from this document until coding is explicitly approved.  
**Authority:** Approved design `docs/backdated_logging_design.md` with **scope reduction** below.  
**Audience:** The engineer who will implement Feature #1 (backend-first).

---

## Phase 1 scope (binding)

**In scope**

| Item | Meaning |
|------|---------|
| `TemporalResolver` | Deterministic NL → occurrence instant + precision |
| `logged_at` | Immutable capture time |
| `event_time_precision` | `NOW` \| `EXACT` \| `PERIOD` \| `DAY` \| `UNRESOLVED` |
| `event_timezone` | IANA zone used at resolve time (`Asia/Kolkata`) |
| Flyway `V3` | Additive migration; no rewrite of historical `event_timestamp` |
| API response additions | `loggedAt`, `eventTimePrecision`, `eventTimezone`, `backdated`, `loggedLater` |
| Backdated event creation | Text `POST /api/logs` and image `POST /api/logs/image` |
| Unit tests | Resolver + service mapping |
| Integration tests | Create → persist → timeline read-back |

**Out of scope (do not build)**

| Item | Why deferred |
|------|----------------|
| `PATCH /api/logs/{id}` | Correction can wait; wrong day is rarer than missing day |
| Duplicate detection | False positives on smoking; extra product surface |
| Timeline UI changes | Existing SPA already renders `timestamp`; badges/grouping later |
| Ask DevFuel | Separate feature; this work only **stores** honest times |

If a later PR sneaks any of the four into Phase 1, it is **out of contract**.

---

## 1. Executive Summary

### What will be built

A **backend-only** change so that creating a log stores **when the life event happened**, not only when the request arrived.

On every create:

1. Capture `now` once (UTC).
2. Classify the message as today (existing OpenAI parser — **unchanged**).
3. Run `TemporalResolver` on the same text (or image `note`).
4. Persist `event_timestamp` = occurrence, `logged_at` = `now`, plus precision and timezone.
5. Return the existing `timestamp` field as occurrence time, plus the new fields.

No new endpoints. No request-body time field. No LLM as the clock. No frontend work.

### Business value

People log **after** the fact. Recovery, missed days, and “kal / parso” are normal. Today those sentences are stored as **this evening**, which:

- Inflates today
- Empties yesterday
- Makes any future “how many cigarettes last week” lie

Phase 1 makes the **database** honest. The product can still look like Phase 0 in the UI; the data is already correct for everything that comes next.

### Why this is foundational

| Downstream | What it needs from Phase 1 |
|------------|----------------------------|
| **Recovery Roadmap** | Catch-up logs must land on the missed calendar days, or relapse charts become “one bad Thursday”. |
| **Timeline** | Day headings and “logged later” are UI on top of `event_timestamp` vs `logged_at`. Without columns, UI cannot be honest. |
| **Ask DevFuel** | “What did I eat yesterday?” is a filter on occurrence in `Asia/Kolkata`. Capture time is the wrong index. |
| **Analytics** | Weight, smoking, food, sleep aggregates are time-series on **life**, not on **app opens**. |

Phase 1 does not ship those products. It is the **shared clock** they will all query.

---

## 2. File-by-File Change Plan

Convention: **new** = create file; **update** = edit existing; **unchanged** = do not touch in this PR.

### 2.1 Database

| Path | Type | Why |
|------|------|-----|
| `backend/src/main/resources/db/migration/V3__backdated_logging.sql` | **new** | Only safe way to add NOT NULL columns on a live `event_logs` table (`ddl-auto: validate`). |
| `backend/src/main/resources/db/migration/V1__event_logs.sql` | **unchanged** | Never edit applied Flyway history. |
| `backend/src/main/resources/db/migration/V2__image_logging.sql` | **unchanged** | Same. |
| `10_Schema.sql` | **unchanged** | Phase 0 snapshot. Updating it creates a second “truth” that can drift from Flyway. Point readers at V3 in the design doc only. |

### 2.2 Domain / persistence

| Path | Type | Why |
|------|------|-----|
| `backend/src/main/java/com/devfuel/common/EventTimePrecision.java` | **new** | Small enum matching the CHECK constraint. Keep next to `EventType`, not inside the resolver, so JPA/API can share it. |
| `backend/src/main/java/com/devfuel/log/EventLog.java` | **update** | Map `loggedAt`, `eventTimePrecision`, `eventTimezone`. Do not remove `createdAt`. |
| `backend/src/main/java/com/devfuel/log/EventLogRepository.java` | **update** | Tie-break timeline/search by `loggedAt` instead of `createdAt` (`findAllByOrderByEventTimestampDescLoggedAtDesc` + search `ORDER BY`). Semantically correct after V3; `created_at` ≈ `logged_at` for old rows. |
| `backend/src/main/java/com/devfuel/log/ImageStorageService.java` | **unchanged** | No time logic. |

### 2.3 TemporalResolver (new package)

Keep this **off** the OpenAI parser. Classification and clocks are different failure domains.

| Path | Type | Why |
|------|------|-----|
| `backend/src/main/java/com/devfuel/temporal/TemporalResolution.java` | **new** | Immutable result: occurrence `Instant`, precision, zone id, optional matched span (for logs/tests only — **not** written into `structured_json` in Phase 1). |
| `backend/src/main/java/com/devfuel/temporal/TemporalResolver.java` | **new** | Pure function + injected `Clock` + zone. See §4. |
| `backend/src/main/java/com/devfuel/config/TemporalProperties.java` | **new** | `app.temporal.zone` default `Asia/Kolkata`. One property. No per-user TZ. |
| `backend/src/main/java/com/devfuel/config/ClockConfig.java` | **new** | `@Bean Clock.systemUTC()` so tests can replace the clock without PowerMock. |

**Do not add** a second LLM call, a `TemporalParserClient`, or prompt changes.

### 2.4 Parser layer (classification)

| Path | Type | Why |
|------|------|-----|
| `backend/src/main/java/com/devfuel/parser/OpenAiParserClient.java` | **unchanged** | Prompt stays type/structure only. LLM must not own dates in Phase 1. |
| `backend/src/main/java/com/devfuel/parser/OpenAiVisionClient.java` | **unchanged** | Same. |
| `backend/src/main/java/com/devfuel/parser/EventParserService.java` | **unchanged** | Still returns type + structured map. |
| `backend/src/main/java/com/devfuel/parser/ImageParseService.java` | **unchanged** | Time comes from `note` in `LogService`, not vision JSON. |
| `backend/src/main/java/com/devfuel/parser/ParseResult.java` | **unchanged** | Do not overload with timestamps. |
| `backend/src/main/java/com/devfuel/parser/dto/OpenAiParseResponse.java` | **unchanged** | |
| `backend/src/main/java/com/devfuel/parser/dto/OpenAiChatDtos.java` | **unchanged** | |
| `backend/src/main/java/com/devfuel/config/OpenAiConfig.java` | **unchanged** | |
| `backend/src/main/java/com/devfuel/config/ParserProperties.java` | **unchanged** | Do **not** bump `parser_version` for a clock change. |

### 2.5 Service / API

| Path | Type | Why |
|------|------|-----|
| `backend/src/main/java/com/devfuel/log/LogService.java` | **update** | Orchestration: stamp `now`, parse type, `resolver.resolve(text, now)`, set entity fields, map new DTO fields. Same path for `createImageLog` using `note` if present else treat as no temporal language (`NOW`). **Do not** merge a `temporal` object into `structured_json` in Phase 1 (existing UI dumps JSON; that would look like a UI change). |
| `backend/src/main/java/com/devfuel/log/LogController.java` | **unchanged** | No new routes. Responses change only because DTO records gain fields. |
| `backend/src/main/java/com/devfuel/log/dto/CreateLogRequest.java` | **unchanged** | Still `{ message, source? }`. No client-supplied timestamp (avoids clock skew). |
| `backend/src/main/java/com/devfuel/log/dto/CreateLogResponse.java` | **update** | Additive: `loggedAt`, `eventTimePrecision`, `eventTimezone`, `backdated`, `loggedLater`. Keep `timestamp` = occurrence. |
| `backend/src/main/java/com/devfuel/log/dto/LogItemResponse.java` | **update** | Same additive fields so `GET /api/logs` matches create. |
| `backend/src/main/java/com/devfuel/common/exception/*` | **unchanged** | No new error codes. Unresolvable time still **201** with `UNRESOLVED` + occurrence = `now` (collect truth). |
| `backend/src/main/java/com/devfuel/common/api/*` | **unchanged** | |

**Derived flags (no extra columns):**

- `backdated` = precision ≠ `NOW` **or** `|event_timestamp - logged_at| > 15 minutes`
- `loggedLater` = local calendar **date** of occurrence ≠ local date of `logged_at` (zone = `event_timezone`), **or** precision is `PERIOD` / `DAY` / `EXACT` / `UNRESOLVED` with a different date — **Phase 1 freeze:** `loggedLater` = **different local dates** only. Same-day “aaj subah” logged at 5pm is `backdated` true, `loggedLater` false. Simpler badge later; fewer false “logged later” on same-day periods.

Do **not** add `possibleDuplicate`.

### 2.6 Configuration

| Path | Type | Why |
|------|------|-----|
| `backend/src/main/resources/application.yml` | **update** | Add `app.temporal.zone: ${APP_TEMPORAL_ZONE:Asia/Kolkata}` only. No feature flag (single user; revert the deploy if needed). |
| `backend/pom.xml` | **update** | Add **test-scoped** Testcontainers (PostgreSQL + Flyway path) so integration tests do not require a laptop Postgres on `:5433`. See §6. If that is rejected, use `@SpringBootTest` against compose — document as a weaker alternative. |
| `backend/src/main/java/com/devfuel/DevFuelApplication.java` | **unchanged** | |
| `backend/src/main/java/com/devfuel/config/CorsConfig.java` | **unchanged** | |
| `backend/src/main/java/com/devfuel/config/UploadProperties.java` | **unchanged** | |

### 2.7 Tests

| Path | Type | Why |
|------|------|-----|
| `backend/src/test/java/com/devfuel/temporal/TemporalResolverTest.java` | **new** | Table-driven; **this is the product**. |
| `backend/src/test/java/com/devfuel/log/LogServiceBackdatedLoggingTest.java` | **new** | Mocks repository + parsers; real resolver + fixed `Clock`. Proves wiring without OpenAI. |
| `backend/src/test/java/com/devfuel/log/BackdatedLoggingIT.java` | **new** | Flyway V3 + `POST` + `GET` against Testcontainers Postgres. OpenAI mocked / API key empty → UNKNOWN type is fine; **time must still backdate**. |
| `backend/src/test/java/com/devfuel/parser/EventParserServiceTest.java` | **unchanged** | Regression: classification still independent of time. |

No frontend tests. No PATCH tests. No duplicate tests.

### 2.8 Frontend (explicit non-work)

| Path | Type | Why |
|------|------|-----|
| `frontend/src/types/log.ts` | **unchanged** | Extra JSON fields are ignored at runtime. Updating types without UI is optional churn; skip in Phase 1. |
| `frontend/src/api/logsApi.ts` | **unchanged** | Request body unchanged. |
| `frontend/src/features/logs/LogListItem.tsx` | **unchanged** | Already shows `log.timestamp` via `toLocaleString()`. After backend change, **kal** rows will **display yesterday’s time** with no UI PR. That is acceptable and is **not** a Timeline UI project (no badges, no day groups). |
| `frontend/src/App.tsx`, `Timeline.tsx`, `LogInput.tsx`, `PhotoInput.tsx`, `SearchBar.tsx`, `client.ts` | **unchanged** | Out of scope. |

**Operational note for QA:** existing UI will start showing past datetimes. Do not “fix” that by forcing `timestamp` back to `now`. That would undo the feature.

### 2.9 Docs (this PR)

| Path | Type | Why |
|------|------|-----|
| `docs/backdated_logging_implementation_plan.md` | **new** | This file. |
| `docs/backdated_logging_design.md` | **unchanged** in the code PR | Already approved. Optionally add a one-line “Phase 1 shipped subset” later; not required to code. |
| `09_Technical_Design.md` §5.4 | **unchanged** in Phase 1 | Avoid a large doc rewrite in the same PR as schema. Follow-up doc PR can mark §5.4 superseded. |

---

## 3. Database Migration Plan

### 3.1 Exact schema changes (V3)

All additive. One Flyway version. Single transaction (Flyway default).

1. `ALTER TABLE event_logs ADD COLUMN logged_at TIMESTAMPTZ NULL;`
2. `UPDATE event_logs SET logged_at = created_at WHERE logged_at IS NULL;`
3. `ALTER TABLE event_logs ALTER COLUMN logged_at SET NOT NULL;`
4. `ALTER TABLE event_logs ADD COLUMN event_time_precision VARCHAR(16) NULL;`
5. `UPDATE event_logs SET event_time_precision = 'NOW' WHERE event_time_precision IS NULL;`  
   Phase 0 **defined** timestamp as create time. Do **not** NLP-backfill `event_timestamp`.
6. `ALTER TABLE event_logs ALTER COLUMN event_time_precision SET NOT NULL;`
7. `ALTER TABLE event_logs ADD CONSTRAINT event_logs_event_time_precision_valid CHECK (event_time_precision IN ('NOW', 'EXACT', 'PERIOD', 'DAY', 'UNRESOLVED'));`
8. `ALTER TABLE event_logs ADD COLUMN event_timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Kolkata';`
9. `CREATE INDEX idx_event_logs_logged_at_desc ON event_logs (logged_at DESC);`
10. `COMMENT ON COLUMN event_logs.event_timestamp` → occurrence time; pre-V3 rows equal capture time.
11. `COMMENT ON COLUMN event_logs.logged_at` → immutable submit time.
12. Comments on precision and timezone.

**Do not:** drop `created_at`, change `event_timestamp` values, add generated columns, add unique constraints.

### 3.2 Migration order vs application

1. Backup Postgres (local: `pg_dump` is enough).
2. Deploy backend whose entity **includes** the new fields **together with** V3 (Flyway runs on startup before JPA `validate`).
3. Never deploy the new `EventLog` mapping **before** V3 — `ddl-auto: validate` will fail.

Order inside the JVM: Flyway migrate → Hibernate validate → app up.

### 3.3 Rollback strategy

V3 is additive. Prefer **roll forward**.

| Situation | Action |
|-----------|--------|
| App bug, schema OK | Redeploy previous JAR **only if** it still maps unknown columns. **It will not.** Old `EventLog` has no `loggedAt` but extra NOT NULL columns are fine for INSERT if the old code only writes known columns… Hibernate `validate` on **old** JAR: extra columns are **allowed**; missing columns are not. **Old app can run on V3 schema.** New app cannot run on V2 schema. |
| Need to undo V3 | Restore `pg_dump` from before migrate. Do not `DROP COLUMN` if any new row has `event_timestamp ≠ logged_at` unless product accepts losing occurrence times. |
| Bad precision backfill | All historical rows are `NOW` by design; no data rewrite to undo. |

**Verify rollback theory once:** old JAR + V3 DB → timeline still loads (new columns ignored).

### 3.4 Backward compatibility

| Consumer | After V3 |
|----------|----------|
| Historical rows | `event_timestamp` unchanged; `logged_at = created_at`; precision `NOW`; zone default IST |
| `timestamp` JSON | Still occurrence; for old rows still equals original create time |
| Frontend | Additive JSON; request unchanged |
| `structured_json` | Unchanged shape (no forced `temporal` key) |
| Image rows | Same three new columns |

---

## 4. TemporalResolver Design

Component only. No code in this document.

### 4.1 Responsibility

Input: raw string + `Instant now` (and zone from config).  
Output: `TemporalResolution` (occurrence instant, precision, zone).

It must resolve:

| Intent | Tokens (minimum set) |
|--------|----------------------|
| Today | `aaj`, `today` |
| Yesterday | `yesterday`, `kal` (past-biased; see tense) |
| Day before yesterday | `parso`, `parson`, `day before yesterday` |
| Morning | `subah`, `morning` |
| Afternoon | `dopahar`, `afternoon` |
| Lunch | `lunch`, `office lunch` |
| Evening | `shaam`, `evening` |
| Night | `raat`, `night`, `last night` |
| Explicit dates | `yyyy-MM-dd`, `dd-MM-yyyy`, `dd/MM/yyyy`, `d MMM` / `d MMMM` (English month, current year) |
| Explicit clock | `h:mm`, `H:mm`, `9 pm`, `9pm`, `21:30` when cheap to add — **include in Phase 1** if it stays regex-simple; if it threatens the calendar logic, ship date+period first and add clock in a tiny follow-up **inside the same feature** only if tests stay green |

**Non-responsibilities:** event type, quantity, OpenAI, HTTP, persistence, duplicate detection, user confirmation.

### 4.2 Architecture decisions (simple on purpose)

1. **Deterministic first, LLM never (Phase 1).** Known tokens are a finite Hinglish/English set. A second model call doubles cost, latency, and disagreement. OpenAI remaining down must **not** prevent `kal` from working.
2. **Inject `Clock`, do not call `Instant.now()` inside the resolver.** Tests freeze “Thu 20 Aug 2026 17:10 IST”.
3. **Calendar math in `Asia/Kolkata`, store UTC `Instant`.** Never “minus 24 hours”.
4. **One pass, priority rules,** not a parser combinator library. Suggested scan order:
   1. Explicit date (wins over kal/parso if both present)
   2. Relative day (`parso` > `kal`/`yesterday` > `aaj`/`today` > implicit today)
   3. Period or clock on that date
   4. If no day token and no date → occurrence = `now`, precision `NOW` (even if the sentence is past tense: “tahri khayi thi” is **not** enough to backdate)
5. **`kal` tense:** if future markers (`unga`, `ungi`, `khaunga`, `will`) and no past markers → treat as **not** yesterday: Phase 1 maps future-`kal` to **tomorrow** `DAY` noon **only if** we can do it with a tiny token list; otherwise leave `NOW` and document. **Preferred Phase 1:** past markers or no tense → **yesterday**; future markers → `NOW` (do not invent tomorrow). Safer for a life log than writing future occurred events.
6. **Sentinel local times** (then convert to UTC):

   | Period | Local time | Precision |
   |--------|------------|-----------|
   | morning / subah | 08:00 | `PERIOD` |
   | lunch | 13:00 | `PERIOD` |
   | afternoon / dopahar | 14:00 | `PERIOD` |
   | evening / shaam | 18:00 | `PERIOD` |
   | night / raat | 21:00 | `PERIOD` |
   | date only / parso no tod | 12:00 | `DAY` |
   | explicit clock | that time | `EXACT` |

7. **`last night` / `aaj raat` in `[00:00, 05:00)` local:** date = **previous** local date, time 21:00 `PERIOD` (design §8.1). After 05:00, `aaj raat` = current date 21:00.
8. **Ambiguous parse:** precision `UNRESOLVED`, occurrence = `now`. Never throw.
9. **Normalize** input: lowercase, collapse whitespace; keep original for the log line only.
10. **No dependency** on `EventParserService`. `LogService` calls both.

### 4.3 Collaboration with LogService

```
now = clock.instant()
typeResult = eventParser.parse(message)          // may be UNKNOWN
timeResult = temporalResolver.resolve(message, now)
entity.eventTimestamp = timeResult.occurrence
entity.loggedAt = now
entity.eventTimePrecision = timeResult.precision
entity.eventTimezone = timeResult.zone
entity.createdAt = now
```

Image: `resolve(note if present else "")` — empty note → `NOW`. Do not parse vision description for dates.

### 4.4 What we deliberately skip

- ICU / `java.time.format.DateTimeFormatter` with many locales
- NLP libraries
- Storing `temporal` inside `structured_json`
- Configurable sentinel hours
- Feature flag

---

## 5. API Impact Analysis

### 5.1 Request changes

**None.**  

`POST /api/logs` remains `{ "message", "source?" }`.  
`POST /api/logs/image` remains multipart `file`, `source?`, `note?`.

Clients cannot set `eventTimestamp`. Time is a function of language + server clock.

### 5.2 Response changes (additive)

`CreateLogResponse` and `LogItemResponse` gain:

| Field | Type | Notes |
|-------|------|--------|
| `timestamp` | existing Instant | **Now means occurrence** (breaking **semantics**, not shape) |
| `loggedAt` | Instant | Capture |
| `eventTimePrecision` | enum string | |
| `eventTimezone` | string | e.g. `Asia/Kolkata` |
| `backdated` | boolean | |
| `loggedLater` | boolean | Different local date |

No `possibleDuplicate`. No PATCH body.

`GET /api/logs` sort: `event_timestamp DESC`, `logged_at DESC`.

### 5.3 Compatibility with current frontend

| Check | Result |
|-------|--------|
| TypeScript request types | Compatible |
| Extra response fields | Ignored by `apiFetch` / unused props |
| `timestamp` still ISO-8601 | `new Date(log.timestamp)` still works |
| Visible change | List times follow **occurrence** (e.g. yesterday 9pm) |

Risk is **product surprise**, not a white-screen. QA should expect that; we will not hide it.

Search endpoint still returns `[]` — out of this feature’s cleanup scope.

### 5.4 API risk assessment

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Semantic change of `timestamp` | Certain | Document; keep field name; frontend already displays it |
| Jackson record constructor order | Medium | Compile-time; add tests asserting JSON keys |
| Clients posting `eventTimestamp` ignored | Low | Unknown JSON ignored on request (`@JsonIgnoreProperties` not required if field absent) |
| `structuredJson.temporal` leaking into UI | Avoided | Not written in Phase 1 |
| CORS / auth | None | Unchanged |

---

## 6. Testing Matrix

Clock for all unit cases unless noted: **2026-08-20T17:10 IST** (`2026-08-20T11:40:00Z`). Zone `Asia/Kolkata`.

### 6.1 Unit tests — `TemporalResolverTest`

Table-driven `{ input, expectedLocalDate, expectedLocalTime, precision }`.

| ID | Input | Expected |
|----|--------|----------|
| U1 | `1 cigarette pee li` | 20 Aug 17:10 `NOW` |
| U2 | `Aaj subah 2 cigarette pi` | 20 Aug 08:00 `PERIOD` |
| U3 | `today` only as part of a real log `today mood kharab hai` | 20 Aug `NOW` or today+no period → `NOW` if no period token; with no tod → `NOW` |
| U4 | `Kal raat tahri khayi thi` | 19 Aug 21:00 `PERIOD` |
| U5 | `yesterday I had tahri` | 19 Aug 12:00 `DAY` (no period) |
| U6 | `Yesterday morning mood kharab tha` | 19 Aug 08:00 `PERIOD` |
| U7 | `Parso 2 cigarette pi thi` | 18 Aug 12:00 `DAY` |
| U8 | `Kal office lunch me samosa khaya tha` | 19 Aug 13:00 `PERIOD` |
| U9 | `15 Aug ko weight 93.5` | 15 Aug 2026 12:00 `DAY` |
| U10 | `2026-08-18 2 cigarette` | 18 Aug 12:00 `DAY` |
| U11 | `tahri khayi thi` (past, no date) | 20 Aug 17:10 `NOW` |
| U12 | empty / blank | `NOW` |
| U13 | `kal` + `2026-08-01` | 1 Aug 2026 `DAY` (explicit wins) |

**Edge clock:** freeze `2026-08-21T01:15 IST`.

| ID | Input | Expected |
|----|--------|----------|
| U14 | `kal raat tahri khayi thi` | **20 Aug** 21:00 `PERIOD` (not 21 Aug, not 24h ago) |
| U15 | `aaj raat` | **20 Aug** 21:00 `PERIOD` (night-owl rule) |

**Edge clock:** `2026-08-21T15:00 IST`.

| ID | Input | Expected |
|----|--------|----------|
| U16 | `aaj raat` | 21 Aug 21:00 `PERIOD` |

**Tense:**

| ID | Input | Expected |
|----|--------|----------|
| U17 | `kal cigarette piungi` (future-ish) | `NOW` (Phase 1 freeze) |

**Periods alone on today:** `subah` / `lunch` / `shaam` / `dopahar` / `raat` with `aaj` or without day token: without day token, period **on today** (`PERIOD`), not `NOW`. Example: `subah 2 cigarette` → 20 Aug 08:00. (If that over-triggers, document and require `aaj` — **preferred:** period without day = **today** that period.)

Resolver must not call the network.

### 6.2 Unit tests — `LogServiceBackdatedLoggingTest`

| ID | Setup | Assert |
|----|--------|--------|
| S1 | Message kal raat; parser returns FOOD | Saved `eventTimestamp` = resolver occurrence; `loggedAt` = frozen now; `createdAt` = now; precision `PERIOD` |
| S2 | Parser throws/UNKNOWN | Time still from resolver |
| S3 | Image with note `parso…` | Resolver sees note |
| S4 | Image without note | precision `NOW`, timestamps equal |
| S5 | DTO `timestamp` equals occurrence; `loggedAt` equals now; `loggedLater` true when dates differ |
| S6 | Mapper does not put `temporal` into structured map |

### 6.3 Integration tests — `BackdatedLoggingIT`

Postgres via Testcontainers; Flyway V1+V2+V3; OpenAI key empty.

| ID | Flow | Assert |
|----|------|--------|
| I1 | Schema: columns exist; old-row simulation: insert as V2-shaped via SQL then… (or rely on V3 backfill on a pre-insert) | Optional: run V3 against a row inserted in test with only V1 columns using Flyway baseline — **keep I1 simple:** after migrate, `information_schema` has the three columns |
| I2 | `POST /api/logs` kal raat (frozen clock via test `Clock` bean) | 201; DB `event_timestamp` previous night; `logged_at` frozen now |
| I3 | `GET /api/logs` | Item includes new fields; `timestamp` matches occurrence; order by occurrence (insert a past event and a now event; past may sort below today) |
| I4 | `POST /api/logs/image` with note parso | Occurrence day-before-yesterday (may skip if multipart is heavy; then cover in S3 only) |
| I5 | Hibernate validate on startup | Context loads |

Use a test `@Primary Clock` bean; do not depend on wall clock.

### 6.4 Acceptance tests (manual, no UI work)

Run against local app after ITs pass. Same clock as real now (document the local date).

| ID | Action | Expect |
|----|--------|--------|
| A1 | Log `Kal raat tahri khayi thi` | `GET` shows `timestamp` yesterday evening IST; `loggedAt` ≈ now; `loggedLater` true |
| A2 | Log `Parso 2 cigarette pi thi` | Occurrence two local days back; `DAY` |
| A3 | Log `Kal office lunch me samosa khaya tha` | Yesterday ~13:00 IST |
| A4 | Log `1 cigarette pee li` | `timestamp` ≈ `loggedAt`; `loggedLater` false; `NOW` |
| A5 | Existing Phase 0 rows | Still listed; precision `NOW`; times unchanged |
| A6 | UI (unmodified) | Yesterday’s datetime appears on the kal row — **pass**, not a bug |
| A7 | ~1 AM: `kal raat…` | Previous calendar date |

Ask DevFuel and PATCH are **not** acceptance for Phase 1.

### 6.5 Out-of-scope tests (do not write)

Duplicate detector, PATCH, timeline badges, Ask windows, NLP backfill of old rows.

---

## 7. Implementation Sequence

Dependencies flow **down**. Do not start UI or Ask. Do not parallelize schema + entity incorrectly.

**Step 1 — Tests for the resolver (empty class + table, or resolver + tests together)**  
Write `TemporalResolver` and `TemporalResolverTest` first. No Spring, no DB.  
*Depends on:* design sentinels and kal rules only.

**Step 2 — `Clock` bean + `TemporalProperties`**  
*Depends on:* Step 1 can already take `Clock` in the constructor without a bean; Step 2 is for the app.

**Step 3 — Flyway V3**  
Apply against local Docker Postgres; confirm `validate` still works with **current** `EventLog` **before** entity change? Actually: **do not** start the old app after adding NOT NULL columns without Hibernate ignoring them — extra columns are OK. Safer: **Step 3 and Step 4 in the same PR**, never merge V3 without entity fields.

**Step 4 — `EventTimePrecision` + `EventLog` fields**  
*Depends on:* V3 column names.

**Step 5 — Repository sort key**  
*Depends on:* `loggedAt` on entity.

**Step 6 — DTO record fields + `LogService` mapping + derived flags**  
*Depends on:* Steps 1–5. Wire resolver into `createLog` and `createImageLog`.

**Step 7 — `LogServiceBackdatedLoggingTest`**  
*Depends on:* Step 6.

**Step 8 — Testcontainers IT + `pom.xml` test deps**  
*Depends on:* Steps 3–6. Proves Flyway + HTTP.

**Step 9 — Manual A1–A7**  
*Depends on:* running API. Frontend unchanged.

**Step 10 — Stop**  
No PATCH, no duplicate, no `LogListItem` badge, no Ask.

If Step 1 tests are red, **do not** open Step 6. Wrong dates in production are expensive to undo (you would be rewriting `event_timestamp`).

---

## 8. Estimated Risk Areas

| What can break | How to verify | How to recover |
|----------------|---------------|----------------|
| `kal` at 1 AM maps to wrong date | U14, A7 | Fix resolver only; already-saved rows stay wrong until a future PATCH |
| Period sentinels shown as “exact” in UI | A1: time is 21:00 | Accept in Phase 1; UI labels later (`PERIOD`) |
| Hibernate validate fail (V3 not applied) | App start logs Flyway | Run migrate; do not set `ddl-auto: update` |
| Old JAR after V3 | Start previous build | Should work (extra columns). If not, restore dump |
| New JAR before V3 | Start fails validate | Apply V3 first / same deploy |
| OpenAI outage | I2 with empty key | Type UNKNOWN; **time still correct** |
| Image note ignored | S3 / I4 | Fix `LogService` argument only |
| Timeline order surprises | I3 two rows | Confirm sort is occurrence, not insert |
| `structured_json` polluted | S6; look at UI dump | Phase 1 must not merge `temporal` |
| IST vs UTC off-by-5:30 | U4 expected UTC `15:30Z` for 21:00 IST | Fix zone conversion; add UTC column in test table |
| Explicit `dd/MM` vs `MM/dd` | U10 ISO; one DMY case | Document DMY; add `03/04/2026` = 3 Apr |
| Future `kal` stored as yesterday | U17 | Past-biased default is intended; future freeze = `NOW` |
| Frontend looks “wrong” | A6 | Do not revert `timestamp` semantics |
| Testcontainers flaky on Darwin | ITs | Fallback: `@SpringBootTest` + compose `:5433` documented in PR |

**Data recovery:** there is no automated reverse of occurrence times. If a resolver bug ships, **stop logging**, fix tests, deploy; optionally SQL-correct known `raw_text` patterns **by hand** — no mass NLP update.

---

## 9. Definition Of Done

Phase 1 is complete **only if all** of the following are true:

- [ ] `V3__backdated_logging.sql` applied; three columns + CHECK + `logged_at` index exist
- [ ] Historical rows: `logged_at = created_at`, `event_time_precision = 'NOW'`, `event_timestamp` **unchanged** from pre-deploy
- [ ] `TemporalResolver` has no OpenAI dependency
- [ ] `POST /api/logs` with `Kal raat tahri khayi thi` stores yesterday 21:00 IST (precision `PERIOD`), `logged_at` = request time
- [ ] `Parso 2 cigarette pi thi` stores local date minus 2, precision `DAY`
- [ ] `Kal office lunch me samosa khaya tha` stores yesterday 13:00 IST
- [ ] Message with no temporal token stores occurrence = `logged_at`, precision `NOW`
- [ ] Image create with `note` containing `kal`/`parso` uses that text for the resolver
- [ ] Image create without note is `NOW`
- [ ] JSON still has `timestamp` as occurrence; adds `loggedAt`, `eventTimePrecision`, `eventTimezone`, `backdated`, `loggedLater`
- [ ] JSON does **not** add `possibleDuplicate`; no `PATCH` route
- [ ] `GET /api/logs` ordered by occurrence then `logged_at`
- [ ] `structured_json` from the classifier is not overwritten with a `temporal` object
- [ ] `TemporalResolverTest` covers U1–U17 (or equivalent table with those behaviors)
- [ ] `LogServiceBackdatedLoggingTest` and `BackdatedLoggingIT` pass
- [ ] Existing `EventParserServiceTest` passes
- [ ] `mvn test` green
- [ ] Frontend files in this PR: **zero** (or doc-only)
- [ ] Manual A1–A7 recorded (even a short checklist in the PR description)
- [ ] `09_Technical_Design.md` §5.4 not silently implemented as “still create time”

If resolver tests pass but IT cannot run Testcontainers, DoD still requires **one** proven path against real Postgres (compose is acceptable if called out).

---

## 10. Post-Feature Opportunities

Phase 1 stores the clock. It does not productize it. Next work, in suggested order:

### Weight logging (honesty of the series)

Weight is a **dated observation**. “Kal weight 93.5” must sit on yesterday or trend lines lie. Phase 1 unblocks a weight chart that uses `event_timestamp` + `event_type = WEIGHT` with no extra schema.

### Daily timeline (UI)

Now cheap: group `GET /api/logs` by local date of `event_timestamp`; badge `loggedLater`; show period labels from `eventTimePrecision` instead of `:00` clocks. **This was deferred on purpose** so the API can bake in production first.

### Ask DevFuel

Questions share `TemporalResolver` for the **query** (“yesterday”, “last week”) and SQL on `event_timestamp`. Do not start Ask until Phase 1 DoD is met; otherwise answers will be coded against the wrong column and never trusted.

### Recovery dashboard

Missed days, catch-up density (`logged_at::date ≠ event_timestamp::date`), smoking/food by **life day**. Relapse vs logging-session confusion goes away only because Phase 1 exists.

### Later (still not now)

`PATCH` for “I meant parso”; duplicate hints; LLM assist when tokens fail; per-user timezone; `structured_json.temporal` for debug; parser_version bump.

---

## Appendix — Engineering constraints for the implementing PR

1. One PR: migration + resolver + service + tests.  
2. No drive-by search implementation, no prompt rewrite, no UI polish.  
3. Prefer a 200-line resolver with a 150-line test table over a framework.  
4. If a token is expensive (fuzzy “pichle Sunday”), **skip it** and leave `NOW` rather than guess.  
5. Match names to the database: `logged_at` → `loggedAt`, `event_time_precision` → `eventTimePrecision`, `event_timezone` → `eventTimezone`.
)
