# Refactoring boundaries

The September 2026 cleanup separates responsibilities while retaining existing import formats, stored health details, Telegram history, routes and day navigation.

## Ownership

- `FileImportService` owns upload staging, the single import worker, rejection of concurrent starts (HTTP 409), terminal status and temporary-file cleanup. Importers parse and persist data. A failed import may have committed earlier batches; the status states this explicitly.
- `HealthDataService` remains the shared facade. `HealthMetricImportService` and `WorkoutImportService` own their write transactions, their mappers handle generated transport types, and `HealthQueryService` owns read-only queries.
- `TelegramTdlibService` owns the client lifecycle and synchronization orchestration. `TelegramBackfill` handles paginated historical reads and checkpoints, including upgraded basic-group histories and their existing message ID prefixes.
- Configuration composes independent file-import and Telegram settings components. Each owns one replaceable polling subscription, disposed on destruction.
- Day View owns date navigation and composes location, steps, messages and workouts. Its provided signal stores share a selected date and keep requests, errors and editing state local to that page. Day View and Location History use the same local-calendar-date helpers.

## Deliberate removals and retained features

- Removed unreferenced date/formatting and Telegram lookup helpers, unused imports and redundant historical backfill loops.
- Removed the misleading “Days spent abroad” statistic; country statistics remain available.
- Message counters use the authenticated user's identity. Received messages require an explicit receiver match; group or unknown recipients are not assumed to be the current user. Without a known identity the card shows the total. The message list is unchanged.
- Retained uncommon health metric details, Telegram folder/history support and existing authentication behaviour. Rarity alone is not evidence of dead functionality.

## Verification and test data

Run the commands in the root `agents.md`. Backend tests use a disposable Compose MariaDB on port 3307 with dedicated credentials and an `_test` schema guard before datasource/Flyway initialization. Maven generates test-only copies of historical migrations that contain the hardcoded `thereabout.` schema qualifier; production migration files are unchanged.

Regression coverage includes import exclusion/failure/cleanup, polling disposal, supported health metric and workout mappings, Telegram pagination and upgraded groups, date navigation, stale responses, refresh preservation and user-aware message counts. Browser checks cover desktop and 390 px layouts, date selection, map/step/message dialogs and the import selector. Production imports, live Telegram resynchronization and deployment require their own verification.
