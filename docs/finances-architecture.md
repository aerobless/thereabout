# Finances architecture

The financial behaviour and imported ledger are preserved. Core writes now use JPA; SQL remains explicit where it makes historical projections and batch import easier to audit.

## Backend boundaries

- `finance/domain`: shared validation and exact decimal/date handling.
- `finance/data`: JPA entities and repositories, typed SQL read projections, and one explicit batch writer for downloaded ECB rates.
- `finance/service`: account, category, transaction, valuation, exchange-rate and report use cases. REST and MCP invoke these same services.
- `finance/transport`: typed REST endpoints, consistent HTTP errors, strict decimal-string deserialization and the MCP SDK adapter. Entities are never exposed directly.

`FinanceTransactionEntity` owns its two `FinancePostingEntity` records. Account, category, transaction and manual-rate changes use `@Version`. Source IDs, source metadata, deleted flags and archive records retain their imported meaning. No cascade physically deletes ledger history.

`FinanceWriteCoordinator` is the shared transaction boundary. It acquires the singleton pessimistic write lock before reading mutable state; the entity changes, both postings, audit record and replay response either all commit or all roll back. Canonical request fingerprints distinguish retries from key reuse with different input. A transaction update timestamp dirties the aggregate even if only a posting amount changes, so the parent version protects the complete financial operation.

A failed bulk categorization releases its request key and rolls back earlier items. Two requests with the same key produce one transaction; two edits with the same version yield one success and one conflict. Tests exercise these cases with concurrent database transactions.

ECB downloads run outside a database transaction and lock. Existing successful refresh requests replay without downloading again. The parsed batch is committed under the same finance write lock. Manual rates use JPA, audit history and optimistic versions. Report conversion has a per-request rate cache, never shared mutable request state.

An injected `Clock` uses Europe/Zurich. Money remains `BigDecimal`/`DECIMAL(36,24)` internally and decimal strings at transport boundaries. Numeric JSON tokens for financial amounts are rejected before coercion. Original and foreign posting amounts are not recomputed by reports.

## SQL that remains intentionally

`FinanceReadRepository` contains read-only projections for lists, historical running balances, ledger snapshots, audit history and metadata. `ExchangeRateReadRepository` retrieves the dated conversion quote. Both map into explicit typed DTOs or records; there are no generic row maps in business logic.

`ExchangeRateBatchRepository` is the only application SQL write exception, for bulk upserts of downloaded reference rates. Local Python restore/import scripts use SQL because they preserve complete source rows and archive data. They are administration tools, not application API operations.

This avoids turning every historical aggregation into an entity graph or loading lazy associations in controllers. It also keeps financial validation out of SQL strings.

## Contracts and UI

`openapi/finances.yaml` is a small finance catalog used by MCP. The main OpenAPI document references resource files directly because the generator does not reliably follow chained path-item references. Resource definitions live in `openapi/finances/`: accounts, categories, transactions, valuations, rates, reports (including overview), and shared schemas. Keep operations and their request/response schemas together; use relative `$ref` links for shared definitions. Add new public paths and models to the finance index and the main document as needed. The main document remains the single generation entry point for Java DTOs and the Angular client. MCP loads the same YAML schemas at startup, resolving bundled relative references into self-contained input schemas; it does not fetch external references. Generation does not skip changes to referenced schema files. Existing MCP tool names remain stable, with `finance_currencies_list` added; responses consistently use camelCase fields. The old generic REST dispatcher and separate handwritten MCP schema file are removed.

The Angular finance shell provides local context, navigation and dialog coordination. Overview, Accounts, Account Detail, Transactions and Reports are routed pages. Account, category, transaction, valuation, exchange-rate, history and deletion dialogs have separate components. Typed reactive forms preserve exact decimal text. Shared request resources use `switchMap` to cancel obsolete requests and recover after errors; components use OnPush change detection and signals for asynchronous state.

The deletion confirmation is an in-app modal. It performs no write until explicitly confirmed and uses the displayed transaction version. Request keys are retained for unchanged retries after uncertain responses.

## Migrations and verification

- V20 remains unchanged.
- V21 adds manual-rate versions and initializes existing versions from their audit history.
- V22 adds the transaction aggregate update timestamp; imported rows remain untouched until edited.

The existing local demo upgraded successfully. A separate rehearsal schema received all 7,461 Firefly journals and 14,922 postings through the refactored importer. Ten historical balance checkpoints, exact amounts and source relationships reconciled without differences. Reports match independent Firefly SQL and the pre-refactor API baseline, allowing only the new DTO representation of optional null fields.

Run backend tests from `backend/` with `mvn clean install -Dspring.datasource.url=jdbc:mariadb://localhost:3306/thereabout_finance_test`. This existing local test clone isolates synthetic fixtures from demo data. New backend assertions use AssertJ. See [the local runbook](finances-local-mvp.md) for frontend commands, runtime setup, verification evidence and the remaining live-browser limitation.
