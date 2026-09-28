package com.sixtymeters.thereabout.access;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class AccessConfigurationTest {
    ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(AccessConfiguration.class);

    @Test void localStartupNeedsNoCloudflareSettings() {
        runner.withPropertyValues("thereabout.access.mode=local").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(AccessConfiguration.CloudflareAccess.class).decoder()).isNull();
        });
    }
    @Test void cloudflareSettingsCreateTheVerifier() {
        runner.withPropertyValues("thereabout.access.mode=cloudflare", "thereabout.access.cloudflare.issuer=https://team.cloudflareaccess.com",
                "thereabout.access.cloudflare.audience=app").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(AccessConfiguration.CloudflareAccess.class).decoder()).isNotNull();
        });
    }
    @Test void missingPartialOrInvalidSettingsFailClosed() {
        for (String[] properties : new String[][] {
                {"thereabout.access.mode="},
                {"thereabout.access.mode=open"},
                {"thereabout.access.mode=cloudflare"},
                {"thereabout.access.mode=local", "thereabout.access.cloudflare.issuer=https://team.cloudflareaccess.com"},
                {"thereabout.access.mode=local", "thereabout.access.cloudflare.audience=app"},
                {"thereabout.access.mode=cloudflare", "thereabout.access.cloudflare.issuer=http://team.cloudflareaccess.com", "thereabout.access.cloudflare.audience=app"}}) {
            runner.withPropertyValues(properties).run(context -> assertThat(context).as(String.join(", ", properties)).hasFailed());
        }
    }
    @Test void cloudflareModeExplainsMissingSettings() {
        runner.withPropertyValues("thereabout.access.mode=cloudflare").run(context ->
                assertThat(context).getFailure().hasRootCauseMessage("Cloudflare access requires thereabout.access.cloudflare.issuer and audience"));
    }
}
