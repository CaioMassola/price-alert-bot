package com.pricealert.integration;
import java.time.Instant;
public class NotificationException extends RuntimeException {
    public final String state;
    public final Instant retryAt;
    public NotificationException(String state,Instant retryAt) {
        super("Entrega Discord: "+state); this.state=state; this.retryAt=retryAt;
    }
}

