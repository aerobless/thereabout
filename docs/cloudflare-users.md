# Users, roles and Cloudflare Access

People and groups remain existing identities. A person becomes a user through **Create User** on the identity detail page: enter the Cloudflare email and select **User** (default) or **Admin**. `identity.role` is `USER`, `ADMIN`, or null for contacts. Groups have no role. Identity ID and user ID are the same. Email normalization, unique mapping, existing chat links, and idempotent creation are preserved. Email changes, deactivation and user deletion remain unavailable.

Admins may change roles in **Edit Identity**, through `PUT /backend/api/v1/identity/{id}/user`. Generic identity writes cannot set roles or alter Cloudflare mappings. A transaction locks the current admin set before a role change; concurrent demotions cannot remove the last admin. An empty installation still atomically admits exactly one first verified login as its first admin.

## Access

Cloudflare admits requests; Thereabout verifies the signed assertion on every request and resolves the email against eligible user identities. Plain email headers and payload IDs do not authenticate users. Configure `THEREABOUT_ACCESS_MODE=cloudflare`, `THEREABOUT_CLOUDFLARE_ISSUER` and `THEREABOUT_CLOUDFLARE_AUDIENCE` in production. Keep the origin behind Cloudflare. Invalid or incomplete Cloudflare configuration fails startup.

Configuration management and all Identity APIs/pages are admin-only. Navigation hides them for users; route guards and backend authorization also reject direct access. The shared browser bootstrap at `GET /backend/api/v1/config` is available to every authenticated user so maps can load on a first visit; it contains only the Google Maps browser key and version information. Management and secret endpoints still require Admin. Authenticated users without the required role receive 403. Personal object IDs outside their data context receive 404. Messages, people and communication mappings remain global; personal message separation is outside this release.

Browser writes require the `XSRF-TOKEN` cookie and `X-XSRF-TOKEN` header. Static frontend assets and `/actuator/health` contain no personal data. Unlinked verified logins can load those assets and the non-cacheable `current-user` endpoint, but cannot load application data.

`GET /backend/api/v1/current-user` reports effective `identityId`, display name and role, authenticated `actorIdentityId`, actor name and role, and `impersonating`. Recognition statuses remain `resolved`, `unlinked`, `missing_token`, `invalid_token`, `verification_unavailable`, and `disabled`. Local mode resolves user 1 when eligible, with loopback administrator privileges; without a valid user it permits administration but rejects personal data requests.

## Impersonation

**Identities → user → Impersonate** works in the production bundle. The Actions card offers this button to an authenticated admin for person identities with either the User or Admin role. Browser requests send `X-Thereabout-Impersonate-User`; the server checks the real actor's current admin role and the target's eligibility on every request, then applies the target's data and permissions. Impersonating a normal user hides and blocks Configuration and Identities. The persistent banner's exit button restores the authenticated actor.

Selection stays only in the current tab's memory. A full reload ends impersonation. Context changes cancel pending browser requests and recreate route components, discarding editors and personal data. Private launcher/bank images use Angular HTTP blob requests with the same header and a user-scoped cache. Invalid targets or revoked actor privileges reject further requests; frontend verification failure clears the selection and resolves the actor again.

## Shared integrations

- Health and location ingestion use their existing ingestion key and always write as user 1. File imports also capture user 1 before starting their asynchronous job. No import owner can be selected through browser payloads.
- Bypass only `/backend/api/v1/ingest` at Cloudflare. Health/location uploads require the application ingestion key; Google notifications verify their channel token. Ordinary reads never accept the ingestion key.
- Finance MCP requires a valid Cloudflare assertion and MCP key. All mapped users and existing machine clients are eligible. It always operates as user 1, independently of browser impersonation, through the same business services as REST. The servlet captures the verified actor for audits on SDK worker threads.
- MCP credentials, Google secrets and Telegram configuration remain global and admin-managed. General frontend bootstrap returns only Maps/bootstrap information and version metadata. The ingestion credential is served separately at `/backend/api/v1/config/ingestion-key` with `Cache-Control: no-store`.

See [personal data and release checks](user-data.md) for ownership, financial reports, calendar sharing and migration requirements.
