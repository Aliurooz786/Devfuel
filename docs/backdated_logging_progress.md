# Backdated Logging — Phase 1 Progress

**Plan:** `docs/backdated_logging_implementation_plan.md`  
**Rule:** Follow the plan. No PATCH, duplicates, Ask DevFuel, or Timeline UI.

| Step | Status | Validation |
|------|--------|------------|
| 1. TemporalResolver | done | compiles |
| 2. Unit tests (resolver) | done | `TemporalResolverTest` 20 passed |
| 3. Flyway V3 | done | applied on `devfuel_test` (V1→V3) |
| 4. Entity changes | done | `EventLog` maps new columns |
| 5. DTO changes | done | additive JSON fields |
| 6. LogService integration | done | `LogServiceBackdatedLoggingTest` 4 passed |
| 7. Repository updates | done | sort by `loggedAt` |
| 8. Integration tests | done | `BackdatedLoggingIT` 2 passed |

**Suite:** `mvn test` → **30 tests, 0 failures** (2026-08-20)

**Not implemented (out of scope):** PATCH, duplicate detection, Ask DevFuel, Timeline UI, frontend files.

---

## Step 1 — TemporalResolver

Deterministic resolver. No OpenAI. No `Instant.now()` inside the class. Calendar math in `Asia/Kolkata`.

**Files**

- new: `backend/src/main/java/com/devfuel/common/EventTimePrecision.java`
- new: `backend/src/main/java/com/devfuel/temporal/TemporalResolution.java`
- new: `backend/src/main/java/com/devfuel/temporal/TemporalResolver.java`

---

## Step 2 — Unit tests

Table-driven cases U1–U17 plus period-without-day, blank, and null.

**Files**

- new: `backend/src/test/java/com/devfuel/temporal/TemporalResolverTest.java`

**Status:** 20 passed.

---

## Step 3 — Flyway V3

Additive columns: `logged_at`, `event_time_precision`, `event_timezone`. Historical `event_timestamp` is not rewritten. Backfill: `logged_at = created_at`, precision `NOW`.

**Files**

- new: `backend/src/main/resources/db/migration/V3__backdated_logging.sql`

**Note:** Live database `devfuel` is migrated the next time the app starts (Flyway). ITs used isolated DB `devfuel_test`.

---

## Step 4 — Entity changes

**Files**

- update: `backend/src/main/java/com/devfuel/log/EventLog.java` (`loggedAt`, `eventTimePrecision`, `eventTimezone`)

---

## Step 5 — DTO changes

Request body unchanged. Responses add `loggedAt`, `eventTimePrecision`, `eventTimezone`, `backdated`, `loggedLater`. `timestamp` remains occurrence time.

**Files**

- update: `backend/src/main/java/com/devfuel/log/dto/CreateLogResponse.java`
- update: `backend/src/main/java/com/devfuel/log/dto/LogItemResponse.java`

---

## Step 6 — LogService integration

One `now` from injected `Clock`. Text logs resolve from `message`. Image logs resolve from `note` only (empty note → `NOW`). Classifier `structured_json` is not given a `temporal` key.

**Files**

- new: `backend/src/main/java/com/devfuel/config/TemporalProperties.java`
- new: `backend/src/main/java/com/devfuel/config/TemporalConfig.java` (`Clock` + `TemporalResolver` beans)
- update: `backend/src/main/resources/application.yml` (`app.temporal.zone`)
- update: `backend/src/main/java/com/devfuel/log/LogService.java`
- new: `backend/src/test/java/com/devfuel/log/LogServiceBackdatedLoggingTest.java`

**Status:** 4 passed (S1–S4, including S5/S6 assertions inside S1/S3).

---

## Step 7 — Repository updates

Timeline and search tie-break on `loggedAt` instead of `createdAt`.

**Files**

- update: `backend/src/main/java/com/devfuel/log/EventLogRepository.java`

---

## Step 8 — Integration tests

Testcontainers could not use the local Docker API (400). Fallback from the plan: compose Postgres on `:5433`, database **`devfuel_test`** (not live `devfuel`). Surefire includes `*IT.java`.

**Files**

- new: `backend/src/test/java/com/devfuel/log/BackdatedLoggingIT.java`
- update: `backend/pom.xml` (surefire: `*IT.java`, Byte Buddy experimental for JDK 25 Mockito)

**Status:** I1 columns exist; I2/I3 create kal-raat + live log; timeline ordered by occurrence. 2 passed.

---

## Ops

Restart the backend against `devfuel` so Flyway applies V3 to the real table. Existing rows keep their timestamps; new `kal` / `parso` logs will land on past days. The current UI will show those occurrence times with no frontend change.
