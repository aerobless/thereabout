package com.sixtymeters.thereabout.shared.icons;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sixtymeters.thereabout.finance.data.FinanceAccountEntity;
import com.sixtymeters.thereabout.finance.service.AccountService;
import com.sixtymeters.thereabout.finance.transport.FinanceAccountIconController;
import java.time.Instant;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {"thereabout.calendar.worker-enabled=false", "thereabout.launcher.fetch-icons=false"})
@ActiveProfiles("test")
@Transactional
class WebsiteIconServiceTest {
  @Autowired WebsiteIconService icons;
  @Autowired WebsiteIconRepository cache;
  @MockitoBean WebsiteIconFetcher fetcher;

  private static final String WEBSITE = "https://bank.example/";
  private static final byte[] PNG = {(byte)137,80,78,71,13,10,26,10,0,0,0,0};

  @Test
  void cachesTheDownloadedIconAndServesItWithSafeHeaders() {
    when(fetcher.fetch(WEBSITE)).thenReturn(new WebsiteIconFetcher.Image(PNG, "image/png"));
    var accounts = mock(AccountService.class);
    var account = new FinanceAccountEntity();
    account.setWebsiteUrl(WEBSITE);
    when(accounts.requireAccount(1)).thenReturn(account);
    var controller = new FinanceAccountIconController(accounts, icons);
    var response = controller.icon(1);
    assertThat(response.getBody()).containsExactly(PNG);
    assertThat(response.getHeaders().getContentType().toString()).isEqualTo("image/png");
    assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
    assertThat(response.getHeaders().getFirst("Content-Security-Policy")).contains("sandbox");
    assertThat(icons.icon(WEBSITE)).isPresent();
    verify(fetcher, times(1)).fetch(WEBSITE);
    assertThat(cache.findAll()).anySatisfy(entry -> assertThat(entry.getImageData()).containsExactly(PNG));
  }

  @Test
  void remembersMissingIconsAndRetriesAfterTheFailureCacheExpires() {
    assertThat(icons.icon(WEBSITE)).isEmpty();
    assertThat(icons.icon(WEBSITE)).isEmpty();
    verify(fetcher, times(1)).fetch(WEBSITE);
    var entry = cache.findAll().stream().filter(e -> e.getImageData() == null).findFirst().orElseThrow();
    entry.setCheckedAt(Instant.now().minus(Duration.ofHours(2)));
    cache.saveAndFlush(entry);
    when(fetcher.fetch(WEBSITE)).thenReturn(new WebsiteIconFetcher.Image(PNG, "image/png"));
    assertThat(icons.icon(WEBSITE)).isPresent();
    verify(fetcher, times(2)).fetch(WEBSITE);
  }

  @Test
  void aTemporaryOutageRetainsThePreviousIcon() {
    when(fetcher.fetch(WEBSITE)).thenReturn(new WebsiteIconFetcher.Image(PNG, "image/png"));
    icons.icon(WEBSITE);
    var entry = cache.findAll().stream().filter(e -> e.getImageData() != null).findFirst().orElseThrow();
    entry.setCheckedAt(Instant.now().minus(Duration.ofDays(31)));
    cache.saveAndFlush(entry);
    when(fetcher.fetch(WEBSITE)).thenReturn(null);
    assertThat(icons.icon(WEBSITE)).get().satisfies(image -> assertThat(image.bytes()).containsExactly(PNG));
  }
}
