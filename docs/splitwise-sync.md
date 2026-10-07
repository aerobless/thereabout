# Splitwise synchronization

Splitwise remains the expense editor. An administrator configures one connection and group in Configuration → Splitwise. Only read requests are sent to Splitwise. Automatic synchronization is disabled until the first import is confirmed and the administrator saves the enabled setting.

## Configuration and first import

1. Save the API key and test the connection. Responses disclose only whether credentials exist, never the key. Replacing or removing a key disables automatic synchronization until access has been tested again.
2. Select a group. Map each imported member to an existing active CASH account and a separate CASH bank account with the same owner and currency. Leave other members unmapped. The account owner determines the Thereabout user. The proposed start date follows the most recent active, non-future transaction on the virtual account; confirm the date or choose Full history.
3. Open **Category mapping** to map Splitwise subcategories to finance categories. Save or cancel the modal independently of other settings; dismissal discards its draft. Uncategorized is an explicit saved mapping. Unique exact names can be proposed, but nothing is imported before mappings are saved. Missing or deleted mappings defer the affected entries.
4. Request the first-import preview. Inspect new, adopted, preserved, deleted, future and pending entries, plus balances at a common timestamp. Pending entries remain unimported. Projected balances exclude unresolved entries and optional starting-balance corrections; they may differ substantially from Splitwise until settlements are resolved.
5. Inspect **Needs review** in its read-only modal. Resolve cases through MCP or consciously defer them, then confirm a fresh first-import preview. Changing local transactions after a preview invalidates its confirmation. Initial group, account and start-date mappings are fixed after confirmation; category mappings and automatic synchronization remain editable.
6. Enable automatic synchronization only after reviewing the local rehearsal and first import. Sync now queues the same serialized worker. The browser can inspect progress while it runs.

A separate optional starting correction uses RECONCILIATION and an idempotent request key. Confirm that the account's historical balance covers only the selected group's intended history. Ambiguous referenced legacy entries must be reviewed before a correction is offered. There are no subsequent automatic balance corrections.

## Ledger rules

Each source expense has one durable record per mapped member, keyed by expense ID and member ID. The virtual posting is `paid_share - owed_share`, with exact decimal arithmetic. Negative shares are withdrawals. Positive shares are deposits with EXPENSE_REIMBURSEMENT: they reduce category/monthly expenses without adding income. Asset balances continue to use actual postings. Shared technical expense/reimbursement counterparties are created as needed.

The bootstrap cutoff affects new historical entries only. Exact preserved references in externalReference, notes and Firefly metadata can adopt matching active transactions without changing them. Deleted or mismatched references are preserved or deferred. Anonymous older history is registered as ignored; similar descriptions/amounts never establish identity. A new backdated source entry remains importable after initialization.

Source edits update only automatically managed transactions. Source deletion, movement outside the configured group and a zero share reversibly remove managed transactions while retaining source identity. Source restoration or a later nonzero share can restore them. Real local edits, categorization, deletion or restoration through REST or MCP permanently detach that member's transaction. No-op saves advance the tracked version without detaching. The other person's transaction continues independently. Subsequent source changes are recorded as source-observation audits while the applied fingerprint remains intact.

All projections of one expense commit inside the shared finance write boundary with audit and optimistic versions. Pending source states survive cursor advancement and are retried from their persisted payloads.

## Settlements and duplicates

Splitwise payment flags, adopted transfer references and explicit review classifications identify settlements. The API's repayments list also appears on ordinary expenses and is not a settlement flag. Historical settlement-like descriptions are surfaced for explicit Expense/Settlement classification; classifications persist by source identity.

A matching existing bank↔virtual transfer can be adopted. Otherwise MCP lets the agent link a bank transaction with the matching date/currency/opposite amount, explicitly create a transfer on the configured default bank account, or skip permanently. Unmatched settlements remain pending; no new fallback or automatic settlement creation is introduced. The `LINK` action prepares replacement of the bank booking with the paired transfer on the next explicit sync; it does not add a second bank movement. Later account corrections use ordinary transaction tools and preserve the Splitwise link while permanently ending automatic updates for that transaction. A transaction already linked to another source cannot be reused. CSV import duplicate review recognizes the resulting bank posting; unresolved duplicate warnings are still not automatically accepted.

## MCP reconciliation

The authenticated finance MCP endpoint exposes six Splitwise tools:

| Tool | Behaviour |
| --- | --- |
| `finance_splitwise_get` | Safe settings, mappings, cached catalog and current sync status; no credentials or remote reads. |
| `finance_splitwise_sources_list` | Paginated source history, including ACTIVE, PENDING, IGNORED, SOURCE_DELETED, ZERO, LOCAL and UNAVAILABLE. Filters: expenseId, memberId, state, description query and Zurich calendar date range. |
| `finance_splitwise_sources_get` | One expense/member projection with paid/owed shares, timestamps, source version, linked/candidate transaction ID and current transaction version. |
| `finance_splitwise_resolve` | AS_EXPENSE, AS_SETTLEMENT, CREATE, LINK, SKIP or LINK_EXISTING. Requires requestKey/sourceVersion; link actions additionally require transactionVersion. Returns updated evidence without starting sync. |
| `finance_splitwise_categories_save` | Replace the complete mapping using settings revision and requestKey. Omitted source IDs are unmapped; a mapping with null/omitted categoryId explicitly means Uncategorized. Other settings are preserved. |
| `finance_splitwise_sync` | Queue the existing worker; repeated requests coalesce. Poll `finance_splitwise_get` for completion. |

