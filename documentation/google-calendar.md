# Google Calendar

Thereabout imports selected calendars into MariaDB. Google push notifications trigger incremental synchronization, and a daily check recovers missed notifications. Day View reads only the local database.

## Setup

1. Enable the **Google Calendar API** in your Google Cloud project.
2. Obtain an OAuth client ID, client secret and refresh token for the same OAuth application. The refresh token must grant `https://www.googleapis.com/auth/calendar.calendarlist.readonly` and `https://www.googleapis.com/auth/calendar.events`, or the broader `https://www.googleapis.com/auth/calendar` scope. Thereabout does not implement an OAuth consent flow. A Google app left in external Testing mode may issue short-lived refresh tokens; configure its publishing status appropriately for ongoing personal use.
3. Save these values under **Configuration → Secrets → Google**. Values are stored in the database, independently of environment variables. They apply immediately. Focusing a field reveals its saved value; leaving it masks it again. Blank required values pause sync. Secret responses use `Cache-Control: no-store`; keep database backups protected as they contain credentials.
4. For automatic updates, configure an HTTPS callback URL ending in `/backend/api/v1/calendar/google/notifications`. For the existing hosted instance the URL is `https://thereabout.w1nter.com/backend/api/v1/calendar/google/notifications`.
5. Route that exact path to the backend (port 9050) and allow Google POSTs through any external login challenge. Use a valid publicly trusted TLS certificate. Preserve Google’s `X-Goog-*` headers, allow empty request bodies, and do not cache the route. Do not log the channel token header.
6. Click **Full import**, select the calendars, and confirm. A public URL is optional for manual imports and **Sync now**, including local testing. Without it, webhook status is **DISABLED** and no daily checks or watch registrations run. With a URL configured, wait for import completion and **HEALTHY** webhook status. **AWAITING_CALLBACK** means no verified callback has arrived yet; after five minutes this becomes **DEGRADED**. Watch registration alone does not prove delivery.

For Cloudflare Access, use an application/path exception covering **only** the notification URL. Keep Secrets, calendar management, Day View, and other API paths behind the existing authentication. The callback authenticates channel ID, random channel token, Google resource ID, and channel lifecycle. A public route without these matching values cannot enqueue synchronization. No reverse-proxy configuration is stored in this repository; apply the path exception to the actual hosting configuration before connecting.

## Behaviour

- Full import has no historical cutoff. Recurring masters and exceptions are stored; iCal4j expands occurrences for the requested day, including moved/cancelled occurrences and daylight-saving transitions.
- Full imports publish a new snapshot only after all pages complete. Interrupted imports keep the preceding snapshot; the next attempt restarts staging.
- A server-side worker checks **local database work** every ten seconds. It contacts Google only for queued changes, channel registration/renewal, recovery, or user actions. It does not poll Google every ten seconds.
- Events watches are registered for each selected calendar, plus one CalendarList watch. Channels request seven days and are replaced before their actual expiry. Initial callbacks may arrive before watch responses; both orders are supported. Renewal overlap and duplicate notifications are harmless.
- Notifications are persisted before acknowledgment. Changes arriving during synchronization trigger a further pass. API failures use bounded exponential backoff; invalid credentials pause sync.
- When a webhook URL is configured, daily reconciliation occurs roughly every 24 hours, with up to five minutes of jitter. Startup, registration, renewal and **Sync now** also catch up. A dropped notification can therefore leave data stale until one of these triggers.
- The UI refreshes local snapshots while visible. This incurs no Google API calls.
- Deselecting calendars retains their events as **Not syncing**, with local deletion still available. Switching accounts also retains old data and requires a fresh selection.
- Guest email addresses create unlinked Google application identities. Existing manual person links survive imports. Your primary account gets its own identity, even when absent from guest lists.
- Deletion is local to Thereabout and never modifies Google Calendar. One click hides the event immediately after successful storage and shows a success toast, without a confirmation step. It works offline and for read-only, deselected, or previous-account calendars. Separate durable deletion markers survive incremental syncs, full imports, and restarts. Recurring deletions hide only the selected occurrence, including if Google later moves it. Copies in other calendars remain visible. There is no restore UI or series-wide deletion.
- Thereabout supports one active Google account and one backend process. Running multiple backend replicas against this database is not supported by the in-process synchronization lock.

## Verification

Normal tests mock Google and do not need credentials or an HTTPS tunnel. The worker can be disabled for a fixture-only preview with `--thereabout.calendar.worker-enabled=false`.

For live verification, use a dedicated Google test calendar and an explicitly configured HTTPS tunnel when running locally. Import it, verify the initial callback, then create, move, and cancel disposable events in Google. Confirm corresponding day-view changes and identity creation. Delete a disposable recurring occurrence in Thereabout and verify that it remains in Google, stays hidden locally after Sync now and a full import, and adjacent occurrences remain visible in Thereabout. Exercise renewal and a restart; confirm channel health and catch-up. Do not use real appointments for destructive smoke tests.

Run backend checks from `backend/` (`mvn clean install`) and frontend checks from `frontend/` (`npm run openapi:generate`, `npm test -- --watch=false`, `npm run build`) using Node 24.

**Test database isolation:** historical migration V5 explicitly references the `thereabout` schema. Do not replay the entire historical migration chain against an alternate schema on a server containing real Thereabout data. For an existing installation, create an empty test database from a schema-only dump of V16 (excluding Flyway history), baseline that test database at V16, then apply V17 and subsequent migrations. Run checks with `-Dspring.datasource.url=jdbc:mariadb://localhost:3306/thereabout_calendar_test`. Alternatively use a disposable MariaDB server with its own `thereabout` database. The calendar migration itself uses no hard-coded schema names.

References: [Google push notifications](https://developers.google.com/workspace/calendar/api/guides/push), [incremental synchronization](https://developers.google.com/workspace/calendar/api/guides/sync).
