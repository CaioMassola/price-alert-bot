package com.pricealert.service;
import com.pricealert.config.MonitorConfig;
import com.pricealert.domain.alert.DiscountAnalysis;
import com.pricealert.domain.product.*;
import com.pricealert.domain.price.PriceHistory;
import org.springframework.stereotype.Service;
import java.math.*;
import java.time.*;
import java.util.*;
@Service
public class DiscountService {
    private final MonitorConfig config;
    public DiscountService(MonitorConfig config) { this.config=config; }
    public static BigDecimal discount(BigDecimal reference, BigDecimal price) {
        if (reference==null || reference.signum()<=0) return BigDecimal.ZERO;
        return reference.subtract(price).multiply(BigDecimal.valueOf(100)).divide(reference,2,RoundingMode.HALF_UP);
    }
    public static BigDecimal average(List<PriceHistory> history) {
        if(history.isEmpty()) return null;
        return history.stream().map(h->h.price).reduce(BigDecimal.ZERO,BigDecimal::add)
            .divide(BigDecimal.valueOf(history.size()),2,RoundingMode.HALF_UP);
    }
    public DiscountAnalysis analyze(Product previous, ProductSnapshot snapshot, List<PriceHistory> history, BigDecimal target, boolean tracked) {
        var recent=history.stream().filter(h->!h.collectedAt.isBefore(snapshot.collectedAt().minus(Duration.ofDays(30)))).toList();
        BigDecimal average=average(recent), price=snapshot.currentPrice();
        boolean sufficient=recent.size()>=config.minimumSamples() &&
            recent.getFirst().collectedAt.isBefore(snapshot.collectedAt().minus(config.historyMinimumAge()));
        BigDecimal lowest=history.stream().map(h->h.price).min(BigDecimal::compareTo).orElse(null);
        BigDecimal old=previous==null ? null:previous.currentPrice;
        BigDecimal store=discount(snapshot.originalPrice(),price), historical=discount(average,price), change=discount(old,price);
        boolean newLow=lowest!=null && price.compareTo(lowest)<0;
        boolean reached=target!=null && price.compareTo(target)<=0;
        boolean restocked=previous!=null && !previous.available && snapshot.available();
        boolean newCoupon=snapshot.coupon()!=null && !Objects.equals(previous==null?null:previous.couponCode,snapshot.coupon().code());
        // Once history exists, an inflated reference price alone must not trigger a deal.
        boolean goodHistorical=sufficient && historical.compareTo(config.minHistoricalDiscount())>=0;
        boolean goodStore=!sufficient && store.compareTo(config.minStoreDiscount())>=0;
        String reason=reached?"TARGET_PRICE":newLow?"HISTORICAL_LOW":
            tracked && change.compareTo(config.trackedDrop())>=0?"PRICE_DROP":
            goodHistorical?"HISTORICAL_DISCOUNT":goodStore?"STORE_DISCOUNT_UNVERIFIED":
            tracked && restocked?"BACK_IN_STOCK":tracked && newCoupon?"NEW_COUPON":"NONE";
        // Discovery must meet the configured discount floor, even for a new historical low.
        if(!tracked && !goodHistorical && !goodStore) reason="NONE";
        return new DiscountAnalysis(price,old,average,lowest,store,historical,change,sufficient,newLow,
            snapshot.available() && !"NONE".equals(reason),reason);
    }
}

