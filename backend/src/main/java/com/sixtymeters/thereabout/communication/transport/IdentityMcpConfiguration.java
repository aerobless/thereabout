package com.sixtymeters.thereabout.communication.transport;

import com.sixtymeters.thereabout.communication.service.IdentityOperations;
import com.sixtymeters.thereabout.finance.transport.*;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.*;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import com.sixtymeters.thereabout.generated.model.*;
import jakarta.validation.Validator;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.*;
import static com.sixtymeters.thereabout.finance.domain.FinanceRules.*;

@Configuration
public class IdentityMcpConfiguration {
  @Bean
  public HttpServletStreamableServerTransportProvider identityTransport() {
    return HttpServletStreamableServerTransportProvider.builder().jsonMapper(McpJsonDefaults.getMapper())
        .mcpEndpoint("/mcp/identities").contextExtractor(request -> {
          var auth = SecurityContextHolder.getContext().getAuthentication();
          return io.modelcontextprotocol.common.McpTransportContext.create(auth == null ? Map.of() : Map.of("thereabout.actor", auth));
        }).build();
  }
  @Bean
  public ServletRegistrationBean<HttpServletStreamableServerTransportProvider> identityMcpServlet(
      @Qualifier("identityTransport") HttpServletStreamableServerTransportProvider transport) {
    var servlet = new ServletRegistrationBean<>(transport, "/mcp/identities"); servlet.setAsyncSupported(true); return servlet;
  }
  @Bean(destroyMethod = "close")
  public McpSyncServer identityMcpServer(@Qualifier("identityTransport") HttpServletStreamableServerTransportProvider transport,
      IdentityOperations identities, ObjectMapper json, Validator validator, McpToolCatalog catalog) throws IOException {
    var tools = new Tools(json, validator, new FinanceOpenApiSchemas(json).load("openapi/communication.yaml"), catalog);
    return McpServer.sync(transport).serverInfo("thereabout-identities", "1.0.0")
        .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
        .tools(
            tools.tool("list", "Search people/groups, relationships and app IDs with pagination and application/linked filters.", true, GenIdentityQuery.class, identities::list),
            tools.tool("get", "Read identity metadata, current version and linked application IDs.", true, GenIdentityId.class, input -> identities.get(input.getId())),
            tools.tool("create", "Create a contact or group without a user role. Requires requestKey. User creation and Cloudflare login management are UI-only.", false, GenIdentityCreateInput.class, identities::create),
            tools.tool("update", "Update identity name/kind/relationship; preserves existing role and Cloudflare login. Requires current version and requestKey.", false, GenIdentityUpdateInput.class, identities::update),
            tools.tool("delete", "Delete a contact/group with current version and requestKey. User deletion is prohibited.", false, GenIdentityVersionedInput.class, identities::delete),
            tools.tool("applications_list", "Search imported application IDs, username hints and identity names; filter linked/unlinked, kind, application or identity with pagination.", true, GenIdentityQuery.class, identities::applications),
            tools.tool("applications_get", "Read an existing imported app ID, linked identityId and version. This endpoint cannot create app IDs.", true, GenIdentityId.class, input -> identities.application(input.getId())),
            tools.tool("applications_link", "Link an existing imported app ID to a person/group of the same kind; Cloudflare is prohibited. Requires app and identity versions and requestKey.", false, GenIdentityLinkInput.class, identities::link),
            tools.tool("applications_unlink", "Unlink an imported app ID with current version and requestKey; Cloudflare is prohibited.", false, GenIdentityVersionedInput.class, identities::unlink),
            tools.tool("members_get", "Read explicit group history access and current identity version.", true, GenIdentityId.class, input -> identities.members(input.getId())),
            tools.tool("members_save", "Replace group membership with up to 100 existing users; grants complete group history. Requires identity version and requestKey.", false, GenIdentityMembershipInput.class, identities::saveMembers)
        ).build();
  }
  private record Tools(ObjectMapper json, Validator validator, JsonNode schemas, McpToolCatalog catalog) {
    @SuppressWarnings("unchecked")
    <I, R> McpServerFeatures.SyncToolSpecification tool(String operation, String description, boolean read, Class<I> type, Function<I, R> handler) {
      var schema = schemas.required(type.getSimpleName().substring(3));
      var tool = McpSchema.Tool.builder("identity_" + operation, json.convertValue(schema, Map.class)).description(description)
          .annotations(new McpSchema.ToolAnnotations(operation, read, !read, true, false, false)).build();
      catalog.register("/mcp/identities", tool, read);
      return McpServerFeatures.SyncToolSpecification.builder().tool(tool).callHandler((exchange, request) -> {
        var previous = SecurityContextHolder.getContext(); var context = SecurityContextHolder.createEmptyContext();
        if (exchange.transportContext().get("thereabout.actor") instanceof org.springframework.security.core.Authentication actor) context.setAuthentication(actor);
        SecurityContextHolder.setContext(context);
        try {
          // Jackson's global compatibility settings must not allow injected privileged fields here.
          require(request.arguments().keySet().stream().allMatch(schema.required("properties")::has), "Unsupported input property");
          I input = json.convertValue(request.arguments(), type);
          var violations = validator.validate(input);
          require(violations.isEmpty(), violations.stream().map(v -> v.getPropertyPath() + ": " + v.getMessage()).findFirst().orElse("Invalid input"));
          R result = handler.apply(input);
          return McpSchema.CallToolResult.builder().structuredContent(result).addTextContent(json.writeValueAsString(result)).isError(false).build();
        } catch (ResponseStatusException e) { return error(e.getStatusCode() + ": " + e.getReason()); }
        catch (OptimisticLockingFailureException e) { return error("409: Record changed; reload before saving"); }
        catch (IllegalArgumentException e) { return error("400: Invalid request values"); }
        finally { SecurityContextHolder.setContext(previous); }
      }).build();
    }
    private McpSchema.CallToolResult error(String message) { return McpSchema.CallToolResult.builder().addTextContent(message).isError(true).build(); }
  }
}
