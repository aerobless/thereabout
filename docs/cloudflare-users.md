# Thereabout users through Cloudflare Access

An existing person identity becomes a user through **Create User**, available in the Actions card on the identity detail page. Enter the email used to sign in through Cloudflare Access. The server trims and lowercases it, validates it, and atomically stores `identity.is_user` and a `CLOUDFLARE` application identity. The person's stable identity ID and `shortName` remain the source of identity and display name. Existing chat links are retained. Groups cannot become users.

One email per person is supported in this slice. Repeating the same request is harmless. Conflicting requests return HTTP 409 without a partial user flag or link. Generic identity edits preserve the flag and mapping, including stale payloads. Generic link/unlink cannot modify Cloudflare links; users cannot become groups or be deleted through generic identity deletion. Email changes and deactivation are not provided.

## Access model

Cloudflare Access decides who may reach Thereabout; Thereabout verifies the signed assertion on every request and decides what that login may use. There is no separate Thereabout login and no session.

- **Users** (a verified email mapped to a person user) can use the application and finance. **Administrators** can additionally create users. Existing users became administrators when roles were introduced.
- **First login:** while no users exist, the first verified login becomes the administrator `Admin`, linked to its email. Rename it through Edit Identity. Concurrent first logins produce exactly one administrator.
- **Unlinked logins** are authenticated but can only load the frontend and `current-user`, which reports **No Thereabout user assigned.** An administrator maps them with Create User.
- **CSRF:** the Access cookie makes browser requests carry the login implicitly, so writes require the `XSRF-TOKEN` cookie value in the `X-XSRF-TOKEN` header. Angular's HTTP client sends it for same-origin requests.
- **Clients outside Access** use only their own credentials under `/backend/api/v1/ingest`: location and health uploads (`POST /backend/api/v1/ingest/location/geojson`, `POST /backend/api/v1/ingest/health`) need the ingestion API key, and the Google Calendar callback (`POST /backend/api/v1/ingest/calendar/google/notifications`) is authenticated by its channel token. Give `/backend/api/v1/ingest` a Cloudflare Access Bypass policy; nothing else is served there, and every other path must stay behind Access.
- **MCP** still requires both an admitted Access login (for example a service token) with the finance audience and its own bearer key.
- Static frontend files and `/actuator/health` need no login; they hold no personal data.

Set `thereabout.access.mode=cloudflare` in production. When it is absent, `thereabout.finances.access-mode` applies, then `local`. Cloudflare mode without issuer/audience settings, or an unknown mode, fails startup. Local mode admits only requests from and addressed to the loopback interface, which act as the local administrator. Keep the origin itself unreachable except through Cloudflare (for example `cloudflared` on an internal Docker network) as a second line of defence.

Domain tables are shared between users; ownership filtering is not implemented.

## Minimum configuration

Recognition starts automatically when Cloudflare issuer/audience settings are present. Configure a separate Access application only if needed:

```properties
thereabout.users.access-issuer=https://your-team.cloudflareaccess.com
thereabout.users.access-audience=YOUR_ACCESS_APPLICATION_AUDIENCE
```

Use the Access application audience for the browser application and backend path, not a service-token identifier. Set these through the normal Spring environment (`THEREABOUT_USERS_ACCESS_ISSUER`, `THEREABOUT_USERS_ACCESS_AUDIENCE`) or the existing optional `/data/finances.properties` import. No new file import is required. Do not put real mappings, credentials, JWTs, or private deployment configuration in source control.

For compatibility, when the user issuer/audience properties are absent they fall back individually to `thereabout.finances.access-issuer` and `thereabout.finances.access-audience`. This only works if that existing Access application also covers the user endpoint. No enable flag is required. Finance access-mode/public-origin settings retain their existing meaning; finance and MCP protections remain in place. The two JWT decoder beans have explicit qualifiers, so simultaneous enablement does not create ambiguous injection. If any issuer/audience setting is supplied, incomplete or invalid settings fail startup rather than assigning a fallback user. With no Access settings, local startup needs no Cloudflare credentials.

