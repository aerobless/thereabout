# Finances local MVP

The local application is at **http://127.0.0.1:4200/finances**, with backend port **9050**.
This document records the original local MVP and its verification. For the subsequent production deployment and access configuration, see [production operations](finances-production.md). Nullpaw integration remains out of scope.

## Start

From the repository root, keep these commands running in separate terminals:

```sh
docker compose -f docker-compose-development.yaml up -d
./scripts/finances/run-local.sh
```

```sh
cd frontend
export PATH="/opt/homebrew/opt/node@24/bin:$PATH"
npm start -- --host 127.0.0.1
```

Java 25, Maven and Node 24 are required. The launcher script enables the `development` profile, binds the backend to loopback and uses the database-backed MCP key. At application startup, a cryptographically random 256-bit key is generated if `configuration.FINANCE_MCP_KEY` does not exist. Existing keys survive restarts. Reveal it in **Configuration → Finances MCP** by focusing the masked field; leaving the field clears it from the component. Calendar background synchronization is disabled by the development profile; launcher icons load normally. A Google Maps key is optional for startup, but real map functionality requires one.

Finance endpoints are always registered, locally and in production. Local access mode admits only loopback callers and Host names, and browser writes require the CSRF token. MCP additionally requires the bearer key. Production uses the Cloudflare access mode described in the production operations guide.

## MCP and REST

MCP uses the official Java SDK (`io.modelcontextprotocol.sdk:mcp:2.0.1`) with its Streamable HTTP servlet transport. Client configuration shape (substitute the key privately, never in source control):

```json
{
  "mcpServers": {
    "thereabout-finances": {
      "url": "http://127.0.0.1:9050/mcp/finances",
      "headers": {"Authorization": "Bearer <local-key>"}
    },
    "thereabout-identities": {
      "url": "http://127.0.0.1:9050/mcp/identities",
      "headers": {"Authorization": "Bearer <local-key>"}
    }
  }
}
```

Copy the key from Configuration into your MCP client's private credential storage. The application no longer reads a `FINANCE_MCP_KEY` environment variable or key file. No client is configured automatically.

The finance operations cover overview, accounts, categories, currencies, transaction listing/details/creation/editing/deletion/restoration/bulk classification, valuation preview/save/history, reports, and exchange-rate listing/manual correction/ECB refresh. Tool names are `finance_` followed by the operation with dots replaced by underscores. Their input schemas are derived from `backend/src/main/resources/openapi/finances.yaml`, the same contract used to generate REST DTOs and the Angular client.

REST uses explicit typed endpoints: `GET /api/finances/transactions`, `POST /api/finances/transactions`, `PUT /api/finances/transactions/{id}`, and `DELETE /api/finances/transactions/{id}`; other resources follow the same pattern. Reports are available at `/api/finances/reports/income-expenses`, `/categories` and `/investments`. The generic operation dispatcher has been removed. Angular uses the generated OpenAPI `FinancesService`. Regenerate it with `npm run openapi:generate` in `frontend/` after changing the contract.

Amounts are decimal strings in both directions. Writes require a unique `requestKey`; retry the **same operation and arguments with the same key** if a response is lost. A different payload with a used key returns a conflict. Account/category/transaction edits and replacement of manual exchange rates require the returned `version`; reload after a conflict. The browser retains a failed write's key while its payload stays unchanged. A database lock serializes ledger writes, and both postings, audit entry and replay response commit in one transaction.

Counterparty metadata can be edited with `finance_counterparties_save`. First read `finance_counterparties_get`, then send the ID and the complete REST update inside `input`:

```json
{
  "id": 123,
  "input": {
    "name": "Example company",
    "aliases": ["Example company", "Original merchant label"],
    "websiteUrl": "https://example.com/",
    "version": 4,
    "requestKey": "unique-key-for-this-edit"
  }
}
```

For website-only edits, copy the current name and complete aliases list unchanged. The website is normalized to an HTTPS origin; omitting it or sending an empty string clears it. REST and MCP use the same authorization, validation, version checks, audit and idempotency service. A combined ID or stale version returns a conflict; reload before editing. Metadata edits preserve ledger accounts and financial history.

