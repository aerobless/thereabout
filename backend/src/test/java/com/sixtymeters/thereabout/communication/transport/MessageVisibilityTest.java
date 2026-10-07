package com.sixtymeters.thereabout.communication.transport;

import com.sixtymeters.thereabout.communication.data.*;
import com.sixtymeters.thereabout.communication.service.*;
import com.sixtymeters.thereabout.generated.model.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class MessageVisibilityTest {
  @Autowired JdbcTemplate db;
  @Autowired IdentityRepository identities;
  @Autowired IdentityInApplicationRepository applications;
  @Autowired MessageRepository messages;
  @Autowired GroupMembershipService memberships;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  private final LocalDate day=LocalDate.of(1902,2,10);
  private IdentityInApplicationEntity theo, heidi, friend, group, unlinked;
  @BeforeEach void setup() {
    com.sixtymeters.thereabout.testing.TestUsers.owner(db);
    db.update("INSERT INTO identity(id,first_name,is_group,role) VALUES(2,'Heidi',FALSE,'USER') ON DUPLICATE KEY UPDATE role='USER',is_group=FALSE");
    theo=application(identities.findById(1L).orElseThrow(),false);
    heidi=application(identities.findById(2L).orElseThrow(),false);
    friend=application(identities.save(IdentityEntity.builder().firstName("Private friend").build()),false);
    group=application(identities.save(IdentityEntity.builder().firstName("Shared group").isGroup(true).build()),true);
    unlinked=application(null,false);
    message(friend,friend,"Theo private incoming");
    message(theo,heidi,"Shared direct outgoing");
    message(heidi,heidi,"Shared direct incoming");
    message(heidi,group,"Group authored by Heidi");
    message(friend,group,"Other group message");
    message(unlinked,unlinked,"Unlinked private archive");
    messages.flush();
  }
  private IdentityInApplicationEntity application(IdentityEntity identity,boolean group) {
    return applications.save(IdentityInApplicationEntity.builder().identity(identity).isGroup(group)
        .application(CommunicationApplication.TELEGRAM).identifier(UUID.randomUUID().toString()).build());
  }
  private void message(IdentityInApplicationEntity from,IdentityInApplicationEntity to,String body) {
    messages.save(MessageEntity.builder().type("text").source(CommunicationApplication.TELEGRAM).sender(from).receiver(to)
        .archiveUserIds(new HashSet<>(Set.of(1L))).body(body).timestamp(day.atTime(12,0)).build());
  }
  private GenMessagePage page(String target,int size) throws Exception {
    var request=get("/backend/api/v1/message/list").param("dateFrom",day.toString()).param("dateTo",day.toString()).param("size",String.valueOf(size));
    if(target!=null)request.header("X-Thereabout-Impersonate-User",target);
    return json.readValue(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),GenMessagePage.class);
  }
  @Test void impersonatedUserSeesOnlyDirectConversationsBeforePaginationAndCounts() throws Exception {
    assertThat(page(null,20).getTotalElements()).isEqualTo(6);
    var heidiPage=page("2",1);
    assertThat(heidiPage.getTotalElements()).isEqualTo(2);assertThat(heidiPage.getContent()).hasSize(1);
    var rows=json.readValue(mvc.perform(get("/backend/api/v1/message").param("date",day.toString()).header("X-Thereabout-Impersonate-User","2"))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),GenMessage[].class);
    assertThat(rows).extracting(GenMessage::getBody).containsExactlyInAnyOrder("Shared direct outgoing","Shared direct incoming");
  }
  @Test void explicitMembershipGrantsWholeGroupHistoryAndRemovalRevokesIt() throws Exception {
    long id=group.getIdentity().getId();var original=memberships.get(id);
    var saved=memberships.save(id,new GenGroupMembers().version(original.getVersion()).userIds(List.of(2L)));
    assertThat(saved.getVersion()).isGreaterThan(original.getVersion());
    assertThat(page("2",20).getContent()).extracting(GenMessage::getBody)
        .contains("Group authored by Heidi","Other group message").doesNotContain("Theo private incoming","Unlinked private archive");
    memberships.save(id,new GenGroupMembers().version(saved.getVersion()).userIds(List.of()));
    assertThat(page("2",20).getTotalElements()).isEqualTo(2);
    assertThatThrownBy(()->memberships.save(id,new GenGroupMembers().version(original.getVersion()).userIds(List.of(2L)))).hasMessageContaining("409");
  }
  @Test void ordinaryUsersCannotManageMembershipsOrReadConfigurationCatalog() throws Exception {
    mvc.perform(get("/backend/api/v1/identity/"+group.getIdentity().getId()+"/members").header("X-Thereabout-Impersonate-User","2")).andExpect(status().isForbidden());
    mvc.perform(get("/api/finances/configuration/mcp-tools").header("X-Thereabout-Impersonate-User","2")).andExpect(status().isForbidden());
  }
  @Test void duplicateArchiveOwnershipAccumulatesOnlyWithinSameConversation() {
    var original=messages.findAll().stream().filter(m->m.getBody().equals("Unlinked private archive")).findFirst().orElseThrow();
    original.setSourceIdentifier("archive-test");messages.flush();
    assertThat(ownership.retainExisting("archive-test",unlinked.getId(),new com.sixtymeters.thereabout.access.UserId(2))).isTrue();
    assertThat(ownership.retainExisting("archive-test",friend.getId(),new com.sixtymeters.thereabout.access.UserId(2))).isFalse();
    assertThat(original.getArchiveUserIds()).containsExactlyInAnyOrder(1L,2L);
  }
  @Autowired MessageArchiveOwnership ownership;
}
