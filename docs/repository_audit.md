# Repository Audit — Pre–Open-Source Cleanup

**Date:** 2026-08-20  
**Scope:** Entire workspace `confession` (DevFuel)  
**Mode:** Audit only — **nothing was deleted or modified** except creation of this document.  
**Git status:** No `.git` directory detected. This is a good window to clean **before** the first public commit.

---

## Executive summary

| Severity | Finding |
|---|---|
| **CRITICAL** | Live **OpenAI API key** stored in IntelliJ run config (`backend/.idea/workspace.xml`) |
| **CRITICAL** | Unrelated **Airtel / credit-bureau scripts** at repo root with hardcoded **Basic auth + JSESSIONID** and internal hostname |
| **HIGH** | Personal health/life logs (smoking, food, cannabis, meal photos) under `analysis/` and meal uploads under `backend/data/uploads/` |
| **HIGH** | `.gitignore` incomplete for public push (`analysis/`, `backend/target/`, `proof/` decision, IDE workspace, etc.) |
| **MEDIUM** | OpenAI request/response bodies logged at **INFO** (privacy + cost noise) |
| **LOW** | Unused Vite scaffold assets; OS `.DS_Store` litter; Cursor `.github/modernize` tooling |

**Do not push until:** (1) OpenAI key is rotated, (2) Airtel scripts removed from the tree intended for GitHub, (3) personal logs/uploads excluded, (4) `.gitignore` hardened.

---

## 1. Sensitive files (do not commit)

### 1.1 Secrets / credentials

| Path | Issue | Action |
|---|---|---|
| `backend/.idea/workspace.xml` | Contains env `OPENAI_API_KEY=sk-proj-…` (full live key) | **Rotate/revoke key in OpenAI dashboard immediately.** Remove key from IDE run config; use shell env or local untracked `.env`. Never commit `.idea/workspace.xml`. |
| `version1 script.py` | Unrelated credit-score tool; hardcoded `AUTHORIZATION` (Basic …), `COOKIE` (JSESSIONID), internal Airtel Kong URL | **Delete from this project** (or move outside repo). Treat credentials as compromised; rotate if still valid. |
| `version2.py` | Same class of secrets as above | Same as above |
| `.env` / `.env.local` | Not present in scan (good) | Keep gitignored |
| `.env.example` | Placeholder `sk-your-key-here` + local DB defaults | **OK to commit** (document that defaults are local-only) |
| `frontend/.env.example` | Empty API base | **OK to commit** |

### 1.2 Default local credentials (acceptable for demo, not production)

| Location | Values | Note |
|---|---|---|
| `docker-compose.yml` | user/password/db `devfuel` | Fine for local Docker; document “change before any shared deploy” |
| `backend/src/main/resources/application.yml` | Default JDBC URL `localhost:5433`, user/pass `devfuel` | Env-overridable; OK for OSS demo |
| Root `.env.example` | Same local Postgres defaults | OK as examples |

### 1.3 Personal / private life data (PHI-adjacent)

| Path | Contents | Action |
|---|---|---|
| `analysis/` (~23 MB) | Full DB export CSV, daily summaries, health report, charts, **copies of meal photos**, personal smoking/food/joint logs | **Do not publish.** Gitignore entire tree or delete before push |
| `analysis/raw_event_export.csv` | Raw personal event text | Exclude |
| `analysis/sample_images/` | Real meal photos | Exclude |
| `backend/data/uploads/` (~22 MB, 11 images) | User meal uploads | Already covered by `backend/data/` in `.gitignore` — **verify stays untracked** |
| `proof/` (~4.6 MB) | Phase smoke screenshots, API dumps, DB row dumps, Vision JSON, logs | Prefer exclude publicly **or** scrub to synthetic-only fixtures |

### 1.4 Local URLs / infra mentions

Found in docs (`README.md`, `12_Phase5_Plan.md`, etc.): `localhost:5173`, `localhost:8080`, `localhost:5433`, ngrok guidance. **Safe** for OSS docs. No live ngrok URLs found.

---

## 2. Files safe to delete (recommended)

Nothing deleted in this audit. Recommended removals **after approval**:

### 2.1 Critical — wrong project / credentialed scripts

- [ ] `version1 script.py`
- [ ] `version2.py`

### 2.2 Generated / local analysis outputs

- [ ] Entire `analysis/` directory (or keep only a sanitized README if desired later)
  - `analysis/raw_event_export.csv`
  - `analysis/food_logs.csv`, `daily_summary.csv`, `analysis_summary.json`
  - `analysis/devfuel_report.md`
  - `analysis/charts/*`
  - `analysis/sample_images/*`
  - `analysis/.mplconfig/`
  - `analysis/scripts/*` (optional: move a **generic** offline script later without personal data)

