package com.sixtymeters.thereabout.finance.service;

import static com.sixtymeters.thereabout.finance.domain.FinanceRules.*;
import java.net.URI;
import java.util.Locale;

public final class WebsiteUrls {
  private WebsiteUrls() {}
  public static String normalize(String value) {
    if (value == null || value.isBlank()) return null;
    require(value.length() <= 2048, "Website URL is too long");
    String url = value.trim(); if (!url.contains("://")) url = "https://" + url;
    try {
      var uri = URI.create(url);
      require("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null
          && (uri.getPort() == -1 || uri.getPort() == 443), "Enter an HTTPS website without credentials");
      return new URI("https", null, uri.getHost().toLowerCase(Locale.ROOT), -1, "/", null, null).toASCIIString();
    } catch (IllegalArgumentException | java.net.URISyntaxException ex) {
      throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid website URL");
    }
  }
}
