package com.sixtymeters.thereabout.access;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@Configuration
@Conditional(UserAccessConfiguration.HasAccessSettings.class)
public class UserAccessConfiguration {
    /** A partial configuration must reach decoder validation and fail startup. */
    static class HasAccessSettings implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            var environment = context.getEnvironment();
            return environment.containsProperty("thereabout.users.access-issuer")
                    || environment.containsProperty("thereabout.users.access-audience")
                    || environment.containsProperty("thereabout.finances.access-issuer")
                    || environment.containsProperty("thereabout.finances.access-audience");
        }
    }

    @Bean
    JwtDecoder userAccessTokenDecoder(
            @Value("${thereabout.users.access-issuer:${thereabout.finances.access-issuer:}}") String issuer,
            @Value("${thereabout.users.access-audience:${thereabout.finances.access-audience:}}") String audience) {
        return CloudflareAccessTokens.decoder(issuer, audience);
    }
}
