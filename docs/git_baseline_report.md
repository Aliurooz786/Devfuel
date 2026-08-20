# Git Baseline Report

**Date:** 2026-08-20  
**Action:** Initialize repository, first baseline commit, push to GitHub

---

## Baseline commit

| Field | Value |
|---|---|
| **Branch** | `main` |
| **Commit hash** | `7204f50afdde4f600377ed0da048e7937600b8ed` |
| **Short hash** | `7204f50` |
| **Commit message** | DevFuel v1.0 - baseline before recovery roadmap |
| **Remote URL** | `https://github.com/Aliurooz786/Devfuel.git` |
| **Files committed** | **75** |
| **Diffstat** | 75 files changed, 6005 insertions(+) |

---

## Ignore verification (pre-commit)

| Path | Result |
|---|---|
| `analysis/` | Ignored (`.gitignore:34:analysis/`) |
| `proof/` | Ignored (`.gitignore:36:proof/`) |
| `backend/data/` | Ignored (`.gitignore:21:backend/data/`) |
| `backend/target/` | Ignored (`.gitignore:20:backend/target/`) |
| `backend/.idea/` | Ignored (`.gitignore:4:.idea/`) |
| `frontend/node_modules/` | Ignored (`frontend/.gitignore:10:node_modules`) |
| `.env` | Ignored (`.gitignore:14:.env`) |
| `.env.local` | Ignored (`.gitignore:15:.env.*`) |

Also excluded by policy: `logs/`, `**/uploads/`, `reports/`, build caches.

---

## Secret scan (staged content)

| Check | Result |
|---|---|
| Real `sk-proj-…` API keys | **None** |
| Hardcoded Basic auth / JSESSIONID | **None** |
| Sensitive filenames (`.env`, `workspace.xml`, uploads) | **None staged** |
| Placeholder in `.env.example` (`sk-your-key-here`) | Present — intentional, safe |

**Reminder:** Local `backend/.idea/workspace.xml` may still hold a real key on disk; it is **not** tracked. Rotate that key if not already done.

---

## Files committed (75)

```
.env.example
.gitignore
01_Vision.md
02_MVP_Scope.md
03_User_Stories.md
04_Data_Model.md
05_API_Contract.md
06_Architecture.md
07_Backlog.md
08_Guardrails.md
09_Technical_Design.md
10_Schema.sql
11_Project_Structure.md
12_Phase5_Plan.md
README.md
backend/README.md
backend/pom.xml
backend/src/main/java/com/devfuel/DevFuelApplication.java
backend/src/main/java/com/devfuel/common/EventType.java
backend/src/main/java/com/devfuel/common/api/ErrorBody.java
backend/src/main/java/com/devfuel/common/api/ErrorResponse.java
backend/src/main/java/com/devfuel/common/exception/ApiException.java
backend/src/main/java/com/devfuel/common/exception/ErrorCode.java
backend/src/main/java/com/devfuel/common/exception/GlobalExceptionHandler.java
backend/src/main/java/com/devfuel/config/CorsConfig.java
backend/src/main/java/com/devfuel/config/OpenAiConfig.java
backend/src/main/java/com/devfuel/config/OpenAiProperties.java
backend/src/main/java/com/devfuel/config/ParserProperties.java
backend/src/main/java/com/devfuel/config/UploadProperties.java
backend/src/main/java/com/devfuel/log/EventLog.java
backend/src/main/java/com/devfuel/log/EventLogRepository.java
backend/src/main/java/com/devfuel/log/ImageStorageService.java
backend/src/main/java/com/devfuel/log/LogController.java
backend/src/main/java/com/devfuel/log/LogService.java
backend/src/main/java/com/devfuel/log/dto/CreateLogRequest.java
backend/src/main/java/com/devfuel/log/dto/CreateLogResponse.java
backend/src/main/java/com/devfuel/log/dto/LogItemResponse.java
backend/src/main/java/com/devfuel/parser/EventParserService.java
backend/src/main/java/com/devfuel/parser/ImageParseService.java
backend/src/main/java/com/devfuel/parser/OpenAiParseException.java
backend/src/main/java/com/devfuel/parser/OpenAiParserClient.java
backend/src/main/java/com/devfuel/parser/OpenAiVisionClient.java
backend/src/main/java/com/devfuel/parser/ParseResult.java
backend/src/main/java/com/devfuel/parser/dto/OpenAiChatDtos.java
backend/src/main/java/com/devfuel/parser/dto/OpenAiParseResponse.java
backend/src/main/resources/application.yml
backend/src/main/resources/db/migration/V1__event_logs.sql
backend/src/main/resources/db/migration/V2__image_logging.sql
backend/src/test/java/com/devfuel/parser/EventParserServiceTest.java
docker-compose.yml
docs/git_cleanup_report.md
docs/repository_audit.md
frontend/.env.example
frontend/.gitignore
frontend/.oxlintrc.json
frontend/README.md
frontend/index.html
frontend/package-lock.json
frontend/package.json
frontend/public/favicon.svg
frontend/public/icons.svg
frontend/src/App.css
frontend/src/App.tsx
frontend/src/api/client.ts
frontend/src/api/logsApi.ts
frontend/src/assets/hero.png
frontend/src/assets/react.svg
frontend/src/assets/vite.svg
frontend/src/index.css
frontend/src/main.tsx
frontend/src/types/log.ts
frontend/tsconfig.app.json
frontend/tsconfig.json
frontend/tsconfig.node.json
frontend/vite.config.ts
```

---

## Ignored locally (not in commit)

Major trees still present on disk but excluded:

- `analysis/` — personal health analysis outputs
- `proof/` — phase smoke artifacts
- `backend/data/` — uploads / local runtime data
- `backend/target/` — Maven build
- `frontend/node_modules/` — npm deps
- `backend/.idea/` — IDE (may contain local secrets)

---

## Remote push

```
git remote add origin https://github.com/Aliurooz786/Devfuel.git
git push -u origin main
```

**Result:** Success — `main` tracking `origin/main`  
**Repo:** https://github.com/Aliurooz786/Devfuel

---

## Post-status

```
## main...origin/main
```

Working tree clean after baseline push (this report file may appear as a follow-up untracked/committed doc).
