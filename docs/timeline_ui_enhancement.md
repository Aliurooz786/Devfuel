# Timeline UI Enhancement — Making Backdated Logging Visible

Status: implemented
Scope: frontend only. No backend changes, no new endpoints, no new API fields.

---

## 1. Root UX Problem

The Phase 1 Timeline UI is functionally correct. Verification confirmed:

- the API returns `loggedLater`, `eventTimePrecision`, and `eventTimezone` correctly
- the timeline groups by local date
- the "Logged later" badge renders
- period labels ("Night", "Morning") render

Despite all of that, users report the feature as invisible. The reason is not a
rendering bug. It is a **feedback gap at the moment of action**.

When a user types "Kal raat tahri khayi thi" and presses Log:

1. The entry is correctly placed under **Yesterday**.
2. **Yesterday** sits below every **Today** entry — often 7 to 20 rows down, well
   below the fold.
3. The compose box gives no confirmation beyond clearing the input.

From the user's seat, the interaction reads as: *"I logged something and nothing
happened."* Their mental model is "new thing goes on top." Backdated logging
deliberately violates that model, and the UI never tells them so. Everything the
feature produces — the group, the badge, the period label — is correct but lives
outside the user's viewport at the only moment they were looking for it.

So the problem to solve is **discovery at submit time**, not styling and not
information density.

---

## 2. Options Considered

| Option | Verdict |
| --- | --- |
| 1. Auto-scroll to the new backdated entry | Right instinct, wrong default. Solves discovery, but yanks the viewport away from the compose box. Catch-up logging is bursty ("kal ye khaya", "parso wo piya"), so scrolling on every submit fights the user mid-flow. |
| 2. Summary banner: "1 backdated event logged today" | Reports a statistic, not a location. Does not answer "where did my entry go?" |
| 3. Visible counters: Today (7), Yesterday (12) | Cheap and genuinely useful for scanning structure, but passive — it never fires at the moment of confusion. |
| 4. Highlight newly created entries | Helps recognition once you arrive, but does nothing to get you there. |
| 5. Badge near timeline title: "3 backdated entries" | Same weakness as option 2, plus it competes with the section heading. |

**Chosen: a targeted combination of 1, 4, and 3**, weighted so the expensive
behavior (moving the viewport) stays under user control.

---

## 3. Proposed Fix

### 3.1 Placement confirmation at the compose box (primary)

When a submission comes back with `loggedLater === true`, an inline notice
appears directly beneath the input — where the user is already looking:

> Saved to **Yesterday · Night**, not now.   [ Show ]  ×

This closes the feedback gap in one line. It names the destination using the
same vocabulary the timeline uses, so the label the user reads at submit time is
the label they will find when they scroll. It appears only for backdated
entries, so ordinary "log it now" flows are untouched and never see extra chrome.

The placement string is derived from the create response the app already
receives (`timestamp` + `eventTimePrecision`). No extra request.

### 3.2 "Show" jumps to the entry, instead of auto-scrolling (option 1, made safe)

Rather than auto-scrolling on every backdated submit, the notice carries a
**Show** button that smooth-scrolls the entry into view. The user gets the
benefit of option 1 exactly when they want it, and keeps their place in the
compose box when they are mid-burst. This is the one deliberate deviation from
the brief's option 1, and it is what makes repeated catch-up logging bearable.

### 3.3 Highlight the entry that was just placed (option 4)

The referenced entry is highlighted with a warm tint and a left rule while the
notice is active. It makes the entry findable whether the user clicks **Show**
or scrolls manually, and it disappears once the notice is dismissed or the next
log is submitted.

### 3.4 Per-day counts (option 3)

Day headings become `TODAY (7)`, `YESTERDAY (12)`. Two lines of code, and it
makes the timeline's structure legible at a glance — including the fact that
Yesterday exists and is populated.

---

## 4. Files Affected

| File | Change |
| --- | --- |
| `frontend/src/features/logs/BackdatedNotice.tsx` | New. Presentational notice with Show and dismiss actions. |
| `frontend/src/App.tsx` | Captures the create response, records placement when `loggedLater` is true, owns highlight and scroll state, renders the notice. |
| `frontend/src/features/logs/Timeline.tsx` | Renders per-group counts; forwards `highlightId` and `scrollKey`. |
| `frontend/src/features/logs/LogListItem.tsx` | Applies the highlight class; scrolls itself into view when Show is pressed. |
| `frontend/src/features/logs/timelineDisplay.ts` | Adds `describeOccurrence()`, reusing existing date-group and period helpers so the notice and the timeline can never disagree. |
| `frontend/src/App.css` | Styles for the notice, the highlight, and the count. |

Backend, API contract, and database are untouched.

---

## 5. User Impact

**Before:** user logs "Kal raat tahri khayi thi", the input clears, nothing
visible changes. The feature appears broken and the user stops trusting
backdated phrasing.

**After:** the user immediately reads "Saved to Yesterday · Night, not now."
They can press **Show** to jump straight to the highlighted entry, or ignore it
and keep logging. Day headings show counts, so the shape of the timeline is
obvious without scrolling.

Concretely:

- The system's interpretation of "kal raat" is now surfaced at submit time, so a
  misparse is caught in seconds instead of never.
- Backdated logging becomes a trustworthy behavior rather than an invisible one,
  which is the precondition for the catch-up logging that Recovery Roadmap and
  Ask DevFuel depend on.
- Non-backdated logging is completely unchanged — zero added noise for the
  common path.
