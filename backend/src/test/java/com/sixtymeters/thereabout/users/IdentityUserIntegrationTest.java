package com.sixtymeters.thereabout.users;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import com.sixtymeters.thereabout.communication.data.*;
import com.sixtymeters.thereabout.communication.service.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"thereabout.users.access-mode=local", "thereabout.calendar.worker-enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IdentityUserIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired IdentityRepository identities;
    @Autowired IdentityUserService users;
    @Autowired IdentityService editing;
    @Autowired IdentityInApplicationService linking;
    @Autowired JdbcTemplate db;

    private IdentityEntity person(boolean group) {
        return identities.saveAndFlush(IdentityEntity.builder().shortName("User-" + UUID.randomUUID().toString().substring(0,8)).isGroup(group).build());
    }
    private String email() { return UUID.randomUUID() + "@example.test"; }
    private org.springframework.mock.web.MockHttpServletResponse create(long id, String email) throws Exception {
        return mvc.perform(post("/backend/api/v1/identity/{id}/user",id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}")).andReturn().getResponse();
    }
    @Test void createsUserNormalizesEmailAndReturnsFlagWithoutLosingChatLinks() throws Exception {
        var person = person(false);
        var chat = IdentityInApplicationEntity.builder().identity(person).application(CommunicationApplication.WHATSAPP).identifier(email()).build();
        person.getIdentityInApplications().add(chat); identities.saveAndFlush(person);
        String email = email();
        var response = create(person.getId(), "  " + email.toUpperCase(Locale.ROOT) + "  ");
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("\"isUser\":true", "Cloudflare", email, "WhatsApp");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(create(person.getId(), email).getStatus()).isEqualTo(200);
        assertThat(db.queryForObject("select count(*) from identity_in_application where identity_id=?",Integer.class,person.getId())).isEqualTo(2);
    }
    @Test void rejectsBadEmailGroupsAndDuplicateClaimsWithoutLeavingAUserFlag() throws Exception {
        var first=person(false); var second=person(false); var group=person(true); String email=email();
        assertThat(create(first.getId(),"not-an-email").getStatus()).isEqualTo(400);
        assertThat(create(group.getId(),email).getStatus()).isEqualTo(400);
        assertThat(create(first.getId(),email).getStatus()).isEqualTo(200);
        assertThat(create(second.getId(),email).getStatus()).isEqualTo(409);
        assertThat(identities.findById(second.getId()).orElseThrow().isUser()).isFalse();
        assertThat(create(first.getId(),email()).getStatus()).isEqualTo(409);
    }
    @Test void genericEditsCannotEraseReplaceOrReassignTheCloudflareLink() {
        var person=person(false); var user=users.createUser(person.getId(),email());
        var cloudflare=user.getIdentityInApplications().getFirst();
        var update=IdentityEntity.builder().shortName("Renamed").identityInApplications(new ArrayList<>(List.of(
                IdentityInApplicationEntity.builder().application(CommunicationApplication.SIGNAL).identifier(email()).build()))).build();
        var edited=editing.updateIdentity(person.getId(),update);
        assertThat(edited.isUser()).isTrue();
        assertThat(edited.getIdentityInApplications()).extracting(IdentityInApplicationEntity::getApplication).contains(CommunicationApplication.CLOUDFLARE,CommunicationApplication.SIGNAL);
        update.setGroup(true);
        assertThatThrownBy(() -> editing.updateIdentity(person.getId(),update)).hasMessageContaining("Users cannot become groups");
        assertThatThrownBy(() -> linking.unlinkAppIdentity(cloudflare.getId())).hasMessageContaining("cannot be changed");
        var other=person(false);
        assertThatThrownBy(() -> linking.linkAppIdentity(cloudflare.getId(),other.getId())).hasMessageContaining("cannot be changed");
        assertThatThrownBy(() -> editing.createIdentity(IdentityEntity.builder().shortName("Bypass").identityInApplications(List.of(
                IdentityInApplicationEntity.builder().application(CommunicationApplication.CLOUDFLARE).identifier(email()).build())).build())).hasMessageContaining("Use Create User");
    }
    @Test void concurrentRequestsForOnePersonAreIdempotent() throws Exception {
        var person=person(false); String email=email();
        assertThat(concurrent(person.getId(),person.getId(),email)).containsExactlyInAnyOrder(200,200);
        assertThat(db.queryForObject("select count(*) from identity_in_application where application='CLOUDFLARE' and identity_id=?",Integer.class,person.getId())).isEqualTo(1);
    }
    @Test void concurrentPeopleCannotClaimTheSameEmailAndTheLoserRollsBack() throws Exception {
        var first=person(false); var second=person(false); String email=email();
        assertThat(concurrent(first.getId(),second.getId(),email)).containsExactlyInAnyOrder(200,409);
        assertThat(db.queryForObject("select count(*) from identity where id in (?,?) and is_user=true",Integer.class,first.getId(),second.getId())).isEqualTo(1);
    }
    private List<Integer> concurrent(long first,long second,String email) throws Exception {
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(() -> { start.await(); return create(first,email).getStatus(); });
            var b=pool.submit(() -> { start.await(); return create(second,email).getStatus(); });
            start.countDown(); return List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
        }
    }
    @Test void currentUserWithoutCloudflareIsNeutralAndNeverCached() throws Exception {
        var response=mvc.perform(get("/backend/api/v1/current-user")).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getContentAsString()).contains("NOT_CONFIGURED", "\"displayName\":null", "\"identityId\":null").doesNotContain("Theo");
    }
}
