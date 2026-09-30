package com.pricealert.integration;
import com.pricealert.domain.alert.PriceAlert;
public interface NotificationChannel {
    void send(PriceAlert alert);
    default void send(PriceAlert alert, boolean introduction) { send(alert); }
    boolean configured();
    default boolean configured(com.pricealert.domain.store.Store store) { return configured(); }
}