### 2.3 Uploads & build caches

- [ ] `backend/data/uploads/*` (local only; already gitignored via `backend/data/`)
- [ ] `backend/target/` (~52 MB Maven build)
- [ ] `frontend/node_modules/` (~81 MB)
- [ ] `frontend/node_modules/.vite/` cache

### 2.4 OS / IDE noise

- [ ] All `.DS_Store` (many under root, frontend, backend, target)
- [ ] `backend/.idea/workspace.xml` (**contains API key** — remove file or strip secrets even if `.idea/` stays local)
- [ ] Prefer not committing any of `backend/.idea/` for public repo

### 2.5 Proof / experiment artifacts (decision)

**Option A — exclude entirely (recommended for public):** delete or gitignore `proof/`

**Option B — keep sanitized subset:** keep short `proof/*/REPORT.md` with synthetic samples only; remove:

- [ ] `proof/**/*.png`, `*.jpg` screenshots
- [ ] `proof/phase5a/samosa-test.jpg`
- [ ] `proof/**/database-*.txt`, `api-*.json`, `openai-raw-*.json`
- [ ] `proof/phase41/backend-openai-slice.log`
- [ ] Duplicate root-level `proof/timeline-ui.png`, `proof/database-rows.txt`, etc.

### 2.6 Likely unused frontend assets

No imports found in `frontend/src` for:

- [ ] `frontend/src/assets/hero.png`
- [ ] `frontend/src/assets/react.svg`
- [ ] `frontend/src/assets/vite.svg`

Confirm UI still has favicon via `frontend/public/` before deleting public icons.

### 2.7 Accidental tooling

- [ ] `.github/modernize/` (Cursor Java-upgrade hooks; not DevFuel CI) — remove unless you intentionally want it

### 2.8 Temporary / debug

- [ ] `proof/phase41/backend-openai-slice.log`
- [ ] Any future `*.log`, `reports/`, `logs/` folders (none at root today besides proof log)

---

## 3. Gitignore recommendations

### 3.1 Current `.gitignore` (gaps)

**Already covered (good):** `.DS_Store`, `.idea/`, `.vscode/`, `.env`, `backend/data/`, `frontend/node_modules/`, `frontend/dist/`, `*.log`

**Gaps / issues:**

| Gap | Why |
|---|---|
| No `backend/target/` | Maven output will be committed if `git add .` |
| No `analysis/` | Personal health export will leak |
| `proof/` commented out | Easy to commit screenshots + DB dumps |
| Root `package-lock.json` ignored | Harmless at root, but **do commit** `frontend/package-lock.json` (currently not ignored — good) |
| No `**/uploads/` belt-and-suspenders | Safer if upload path ever moves |
| No `*.sqlite`, `*.db` | Future local DB files |
| No `.env.*` except a few | Prefer ignore all env except `!.env.example` |
| No Python/cache patterns | `__pycache__/`, `.mplconfig/` |
| No coverage / surefire | `backend/target/surefire-reports/` |

### 3.2 Recommended additions (apply after approval)

```gitignore
# Build
backend/target/
frontend/dist/
frontend/dist-ssr/
*.class
*.jar
!**/src/**/resources/**

# Local data & analysis
analysis/
reports/
logs/
**/uploads/
backend/data/
*.sqlite
*.db

# Proof / smoke artifacts (uncomment to allow curated subset)
proof/

# Env
.env
.env.*
!.env.example
!**/.env.example

# OS / IDE
.DS_Store
Thumbs.db
.idea/
*.iml
.vscode/
*.swp

# Python / notebooks (if analysis scripts return)
__pycache__/
.mplconfig/
*.pyc
.venv/
venv/

# Node
node_modules/
npm-debug.log*

# Logs / temp
*.log
tmp/
temp/
*.tmp
```

**Also:** ensure IntelliJ never stores secrets in shareable run configs; prefer “Environment files” that point at gitignored `.env`.

---

## 4. Logging / debug / dead code candidates

### 4.1 Excessive / sensitive logging (reduce before public)

| File | Level | Issue |
|---|---|---|
| `OpenAiParserClient.java` | INFO | Logs **full raw classification JSON** |
| `OpenAiVisionClient.java` | INFO | Logs **full Vision raw JSON** (may include meal descriptions) |
| `ImageStorageService.java` | INFO | Logs full filesystem path of stored images |

**Recommendation:** Downgrade raw model payloads to `DEBUG`, or log only length/hash/eventType. Keep WARN for failures.

### 4.2 Acceptable logging

