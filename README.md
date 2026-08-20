# DevFuel Phase 0

Truth collection system — natural language logging for 14 days.

## Docs

See `01_Vision.md` … `11_Project_Structure.md`.

## Scaffolding (current)

Structure only. No OpenAI integration. No business logic. No UI.

### Prerequisites

- Java 17+
- Node.js LTS
- Docker (PostgreSQL)

### Start database

```bash
docker compose up -d
```

Postgres is published on host port **5433** (avoids clashing with a local Homebrew Postgres on 5432).

### Backend

```bash
export JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"
cd backend
./mvnw spring-boot:run
# or: mvn spring-boot:run
```

API base: `http://localhost:8080`

### Frontend

```bash
cd frontend
npm install
npm run dev
```

App: `http://localhost:5173`
