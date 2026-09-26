package com.sixtymeters.thereabout.finance.data;

public enum AccountKind {
  CASH,
  INVESTMENT,
  REAL_ESTATE,
  OTHER_ASSET,
  EXPENSE,
  REVENUE,
  OPENING,
  RECONCILIATION;

  public boolean isOwn() {
    return this == CASH || this == INVESTMENT || this == REAL_ESTATE || this == OTHER_ASSET;
  }

  public boolean isValuedAsset() {
    return this == INVESTMENT || this == REAL_ESTATE || this == OTHER_ASSET;
  }

  public boolean isCounterparty() {
    return this == EXPENSE || this == REVENUE;
  }
}
