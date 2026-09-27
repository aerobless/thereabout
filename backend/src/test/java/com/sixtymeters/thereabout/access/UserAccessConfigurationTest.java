package com.sixtymeters.thereabout.access;

import com.sixtymeters.thereabout.communication.data.IdentityInApplicationRepository;
import com.sixtymeters.thereabout.finance.*;
import com.sixtymeters.thereabout.finance.service.FinanceMcpKeyService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.*;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserAccessConfigurationTest {
    ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(UserAccessConfiguration.class, FinanceCloudflareAccessConfiguration.class,
                    CurrentUserController.class, FinanceAccessFilter.class)
            .withBean(HttpServletRequest.class, MockHttpServletRequest::new)
            .withBean(IdentityInApplicationRepository.class, () -> mock(IdentityInApplicationRepository.class))
            .withBean(FinanceMcpKeyService.class, () -> mock(FinanceMcpKeyService.class));

    @Test void localStartupNeedsNoCloudflareSettings() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(JwtDecoder.class)).isEmpty();
            assertThat(context.getBean(CurrentUserController.class).getCurrentUser().getBody().getStatus().getValue()).isEqualTo("disabled");
            var request = new MockHttpServletRequest("GET", "/api/finances/accounts");
            var response = new MockHttpServletResponse();
            context.getBean(FinanceAccessFilter.class).doFilter(request, response, (req, res) -> res.getWriter().write("passed"));
            assertThat(response.getContentAsString()).isEqualTo("passed");
        });
    }
    @Test void userRecognitionStartsAutomaticallyWithItsOwnSettings() {
        runner.withPropertyValues("thereabout.users.access-issuer=https://team.cloudflareaccess.com",
                "thereabout.users.access-audience=user-app").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean("userAccessTokenDecoder").doesNotHaveBean("financeAccessTokenDecoder");
            assertThat(context.getBean(CurrentUserController.class).getCurrentUser().getBody().getStatus().getValue()).isEqualTo("missing_token");
        });
    }
    @Test void existingFinanceSettingsCanBeReusedWithoutAmbiguousDecoderInjection() {
        runner.withPropertyValues("thereabout.finances.access-mode=cloudflare", "thereabout.finances.access-issuer=https://team.cloudflareaccess.com",
                "thereabout.finances.access-audience=finance-app", "thereabout.finances.public-origin=https://app.example.test").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(JwtDecoder.class)).hasSize(2);
            assertThat(context.getBean(CurrentUserController.class).getCurrentUser().getBody().getStatus().getValue()).isEqualTo("missing_token");
            var request = new MockHttpServletRequest("GET", "/api/finances/accounts");
            var response = new MockHttpServletResponse();
            context.getBean(FinanceAccessFilter.class).doFilter(request, response, (req, res) -> {throw new AssertionError("Must reject missing token");});
            assertThat(response.getStatus()).isEqualTo(401);
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
}
