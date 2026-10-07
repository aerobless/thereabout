# Reviewed CSV imports

Configure an OpenAI API key and model in **Configuration → OpenAI**. Only administrators can change these settings. The key is stored in the database and is never returned to the browser, general configuration or MCP. The default model is `gpt-6-luna`; the official Java SDK uses the Responses API with strict structured output and `store: false`. Connection tests use the saved settings.

Choose **Import** in the finance header and select an account, or **Import transactions** on an account detail to preselect it. Every personal-data user can import into any user's active main account. Ownership continues to determine reporting.

The parser handles common delimiters, quoted multiline fields, BOMs, UTF-8, UTF-16 and Windows-1252. Files have no bank-specific adapter. One CSV can contain up to 2 MiB and 2,000 data rows, with at most 20 additional header/preamble records. Two workers process chunks of at most 50 records; each caller may have one running job. Larger input is rejected rather than truncated.

Headers, preambles and statement totals are discarded before review; skipped transactions remain visible. The review shows immutable source rows, proposed descriptions, dates, exact decimal amounts, counterparties, categories, skips, duplicate warnings and predicted balance. Edit each unresolved row or explicitly skip it. The model receives all existing categories and eligible accounts with their IDs. Imports can only select an existing category or remain uncategorised; categories cannot be created by REST or MCP approval. Uniquely matching counterparty names resolve to existing accounts before preview, including case, punctuation and umlaut spelling differences. Named new counterparties are marked and created only when approval commits. Prefer existing entities; no holdings or additional currency accounts are created.

Transfers use a named direction relative to the selected account. For matching currencies, the booked amount is used on both sides; for different currencies, the second account's booked amount is required and preserved exactly.

Book in the selected account's currency. Original currency/amount remain informational. Missing booked amounts require manual entry or skipping; exchange rates are never invented. Internal FX exchanges and securities buys/sells are skipped. Separate fees, dividends, interest and external funding remain eligible. No automatic opening balances, valuations or balance corrections are created.

Jobs and review state exist only in process memory. Open dialogs renew their drafts automatically; abandoned drafts expire after 60 minutes without activity; cancellation, expiry and server restart discard them. Approval requires the current revision and a unique request key. The finance write lock serializes approval and rechecks duplicates. All transactions, paired postings, new entities, provenance and audit records commit atomically. Repeating an approved request returns its persistent receipt, even after the draft expires or the server restarts. A different payload requires a new request key. Explicit duplicate overrides are recorded in provenance and audit history.

Exact source decimals are preserved up to the ledger's 12 integer and 24 fractional digits. Ordinary transaction editor precision rules remain unchanged. Clear source/ledger matches are skipped initially, while possible duplicates require an explicit decision. Matching occurrence counts preserve repeated payments in a statement.

## MCP

Use the same authenticated `/mcp/finances` server:

- `finance_imports_prepare`: `{accountId, fileName, csvText}` starts a job; no ledger writes. CSV is sent to OpenAI and consumes API credits.
- `finance_imports_get`: `{jobId, page?, pageSize?}` returns progress and paginated proposals. Page size is at most 200.
- `finance_imports_review`: `{jobId, revision, rows}` updates selected rows by stable `rowId`. Source evidence cannot be replaced. This increments the draft revision.
- `finance_imports_cancel`: `{jobId}` discards the draft.
- `finance_imports_approve`: `{jobId, revision, requestKey, rows?}` approves the reviewed draft. Optional corrected rows are validated within the same atomic approval. Every row must be valid or explicitly skipped. To retain a flagged duplicate, set `duplicateOverride: true`.

Drafts belong to the preparing caller. Account ownership does not restrict the selected target account. The browser and MCP share parsing, interpretation, review, validation, duplicate detection and approval services.

Synthetic fixtures and an in-process fake OpenAI endpoint verify the SDK and accounting boundaries without sending private bank data to a test service. Uploaded real CSVs must stay outside source control.

## Account hints

Select **Import hints** from the **Import transactions** split-button menu on a main account to add or remove reusable sentences such as “Always treat IBKR as a transfer”. Any finance user can manage any main account's hints. Hints are saved immediately, independently of transaction approval; cancelling an import keeps saved hints. Each account can have up to 50 hints of up to 1,000 characters each.

Once the import preview is ready, **Import hints** opens the same hints modal above the preview. Closing it returns to the existing draft and page. Hints are not shown during file selection. Preparation captures all saved hints for that account and supplies the same snapshot to every interpretation chunk. Changes made after preparation apply to future imports and preserve the current draft. Hints guide interpretation but cannot override accounting safeguards, source evidence or the existing category list. Empty optional subjects are omitted from descriptions and notes instead of described as blank or missing.

Editing a row opens a separate modal above the preview, containing its original CSV evidence, warnings and editable fields. The preview stays mounted on its current page. Closing or discarding the edit leaves the draft unchanged; applying it refreshes that page. Dismissal is locked while a review is saving, and a failed review retains the inputs and error in the editor.

MCP exposes `finance_import_hints_list` (`accountId`), `finance_import_hints_add` (`accountId`, `text`, `requestKey`) and `finance_import_hints_remove` (`accountId`, `id`, `version`, `requestKey`). These tools use the same audited, idempotent service as the browser. `finance_imports_prepare` automatically uses saved account hints without additional input.
