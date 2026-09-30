package com.sixtymeters.thereabout.access;

import com.sixtymeters.thereabout.communication.data.IdentityEntity;
import com.sixtymeters.thereabout.communication.data.IdentityRepository;
import com.sixtymeters.thereabout.communication.service.IdentityUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class UserBootstrapTest {
    @Autowired CloudflareUsers users;
    @Autowired IdentityRepository identities;
    @Autowired IdentityUserService identityUsers;
    @Autowired JdbcTemplate jdbc;

    /** Removes users created by other tests so the installation looks new. */
    @BeforeEach
    void withoutUsers() {
        jdbc.update("delete from identity_in_application where application='CLOUDFLARE'");
        jdbc.update("update identity set role=null");
        jdbc.update("delete from configuration where config_key='USER_BOOTSTRAP'");
    }

    String email() { return UUID.randomUUID() + "@example.test"; }

    @Test
    void firstLoginBecomesTheAdministratorAndLaterLoginsStayUnlinked() {
        String first = email();
        var admin = users.resolve(first).orElseThrow();
        assertThat(admin.admin()).isTrue();
        assertThat(admin.displayName()).isEqualTo("Admin");
        assertThat(users.resolve(first)).contains(admin);
        assertThat(users.resolve(email())).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from identity where role is not null", Integer.class)).isEqualTo(1);
    }

    @Test
    void existingUsersPreventBootstrap() {
        long id = identities.save(IdentityEntity.builder().shortName("existing-user").build()).getId();
        identityUsers.createUser(id, email(), com.sixtymeters.thereabout.communication.data.UserRole.USER);
        assertThat(users.resolve(email())).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from identity where role='ADMIN'", Integer.class)).isZero();
    }

    @Test
    void concurrentFirstLoginsCreateExactlyOneAdministrator() throws Exception {
        var start = new CyclicBarrier(2);
        Callable<Boolean> login = () -> {
            String email = email();
            start.await();
            return users.resolve(email).isPresent();
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = executor.invokeAll(List.of(login, login));
            assertThat(results.stream().map(result -> {
                try { return result.get(); } catch (Exception e) { throw new IllegalStateException(e); }
            })).containsExactlyInAnyOrder(true, false);
        }
        assertThat(jdbc.queryForObject("select count(*) from identity where role='ADMIN'", Integer.class)).isEqualTo(1);
    }
}
