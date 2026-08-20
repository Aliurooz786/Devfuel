# DevFuel Phase 0 — Project Structure

**Status:** Scaffolding phase — structure generated; business logic not implemented  
**Rule:** Folders exist to support log → parse → store → timeline → search only.

---

## 1. Repository layout (monorepo)

```
devfuel/
├── 01_Vision.md
├── 02_MVP_Scope.md
├── 03_User_Stories.md
├── 04_Data_Model.md
├── 05_API_Contract.md
├── 06_Architecture.md
├── 07_Backlog.md
├── 08_Guardrails.md
├── 09_Technical_Design.md
├── 10_Schema.sql
├── 11_Project_Structure.md
├── README.md                          # added at implementation start
├── docker-compose.yml                 # PostgreSQL only (Phase 0)
├── .env.example                       # no secrets; keys listed only
├── .gitignore
│
├── backend/                           # Spring Boot API
│   └── ...
│
└── frontend/                          # React SPA
    └── ...
```

Product docs stay at repo root. Application code lives under `backend/` and `frontend/` only.

---

## 2. Backend structure (`backend/`)

Spring Boot, single module, package-by-feature with thin shared packages.

```
backend/
├── build.gradle.kts                   # or pom.xml — choose one at implementation
├── settings.gradle.kts
├── src/
│   ├── main/
│   │   ├── java/com/devfuel/
│   │   │   ├── DevFuelApplication.java
│   │   │   │
│   │   │   ├── config/
│   │   │   │   ├── CorsConfig.java
│   │   │   │   ├── OpenAiConfig.java
│   │   │   │   └── JacksonConfig.java          # only if needed
│   │   │   │
│   │   │   ├── common/
│   │   │   │   ├── EventType.java              # enum
│   │   │   │   ├── api/
│   │   │   │   │   └── ErrorResponse.java
│   │   │   │   └── exception/
│   │   │   │       ├── ApiException.java
│   │   │   │       ├── ErrorCode.java
│   │   │   │       └── GlobalExceptionHandler.java
│   │   │   │
│   │   │   ├── parser/
│   │   │   │   ├── OpenAiParserClient.java
│   │   │   │   ├── ParseResult.java
│   │   │   │   └── dto/
│   │   │   │       ├── OpenAiParseRequest.java  # internal
│   │   │   │       └── OpenAiParseResponse.java # model JSON shape
│   │   │   │
│   │   │   └── log/
│   │   │       ├── LogController.java
│   │   │       ├── LogService.java
│   │   │       ├── EventLog.java               # JPA entity
│   │   │       ├── EventLogRepository.java
│   │   │       └── dto/
│   │   │           ├── CreateLogRequest.java
│   │   │           ├── CreateLogResponse.java
│   │   │           └── LogItemResponse.java
│   │   │
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-local.yml
│   │       └── db/migration/
│   │           └── V1__event_logs.sql          # content from 10_Schema.sql
│   │
│   └── test/
│       └── java/com/devfuel/
│           ├── parser/
│           │   └── OpenAiParserNormalizationTest.java
│           └── log/
│               └── LogServiceTest.java         # optional for Phase 0
│
└── README.md                                   # how to run backend
```

### Backend package rules

| Package | May contain | Must not contain |
|---------|-------------|------------------|
| `log` | HTTP + persistence for event logs | OpenAI HTTP details beyond calling client |
| `parser` | OpenAI client + parse DTOs | Controllers |
| `common` | Shared errors/enums | Feature business logic |
| `config` | Wiring only | Business rules |

### Backend files intentionally omitted (out of scope)

- Auth / security user tables
- Analytics modules
- Scheduling / notification packages
- Vector / RAG packages
- Multi-module Maven reactor

---

## 3. Frontend structure (`frontend/`)

React SPA. Prefer Vite unless the team standardizes otherwise (frozen at scaffolding).

```
frontend/
├── package.json
├── vite.config.ts                     # or js
├── index.html
├── .env.example                       # VITE_API_BASE_URL=
├── src/
│   ├── main.tsx
│   ├── App.tsx                        # composes log + timeline + search
│   ├── index.css                      # global styles only as needed
│   │
│   ├── api/
│   │   ├── client.ts                  # base URL + fetch helper
│   │   └── logsApi.ts                 # createLog, getTimeline, searchLogs
│   │
│   ├── types/
│   │   └── log.ts                     # mirrors API contracts
│   │
│   └── features/
│       └── logs/
│           ├── LogInput.tsx           # natural language entry
│           ├── Timeline.tsx           # chronological list
│           ├── SearchBar.tsx          # q → search results
│           └── LogListItem.tsx        # one row: type + rawText + time
│
└── README.md
```

### Frontend rules

- One feature folder: `features/logs`
- No `charts/`, `insights/`, `coach/`, or `analytics/` directories
- No auth pages
- API types stay aligned with `09_Technical_Design.md` §8

---

## 4. Docker / env files (repo root)

```
docker-compose.yml
.env.example
```

**`docker-compose.yml` (design intent):**

- Service: `db` (Postgres)
- Port: `5432`
- Volume for data persistence during the 14-day run
- Database name: `devfuel`

**`.env.example` keys:**

```
POSTGRES_USER=
POSTGRES_PASSWORD=
POSTGRES_DB=devfuel
OPENAI_API_KEY=
OPENAI_MODEL=
VITE_API_BASE_URL=http://localhost:8080
```

---

## 5. Mapping: structure → Phase 0 capabilities

| Capability | Backend | Frontend |
|------------|---------|----------|
| Natural language logging | `LogController` + `LogService` + `OpenAiParserClient` | `LogInput` |
| Store event | `EventLog` + `EventLogRepository` + Flyway `V1` | — |
| Timeline | `GET /api/logs` | `Timeline` |
| Search | `GET /api/logs/search` | `SearchBar` |
| Errors | `GlobalExceptionHandler` | inline error in `App` / `LogInput` |

---

## 6. What not to add during scaffolding

Per `08_Guardrails.md` and `07_Backlog.md`:

- Extra apps or packages “for later”
- Shared UI component libraries beyond what logging UI needs
- API gateway, Redis, Kafka, Elasticsearch
- Mobile projects

Scaffold only what this document lists. Anything else waits until Phase 0 logging succeeds for 14 days.

---

## 7. Implementation order (after design approval)

1. `docker-compose.yml` + apply schema
2. Scaffold `backend/` with empty health + `event_logs` repository
3. Implement parser client + `POST /api/logs`
4. Implement `GET /api/logs` and search
5. Scaffold `frontend/` with log input + timeline + search wired to API
6. Smoke test the 14-day loop

No application code in this document — structure only.