## Identity MCP and browser writes

`/mcp/identities` uses the same database-managed bearer credential and endpoint admission as `/mcp/finances`, including supported Cloudflare machine clients and loopback development clients. Eligible authenticated callers need **no administrator role** for identity tools. Browser identity pages and REST operations retain their administrator restrictions. Browser impersonation does not change the finance MCP data-user context.

The settings catalog lists both endpoints and their complete current tool schemas. The identity endpoint offers `identity_list`, `identity_get`, `identity_create`, `identity_update`, `identity_delete`, `identity_applications_list`, `identity_applications_get`, `identity_applications_link`, `identity_applications_unlink`, `identity_members_get` and `identity_members_save`. List tools accept `q`, `isGroup`, `application`, `linked`, `identityId`, `page` and `pageSize` (1–200). Searches include names, relationships, imported identifiers and username hints.

MCP write inputs accept only identity metadata and write controls: unsupported fields are rejected. No endpoint registers Create User or role-management tools. New contacts/groups have no role; metadata updates preserve user roles and Cloudflare links. Cloudflare linking/unlinking and user deletion are prohibited. App tools only manage existing imported IDs. Contact/group deletion unlinks and retains imported IDs and their message history. Membership grants access to the group's complete history.

Identity REST and MCP share transactional validation, receipts and auditing. Every metadata/link/membership/delete write requires `requestKey`; updates/deletion require the identity's `version`, app mutations require the app's `version`, linking also requires the destination's `identityVersion`. Retry identical inputs with the same key; changed inputs need a new key. The browser retains retry keys for unchanged drafts. Identity metadata and membership share the existing identity version column; additive Flyway V36 versions imported app IDs. Audits retain the authenticated request actor when available. UI-only user creation and role changes retain their existing protected endpoints.

## CSV matching and review