- WARN when API key missing / parse failures (`EventParserService`, `ImageParseService`, `OpenAiConfig`) — fine for ops.

### 4.3 Frontend

- No `console.log` / debug prints in `frontend/src` (good).

### 4.4 Analysis scripts

- `analysis/scripts/run_devfuel_analysis.py` uses `print()` for CLI progress — fine if analysis stays private; not part of app runtime.

### 4.5 Dead / obsolete code candidates

| Candidate | Notes |
|---|---|
| Unused Vite assets (`hero.png`, `react.svg`, `vite.svg`) | No references in `frontend/src` |
| Root design docs `01_`–`12_` | Keep for OSS narrative **or** fold into `docs/` — not dead, but noisy at root |
| `.github/modernize` | Unrelated to DevFuel |
| Duplicate proof trees (`proof/` vs `proof/phase41` overlapping artifacts) | Cleanup candidate |
| Commented `# proof/` in gitignore | Resolve intentionally |

No large blocks of commented-out production Java/TS found in a quick pass. No obsolete TODO blocks flagged in app source.

---

## 5. Refactoring recommendations (non-blocking for cleanup)

1. **Secrets hygiene:** Document in README: set `OPENAI_API_KEY` via env only; never IntelliJ XML.
2. **Default DB password:** Keep for local compose; add a short “Security” section: app has **no auth**; do not expose without a tunnel + basic auth.
3. **Separate `logged_at` vs `event_timestamp`:** Already planned (Feature #1); `created_at` currently doubles as insert time — clarify in docs when publishing schema.
4. **Proof strategy:** Prefer `docs/screenshots/` with **synthetic** UI images over live personal meal photos.
5. **Root clutter:** Move `01_`–`12_` markdown into `docs/` for a cleaner GitHub landing (optional).
6. **License + CODE_OWNERS + SECURITY.md:** Add before public launch.
7. **CI:** Minimal GitHub Action: `mvn test` + `npm ci && npm run build` — only after secrets/data cleanup.
8. **Scan before every push:** `gitleaks` or `trufflehog` on the tree (would have caught `sk-proj-` and Basic auth).

---

## 6. Size / noise footprint (approx.)

| Path | Size | Public? |
|---|---|---|
| `frontend/node_modules/` | ~81 MB | No |
| `backend/target/` | ~52 MB | No |
| `analysis/` | ~23 MB | No |
| `backend/data/` | ~22 MB | No |
| `proof/` | ~4.6 MB | Prefer no |
| App source + docs | small | Yes |

---

## 7. Recommended cleanup actions (ordered)

**Wait for approval before executing.**

### P0 — before any GitHub remote exists

1. **Rotate** the OpenAI API key found in `backend/.idea/workspace.xml`.
2. **Delete or relocate** `version1 script.py` and `version2.py`; rotate Airtel credentials if still active.
3. **Strip** secrets from any IDE run configurations; delete `workspace.xml` or regenerate without env values.
4. Expand `.gitignore` as in §3.2.
5. Confirm `analysis/`, `backend/data/`, `backend/target/`, `node_modules/` will not be added.

### P1 — repo hygiene

6. Delete or gitignore `analysis/` and `proof/` (or scrub proof to synthetic).
7. Remove `.DS_Store` files.
8. Remove unused frontend scaffold assets (after quick UI check).
9. Remove `.github/modernize/` unless wanted.
10. Downgrade OpenAI raw JSON logging to DEBUG.

### P2 — open-source polish

11. Add `LICENSE`, tighten README setup, document env vars.
12. Initialize git, verify `git status` shows only intended files, run secret scan, then push.

---

## 8. Suggested keep set (public-safe)

```
README.md
01_*.md … 12_*.md   (or docs/*)
docker-compose.yml
.env.example
.gitignore          (improved)
backend/pom.xml
backend/src/**
backend/README.md
frontend/package.json
frontend/package-lock.json
frontend/src/**
frontend/public/**
frontend/vite.config.ts
frontend/tsconfig*.json
frontend/.env.example
frontend/README.md
docs/repository_audit.md
```

---

## 9. Approval checklist

Reply with which packs to execute:

- [ ] **A** — Rotate key reminder only (manual); update `.gitignore`
- [ ] **B** — Delete credentialed root scripts (`version1 script.py`, `version2.py`)
- [ ] **C** — Delete / exclude `analysis/`
- [ ] **D** — Delete / exclude `proof/`
- [ ] **E** — Delete unused assets + `.DS_Store` + `.github/modernize`
- [ ] **F** — Reduce OpenAI INFO payload logging
- [ ] **G** — Full P0+P1 cleanup in one pass

---

*Audit generated for DevFuel pre-open-source prep. No automatic deletions were performed.*
