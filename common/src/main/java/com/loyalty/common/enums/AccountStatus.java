package com.loyalty.common.enums;

/** Account status (design 4.5). SUSPENDED/CLOSED accounts reject point writes. */
public enum AccountStatus {
    ACTIVE,
    SUSPENDED,
    CLOSED
}