Use existing account/transaction searches and report tools to inspect both source evidence and local finances. Propose corrections and apply only explicitly authorized financial changes through ordinary transaction tools. Then resolve classifications, create approvals or links, request one sync and verify account balances and review counts. Decisions and category saves are versioned, audited and idempotent; review decisions are rejected while synchronization is running. Before initialization, either operation invalidates an existing preview. Mapping updates do not recategorize previously imported transactions.

`LINK_EXISTING` attaches an already prepared local transaction to its expense/member source. It requires the mapped virtual-account posting, matching signed amount/currency and current versions, but deliberately permits different legacy dates and deleted local entries. Ignored historical sources can also be linked. It preserves transaction contents, external references and deletion status and marks the source permanently locally managed. Conflicting source links are rejected. This is provenance only: it neither creates a posting nor resumes automatic management. The other member remains independent. All subsequent source edits are observed without overwriting this transaction.

The review modal contains evidence and links only; category mapping remains editable by the administrator. Browser settings and MCP share the same validation/services. Credentials and connection replacement remain outside these MCP tools.

## Polling and failures

The JDK HTTP client calls current-user, groups, categories and expense read endpoints. Every five minutes the worker requests `updated_after` with a ten-minute overlap and `updated_before` fixed at the start of the run, reading all pages before applying and advancing the cursor. A daily full reconciliation explicitly fetches missing known IDs. An unavailable ID creates a review case and preserves local postings; absence from a list never implies deletion.

The run state, snapshot, cursor and source links are persisted. Interrupted initialization or synchronization replays safely. Automatic and manual runs coalesce behind one worker in the application process. Source projections and local changes share the database finance lock. This deployment expects one backend worker instance.

HTTP 429 honors Retry-After. Other transient failures use persisted exponential backoff and jitter; the worker does not sleep while holding the finance lock. Authentication failures require a successful connection test before resuming. There is no documented webhook or fixed numeric public rate limit in the [Splitwise API documentation](https://dev.splitwise.com/).

## Verification

Normal tests use the isolated database on port 3307, never development or production:

```sh
docker compose -f docker-compose-test.yaml up -d --wait
cd backend && mvn clean install
```

Frontend checks require Node 24:

```sh
cd frontend
npm run openapi:generate
npm test -- --watch=false
npm run build
```

`SplitwiseLedgerTest` covers accounting effects, precision, payer changes, zero/deleted/restored sources, cutoffs, references, local overrides, settlements, future dates, categories, cursors, rate limits and missing source IDs. `SplitwiseClientTest` exercises real local HTTP pagination and rate-limit responses. REST/MCP protocol and authorization tests cover the shared local-management hooks and admin configuration boundary.

`SplitwiseLiveClientTest` is opt-in (`THEREABOUT_SPLITWISE_LIVE=true`, `THEREABOUT_SPLITWISE_KEY_FILE` pointing to the existing secret file). It sends only GET requests, verifies full and incremental reads and never writes the key into test output.

`SplitwiseRehearsalTest` is opt-in (`THEREABOUT_SPLITWISE_REHEARSAL=true`). It requires an independently restored database ending in `_test`, `THEREABOUT_TEST_DB_URL/USER/PASSWORD`, and source JSON fixtures at `THEREABOUT_SPLITWISE_SNAPSHOT` and `THEREABOUT_SPLITWISE_CATALOG`. Optional `THEREABOUT_SPLITWISE_REPORT` stores the local result outside Git. Calendar, Splitwise scheduling, Telegram and external icon fetching are disabled in the test profile. The rehearsal itself uses captured read-only API responses; it never contacts external write endpoints.

The 4 October 2026 rehearsal used the SHA256-verified 3 October backup and 1,023 source expenses (8 deleted). With the proposed existing-history cutoff for one member and full history for the other, it produced 1,036 new transactions, adopted 134 active references, and deferred 18 entries: 10 payments, 5 historical settlement classifications and 3 differing legacy references. All 7,462 original transactions, including posting amounts, dates, metadata, versions and deletion state, were preserved. Every created/adopted amount, date and created category matched the preview; balances matched its projections. An identical second run created zero transactions. A simultaneous manual Sync now request coalesced with that running worker and returned without blocking; a second worker invocation did not fetch another batch. Categories without unique exact matches were explicitly Uncategorized in this disposable rehearsal; these test choices are not production settings. Production synchronization has not been enabled.
