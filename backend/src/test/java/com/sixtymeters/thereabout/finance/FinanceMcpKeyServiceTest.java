package com.sixtymeters.thereabout.finance;

import static org.assertj.core.api.Assertions.*;

import com.sixtymeters.thereabout.client.data.ConfigurationKey;
import com.sixtymeters.thereabout.client.data.ConfigurationRepository;
import com.sixtymeters.thereabout.finance.service.FinanceMcpKeyService;
import java.util.ArrayList;
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {"thereabout.calendar.worker-enabled=false", "thereabout.launcher.fetch-icons=false"})
@ActiveProfiles("test")
class FinanceMcpKeyServiceTest {
  @Autowired ConfigurationRepository configuration;
  @Autowired FinanceMcpKeyService keys;

  @Test
  void startupPersistsCredentialAndFurtherStartsReuseIt() {
    String original = keys.getKey();
    assertThat(configuration.findById(ConfigurationKey.FINANCE_MCP_KEY)).isPresent();
    var restarted = new FinanceMcpKeyService(configuration);
    restarted.initialize();
    assertThat(restarted.getKey()).isEqualTo(original);
    assertThat(restarted.matchesAuthorization("Bearer " + original)).isTrue();
    assertThat(restarted.matchesAuthorization(null)).isFalse();
    assertThat(restarted.matchesAuthorization("Bearer incorrect")).isFalse();
    assertThat(restarted.matchesAuthorization(original)).isFalse();
  }

  @Test
  void concurrentFirstStartsConvergeOnOneSecureCredential() throws Exception {
    configuration.deleteById(ConfigurationKey.FINANCE_MCP_KEY);
    try (var executor = Executors.newFixedThreadPool(8)) {
      var results = new ArrayList<Future<String>>();
      for (int i = 0; i < 8; i++) {
        results.add(executor.submit(() -> {
          var instance = new FinanceMcpKeyService(configuration);
          instance.initialize();
          return instance.getKey();
        }));
      }
      String persisted = results.getFirst().get();
      for (var result : results) assertThat(result.get()).isEqualTo(persisted);
      assertThat(Base64.getUrlDecoder().decode(persisted)).hasSize(32);
      assertThat(keys.getKey()).isEqualTo(persisted);
    } finally {
      keys.initialize();
    }
  }
}
