# Backdated Logging — Production Smoke Test Report

**Date:** 2026-08-20  
**Clock at test:** Thu 20 Aug 2026, **17:41 IST** (`2026-08-20T12:11:17Z`)  
**Local calendar date:** 2026-08-20 (`Asia/Kolkata`)  
**Mode:** Verification only. **No application code was modified.**  
**Target:** Live running app + live database `devfuel` (not `devfuel_test`).

| Check | Result |
|-------|--------|
| Backend reachable | **Pass** — `GET http://127.0.0.1:8080/api/logs` → **200** |
| Frontend / timeline loading | Observed by operator; API timeline returned **52** items |
| Startup failure | **Not a failure.** App is serving logs. |
| Flyway V3 on live `devfuel` | **Pass** |
| Three API smoke cases vs design | **Pass** |
| Phase 1 production-ready | **Yes** (see §8) |

---

## 1. Backend using V3 schema

Live Postgres: `localhost:5433`, database **`devfuel`**, user `devfuel` (compose container `devfuel-db`).

`information_schema.columns` on `event_logs`:

| column_name | data_type | is_nullable | column_default |
|-------------|-----------|-------------|----------------|
| `event_timestamp` | timestamptz | NO | |
| `logged_at` | timestamptz | NO | |
| `event_time_precision` | varchar | NO | |
| `event_timezone` | varchar | NO | `'Asia/Kolkata'` |
| `created_at` | timestamptz | NO | `now()` |

Constraint:

```
event_logs_event_time_precision_valid
CHECK (event_time_precision IN ('NOW','EXACT','PERIOD','DAY','UNRESOLVED'))
```

Index: `idx_event_logs_logged_at_desc`

Hibernate `ddl-auto: validate` is on. The running API returned the new JSON fields (`loggedAt`, `eventTimePrecision`, `eventTimezone`, `backdated`, `loggedLater`). That is only possible if the running process mapped V3 columns.

**Verdict:** Backend is on the V3 schema.

---

## 2. Flyway V3 applied to live `devfuel`

```
SELECT installed_rank, version, description, success, installed_on
FROM flyway_schema_history
ORDER BY installed_rank;
```

| installed_rank | version | description | success | installed_on |
|----------------|---------|-------------|---------|--------------|
| 1 | 1 | event logs | t | 2026-08-16 03:54:47.745992 |
| 2 | 2 | image logging | t | 2026-08-18 11:56:54.627246 |
| **3** | **3** | **backdated logging** | **t** | **2026-08-20 17:34:34.58941** |

Row census after smoke inserts:

```
SELECT COUNT(*) AS total,
       COUNT(*) FILTER (WHERE event_time_precision = 'NOW') AS now_prec,
       COUNT(*) FILTER (WHERE logged_at IS NOT NULL) AS has_logged_at
FROM event_logs;
```

| total | now_prec | has_logged_at |
|-------|----------|---------------|
| 52 | 46 | 52 |

Every row has `logged_at`. Most rows remain `NOW` (Phase 0 / live logs). Six rows are not `NOW` (includes the three smoke events plus any other backdated creates).

**Verdict:** V3 ran successfully on **live `devfuel`**, not only on `devfuel_test`.

---

## 3. Direct API testing

Endpoint: `POST http://127.0.0.1:8080/api/logs`  
`Content-Type: application/json`

Design expectation at **20 Aug 2026 IST**:

| Case | Message | Occurrence (IST) | Precision | loggedLater |
|------|---------|------------------|-----------|-------------|
| A | Kal raat tahri khayi thi | 19 Aug 21:00 | PERIOD | true (different date) |
| B | Parso 2 cigarette pi thi | 18 Aug 12:00 | DAY | true |
| C | Aaj subah 3 cigarette pi thi | 20 Aug 08:00 | PERIOD | false (same date) |

All three should be `backdated: true` (precision ≠ `NOW`).

---

## 4. Per-test results

### A. Kal raat tahri khayi thi

**Request payload**

```json
{"message":"Kal raat tahri khayi thi","source":"web"}
```

**HTTP status:** `201 Created`

