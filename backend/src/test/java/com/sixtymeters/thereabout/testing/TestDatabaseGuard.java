package com.sixtymeters.thereabout.testing;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import java.util.regex.Pattern;

/** Runs before datasource and Flyway creation, including when environment variables override the profile. */
public class TestDatabaseGuard implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    private static final Pattern TEST_URL = Pattern.compile("^jdbc:mariadb://[^/]+/[A-Za-z0-9_]+_test(?:\\?.*)?$");

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        validate(context.getEnvironment().getProperty("spring.datasource.url"));
    }

    static void validate(String url) {
        if (url == null || !TEST_URL.matcher(url).matches()) {
            throw new IllegalStateException("Tests require an explicit MariaDB database ending in _test. Check THEREABOUT_TEST_DB_URL and datasource overrides.");
        }
    }
}
