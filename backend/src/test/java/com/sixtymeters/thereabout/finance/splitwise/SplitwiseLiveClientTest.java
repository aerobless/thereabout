package com.sixtymeters.thereabout.finance.splitwise;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.json.JsonMapper;

/** Opt-in, read-only transport verification; the credential never enters test output. */
@EnabledIfEnvironmentVariable(named="THEREABOUT_SPLITWISE_LIVE",matches="true")
class SplitwiseLiveClientTest {
  @Test void realClientReadsCatalogFullPaginationAndIncrementalBounds() throws Exception {
    String key=Files.readAllLines(Path.of(System.getenv("THEREABOUT_SPLITWISE_KEY_FILE"))).stream().filter(line->line.startsWith("SPLITWISE_API_KEY=")).findFirst().orElseThrow().substring("SPLITWISE_API_KEY=".length()).trim();
    if(key.startsWith("\"") || key.startsWith("'")) key=key.substring(1,key.length()-1);
    var client=new SplitwiseClient(new JsonMapper()); var at=Instant.now();
    var catalog=client.catalog(key); assertThat(catalog.getGroups()).extracting(g->g.getId()).contains(53533798L); assertThat(catalog.getSourceCategories()).isNotEmpty();
    var all=client.expenses(key,53533798L,null,at); assertThat(all).hasSizeGreaterThan(1000); assertThat(all.stream().map(SplitwiseExpense::id).distinct().count()).isEqualTo(all.size());
    var from=at.minus(Duration.ofMinutes(10)); var recent=client.expenses(key,53533798L,from,at);
    for(var e:recent) {assertThat(Instant.parse(e.updatedAt())).isAfterOrEqualTo(from);assertThat(Instant.parse(e.updatedAt())).isBeforeOrEqualTo(at);}
    assertThat(client.expense(key,all.getFirst().id()).id()).isEqualTo(all.getFirst().id());
    System.out.println("Read-only Splitwise client verified: "+all.size()+" expenses, "+recent.size()+" in the ten-minute incremental window.");
  }
}
