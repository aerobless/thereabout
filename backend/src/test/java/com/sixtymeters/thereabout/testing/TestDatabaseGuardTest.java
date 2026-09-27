package com.sixtymeters.thereabout.testing;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TestDatabaseGuardTest {
    @Test void acceptsOnlyExplicitTestDatabases() {
        assertThatCode(() -> TestDatabaseGuard.validate("jdbc:mariadb://127.0.0.1:3307/thereabout_test")).doesNotThrowAnyException();
        assertThatCode(() -> TestDatabaseGuard.validate("jdbc:mariadb://localhost:3306/ci_test?useSsl=false")).doesNotThrowAnyException();
        for (String url : new String[]{null, "", "jdbc:mariadb://localhost:3306/thereabout", "jdbc:mariadb://localhost/thereabout?name=_test"}) {
            assertThatThrownBy(() -> TestDatabaseGuard.validate(url)).isInstanceOf(IllegalStateException.class);
        }
    }
}
