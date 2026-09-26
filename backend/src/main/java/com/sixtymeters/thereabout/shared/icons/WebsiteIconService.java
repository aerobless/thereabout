package com.sixtymeters.thereabout.shared.icons;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** Site-level cache, shared by accounts at the same bank, independent of financial postings. */
@Service
@RequiredArgsConstructor
public class WebsiteIconService {
  private final WebsiteIconRepository icons;
  private final WebsiteIconFetcher fetcher;

  public Optional<WebsiteIconFetcher.Image> icon(String website) {
    String id = cacheKey(website);
    var existing = icons.findById(id);
    if (existing.isPresent()) {
      var cached = existing.get();
      Duration lifetime = cached.getImageData() == null ? Duration.ofHours(1) : Duration.ofDays(30);
      if (cached.getCheckedAt().plus(lifetime).isAfter(Instant.now())) return image(cached);
    }
    // Network work happens outside a database transaction or the finance write lock.
    var downloaded = fetcher.fetch(website);
    var cached = existing.orElseGet(WebsiteIconEntity::new);
    cached.setId(id);
    cached.setCheckedAt(Instant.now());
    // A temporary outage must not remove a previously usable logo.
    if (downloaded != null) {
      cached.setImageData(downloaded.bytes());
      cached.setContentType(downloaded.type());
    }
    try {
      icons.saveAndFlush(cached);
    } catch (DataIntegrityViolationException concurrentInsert) {
      // Another request may have populated this site's cache in the meantime.
      var winner = icons.findById(id);
      if (winner.isEmpty()) throw concurrentInsert;
      return image(winner.get());
    }
    return image(cached);
  }

  private Optional<WebsiteIconFetcher.Image> image(WebsiteIconEntity cached) {
    return cached.getImageData() == null ? Optional.empty()
        : Optional.of(new WebsiteIconFetcher.Image(cached.getImageData(), cached.getContentType()));
  }

  private String cacheKey(String website) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(website.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
  }
}
