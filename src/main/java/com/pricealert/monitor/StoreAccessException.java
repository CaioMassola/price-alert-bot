package com.pricealert.monitor;
public class StoreAccessException extends RuntimeException {
    private final int status;
    public StoreAccessException(int status, String message) { super(message); this.status=status; }
    public int status() { return status; }
}

