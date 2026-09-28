package com.sixtymeters.thereabout.access;

import com.sixtymeters.thereabout.config.SecurityConfiguration;
import com.sixtymeters.thereabout.finance.FinanceCloudflareAccessConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserAccessConfigurationTest {
    ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(UserAccessConfiguration.class, FinanceCloudflareAccessConfiguration.class);

    @Test void localStartupNeedsNoCloudflareSettings() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(JwtDecoder.class)).isEmpty();
        });
    }
    @Test void userRecognitionStartsAutomaticallyWithItsOwnSettings() {
        runner.withPropertyValues("thereabout.users.access-issuer=https://team.cloudflareaccess.com",
                "thereabout.users.access-audience=user-app").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean("userAccessTokenDecoder").doesNotHaveBean("financeAccessTokenDecoder");
        });
    }
    @Test void existingFinanceSettingsCanBeReusedWithoutAmbiguousDecoderInjection() {
        runner.withPropertyValues("thereabout.finances.access-mode=cloudflare", "thereabout.finances.access-issuer=https://team.cloudflareaccess.com",
                "thereabout.finances.access-audience=finance-app").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean("userAccessTokenDecoder").hasBean("financeAccessTokenDecoder");
        });
    }
    @Test void partialOrEmptyAccessSettingsFailStartup() {
        for (String property : new String[] {
                "thereabout.users.access-issuer=https://team.cloudflareaccess.com",
                "thereabout.users.access-audience=user-app",
                "thereabout.finances.access-issuer=https://team.cloudflareaccess.com",
                "thereabout.finances.access-audience=finance-app",
                "thereabout.users.access-issuer="}) {
            runner.withPropertyValues(property).run(context -> assertThat(context).hasFailed());
        }
    }
    @Test void cloudflareModeWithoutAccessSettingsAndUnknownModesFailClosed() {
        var security = runner.withUserConfiguration(SecurityConfiguration.class)
                .withBean(CloudflareUsers.class, () -> mock(CloudflareUsers.class));
        security.withPropertyValues("thereabout.access.mode=cloudflare").run(context ->
                assertThat(context).getFailure().hasRootCauseMessage("Cloudflare access requires thereabout.users.access-issuer and access-audience"));
        security.withPropertyValues("thereabout.access.mode=open").run(context ->
                assertThat(context).getFailure().hasRootCauseMessage("thereabout.access.mode must be local or cloudflare"));
    }
}
