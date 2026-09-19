package com.loyalty.common.enums;

/** Allocation type (design 4.2). Records how a positive asset is consumed/expired/... */
public enum AllocationType {
    CONSUME,
    EXPIRE,
    REVERSE,
    RESTORE,
    ADJUST
}