Responses structured output extracts CSV evidence, own transfer accounts, categories and account guidance. It does not receive the complete counterparty catalog. Canonical names and aliases are searched locally; unique normalized exact matches are selected automatically. Other plausible matches are ranked using merchant tokens and spelling similarity, with at most eight candidates sent to [Decisions](https://developers.openai.com/api/docs/guides/decisions), including a “none fits” choice. Repeated merchant decisions are cached within the import. Java SDK 4.78.0 supplies the Decisions client.

Unique normalized names/aliases are accepted automatically. For legacy identities with identical canonical names, a unique active ledger for the required direction and currency resolves the match. Other existing matches are accepted automatically only when Decisions confidence and the selected option probability are both at least 0.95; unresolved exact-name collisions still require review. Weaker suggestions require confirmation before approval. Use the row checkmark, or choose a counterparty in the row editor and apply it. When plausible matches exist, creating a new counterparty requires explicit confirmation. Failures/refusals retain candidates for review and cannot cause automatic creation. Changes to matching evidence reset confirmation. Match suggestions and selected versions are server-owned; original CSV evidence stays immutable.

Preparation writes no ledger records. Approval revalidates selected canonical identities and returns changed/unavailable choices to review. A missing currency/direction account is created under the selected canonical identity during approval. Duplicate protection, exact decimal strings, cancellation and idempotent approval remain in place. Polling reports interpretation, counterparty checking and validation stages with real counters, excluding discarded headers/totals from “Checking transaction 12 of 43”.

Transaction searches accept `descriptionQ`, `fromQ` and `toQ`, combined with AND; side searches include canonical names, aliases and ledger names. Fixed-account tables use `counterpartQ` on the opposite side. Legacy `q`, account/report drill-down scope and API flags remain supported. Report date presets use calendar-year boundaries and valid manual changes refresh automatically. Empty report sections are hidden while zero-valued accounts remain visible.

## Data and recovery

Only the approved backup `Firefly-III_2026-09-26_10-18-00.zip` is used. Its original in Google Drive remains unchanged. A private local copy, extracted dump, logs and reconciliation reports are stored in:

```text
~/Library/Application Support/thereabout-finances/
```

Restore/import tools connect only to `127.0.0.1:3306`, using credentials from the existing local Docker container. They never access the live Firefly server. Stop the local backend before replacing finance data.

```sh
python3 scripts/finances/restore_source.py \
  "$HOME/My Drive/backups/Manual/Firefly-III_2026-09-26_10-18-00.zip"

"$HOME/Library/Application Support/thereabout-finances/venv/bin/python" \
  scripts/finances/import_firefly.py --replace-local-finances \
  --report "$HOME/Library/Application Support/thereabout-finances/import-report.json"
```

For a new machine, create that venv with `python3 -m venv` and install `pymysql` in it. Run the backend once to apply Flyway V20–V22 before importing, then stop it. The restore script verifies the copied archive against its input and the SQL dump against the approved SHA-256:

```text
57c049aa3efed1559d458cc818a24022ce58a60f441e42d55c0538e6f473bc34
```

The restore schema is `firefly_local_source`; the destination is `thereabout`. Reimport replaces **only the finance tables**, including local edits, audit history, requests and fetched rates. Other Thereabout modules are not cleared. Refresh ECB rates from the UI after a reimport. No SQL dumps, key values or actual financial fixtures are added to the repository.

The original IDs are preserved as `source_id`, and source records/metadata retain Firefly relationships. `finance_archive` preserves the non-UI financial tables, including deleted data, tags, budgets, bills/subscriptions and rules. Authentication/session/job/configuration tables are deliberately not copied into the application's financial archive; the unchanged full SQL backup preserves them for disaster recovery.

A verify-only run leaves application data unchanged:

```sh
"$HOME/Library/Application Support/thereabout-finances/venv/bin/python" \
  scripts/finances/import_firefly.py --verify-only \
  --report "$HOME/Library/Application Support/thereabout-finances/verification.json"

# With the backend running, independently check reports against Firefly SQL:
"$HOME/Library/Application Support/thereabout-finances/venv/bin/python" \
  scripts/finances/verify_reports.py
```

The importer compares every account/currency balance at ten historical cutoffs (2018–2025 year ends, August 30 and September 26, 2026), exact original/foreign amounts, posting relationships, currencies, category relationships, dates, deletion flags, active flags and core row counts. It rolls back on mismatch. The report verifier independently groups the 2026 Firefly source transactions and checks category/month totals and each investment/property value equation. It deliberately rejects a changed fixture requiring foreign-currency report conversion rather than silently assuming CHF.

## Accounting and extension points

`finance_transaction` holds business context; two `finance_posting` rows hold signed money movements. New money obeys currency precision. Existing imported sub-cent values are preserved when editing metadata. Saldo queries include openings and reconciliation transactions, and exclude deleted journals/postings. Account running balances use all earlier entries, not just the displayed page or filter.

Source type, category and counterparty determine operating cash flow vs. transfers, openings, reconciliation and valuation changes. The imported Stock Market Updates category is mapped explicitly to valuation effects. No misclassified source transfer or expense is silently corrected. Reports can therefore intentionally differ from Firefly's generic income/expense view, which includes market adjustments.

Own accounts have four roles, separate from counterparties and technical opening/reconciliation accounts. Net-worth inclusion is copied and editable. The property account retains its recorded equity, not an inferred gross property price.

`finance_valuation` stores the reported aggregate, prior balance, date, origin and linked adjustment. A preview checks the exact book balance again on save. A zero difference creates no posting. Reusing an account/reference cannot create another correction. Linked valuations are corrected with a new dated valuation, not by silently changing or deleting their posted adjustment. Category changes remain available through bulk classification. Backdated edits do not rewrite subsequent historical valuations.

Imported valuation totals have origin `FIREFLY_INFERRED`: they are reconstructed from the historical ledger around each existing adjustment, not independent statement evidence. The UI labels this distinction.

Future positions can reference an account and an instrument and have their own quantity/cost/valuation history. Reconcile position values plus remaining cash against a statement's aggregate. Any residual should be explicit. Only the account ledger value contributes to net worth; do not add the positions' values a second time. No position/trade/price or statement-parser functionality is implemented here.

ECB reference rates are stored by date and converted to CHF; a query takes the latest available date not after the valuation date, preferring a manual rate over ECB on the same day. Missing rates produce an explicitly partial valuation. Rates older than seven days are flagged. Original transaction amounts never change. Operating reports convert at transaction dates; investment reconciliation remains in account currency. Rate source/date is available in the API, the account detail and the reports' exchange-rate details.

Dates are local business dates/times in Europe/Zurich. Monthly history points are booked account balances, not a forecast or an investment-return calculation.

## Validation and limits

- Backend: `cd backend && mvn clean install -Dspring.datasource.url=jdbc:mariadb://localhost:3306/thereabout_finance_test`.
- Frontend: Node 24, `npm test -- --watch=false` and `npm run build` in `frontend/`.
- Tests use a local clone of the existing schema/Flyway history, including the `calendar_connection` and `finance_write_lock` singleton rows. The existing historical V1/V5 migrations conflict on a brand-new empty schema; they were not rewritten as part of this feature. The normal existing development schema upgrades with V20–V22.
- Finance tests cover decimal arithmetic, transfers/FX, negative balances, filtered running balances, backdated edits, delete/restore (including imported deleted posting flags), metadata-only precision preservation, valuations, historical rates, replay and version conflicts. Real SDK client tests cover initialization, all tools, reads/writes, schema validation, duplicate requests, stale versions and missing/incorrect authentication.
- The original MVP browser acceptance covered reports, deletion/restoration, valuations and a 390px viewport. After refactoring, dashboard, account history/pagination, counterparty search, creation and amount-only editing were checked live. The Mac then locked with a Chrome native confirmation open, blocking the remaining live checks. Native confirmation has since been replaced by a tested in-app dialog; final mobile/report/valuation visual checks remain outstanding.
- The UI uses native table scrolling on narrow screens. Angular build reports a pre-existing stylesheet budget warning for Day View; the build succeeds.
- Openings/reconciliation entries are imported and included in balances. There is no dedicated manual opening-balance wizard.
- The source contains eight very small legacy posting imbalances, retained exactly, plus one already-deleted journal with an incompatible historical currency. It cannot be restored into an account of a different currency. Details and source IDs are in the private import report.
- Manual-rate replacement is serialized, audited and revision-checked. A stale edit returns a conflict and must be reloaded before saving.
- Background bank sync, splits editor, budgets/subscriptions/tags UI, export, positions and return-percentage calculations are not included. Production access is configured separately as described in the production runbook.

## Final local acceptance, 2026-09-26

- Full source import: **2,156 accounts, 41 categories, 7,461 journals, 14,922 postings**; **5,879 active** journals. Deleted data remains preserved.
- **10 historical balance checkpoints**, zero amount mismatches and zero relationship/currency/status mismatches.
- Independent source SQL vs. REST: **13 category groups, 8 monthly groups, 6 investment/property account reconciliations**, all exact.
- Final `mvn clean install`: **155 tests, 0 failures, 0 errors**. Frontend: **162 tests in 26 files**, all passing; production build succeeds.
- The original MVP demo was reimported after testing. The refactor preserved that database, including fetched ECB rates. Its one disposable browser transaction, postings, audit entries and replay responses were removed specifically; source reconciliation was repeated afterwards. A separate `thereabout_finance_rehearsal` schema verified a complete fresh import without replacing the demo.
- Private evidence: `refactor-fresh-import.json`, `refactor-verification.json`, `import-report.json` and `report-reconciliation.json` in the support directory above. Runtime logs are `backend.log` and `frontend.log` there.

## Refactored structure

See [the architecture notes](finances-architecture.md) for the persistence boundary, transactions, contracts and UI structure. Historical request keys remain reserved; replaying a request written by the old generic API is rejected rather than replaying a differently shaped response. Use a fresh key for a new operation.

Before this refactor, a full private database and source snapshot was saved in `~/Library/Application Support/thereabout-finances/refactor-baseline-20260926-162041/`. `thereabout.sql` is the complete pre-refactor local database; `finance-code.tgz` is the matching code snapshot. Stop the local backend before restoring both together. For finance-only recovery, use the approved Firefly import command above instead; that intentionally replaces local finance edits and rates.
