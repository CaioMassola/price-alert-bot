package com.pricealert.service;
import com.pricealert.domain.price.PriceHistory;
import com.pricealert.domain.product.*;
import com.pricealert.domain.alert.DiscountAnalysis;
import com.pricealert.repository.PriceHistoryRepository;
import org.springframework.stereotype.Service;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
@Service
public class PriceHistoryService {
    private final PriceHistoryRepository repository;
    public PriceHistoryService(PriceHistoryRepository repository) { this.repository=repository; }
    public void record(Product product, ProductSnapshot snapshot, DiscountAnalysis analysis, List<PriceHistory> history) {
        PriceHistory last=history.isEmpty()?null:history.getLast();
        String code=snapshot.coupon()==null?null:snapshot.coupon().code();
        boolean changed=last==null || last.price.compareTo(snapshot.currentPrice())!=0 ||
            !moneyEquals(last.originalPrice,snapshot.originalPrice()) || !Objects.equals(last.coupon,code) ||
            last.available!=snapshot.available();
        // One daily unchanged observation preserves a useful 30-day baseline without five-minute duplicates.
        boolean daily=last!=null && last.collectedAt.atZone(ZoneOffset.UTC).toLocalDate()
            .isBefore(snapshot.collectedAt().atZone(ZoneOffset.UTC).toLocalDate());
        if(changed || daily) {
            PriceHistory entry=new PriceHistory();
            entry.productId=product.id; entry.price=snapshot.currentPrice(); entry.originalPrice=snapshot.originalPrice();
            entry.discountPercentage=analysis.storeDiscount(); entry.coupon=code; entry.available=snapshot.available();
            entry.collectedAt=snapshot.collectedAt(); repository.save(entry); history.add(entry);
        }
        product.lowestPrice=history.stream().map(h->h.price).min(BigDecimal::compareTo).orElse(snapshot.currentPrice());
        product.highestPrice=history.stream().map(h->h.price).max(BigDecimal::compareTo).orElse(snapshot.currentPrice());
        product.averagePrice=DiscountService.average(history);
        product.average30DayPrice=DiscountService.average(history.stream()
            .filter(h->!h.collectedAt.isBefore(snapshot.collectedAt().minus(Duration.ofDays(30)))).toList());
    }
    private boolean moneyEquals(BigDecimal a, BigDecimal b) { return a==null?b==null:b!=null && a.compareTo(b)==0; }
}

