package com.chequetrack.chequebook.domain;

public enum LeafStatus {
    /** Blank leaf in hand. */
    UNUSED,
    /** Reserved for one user while a cheque is being prepared (M9). */
    ALLOCATED,
    /** A cheque was written on it (M9). */
    USED,
    /** Spoiled, damaged or cancelled after printing. */
    VOID,
    /** Book cancelled while the leaf was unused. */
    CANCELLED,
    /** Book reported lost while the leaf was unused. */
    LOST
}
