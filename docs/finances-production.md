# Finances in production

Finance endpoints are always registered. Local development uses the single `development` profile. Production uses Cloudflare Access JWT validation in addition to the existing edge policy, for the whole application (see [Cloudflare users](cloudflare-users.md)). Finance and MCP require the finance application's audience; direct requests to the origin without a valid signed token are rejected. Finance additionally requires a Thereabout user, and browser writes require the CSRF token. MCP additionally requires its own bearer key.

The application optionally loads `/data/finances.properties` from its persistent volume. Create it with mode 0600, accessible only to the application and administrator:

```properties
thereabout.finances.access-mode=cloudflare
thereabout.finances.access-issuer=https://your-team.cloudflareaccess.com
thereabout.finances.access-audience=YOUR_ACCESS_APPLICATION_AUDIENCE
```

Use the Access application's audience, not a service-token ID. The backend fetches and caches the team's signing keys and validates signature, issuer, audience, expiration and not-before. Cross-site browser writes are rejected by the CSRF token check. `thereabout.finances.public-origin` is no longer an access check; it remains the fallback for `thereabout.public-origin`, which fixes the Google Calendar callback URL. Unknown access modes fail closed. Keep this file out of Git. The MCP credential is stored in the existing `configuration` table under `FINANCE_MCP_KEY`, not in this properties file. Do not enable the local profile on the server.

For MCP, authenticate through Cloudflare Access and supply the bearer key to `/mcp/finances`. No agent integration or Cloudflare policy changes are performed by the application.

## MCP credential lifecycle

At application startup, a missing MCP key is generated using 32 cryptographically random bytes and persisted as a URL-safe string. An atomic insert preserves the first key even when several instances start concurrently. Existing keys are never regenerated on restart. Database backups therefore include the credential and must remain private.

**Configuration → Finances MCP** shows a masked, read-only field. Focusing it fetches the key through `GET /api/finances/configuration/mcp-key`; blur or Escape clears it. This endpoint requires the same validated Cloudflare Access identity and Thereabout user as the finance UI, sends `Cache-Control: no-store`, and is not part of the public frontend configuration or MCP tool list. The key must never be logged.

When upgrading an installation that previously used `thereabout.finances.mcp-key`, stop the old application and copy that exact credential privately into `configuration` as `config_key = 'FINANCE_MCP_KEY'` before starting the new version. Insert only if absent; never overwrite an existing database key. Remove the obsolete property after verifying MCP access. Without this one-time migration the new application generates a different key and clients must update their credential from Configuration. The server no longer reads key properties, environment variables, or files.

## First import and rollback

1. Save a consistent full production database dump and the current container/image metadata privately. Verify the compressed dump and retain the previous image for rollback.
2. Restore the approved Firefly archive into the local source database using `restore_source.py`. Import into a separate local rehearsal database migrated to the same Flyway version, and run the independent reconciliation in `import_firefly.py`.
3. Deploy the tested application through CI. Flyway adds the finance tables. Stop the application for the initial import; check that the production finance tables are empty.
4. Transfer a checksummed, data-only SQL dump of the reconciled finance tables. Load those tables only, in one transaction. Never restore the entire local Thereabout database over production. No Firefly authentication/session data is included in the finance archive.
5. Read back the imported finance tables and repeat the source reconciliation. Restore availability and verify authenticated dashboard, reports, pagination, denied unauthenticated origin access and MCP authentication.
6. Fetch ECB reference rates for valuation, preserving original booked amounts. Store reconciliation results and deployment identity privately with the release backup.

For application rollback, retain the additive finance tables and the access configuration, and redeploy the previous image. Removing access settings is not a way to disable the finance module. A full database restore is a separate disaster-recovery operation: stop the application and restore the pre-deployment SQL dump only if necessary, accounting for any new non-finance writes since the backup. Import scripts deliberately remain local-only; production transfer is an administrator operation over SSH.

Security references: [Cloudflare JWT validation](https://developers.cloudflare.com/cloudflare-one/access-controls/applications/http-apps/authorization-cookie/validating-json/) and [Spring JWT validation](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html).
