package com.pricealert.service;
import com.pricealert.config.MonitorConfig;
import com.pricealert.domain.alert.*;
import com.pricealert.domain.product.Product;
import com.pricealert.repository.AlertRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
@Service
public class AlertService {
    private final AlertRepository repository;
    private final MonitorConfig config;
    private final ObjectMapper mapper;
    public AlertService(AlertRepository repository, MonitorConfig config, ObjectMapper mapper) {
        this.repository=repository; this.config=config; this.mapper=mapper;
    }
    public void enqueue(Product product, PriceAlert alert) {
        if(!alert.analysis().shouldAlert()) return;
        String coupon=alert.product().coupon()==null?null:alert.product().coupon().code();
        String key=product.id+"|"+alert.product().currentPrice().toPlainString()+"|"+coupon;
        // Restock is an event; the old lastCheckedAt identifies this stock transition.
        if("BACK_IN_STOCK".equals(alert.analysis().reason())) key+="|restock|"+product.lastCheckedAt;
        String fingerprint=hash(key);
        if(repository.existsByFingerprint(fingerprint)) return;
        Alert last=repository.findFirstByProductIdOrderByCreatedAtDesc(product.id).orElse(null);
        if(!canSend(last,alert,coupon,alert.product().collectedAt())) return;
        try {
            Alert entity=new Alert(); entity.productId=product.id; entity.alertType=alert.analysis().reason();
            entity.price=alert.product().currentPrice(); entity.discount=alert.analysis().sufficientHistory()?
                alert.analysis().historicalDiscount():alert.analysis().storeDiscount();
            entity.couponCode=coupon; entity.fingerprint=fingerprint; entity.status="PENDING";
            entity.payload=mapper.writeValueAsString(alert); entity.createdAt=alert.product().collectedAt();
            entity.nextAttemptAt=Instant.now(); repository.save(entity);
        } catch(com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("Falha ao serializar alerta"); }
    }
    public boolean canSend(Alert last, PriceAlert next, String coupon, Instant now) {
        if(last==null || !last.createdAt.plus(config.cooldown()).isAfter(now)) return true;
        boolean extraDrop=DiscountService.discount(last.price,next.product().currentPrice()).compareTo(config.trackedDrop())>=0;
        boolean exception=next.analysis().newHistoricalLow() || extraDrop ||
            coupon!=null && !Objects.equals(last.couponCode,coupon) ||
            "BACK_IN_STOCK".equals(next.analysis().reason()) ||
            "TARGET_PRICE".equals(next.analysis().reason()) && !"TARGET_PRICE".equals(last.alertType);
        return exception;
    }
    private String hash(String key) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}