Only `Cf-Access-Jwt-Assertion` is used. Signature, issuer, audience, expiration (required), and not-before are validated before reading email. Plain email headers and client-supplied person IDs do not authenticate anyone. Shared verification uses Nimbus and the Cloudflare signing-key endpoint, following [Cloudflare's JWT validation documentation](https://developers.cloudflare.com/cloudflare-one/access-controls/applications/http-apps/authorization-cookie/validating-json/). Tokens are neither logged nor returned by this flow.

`GET /backend/api/v1/current-user` always sends `Cache-Control: no-store` and returns an explicit status:

| Status | Meaning |
| --- | --- |
| `resolved` | Verified email linked to a person user; includes `identityId` and `displayName` (`shortName`). |
| `unlinked` | Verified login email has no eligible Thereabout user. |
| `missing_token` | Recognition enabled, assertion absent. |
| `invalid_token` | Invalid assertion, claims, signature, lifetime, or email. |
| `verification_unavailable` | Technical JWT verification/key retrieval failure. |
| `disabled` | No Cloudflare recognition settings configured. |

Non-resolved states never contain a resolved identity or name. The frontend uses a generic time-based greeting while loading, clears any previous identity on reload, and displays **No Thereabout user assigned.** for `unlinked`. Technical/HTTP failures show a neutral verification-unavailable state. The in-memory user is cleared when hiding/leaving the page and refreshed when returning, including browser back/forward-cache restoration. Nothing is stored in local/session storage. Production has no impersonation or default Theo fallback. Local development has the explicit preview described below.

## Local development and verification

Keep development servers bound to loopback. Leave Cloudflare settings unset for ordinary local UI work; the greeting stays generic and initial mapping remains possible.

In **Configuration → Local impersonation**, select an existing person with `isUser=true` and choose **Start impersonation**. A fixed banner at the top centre shows the selected name; its **×** button ends simulation and re-fetches the verified current user. Selection survives SPA navigation and tab visibility changes, but a full reload ends it. It is kept only in memory in the current tab.

This preview is available only when Angular `isDevMode()` is true **and** the browser hostname is `localhost`, `127.0.0.1` or `[::1]`. The production bundle hides the card even on localhost, and the service rejects attempts to start simulation outside that boundary. No selected identity is sent to the backend, and no authentication header, cookie, mapping or database flag is changed. The backend's verified Cloudflare identity and finance/MCP authentication remain authoritative. Preview changes the frontend's current-user identity and greeting; domain tables remain shared until ownership filtering is implemented.

The identity list now links to each identity's detail page. Its separate **Actions** card contains Create User, Edit Identity and Delete Identity. The shared editor retains chat-link editing and protects Cloudflare links. User deletion remains unavailable. JWT tests generate their own local RSA keys and synthetic claims; browser greeting fixtures are test-only and do not establish a real Cloudflare session.

**Use a disposable MariaDB container, never the normal development or production database.** Existing repository tests can remove data, and legacy migration V5 explicitly names schema `thereabout`. Use that schema name inside a separate container on another loopback port:

```sh
docker run --name thereabout-users-test-db \
  -e MARIADB_DATABASE=thereabout -e MARIADB_USER=users_test \
  -e MARIADB_PASSWORD=users_test_only -e MARIADB_ROOT_PASSWORD=disposable_test_only \
  -p 127.0.0.1:3337:3306 -d mariadb:latest
# Wait for MariaDB readiness, then verify the container and empty target schema:
docker exec thereabout-users-test-db mariadb -uusers_test -pusers_test_only thereabout \
  -e 'SELECT DATABASE(), @@hostname; SHOW TABLES;'
cd backend
SPRING_DATASOURCE_URL=jdbc:mariadb://127.0.0.1:3337/thereabout \
SPRING_DATASOURCE_USERNAME=users_test SPRING_DATASOURCE_PASSWORD=users_test_only \
mvn clean install
```

Do not run the app against this database while running destructive tests. These credentials are disposable examples, not deployment credentials. Stop/remove only the container you created once its test data is no longer needed.

Regenerate contracts through existing workflows; never edit generated files:

```sh
cd backend && mvn generate-sources
# From repository root:
cd frontend
PATH=/opt/homebrew/opt/node@24/bin:$PATH npm ci
PATH=/opt/homebrew/opt/node@24/bin:$PATH npm run openapi:generate
PATH=/opt/homebrew/opt/node@24/bin:$PATH npm test -- --watch=false
PATH=/opt/homebrew/opt/node@24/bin:$PATH npm run build
```

The server output is under `backend/target/generated-sources/openapi`; Angular contracts under `frontend/generated` are ignored by Git and regenerated during normal builds. Migration V25 only adds a non-null false-default flag; it does not rewrite identities or links. Back up the database using the normal release procedure before deployment. Application rollback can retain the additive column and Cloudflare rows; do not use older identity editors against those mappings because older code does not enforce their preservation.

## Live acceptance after a separately authorized deployment

This work does not deploy, change Cloudflare policies, or create real mappings. Local JWT and browser tests are not proof of live Access behavior.

1. Deploy the tested build separately. Configure recognition with the correct team issuer and browser application's audience. Keep finance/MCP configuration intact. Confirm the edge forwards the signed assertion to `/backend/api/v1/current-user`; never copy tokens into reports or logs.
2. In a clean browser profile sign in through Cloudflare as the first real account. Before mapping, verify a generic greeting and **No Thereabout user assigned.**, with an `unlinked` response and `Cache-Control: no-store`. Confirm identity management is usable.
3. Open that person's existing identity (for example Theo) by clicking the name in the list. In its **Actions** card, choose **Create User**, enter that account's email, and submit. Verify the toast, User status, exactly one Cloudflare link, unchanged identity ID, and retained chat identities. Confirm the launcher now greets that person's `shortName`.
4. In a second clean browser profile sign in with the second real Cloudflare account (for example Heidi). Verify it does not greet Theo before or during loading. Map Heidi's existing identity through its detail page and verify the launcher greets Heidi. Check `/current-user` returns Heidi's stable ID and shortName, distinct from Theo's.
5. Attempt assigning the same email to another person. The dialog must retain the email and show an actionable conflict; the other person must remain a non-user. Verify groups have no Create User action. Edit an unrelated contact/chat identity to confirm the existing workflow remains usable.
6. Repeat the modal and successful detail/list refresh at desktop and narrow phone widths. Verify keyboard focus, Cancel, required email, error visibility, pending/double-click behavior, status and linked email.
7. Sign out/re-authenticate between the two real accounts in one browser, including refresh and back/forward navigation and returning to an already open hidden tab. Confirm loading is generic and no previous-user greeting survives. A missing/invalid assertion must never resolve a user. Exercise key-service unavailability in a controlled non-production environment and verify the distinct technical-failure state.
8. Verify finance same-origin access remains protected, unauthenticated direct-origin finance requests fail, and MCP still requires both Cloudflare verification and its own bearer key. Record the deployed version, both observed names/IDs, cache headers, viewport checks, and finance/MCP results without capturing JWTs.

## Implementation verification (2026-09-27)

- Java 25: `mvn clean install` from `backend/`, with the datasource explicitly pointing to the disposable container on `127.0.0.1:3337`: **191 tests, zero failures/errors/skips**.
- Node 24.18.0: regenerated Angular contracts, **186 frontend tests passed**, production build passed. The build retains the existing Day View stylesheet budget warning (8.50 kB vs 8.00 kB).
- Backend coverage includes normalized email (including pasted Unicode edge whitespace), bad/missing email shapes, groups, missing identities, repeat creation, changed/duplicate email, simultaneous same-person and competing-person requests, actual database unique-index arbitration and loser rollback, stale generic edits, chat-link preservation, and blocked Cloudflare creation/link/unlink via generic routes.
- Generated RSA JWT tests exercise known/unlinked login, ignored plain email header, invalid signature/issuer/audience/expiry, missing expiry/email, disabled recognition, and distinct verification failure. Configuration tests check recognition with finances disabled, simultaneous named decoders, finance-setting fallback, and fail-fast missing configuration; existing finance origin/MCP tests remain green.
- Local Chromium browser checks used the actual API and disposable database for list/detail creation, normalization, conflict retention, toast/status/link refresh, and group exclusion at **1440×1000** and **390×844**. Additional **320×844** and **390×844** modal checks verified focus, Escape, visible actions and no page overflow. Screenshots were visually inspected. The new buttons have explicit accessible names; the identity list uses stacked rows on phones.
- Launcher browser checks used explicitly synthetic `current-user` responses for unlinked, Theo, and Heidi; frontend tests cover loading, old-response cancellation and clearing/restoring state across hidden pages and back/forward-cache navigation. These checks do **not** constitute live Cloudflare acceptance. The local browser preview had no uncaught page errors; it reported the existing missing local PrimeUI license asset/configuration warning.

No production deployment, Cloudflare policy/settings changes, or real-person mappings were performed. Local verification servers and the disposable database were stopped after verification. Existing development services on ports 4200, 9050 and 3306 were left untouched.


## Follow-up verification: actions and local impersonation

The UI follow-up passed 193 frontend tests and the Node 24 production build. Backend code and APIs did not change. Browser verification uses the isolated database and localhost preview, with real API identity editing and user selection. The production bundle was also served locally to verify that the impersonation card/banner are absent even on localhost. Existing Day View stylesheet budget and absent local PrimeUI license warnings remain unrelated to this change.

Desktop (1440px) and narrow (390px) checks confirmed the separate Actions card, no per-row person actions, identity creation/edit/deletion against the disposable database, user selection, the persistent top-centre banner, SPA greeting changes, and restoring the verified state through the close button. The narrow configuration page and banner have no horizontal overflow. The review preview is left running on port 4201 with the isolated backend on 9051 and disposable MariaDB on 3337; normal local services on 4200/9050/3306 are unchanged.

The review backend uses the `development` profile. The complete finance API is always available; no module enable flags are needed. For previews on alternate ports, set `thereabout.finances.local-ui-port=4201` and `server.port=9051`; the local finance origin check permits exactly that loopback UI port and the actual backend port. Defaults remain 4200/9050, and production Cloudflare origin rules are unchanged. Keep the isolated datasource overrides when restarting this preview.
