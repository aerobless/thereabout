package com.sixtymeters.thereabout.communication.transport;

import com.sixtymeters.thereabout.communication.data.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IdentityUserTest {
    @Autowired MockMvc mvc;
    @Autowired IdentityRepository identities;
    @Autowired JdbcTemplate jdbc;

    long person(boolean group) {
        return identities.saveAndFlush(IdentityEntity.builder().shortName("user-test-" + UUID.randomUUID().toString().substring(0, 8)).isGroup(group).build()).getId();
    }
    String email() { return UUID.randomUUID() + "@example.test"; }
    int create(long id, String email) throws Exception {
        return mvc.perform(post("/backend/api/v1/identity/{id}/user", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"role\":\"USER\"}")).andReturn().getResponse().getStatus();
    }
    int edit(long id, String body) throws Exception {
        return mvc.perform(put("/backend/api/v1/identity/{id}", id).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus();
    }
    long link(long id) {
        return jdbc.queryForObject("select id from identity_in_application where identity_id=? and application='CLOUDFLARE'", Long.class, id);
    }
    @Test void currentUserEndpointIsExplicitAndNeverCachedWhenDisabled() throws Exception {
        var response = mvc.perform(get("/backend/api/v1/current-user")
                .header("Cf-Access-Authenticated-User-Email", "forged@example.test")).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getContentAsString()).doesNotContain("forged@example.test").doesNotContain("forged@example.test");
    }
    @Test void createsNormalizesAndIsIdempotentWithoutLosingChatLinks() throws Exception {
        long id = person(false);
        jdbc.update("insert into identity_in_application(identity_id,application,identifier,is_group) values (?,'TELEGRAM',?,false)", id, UUID.randomUUID().toString());
        String email = email();
        assertThat(create(id, " \u00a0" + email.toUpperCase() + "\uFEFF ")).isEqualTo(200);
        long link = link(id);
        assertThat(create(id, email)).isEqualTo(200);
        assertThat(link(id)).isEqualTo(link);
        assertThat(identities.findById(id).orElseThrow().isUser()).isTrue();
        assertThat(jdbc.queryForObject("select identifier from identity_in_application where id=?", String.class, link)).isEqualTo(email);
        assertThat(jdbc.queryForObject("select count(*) from identity_in_application where identity_id=?", Integer.class, id)).isEqualTo(2);
    }
    @Test void rejectsBadEmailGroupsMissingPeopleAndChangedEmail() throws Exception {
        long id = person(false);
        for (String bad : new String[]{"", "a", "a@b", "a..b@example.test", "a@-bad.test", "x".repeat(65) + "@example.test", "a@" + "x".repeat(64) + ".test"}) {
            assertThat(create(id, bad)).isEqualTo(400);
        }
        assertThat(create(person(true), email())).isEqualTo(400);
        assertThat(create(Long.MAX_VALUE, email())).isEqualTo(404);
        assertThat(create(id, email())).isEqualTo(200);
        assertThat(create(id, email())).isEqualTo(409);
    }
    @Test void conflictingEmailRollsBackFlagAndLink() throws Exception {
        long one = person(false), two = person(false);
        String email = email();
        assertThat(create(one, email)).isEqualTo(200);
        assertThat(create(two, email)).isEqualTo(409);
        assertThat(identities.findById(two).orElseThrow().isUser()).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from identity_in_application where identity_id=?", Integer.class, two)).isZero();
    }
    @Test void parallelRequestsForOnePersonAreIdempotentAndAllowOnlyOneEmail() throws Exception {
        long id = person(false);
        String email = email();
        assertThat(parallel(() -> create(id, email), () -> create(id, email))).containsExactlyInAnyOrder(200, 200);
        long second = person(false);
        assertThat(parallel(() -> create(second, email()), () -> create(second, email()))).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject("select count(*) from identity_in_application where identity_id=?", Integer.class, second)).isEqualTo(1);
    }
    @Test void parallelEmailClaimsHaveOneWinnerAndFullyRollBackLoser() throws Exception {
        long one = person(false), two = person(false);
        String email = email();
        assertThat(parallel(() -> create(one, email), () -> create(two, email))).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject("select count(*) from identity where id in (?,?) and role is not null", Integer.class, one, two)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from identity_in_application where identity_id in (?,?)", Integer.class, one, two)).isEqualTo(1);
    }
    java.util.List<Integer> parallel(Callable<Integer> first, Callable<Integer> second) throws Exception {
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { gate.await(); return first.call(); });
            var b = pool.submit(() -> { gate.await(); return second.call(); });
            gate.countDown();
            return java.util.List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        }
    }
    @Test void staleGenericEditPreservesUserAndCloudflareWhileChatEditingStillWorks() throws Exception {
        long id = person(false);
        assertThat(create(id, email())).isEqualTo(200);
        long link = link(id);
        assertThat(edit(id, """
                {"id":%d,"shortName":"Heidi","role":null,"isGroup":false,"identityInApplications":[
                {"id":0,"application":"Telegram","identifier":"%s"}]}
                """.formatted(id, UUID.randomUUID()))).isEqualTo(200);
        assertThat(link(id)).isEqualTo(link);
        assertThat(identities.findById(id).orElseThrow().isUser()).isTrue();
        assertThat(edit(id, "{\"id\":" + id + ",\"shortName\":\"Group\",\"isGroup\":true}")).isEqualTo(400);
        assertThat(identities.findById(id).orElseThrow().getShortName()).isEqualTo("Heidi");
        assertThat(mvc.perform(put("/backend/api/v1/identity-in-application/{id}/unlink", link)).andReturn().getResponse().getStatus()).isEqualTo(400);
        assertThat(mvc.perform(put("/backend/api/v1/identity-in-application/{id}/link/{identityId}", link, person(false))).andReturn().getResponse().getStatus()).isEqualTo(400);
        assertThat(mvc.perform(delete("/backend/api/v1/identity/{id}", id)).andReturn().getResponse().getStatus()).isEqualTo(400);
        assertThat(edit(id, """
                {"id":%d,"shortName":"Heidi","identityInApplications":[{"id":%d,"application":"Cloudflare","identifier":"changed@example.test"}]}
                """.formatted(id, link))).isEqualTo(400);
        assertThat(link(id)).isEqualTo(link);
    }
    @Test void genericCreationAndEditingCannotManufactureCloudflareMappings() throws Exception {
        long id = person(false);
        String payload = """
                {"id":%d,"shortName":"Forged","role":"ADMIN","identityInApplications":[{"id":0,"application":"Cloudflare","identifier":"%s"}]}
                """.formatted(id, email());
        assertThat(edit(id, payload)).isEqualTo(400);
        assertThat(mvc.perform(post("/backend/api/v1/identity").contentType(MediaType.APPLICATION_JSON).content(payload)).andReturn().getResponse().getStatus()).isEqualTo(400);
        assertThat(edit(id, "{\"id\":" + id + ",\"shortName\":\"Still contact\",\"role\":\"ADMIN\"}")).isEqualTo(200);
        assertThat(identities.findById(id).orElseThrow().isUser()).isFalse();
    }
}
