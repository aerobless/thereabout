package com.sixtymeters.thereabout.finance.service;

import static com.sixtymeters.thereabout.finance.domain.FinanceRules.*;

import com.sixtymeters.thereabout.finance.data.*;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.*;

/** Owns the atomic ledger write, audit and replay boundary for every transport. */
@Service
@RequiredArgsConstructor
public class FinanceWriteCoordinator {
  private final FinanceWriteLockRepository locks;
  private final FinanceRequestRepository requests;
  private final FinanceAuditRepository audits;
  private final EntityManager entityManager;
  private final ObjectMapper json;
  private final Clock financeClock;

  @Transactional
  public <T> T write(
      String operation, String key, Object input, Class<T> resultType, Supplier<T> action) {
    require(
        key != null && !key.isBlank() && key.length() <= 100,
        "requestKey is required (max 100 characters)");
    if (locks.acquire() == null) throw new IllegalStateException("Finance write lock is missing");
    String fingerprint = fingerprint(operation, input);
    var previous = replay(operation, key, input, resultType);
    if (previous.isPresent()) return previous.get();
    T result = action.get();
    entityManager.flush();
    var record = new FinanceRequestEntity();
    record.setRequestKey(key);
    record.setFingerprint(fingerprint);
    record.setResultJson(json.writeValueAsString(result));
    requests.saveAndFlush(record);
    return result;
  }

  @Transactional(readOnly = true)
  public <T> Optional<T> replay(String operation, String key, Object input, Class<T> resultType) {
    require(
        key != null && !key.isBlank() && key.length() <= 100,
        "requestKey is required (max 100 characters)");
    return requests
        .findById(key)
        .map(
            previous -> {
              conflict(
                  previous.getFingerprint().equals(fingerprint(operation, input)),
                  "requestKey was already used for different arguments or an older API; use a new"
                      + " key for a new operation");
              return json.readValue(previous.getResultJson(), resultType);
            });
  }

  public void audit(String operation, Long id, Object before, Object after) {
    var audit = new FinanceAuditEntity();
    audit.setOperation(operation);
    audit.setEntityId(id);
    audit.setCreatedAt(LocalDateTime.now(financeClock));
    audit.setBeforeJson(json.writeValueAsString(before));
    audit.setAfterJson(json.writeValueAsString(after));
    audits.save(audit);
  }

  private String fingerprint(String operation, Object input) {
    try {
      String canonical = json.writeValueAsString(sorted(json.valueToTree(input)));
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(
                      ("finance-v2:" + operation + canonical).getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private JsonNode sorted(JsonNode node) {
    if (node.isObject()) {
      var result = json.createObjectNode();
      var names = new ArrayList<String>();
      node.propertyNames().forEach(names::add);
      Collections.sort(names);
      names.forEach(name -> result.set(name, sorted(node.get(name))));
      return result;
    }
    if (node.isArray()) {
      var result = json.createArrayNode();
      node.forEach(value -> result.add(sorted(value)));
      return result;
    }
    return node;
  }
}
