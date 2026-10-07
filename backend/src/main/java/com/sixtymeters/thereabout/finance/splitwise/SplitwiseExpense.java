package com.sixtymeters.thereabout.finance.splitwise;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SplitwiseExpense(long id, @JsonProperty("group_id") long groupId,
    String description, String details, String date, @JsonProperty("currency_code") String currency,
    @JsonProperty("updated_at") String updatedAt, @JsonProperty("deleted_at") String deletedAt,
    boolean payment, Category category, List<Share> users) {
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Category(long id, String name) {}
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Share(@JsonProperty("user_id") long userId,
      @JsonProperty("paid_share") String paidShare, @JsonProperty("owed_share") String owedShare) {}
  public BigDecimal amount(long member) {
    return users == null ? BigDecimal.ZERO : users.stream().filter(s -> s.userId() == member)
        .map(s -> new BigDecimal(s.paidShare()).subtract(new BigDecimal(s.owedShare())))
        .findFirst().orElse(BigDecimal.ZERO);
  }
  public LocalDateTime occurredAt() {
    return OffsetDateTime.parse(date).atZoneSameInstant(ZoneId.of("Europe/Zurich")).toLocalDateTime();
  }
  public static String reference(long expense, long member) { return "splitwise:" + expense + ":user:" + member; }
}
