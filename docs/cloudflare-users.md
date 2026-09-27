# Cloudflare users

This first slice maps an existing person to a verified Cloudflare Access login and uses their `shortName` in the launcher greeting. It does not filter location, health, finance, calendar, or other domain data by user. Access policies remain managed in Cloudflare.

## Configuration

Configure these Spring properties in the deployment configuration (or the corresponding Spring environment variables):

```properties
thereabout.users.access-mode=cloudflare
thereabout.users.access-issuer=https://YOUR-TEAM.cloudflareaccess.com
thereabout.users.access-audience=YOUR-ACCESS-APPLICATION-AUD
thereabout.users.public-origin=https://YOUR-THEREABOUT-HOST
```

If omitted, each property falls back to its existing `thereabout.finances` equivalent. User recognition works independently of `thereabout.finances.enabled`. With neither configuration, recognition is disabled. Explicit user settings take precedence without changing finance or MCP access rules. The application already imports `/data/finances.properties`; existing installations can place the new properties there, or supply them through their runtime environment. Do not copy placeholder values into a running deployment.

The backend validates the signature, issuer, application audience and lifetime of `Cf-Access-Jwt-Assertion`, then resolves its email to a `CLOUDFLARE` application identity. A plain email header is never sufficient. Tokens are not returned or logged. Public signing keys are fetched through the standard Nimbus decoder; no Cloudflare API key is needed.

For local development, the `development` profile explicitly selects `thereabout.users.access-mode=local`. User creation additionally requires a development/test profile, loopback remote address and host, and an allowed local Origin when supplied. This mode does not impersonate a Cloudflare user: the greeting remains neutral. To test through Cloudflare even with a development profile, explicitly override the user access mode and related properties.

## Create a user

1. Open **Identities** and select an existing person (not a group).
2. Choose **Create User**, enter the email used for Cloudflare Access, and save.
3. The person receives the user flag and a linked Cloudflare application identity atomically. Their existing chat links remain intact.

Emails are trimmed and matched case-insensitively. One email cannot be claimed by two people. Repeating the same operation for the same person/email is harmless. General contact edits cannot remove or alter a Cloudflare user link. Email changes, user deactivation, and roles are outside this slice.

Creating a mapping in Cloudflare mode requires a valid person login but does not require an already mapped Thereabout user, so first-time setup remains possible. No new global access gate is introduced. All people admitted by the existing Cloudflare policy can use this setup operation; this slice does not introduce an administrator role.

## API and greeting

- `POST /backend/api/v1/identity/{id}/user`: `{ "email": "heidi@example.test" }`, returns the updated identity. Invalid input/group: 400; missing login: 401; disallowed origin: 403; missing identity: 404; conflicting email: 409; unavailable verification: 503.
- `GET /backend/api/v1/current-user`: never cached; status plus identity ID and display name only for `AUTHENTICATED`.
- Other statuses: `UNASSIGNED` (verified login without an active person mapping), `NO_IDENTITY` (missing token or person email), `INVALID_IDENTITY`, `NOT_CONFIGURED`, and `UNAVAILABLE` (verification infrastructure unavailable).

The application loads current-user on startup, window focus, and successful user creation. It clears the prior name before resolving again and cancels stale requests. Loading and unresolved states never fall back to Theo. A verified but unlinked login shows **No Thereabout user assigned**.

## Validation and live acceptance

Automated backend tests exercise actual RSA signatures and invalid issuer/audience/expiry, separate infrastructure failures, initial setup, local-only creation, database rollback and simultaneous email claims. Frontend tests cover the dialog, errors, duplicate submission, greeting changes and stale requests. Backend tests must use an isolated database server: historic Flyway migrations explicitly reference the `thereabout` schema, so a different database name on the normal server is insufficient isolation.

After a separately authorized deployment, with a recoverable backup and successful Flyway migration:

1. Confirm the configured Access application passes its signed assertion to the backend.
2. Link Theo and Heidi to their real, distinct Cloudflare emails using Create User.
3. Use two separate real Cloudflare sessions: each launch must show its own name.
4. Switch sessions or sign out and sign in again; reload/refocus must never retain the previous name.
5. A Cloudflare-admitted but unlinked account must receive the neutral greeting and unassigned notice; initial setup must remain reachable.
6. Confirm financial endpoints and MCP retain their existing authentication behaviour.

Local JWT fixtures and component tests do not establish a successful live Cloudflare login or desktop/mobile visual acceptance. Neither production deployment nor real user mappings are performed by these tests.
