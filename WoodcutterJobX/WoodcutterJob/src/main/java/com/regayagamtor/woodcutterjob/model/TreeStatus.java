package com.regayagamtor.woodcutterjob.model;

/** Live status of a registered tree (computed on demand, never by scanning). */
public enum TreeStatus {
    VALID,
    INVALID,
    DISABLED,
    UNLOADED
}