**Response JSON**

```json
{
  "success": true,
  "id": "ba0ac467-5cf4-4c9d-93cf-f2bd59de80b9",
  "eventType": "FOOD",
  "rawText": "Kal raat tahri khayi thi",
  "structuredJson": { "item": "tahri", "quantity": 1 },
  "source": "web",
  "parserVersion": "v1",
  "timestamp": "2026-08-19T15:30:00Z",
  "imageRef": null,
  "loggedAt": "2026-08-20T12:11:17.920321Z",
  "eventTimePrecision": "PERIOD",
  "eventTimezone": "Asia/Kolkata",
  "backdated": true,
  "loggedLater": true
}
```

| Field | Value |
|-------|--------|
| event_timestamp (`timestamp`) | `2026-08-19T15:30:00Z` = **19 Aug 2026 21:00 IST** |
| logged_at | `2026-08-20T12:11:17.920321Z` = **20 Aug 2026 17:41:17 IST** |
| event_time_precision | `PERIOD` |
| loggedLater | `true` |
| backdated | `true` |

### B. Parso 2 cigarette pi thi

**Request payload**

```json
{"message":"Parso 2 cigarette pi thi","source":"web"}
```

**HTTP status:** `201 Created`

**Response JSON**

```json
{
  "success": true,
  "id": "e31c1fe8-a81d-4e10-90d8-f3a49361e0d9",
  "eventType": "SMOKING",
  "rawText": "Parso 2 cigarette pi thi",
  "structuredJson": { "unit": "cigarette", "quantity": 2 },
  "source": "web",
  "parserVersion": "v1",
  "timestamp": "2026-08-18T06:30:00Z",
  "imageRef": null,
  "loggedAt": "2026-08-20T12:11:19.765088Z",
  "eventTimePrecision": "DAY",
  "eventTimezone": "Asia/Kolkata",
  "backdated": true,
  "loggedLater": true
}
```

| Field | Value |
|-------|--------|
| event_timestamp | `2026-08-18T06:30:00Z` = **18 Aug 2026 12:00 IST** |
| logged_at | `2026-08-20T12:11:19.765088Z` = **20 Aug 2026 17:41:19 IST** |
| event_time_precision | `DAY` |
| loggedLater | `true` |
| backdated | `true` |

### C. Aaj subah 3 cigarette pi thi

**Request payload**

```json
{"message":"Aaj subah 3 cigarette pi thi","source":"web"}
```

**HTTP status:** `201 Created`

**Response JSON**

```json
{
  "success": true,
  "id": "03a210f8-f4af-4258-a198-5f82b1ed076d",
  "eventType": "SMOKING",
  "rawText": "Aaj subah 3 cigarette pi thi",
  "structuredJson": { "unit": "cigarette", "quantity": 3 },
  "source": "web",
  "parserVersion": "v1",
  "timestamp": "2026-08-20T02:30:00Z",
  "imageRef": null,
  "loggedAt": "2026-08-20T12:11:21.151960Z",
  "eventTimePrecision": "PERIOD",
  "eventTimezone": "Asia/Kolkata",
  "backdated": true,
  "loggedLater": false
}
```

| Field | Value |
|-------|--------|
| event_timestamp | `2026-08-20T02:30:00Z` = **20 Aug 2026 08:00 IST** |
| logged_at | `2026-08-20T12:11:21.151960Z` = **20 Aug 2026 17:41:21 IST** |
| event_time_precision | `PERIOD` |
| loggedLater | `false` |
| backdated | `true` |

---

## 5. Database rows (live `event_logs`)

Query (timestamptz as stored, UTC offset `+00`):

```sql
SELECT id, raw_text, event_timestamp, logged_at, event_time_precision, event_timezone
FROM event_logs
WHERE id IN (
  'ba0ac467-5cf4-4c9d-93cf-f2bd59de80b9',
  'e31c1fe8-a81d-4e10-90d8-f3a49361e0d9',
  '03a210f8-f4af-4258-a198-5f82b1ed076d'
);
```

