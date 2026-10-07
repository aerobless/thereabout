package com.sixtymeters.thereabout.finance.splitwise;

import static org.assertj.core.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import tools.jackson.databind.json.JsonMapper;

class SplitwiseClientTest {
  HttpServer server;
  SplitwiseClient client;
  List<String> requests;
  @BeforeEach void setup() throws Exception {
    requests = Collections.synchronizedList(new ArrayList<>());
    server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    client = new SplitwiseClient(JsonMapper.builder().build(),HttpClient.newHttpClient(),"http://127.0.0.1:"+server.getAddress().getPort()+"/");
  }
  @AfterEach void stop() { server.stop(0); }
  String expense(int id) { return "{\"id\":"+id+",\"group_id\":99,\"description\":\"Meal\",\"date\":\"2026-09-01T12:00:00Z\",\"currency_code\":\"CHF\",\"payment\":false,\"deleted_at\":null,\"users\":[]}"; }
  @Test void readsEveryPageWithFixedUpperBoundaryAndNeverWrites() {
    var calls = new AtomicInteger();
    server.createContext("/get_expenses",exchange -> {
      requests.add(exchange.getRequestURI().toString());
      assertThat(exchange.getRequestMethod()).isEqualTo("GET"); assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer fixture");
      int page=calls.getAndIncrement(); String body="{\"expenses\":["+String.join(",",java.util.stream.IntStream.range(page*100,page==0?100:101).mapToObj(this::expense).toList())+"]}";
      byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
    }); server.start();
    var at=Instant.parse("2026-10-04T12:00:00Z");var after=at.minusSeconds(600);
    assertThat(client.expenses("fixture",99,after,at)).hasSize(101);assertThat(requests).hasSize(2);
    assertThat(requests.get(0)).contains("offset=0","updated_after=2026-10-04T11%3A50%3A00Z","updated_before=2026-10-04T12%3A00%3A00Z"); assertThat(requests.get(1)).contains("offset=100","updated_before=2026-10-04T12%3A00%3A00Z");
  }
  @Test void rateLimitPreservesRetryAfterAndDoesNotSleepOrLeakKey() {
    server.createContext("/get_current_user",exchange -> { exchange.getResponseHeaders().add("Retry-After","120");exchange.sendResponseHeaders(429,-1);exchange.close(); });server.start();
    assertThatThrownBy(() -> client.catalog("secret-fixture")).isInstanceOfSatisfying(SplitwiseClient.RemoteFailure.class,e -> {
      assertThat(e.status).isEqualTo(429); assertThat(e.retryAfter).isEqualTo(Duration.ofSeconds(120));assertThat(e).hasMessageNotContaining("secret-fixture");
    });
  }
  @Test void repeatedPagesRejectUnsafeCursorAdvance() {
    server.createContext("/get_expenses",exchange -> {String body="{\"expenses\":["+String.join(",",java.util.stream.IntStream.range(0,100).mapToObj(this::expense).toList())+"]}";byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});server.start();
    assertThatThrownBy(() -> client.expenses("fixture",99,null,Instant.now())).isInstanceOfSatisfying(SplitwiseClient.RemoteFailure.class,e-> assertThat(e.status).isEqualTo(502));
  }
}
