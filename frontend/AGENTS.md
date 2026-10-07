# Frontend Agents Guide

These instructions extend the repository's root `agents.md` for work in `frontend/`.

## Reuse the existing design

- Follow Thereabout's existing typography, colours, spacing and interaction patterns. Inspect an equivalent screen before introducing a new pattern; Day View is a reference for date selection.
- Use the existing PrimeNG controls and shared wrappers for selects, date pickers, dialogs and notifications. Do not introduce native select/date controls with a different popup appearance. In Finances, reuse `finance-date-input` and the shared finance control styles.
- Extract repeated controls and styles into shared components or stylesheets instead of copying page-specific overrides. Avoid custom class names that collide with PrimeFlex utilities, such as `inline`.
- Use the existing global toast mechanism for successful saves and other transient confirmations. Do not add inline "Saved" banners that shift the page or remain after navigation. Keep actionable form errors near the affected fields.
- Keep UI text English and preserve user-provided account and category names. Remove redundant explanatory copy, implementation details and actions that are unrelated to the current view; retain warnings that affect interpretation or correctness.

## Filters, tables and rendering

- Place search and filters in the associated table caption or column header area. Keep date ranges, checkboxes and related controls visually grouped and labelled.
- Prefer existing PrimeNG table functionality when it fits. Server-side search and pagination are compatible with lazy tables; they are not a reason to rebuild the table. If a custom table is justified, explain the concrete trade-off.
- Filter and sort the full result set on the server when data is paginated, not just the loaded page. Preserve server totals and reset pagination when filters change. Small collections such as own accounts should not have unnecessary pagination; counterparties and transaction histories should remain paginated.
- Refresh reports automatically when a valid filter value actually changes. Do not require an Apply button for simple date/account filters. Advanced transaction date rules use PrimeNG Match All/Any with explicit Apply/Clear; incomplete rules cannot be applied. Debounce typed searches, ignore incomplete or invalid date ranges, and prevent stale responses from replacing newer results.
- Keep chart data and options stable across unrelated change detection. Derive them with computed signals or equivalent memoization rather than constructing new objects from template-called methods. Focus, blur and unchanged filter values must not reload or redraw charts. Disabling animation is not a substitute for fixing unstable inputs.
- Use searchable autocomplete for large entity choices such as counterparties. Selecting an existing result must retain its identity; changing the text must not silently retain an unrelated selected ID. Offer an explicit create-new choice when appropriate, without a separate Search button.

## Layout and responsive behaviour

- Use `AppModalComponent` (`app-modal`) for all modal dialogs, including confirmations. It owns the blurred backdrop, backdrop/Escape dismissal, focus trap, scroll locking and mobile fullscreen layout. Supply content, an optional `#footer`, title and desktop size; use `dismissible=false` only while an operation or nested editor prevents closing. Do not introduce direct `p-dialog`/`p-confirmdialog` instances or duplicate modal chrome.
- Give modal content space below the header and between inputs and buttons. Size dialogs for the normal amount of content, using columns where helpful, while keeping them usable within narrow and short viewports. Avoid unnecessary nested scrolling.
- Use compact account cards with the logo beside the name, value and details. On account details, show the identity card before the balance card, with a clearly separated back link.
- Reuse the shared website-icon/account-logo mechanism for configurable bank websites, including a graceful fallback. Do not hardcode bank-specific icon URLs in page templates.
- Design the mobile composition deliberately rather than stacking every element automatically. The launcher's three day-glance items should remain compact and side by side; separators must not become orphaned lines after wrapping.
- Keep Recent activity focused on recent transactions; category and exchange-rate management belong in Configuration → Finances. Do not reintroduce the removed "booked values, not a forecast" copy or "Exchange rates used" disclosure panels. Preserve missing-rate warnings and access to exchange-rate management.

- Use PrimeNG `severity="danger"` or the shared `.app-button.danger` for destructive actions and their final confirmations (Delete, Remove, Disconnect). Restore and Cancel use normal primary/secondary styling. Never add feature-specific red button CSS.

## Money, dates and secrets

- Show whole currency values in the Finances overview's top summary cards. Detailed monetary values and ordinary money inputs should use two decimal places for the current fiat-currency flows, respecting currency precision where applicable. Do not expose SQL padding such as `25.900000000000000000` in edit fields.
- Formatting is not a data correction. Preserve exact decimal strings and untouched source values when submitting edits; never round stored amounts merely to improve their display. Do not use JavaScript floating-point arithmetic to construct exact monetary API payloads.
- Preserve calendar dates through date-picker adapters without introducing UTC timezone shifts. Reuse the shared adapter instead of implementing date conversion separately on each page.
- Show the MCP credential masked in Configuration, reveal it only through the protected settings request on explicit interaction, and clear the revealed value on blur or dismissal. Never include it in general frontend configuration, bundles, logs or screenshots.

## Verification

- Use Node 24 and the existing scripts from `frontend/`: `npm test -- --watch=false` and `npm run build`. Use `npm run openapi:generate` when API contracts change; never hand-edit generated clients.
- For visual changes, check the relevant desktop and narrow mobile views in the browser, including opened dropdowns/date pickers, dialog spacing, bank-icon loading and save toasts. A successful build does not establish that these interactions look or behave correctly.
- For behavioural bugs, verify the triggering interaction and add a focused regression test where useful. In particular, check chart stability on focus/blur and repeated identical filter values; a screenshot alone cannot establish this.
- When screenshots are requested for remote review, provide representative screenshots of the changed flows. Keep private data, credentials and review captures out of source control.

## Angular conventions

- For new or substantially revised code, use standalone components, signals for local state, `computed` for derived state, `input()`/`output()`, native control flow and `host` bindings. Use OnPush together with reactive state; do not switch change detection without adapting asynchronous updates.
- Angular runs zoneless: async UI changes (HTTP, polling, promises; success, error and cleanup) must notify through template-read signals/`AsyncPipe`, or `markForCheck()` for existing mutable state; `Eager` alone is insufficient. Test delayed responses with `whenStable()`, without an extra click or manual `detectChanges()` after the response.
- Preserve strict typing. Prefer inference and `unknown` with narrowing; do not hide type errors with `any` or unchecked casts.
- Keep templates simple and feature routes lazy. Avoid unrelated syntax-only rewrites.
- Do not add `::ng-deep` or other deep combinators. Prefer PrimeNG configuration, styles owned by the component or narrowly scoped shared styles. Replace existing deep selectors when substantially changing that area.