| id | raw_text | event_timestamp | logged_at | event_time_precision | event_timezone |
|----|----------|-----------------|-----------|----------------------|----------------|
| `ba0ac467-…` | Kal raat tahri khayi thi | `2026-08-19 15:30:00+00` | `2026-08-20 12:11:17.920321+00` | PERIOD | Asia/Kolkata |
| `e31c1fe8-…` | Parso 2 cigarette pi thi | `2026-08-18 06:30:00+00` | `2026-08-20 12:11:19.765088+00` | DAY | Asia/Kolkata |
| `03a210f8-…` | Aaj subah 3 cigarette pi thi | `2026-08-20 02:30:00+00` | `2026-08-20 12:11:21.15196+00` | PERIOD | Asia/Kolkata |

IST conversion (`AT TIME ZONE 'Asia/Kolkata'`):

| raw_text | event IST | logged IST |
|----------|-----------|------------|
| Kal raat… | **2026-08-19 21:00:00** | 2026-08-20 17:41:17 |
| Parso… | **2026-08-18 12:00:00** | 2026-08-20 17:41:19 |
| Aaj subah… | **2026-08-20 08:00:00** | 2026-08-20 17:41:21 |

`created_at` equals `logged_at` on all three (insert time).

`GET /api/logs` (occurrence descending) positions:

| Timeline index | id prefix | text | loggedLater |
|----------------|-----------|------|-------------|
| 6 | `03a210f8` | Aaj subah… (today 08:00, after later today events) | false |
| 7 | `ba0ac467` | Kal raat… | true |
| 26 | `e31c1fe8` | Parso… | true |

Sort is by **occurrence**, not insert order. Parso (18 Aug) sits below many 19–20 Aug events. That matches V3 design.

---

## 6. Actual vs expected

| Case | Expected | Actual | Match |
|------|----------|--------|-------|
| A date | Yesterday 19 Aug | 19 Aug | Yes |
| A time | Night sentinel 21:00 IST | 21:00 IST (`15:30Z`) | Yes |
| A precision | PERIOD | PERIOD | Yes |
| A loggedLater | true | true | Yes |
| A type | FOOD tahri | FOOD tahri qty 1 | Yes |
| B date | 18 Aug (minus 2) | 18 Aug | Yes |
| B time | Noon DAY | 12:00 IST (`06:30Z`) | Yes |
| B precision | DAY | DAY | Yes |
| B type | SMOKING qty 2 | SMOKING qty 2 | Yes |
| C date | Today 20 Aug | 20 Aug | Yes |
| C time | Morning 08:00 IST | 08:00 IST (`02:30Z`) | Yes |
| C loggedLater | false (same local date) | false | Yes |
| C backdated | true (PERIOD, not NOW) | true | Yes |
| Capture clock | ≈ 17:41 IST 20 Aug | 17:41:17–21 IST | Yes |
| Two clocks | occurrence ≠ logged_at for A/B/C | Yes | Yes |
| UTC storage | TIMESTAMPTZ UTC | `+00` | Yes |

No mismatches against Phase 1 design (`docs/backdated_logging_design.md` + implementation plan sentinels and `loggedLater` = different **local dates** only).

---

## 7. Incorrect behavior?

**None found** for the three smoke cases.

§7 root-cause analysis is **not applicable**.

---

## 8. Production-ready confirmation

**Phase 1 is production-ready** for the approved scope:

- V3 is on the live database.
- Create path writes occurrence vs capture correctly for kal / parso / aaj subah.
- API additive fields match stored columns.
- Existing timeline still loads (52 events).
- Classifier still works (FOOD / SMOKING) independently of the clock.

Remaining (already out of scope, not smoke failures):

- No PATCH, no duplicate warnings, no Timeline “Logged later” badge, no Ask DevFuel.
- UI shows occurrence ISO via existing `timestamp` (kal appears as yesterday evening). Expected.
- These three smoke rows remain in live `devfuel` (`ba0ac467…`, `e31c1fe8…`, `03a210f8…`) if operators want them deleted later.

**Recommendation:** Treat Feature #1 Phase 1 as **verified on the running production-local stack.** No code change required from this test.
)
