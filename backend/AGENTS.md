# Backend Agents Guide

These instructions extend the repository's root `agents.md` for work in `backend/`. Its Maven, AssertJ and JPA column-mapping rules still apply.

## Readable architecture and API contracts

- Prefer cohesive services, explicit domain types and small, clearly named methods over large controllers, generic maps and scattered query strings. Keep persistence, business rules and transport mapping separate.
- Prefer JPA entities and repositories for domain persistence and writes. Use JDBC or native SQL only where there is a concrete benefit, such as complex reporting, aggregation or an atomic database operation; encapsulate parameterized SQL in a repository. Do not make either approach a blanket rule regardless of the use case.
- REST and MCP must call the same business services, with the same validation, authorization, version checks and idempotency behaviour. Do not duplicate financial rules in transport handlers.
- Keep `src/main/resources/thereabout.openapi.yaml` as the API entry point and extend the appropriate domain files under `openapi/` using `$ref`. Avoid rebuilding a monolithic spec or duplicating shared schemas. Regenerate affected server/client code through the existing generation workflow; do not patch generated files manually.
- Keep the current scope small while leaving clear extension points. Do not implement speculative position tracking, trade imports or statement parsers merely because the model should support them later.

## Financial correctness

- Use `BigDecimal` and sufficiently precise SQL `DECIMAL` columns. Transfer amounts as decimal strings, omitting insignificant padding zeros without losing significant precision. Two-decimal UI formatting must not become global rounding of stored or imported data.
- Save a transaction and its paired account movements atomically. Derive balances from postings, including openings and reconciliation entries; exclude deleted transactions. Preserve reversible deletion, audit history, optimistic version checks and idempotency for writes.
- Preserve the actual amount and currency on each side of a foreign-currency transfer. Keep currency conversion for reporting separate from booked amounts. Internal transfers, openings, technical reconciliations and valuation changes must not inflate operating income or expenses.
- Use the latest available exchange rate on or before the valuation date, with its source and date retained. Missing rates must produce an explicitly incomplete valuation, not an invented rate or silently omitted value.
- Keep accounts, money movements, reported total valuations and valuation corrections separate and linked. Existing account-level gains/losses remain supported; future position values and account totals must not be counted twice in net worth.
- A linked valuation correction must not be edited as an ordinary independent transaction. When implementing valuation amendments, update the reported valuation and derived correction coherently, with a preview, history, version checks and idempotency. A zero difference creates no correction. Backdated edits must not silently rewrite later valuations.
- Do not present a currently missing valuation-amendment workflow as a permanent accounting prohibition. Explain unsupported operations accurately and preserve the model's ability to support deliberate corrections.

## Configuration and shared integrations

- Store the application-managed MCP credential in the database. Generate a cryptographically random value at startup only when none exists, using an atomic initialization that is safe across concurrent starts. Preserve it across restarts; do not use a property/environment value as the ongoing source of truth.
- Credential reveal belongs in a protected configuration endpoint with non-cacheable responses. Exclude the secret from general configuration responses, MCP tools and logs. During migration from a previous credential source, preserve the existing credential rather than unexpectedly rotating it.
- Reuse the shared website-icon service for launcher and bank icons. Accept a configurable website, normalize it, cache fetched icons and fail gracefully. Keep external fetches bounded and retain the service's URL/network and content validation instead of introducing an unrestricted URL proxy.

## Imports, testing and release safety

- Work from a copied, checksum-verified backup when importing Firefly data. Keep the original backup and source production database unchanged. Preserve source IDs, relationships, deletion/inactive status, precise amounts, currencies, notes and external references; archive relevant unsupported data rather than silently discarding it.
- Record source anomalies instead of silently applying accounting corrections. Verify the complete imported population, not only a sample: counts, relationships, deletion status, amounts and independently calculated current/historical balances and reports.
- Use the existing test setup and verify the configured database target before tests or restore commands. Permission to reset a local development database is not permission to replace production data. After destructive local testing for a demo, restore the full relevant dataset and ensure no test transactions remain.
- Run Maven from `backend/` as described in the root guide. Add meaningful regression coverage for affected accounting behaviour: precision, paired postings, foreign currencies, backdated changes, deletion/restoration, valuation corrections, retries and version conflicts. Verify MCP protocol changes with a real client as well as service tests.
- For an authorized production release, follow the existing release runbook, preserve production data/configuration and verify a recoverable backup before migrations. Use Flyway migrations; do not overwrite production with a local database to deliver a code change. A repeat import is a separate data operation, not an automatic deployment step.
- Distinguish local tests, CI success and live verification. Confirm the running revision, migration success and affected production flows before claiming deployment success. For financial data changes, include a before/after reconciliation. Keep backups, credentials and temporary operational artifacts out of Git.

## Transactions and entity lifecycles

- Define transaction boundaries around service use cases. Group related writes atomically; mark transactional read-only queries with `@Transactional(readOnly = true)`.
- Prefer lazy entity references. Use cascades only where the parent owns the child's lifecycle; do not cascade `ALL` between independent entities.
- Keep endpoint and DTO descriptions in OpenAPI. Comments should explain rationale, constraints and invariants rather than restating signatures.
- Use the isolated test database described in the root guide. Never run destructive fixtures against development or production data.
