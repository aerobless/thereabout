package com.sixtymeters.thereabout.communication.transport;

import com.sixtymeters.thereabout.communication.data.*;
import com.sixtymeters.thereabout.communication.service.IdentityUserService;
import com.sixtymeters.thereabout.finance.service.FinanceMcpKeyService;
import com.sixtymeters.thereabout.generated.model.*;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class IdentityMcpTest {
  @LocalServerPort int port;
  @Autowired FinanceMcpKeyService keys;
  @Autowired JdbcTemplate db;
  @Autowired ObjectMapper json;
  @Autowired IdentityInApplicationRepository applications;
  @Autowired IdentityUserService users;
  @BeforeEach void owner() { com.sixtymeters.thereabout.testing.TestUsers.owner(db); }
  private McpSyncClient client() {
    var transport=HttpClientStreamableHttpTransport.builder("http://127.0.0.1:"+port).endpoint("/mcp/identities")
        .requestBuilder(HttpRequest.newBuilder().header("Authorization","Bearer "+keys.getKey())).build();
    var client=McpClient.sync(transport).requestTimeout(Duration.ofSeconds(15)).build(); client.initialize(); return client;
  }
  private McpSchema.CallToolResult call(McpSyncClient client,String tool,Map<String,Object> input) {
    return client.callTool(McpSchema.CallToolRequest.builder("identity_"+tool).arguments(input).build());
  }
  private <T> T ok(McpSyncClient client,String tool,Map<String,Object> input,Class<T> type) {
    var result=call(client,tool,input); assertThat(result.isError()).as(tool+": "+result.content()).isFalse(); return json.convertValue(result.structuredContent(),type);
  }
  private GenIdentity create(McpSyncClient client,String name,boolean group) {
    return ok(client,"create",Map.of("firstName",name,"isGroup",group,"requestKey",UUID.randomUUID().toString()),GenIdentity.class);
  }
  @Test void discoversStrictToolsAndProvidesReplayVersionedLinkingSearchAndMembership() {
    try(var client=client()) {
      var tools=client.listTools().tools(); assertThat(tools).hasSize(11);
      assertThat(tools).extracting(McpSchema.Tool::name).noneMatch(name->name.toLowerCase().contains("role")||name.contains("create_user"));
      var input=Map.<String,Object>of("firstName","MCP Person "+UUID.randomUUID(),"lastName","Surname","isGroup",false,"requestKey",UUID.randomUUID().toString());
      var person=ok(client,"create",input,GenIdentity.class);
      assertThat(ok(client,"create",input,GenIdentity.class)).isEqualTo(person); assertThat(person.getRole()).isNull();
      var injected=new HashMap<>(input); injected.put("role","ADMIN"); assertThat(call(client,"create",injected).isError()).isTrue();
      injected.remove("role"); injected.put("identityInApplications",List.of()); assertThat(call(client,"create",injected).isError()).isTrue();
      long id=person.getId().longValueExact();
      var page=ok(client,"list",Map.of("q",person.getFirstName()+" Surname","isGroup",false,"pageSize",1),GenIdentityPage.class);
      assertThat(page.getTotal()).isEqualTo(1); assertThat(page.getItems()).hasSize(1);
      var app=applications.saveAndFlush(IdentityInApplicationEntity.builder().application(CommunicationApplication.TELEGRAM)
          .identifier("mcp-"+UUID.randomUUID()).usernameHint("Unique import hint").isGroup(false).build());
      var args=Map.<String,Object>of("id",app.getId(),"identityId",id,"identityVersion",person.getVersion(),"version",app.getVersion(),"requestKey",UUID.randomUUID().toString());
      var linked=ok(client,"applications_link",args,GenIdentityInApplication.class);
      assertThat(linked.getIdentityId()).isEqualTo(id); assertThat(linked.getVersion()).isGreaterThan(app.getVersion());
      assertThat(ok(client,"applications_link",args,GenIdentityInApplication.class)).isEqualTo(linked);
      var search=ok(client,"applications_list",Map.of("q","Unique import hint","linked",true,"identityId",id,"application","Telegram"),GenApplicationIdentityPage.class);
      assertThat(search.getItems()).extracting(GenIdentityInApplication::getId).contains(linked.getId());
      var stale=new HashMap<>(args); stale.put("requestKey",UUID.randomUUID().toString()); assertThat(call(client,"applications_link",stale).content().toString()).contains("409");
      var unlinked=ok(client,"applications_unlink",Map.of("id",app.getId(),"version",linked.getVersion(),"requestKey",UUID.randomUUID().toString()),GenIdentityInApplication.class);
      assertThat(unlinked.getIdentityId()).isNull();
      var group=create(client,"MCP Group "+UUID.randomUUID(),true);
      assertThat(call(client,"applications_link",Map.of("id",app.getId(),"version",unlinked.getVersion(),"identityId",group.getId(),"identityVersion",group.getVersion(),"requestKey",UUID.randomUUID().toString())).isError()).isTrue();
      var membership=Map.<String,Object>of("id",group.getId(),"version",group.getVersion(),"userIds",List.of(1L),"requestKey",UUID.randomUUID().toString());
      var members=ok(client,"members_save",membership,GenGroupMembers.class); assertThat(members.getUserIds()).containsExactly(1L);
      assertThat(ok(client,"members_save",membership,GenGroupMembers.class)).isEqualTo(members);
      var staleMembership=new HashMap<>(membership); staleMembership.put("requestKey",UUID.randomUUID().toString()); assertThat(call(client,"members_save",staleMembership).isError()).isTrue();
      var current=ok(client,"get",Map.of("id",id),GenIdentity.class);
      var changed=ok(client,"update",Map.of("id",id,"version",current.getVersion(),"firstName","Changed "+UUID.randomUUID(),"isGroup",false,"requestKey",UUID.randomUUID().toString()),GenIdentity.class);
      assertThat(changed.getVersion()).isGreaterThan(current.getVersion());
      var staleUpdate=call(client,"update",Map.of("id",id,"version",current.getVersion(),"firstName","Stale","isGroup",false,"requestKey",UUID.randomUUID().toString())); assertThat(staleUpdate.content().toString()).contains("409");
      ok(client,"delete",Map.of("id",id,"version",changed.getVersion(),"requestKey",UUID.randomUUID().toString()),Boolean.class);
      ok(client,"delete",Map.of("id",group.getId(),"version",members.getVersion(),"requestKey",UUID.randomUUID().toString()),Boolean.class);
      applications.deleteById(app.getId());
    }
  }
  @Test void editsPreserveUserRoleAndCloudflareMappingAndRejectPrivilegeMutations() {
    try(var client=client()) {
      var contact=create(client,"Existing user "+UUID.randomUUID(),false); long id=contact.getId().longValueExact();
      String email=UUID.randomUUID()+"@example.test";
      users.createUser(id,email,UserRole.USER);
      var user=ok(client,"get",Map.of("id",id),GenIdentity.class);
      var changed=ok(client,"update",Map.of("id",id,"version",user.getVersion(),"firstName","Updated user","isGroup",false,"requestKey",UUID.randomUUID().toString()),GenIdentity.class);
      assertThat(changed.getRole()).isEqualTo(GenUserRole.USER);
      assertThat(changed.getIdentityInApplications()).anySatisfy(app->{assertThat(app.getApplication()).isEqualTo("Cloudflare");assertThat(app.getIdentifier()).isEqualTo(email);});
      assertThat(call(client,"delete",Map.of("id",id,"version",changed.getVersion(),"requestKey",UUID.randomUUID().toString())).isError()).isTrue();
      var app=changed.getIdentityInApplications().getFirst();
      assertThat(call(client,"applications_unlink",Map.of("id",app.getId(),"version",app.getVersion(),"requestKey",UUID.randomUUID().toString())).isError()).isTrue();
      assertThat(call(client,"update",Map.of("id",id,"version",changed.getVersion(),"firstName","Group","isGroup",true,"requestKey",UUID.randomUUID().toString())).isError()).isTrue();
      for(String property:List.of("role","email","cloudflareLogin","identityInApplications")) {
        var malicious=new HashMap<String,Object>(Map.of("id",id,"version",changed.getVersion(),"firstName","No change","isGroup",false,"requestKey",UUID.randomUUID().toString()));
        malicious.put(property,"ADMIN"); assertThat(call(client,"update",malicious).isError()).as(property).isTrue();
      }
      assertThat(ok(client,"get",Map.of("id",id),GenIdentity.class)).isEqualTo(changed);
    }
  }
}
