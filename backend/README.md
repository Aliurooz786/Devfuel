# DevFuel Backend

Spring Boot 3.4 / Java 17 for Phase 0.

## Run

Requires Docker Postgres (`docker compose up -d` from repo root).

```bash
export JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"
# Optional — without this, logs save as UNKNOWN
export OPENAI_API_KEY="sk-..."
export OPENAI_MODEL="gpt-4o-mini"
mvn spring-boot:run
```

Default datasource: `jdbc:postgresql://localhost:5433/devfuel`

## OpenAI

- Keys come from environment only (`OPENAI_API_KEY`, `OPENAI_MODEL`)
- Never commit real keys; use `.env.example` placeholders only
- App starts without a key and falls back to `UNKNOWN` + `parseError`
