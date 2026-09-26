package com.sixtymeters.thereabout.finance.service;

import com.sixtymeters.thereabout.client.data.ConfigurationKey;
import com.sixtymeters.thereabout.client.data.ConfigurationRepository;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** The database owns the MCP credential; startup never replaces an existing key. */
@Service
@RequiredArgsConstructor
public class FinanceMcpKeyService {
  private final ConfigurationRepository configuration;

  @PostConstruct
  public void initialize() {
    if (configuration.existsById(ConfigurationKey.FINANCE_MCP_KEY)) return;
    byte[] random = new byte[32];
    new SecureRandom().nextBytes(random);
    configuration.insertMcpKeyIfAbsent(Base64.getUrlEncoder().withoutPadding().encodeToString(random));
  }

  public String getKey() {
    return configuration.findById(ConfigurationKey.FINANCE_MCP_KEY)
        .map(entry -> entry.getConfigValue())
        .filter(value -> value.length() >= 32)
        .orElseThrow(() -> new IllegalStateException("MCP credential is missing or invalid"));
  }

  public boolean matchesAuthorization(String authorization) {
    if (authorization == null) return false;
    return MessageDigest.isEqual(
        ("Bearer " + getKey()).getBytes(StandardCharsets.UTF_8),
        authorization.getBytes(StandardCharsets.UTF_8));
  }
}
