# Personal data and shared finance

## Ownership

Every personal business operation receives an explicit `UserId`. Browser controllers derive it from the verified effective principal. Creating a main finance account can select another eligible user as owner; other domains derive ownership from the principal. Ingestion, file-import jobs and the finance MCP explicitly use eligible identity 1.

| Domain | Owner and inherited data |
| --- | --- |
| Launcher | Group owner; shortcuts and protected icons inherit the group. Positions and initial import are per user. |
| Health | Base metric owner; every detailed measurement inherits it. Health, heart and weight queries filter the base. Re-import replaces only the same user's metric/day. |
| Workouts | Internal numeric primary key; external `source_id` is unique within `(user_id, source_id)`. Time series reference the internal key. |
| Preferences | Weight goal and challenge start date per user, retaining exact legacy values. New users start at 75 kg today. |
| Choices | Composite key `(user_id, score_date)`. Atomic adjustments are independent between users. |
| Locations | Owner on every active entry; range, sparse, edit and bulk-delete operations filter it. |
| Finance | Main account owner; accounts and their transactions/valuations can be viewed and managed by every personal data user. |

People, groups, messages, categories, expense/revenue counterparties, currencies, exchange rates and integration settings remain shared. Groups and contacts cannot own personal data. Legacy historical country codes and the existing migration archive remain stored; Statistics, its APIs and reverse-country calculation are removed.

## Finance permissions and reports

`CASH`, `INVESTMENT`, `REAL_ESTATE` and `OTHER_ASSET` require an owner. `EXPENSE`, `REVENUE`, opening and reconciliation accounts have none. Main account owners cannot be changed through edits. Every personal data user can view and manage any user's main accounts, transactions and valuations, and create accounts for another eligible user. The Accounts view lists Your accounts, other users by name, then Counterparties. `OWN` defaults to the current user or filters by `userId`; `ALL_OWN` supplies main accounts across owners for shared account choices. Main account details include owner ID/name. Personal net-worth and operating summaries default to the current user; an explicit main-account filter uses that account's owner and ledger.

`GET /api/finances/transfer-accounts` searches active main accounts across users and returns only ID, name, owner ID/name and currency. A selected existing account can be hydrated by ID even if inactive; inactive accounts cannot be used for a new transaction. The choices omit balances and valuations; full main-account details are available separately to all users. Selecting a transfer target does not change its type or owner.

A transfer is one transaction and two postings. Every personal data user may create, edit, delete or restore it in either direction, including transactions between other users. Deletes/restores affect the shared aggregate and both personal balances. The existing ledger lock, transaction boundary and optimistic version protect simultaneous operations. Bulk operations validate every selection before mutating any entity.

Shared counterparties expose only transactions involving an owned main account; their displayed balances are calculated from those transactions. Request keys and replay responses use `(user_id, request_key)`. New audits retain the authenticated actor and effective data user. Replays recheck that the referenced object still exists before returning saved results.

Personal operating reports derive cross-user transfers as outgoing expenses and incoming income. The stored transaction type and transaction-list label remain `TRANSFER`. Each report uses its own account side's exact amount/currency, then the existing CHF conversion, date-based rates and missing-rate warnings. Transfers between the same owner's main accounts are excluded even if only one account is selected. No duplicate income/expense transactions are created. Future group reports can still classify internal transfers from the account owners; group reports are not implemented here.

## Calendars

`calendar_user(calendar_id,user_id)` is many-to-many. Admins configure eligible users in each synced calendar's multi-select. Initial calendars and newly selected calendars default to user 1. Empty membership hides a calendar for every user without deleting events or stopping synchronization. Day and upcoming-event views use assigned calendars only.

An assigned user may hide a local occurrence, including on a read-only calendar. The deletion marker applies calendar-wide to every assigned user. No Google event is deleted. Unassigned users receive 404. Assignments reject groups, contacts, duplicate and missing IDs atomically.

## Migration and rollback

V27 validates legacy identities before V28's non-transactional MariaDB DDL: existing users or any personal legacy population require identity 1 to be an existing non-group user. This also prevents migrating an existing installation into a state with users but no administrator. Contradictory legacy user/admin flags fail explicitly. Empty installations retain first-login bootstrap. No synthetic owner is manufactured. V28 maps user 1 to Admin and other existing users to User, backfills personal data/main accounts to user 1, moves exact weight preferences, converts workout references, scopes replay records, and creates calendar assignments. Shared counterparties, categories, technical accounts, bookings and amounts are retained.

The schema change replaces columns and keys, so deploying the older application over V28 is not an application-only rollback. Before production deployment, create and verify a fresh backup, stop application writers, migrate and reconcile counts/relationships/financial balances, and retain the previous image. Recovery requires stopping the new application, restoring the pre-migration database/uploads/configuration and running the matching old image. Account for writes made after the backup; never overwrite production from a development database. Production deployment is a separate step.

## Verification and repeatable rehearsal

Use the repository's isolated test instance only:

```sh
docker compose -f docker-compose-test.yaml up -d --wait
cd backend
mvn clean install
# From frontend/, with Node 24:
npm test -- --watch=false
npm run build
```

`PersonalMigrationTest` creates and drops its own uniquely named `_test` schema on the isolated test container. It verifies preflight failure before DDL, a populated workout/time-series upgrade and full preference preservation. The test-only root credential can be overridden with `THEREABOUT_TEST_DB_ROOT_USER/PASSWORD` for a custom isolated instance.

For a real backup rehearsal, verify its SHA-256 checksums, copy/extract the SQL to a private temporary directory, restore into a separate `_test` database/container using the source MariaDB version, and compare every table count with the backup manifest. Change only the copied dump's database qualifier when naming the isolated schema. Keep the original archive unchanged. Then run from `backend/`:

```sh
THEREABOUT_REHEARSAL_URL=jdbc:mariadb://127.0.0.1:3308/thereabout_rehearsal_test \
THEREABOUT_REHEARSAL_USER=thereabout_test \
THEREABOUT_REHEARSAL_PASSWORD=test-only \
THEREABOUT_REHEARSAL_COUNTS=/private/copied-backup/source-row-counts.json \
mvn -Dtest=MigrationRehearsalTest test
```

This opt-in test uses original production migrations and compares all legacy row counts (apart from deliberate configuration/history changes), workout references, historical country-code populations, owner assignments, preferences and independently summed account/currency balances. Never point it at production or a development database.

The 27 September 2026 backup rehearsal used MariaDB 13.0.2. All 43 restored table counts matched the backup manifest before migration. V26–V28 then reconciled 41 unchanged legacy tables with 3,480,814 rows and all 1,959 account/currency balances. Legacy preferences, workout references and historical country-code populations matched. The source archive and production were unchanged.

The final implementation passed `mvn clean install` with 217 successful tests (the opt-in backup rehearsal is skipped in that normal run), all 230 frontend tests on Node 24, production bundle generation and `git diff --check`. Browser checks against the isolated migrated backup covered desktop and 390×844 mobile layouts, user/admin navigation, empty personal launcher/accounts for the second user, role selection in Create User and Edit Identity, eligible-user calendar multi-select, transfer account search with owner/currency, reload ending impersonation, and context switching during delayed finance requests. Automated tests additionally cover cross-user transfer reports, currency conversion, shared counterparty isolation, mixed bulk rollback, scoped retries, revoked impersonation privileges, concurrent last-admin demotions and the MCP's fixed data user/audit actor.
