# Project Agent Rules

## Project constraints

- Use Java 17, Spring Boot 4.0.7, and Gradle. Do not change these versions unless the user explicitly requests it.
- Keep the domain-first package structure. Inside each domain, separate `controller`, `service`, `repository`, `entity`, and `dto`; place only cross-cutting code in `global`.
- Prefer constructor injection, lazy JPA relationships, string-mapped enums, DTO-based API boundaries, and service-layer transaction boundaries.

## Sources of truth

- Database and Entity mappings: `docs/데이터모델링.md`
- API contracts and error codes: `docs/API명세서.md`
- Functional behavior: `docs/기능명세서.md`
- Product requirements: `docs/요구사항명세서.md`
- Dependencies and architecture: `docs/서비스기획.md`
- Planned work: `docs/todo/`
- When code changes affect a source-of-truth document, update both in the same task.

## Security invariants

- Authentication providers are `LOCAL`, `KAKAO`, and `GOOGLE`; do not link accounts across providers automatically.
- Store LOCAL passwords only as Argon2 hashes. Never store or log raw passwords, JWT secrets, OAuth tokens, or Refresh Token values.
- Sign Access Tokens with HS256 using a Base64-encoded secret of at least 32 random bytes. Access Token TTL is 15 minutes.
- Refresh Tokens are opaque, expire after 14 days, are delivered via Secure/HttpOnly cookies, and are stored only as hashes. Preserve rotation and reuse-detection behavior.
- Do not place JWTs, Refresh Tokens, or OAuth provider identifiers in redirect URLs.
- Do not globally disable CSRF as a final solution while cookie-based Refresh Token endpoints exist.
- Do not broaden CORS permissions unless the user explicitly defines the frontend origin policy.

## Data invariants

- User IDs are UUIDs; ordinary domain IDs are BIGINT.
- Store locations as `GEOGRAPHY(POINT, 4326)` and construct points in longitude, latitude order.
- Store embeddings as `vector(1536)` unless the selected embedding model and documentation are changed together.
- Do not store a user's live location in `users`.
- Do not manually edit or delete PostGIS system tables such as `spatial_ref_sys`.

## Change and verification rules

- Preserve existing user changes and avoid unrelated edits or destructive Git/filesystem commands.
- Never commit `.env` or real credentials. When adding an environment variable, update `.env.sample` with an empty/example value.
- After Java changes, run `gradlew.bat compileJava`; after Entity, Security, configuration, or behavior changes, run `gradlew.bat test`.
- A successful test task is insufficient if Hibernate logged DDL errors; check test logs for `CommandAcceptanceException` and schema-generation failures.
- Report changed files, verification results, and any remaining migration or security caveats.
