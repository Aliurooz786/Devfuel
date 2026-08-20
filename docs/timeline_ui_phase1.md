# Timeline UI Phase 1

**Branch:** `feature/timeline-ui`  
**Scope:** Frontend readability only. Backend is unchanged.  
**Status:** Implemented and validated (`npm run build`).

---

## 1. Implementation plan

1. Read existing `timestamp` / new backdated fields from `GET /api/logs` (already returned).
2. Format time labels from `eventTimePrecision` in `Asia/Kolkata`.
3. Show **Logged later** when `loggedLater === true`.
4. Group rows by local calendar date: **Today**, **Yesterday**, else `18 Aug 2026`.
5. Keep compose, photos, search, and styling language the same.
6. Do not add APIs, Ask, charts, or weight UI.

---

## 2. Frontend files

| File | Change |
|------|--------|
| `frontend/src/types/log.ts` | Optional `eventTimePrecision`, `loggedLater`, `loggedAt`, `eventTimezone`, `backdated` |
| `frontend/src/features/logs/timelineDisplay.ts` | **New.** Date keys, group labels, period/datetime text |
| `frontend/src/features/logs/Timeline.tsx` | Group by local date |
| `frontend/src/features/logs/LogListItem.tsx` | Human time + Logged later badge |
| `frontend/src/App.css` | Day headings; later badge |
| `docs/timeline_ui_phase1.md` | This document |

**Also committed:** `frontend/src/features/logs/` was previously ignored by root `logs/`. Gitignore now allows that folder so the timeline UI can be versioned. Existing compose components (`LogInput`, `PhotoInput`, …) are included for the same reason.

---

## 3. Display rules

Zone: **`Asia/Kolkata`** (matches backend).

| `eventTimePrecision` | Label |
|----------------------|--------|
| `PERIOD` | Morning / Lunch / Afternoon / Evening / Night from IST hour (sentinels 08/13/14/18/21) |
| `DAY` | Date only, e.g. `18 Aug 2026` |
| `NOW` / `EXACT` / missing / `UNRESOLVED` | Normal datetime in IST |

**Logged later:** badge next to event type when `loggedLater` is true. Missing field → no badge.

**Groups:** consecutive logs that share the same IST calendar date. List order stays occurrence-newest-first from the API.

---

## 4. Validation

```
cd frontend && npm run build
```

Result: **pass** — `tsc -b && vite build` (Vite 8.2.1). `npm run lint` (oxlint) clean.

---

## 5. Out of scope (not built)

Ask DevFuel, weight logging, dashboard, charts, analytics, recovery score, new backend APIs.
