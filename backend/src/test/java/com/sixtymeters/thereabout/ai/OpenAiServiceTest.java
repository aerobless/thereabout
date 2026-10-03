package com.sixtymeters.thereabout.ai;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.sixtymeters.thereabout.client.data.*;
import com.sixtymeters.thereabout.finance.service.ImportInterpreter;
import com.sixtymeters.thereabout.generated.model.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class OpenAiServiceTest {
  private final ConfigurationRepository repository = mock(ConfigurationRepository.class);
  private final Map<ConfigurationKey, ConfigurationEntity> values =
      new EnumMap<>(ConfigurationKey.class);
  private final OpenAiClientFactory clients = mock(OpenAiClientFactory.class);

  private OpenAiService service() {
    when(repository.findById(any()))
        .thenAnswer(i -> Optional.ofNullable(values.get(i.getArgument(0))));
    when(repository.save(any()))
        .thenAnswer(
            i -> {
              ConfigurationEntity entity = i.getArgument(0);
              values.put(entity.getConfigKey(), entity);
              return entity;
            });
    doAnswer(
            i -> {
              values.remove(i.getArgument(0));
              return null;
            })
        .when(repository)
        .deleteById(any());
    return new OpenAiService(repository, clients);
  }

  @Test
  void storesReplacementAndRemovalWithoutReturningSecrets() {
    var ai = service();
    assertThat(ai.settings().getModel()).isEqualTo("gpt-6-luna");
    assertThatThrownBy(() -> ai.respond("test", "test", OpenAiService.ConnectionResult.class))
        .hasMessageContaining("Configure");
    var saved = ai.save(new GenOpenAiSettingsInput().model("gpt-6-luna").apiKey("synthetic-key"));
    assertThat(saved.getConfigured()).isTrue();
    assertThat(saved.toString()).doesNotContain("synthetic-key");
    ai.save(new GenOpenAiSettingsInput().model("another-model"));
    assertThat(values.get(ConfigurationKey.OPENAI_API_KEY).getConfigValue())
        .isEqualTo("synthetic-key");
    ai.save(new GenOpenAiSettingsInput().model("another-model").removeKey(true));
    assertThat(ai.settings().getConfigured()).isFalse();
  }

  @Test
  void exercisesOfficialSdkWireFormatAndTypedResponseWithoutExternalApiCalls() throws Exception {
    var ai = service();
    ai.save(new GenOpenAiSettingsInput().model("gpt-6-luna").apiKey("synthetic-key"));
    var request = new AtomicReference<String>();
    var response =
        new AtomicReference<String>(
            """
            {"id":"resp-test","object":"response","created_at":1,"model":"gpt-6-luna","status":"completed","output":[{"id":"msg-test","type":"message","role":"assistant","status":"completed","content":[{"type":"output_text","text":"{\\"message\\":\\"ready\\"}","annotations":[]}]}]}
            """);
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/v1/responses",
        exchange -> {
          request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] body = response.get().getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          try (var out = exchange.getResponseBody()) {
            out.write(body);
          }
        });
    server.start();
    try {
      when(clients.create(anyString()))
          .thenAnswer(
              i ->
                  OpenAIOkHttpClient.builder()
                      .apiKey(i.getArgument(0, String.class))
                      .baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1")
                      .maxRetries(0)
                      .build());
      assertThat(
              ai.respond("Acknowledgement", "synthetic input", OpenAiService.ConnectionResult.class)
                  .message)
          .isEqualTo("ready");
      var json = new ObjectMapper();
      var sent = json.readTree(request.get());
      assertThat(sent.path("store").asBoolean()).isFalse();
      assertThat(sent.at("/text/format/strict").asBoolean()).isTrue();
      assertThat(sent.at("/reasoning/effort").asString()).isEqualTo("medium");
      response.set(
          "{\"id\":\"resp-test\",\"object\":\"response\",\"created_at\":1,\"model\":\"gpt-6-luna\",\"status\":\"completed\",\"output\":[{\"id\":\"msg-test\",\"type\":\"message\",\"role\":\"assistant\",\"status\":\"completed\",\"content\":[{\"type\":\"output_text\",\"text\":\"{\\\"rows\\\":[]}\",\"annotations\":[]}]}]}");
      assertThat(
              ai.respond("Interpret synthetic CSV", "synthetic", ImportInterpreter.Result.class)
                  .rows)
          .isEmpty();
      sent = json.readTree(request.get());
      assertThat(
              sent.at("/text/format/schema/properties/rows/items/properties/otherAccountId")
                  .toString())
          .contains("null");
      response.set(
          "{\"id\":\"resp-test\",\"object\":\"response\",\"created_at\":1,\"model\":\"gpt-6-luna\",\"status\":\"incomplete\",\"incomplete_details\":{\"reason\":\"max_output_tokens\"},\"output\":[]}");
      assertThatThrownBy(() -> ai.respond("test", "test", OpenAiService.ConnectionResult.class))
          .hasMessageContaining("incomplete");
    } finally {
      server.stop(0);
    }
  }
}
