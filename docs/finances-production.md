# Finances in production

Finance endpoints remain disabled unless explicitly enabled. Local development uses the `finance-local` profile. Production uses Cloudflare Access JWT validation in addition to the existing edge policy; direct requests to the origin without a valid signed application token are rejected. MCP additionally requires its own bearer key. This does not change authentication for other Thereabout modules.

The application optionally loads `/data/finances.properties` from its persistent volume. Create it with mode 0600, accessible only to the application and administrator:

```properties
thereabout.finances.enabled=true
thereabout.finances.access-mode=cloudflare
thereabout.finances.public-origin=https://your-app.example.com
thereabout.finances.access-issuer=https://your-team.cloudflareaccess.com
thereabout.finances.access-audience=YOUR_ACCESS_APPLICATION_AUDIENCE
thereabout.finances.mcp-key=GENERATE_A_RANDOM_KEY_OF_AT_LEAST_32_CHARACTERS
```

Use the Access application's audience, not a service-token ID. The backend fetches and caches the team's signing keys and validates signature, issuer, audience, expiration and not-before. Browser origins must match the configured public origin exactly. Unknown access modes fail closed. Keep this file and its key out of Git, frontend configuration and logs. Do not enable the local profile on the server.

For MCP, authenticate through Cloudflare Access and supply the bearer key to `/mcp/finances`. No agent integration or Cloudflare policy changes are performed by the application.

## First import and rollback

1. Save a consistent full production database dump and the current container/image metadata privately. Verify the compressed dump and retain the previous image for rollback.
2. Restore the approved Firefly archive into the local source database using `restore_source.py`. Import into a separate local rehearsal database migrated to the same Flyway version, and run the independent reconciliation in `import_firefly.py`.
3. Deploy the tested application through CI. Flyway adds the finance tables. Stop the application for the initial import; check that the production finance tables are empty.
4. Transfer a checksummed, data-only SQL dump of the reconciled finance tables. Load those tables only, in one transaction. Never restore the entire local Thereabout database over production. No Firefly authentication/session data is included in the finance archive.
5. Read back the imported finance tables and repeat the source reconciliation. Restore availability and verify authenticated dashboard, reports, pagination, denied unauthenticated origin access and MCP authentication.
6. Fetch ECB reference rates for valuation, preserving original booked amounts. Store reconciliation results and deployment identity privately with the release backup.

For application rollback, retain the additive finance tables, disable finances by removing the properties file, and redeploy the previous image. A full database restore is a separate disaster-recovery operation: stop the application and restore the pre-deployment SQL dump only if necessary, accounting for any new non-finance writes since the backup. Import scripts deliberately remain local-only; production transfer is an administrator operation over SSH.

Security references: [Cloudflare JWT validation](https://developers.cloudflare.com/cloudflare-one/access-controls/applications/http-apps/authorization-cookie/validating-json/) and [Spring JWT validation](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html).
