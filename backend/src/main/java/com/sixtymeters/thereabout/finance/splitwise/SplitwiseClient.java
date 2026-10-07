package com.sixtymeters.thereabout.finance.splitwise;

import com.sixtymeters.thereabout.generated.model.*;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.*;

/** Read-only client. The worker persists retry times instead of sleeping through rate limits. */
@Component
public class SplitwiseClient {
  private final ObjectMapper json;
  private final HttpClient http;
  private final String base;
  @org.springframework.beans.factory.annotation.Autowired
  public SplitwiseClient(ObjectMapper json) {
    this(json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NEVER).build(),
        "https://secure.splitwise.com/api/v3.0/");
  }
  SplitwiseClient(ObjectMapper json, HttpClient http, String base) { this.json = json; this.http = http; this.base = base; }
  public static class RemoteFailure extends RuntimeException {
    public final int status;
    public final Duration retryAfter;
    public RemoteFailure(int status, Duration retryAfter) { super("Splitwise request failed (HTTP " + status + ")"); this.status = status; this.retryAfter = retryAfter; }
  }
  private JsonNode get(String key, String path) {
    try {
      var response = http.send(HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(30))
          .header("Authorization", "Bearer " + key).header("User-Agent", "Thereabout/1.0")
          .header("Accept", "application/json").GET().build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) throw new RemoteFailure(response.statusCode(), retry(response.headers().firstValue("Retry-After").orElse(null)));
      if (response.body().length() > 10_000_000) throw new RemoteFailure(502, null);
      return json.readTree(response.body());
    } catch (RemoteFailure e) { throw e; }
    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RemoteFailure(503, null); }
    catch (Exception e) { throw new RemoteFailure(503, null); }
  }
  private Duration retry(String value) {
    if (value == null) return null;
    try { return Duration.ofSeconds(Math.max(0, Long.parseLong(value))); }
    catch (NumberFormatException e) {
      try { return Duration.between(Instant.now(), ZonedDateTime.parse(value, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).isNegative()
          ? Duration.ZERO : Duration.between(Instant.now(), ZonedDateTime.parse(value, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()); }
      catch (RuntimeException ignored) { return null; }
    }
  }
  public GenSplitwiseCatalog catalog(String key) {
    get(key, "get_current_user");
    var result = new GenSplitwiseCatalog().groups(new ArrayList<>()).sourceCategories(new ArrayList<>()).accounts(new ArrayList<>()).categories(new ArrayList<>());
    for (var g : get(key, "get_groups").path("groups")) {
      if (g.path("id").asLong() == 0) continue;
      var group = new GenSplitwiseGroup().id(g.path("id").asLong()).name(g.path("name").asString()).members(new ArrayList<>());
      for (var m : g.path("members")) group.addMembersItem(new GenSplitwiseMember().id(m.path("id").asLong())
          .name((m.path("first_name").asString("") + " " + m.path("last_name").asString("")).trim()));
      result.addGroupsItem(group);
    }
    for (var parent : get(key, "get_categories").path("categories"))
      for (var c : parent.path("subcategories")) result.addSourceCategoriesItem(new GenSplitwiseCategory()
          .id(c.path("id").asLong()).name(c.path("name").asString()).parentName(parent.path("name").asString()));
    return result;
  }
  public List<SplitwiseExpense> expenses(String key, long group, Instant after, Instant before) {
    var result = new ArrayList<SplitwiseExpense>();
    var ids = new HashSet<Long>();
    for (int offset = 0; offset < 1_000_000; offset += 100) {
      String path = "get_expenses?group_id=" + group + "&limit=100&offset=" + offset
          + (after == null ? "" : "&updated_after=" + encode(after)) + "&updated_before=" + encode(before);
      var page = get(key, path).path("expenses");
      if (!page.isArray()) throw new RemoteFailure(502, null);
      for (var node : page) {
        var expense = json.treeToValue(node, SplitwiseExpense.class);
        if (!ids.add(expense.id())) throw new RemoteFailure(502, null);
        result.add(expense);
      }
      if (page.size() < 100) return result;
    }
    throw new RemoteFailure(502, null);
  }
  public SplitwiseExpense expense(String key, long id) { return json.treeToValue(get(key, "get_expense/" + id).path("expense"), SplitwiseExpense.class); }
  private String encode(Instant instant) { return java.net.URLEncoder.encode(instant.toString(), java.nio.charset.StandardCharsets.UTF_8); }
}
