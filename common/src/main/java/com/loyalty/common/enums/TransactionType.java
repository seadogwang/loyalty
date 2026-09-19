package com.loyalty.common.enums;

/** Point transaction type (design 4.1). Ledger amount is signed per this enum. */
public enum TransactionType {
    EARN,
    REDEEM,
    EXPIRE,
    REVERSE,
    ADJUST,
    RECALCULATE,
    RESTORE
}
