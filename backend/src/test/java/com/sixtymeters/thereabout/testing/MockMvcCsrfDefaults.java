package com.sixtymeters.thereabout.testing;

import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.setup.ConfigurableMockMvcBuilder;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Controller tests exercise domain behaviour as the local user. The CSRF boundary itself is covered over
 * real HTTP by AccessSecurityTest, where this MockMvc default does not apply.
 */
@Component
class MockMvcCsrfDefaults implements MockMvcBuilderCustomizer {
    @Override
    public void customize(ConfigurableMockMvcBuilder<?> builder) {
        builder.defaultRequest(get("/").with(csrf()));
    }
}
